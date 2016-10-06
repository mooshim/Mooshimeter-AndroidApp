package com.mooshim.mooshimeter.activities;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.LogFile;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.common.Util;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.interfaces.MooshimeterDelegate;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class DownloadLogActivity extends MyActivity implements MooshimeterDelegate{
	// BLE
    private MooshimeterDeviceBase mMeter = null;
    private LogFile mLog = null;
    private boolean mDone = false;
    private Context mContext;

    @BindView(R.id.tw_file) TextView filename_text;
    @BindView(R.id.tw_info) TextView progress_text;
    @BindView(R.id.tw_log) TextView log_text;
    @BindView(R.id.pb_progress) ProgressBar progress_bar;
    @BindView(R.id.share_button) ImageButton share_button;

    @Override
    public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);

        mContext = this;

        Intent intent = getIntent();
        String addr = intent.getStringExtra("addr");
        mMeter = (MooshimeterDeviceBase)getDeviceWithAddress(addr);
        if(mMeter==null) {
            Util.logNullMeterEvent(addr);
            finish();
            return;
        }
        int logIndex = intent.getIntExtra("info_index",-1);
        if(logIndex==-1) {
            // We don't have an index!
            finish();
        }
        mLog = mMeter.getLogInfo(logIndex);

        setContentView(R.layout.activity_downloadlog);
        ButterKnife.bind(this);

        filename_text.setText(mLog.getFile().getAbsolutePath());

        log_text.setMovementMethod(new ScrollingMovementMethod());

        mMeter.addDelegate(this);
        mMeter.downloadLog(mLog);
    }

    @OnClick(R.id.share_button)
    public void shareButtonClicked() {
        if(mDone) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, mLog.getFileName());
            intent.putExtra(Intent.EXTRA_TEXT, "This is a logfile from a Mooshimeter.");
            Uri uri = Uri.fromFile(mLog.getFile());
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(Intent.createChooser(intent, "Send email..."));
        } else {
            Toast.makeText(this,"Cannot share file until download complete!",Toast.LENGTH_LONG).show();
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
    protected void onDestroy() {
        super.onDestroy();
        if(!mDone) {
            mMeter.cancelLogDownload();
        }
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
    public void onLoggingStatusChanged(boolean on, int new_state, String message) {}

    @Override
    public void onRangeChange(MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.RangeDescriptor new_range) {}

    @Override
    public void onInputChange(MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.InputDescriptor descriptor) {}

    @Override
    public void onOffsetChange(MooshimeterControlInterface.Channel c, MeterReading offset) {}

    @Override
    public void onLogInfoReceived(final LogFile log) {}
    @Override
    public void onLogFileReceived(final LogFile log) {
        if(mDone) {
            return;
        }
        mDone = true;
        Util.postToMain(new Runnable() {
            @Override
            public void run() {
                progress_bar.setProgress(progress_bar.getMax());
                int total_kb = mLog.mBytes/1024;
                progress_text.setText("Downloaded "+total_kb+"kb");
                Toast.makeText(mContext,"Saved file to "+mLog.getFile().getAbsolutePath(),Toast.LENGTH_LONG).show();
                shareButtonClicked();
            }
        });
    }

    @Override
    public void onLogDataReceived(LogFile log, final String data) {
        Util.postToMain(new Runnable() {
            @Override
            public void run() {
                int length =(int)mLog.getFile().length();
                float progress = ((float)length)/((float)mLog.mBytes);
                progress_bar.setProgress((int)(progress*progress_bar.getMax()));
                int dl_kb = length/1024;
                int total_kb = mLog.mBytes/1024;
                progress_text.setText(dl_kb+" of "+total_kb+"kb");
                log_text.setText(data);
            }
        });
    }
}
