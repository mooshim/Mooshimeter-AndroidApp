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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PeripheralWrapper {
    private static final String TAG="PeripheralWrapper";
    private static final Lock bleLock= new ReentrantLock();

    private static final Lock conditionLock= new ReentrantLock();

    private Context mContext;
    private boolean mTerminateFlag;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice mDevice;
    private BluetoothGattCallback mGattCallbacks;
    protected Map<UUID,BluetoothGattService> mServices;
    protected Map<UUID,BluetoothGattCharacteristic> mCharacteristics;
    private Map<UUID,Runnable> mNotifyCB;
    private HashMap<Integer, List<Runnable>> mConnectionStateCB;
    private HashMap<Integer, Runnable> mConnectionStateCBByHandle;

    private int connectionStateCBHandle = 0;

    private Thread mConnectionStateManagerThread;
    private Thread mNotificationManagerThread;

    private StatLockManager bleStateCondition    ;
    private StatLockManager bleDiscoverCondition ;
    private StatLockManager bleReadCondition     ;
    private StatLockManager bleWriteCondition    ;
    private StatLockManager bleChangedCondition  ;
    private StatLockManager bleDReadCondition    ;
    private StatLockManager bleDWriteCondition   ;
    private StatLockManager bleRWriteCondition   ;
    private StatLockManager bleRSSICondition     ;

    public int mRssi;
    public int mConnectionState;
    Queue<BluetoothGattCharacteristic> mNotifications;
    Queue<byte[]> mNotificationValues;

    private class Interruptable implements Callable<Void> {
        public int mRval = 0;
        @Override
        public Void call() throws InterruptedException {
            return null;
        }
    }

    // Anything that has to do with the BluetoothGatt needs to go through here
    private int protectedCall(Interruptable r) {
        if(mTerminateFlag) {
            Log.e(TAG,"Protected call made after terminate flag set!");
            Log.e(TAG, Log.getStackTraceString(new Exception()));
            return 1; }
        if(Util.inMainThread()) {
            Log.e(TAG,"Protected call made from main thread!  Not recommended!");
            Log.e(TAG, Log.getStackTraceString(new Exception()));
        }
        try {
            bleLock.lock();
            conditionLock.lock();
            r.call();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            conditionLock.unlock();
            bleLock.unlock();
            return r.mRval;
        }
    }

    private class StatLockManager {
        private int stat;
        private Condition con;
        private Lock lock;
        public StatLockManager(Lock l) {
            stat = 0;
            lock = l;
            con = l.newCondition();
        }
        public void l() {
            lock.lock();
        }
        public void l(int newstat) {
            l();
            stat = newstat;
        }
        public void ul() {
            lock.unlock();
        }
        public void sig() {
            con.signalAll();
        }
        public void await() {
            try {
                l();
                con.await();
                ul();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public PeripheralWrapper(final BluetoothDevice device, final Context context) {
        mTerminateFlag = false;
        mContext = context;
        mDevice = device;
        mConnectionState = BluetoothProfile.STATE_DISCONNECTED;

        mCharacteristics   = new HashMap<UUID, BluetoothGattCharacteristic>();
        mServices          = new HashMap<UUID, BluetoothGattService>();
        mNotifyCB          = new HashMap<UUID, Runnable>();
        mConnectionStateCB = new HashMap<Integer, List<Runnable>>();
        mConnectionStateCB.put(BluetoothProfile.STATE_DISCONNECTED,new ArrayList<Runnable>());
        mConnectionStateCB.put(BluetoothProfile.STATE_DISCONNECTING,new ArrayList<Runnable>());
        mConnectionStateCB.put(BluetoothProfile.STATE_CONNECTED,new ArrayList<Runnable>());
        mConnectionStateCB.put(BluetoothProfile.STATE_CONNECTING,new ArrayList<Runnable>());
        mConnectionStateCBByHandle = new HashMap<Integer, Runnable>();

        mNotifications = new LinkedList<BluetoothGattCharacteristic>();
        mNotificationValues = new LinkedList<byte[]>();

        bleStateCondition    = new StatLockManager(conditionLock);
        bleDiscoverCondition = new StatLockManager(conditionLock);
        bleReadCondition     = new StatLockManager(conditionLock);
        bleWriteCondition    = new StatLockManager(conditionLock);
        bleChangedCondition  = new StatLockManager(conditionLock);
        bleDReadCondition    = new StatLockManager(conditionLock);
        bleDWriteCondition   = new StatLockManager(conditionLock);
        bleRWriteCondition   = new StatLockManager(conditionLock);
        bleRSSICondition     = new StatLockManager(conditionLock);
        
        mGattCallbacks = new BluetoothGattCallback() {
            @Override public void onServicesDiscovered(BluetoothGatt g, int stat)                                 { bleDiscoverCondition.l(stat);                              bleDiscoverCondition.sig(); bleDiscoverCondition.ul();}
            @Override public void onConnectionStateChange(BluetoothGatt g, int stat, int newState)                { bleStateCondition   .l(stat); mConnectionState = newState; bleStateCondition   .sig(); bleStateCondition   .ul();}
            @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int stat)  { bleReadCondition    .l(stat);                              bleReadCondition    .sig(); bleReadCondition    .ul();}
            @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int stat) { bleWriteCondition   .l(stat);                              bleWriteCondition   .sig(); bleWriteCondition   .ul();}
            @Override public void onDescriptorRead(BluetoothGatt g, BluetoothGattDescriptor d, int stat)          { bleDReadCondition   .l(stat);                              bleDReadCondition   .sig(); bleDReadCondition   .ul();}
            @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int stat)         { bleDWriteCondition  .l(stat);                              bleDWriteCondition  .sig(); bleDWriteCondition  .ul();}
            @Override public void onReliableWriteCompleted(BluetoothGatt g, int stat)                             { bleRWriteCondition  .l(stat);                              bleRWriteCondition  .sig(); bleRWriteCondition  .ul();}
            @Override public void onReadRemoteRssi(BluetoothGatt g, int rssi, int stat)                           { bleRSSICondition    .l(stat); mRssi = rssi;                bleRSSICondition    .sig(); bleRSSICondition    .ul();}
            @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c)         {
                bleChangedCondition .l(   0);
                synchronized (mNotifications) {
                    synchronized (mNotificationValues) {
                        mNotificationValues.add(c.getValue().clone());
                        mNotifications.add(c);
                    }
                }
                bleChangedCondition .sig();
                bleChangedCondition .ul();}
        };
    }

    private void connectionStateManager() {
        // Meant to be run in a thread
        while(!mTerminateFlag) {
            bleStateCondition.await();
            if(mConnectionState==BluetoothGatt.STATE_DISCONNECTED) {
                mTerminateFlag = true; }
            List<Runnable> cbs = mConnectionStateCB.get(mConnectionState);
            for(Runnable cb : cbs) {
                cb.run();
            }
        }

    }

    private void notificationManager() {
        // Meant to be run in a thread
        while(!mTerminateFlag) {
            bleChangedCondition.await();
            synchronized (mNotifications) {
                synchronized (mNotificationValues) {
                    while(!mNotifications.isEmpty()) {
                        BluetoothGattCharacteristic c = mNotifications.remove();
                        c.setValue(mNotificationValues.remove());
                        Runnable cb = mNotifyCB.get(c.getUuid());
                        if (cb != null) {
                            cb.run();
                        }
                    }
                }
            }
        }
    }

    public int addConnectionStateCB(int state,Runnable cb) {
        List l = mConnectionStateCB.get(state);
        l.add(cb);
        mConnectionStateCBByHandle.put(connectionStateCBHandle,cb);
        connectionStateCBHandle++;
        return connectionStateCBHandle;
    }

    public void cancelConnectionStateCB(int handle) {
        Runnable cb = mConnectionStateCBByHandle.get(handle);
        mConnectionStateCBByHandle.remove(handle);
        if(cb!=null) {
            for( List<Runnable> l : mConnectionStateCB.values() ) {
                for( Runnable r : l ) {
                    if(r==cb) {
                        l.remove(r);
                        return;
                    }
                }
            }
        }
    }

    public BluetoothGattCharacteristic getChar(UUID uuid) {
        return mCharacteristics.get(uuid);
    }

    public int connect() {
        if( mConnectionState == BluetoothProfile.STATE_CONNECTED ) {
            return 0;
        }
        if( mConnectionState == BluetoothProfile.STATE_CONNECTING ) {
            return 0;
        }
        mTerminateFlag = false;
        mConnectionStateManagerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                connectionStateManager();
            }
        });
        mConnectionStateManagerThread.start();
        mNotificationManagerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                notificationManager();
            }
        });
        mNotificationManagerThread.start();

        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                // Try to connect
                mBluetoothGatt = mDevice.connectGatt(mContext.getApplicationContext(),false,mGattCallbacks);
                refreshDeviceCache();

                // TODO: Implement timeout
                while( mConnectionState != BluetoothProfile.STATE_CONNECTED ) {
                    bleStateCondition.await();
                    if(bleStateCondition.stat != 0) {break;}
                }
                mRval = bleStateCondition.stat;
                return null;
            }
        });
    }

    public int discover() {
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                // Discover services
                mBluetoothGatt.discoverServices();
                bleDiscoverCondition.await();
                // Build a local dictionary of all characteristics and their UUIDs
                for( BluetoothGattService s : mBluetoothGatt.getServices() ) {
                    mServices.put(s.getUuid(),s);
                    for( BluetoothGattCharacteristic c : s.getCharacteristics() ) {
                        mCharacteristics.put(c.getUuid(),c);
                    }
                }
                mRval = bleDiscoverCondition.stat;
                return null;
            }
        });
    }

    public int disconnect() {
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                mBluetoothGatt.disconnect();
                while( mConnectionState != BluetoothProfile.STATE_DISCONNECTED ) {
                    bleStateCondition.await();
                }
                mTerminateFlag = true;
                mRssi = bleStateCondition.stat;
                return null;
            }
        });
    }

    public byte[] req(UUID uuid) {
        final BluetoothGattCharacteristic c = getChar(uuid);
        protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                mBluetoothGatt.readCharacteristic(c);
                bleReadCondition.await();
                return null;
            }
        });
        return c.getValue();
    }

    public int send(final UUID uuid, final byte[] value) {
        final BluetoothGattCharacteristic c = mCharacteristics.get(uuid);
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                c.setValue(value);
                mBluetoothGatt.writeCharacteristic(c);
                bleWriteCondition.await();
                mRval = bleWriteCondition.stat;
                return null;
            }
        });
    }

    public boolean isNotificationEnabled(BluetoothGattCharacteristic c) {
        final BluetoothGattDescriptor d = c.getDescriptor(GattInfo.CLIENT_CHARACTERISTIC_CONFIG);
        final byte[] dval = d.getValue();
        return (dval == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
    }

    public int enableNotify(final UUID uuid, final boolean enable, final Runnable on_notify) {
        final BluetoothGattCharacteristic c = mCharacteristics.get(uuid);
        // Set up the notify callback
        if(on_notify != null) {
            mNotifyCB.put(uuid, on_notify);
        } else {
            mNotifyCB.remove(uuid);
        }
        if(isNotificationEnabled(c) != enable) {
            return protectedCall(new Interruptable() {
                @Override
                public Void call() throws InterruptedException {
                    // Only bother setting the notification if the status has changed
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
                        bleDWriteCondition.await();
                    }
                    mRval = bleDWriteCondition.stat;
                    return null;
                }
            });
        }
        return 0;
    }

    public void setWriteType(UUID uuid, int wtype) {
        // TODO: This needs proper locks around it
        final BluetoothGattCharacteristic c = mCharacteristics.get(uuid);
        c.setWriteType(wtype);
    }

    public String getAddress() {
        return mDevice.getAddress();
    }

    public BluetoothDevice getBLEDevice() {
        return mDevice;
    }

    private boolean refreshDeviceCache(){
        // Forces the BluetoothGATT layer to dump what it knows about the connected device
        // If this is not called during connection, the GATT layer will simply return the last cached
        // services and refuse to do the service discovery process.
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
}