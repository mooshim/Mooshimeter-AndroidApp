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

import android.content.Context;
import android.util.Log;

import com.idevicesinc.sweetblue.BleDevice;
import com.idevicesinc.sweetblue.BleDeviceState;
import com.idevicesinc.sweetblue.BleNode;
import com.idevicesinc.sweetblue.DeviceStateListener;

import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.StatLockManager;
import com.mooshim.mooshimeter.common.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

public class PeripheralWrapper {
    private static final String TAG="PeripheralWrapper";

    private StatLockManager mConnStateLock;

    protected Context mContext;
    protected BleDevice mDevice;

    private final List<Runnable> mConnectCBs;
    private final List<Runnable> mDisconnectCBs;

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
        if(Util.inMainThread()) {
            Log.e(TAG,"PROTECTED CALL FROM MAIN THREAD!");
        }
        Runnable payload = new Runnable() {
            @Override
            public void run() {
                try {
                    r.call();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
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
    
    public PeripheralWrapper(final BleDevice device, final Context context) {
        mContext = context;
        mDevice = device;

        mConnStateLock = new StatLockManager();

        mConnectCBs    = new ArrayList<>();
        mDisconnectCBs = new ArrayList<>();

        mDevice.setListener_State(new DeviceStateListener() {
            @Override
            public void onEvent(BleDevice.StateListener.StateEvent e) {
                mConnStateLock.sig();
                if(e.didEnter(BleDeviceState.ADVERTISING)) {
                    for(Runnable cb : mDisconnectCBs) {
                        Util.dispatchCb(cb);
                    }
                }
                if(e.didEnter(BleDeviceState.INITIALIZED)) {
                    for(Runnable cb : mConnectCBs) {
                        Util.dispatchCb(cb);
                    }
                }
            }
        });
        mDevice.setListener_ConnectionFail(new BleDevice.ConnectionFailListener() {
            @Override
            public Please onEvent(ConnectionFailEvent e) {
                return null;
            }
        });
    }

    public void addConnectCB(Runnable cb) {
        synchronized (mConnectCBs) {
            mConnectCBs.add(cb);
        }
    }

    public void cancelConnectCB(Runnable cb) {
        synchronized (mConnectCBs) {
            if(mConnectCBs.contains(cb)) {
                mConnectCBs.remove(cb);
            }
        }
    }

    public void addDisconnectCB(Runnable cb) {
        synchronized (mDisconnectCBs) {
            mDisconnectCBs.add(cb);
        }
    }

    public void cancelDisconnectCB(Runnable cb) {
        synchronized (mDisconnectCBs) {
            if(mDisconnectCBs.contains(cb)) {
                mDisconnectCBs.remove(cb);
            }
        }
    }

    public boolean isConnected() {
        return mDevice.isAny(BleDeviceState.INITIALIZED);
    }

    public boolean isConnecting() {
        return mDevice.isAny(BleDeviceState.CONNECTING);
    }

    public int connect() {
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                mDevice.connect();
                while(!mConnStateLock.awaitMilli(6000)) {
                    // Wait
                    if(mDevice.isAny(BleDeviceState.INITIALIZED)) {
                        break;
                    }
                }
                if(mDevice.isAny(BleDeviceState.INITIALIZED)) {
                    mRval = 0;
                } else {
                    // We timed out
                    mRval = -1;
                }
                return null;
            }
        });
    }

    public int disconnect() {
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                mDevice.disconnect();
                while(mConnStateLock.awaitMilli(3000)) {
                    if(mDevice.isAny(BleDeviceState.ADVERTISING)) {
                        mRval = 0;
                        return null;
                    }
                }
                // We timed out
                mRval = -1;
                return null;
            }
        });
    }

    public int reqRSSI() {
        return mDevice.getRssi();
    }

    public byte[] req(final UUID uuid) {
        final byte[][] rval = new byte[1][];
        int error = protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                final StatLockManager l = new StatLockManager();
                mDevice.read(uuid,new BleDevice.ReadWriteListener() {
                    @Override public void onEvent(BleDevice.ReadWriteListener.ReadWriteEvent result)
                    {
                        if( result.wasSuccess() )
                        {
                            rval[0] = result.data();
                            mRval = 0;
                        } else {
                            Log.e(TAG,"Read fail");
                            mRval = -1;
                        }
                        l.sig();
                    }
                });
                if(l.awaitMilli(1000)) {
                    //Timeout
                    mRval = -1;
                }
                return null;
            }
        });
        if(error == 0) {
            return rval[0];
        } else {
            return null;
        }
    }

    public int send(final UUID uuid, final byte[] value) {
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                final StatLockManager l = new StatLockManager();
                mDevice.write(uuid, value, new BleDevice.ReadWriteListener() {
                    @Override
                    public void onEvent(ReadWriteEvent e) {
                        if(e.wasSuccess()) {
                            mRval = 0;
                        } else {
                            mRval = e.status().ordinal();
                        }
                        l.sig();
                    }
                });
                if(l.awaitMilli(1000)) {
                    //Timeout
                    mRval = -1;
                }
                return null;
            }
        });
    }

    public boolean isNotificationEnabled(UUID uuid) {
        return mDevice.isNotifyEnabled(uuid);
    }

    public int enableNotify(final UUID uuid, final boolean enable, final NotifyHandler on_notify) {
        if(enable) {
            mDevice.enableNotify(uuid, new BleDevice.ReadWriteListener() {
                @Override
                public void onEvent(ReadWriteEvent e) {
                    if(e.wasSuccess()) {
                        if(e.isRead()) {
                            final byte[] payload = e.data().clone();
                            final double timestamp = Util.getUTCTime();
                            Util.dispatchCb(new Runnable() {
                                @Override
                                public void run() {
                                    on_notify.onReceived(timestamp, payload);
                                }
                            });
                        }
                    } else {
                        Log.e(TAG,"Notification failure");
                    }
                }
            });
        } else {
            mDevice.disableNotify(uuid);
        }
        return 0;
    }

    public String getAddress() {
        return mDevice.getMacAddress();
    }

    public BleDevice getBLEDevice() {
        return mDevice;
    }
}