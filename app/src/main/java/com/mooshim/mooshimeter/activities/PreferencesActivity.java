package com.mooshim.mooshimeter.activities;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
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
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.devices.MooshimeterDevice;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.Util;

import java.util.ArrayList;
import java.util.Arrays;

public class PreferencesActivity extends MyActivity {
    protected Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        mContext = this;
    }

    // GUI housekeeping
    protected class PreferenceGUIBuilder {
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
            if(widget!=null) {
                widget.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                FrameLayout frame = (FrameLayout)v.findViewById(R.id.frame);
                frame.addView(widget);
            }
            v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            base.addView(v);
        }
        public void add(View widget) {
            base.addView(widget);
        }
    }

    protected Button makeButton(String label, final Runnable cb) {
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
    protected Switch makeSwitch(boolean checked, final BooleanRunnable cb) {
        Switch s = new Switch(mContext);
        s.setChecked(checked);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cb.arg = isChecked;
                cb.run();
            }
        });
        return s;
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

	protected void setError(final String txt) {
        final Context c = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(c, txt, Toast.LENGTH_LONG).show();
            }
        });
	}
}
