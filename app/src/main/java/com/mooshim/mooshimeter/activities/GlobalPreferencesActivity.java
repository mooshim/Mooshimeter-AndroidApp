package com.mooshim.mooshimeter.activities;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.Util;

public class GlobalPreferencesActivity extends PreferencesActivity {
    private static final String TAG = "GPreferenceActivity";

    public Switch makeSwitchForPreference(final String pref_name) {
        boolean set = Util.getPreferenceBoolean(pref_name);
        return makeSwitch(set, new BooleanRunnable() {
            @Override
            public void run() {
                Util.setPreference(pref_name,arg);
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_preference);

        PreferenceGUIBuilder builder = new PreferenceGUIBuilder();
        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String version = pInfo.versionName;
        builder.add("Android App","Version "+version,null);
        builder.add("Bundled Firmware","Version code: "+Util.getBundledFW().getVersion(),null);
        if(Util.getDownloadFW()!=null) {
            builder.add("Downloaded Firmware","Version code: "+Util.getDownloadFW().getVersion(),null);
        }
        builder.add("Use Fahrenheit","Display temperatures in F instead of C",makeSwitchForPreference(Util.preference_keys.USE_FAHRENHEIT));
        builder.add("Broadcast Intents","Broadcasts readings to the Android system so other apps can listen.",makeSwitchForPreference(Util.preference_keys.BROADCAST_INTENTS));
        builder.add("Simulated Meter","Adds a simulated Mooshimeter to the scan list for speeding app development",makeSwitchForPreference(Util.preference_keys.SIMULATED_METER));
        builder.add("Help","Visit help website",makeButton("Help", new Runnable() {
            @Override
            public void run() {
                // View the instructions
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://moosh.im/support/"));
                startActivity(browserIntent);
            }
        }));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
        setResult(RESULT_OK);
	}

    @Override
    public void onBackPressed() {
        Log.e(TAG, "Back pressed");
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }
}
