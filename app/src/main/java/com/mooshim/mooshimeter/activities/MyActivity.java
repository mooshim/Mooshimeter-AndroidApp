package com.mooshim.mooshimeter.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.mooshim.mooshimeter.devices.BLEDeviceBase;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by First on 12/4/2015.
 */
public abstract class MyActivity extends Activity {

    // This is the master list of all Mooshimeters
    protected static final Map<String,BLEDeviceBase> mMeterDict = new HashMap<String, BLEDeviceBase>();

    public static BLEDeviceBase getDeviceWithAddress(String addr) {
        return mMeterDict.get(addr);
    }

    protected void transitionToActivity(BLEDeviceBase d, Class activity_class) {
        Intent intent = new Intent(this, activity_class);
        if(d!=null) {
            intent.putExtra("addr", d.getAddress());
        }
        finish();
        startActivityForResult(intent, 0);
    }

    @Override
    protected void onCreate(Bundle bundle) {
        Log.d(this.getClass().getName(), "onCreate");
        super.onCreate(bundle);
    }

    @Override
    protected void onStart() {
        Log.d(this.getClass().getName(), "onStart");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(this.getClass().getName(), "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(this.getClass().getName(), "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(this.getClass().getName(), "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(this.getClass().getName(), "onDestroy");
        super.onDestroy();
    }
}
