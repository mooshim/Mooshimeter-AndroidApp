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
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.UUID;

import static java.util.UUID.fromString;

public class BLEDeviceBase {
    ////////////////////////////////
    // Statics
    ////////////////////////////////
    private static final String TAG="BLEDevice";
    /*
    mUUID stores the UUID values of all the Mooshimeter fields.
    Note that the OAD fields are only accessible when connected to the Mooshimeter in OAD mode
    and the METER_ fields are only accessible when connected in meter mode.
     */

    public static final class mServiceUUIDs {
        public final static UUID
        METER_SERVICE      = fromString("1BC5FFA0-0200-62AB-E411-F254E005DBD4"),
        OAD_SERVICE_UUID   = fromString("1BC5FFC0-0200-62AB-E411-F254E005DBD4");
    }

    // Used so the inner classes have something to grab
    public BLEDeviceBase mInstance;
    public PeripheralWrapper mPwrap;
    protected Runnable rssi_cb = null;

    public int              mBuildTime;
    public boolean          mOADMode;
    public boolean          mInitialized = false;

    public BLEDeviceBase(PeripheralWrapper wrap) {
        mPwrap = wrap;
    }

    public int initialize() {
        Util.postDelayed(RSSI_poller,1000);
        return 0;
    }

    private Runnable RSSI_poller = new Runnable() {
        @Override
        public void run() {
            if(!isConnected()) {
                return;
            }
            mPwrap.reqRSSI();
            if(rssi_cb!=null) {
                rssi_cb.run();
            }
            Util.postDelayed(RSSI_poller, 5000);
        }
    };

    public int disconnect() {
        mInitialized = false;
        return mPwrap.disconnect();
    }

    public BLEDeviceBase chooseSubclass() {
        if(!mPwrap.isConnected()) {
            Log.e(TAG,"Can't decide subclass until after connection!");
            return null;
        }
        BLEDeviceBase rval;
        if(isInOADMode()) {
            rval = new OADDevice(mPwrap);
        } else if(mBuildTime < 1454355414) {
            rval = new LegacyMooshimeterDevice(mPwrap);
        } else if(mBuildTime <= Util.getBundledFirmwareVersion()){
            rval = new MooshimeterDevice(mPwrap);
        } else {
            // TODO: RAISE SOME KIND OF WARNING TO UPDATE THE APP
            rval = new MooshimeterDevice(mPwrap);
        }
        // FIXME: This is hacked up.
        rval.mBuildTime = mBuildTime;
        rval.mOADMode = mOADMode;
        return rval;
    }

    ////////////////////////////////
    // Persistent settings
    ////////////////////////////////

    public static final class mPreferenceKeys {
        public static final String
                AUTOCONNECT = "AUTOCONNECT",
                SKIP_UPGRADE= "SKIP_UPGRADE",
                USE_FAHRENHEIT= "USE_FAHRENHEIT";
    }

    private String getSharedPreferenceString() {
        return "mooshimeter-preference-"+mPwrap.getAddress();
    }

    private SharedPreferences getSharedPreferences() {
        return mPwrap.mContext.getSharedPreferences(getSharedPreferenceString(),Context.MODE_PRIVATE);
    }

    public boolean hasPreference(String key) {
        return getSharedPreferences().contains(key);
    }

    public boolean getPreference(String key) {
        return getSharedPreferences().getBoolean(key, false);
    }

    public void setPreference(String key, boolean val) {
        SharedPreferences sp = getSharedPreferences();
        SharedPreferences.Editor e = sp.edit();
        e.putBoolean(key,val);
        e.commit();
    }

    ////////////////////////////////
    // Convenience functions
    ////////////////////////////////

    public boolean isInOADMode() {
        // If we've already connected and discovered characteristics,
        // we can just see what's in the service dictionary.
        // If we haven't connected, revert to whatever the scan
        // hinted at.
        if(mPwrap.mServices.containsKey(mServiceUUIDs.METER_SERVICE)){
            mOADMode = false;
        }
        if(mPwrap.mServices.containsKey(mServiceUUIDs.OAD_SERVICE_UUID)) {
            mOADMode = true;
        }
        return mOADMode;
    }

    ///////////////////
    // Forwarding requests to inner pwrap
    ///////////////////
    public int connect() {
        return mPwrap.connect();
    }
    public int discover() {
        return mPwrap.discover();
    }
    public boolean isConnected() {
        return mPwrap.isConnected();
    }
    public boolean isConnecting() {
        return mPwrap.isConnecting();
    }
    public boolean isDisconnected() {
        return mPwrap.isDisconnected();
    }
    public int getRSSI() {
        return mPwrap.mRssi;
    }
    public void setRSSI(int rssi) {
        mPwrap.mRssi = rssi;
    }
    public BluetoothDevice getBLEDevice() {
        return mPwrap.getBLEDevice();
    }
    public String getAddress() {
        return mPwrap.getAddress();
    }
}
