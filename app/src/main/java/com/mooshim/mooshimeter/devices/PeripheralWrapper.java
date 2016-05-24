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

package com.mooshim.mooshimeter.devices;

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

import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.StatLockManager;
import com.mooshim.mooshimeter.common.Util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class PeripheralWrapper {
    private static final String TAG="PeripheralWrapper";
    private static final ReentrantLock bleLock= new ReentrantLock(true);
    private static final ReentrantLock conditionLock= new ReentrantLock(true);

    protected Context mContext;
    private BluetoothGatt mBluetoothGatt;
    protected BluetoothDevice mDevice;
    private BluetoothGattCallback mGattCallbacks;
    protected Map<UUID,BluetoothGattService> mServices;
    protected Map<UUID,BluetoothGattCharacteristic> mCharacteristics;
    private Map<UUID,NotifyHandler> mNotifyCB;
    private final Map<Integer, List<Runnable>> mConnectionStateCB;
    private Map<Integer, Runnable> mConnectionStateCBByHandle;

    private int connectionStateCBHandle = 0;

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

    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private class Interruptable implements Callable<Void> {
        public int mRval = 0;
        @Override
        public Void call() throws InterruptedException {
            return null;
        }
    }

    // Anything that has to do with the BluetoothGatt needs to go through here
    private int protectedCall(final Interruptable r,boolean force_main_thread) {
        if(Util.onCBThread()) {
            Log.e(TAG,"DON'T DO BLE STUFF FROM THE CB THREAD!");
            new Exception().printStackTrace();
        }
        Runnable payload = new Runnable() {
            @Override
            public void run() {
                try {
                    if(bleLock.isLocked() && !bleLock.isHeldByCurrentThread()) {
                        Log.d(TAG,"WAITING ON bleLock");
                    }
                    bleLock.lock();
                    if(conditionLock.isLocked() && !conditionLock.isHeldByCurrentThread()) {
                        Log.d(TAG,"WAITING ON conditionLock");
                    }
                    conditionLock.lock();
                    Log.d(TAG, "MAKING PROTECTED CALL");
                    r.call();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    boolean released = false;
                    if(conditionLock.isHeldByCurrentThread()) {
                        conditionLock.unlock();
                        released = true;
                    }
                    if(bleLock.isHeldByCurrentThread()) {
                        bleLock.unlock();
                        released = true;
                    }
                    if(released) {
                        Log.d(TAG,"RELEASED");
                    }
                }
            }
        };
        if(force_main_thread) {
            Util.blockUntilRunOnMainThread(payload);
        } else {
            payload.run();
        }
        return r.mRval;
    }

    private int protectedCall(final Interruptable r) {
        return protectedCall(r,false);
    }
    
    public PeripheralWrapper(final BluetoothDevice device, final Context context) {
        mContext = context;
        mDevice = device;
        mConnectionState = BluetoothProfile.STATE_DISCONNECTED;

        mCharacteristics   = new ConcurrentHashMap<>();
        mServices          = new ConcurrentHashMap<>();
        mNotifyCB          = new ConcurrentHashMap<>();
        mConnectionStateCB = new ConcurrentHashMap<>();
        mConnectionStateCB.put(BluetoothProfile.STATE_DISCONNECTED,new ArrayList<Runnable>());
        mConnectionStateCB.put(BluetoothProfile.STATE_DISCONNECTING,new ArrayList<Runnable>());
        mConnectionStateCB.put(BluetoothProfile.STATE_CONNECTED,new ArrayList<Runnable>());
        mConnectionStateCB.put(BluetoothProfile.STATE_CONNECTING,new ArrayList<Runnable>());
        mConnectionStateCBByHandle = new ConcurrentHashMap<>();

        bleStateCondition    = new StatLockManager(conditionLock,"STATE");
        bleDiscoverCondition = new StatLockManager(conditionLock,"DISCO");
        bleReadCondition     = new StatLockManager(conditionLock,"READ ");
        bleWriteCondition    = new StatLockManager(conditionLock,"WRITE");
        bleChangedCondition  = new StatLockManager(conditionLock,"CHANG");
        bleDReadCondition    = new StatLockManager(conditionLock,"DREAD");
        bleDWriteCondition   = new StatLockManager(conditionLock,"DWRIT");
        bleRWriteCondition   = new StatLockManager(conditionLock,"RWRIT");
        bleRSSICondition     = new StatLockManager(conditionLock,"RSSI ");
        
        mGattCallbacks = new BluetoothGattCallback() {
            @Override public void onServicesDiscovered(BluetoothGatt g, int stat)                                 { Log.d(TAG,"GATTCB:DISCOVER");bleDiscoverCondition.l(stat);               bleDiscoverCondition.sig(); bleDiscoverCondition.ul();}
            @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int stat)  { Log.d(TAG,"GATTCB:READ");    bleReadCondition    .l(stat);               bleReadCondition    .sig(); bleReadCondition    .ul();}
            @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int stat) { Log.d(TAG,"GATTCB:WRITE");   bleWriteCondition   .l(stat);               bleWriteCondition   .sig(); bleWriteCondition   .ul();}
            @Override public void onDescriptorRead(BluetoothGatt g, BluetoothGattDescriptor d, int stat)          { Log.d(TAG,"GATTCB:DREAD");   bleDReadCondition   .l(stat);               bleDReadCondition   .sig(); bleDReadCondition   .ul();}
            @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int stat)         { Log.d(TAG,"GATTCB:DWRITE");  bleDWriteCondition  .l(stat);               bleDWriteCondition  .sig(); bleDWriteCondition  .ul();}
            @Override public void onReliableWriteCompleted(BluetoothGatt g, int stat)                             { Log.d(TAG,"GATTCB:RWRITE");  bleRWriteCondition  .l(stat);               bleRWriteCondition  .sig(); bleRWriteCondition  .ul();}
            @Override public void onReadRemoteRssi(BluetoothGatt g, int rssi, int stat)                           { Log.d(TAG,"GATTCB:RSSI");    bleRSSICondition    .l(stat); mRssi = rssi; bleRSSICondition    .sig(); bleRSSICondition    .ul();}
            @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c)         { Log.d(TAG,"GATTCB:CCHANGE");
                bleChangedCondition .l(0);
                final byte[] val = c.getValue();
                // The BLE stack sometimes gives us a null here, unclear why.
                if( val != null ) {
                    final NotifyHandler cb = mNotifyCB.get(c.getUuid());
                    if (cb != null) {
                        final byte[] payload = val.clone();
                        final double timestamp = Util.getNanoTime();
                        Util.dispatchCb(new Runnable() {
                            @Override
                            public void run() {
                                cb.onReceived(timestamp, payload);
                            }
                        });
                    }
                }
                bleChangedCondition .ul();
            }
            @Override
            public void onConnectionStateChange(BluetoothGatt g, int stat, int newState) {
                Log.d(TAG, "GATTCB:CONN");
                bleStateCondition   .l(stat);
                mConnectionState = newState;
                switch(newState) {
                    case BluetoothProfile.STATE_DISCONNECTED:
                        Log.d(TAG,"New state: Disconnected");
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        Log.d(TAG,"New state: Connecting");
                        break;
                    case BluetoothProfile.STATE_CONNECTED:
                        Log.d(TAG,"New state: Connected");
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                        Log.d(TAG,"New state: Disconnecting");
                        break;
                }
                synchronized (mConnectionStateCB) {
                    List<Runnable> cbs = mConnectionStateCB.get(mConnectionState);
                    for(Runnable cb : cbs) {
                        Util.dispatchCb(cb);
                    }
                }
                bleStateCondition   .sig();
                bleStateCondition   .ul();
            }
        };
    }

    public int addConnectionStateCB(int state,Runnable cb) {
        synchronized (mConnectionStateCB) {
            connectionStateCBHandle++;
            List<Runnable> l = mConnectionStateCB.get(state);
            l.add(cb);
            mConnectionStateCBByHandle.put(connectionStateCBHandle, cb);
        }
        return connectionStateCBHandle;
    }

    public void cancelConnectionStateCB(int handle) {
        synchronized (mConnectionStateCB) {
            Runnable cb = mConnectionStateCBByHandle.get(handle);
            mConnectionStateCBByHandle.remove(handle);
            if (cb != null) {
                for (List<Runnable> l : mConnectionStateCB.values()) {
                    for (Runnable r : l) {
                        if (r == cb) {
                            l.remove(r);
                            return;
                        }
                    }
                }
            }
        }
    }

    public boolean isConnected() {
        return (mConnectionState == BluetoothProfile.STATE_CONNECTED);
    }

    public boolean isConnecting() {
        return (mConnectionState == BluetoothProfile.STATE_CONNECTING);
    }

    public boolean isDisconnected() {
        return ((mConnectionState == BluetoothProfile.STATE_DISCONNECTED) || (mConnectionState == BluetoothProfile.STATE_DISCONNECTING));
    }

    public BluetoothGattCharacteristic getChar(UUID uuid) {
        return mCharacteristics.get(uuid);
    }

    public int connect() {
        if( isConnected() || isConnecting()) {
            return 0;
        }

        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                if(BluetoothAdapter.getDefaultAdapter().isDiscovering()) {
                    Log.e(TAG,"Trying to connect while the adapter is discovering!  Going to cancelDelayedCB discovery.");
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                }
                // Try to connect
                Log.d(TAG,"CONNECTGATT");
                mBluetoothGatt = mDevice.connectGatt(mContext.getApplicationContext(),false,mGattCallbacks);
                refreshDeviceCache();
                while (!isConnected()) {
                    //If we time out in connection or the connect routine returns an error
                    if (bleStateCondition.awaitMilli(10000) ) {
                        if(mBluetoothGatt!=null) {
                            mBluetoothGatt.close();
                        }
                        mRval = -1;
                        return null;
                    }
                    if (bleStateCondition.stat != 0) {
                        if(mBluetoothGatt!=null) {
                            mBluetoothGatt.close();
                        }
                        mRval = bleStateCondition.stat;
                        return null;
                    }
                }
                return null;
            }
        });
    }

    public int discover() {
        if(!isConnected()) {
            new Exception().printStackTrace();
            return -1;
        }
        protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                // Discover services
                Log.d(TAG,"DISCOVER");
                while(!mBluetoothGatt.discoverServices()) {
                    Log.e(TAG,"DISCOVER FAILED TO START");
                }
                if(bleDiscoverCondition.awaitMilli(10000)) {
                    //if(bleDiscoverCondition.await()) {
                    // Timed out
                    Log.e(TAG,"Timed out on service discovery");
                    mRval = -1;
                }
                return null;
            }
        });
        // Build a local dictionary of all characteristics and their UUIDs
        if(bleDiscoverCondition.stat == 0) {
            for (BluetoothGattService s : mBluetoothGatt.getServices()) {
                mServices.put(s.getUuid(), s);
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    mCharacteristics.put(c.getUuid(), c);
                }
            }
            Log.d(TAG,"Characteristic map has " + mCharacteristics.size() + " elements");
        } else {
            // This is a shot in the dark trying to fix a longstanding Android BLE bug
            // After a failed discover (reason for failure unknown, code 129), the phone
            // maintains a phantom connection to the Mooshimeter.  This is bad because the Mooshimeter stops
            // advertising and no longer appears in the scan list and the only way to disconnect
            // is to reboot the mooshimeter or the phone (cycling BLE doesn't help).
            //refreshDeviceCache();
            Log.e(TAG, "Discover status: " + bleDiscoverCondition.stat);
        }
        return bleDiscoverCondition.stat;
    }

    public int disconnect() {
        if(isDisconnected()) {
            Log.d(TAG, "Disconnect called on peripheral that's already disconnected!");
            return 0;
        }
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                Log.d(TAG, "DISCONNECT");
                mBluetoothGatt.disconnect();
                while (mConnectionState != BluetoothProfile.STATE_DISCONNECTED) {
                    bleStateCondition.await();
                }
                Log.d(TAG, "CLOSE");
                mBluetoothGatt.close();
                return null;
            }
        });
    }

    public int reqRSSI() {
        protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                if (isConnected()) {
                    Log.d(TAG, "READRSSI");
                    mBluetoothGatt.readRemoteRssi();
                    if(bleRSSICondition.awaitMilli(500)) {
                        Log.e(TAG, "RSSI read timed out!");
                    }
                }
                return null;
            }
        });
        return mRssi;
    }

    public byte[] req(UUID uuid) {
        if(!isConnected()) {
            Log.e(TAG,"Trying to read from a disconnected peripheral");
            new Exception().printStackTrace();
            return null;
        }
        final BluetoothGattCharacteristic c = getChar(uuid);
        if(c==null) {
            Log.e(TAG,"Couldn't find char for " + uuid.toString());
            return null;
        }
        protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                Log.d(TAG,"READ");
                mBluetoothGatt.readCharacteristic(c);
                if(bleReadCondition.awaitMilli(1000)) {
                    mRval = -1;
                } else {
                    mRval = bleReadCondition.stat;
                }
                return null;
            }
        });
        return c.getValue();
    }

    public int send(final UUID uuid, final byte[] value) {
        if(!isConnected()) {
            Log.e(TAG,"Trying to send to a disconnected peripheral");
            new Exception().printStackTrace();
            return -1;
        }
        final BluetoothGattCharacteristic c = getChar(uuid);
        if(c==null) {
            Log.e(TAG, "Couldn't find write characteristic for "+uuid.toString());
            return -1;
        }
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                Log.d(TAG, "WRITE");
                c.setValue(value);
                mBluetoothGatt.writeCharacteristic(c);
                if (bleWriteCondition.awaitMilli(1000)) {
                    mRval = -1;
                } else {
                    mRval = bleWriteCondition.stat;
                }
                return null;
            }
        });
    }

    public NotifyHandler getNotificationCallback(UUID uuid) {
        return mNotifyCB.get(uuid);
    }

    public boolean isNotificationEnabled(UUID uuid) {
        if(!isConnected()) {
            Log.e(TAG,"Trying to read notification on a disconnected peripheral");
            new Exception().printStackTrace();
            return false;
        }
        BluetoothGattCharacteristic c = getChar(uuid);
        if(c == null) {
            Log.e(TAG, "Asked for a characteristic that doesn't exist!");
            return false;
        }
        final BluetoothGattDescriptor d = c.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
        final byte[] dval = d.getValue();
        return (dval == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
    }

    private Map<UUID,Boolean> notification_disable_preempted = new ConcurrentHashMap<UUID, Boolean>();

    private int enableNotifyDirect(final UUID uuid, final boolean enable) {
        final BluetoothGattCharacteristic c = getChar(uuid);
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                // Only bother setting the notification if the status has changed
                if (mBluetoothGatt.setCharacteristicNotification(c, enable)) {
                    final BluetoothGattDescriptor clientConfig = c.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                    final byte[] enable_val = enable?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                    while(!clientConfig.setValue(enable_val)) {
                        Log.e(TAG, "setValue Fail!");
                    }
                    Log.d(TAG, "DWRITE");
                    mBluetoothGatt.writeDescriptor(clientConfig);
                    if(bleDWriteCondition.awaitMilli(3000)) {
                        Log.e(TAG, "writeDescriptor timed out!");
                        mRval = -1;
                    } else {
                        mRval = bleDWriteCondition.stat;
                        if(mRval != 0) {
                            Log.e(TAG,"DWRITE RVAL "+mRval);
                        }
                    }
                } else {
                    mRval = 0;
                }
                return null;
            }
        });
    }

    public int enableNotify(final UUID uuid, final boolean enable, final NotifyHandler on_notify) {
        if(!isConnected()) {
            Log.e(TAG,"Trying to set notification on a disconnected peripheral");
            new Exception().printStackTrace();
            return -1;
        }
        // Set up the notify callback
        if(on_notify != null) {
            mNotifyCB.put(uuid, on_notify);
        } else {
            mNotifyCB.remove(uuid);
        }
        if(enable) {
            notification_disable_preempted.put(uuid,Boolean.TRUE);
        }
        if(isNotificationEnabled(uuid) != enable) {
            if(enable) {
                // Enable immediately
                enableNotifyDirect(uuid,true);
            } else {
                // Disable only after a delay
                notification_disable_preempted.put(uuid,Boolean.FALSE);
                Util.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(notification_disable_preempted.get(uuid).equals(Boolean.TRUE)) {
                            // If the disable was preempted, don't disable
                            return;
                        }
                        enableNotifyDirect(uuid, false);
                    }
                }, 3000);
            }
        }
        return 0;
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
            Method localMethod = mBluetoothGatt.getClass().getMethod("refresh");
            if (localMethod != null) {
                final boolean b = ((Boolean) localMethod.invoke(mBluetoothGatt)).booleanValue();
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