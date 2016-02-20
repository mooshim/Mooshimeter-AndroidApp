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

import android.bluetooth.BluetoothGatt;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import com.mooshim.mooshimeter.common.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.common.NotifyHandler;
import com.mooshim.mooshimeter.common.Util;
import com.mooshim.mooshimeter.main.legacy.PreferencesActivity;

import java.util.List;

public class DeviceActivity extends MyActivity {
    private static final String TAG = "DeviceActivity";

    private static int PREF_ACT_REQ = 1;
    private static int GRAPH_ACT_REQ = 2;

    // Callback handles


	// BLE
    private MooshimeterDeviceBase mMeter = null;

    // GUI
    private final TextView[] value_labels = new TextView[2];

    private final Button[] input_set_buttons = {null,null};
    private final Button[] range_buttons     = {null,null};
    private final Button[] math_buttons      = {null,null};
    private final Button[] zero_buttons      = {null,null};
    private final Button[] sound_buttons     = {null,null};

    private Button rate_button;
    private Button depth_button;
    private Button logging_button;
    private Button graph_button;

    // GUI housekeeping
    private final static GradientDrawable AUTO_GRADIENT    = new GradientDrawable( GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF00FF00,0xFF00CC00});
    private final static GradientDrawable MANUAL_GRADIENT  = new GradientDrawable( GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFFFF0000,0xFFCC0000});
    private final static GradientDrawable DISABLE_GRADIENT = new GradientDrawable( GradientDrawable.Orientation.BOTTOM_TOP, new int[] {0xFFBBBBBB,0xFF888888});
    private final static GradientDrawable ENABLE_GRADIENT  = new GradientDrawable( GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFFFFFFFF,0xFFCCCCCC});

    private CooldownTimer autorange_cooldown = new CooldownTimer();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);

        // GUI
        setContentView(R.layout.activity_meter_new);

        AUTO_GRADIENT   .setStroke(1, 0xFF999999);
        MANUAL_GRADIENT .setStroke(1, 0xFF999999);
        DISABLE_GRADIENT.setStroke(1, 0xFF999999);
        ENABLE_GRADIENT .setStroke(1, 0xFF999999);

        // Bind the GUI elements
        value_labels[0] = (TextView) findViewById(R.id.ch1_value_label);
        value_labels[1] = (TextView) findViewById(R.id.ch2_value_label);

        input_set_buttons  [0] = (Button) findViewById(R.id.ch1_input_set_button);
        range_buttons      [0] = (Button) findViewById(R.id.ch1_range_button);
        math_buttons       [0] = (Button) findViewById(R.id.ch1_math_button);
        zero_buttons       [0] = (Button) findViewById(R.id.ch1_zero_button);
        sound_buttons      [0] = (Button) findViewById(R.id.ch1_sound_button);

        input_set_buttons  [1] = (Button) findViewById(R.id.ch2_input_set_button);
        range_buttons      [1] = (Button) findViewById(R.id.ch2_range_button);
        math_buttons       [1] = (Button) findViewById(R.id.ch2_math_button);
        zero_buttons       [0] = (Button) findViewById(R.id.ch2_zero_button);
        sound_buttons      [0] = (Button) findViewById(R.id.ch2_sound_button);

        rate_button       = (Button) findViewById(R.id.rate_button);
        depth_button      = (Button) findViewById(R.id.depth_button);
        logging_button    = (Button) findViewById(R.id.log_button);
        graph_button      = (Button) findViewById(R.id.graph_button);
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
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

	@Override
	protected void onResume() {
		super.onResume();
        Intent intent = getIntent();
        mMeter = getDeviceWithAddress(intent.getStringExtra("addr"));
        onMeterInitialized();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTitle(mMeter.getBLEDevice().getName());
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

    private void onMeterInitialized() {
        final int[] cb_handle = new int[1];
        cb_handle[0] = mMeter.addConnectionStateCB(BluetoothGatt.STATE_DISCONNECTED, new Runnable() {
            @Override
            public void run() {
                mMeter.cancelConnectionStateCB(cb_handle[0]);
                transitionToActivity(mMeter,ScanActivity.class);
            }
        });
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.playSampleStream(null, new NotifyHandler() {
                    @Override
                    public void onReceived(double timestamp_utc, Object payload) {
                        Util.dispatch(new Runnable() {
                            @Override
                            public void run() {
                                valueLabelRefresh(0);
                                valueLabelRefresh(1);
                                if(autorange_cooldown.expired && mMeter.applyAutorange()) {
                                    autorange_cooldown.fire(100);
                                    refreshAllControls();
                                }
                            }
                        });
                    }
                });
                Log.i(TAG, "Stream requested");
                refreshAllControls();
            }
        });
    }

	private void startPreferenceActivity() {
		final Intent i = new Intent(this, PreferencesActivity.class);
        i.putExtra("addr", mMeter.getAddress());
		startActivityForResult(i, PREF_ACT_REQ);
	}

	private void setError(String txt) {
		Toast.makeText(this, txt, Toast.LENGTH_LONG).show();
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
                    zero_button_refresh(c);
                    sound_button_refresh(c);
                }
            }
        });
    }
    private void disableableButtonRefresh(Button b, String title, boolean en) {
        final GradientDrawable bg = en?ENABLE_GRADIENT:DISABLE_GRADIENT;
        b.setText(title);
        b.setBackground(bg);
    }
    private void graph_button_refresh() {
        Log.d(TAG, "TBI");
    }
    private void rate_button_refresh() {
        int rate = mMeter.getSampleRateHz();
        String title = String.format("%dHz", rate);
        disableableButtonRefresh(rate_button, title, !mMeter.rate_auto);
    }
    private void depth_button_refresh() {
        int depth = mMeter.getBufferDepth();
        String title = String.format("%dsmpl", depth);
        disableableButtonRefresh(depth_button, title, !mMeter.depth_auto);
    }
    private void logging_button_refresh() {
        final boolean b = mMeter.getLoggingOn();
        final GradientDrawable bg = b?AUTO_GRADIENT:MANUAL_GRADIENT;
        final String title = b?"Logging:ON":"Logging:OFF";
        logging_button.setBackground(bg);
        logging_button.setText(title);
    }
    private void input_set_button_refresh(final int c) {
        input_set_buttons[c].setText(mMeter.getInputLabel(c));
    }
    private void range_button_refresh(final int c) {
        String lval = "";
        lval = mMeter.getRangeLabel(c);
        disableableButtonRefresh(range_buttons[c],lval,!mMeter.range_auto[c]);
    }
    private void valueLabelRefresh(final int c) {
        final TextView v = value_labels[c];
        final String label_text = mMeter.getValueLabel(c);
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
    private void zero_button_refresh (final int c) {
        Log.i(TAG,"zerorefresh");
    }
    private void sound_button_refresh(final int c) {
        Log.i(TAG,"soundrefresh");
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
        refreshAllControls();
    }
    private void onRangeClick(final int c) {
        makePopupMenu(mMeter.getRangeList(c), range_buttons[c], new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                mMeter.setRangeIndex(c, (Integer) payload);
                refreshAllControls();
            }
        });
        refreshAllControls();
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
        Log.i(TAG,"onCh2InputSetClick");
        onInputSetClick(1);
    }
    public void onCh2RangeClick(View v) {
        Log.i(TAG,"onCh2RangeClick");
        onRangeClick(1);
    }
    public void onRateClick(View v) {
        Log.i(TAG, "onRateClick");
        makePopupMenu(mMeter.getSampleRateListHz(), rate_button, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                mMeter.setSampleRateIndex((Integer) payload);
                refreshAllControls();
            }
        });
        refreshAllControls();
    }
    public void onDepthClick(View v) {
        Log.i(TAG, "onDepthClick");
        makePopupMenu(mMeter.getBufferDepthList(), depth_button, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                mMeter.setBufferDepthIndex((Integer) payload);
                refreshAllControls();
            }
        });
        refreshAllControls();
    }
    public void onGraphClick(View v) {
        Log.i(TAG, "onGraphClick");
        //transitionToActivity(mMeter,TrendActivity);
    }
    public void onLoggingClick(View v) {
        Log.i(TAG, "onLoggingClick");
    }
    public void onCh1MathClick (View v) {
        Log.i(TAG,"onCh1MathClick");
    }
    public void onCh1ZeroClick (View v) {
        Log.i(TAG,"onCh1ZeroClick");
    }
    public void onCh1SoundClick(View v) {
        Log.i(TAG,"onCh1SoundClick");
    }
    public void onCh2MathClick (View v) {
        Log.i(TAG,"onCh2MathClick");
    }
    public void onCh2ZeroClick (View v) {
        Log.i(TAG, "onCh2ZeroClick");
    }
    public void onCh2SoundClick(View v) {
        Log.i(TAG,"onCh2SoundClick");
    }

}
