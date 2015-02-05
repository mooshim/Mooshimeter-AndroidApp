package com.mooshim.mooshimeter.common;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

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
    public final static String ACTION_GATT_DISCONNECTED         = "com.mooshim.mooshimeter.ACTION_GATT_DISCONNECTED";
    private static final String TAG="BLEUtil";

    private Context              mContext;
    private BluetoothAdapter     mBtAdapter;
    private BluetoothGatt        mBluetoothGatt;
    private BluetoothGattService bt_gatt_service;

    private LinkedList<BLEUtilRequest> mExecuteQueue = new LinkedList<BLEUtilRequest>();
    private BLEUtilRequest mRunning = null;
    private HashMap<UUID,BLEUtilCB> mNotifyCB= new HashMap<UUID,BLEUtilCB>();

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
            mInstance.close();
            mInstance = null;
        }
    }

    protected BLEUtil(Context context) {
        // Initialize internal structures
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        mContext = context;
    }

    public void close() {
        mExecuteQueue.clear();
        mNotifyCB.clear();
        mRunning = null;
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
        if(mRunning == null) {
            if(!mExecuteQueue.isEmpty()) {
                mRunning = mExecuteQueue.remove();
                mRunning.payload.run();
            }
        }
    }
    synchronized private void finishRunningBlock(final UUID uuid, final int error, final byte[] value) {
        if(mRunning==null) {
            Log.e(TAG,"ERROR: Asked to finish a task but no task running");
            return;
        }
        final BLEUtilCB cb = mRunning.callback;
        mRunning = null;
        if(cb == null) {
            return;
        }
        cb.uuid  = uuid;
        cb.error = error;
        cb.value = value;
        cb.run();
        serviceExecuteQueue(null);
    }

    ////////////////////////////////
    // Accessors
    ////////////////////////////////

    public void connect(final String address, final BLEUtilCB cb) {
        BLEUtilRequest r = new BLEUtilRequest(new Runnable() {
            @Override
            public void run() {
                final BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
                mBluetoothGatt = device.connectGatt(mContext,false,mGattCallbacks);
            }
        }, cb);
        serviceExecuteQueue(r);
    }

    public void disconnect() {
        mBluetoothGatt.disconnect();
        bt_gatt_service = null;
    }

    public void req(UUID uuid, BLEUtilCB on_complete) {
        final BluetoothGattCharacteristic c = bt_gatt_service.getCharacteristic(uuid);
        BLEUtilRequest r = new BLEUtilRequest(new Runnable() {
            @Override
            public void run() {
                mBluetoothGatt.readCharacteristic(c);
            }
        }, on_complete);
        serviceExecuteQueue(r);
    }
    public void send(final UUID uuid, final byte[] value, final BLEUtilCB on_complete) {
        final BluetoothGattCharacteristic c = bt_gatt_service.getCharacteristic(uuid);
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
        final BluetoothGattCharacteristic c = bt_gatt_service.getCharacteristic(uuid);
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

    private static void printGattError(int s) {
        if (s != BluetoothGatt.GATT_SUCCESS) {
            // GATT Error 133 seems to be coming up from time to time.  I don't think we're doing anything wrong,
            // just instability in the Android stack...
            Log.e(TAG, "GATT error code: " + s);
        }
    }

    private void broadcastUpdate(final String action, final String address,
                                 final int status) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_ADDRESS, address);
        intent.putExtra(EXTRA_STATUS, status);
        mContext.sendBroadcast(intent);
    }

    private BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            if (mBluetoothGatt == null) {
                // Log.e(TAG, "mBluetoothGatt not created!");
                return;
            }

            BluetoothDevice device = gatt.getDevice();

            try {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        mBluetoothGatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        String address = device.getAddress();
                        broadcastUpdate(ACTION_GATT_DISCONNECTED, address, status);
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
            for( BluetoothGattService s : mBluetoothGatt.getServices() ) {
                // FIXME: Should be able to specify the service to attach to instead of hardcode
                if(s.getUuid().equals(MooshimeterDevice.mUUID.METER_SERVICE)) {
                    Log.i(TAG, "Found the meter service");
                    bt_gatt_service = s;
                    break;
                }
            }
            if(bt_gatt_service == null) {
                Log.e(TAG, "Did not find the meter service!");
            }
            finishRunningBlock(bt_gatt_service.getUuid(), status, null);
            printGattError(status);
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
