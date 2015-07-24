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
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PeripheralWrapper {
    private static final String TAG="PeripheralWrapper";
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice mDevice;
    private String mAddress;
    private BluetoothGattCallback mGattCallbacks;
    private Map<UUID,BluetoothGattCharacteristic> mCharacteristics;
    private Map<UUID,Runnable> mNotifyCB;
    private Map<Integer,Runnable> mConnectionStateCB;

    private Thread mConnectionStateManagerThread;
    private Thread mNotificationManagerThread;

    private Condition bleStateCondition    ;
    private Condition bleDiscoverCondition ;
    private Condition bleReadCondition     ;
    private Condition bleWriteCondition    ;
    private Condition bleChangedCondition  ;
    private Condition bleDReadCondition    ;
    private Condition bleDWriteCondition   ;
    private Condition bleRWriteCondition   ;
    private Condition bleRSSICondition     ;

    public static final Lock bleLock= new ReentrantLock();
    private static void lock()   { bleLock.lock(); }
    private static void unlock() { bleLock.unlock(); }

    BluetoothGattCharacteristic mChangedChar;

    private class Interruptable implements Callable<Void> {
        @Override
        public Void call() throws InterruptedException {
            return null;
        }
    }

    // Anything that has to do with the BluetoothGatt needs to go through here
    private void protectedCall(Interruptable r) {
        try {
            lock();
            r.call();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            unlock();
        }
    }

    public PeripheralWrapper(final String address, final Context context) {
        mAddress = address;
        mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);

        mCharacteristics   = new HashMap<UUID, BluetoothGattCharacteristic>();
        mNotifyCB          = new HashMap<UUID, Runnable>();
        mConnectionStateCB = new HashMap<Integer, Runnable>();

        bleStateCondition    = new ReentrantLock().newCondition();
        bleDiscoverCondition = new ReentrantLock().newCondition();
        bleReadCondition     = new ReentrantLock().newCondition();
        bleWriteCondition    = new ReentrantLock().newCondition();
        bleChangedCondition  = new ReentrantLock().newCondition();
        bleDReadCondition    = new ReentrantLock().newCondition();
        bleDWriteCondition   = new ReentrantLock().newCondition();
        bleRWriteCondition   = new ReentrantLock().newCondition();
        bleRSSICondition     = new ReentrantLock().newCondition();

        mGattCallbacks = new BluetoothGattCallback() {
            @Override public void onServicesDiscovered(BluetoothGatt g, int stat)                                 { bleDiscoverCondition.signal(); }
            @Override public void onConnectionStateChange(BluetoothGatt g, int stat, int newState)                { bleStateCondition   .signal(); }
            @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int stat)  { bleReadCondition    .signal(); }
            @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int stat) { bleWriteCondition   .signal(); }
            @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c)         { mChangedChar=c; bleChangedCondition.signal(); }
            @Override public void onDescriptorRead(BluetoothGatt g, BluetoothGattDescriptor d, int stat)          { bleDReadCondition   .signal(); }
            @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int stat)         { bleDWriteCondition  .signal(); }
            @Override public void onReliableWriteCompleted(BluetoothGatt g, int stat)                             { bleRWriteCondition  .signal(); }
            @Override public void onReadRemoteRssi(BluetoothGatt g, int rssi, int stat)                           { bleRSSICondition    .signal(); }
        };

        protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                // Try to connect and discover
                mBluetoothGatt = mDevice.connectGatt(context.getApplicationContext(),false,mGattCallbacks);
                refreshDeviceCache();
                while( mBluetoothGatt.getConnectionState(mDevice) != BluetoothProfile.STATE_CONNECTED ) {
                    bleStateCondition.await();
                }
                // Discover services
                mBluetoothGatt.discoverServices();
                bleDiscoverCondition.await();
                // Build a local dictionary of all characteristics and their UUIDs
                for( BluetoothGattService s : mBluetoothGatt.getServices() ) {
                    for( BluetoothGattCharacteristic c : s.getCharacteristics() ) {
                        mCharacteristics.put(c.getUuid(),c);
                    }
                }

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

                return null;
            }
        });
    }

    private void connectionStateManager() {
        // Meant to be run in a thread
        protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                while(true) {
                    bleChangedCondition.await();
                    int state = mBluetoothGatt.getConnectionState(mDevice);
                    Runnable cb = mConnectionStateCB.get(state);
                    if(cb != null) {
                        cb.run();
                    }
                }
            }
        });
    }

    private void notificationManager() {
        // Meant to be run in a thread
        protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                while(true) {
                    bleChangedCondition.await();
                    Runnable cb = mNotifyCB.get(mChangedChar.getUuid());
                    if(cb != null) {
                        cb.run();
                    }
                }
            }
        });
    }

    public void disconnect() {
        protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                mBluetoothGatt.disconnect();
                return null;
            }
        });
    }

    public void req(UUID uuid) {
        final BluetoothGattCharacteristic c = mCharacteristics.get(uuid);
        protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                mBluetoothGatt.readCharacteristic(c);
                bleReadCondition.await();
                return null;
            }
        });
    }

    public void send(final UUID uuid, final byte[] value) {
        final BluetoothGattCharacteristic c = mCharacteristics.get(uuid);
        protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                c.setValue(value);
                mBluetoothGatt.writeCharacteristic(c);
                bleWriteCondition.await();
                return null;
            }
        });
    }

    public boolean isNotificationEnabled(BluetoothGattCharacteristic c) {
        final BluetoothGattDescriptor d = c.getDescriptor(GattInfo.CLIENT_CHARACTERISTIC_CONFIG);
        final byte[] dval = d.getValue();
        return (dval == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
    }

    public void enableNotify(final UUID uuid, final boolean enable, final Runnable on_notify) {
        final BluetoothGattCharacteristic c = mCharacteristics.get(uuid);
        // Set up the notify callback
        if(on_notify != null) {
            mNotifyCB.put(uuid, on_notify);
        } else {
            mNotifyCB.remove(uuid);
        }
        if(isNotificationEnabled(c) != enable) {
            protectedCall(new Interruptable() {
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
                    return null;
                }
            });
        }
    }

    public void setWriteType(UUID uuid, int wtype) {
        final BluetoothGattCharacteristic c = mCharacteristics.get(uuid);
        c.setWriteType(wtype);
    }

    public String getBTAddress() {
        return mBluetoothGatt.getDevice().getAddress();
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