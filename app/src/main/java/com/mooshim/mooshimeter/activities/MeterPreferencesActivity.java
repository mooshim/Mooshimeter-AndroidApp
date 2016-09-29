package com.mooshim.mooshimeter.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.devices.MooshimeterDevice;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.Util;

import java.util.ArrayList;
import java.util.Arrays;

public class MeterPreferencesActivity extends PreferencesActivity{
    private static final String TAG = "PreferenceActivity";

	// BLE
    private MooshimeterDeviceBase mMeter = null;

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

        Intent intent = getIntent();
        String addr = intent.getStringExtra("addr");
        mMeter = (MooshimeterDeviceBase)getDeviceWithAddress(addr);
        if(mMeter==null) {
            Util.logNullMeterEvent(addr);
            finish();
            return;
        }

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
        final int[] ms_options = new int[]{0, 1000, 10000, 60000, 600000};
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
        // Shipping mode
        builder.add("Reboot now",
                    "Reboots the meter and drops back to scanning screen.",
                    makeButton("Set", new Runnable() {
                        @Override
                        public void run() {
                            mMeter.reboot();
                        }
                    }));
        builder.add("Start Firmware Uploader",
                    "Checks if new firmware is available for this meter",
                    makeButton("Go", new Runnable() {
                        @Override
                        public void run() {
                            pushActivityToStack(mMeter,OADActivity.class);
                        }
                    }));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		default:
            finish();
		}
		return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(   mMeter==null ||!mMeter.isConnected()) {
            onBackPressed();
        }
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
