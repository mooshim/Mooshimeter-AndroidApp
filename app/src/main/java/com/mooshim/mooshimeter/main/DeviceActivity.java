/**************************************************************************************************
  Filename:       DeviceActivity.java
  Revised:        $Date: 2013-09-05 07:58:48 +0200 (to, 05 sep 2013) $
  Revision:       $Revision: 27616 $

  Copyright (c) 2013 - 2014 Texas Instruments Incorporated

  All rights reserved not granted herein.
  Limited License. 

  Texas Instruments Incorporated grants a world-wide, royalty-free,
  non-exclusive license under copyrights and patents it now or hereafter
  owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
  this software subject to the terms herein.  With respect to the foregoing patent
  license, such license is granted  solely to the extent that any such patent is necessary
  to Utilize the software alone.  The patent license shall not apply to any combinations which
  include this software, other than combinations with devices manufactured by or for TI (�TI Devices�). 
  No hardware patent is licensed hereunder.

  Redistributions must preserve existing copyright notices and reproduce this license (including the
  above copyright notice and the disclaimer and (if applicable) source code license limitations below)
  in the documentation and/or other materials provided with the distribution

  Redistribution and use in binary form, without modification, are permitted provided that the following
  conditions are met:

    * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
      software provided in binary form.
    * any redistribution and use are licensed by TI for use only with TI Devices.
    * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

  If software source code is provided to you, modification and redistribution of the source code are permitted
  provided that the following conditions are met:

    * any redistribution and use of the source code, including any resulting derivative works, are licensed by
      TI for use only with TI Devices.
    * any redistribution and use of any object code compiled from the source code and any resulting derivative
      works, are licensed by TI for use only with TI Devices.

  Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
  promote products derived from this software without specific prior written permission.

  DISCLAIMER.

  THIS SOFTWARE IS PROVIDED BY TI AND TI�S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL TI AND TI�S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.mooshim.mooshimeter.main;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Html;
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
import com.mooshim.mooshimeter.common.MooshimeterDelegate;
import com.mooshim.mooshimeter.common.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.common.NotifyHandler;
import com.mooshim.mooshimeter.common.SpeaksOnLargeChange;
import com.mooshim.mooshimeter.common.Util;

import java.util.ArrayList;
import java.util.List;

import me.grantland.widget.AutofitHelper;

public class DeviceActivity extends MyActivity implements MooshimeterDelegate {
    private static final String TAG = "DeviceActivity";

    private static int PREF_ACT_REQ = 1;
    private static int GRAPH_ACT_REQ = 2;

    // Callback handles
    private int disconnect_handle = -1;

	// BLE
    private MooshimeterDeviceBase mMeter = null;

    // GUI
    private final TextView[] value_labels = new TextView[2];
    private TextView power_label;

    private final Button[] input_set_buttons = {null,null};
    private final Button[] range_buttons     = {null,null};
    private final Button[] math_buttons      = {null,null};
    private final Button[] zero_buttons      = {null,null};
    private final Button[] sound_buttons     = {null,null};

    private Button rate_button;
    private Button depth_button;
    private Button logging_button;
    private Button graph_button;
    private Button power_button;

    // GUI housekeeping

    private static GradientDrawable __getDrawable(int[] colors) {
        GradientDrawable rval = new GradientDrawable( GradientDrawable.Orientation.TOP_BOTTOM, colors);
        rval.setStroke(1, 0xFF999999);
        return rval;
    }
    private static GradientDrawable getDisableGradient(){return __getDrawable(new int[] {0xFFBBBBBB,0xFF888888});}
    private static GradientDrawable getEnableGradient() {return __getDrawable(new int[] {0xFFFFFFFF,0xFFCCCCCC});}

    // Helpers
    private CooldownTimer autorange_cooldown = new CooldownTimer();
    private SpeaksOnLargeChange speaksOnLargeChange = new SpeaksOnLargeChange();

    private enum PowerOption{
        REAL_POWER,
        APPARENT_POWER,
        POWER_FACTOR,
    };
    private PowerOption chosen_power_option = PowerOption.REAL_POWER;

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
        range_buttons      [0] = (Button)findViewById  (R.id.ch1_range_button);
        math_buttons       [0] = (Button)findAndAutofit(R.id.ch1_math_button);
        zero_buttons       [0] = (Button)findAndAutofit(R.id.ch1_zero_button);
        sound_buttons      [0] = (Button)findAndAutofit(R.id.ch1_sound_button);

        input_set_buttons  [1] = (Button)findAndAutofit(R.id.ch2_input_set_button);
        range_buttons      [1] = (Button)findViewById(R.id.ch2_range_button);
        math_buttons       [1] = (Button)findAndAutofit(R.id.ch2_math_button);
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
        if(!(mMeter.speech_on[0] || mMeter.speech_on[1])) {
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
                mMeter.setDelegate(d);
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
                    math_button_refresh(c);
                    sound_button_refresh(c);
                    zero_button_refresh(c,mMeter.getOffset(c));
                }
            }
        });
    }
    private void disableableButtonRefresh(Button b, String title, boolean en) {
        final GradientDrawable bg = en?getEnableGradient():getDisableGradient();
        b.setText(title);
        b.setBackground(bg);
    }
    private void autoButtonRefresh(final Button b, final String title, boolean auto) {
        final GradientDrawable bg = auto?getDisableGradient():getEnableGradient();
        final String composite = String.format("%s<br/><big><big>%s</big></big>",auto?"AUTO":"MANUAL",title);
        final CharSequence s = Html.fromHtml(composite);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                b.setText(s);
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
    private void power_label_refresh(float val) {
        // Refresh both the power label and the power button
        final float display_val;
        switch(chosen_power_option) {
            case REAL_POWER:
                display_val = val;
                break;
            case APPARENT_POWER:
                display_val = mMeter.getValue(0)*mMeter.getValue(1);
                break;
            case POWER_FACTOR:
                // Power factor = real power/apparent power
                display_val = val/(mMeter.getValue(0)*mMeter.getValue(1));
                break;
            default:
                display_val = 0;
                break;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                power_label.setText(String.format("%.03f", display_val));
                power_button.setText(chosen_power_option.toString());
            }
        });
    }
    private void power_button_refresh() {
        power_label_refresh(mMeter.getPower());
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
        final String s = mMeter.getInputLabel(c);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                input_set_buttons[c].setText(s);
            }
        });
    }
    private void range_button_refresh(final int c) {
        String lval = "";
        lval = mMeter.getRangeLabel(c);
        autoButtonRefresh(range_buttons[c], lval, mMeter.range_auto[c]);
    }
    private void valueLabelRefresh(final int c,final float val) {
        final TextView v = value_labels[c];
        final String label_text = mMeter.formatValueLabel(c, val);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                v.setText(label_text);
            }
        });
    }
    private void math_button_refresh (final int c) {
        Log.i(TAG,"mathrefresh");
    }
    private void zero_button_refresh (final int c, float value) {
        Log.i(TAG,"zerorefresh");
        final String s;
        if(value == 0.0) {
            // No offset applied
            s = "ZERO";
        } else {
            s = mMeter.formatValueLabel(c, value);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                zero_buttons[c].setText(s);
            }
        });
    }
    private void sound_button_refresh(final int c) {
        Log.i(TAG,"soundrefresh");
        final Button b = sound_buttons[c];
        final String s = "SOUND:"+(mMeter.speech_on[c]?"ON":"OFF");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                b.setText(s);
            }
        });
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
        makePopupMenu(mMeter.getInputList(c), input_set_buttons[c], new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                mMeter.setInputIndex(c, (Integer) payload);
                refreshAllControls();
            }
        });
    }
    private void onRangeClick(final int c) {
        List<String> options = mMeter.getRangeList(c);
        options.add(0, "AUTORANGE");
        makePopupMenu(options, range_buttons[c], new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                int choice = (Integer) payload;
                mMeter.range_auto[c] = choice == 0;
                if (!mMeter.range_auto[c]) {
                    mMeter.setRangeIndex(c, choice - 1);
                }
                refreshAllControls();
            }
        });
    }
    private void onSoundClick(final int c) {
        Log.i(TAG, "onCh" + c + "SoundClick");
        if(mMeter.speech_on[c]) {
            mMeter.speech_on[c] = false;
        } else {
            mMeter.speech_on[c==0?1:0] = false;
            mMeter.speech_on[c] = true;
        }
        sound_button_refresh(0);
        sound_button_refresh(1);
    }

    public void onCh1InputSetClick(View v) {
        Log.i(TAG,"onCh1InputSetClick");
        onInputSetClick(0);
    }
    public void onCh1RangeClick(View v) {
        Log.i(TAG,"onCh1RangeClick");
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
        float offset = mMeter.getOffset(c);
        if(offset==0) {
            mMeter.setOffset(c,mMeter.getValue(c));
        } else {
            mMeter.setOffset(c,0);
        }
    }
    public void onCh1MathClick (View v) {
        Log.i(TAG, "onCh1MathClick");
    }
    public void onCh1ZeroClick (View v) {
        Log.i(TAG,"onCh1ZeroClick");
        onZeroClick(0);
    }
    public void onCh1SoundClick(View v) { onSoundClick(0); }
    public void onCh2MathClick (View v) {
        Log.i(TAG, "onCh2MathClick");
    }
    public void onCh2ZeroClick (View v) {
        Log.i(TAG, "onCh2ZeroClick");
        onZeroClick(1);
    }
    public void onCh2SoundClick(View v) {
        onSoundClick(1);
    }
    public void onPowerButtonClick(View v) {
        Log.i(TAG, "onPowerButtonClick");
        final List<String> options = new ArrayList<String>();
        for(PowerOption p:PowerOption.values()) {
            options.add(p.name());
        }
        makePopupMenu(options, power_button, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                chosen_power_option = PowerOption.values()[(Integer)payload];
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        power_button_refresh();
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
        transitionToActivity(mMeter,ScanActivity.class);
    }

    @Override
    public void onBatteryVoltageReceived(float voltage) {
        refreshTitle(voltage);
    }

    @Override
    public void onSampleReceived(double timestamp_utc, int channel, float val) {
        valueLabelRefresh(channel,val);
        // Run the autorange only on channel 2
        if (channel==1 && autorange_cooldown.expired) {
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
        if(mMeter.speech_on[channel]) {
            speaksOnLargeChange.decideAndSpeak(val,mMeter.formatValueLabel(channel,val));
        }
    }

    @Override
    public void onBufferReceived(double timestamp_utc, int channel, float dt, float[] val) {

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
    public void onRangeChange(int c, int i, MooshimeterDeviceBase.RangeDescriptor new_range) {
        range_button_refresh(c);
    }

    @Override
    public void onInputChange(int c, int i, MooshimeterDeviceBase.InputDescriptor descriptor) {
        input_set_button_refresh(c);
    }

    @Override
    public void onRealPowerCalculated(double timestamp_utc, float val) {
        power_label_refresh(val);
    }

    @Override
    public void onOffsetChange(int c, float offset) {
        zero_button_refresh(c,offset);
    }
}
