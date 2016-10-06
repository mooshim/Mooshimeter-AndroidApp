package com.mooshim.mooshimeter.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.mooshim.mooshimeter.devices.BLEDeviceBase;
import com.mooshim.mooshimeter.devices.SimulatedMooshimeterDevice;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by First on 12/4/2015.
 */
public abstract class MyActivity extends Activity {
    // This is the master list of all Mooshimeters
    private final static Map<String,BLEDeviceBase> mMeterDict;
    static {
        mMeterDict = new ConcurrentHashMap<>();
        SimulatedMooshimeterDevice sim = new SimulatedMooshimeterDevice(null);
        putDevice(sim);
    }

    public static int getNDevices() {
        synchronized (mMeterDict) {
            return mMeterDict.size();
        }
    }
    public static Collection<BLEDeviceBase> getDevices() {
        synchronized (mMeterDict) {
            return mMeterDict.values();
        }
    }
    public static void clearDeviceCache() {
        synchronized (mMeterDict) {
            //If there's a simulated device, keep it
            SimulatedMooshimeterDevice sim = (SimulatedMooshimeterDevice) mMeterDict.get("FAKEADDR");
            mMeterDict.clear();
            if(sim!=null) {
                putDevice(sim);
            }
        }
    }
    public static BLEDeviceBase getDeviceWithAddress(String addr) {
        synchronized (mMeterDict) {
            return mMeterDict.get(addr);
        }
    }
    public static void putDevice(BLEDeviceBase device) {
        synchronized (mMeterDict) {
            mMeterDict.put(device.getAddress(),device);
        }
    }
    public static void removeDevice(BLEDeviceBase device) {
        synchronized (mMeterDict) {
            if(!device.getAddress().equals("FAKEADDR")) {
                mMeterDict.remove(device.getAddress());
            }
        }
    }

    protected void pushActivityToStack(BLEDeviceBase d, Class activity_class) {
        Intent intent = new Intent(this, activity_class);
        if(d!=null) {
            intent.putExtra("addr", d.getAddress());
        }
        startActivityForResult(intent, 0);
    }

    protected String cname() {
        return this.getClass().getName();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(cname(),"What do I do with this?");
    }

    @Override
    protected void onCreate(Bundle bundle) {
        Log.d(cname(), "onCreate");
        super.onCreate(bundle);
    }

    @Override
    protected void onStart() {
        Log.d(cname(), "onStart");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(cname(), "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(cname(), "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(cname(), "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(cname(), "onDestroy");
        super.onDestroy();
    }
}
