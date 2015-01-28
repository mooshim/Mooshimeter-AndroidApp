package com.mooshim.mooshimeter.common;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.mooshim.mooshimeter.main.SensorTagGatt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

/**
 * Created by JWHONG on 1/26/2015.
 * The purpose of this class is to pace all interactions with the BLE layer to one request at a time.
 * Anything more than that and everything gets buggy.
 */
public class BLEUtil {
    private static final String TAG="BLEUtil";
    private Context mContext;
    private BluetoothLeService bt_service;
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
        protected BLEUtilRequest() {};
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
            mInstance.mContext.unregisterReceiver(mInstance.mGattUpdateReceiver);
            mInstance.close();
            mInstance = null;
        }
    }

    protected BLEUtil(Context context) {
        // Initialize internal structures
        mContext = context;
        bt_service = BluetoothLeService.getInstance();
        if(bt_service != null) {
            // Get the GATT service
            bt_gatt_service = null;
            for( BluetoothGattService s : bt_service.getSupportedGattServices() ) {
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
        }
        // FIXME:  I am unhappy with the way this class and DeviceActivity are structured
        // There are a lot of interdependencies that make them complicated to work with.
        // But I don't want to change too many things at once.
        final IntentFilter fi = new IntentFilter();
        fi.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        fi.addAction(BluetoothLeService.ACTION_DATA_NOTIFY);
        fi.addAction(BluetoothLeService.ACTION_DATA_WRITE);
        fi.addAction(BluetoothLeService.ACTION_DESCRIPTOR_WRITE);
        fi.addAction(BluetoothLeService.ACTION_DATA_READ);
        context.registerReceiver(mGattUpdateReceiver, fi);
        mContext = context;
    }

    public void close() {
        mExecuteQueue.clear();
        mRunning = null;
    }

    ////////////////////////////////
    // Accessors
    ////////////////////////////////

    synchronized private void serviceExecuteQueue(BLEUtilRequest add) {
        if(add != null) {
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

    public void req(UUID uuid, BLEUtilCB on_complete) {
        final BluetoothGattCharacteristic c = bt_gatt_service.getCharacteristic(uuid);
        BLEUtilRequest r = new BLEUtilRequest(new Runnable() {
            @Override
            public void run() {
                bt_service.readCharacteristic(c);
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
                bt_service.writeCharacteristic(c);
            }
        }, on_complete);
        serviceExecuteQueue(r);
    }
    public void enableNotify(final UUID uuid, final boolean enable, final BLEUtilCB on_complete, final BLEUtilCB on_notify) {
        final BluetoothGattCharacteristic c = bt_gatt_service.getCharacteristic(uuid);
        // Set up the notify callback
        if(on_notify != null) {
            mNotifyCB.put(uuid, on_notify);
        } else {
            mNotifyCB.remove(uuid);
        }
        if(bt_service.isNotificationEnabled(c) != enable) {
            // Only bother setting the notification if the status has changed
            BLEUtilRequest r = new BLEUtilRequest(new Runnable() {
                @Override
                public void run() {
                    bt_service.setCharacteristicNotification(c,enable);
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

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            int status          = intent.getIntExtra(      BluetoothLeService.EXTRA_STATUS, BluetoothGatt.GATT_SUCCESS);
            String uuidStr      = intent.getStringExtra(   BluetoothLeService.EXTRA_UUID);
            byte[] value        = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

            UUID uuid      = UUID.fromString(uuidStr);

            if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "onServiceDiscovery");
            } else if ( BluetoothLeService.ACTION_DATA_READ.equals(action) ) {
                Log.d(TAG, "onCharacteristicRead");
                finishRunningBlock(uuid, status, value);
            } else if ( BluetoothLeService.ACTION_DATA_NOTIFY.equals(action) ) {
                Log.d(TAG, "onCharacteristicNotify");
                if(mNotifyCB.containsKey(uuid)) {
                    BLEUtilCB cb = mNotifyCB.get(uuid);
                    cb.uuid  = uuid;
                    cb.error = status;
                    cb.value = value;
                    cb.run();
                }
            } else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action)) {
                Log.d(TAG, "onCharacteristicWrite");
                finishRunningBlock(uuid, status, value);
            } else if (BluetoothLeService.ACTION_DESCRIPTOR_WRITE.equals(action)) {
                Log.d(TAG, "onDescriptorWrite");
                finishRunningBlock(uuid, status, value);
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "GATT error code: " + status);
            }
        }
    };
}
