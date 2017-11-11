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
import android.content.SharedPreferences;
import android.util.Log;

import com.idevicesinc.sweetblue.BleDevice;
import com.mooshim.mooshimeter.activities.MyApplication;
import com.mooshim.mooshimeter.common.Util;

import java.util.Arrays;
import java.util.List;
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
        private mServiceUUIDs() {}
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
        //Util.postDelayedToMain(RSSI_poller, 1000);
        return 0;
    }
    /*
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
            Util.postDelayedToMain(RSSI_poller, 2000);
        }
    };
    */
    public int disconnect() {
        mInitialized = false;
        return mPwrap.disconnect();
    }

    public BLEDeviceBase chooseSubclass() {
        if(getClass()==SimulatedMooshimeterDevice.class) {
            return this;
        }
        if(!mPwrap.isConnected()) {
            Log.e(TAG,"Can't decide subclass until after connection!");
            return null;
        }
        BLEDeviceBase rval;
        if(isInOADMode()) {
            Log.d(TAG,"Wrapping as an OADDevice");
            rval = new OADDevice(mPwrap);
        } else if(mBuildTime < 1454355414) {
            Log.d(TAG,"Wrapping as a LegacyMooshimeter");
            rval = new LegacyMooshimeterDevice(mPwrap);
        } else {
            Log.d(TAG,"Wrapping as a Mooshimeter");
            rval = new MooshimeterDevice(mPwrap);
        }
        // FIXME: This is hacked up.  I shouldn't have to copy over individual members... indication that I should put some more thought in to architecture
        rval.mBuildTime = mBuildTime;
        rval.mOADMode = mOADMode;
        return rval;
    }

    ////////////////////////////////
    // Persistent settings
    ////////////////////////////////

    public static final class mPreferenceKeys {
        private mPreferenceKeys() {}
        public static final String
                AUTOCONNECT = "AUTOCONNECT",
                SKIP_UPGRADE= "SKIP_UPGRADE";
    }

    private String getSharedPreferenceString() {
        return "mooshimeter-preference-"+getAddress();
    }

    private SharedPreferences getSharedPreferences() {
        return MyApplication.getMyContext().getSharedPreferences(getSharedPreferenceString(),Context.MODE_PRIVATE);
    }

    public boolean hasPreference(String key) {
        return getSharedPreferences().contains(key);
    }

    public boolean getPreference(String key) {
        return getPreference(key,false);
    }

    public boolean getPreference(String key, boolean d) {
        return getSharedPreferences().getBoolean(key, d);
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
        List<UUID> services = Arrays.asList(mPwrap.mDevice.getAdvertisedServices());
        if(services.contains(mServiceUUIDs.METER_SERVICE)) {
            mOADMode = false;
        }
        if(services.contains(mServiceUUIDs.OAD_SERVICE_UUID)) {
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
    public boolean isConnected() {
        return mPwrap.isConnected();
    }
    public boolean isConnecting() {
        return mPwrap.isConnecting();
    }
    public int getRSSI() {
        return getBLEDevice().getRssi();
    }
    public BleDevice getBLEDevice() {
        return mPwrap.getBLEDevice();
    }
    public String getAddress() {
        return mPwrap.getAddress();
    }
    public String getName() {
        return mPwrap.getBLEDevice().getName_native();
    }
}
