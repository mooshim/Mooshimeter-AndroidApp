package com.mooshim.mooshimeter.main;

/**
 * Created by First on 2/3/2015.
 */

import android.app.Application;

import com.mooshim.mooshimeter.R;

import org.acra.*;
import org.acra.annotation.*;

@ReportsCrashes(
        formKey = "", // will not be used
        formUri = "https://moosh.im/crash/report.php",
        mode = ReportingInteractionMode.TOAST,
        resToastText = R.string.crash_toast_text
)
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // The following line triggers the initialization of ACRA
        //ACRA.init(this);
    }
}



