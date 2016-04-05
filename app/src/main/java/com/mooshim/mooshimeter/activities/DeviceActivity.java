
package com.mooshim.mooshimeter.activities;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.CooldownTimer;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.interfaces.MooshimeterDelegate;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.SpeaksOnLargeChange;
import com.mooshim.mooshimeter.common.Util;

import java.util.List;

import me.grantland.widget.AutofitHelper;

public class DeviceActivity extends MyActivity implements MooshimeterDelegate {
    private static final String TAG = "DeviceActivity";

    private static int PREF_ACT_REQ = 1;
    private static int GRAPH_ACT_REQ = 2;

    private static MooshimeterControlInterface.Channel chanEnum(int c) {
        return MooshimeterControlInterface.Channel.values()[c];
    }

    // Callback handles
    private int disconnect_handle = -1;

	// BLE
    private MooshimeterDeviceBase mMeter = null;

    // GUI
    private final TextView[] value_labels = new TextView[2];
    private TextView power_label;

    private final Button[] input_set_buttons = {null,null};
    private final Button[] range_buttons     = {null,null};
    private final Button[] zero_buttons      = {null,null};
    private final Button[] sound_buttons     = {null,null};

    private Button rate_button;
    private Button depth_button;
    private Button logging_button;
    private Button graph_button;
    private Button power_button;

    // GUI housekeeping
    private Drawable getAutoBG(){
        return getResources().getDrawable(R.drawable.button_auto);
    }
    private Drawable getNormalBG() {
        return getResources().getDrawable(R.drawable.button_normal);
    }

    // Helpers
    private CooldownTimer autorange_cooldown = new CooldownTimer();
    private SpeaksOnLargeChange speaksOnLargeChange = new SpeaksOnLargeChange();

    private TextView findAndAutofit(int id) {
        TextView rval = (TextView)findViewById(id);
        AutofitHelper a = AutofitHelper.create(rval);
        a.setMaxLines(1);
        a.setEnabled(true);
        return rval;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);

        // GUI
        setContentView(R.layout.activity_meter);

        // Bind the GUI elements
        value_labels[0]        =         findAndAutofit(R.id.ch1_value_label);
        value_labels[1]        =         findAndAutofit(R.id.ch2_value_label);
        power_label            =         findAndAutofit(R.id.power_label);

        input_set_buttons  [0] = (Button)findAndAutofit(R.id.ch1_input_set_button);
        range_buttons      [0] = (Button)findViewById(R.id.ch1_range_button);
        zero_buttons       [0] = (Button)findAndAutofit(R.id.ch1_zero_button);
        sound_buttons      [0] = (Button)findAndAutofit(R.id.ch1_sound_button);

        input_set_buttons  [1] = (Button)findAndAutofit(R.id.ch2_input_set_button);
        range_buttons      [1] = (Button)findViewById(R.id.ch2_range_button);
        zero_buttons       [1] = (Button)findAndAutofit(R.id.ch2_zero_button);
        sound_buttons      [1] = (Button)findAndAutofit(R.id.ch2_sound_button);

        rate_button            = (Button)findViewById  (R.id.rate_button);
        depth_button           = (Button)findViewById(R.id.depth_button);
        logging_button         = (Button)findAndAutofit(R.id.log_button);
        graph_button           = (Button)findAndAutofit(R.id.graph_button);
        power_button           = (Button)findAndAutofit(R.id.power_button);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
        setResult(RESULT_OK);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//this.optionsMenu = menu;
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.device_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case R.id.opt_prefs:
			startPreferenceActivity();
			break;
		default:
            transitionToActivity(mMeter,ScanActivity.class);
            return true;
		}
		return true;
	}

    @Override
    public void onBackPressed() {
        Log.e(TAG, "Back pressed");
        transitionToActivity(mMeter, ScanActivity.class);
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
        final MooshimeterDelegate d = this;

        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.addDelegate(d);
                mMeter.stream();
                Log.i(TAG, "Stream requested");
                refreshAllControls();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        mMeter = (MooshimeterDeviceBase)getDeviceWithAddress(intent.getStringExtra("addr"));
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

	private void startPreferenceActivity() {
		final Intent i = new Intent(this, PreferencesActivity.class);
        i.putExtra("addr", mMeter.getAddress());
		startActivityForResult(i, PREF_ACT_REQ);
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

    /////////////////////////
    // Widget Refreshers
    /////////////////////////

    private void refreshAllControls() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rate_button_refresh();
                depth_button_refresh();
                logging_button_refresh();
                graph_button_refresh();
                for (int c = 0; c < 2; c++) {
                    input_set_button_refresh(c);
                    range_button_refresh(c);
                    sound_button_refresh(c);
                    zero_button_refresh(c, mMeter.getOffset(chanEnum(c)));
                }
            }
        });
    }
    private void disableableButtonRefresh(Button b, String title, boolean en) {
        final Drawable bg = en? getNormalBG(): getAutoBG();
        b.setText(title);
        b.setBackground(bg);
    }
    private void autoButtonRefresh(final Button b, final String title, boolean auto) {
        final Drawable bg = auto? getAutoBG(): getNormalBG();
        SpannableStringBuilder sb = new SpannableStringBuilder();

        String auto_string = auto?"AUTO":"MANUAL";
        sb.append(auto_string);
        //sb.setSpan(new RelativeSizeSpan(14), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n");
        int i = sb.length();
        sb.append(title);
        sb.setSpan(new RelativeSizeSpan((float)1.6), i, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Util.setText(b, sb);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                b.setBackground(bg);
            }
        });
    }
    private void refreshTitle(float bat_v) {
        final StringBuilder s = new StringBuilder();
        s.append(mMeter.getBLEDevice().getName());
        while(s.length()<20) {
            s.append(' ');
        }
        // Approximate remaining charge
        double soc_percent = (bat_v - 2.0)*100.0;
        if(soc_percent<0){soc_percent=0;}
        if(soc_percent>100){soc_percent=100;}
        s.append(String.format("BAT:%d%%", (int) soc_percent));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setTitle(s.toString());
            }
        });
    }
    private void math_label_refresh(final MeterReading val) {
        Util.setText(power_label,val.toString());
        Util.setText(power_button, mMeter.getSelectedDescriptor(MooshimeterControlInterface.Channel.MATH).name);
    }
    private void math_button_refresh() {
        math_label_refresh(mMeter.getValue(MooshimeterControlInterface.Channel.MATH));
    }
    private void graph_button_refresh() {}
    private void rate_button_refresh() {
        int rate = mMeter.getSampleRateHz();
        String title = String.format("%dHz", rate);
        autoButtonRefresh(rate_button, title, mMeter.rate_auto);
    }
    private void depth_button_refresh() {
        int depth = mMeter.getBufferDepth();
        String title = String.format("%dsmpl", depth);
        autoButtonRefresh(depth_button, title, mMeter.depth_auto);
    }
    private void logging_button_refresh() {
        int s = mMeter.getLoggingStatus();
        final String title;
        final boolean logging_ok = s==0;
        if(logging_ok) {
            title = mMeter.getLoggingOn()?"Logging:ON":"Logging:OFF";
        } else {
            title = mMeter.getLoggingStatusMessage();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                disableableButtonRefresh(logging_button, title, logging_ok);
            }
        });
    }
    private void input_set_button_refresh(final int c) {
        final String s = mMeter.getInputLabel(chanEnum(c));
        Util.setText(input_set_buttons[c], s);
    }
    private void range_button_refresh(final int c) {
        String lval = "";
        lval = mMeter.getRangeLabel(chanEnum(c));
        autoButtonRefresh(range_buttons[c], lval, mMeter.range_auto.get(chanEnum(c)));
    }
    private void valueLabelRefresh(final int c,final MeterReading val) {
        final TextView v = value_labels[c];
        final String label_text = val.toString();
        Util.setText(v, label_text);
    }
    private void zero_button_refresh (final int c, MeterReading value) {
        Log.i(TAG,"zerorefresh");
        final String s;
        if(value.value == 0.0) {
            // No offset applied
            s = "ZERO";
        } else {
            s = value.toString();
        }
        Util.setText(zero_buttons[c], s);
    }
    private void sound_button_refresh(final int c) {
        Log.i(TAG,"soundrefresh");
        final Button b = sound_buttons[c];
        final String s = "SOUND:"+(mMeter.speech_on.get(chanEnum(c))?"ON":"OFF");
        Util.setText(b, s);
    }

    /////////////////////////
    // Button Click Handlers
    ////////////////////////

    PopupMenu popupMenu = null;

    private void makePopupMenu(List<String> options,View anchor, NotifyHandler on_choice) {
        if(popupMenu!=null) {
            return;
        }
        popupMenu = new PopupMenu(getApplicationContext(),anchor);
        popupMenu = Util.generatePopupMenuWithOptions(getApplicationContext(), options, anchor, on_choice, new Runnable() {
            @Override
            public void run() {
                popupMenu = null;
            }
        });
    }
    private void onInputSetClick(final int c) {
        final List<MooshimeterDeviceBase.InputDescriptor> l = mMeter.getInputList(chanEnum(c));
        final List<String> sl = Util.stringifyCollection(l);
        makePopupMenu(sl, input_set_buttons[c], new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                if(0!=mMeter.setInput(chanEnum(c), l.get((Integer) payload))) {
                    setError("Invalid input change!");
                }
                refreshAllControls();
            }
        });
    }
    private void onRangeClick(final int c) {
        List<String> options = mMeter.getRangeList(chanEnum(c));
        options.add(0, "AUTORANGE");
        makePopupMenu(options, range_buttons[c], new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                int choice = (Integer) payload;
                mMeter.range_auto.put(chanEnum(c), choice == 0);
                if (!mMeter.range_auto.get(chanEnum(c))) {
                    mMeter.setRange(chanEnum(c), mMeter.getSelectedDescriptor(chanEnum(c)).ranges.get(choice - 1));
                }
                refreshAllControls();
            }
        });
    }
    private void onSoundClick(final int c) {
        Log.i(TAG, "onCh" + c + "SoundClick");
        if(mMeter.speech_on.get(chanEnum(c))) {
            mMeter.speech_on.put(chanEnum(c),false);
        } else {
            mMeter.speech_on.put(chanEnum(c == 0 ? 1 : 0), false);
            mMeter.speech_on.put(chanEnum(c),true);
        }
        sound_button_refresh(0);
        sound_button_refresh(1);
    }

    public void onCh1InputSetClick(View v) {
        Log.i(TAG, "onCh1InputSetClick");
        onInputSetClick(0);
    }
    public void onCh1RangeClick(View v) {
        Log.i(TAG, "onCh1RangeClick");
        onRangeClick(0);
    }
    public void onCh2InputSetClick(View v) {
        Log.i(TAG, "onCh2InputSetClick");
        onInputSetClick(1);
    }
    public void onCh2RangeClick(View v) {
        Log.i(TAG, "onCh2RangeClick");
        onRangeClick(1);
    }
    public void onRateClick(View v) {
        Log.i(TAG, "onRateClick");
        List<String> options = mMeter.getSampleRateList();
        options.add(0, "AUTORANGE");
        makePopupMenu(options, rate_button, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                int choice = (Integer) payload;
                mMeter.rate_auto = choice == 0;
                if (!mMeter.rate_auto) {
                    mMeter.setSampleRateIndex(choice - 1);
                }
                refreshAllControls();
            }
        });
    }
    public void onDepthClick(View v) {
        Log.i(TAG, "onDepthClick");
        List<String> options = mMeter.getBufferDepthList();
        options.add(0, "AUTORANGE");
        makePopupMenu(options, depth_button, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                int choice = (Integer) payload;
                mMeter.depth_auto = choice == 0;
                if (!mMeter.depth_auto) {
                    mMeter.setBufferDepthIndex(choice - 1);
                }
                refreshAllControls();
            }
        });
    }
    public void onGraphClick(View v) {
        Log.i(TAG, "onGraphClick");
        transitionToActivity(mMeter, GraphingActivity.class);
    }
    public void onLoggingClick(View v) {
        Log.i(TAG, "onLoggingClick");
        if(mMeter.getLoggingStatus()!=0) {
            setError(mMeter.getLoggingStatusMessage());
            mMeter.setLoggingOn(false);
        } else {
            mMeter.setLoggingOn(!mMeter.getLoggingOn());
        }
    }
    public void onZeroClick(final int c) {
        MooshimeterControlInterface.Channel channel = chanEnum(c);
        float offset = mMeter.getOffset(channel).value;
        if(offset==0) {
            mMeter.setOffset(channel, mMeter.getValue(channel).value);
        } else {
            mMeter.setOffset(channel, 0);
        }
    }
    public void onCh1ZeroClick (View v) {
        Log.i(TAG,"onCh1ZeroClick");
        onZeroClick(0);
    }
    public void onCh1SoundClick(View v) { onSoundClick(0); }
    public void onCh2ZeroClick (View v) {
        Log.i(TAG, "onCh2ZeroClick");
        onZeroClick(1);
    }
    public void onCh2SoundClick(View v) {
        onSoundClick(1);
    }
    public void onPowerButtonClick(View v) {
        Log.i(TAG, "onPowerButtonClick");
        final List<MooshimeterDeviceBase.InputDescriptor> list = mMeter.getInputList(MooshimeterControlInterface.Channel.MATH);
        final List<String> slist = Util.stringifyCollection(list);
        makePopupMenu(slist, power_button, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                MooshimeterDeviceBase.InputDescriptor id  = list.get((Integer)payload);
                mMeter.setInput(MooshimeterControlInterface.Channel.MATH,id);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        math_button_refresh();
                    }
                });
            }
        });
    }

    //////////////////////////
    // MooshimeterDelegate calls
    //////////////////////////

    @Override
    public void onInit() {    }

    @Override
    public void onDisconnect() {
        transitionToActivity(mMeter, ScanActivity.class);
    }

    @Override
    public void onRssiReceived(int rssi) {
        Log.d(TAG, "RSSI: " + Integer.toString(rssi));
    }

    @Override
    public void onBatteryVoltageReceived(float voltage) {
        refreshTitle(voltage);
    }

    @Override
    public void onSampleReceived(double timestamp_utc, MooshimeterControlInterface.Channel c, MeterReading val) {
        switch(c) {
            case CH1:
            case CH2:
                valueLabelRefresh(c.ordinal(), val);
                // Run the autorange only on channel 2
                if (c== MooshimeterControlInterface.Channel.CH2 && autorange_cooldown.expired) {
                    autorange_cooldown.fire(200);
                    Util.dispatch(new Runnable() {
                        @Override
                        public void run() {
                            if(mMeter.applyAutorange()) {
                                refreshAllControls();
                            }
                        }
                    });
                }
                if(mMeter.speech_on.get(c)) {
                    speaksOnLargeChange.decideAndSpeak(val);
                }
                break;
            case MATH:
                math_label_refresh(val);
                break;
        }
    }

    @Override
    public void onBufferReceived(double timestamp_utc, MooshimeterControlInterface.Channel c, float dt, float[] val) {

    }
    @Override
    public void onSampleRateChanged(int i, int sample_rate_hz) {
        rate_button_refresh();
    }
    @Override
    public void onBufferDepthChanged(int i, int buffer_depth) {
        depth_button_refresh();
    }
    @Override
    public void onLoggingStatusChanged(boolean on, int new_state, String message) {
        logging_button_refresh();
    }
    @Override
    public void onRangeChange(MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.RangeDescriptor new_range) {
        range_button_refresh(c.ordinal());
    }
    @Override
    public void onInputChange(MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.InputDescriptor descriptor) {
        input_set_button_refresh(c.ordinal());
    }
    @Override
    public void onOffsetChange(MooshimeterControlInterface.Channel c, MeterReading offset) {
        zero_button_refresh(c.ordinal(),offset);
    }
}
