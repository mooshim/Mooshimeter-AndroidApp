/*
 * Copyright (c) Mooshim Engineering LLC 2015.
 *
 * This file is part of Mooshimeter-AndroidApp.
 *
 * Foobar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Mooshimeter-AndroidApp.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mooshim.mooshimeter.common;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mooshim.mooshimeter.util.Conversion;
import com.mooshim.mooshimeter.util.WatchDog;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;

/**
 * Created by JWHONG on 1/26/2015.
 * The purpose of this class is to pace all interactions with the BLE layer to one request at a time.
 * Anything more than that and everything gets buggy.
 */
public class BLEUtil {
    public final static String EXTRA_STATUS                     = "com.mooshim.mooshimeter.EXTRA_STATUS";
    public final static String EXTRA_ADDRESS                    = "com.mooshim.mooshimeter.EXTRA_ADDRESS";
    public final static UUID   CC_SERVICE_UUID                  = UUID.fromString("f000ccc0-0451-4000-b000-000000000000");
    private static final String TAG="BLEUtil";

    private Context              mContext;   // Global application context
    private BluetoothAdapter     mBtAdapter;
    private BluetoothGatt        mBluetoothGatt;
    private BluetoothGattService mPrimaryService;
    private BluetoothGattService mCCService;

    private WatchDog mWatchdog;

    private BLEUtilRequest mRunning = null;
    private LinkedList<BLEUtilRequest> mExecuteQueue = new LinkedList<BLEUtilRequest>();
    private HashMap<UUID,BLEUtilCB> mNotifyCB= new HashMap<UUID,BLEUtilCB>();

    private Runnable mDisconnectCB = null;

    public static abstract class BLEUtilCB implements Runnable {
        public UUID uuid;       // UUID
        public byte[] value;    // Value for the characteristic in question
        public int error;       // Error from the BLE layer
        public abstract void run();
    };

    private static class BLEUtilRequest {
        public Runnable  payload;
        public BLEUtilCB callback;
        protected BLEUtilRequest() {}
        public BLEUtilRequest(final Runnable p, final BLEUtilCB c) {
            payload  = p;
            callback = c;
        }
    }

    private static BLEUtil mInstance = null;

    public static synchronized BLEUtil getInstance() {
        return mInstance;
    }

    public static synchronized BLEUtil getInstance(Context context) {
        if(mInstance == null) {
            mInstance = new BLEUtil(context);
        }
        return getInstance();
    }

    public static void Destroy() {
        // Clear the global instance
        if(mInstance != null) {
            mInstance.disconnect();
            mInstance.clear();
            mInstance = null;
        }
    }

    protected BLEUtil(Context context) {
        // Initialize internal structures
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        mContext = context.getApplicationContext();
    }

    public void clear() {
        mExecuteQueue.clear();
        mNotifyCB.clear();
    }

    ////////////////////////////////
    // Service functions for internal queue
    ////////////////////////////////

    synchronized private void serviceExecuteQueue(BLEUtilRequest add) {
        if(add != null) {
            if(mExecuteQueue.size() != 0) {
                Log.e(TAG, "Adding to BLEUtil queue with an item already in the queue.  This is probably not what you intended to do.");
            }
            mExecuteQueue.addLast(add);
        }
        if(mExecuteQueue.size() > 0) {
            mRunning = mExecuteQueue.remove(0);
            mRunning.payload.run();
        }
    }
    synchronized private void finishRunningBlock(final UUID uuid, final int error, final byte[] value) {
        if(mRunning == null) {
            Log.e(TAG,"ERROR: Asked to finish a task but no task running");
            return;
        }
        // Run this on the main thread, don't block BluetoothGATT for any longer than necessary.
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final BLEUtilCB cb = mRunning.callback;
        if(cb != null) {
            cb.uuid  = uuid;
            cb.error = error;
            cb.value = value;
            mainHandler.post(cb);
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                serviceExecuteQueue(null);
            }
        });
    }

    ////////////////////////////////
    // Accessors
    ////////////////////////////////

    public String getBTAddress() {
        return mBluetoothGatt.getDevice().getAddress();
    }

    public void connect(final String address, final BLEUtilCB cb, final Runnable disconnectCB) {
        mDisconnectCB = disconnectCB;
        connect(address,cb);
    }

    public void connect(final String address, final BLEUtilCB cb) {
        BLEUtilRequest r = new BLEUtilRequest(new Runnable() {
            @Override
            public void run() {
                final BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
                mBluetoothGatt = device.connectGatt(mContext,false,mGattCallbacks);
                refreshDeviceCache();
            }
        }, cb);
        serviceExecuteQueue(r);
    }

    public void setDisconnectCB(final Runnable disconnectCB) {
        mDisconnectCB=disconnectCB;
    }

    public void disconnect() {
        if(mBluetoothGatt!=null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }
        mPrimaryService = null;
    }

    public void disconnect(final Runnable disconnectCB) {
        mDisconnectCB = disconnectCB;
        disconnect();
    }

    public void setWriteType(UUID uuid, int wtype) {
        final BluetoothGattCharacteristic c = mPrimaryService.getCharacteristic(uuid);
        c.setWriteType(wtype);
    }

    public void setConnectionInterval(short msec, short timeout, BLEUtilCB on_complete) {
        // Make sure connection interval is long enough for OAD (Android default connection interval is 7.5 ms)
        /*
        final byte[] value = { Conversion.loUint16(msec), Conversion.hiUint16(msec), Conversion.loUint16(msec),
                Conversion.hiUint16(msec), 0, 0, Conversion.loUint16(timeout), Conversion.hiUint16(timeout) };
        final BluetoothGattCharacteristic cc_char = mCCService.getCharacteristic( CC_SERVICE_UUID );

        BLEUtilRequest r = new BLEUtilRequest(new Runnable() {
            @Override
            public void run() {
                cc_char.setValue(value);
                mBluetoothGatt.writeCharacteristic(cc_char);
            }
        }, on_complete);
        serviceExecuteQueue(r);*/
        // Skip for now
        on_complete.run();
    }

    public void req(UUID uuid, BLEUtilCB on_complete) {
        final BluetoothGattCharacteristic c = mPrimaryService.getCharacteristic(uuid);
        BLEUtilRequest r = new BLEUtilRequest(new Runnable() {
            @Override
            public void run() {
                mBluetoothGatt.readCharacteristic(c);
            }
        }, on_complete);
        serviceExecuteQueue(r);
    }
    public void send(final UUID uuid, final byte[] value, final BLEUtilCB on_complete) {
        final BluetoothGattCharacteristic c = mPrimaryService.getCharacteristic(uuid);
        BLEUtilRequest r = new BLEUtilRequest(new Runnable() {
            @Override
            public void run() {
                c.setValue(value);
                mBluetoothGatt.writeCharacteristic(c);
            }
        }, on_complete);
        serviceExecuteQueue(r);
    }

    public boolean isNotificationEnabled(BluetoothGattCharacteristic c) {
        final BluetoothGattDescriptor d = c.getDescriptor(GattInfo.CLIENT_CHARACTERISTIC_CONFIG);
        final byte[] dval = d.getValue();
        final boolean retval =  (dval == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        return retval;
    }

    public void enableNotify(final UUID uuid, final boolean enable, final BLEUtilCB on_complete, final BLEUtilCB on_notify) {
        final BluetoothGattCharacteristic c = mPrimaryService.getCharacteristic(uuid);
        // Set up the notify callback
        if(on_notify != null) {
            mNotifyCB.put(uuid, on_notify);
        } else {
            mNotifyCB.remove(uuid);
        }
        if(isNotificationEnabled(c) != enable) {
            // Only bother setting the notification if the status has changed
            BLEUtilRequest r = new BLEUtilRequest(new Runnable() {
                @Override
                public void run() {
                    if (mBluetoothGatt.setCharacteristicNotification(c, enable)) {
                        final BluetoothGattDescriptor clientConfig = c.getDescriptor(GattInfo.CLIENT_CHARACTERISTIC_CONFIG);
                        final byte[] enable_val;
                        if(enable) {
                            enable_val = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                        } else {
                            enable_val = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                        }
                        clientConfig.setValue(enable_val);
                        mBluetoothGatt.writeDescriptor(clientConfig);
                    }
                }
            }, on_complete);
            serviceExecuteQueue(r);
        } else {
            // Otherwise just run the callback
            on_complete.run();
        }
    }

    ////////////////////////////////
    // GATT Callbacks
    ////////////////////////////////

    private boolean refreshDeviceCache(){
        try {
            Method localMethod = mBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                final boolean b = ((Boolean) localMethod.invoke(mBluetoothGatt, new Object[0])).booleanValue();
                return b;
            } else {
                Log.e(TAG, "Unable to wipe the GATT Cache");
            }
        }
        catch (Exception localException) {
            Log.e(TAG, "An exception occured while refreshing device");
        }
        return false;
    }

    private static void printGattError(int s) {
        if (s != BluetoothGatt.GATT_SUCCESS) {
            // GATT Error 133 seems to be coming up from time to time.  I don't think we're doing anything wrong,
            // just instability in the Android stack...
            Log.e(TAG, "GATT error code: " + s);
        }
    }

    private BluetoothGattService getService(final UUID sUUID) {
        for( BluetoothGattService s : mBluetoothGatt.getServices() ) {
            if(s.getUuid().equals(sUUID)) {
                return s;
            }
        }
        return null;
    }

    public boolean setPrimaryService(final UUID sUUID) {
        // Sets the GATT service to which all communications are directed
        mPrimaryService = getService(sUUID);
        return mPrimaryService != null;
    }

    private BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (mBluetoothGatt == null) {
                // Log.e(TAG, "mBluetoothGatt not created!");
                return;
            }

            try {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        mBluetoothGatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        if(mDisconnectCB!=null) {
                            mDisconnectCB.run();
                            mDisconnectCB=null;
                        }
                        break;
                    default:
                        Log.e(TAG, "New state not processed: " + newState);
                        break;
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if( setPrimaryService(MooshimeterDevice.mUUID.METER_SERVICE) ) {
                Log.i(TAG, "Found the meter service");
                finishRunningBlock(mPrimaryService.getUuid(), status, null);
                printGattError(status);
            } else {
                Log.e(TAG, "Did not find the meter service!");
                finishRunningBlock(null, status, null);
            }
            mCCService = getService(CC_SERVICE_UUID);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic c) {
            if(mNotifyCB.containsKey(c.getUuid())) {
                BLEUtilCB cb = mNotifyCB.get(c.getUuid());
                cb.uuid  = c.getUuid();
                cb.error = 0; // fixme
                cb.value = c.getValue();
                cb.run();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic c, int s) {
            finishRunningBlock(c.getUuid(), s, c.getValue());
            printGattError(s);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic c, int s) {
            finishRunningBlock(c.getUuid(), s, c.getValue());
            printGattError(s);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor c, int s) {
            finishRunningBlock(c.getUuid(), s, c.getValue());
            printGattError(s);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor c, int s) {
            finishRunningBlock(c.getUuid(), s, c.getValue());
            printGattError(s);
        }
    };
}
