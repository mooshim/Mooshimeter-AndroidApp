package com.mooshim.mooshimeter.activities;

/**
 * Created by First on 2/3/2015.
 */

import android.app.Application;
import android.content.Context;

import com.mooshim.mooshimeter.common.Util;
import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;

public class MyApplication extends Application {
    static Context mContext;
    public static Context getMyContext() {
        return mContext;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
        Util.init(this);
        mContext = getApplicationContext();
    }
}



