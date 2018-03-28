package com.mooshim.mooshimeter.activities;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.LogFile;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.interfaces.MooshimeterDelegate;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

public class LoggingPreferencesActivity extends PreferencesActivity implements MooshimeterDelegate{
	// BLE
    private MooshimeterDeviceBase mMeter = null;
    private FileListView mLogView;
    private TextView mStatusMessage;
    private Switch mLogEnableSwitch;

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

        // Logging Status
        View status_pane = builder.add("Logging Status", mMeter.getLoggingStatusMessage(), null);
        mStatusMessage = (TextView)status_pane.findViewById(R.id.pref_descr);

        // Logging on
        mLogEnableSwitch = makeSwitch(mMeter.getLoggingOn(), new BooleanRunnable() {
                                          @Override
                                          public void run() {
                                              Util.dispatch(new Runnable() {
                                                  @Override
                                                  public void run() {
                                                      mMeter.setLoggingOn(arg);
                                                  }
                                              });
                                          }});
        builder.add("Logging Enable","With logging enabled, logs will be written to SD card", mLogEnableSwitch);

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

        // Scroll view of available logs
        mLogView = new FileListView(mContext);
        builder.add(mLogView);

        final Button tmp = new Button(mContext);
        tmp.setText("Load available logs");
        tmp.setTextSize(32);
        tmp.setGravity(Gravity.CENTER);
        tmp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mMeter.mBuildTime < 1477000000) {
                    Toast.makeText(mContext,"Needs later version of meter firmware",Toast.LENGTH_LONG).show();
                } else if(mMeter.getLoggingStatus() != 0) {
                    Toast.makeText(mContext,"No SD card to load logs from",Toast.LENGTH_LONG).show();
                } else {
                    mLogView.addLine("#","End Time","Size");
                    Util.dispatch(new Runnable() {
                        @Override
                        public void run() {
                            mMeter.pollLogInfo();
                        }
                    });
                    tmp.setEnabled(false);
                }
            }
        });
        mLogView.addView(tmp);
	}

    private class FileListView extends LinearLayout {

        private HashMap<Integer,LinearLayout> mLookup = new HashMap<>();

        public FileListView(Context context) {
            super(context);
            this.setOrientation(LinearLayout.VERTICAL);
            LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT);
            this.setLayoutParams(lp);
            this.setDividerDrawable(ContextCompat.getDrawable(getContext(),R.drawable.divider));
            this.setShowDividers(SHOW_DIVIDER_MIDDLE);
        }
        public LinearLayout addLine(String col0, String col1, String col2) {
            LinearLayout row = new LinearLayout(mContext);
            row.setBackground(ContextCompat.getDrawable(getContext(),R.drawable.list_element));
            row.setOrientation(LinearLayout.HORIZONTAL);
            LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            row.setPadding(10,50,10,50);
            row.setLayoutParams(lp);

            TextView ilabel = new TextView(mContext);
            ilabel.setText(col0);
            ilabel.setTextSize(20);
            ilabel.setGravity(Gravity.CENTER);
            ilabel.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView datelabel = new TextView(mContext);
            datelabel.setText(col1);
            datelabel.setTextSize(20);
            datelabel.setGravity(Gravity.CENTER);
            datelabel.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView sizelabel = new TextView(mContext);
            sizelabel.setText(col2);
            sizelabel.setTextSize(20);
            sizelabel.setGravity(Gravity.CENTER);
            sizelabel.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));

            row.addView(ilabel);
            row.addView(datelabel);
            row.addView(sizelabel);
            this.addView(row);
            requestLayout();

            return row;
        }
        public void addFileLine(final LogFile info) {
            Date date = new Date(info.mEndTime*1000);
            SimpleDateFormat format = new SimpleDateFormat("MM.dd HH:mm z");
            LinearLayout row = addLine(""+info.mIndex,
                                           format.format(date),
                                           (info.mBytes/1024)+"kB");
            mLookup.put(info.mIndex,row);

            row.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(mContext, DownloadLogActivity.class);
                    intent.putExtra("addr", mMeter.getAddress());
                    intent.putExtra("info_index",info.mIndex);
                    startActivityForResult(intent, 0);
                }
            });
        }
        public void deleteFileLine(final int index) {
            LinearLayout row = mLookup.getOrDefault(index,null);
            if(row!=null) {
                removeView(row);
                mLookup.remove(index);
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                String returnedResult = data.getData().toString();
                if(returnedResult.equals("DELETED")) {
                    int deleted_log_index = data.getIntExtra("info_index",-1);
                    mLogView.deleteFileLine(deleted_log_index);
                }
            }
        }
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
        mMeter.addDelegate(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mMeter.removeDelegate(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDisconnect() {}

    @Override
    public void onRssiReceived(int rssi) {}

    @Override
    public void onBatteryVoltageReceived(float voltage) {}

    @Override
    public void onSampleReceived(double timestamp_utc, MooshimeterControlInterface.Channel c, MeterReading val) {}

    @Override
    public void onBufferReceived(double timestamp_utc, MooshimeterControlInterface.Channel c, float dt, float[] val) {}

    @Override
    public void onSampleRateChanged(int i, int sample_rate_hz) {}

    @Override
    public void onBufferDepthChanged(int i, int buffer_depth) {}

    @Override
    public void onLoggingStatusChanged(final boolean on, int new_state, final String message) {
        Util.postToMain(new Runnable() {
            @Override
            public void run() {
                mLogEnableSwitch.setChecked(on);
                mStatusMessage.setText(message);
            }
        });
    }

    @Override
    public void onRangeChange(MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.RangeDescriptor new_range) {}

    @Override
    public void onInputChange(MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.InputDescriptor descriptor) {}

    @Override
    public void onOffsetChange(MooshimeterControlInterface.Channel c, MeterReading offset) {}

    @Override
    public void onLogInfoReceived(final LogFile log) {
        Util.postToMain(new Runnable() {
            @Override
            public void run() {
                mLogView.addFileLine(log);
            }
        });
    }
    @Override
    public void onLogFileReceived(LogFile log) {}

    @Override
    public void onLogDataReceived(LogFile log, String data) {}
}
