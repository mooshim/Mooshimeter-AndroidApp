package com.mooshim.mooshimeter.activities;

/**
 * Created by First on 2/3/2015.
 */

import android.app.Application;

import com.mooshim.mooshimeter.common.Util;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Util.init(this);
    }
}



