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
    private static final String TAG = "PreferenceActivity";

    private Context mContext;

	// BLE
    private MooshimeterDeviceBase mMeter = null;

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
            if(widget!=null) {
                widget.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                FrameLayout frame = (FrameLayout)v.findViewById(R.id.frame);
                frame.addView(widget);
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
        return s;
    }
    public Switch makeSwitchForMeterPreference(final String pref_name) {
        boolean set = mMeter.getPreference(pref_name);
        return makeSwitch(set, new BooleanRunnable() {
            @Override
            public void run() {
                mMeter.setPreference(pref_name,arg);
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
        mContext = this;

        Intent intent = getIntent();
        mMeter = (MooshimeterDeviceBase)getDeviceWithAddress(intent.getStringExtra("addr"));

        setContentView(R.layout.activity_preference);

        PreferenceGUIBuilder builder = new PreferenceGUIBuilder();
        // Name
        builder.add("Name", "Device name, broadcast over BLE.", makeButton("Set", new Runnable() {
            @Override
            public void run() {
                Util.dispatch(new Runnable() {
                    @Override
                    public void run() {
                        String name = Util.offerStringInputBox(mContext,"Input new name");
                        if(name!=null) {
                            mMeter.setName(name);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext, "Name sent!  Change will be visible after reconnect.", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                });
            }
        }));
        // Logging interval
        final Button log_interval_button = new Button(mContext);
        final int[] ms_options = new int[]{0, 1000, 10000, 60000, 60000};
        final ArrayList<String> option_list = new ArrayList<>(
                Arrays.asList("No wait", "1s", "10s", "1min", "10min"));
        int i=0;
        int interval = mMeter.getLoggingIntervalMS();
        for(int option:ms_options) {
            if(option>=interval) {
                break;
            }
            i++;
        }
        log_interval_button.setText(option_list.get(i));
        log_interval_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Util.generatePopupMenuWithOptions(mContext, option_list, v, new NotifyHandler() {
                    @Override
                    public void onReceived(double timestamp_utc, final Object payload) {
                        mMeter.setLoggingInterval(ms_options[(Integer) payload]);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                log_interval_button.setText(option_list.get((Integer) payload));
                            }
                        });
                    }
                }, null);
            }
        });
        builder.add("Logging Interval", "How long to wait between log samples.", log_interval_button);
        // Shipping mode
        builder.add("Shipping Mode",
                    "Sets the Mooshimeter to hibernation mode and turns off the Bluetooth Radio.  To wake it you will need to connect the C and Ω inputs for 10 seconds.",
                    makeButton("Set", new Runnable() {
                        @Override
                        public void run() {
                            Util.dispatch(new Runnable() {
                                @Override
                                public void run() {
                                    if(Util.offerYesNoDialog(mContext, "Enter shipping mode?", "This will disconnect the meter, you will need to wake it before you can reconnect.")) {
                                        // HIBERNATE
                                        mMeter.enterShippingMode();
                                    }
                                }
                            });
                        }
                    }));
        // Extra preferences
        builder.add("Autoconnect","Automatically connect to this meter when detected",makeSwitchForMeterPreference(MooshimeterDevice.mPreferenceKeys.AUTOCONNECT));
        builder.add("Skip upgrade","Suppress offering firmware upgrade",makeSwitchForMeterPreference(MooshimeterDevice.mPreferenceKeys.SKIP_UPGRADE));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
        setResult(RESULT_OK);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		default:
            transitionToActivity(mMeter, DeviceActivity.class);
		}
		return true;
    }

    @Override
    public void onBackPressed() {
        Log.e(TAG, "Back pressed");
        transitionToActivity(mMeter, DeviceActivity.class);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(!(mMeter.speech_on.get(MooshimeterControlInterface.Channel.CH1) || mMeter.speech_on.get(MooshimeterControlInterface.Channel.CH2))) {
            Util.dispatch(new Runnable() {
                @Override
                public void run() {
                    mMeter.pause();
                    mMeter.removeDelegate();
                }
            });
        }
    }

	@Override
	protected void onResume() {
		super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
