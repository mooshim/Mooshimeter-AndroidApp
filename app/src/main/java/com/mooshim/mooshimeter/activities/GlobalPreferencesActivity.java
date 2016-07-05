package com.mooshim.mooshimeter.activities;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.Util;
import com.mooshim.mooshimeter.devices.MooshimeterDevice;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;

import java.util.ArrayList;
import java.util.Arrays;

public class GlobalPreferencesActivity extends MyActivity {
    private static final String TAG = "GPreferenceActivity";

    private Context mContext;

    // GUI housekeeping
    private class PreferenceGUIBuilder {
        LinearLayout base;
        public PreferenceGUIBuilder() {
            base = (LinearLayout)findViewById(R.id.preference_background_layout);
        }
        public void add(String title,String descr,View widget) {
            View v = getLayoutInflater().inflate(R.layout.element_pref_descriptor,null,false);
            TextView titleview = (TextView)v.findViewById(R.id.pref_title);
            TextView descrview = (TextView)v.findViewById(R.id.pref_descr);
            titleview.setText(title);
            descrview.setText(descr);
            FrameLayout frame = (FrameLayout)v.findViewById(R.id.frame);
            if(widget!=null) {
                widget.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                frame.addView(widget);
            } else {
                titleview.setGravity(Gravity.CENTER);
                descrview.setGravity(Gravity.CENTER);
                frame.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT,0f));
            }
            v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            base.addView(v);
        }
    }

    Button makeButton(String label, final Runnable cb) {
        Button b = new Button(mContext);
        b.setText(label);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cb.run();
            }
        });
        return b;
    }
    abstract class BooleanRunnable implements Runnable {
        boolean arg;
    }
    Switch makeSwitch(boolean checked, final BooleanRunnable cb) {
        Switch s = new Switch(mContext);
        s.setChecked(checked);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cb.arg = isChecked;
                cb.run();
            }
        });
        s.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        return s;
    }
    public Switch makeSwitchForPreference(final String pref_name) {
        boolean set = Util.getPreference(pref_name);
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
        mContext = this;

        Intent intent = getIntent();

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
        transitionToActivity(null, ScanActivity.class);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

	@Override
	protected void onResume() {
		super.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            default:
                setError("Unknown request code");
                break;
        }
    }

	private void setError(final String txt) {
        final Context c = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(c, txt, Toast.LENGTH_LONG).show();
            }
        });
	}

}
