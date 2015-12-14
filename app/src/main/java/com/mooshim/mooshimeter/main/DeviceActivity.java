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

import java.util.Arrays;

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
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.*;

public class DeviceActivity extends MyActivity {
    private static final String TAG = "DeviceActivity";

	// Activity
	private static final int PREF_ACT_REQ = 0;
	private static final int FWUPDATE_ACT_REQ = 1;
    private static final int TREND_ACT_REQ = 2;

	// BLE
    private MooshimeterDevice mMeter = null;

    // GUI
    private final TextView[] value_labels = new TextView[2];

    private final Button[] display_set_buttons = new Button[2];
    private final Button[] input_set_buttons   = new Button[2];
    private final Button[] range_auto_buttons  = new Button[2];
    private final Button[] range_buttons       = new Button[2];
    private final Button[] units_buttons       = new Button[2];

    private Button rate_auto_button;
    private Button rate_button;
    private Button logging_button;
    private Button depth_auto_button;
    private Button depth_button;
    private Button zero_button;

    // GUI housekeeping
    private final static GradientDrawable AUTO_GRADIENT    = new GradientDrawable( GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF00FF00,0xFF00CC00});
    private final static GradientDrawable MANUAL_GRADIENT  = new GradientDrawable( GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFFFF0000,0xFFCC0000});
    private final static GradientDrawable DISABLE_GRADIENT = new GradientDrawable( GradientDrawable.Orientation.BOTTOM_TOP, new int[] {0xFFBBBBBB,0xFF888888});
    private final static GradientDrawable ENABLE_GRADIENT  = new GradientDrawable( GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFFFFFFFF,0xFFCCCCCC});

    private int count_since_settings_sent = 0;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);

        // GUI
        setContentView(R.layout.activity_meter);

        AUTO_GRADIENT   .setStroke(1, 0xFF999999);
        MANUAL_GRADIENT .setStroke(1, 0xFF999999);
        DISABLE_GRADIENT.setStroke(1, 0xFF999999);
        ENABLE_GRADIENT .setStroke(1, 0xFF999999);

        // Bind the GUI elements
        value_labels[0] = (TextView) findViewById(R.id.ch1_value_label);
        value_labels[1] = (TextView) findViewById(R.id.ch2_value_label);

        display_set_buttons[0] = (Button) findViewById(R.id.ch1_display_set_button);
        input_set_buttons  [0] = (Button) findViewById(R.id.ch1_input_set_button);
        range_auto_buttons [0] = (Button) findViewById(R.id.ch1_range_auto_button);
        range_buttons      [0] = (Button) findViewById(R.id.ch1_range_button);
        units_buttons      [0] = (Button) findViewById(R.id.ch1_units_button);

        display_set_buttons[1] = (Button) findViewById(R.id.ch2_display_set_button);
        input_set_buttons  [1] = (Button) findViewById(R.id.ch2_input_set_button);
        range_auto_buttons [1] = (Button) findViewById(R.id.ch2_range_auto_button);
        range_buttons      [1] = (Button) findViewById(R.id.ch2_range_button);
        units_buttons      [1] = (Button) findViewById(R.id.ch2_units_button);

        rate_auto_button  = (Button) findViewById(R.id.rate_auto_button);
        rate_button       = (Button) findViewById(R.id.rate_button);
        logging_button    = (Button) findViewById(R.id.logging_button);
        depth_auto_button = (Button) findViewById(R.id.depth_auto_button);
        depth_button      = (Button) findViewById(R.id.depth_button);
        zero_button = (Button) findViewById(R.id.zero_button);
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
        Log.e(TAG, "What now");
        transitionToActivity(mMeter, ScanActivity.class);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.pauseStream();
            }
        });
    }

	@Override
	protected void onResume() {
		super.onResume();
        if(Configuration.ORIENTATION_PORTRAIT == this.getResources().getConfiguration().orientation) {
            // If we're in Portrait, continue as normal
            // If we're in landscape, handleOrientation will have started the trend activity
            Intent intent = getIntent();
            mMeter = getDeviceWithAddress(intent.getStringExtra("addr"));
            onMeterInitialized();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setTitle(mMeter.getBLEDevice().getName());
        }
	}

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handleOrientation();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case PREF_ACT_REQ:
                Toast.makeText(this, "Applying preferences", Toast.LENGTH_SHORT).show();
                break;
            default:
                setError("Unknown request code");
                break;
        }
    }

    private void handleOrientation() {
        switch(this.getResources().getConfiguration().orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                // Start the trend view
                Log.i(TAG, "Starting trend view.");
                transitionToActivity(mMeter, TrendActivity.class);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                // Do nothing, leave to external application
                break;
        }
    }

    private void onMeterInitialized() {
        final int[] cb_handle = new int[1];
        cb_handle[0] = mMeter.addConnectionStateCB(BluetoothGatt.STATE_DISCONNECTED, new Runnable() {
            @Override
            public void run() {
                mMeter.cancelConnectionStateCB(cb_handle[0]);
                setResult(RESULT_OK);
                finish();
            }
        });
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.playSampleStream(new PeripheralWrapper.NotifyCallback() {
                    @Override
                    public void notify(double timestamp_utc, byte[] payload) {
                        valueLabelRefresh(0);
                        valueLabelRefresh(1);

                        // Handle autoranging
                        // Save a local copy of settings
                        byte[] save = mMeter.meter_settings.pack();
                        // This switch provides settling time
                        if (count_since_settings_sent > 0) {
                            mMeter.applyAutorange();
                        }
                        byte[] compare = mMeter.meter_settings.pack();
                        // TODO: There must be a more efficient way to do this.  But I think like a c-person
                        // Check if anything changed, and if so apply changes
                        if (!Arrays.equals(save, compare)) {
                            count_since_settings_sent = 0;
                            Util.dispatch(new Runnable() {
                                @Override
                                public void run() {
                                    mMeter.meter_settings.send();
                                }
                            });
                            refreshAllControls();
                        } else {
                            count_since_settings_sent++;
                        }
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
                rate_auto_button_refresh();
                rate_button_refresh();
                depth_auto_button_refresh();
                depth_button_refresh();
                logging_button_refresh();
                zero_button_refresh();
                for(int c = 0; c < 2; c++) {
                    autoRangeButtonRefresh(c);
                    display_set_button_refresh(c);
                    input_set_button_refresh(c);
                    units_button_refresh(c);
                    range_button_refresh(c);
                }
            }
        });
    }

    private void disableableButtonRefresh(Button b, String title, boolean en) {
        final GradientDrawable bg = en?ENABLE_GRADIENT:DISABLE_GRADIENT;
        b.setText(title);
        b.setBackground(bg);
    }

    private void rate_auto_button_refresh() {
        styleAutoButton(rate_auto_button,mMeter.disp_rate_auto);
    }

    private void rate_button_refresh() {
        byte rate_setting = (byte)(mMeter.meter_settings.adc_settings & MooshimeterDevice.ADC_SETTINGS_SAMPLERATE_MASK);
        int rate = 125 * (1<<rate_setting);
        String title = String.format("%dHz", rate);
        disableableButtonRefresh(rate_button, title, !mMeter.disp_rate_auto);
    }

    private void depth_auto_button_refresh() {
        styleAutoButton(depth_auto_button,mMeter.disp_depth_auto);
    }

    private void depth_button_refresh() {
        byte depth_setting = (byte)(mMeter.meter_settings.calc_settings & MooshimeterDevice.METER_CALC_SETTINGS_DEPTH_LOG2);
        int depth = (1<<depth_setting);
        String title = String.format("%dsmpl", depth);
        disableableButtonRefresh(depth_button, title, !mMeter.disp_depth_auto);
    }

    private void logging_button_refresh() {
        final boolean b = mMeter.meter_log_settings.target_logging_state!=MooshimeterDevice.LOGGING_OFF;
        final GradientDrawable bg = b?AUTO_GRADIENT:MANUAL_GRADIENT;
        final String title = b?"Logging:ON":"Logging:OFF";
        logging_button.setBackground(bg);
        logging_button.setText(title);
    }

    private void zero_button_refresh() {
        final GradientDrawable bg = mMeter.offset_on?AUTO_GRADIENT:MANUAL_GRADIENT;
        zero_button.setBackground(bg);
    }

    private void styleAutoButton(Button button, boolean auto) {
        final GradientDrawable bg = auto?AUTO_GRADIENT:MANUAL_GRADIENT;
        String title = auto?"A":"M";
        button.setText(title);
        button.setBackground(bg);
    }

    private void autoRangeButtonRefresh(final int c) {
        styleAutoButton(range_auto_buttons[c],mMeter.disp_range_auto[c]);
    }

    private void display_set_button_refresh(final int c) {
        display_set_buttons[c].setText(mMeter.getDescriptor(c));
    }

    private void input_set_button_refresh(final int c) {
        input_set_buttons[c].setText(mMeter.getInputLabel(c));
    }

    private void units_button_refresh(final int c) {
        String unit_str;
        if(!mMeter.disp_hex[c]) {
            MooshimeterDevice.SignificantDigits digits = mMeter.getSigDigits(c);
            final String[] prefixes = {"μ","m","","k","M"};
            byte prefix_i = 2;
            //TODO: Unify prefix handling.
            while(digits.high > 4) {
                digits.high -= 3;
                prefix_i++;
            }
            while(digits.high <=0) {
                digits.high += 3;
                prefix_i--;
            }
            unit_str = String.format("%s%s",prefixes[prefix_i],mMeter.getUnits(c));
        } else {
            unit_str = "RAW";
        }
        units_buttons[c].setText(unit_str);
    }

    private void range_button_refresh(final int c) {
        // How many different ranges do we want to support?
        // Supporting a range for every single PGA gain seems mighty excessive.

        byte channel_setting = mMeter.meter_settings.chset[c];
        byte measure_setting = mMeter.meter_settings.measure_settings;
        String lval = "";

        switch(channel_setting & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input
                switch(c) {
                    case 0:
                        switch(channel_setting & MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK) {
                            case 0x10:
                                lval = "10A";
                                break;
                            case 0x40:
                                lval = "2.5A";
                                break;
                            case 0x60:
                                lval = "1A";
                                break;
                        }
                        break;
                    case 1:
                        switch(mMeter.meter_settings.adc_settings & MooshimeterDevice.ADC_SETTINGS_GPIO_MASK) {
                        case 0x00:
                            lval = "1.2V";
                            break;
                        case 0x10:
                            lval = "60V";
                            break;
                        case 0x20:
                            lval = "600V";
                            break;
                    }
                    break;
                }
                break;
            case 0x04:
                // Temp input
                lval = "60C";
                break;
            case 0x09:
                switch(mMeter.disp_ch3_mode) {
                case VOLTAGE:
                case DIODE:
                    switch(channel_setting & MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK) {
                        case 0x10:
                            lval = "1.2V";
                            break;
                        case 0x40:
                            lval = "300mV";
                            break;
                        case 0x60:
                            lval = "100mV";
                            break;
                    }
                    break;
                case RESISTANCE:
                    switch((channel_setting & MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK) | (measure_setting & (MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_LVL|MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_ON))) {
                        case 0x13:
                            lval = "10kΩ";
                            break;
                        case 0x43:
                            lval = "2.5kΩ";
                            break;
                        case 0x63:
                            lval = "1kΩ";
                            break;
                        case 0x12:
                            lval = "250kΩ";
                            break;
                        case 0x42:
                            lval = "100kΩ";
                            break;
                        case 0x62:
                            lval = "25kΩ";
                            break;
                        case 0x11:
                            lval = "10MΩ";
                            break;
                        case 0x41:
                            lval = "2.5MΩ";
                            break;
                        case 0x61:
                            lval = "1MΩ";
                            break;
                    }
                    break;
            }
            break;
        }
        disableableButtonRefresh(range_buttons[c],lval,!mMeter.disp_range_auto[c]);
    }

    private void valueLabelRefresh(final int c) {
        final boolean ac = mMeter.disp_ac[c];
        final TextView v = value_labels[c];
        double val;
        int lsb_int;
        if(ac) { lsb_int = (int)(Math.sqrt(mMeter.meter_sample.reading_ms[c])); }
        else   { lsb_int = mMeter.meter_sample.reading_lsb[c]; }

        final String label_text;

        if( mMeter.disp_hex[c]) {
            lsb_int &= 0x00FFFFFF;
            label_text = String.format("0x%06X", lsb_int);

        } else {
            // If at the edge of your range, say overload
            // Remember the bounds are asymmetrical
            final int upper_limit_lsb = (int) (1.1*(1<<22));
            final int lower_limit_lsb = (int) (-0.9*(1<<22));

            if(   lsb_int > upper_limit_lsb
                    || lsb_int < lower_limit_lsb ) {
                label_text = "OVERLOAD";
            } else {
                // FIXME: Resistance measurement completely breaks all our idioms because it is presented
                // by the meter in native units AND as LSB.  This is a transitional issue... future firmware
                // versions will be sending native units across the link, but we're stuck in the in-between
                // right now.
                if(     0x09==(mMeter.meter_settings.chset[c]&MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK)
                        &&  (mMeter.meter_info.build_time > 1445139447)  // And we have a firmware version late enough that the resistance is calculated in firmware
                        &&  0x00!=(mMeter.meter_settings.calc_settings&MooshimeterDevice.METER_CALC_SETTINGS_RES) ) {
                    // FIXME: We're packing the calculated resistance in to the mean-square field!
                    val = mMeter.meter_sample.reading_ms[c];
                } else {
                    val = mMeter.lsbToNativeUnits(lsb_int, c);
                }
                label_text = MooshimeterDevice.formatReading(val, mMeter.getSigDigits(c));
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                v.setText(label_text);
            }
        });
    }

    /////////////////////////
    // Button Click Handlers
    ////////////////////////

    private void clearOffsets() {
        // If we are switching through a mode change that involves toggling the iSRC
        // we must invalidate the offsets
        for(int i = 0; i < 3; i++) {mMeter.offsets[i]=0;}
        mMeter.offset_on = false;
    }

    private void cycleCH3Mode() {
        switch(mMeter.disp_ch3_mode) {
            case VOLTAGE:
                mMeter.disp_ch3_mode = MooshimeterDevice.CH3_MODES.RESISTANCE;
                clearOffsets();
                break;
            case RESISTANCE:
                mMeter.disp_ch3_mode = MooshimeterDevice.CH3_MODES.DIODE;
                break;
            case DIODE:
                mMeter.disp_ch3_mode = MooshimeterDevice.CH3_MODES.VOLTAGE;
                clearOffsets();
                break;
        }
    }

    private void onDisplaySetClick(int c) {
        // If on normal electrode input, toggle between AC and DC display
        // If reading CH3, cycle from VauxDC->VauxAC->Resistance->Diode
        // If reading temp, do nothing
        byte setting = (byte) (mMeter.meter_settings.chset[c] & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK);
        switch(setting) {
            case 0x00:
                // Electrode input
                mMeter.disp_ac[c] ^= true;
                break;
            case 0x04:
                // Temp input
                break;
            case 0x09:
                switch(mMeter.disp_ch3_mode) {
                case VOLTAGE:
                    mMeter.disp_ac[c] ^= true;
                    if(!mMeter.disp_ac[c]){cycleCH3Mode();}
                    break;
                case RESISTANCE:
                    cycleCH3Mode();
                    break;
                case DIODE:
                    cycleCH3Mode();
                    break;
            }
            switch(mMeter.disp_ch3_mode) {
                case VOLTAGE:
                    mMeter.meter_settings.measure_settings &=~MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_ON;
                    mMeter.meter_settings.measure_settings &=~MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_LVL;
                    mMeter.meter_settings.calc_settings    &=~MooshimeterDevice.METER_CALC_SETTINGS_RES;
                    break;
                case RESISTANCE:
                    mMeter.meter_settings.measure_settings |= MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_ON;
                    mMeter.meter_settings.calc_settings    |= MooshimeterDevice.METER_CALC_SETTINGS_RES;
                    break;
                case DIODE:
                    mMeter.meter_settings.measure_settings |= MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_ON;
                    mMeter.meter_settings.calc_settings    &=~MooshimeterDevice.METER_CALC_SETTINGS_RES;
                    break;
            }
            break;
        }
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.meter_settings.send();
            }
        });
        refreshAllControls();
    }

    private void onInputSetClick(int c) {
        byte setting       = mMeter.meter_settings.chset[c];
        byte other_setting = mMeter.meter_settings.chset[(c+1)%2];
        switch(setting & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input: Advance to CH3 unless the other channel is already on CH3
                if((other_setting & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK) == 0x09 ) {
                    setting &= ~MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK;
                    setting |= 0x04;
                    setting &=~MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK;
                    setting |= 0x10;
                    mMeter.disp_ac[c] = false;
                } else {
                    setting &= ~MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK;
                    setting |= 0x09;
                }
                break;
            case 0x09:
                // CH3 input
                setting &= ~MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK;
                setting |= 0x04;
                setting &=~MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK;
                setting |= 0x10;
                mMeter.disp_ac[c] = false;
                break;
            case 0x04:
                // Temp input
                setting &= ~MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK;
                setting |= 0x00;
                break;
        }
        mMeter.meter_settings.chset[c] = setting;
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.meter_settings.send();
            }
        });
        refreshAllControls();
    }

    private void onUnitsClick(int c) {
        if(0x04 == (mMeter.meter_settings.chset[c] & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK)) {
            // If we're measuring temperature, provide some options for units
            if(mMeter.disp_hex[c]) {
                mMeter.disp_hex[c] = false;
            } else {
                int o = mMeter.disp_temp_units.ordinal()+1;
                if(o == mMeter.disp_temp_units.values().length) {
                    mMeter.disp_hex[c] = true;
                    mMeter.disp_temp_units = mMeter.disp_temp_units.values()[0];
                } else {
                    mMeter.disp_temp_units = mMeter.disp_temp_units.values()[o];
                }
            }
        } else {
            mMeter.disp_hex[c] ^= true;
        }
        refreshAllControls();
    }

    private void onRangeClick(int c) {
        if(mMeter.disp_range_auto[c]) {
            return;
        }

        mMeter.bumpRange(c,true,true);

        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.meter_settings.send();
            }
        });
        refreshAllControls();
    }

    public void onCh1DisplaySetClick(View v) {
        Log.i(TAG,"onCh1DisplaySetClick");
        onDisplaySetClick(0);
    }

    public void onCh1InputSetClick(View v) {
        Log.i(TAG,"onCh1InputSetClick");
        onInputSetClick(0);
    }

    public void onCh1RangeAutoClick(View v) {
        Log.i(TAG,"onCh1RangeAutoClick");
        mMeter.disp_range_auto[0] ^= true;
        refreshAllControls();
    }

    public void onCh1RangeClick(View v) {
        Log.i(TAG,"onCh1RangeClick");
        onRangeClick(0);
    }

    public void onCh1UnitsClick(View v) {
        Log.i(TAG,"onCh1UnitsClick");
        onUnitsClick(0);
    }

    public void onCh2DisplaySetClick(View v) {
        Log.i(TAG,"onCh2DisplaySetClick");
        onDisplaySetClick(1);
    }

    public void onCh2InputSetClick(View v) {
        Log.i(TAG,"onCh2InputSetClick");
        onInputSetClick(1);
    }

    public void onCh2RangeAutoClick(View v) {
        Log.i(TAG,"onCh2RangeAutoClick");
        mMeter.disp_range_auto[1] ^= true;
        refreshAllControls();
    }

    public void onCh2RangeClick(View v) {
        Log.i(TAG,"onCh2RangeClick");
        onRangeClick(1);
    }

    public void onCh2UnitsClick(View v) {
        Log.i(TAG, "onCh2UnitsClick");
        onUnitsClick(1);
    }

    public void onRateAutoClick(View v) {
        Log.i(TAG,"onRateAutoClick");
        mMeter.disp_rate_auto ^= true;
        refreshAllControls();
    }

    public void onRateClick(View v) {
        Log.i(TAG,"onRateClick");
        if(mMeter.disp_rate_auto) {
            // If auto is on, do nothing
        } else {
            byte rate_setting = (byte)(mMeter.meter_settings.adc_settings & MooshimeterDevice.ADC_SETTINGS_SAMPLERATE_MASK);
            rate_setting++;
            rate_setting %= 7;
            mMeter.meter_settings.adc_settings &= ~MooshimeterDevice.ADC_SETTINGS_SAMPLERATE_MASK;
            mMeter.meter_settings.adc_settings |= rate_setting;
            Util.dispatch(new Runnable() {
                @Override
                public void run() {
                    mMeter.meter_settings.send();
                }
            });

            refreshAllControls();
        }
    }

    public void onLoggingClick(View v) {
        Log.i(TAG,"onLoggingClick");
        if(mMeter.meter_log_settings.target_logging_state != MooshimeterDevice.LOGGING_SAMPLING) {
            mMeter.meter_log_settings.target_logging_state = MooshimeterDevice.LOGGING_SAMPLING;
        } else {
            mMeter.meter_log_settings.target_logging_state = MooshimeterDevice.LOGGING_OFF;
        }
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.meter_log_settings.send();
            }
        });
        refreshAllControls();
    }

    public void onDepthAutoClick(View v) {
        Log.i(TAG,"onDepthAutoClick");
        mMeter.disp_depth_auto ^= true;
        refreshAllControls();
    }

    public void onDepthClick(View v) {
        Log.i(TAG,"onDepthClick");
        if(mMeter.disp_depth_auto) {
            // If auto is on, do nothing
        } else {
            byte depth_setting = (byte)(mMeter.meter_settings.calc_settings & MooshimeterDevice.METER_CALC_SETTINGS_DEPTH_LOG2);
            depth_setting++;
            depth_setting %= 9;
            mMeter.meter_settings.calc_settings &= ~MooshimeterDevice.METER_CALC_SETTINGS_DEPTH_LOG2;
            mMeter.meter_settings.calc_settings |= depth_setting;
            Util.dispatch(new Runnable() {
                @Override
                public void run() {
                    mMeter.meter_settings.send();
                }
            });
            refreshAllControls();
        }
    }

    private void auxZero(int c) {
        final int lsb = mMeter.meter_sample.reading_lsb[c];
        if( 0 != (mMeter.meter_settings.measure_settings & MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_ON) ) {
            final double isrc_current = mMeter.getIsrcCurrent();
            // Save aux offset as a resistance
            mMeter.offsets[2] = mMeter.lsbToNativeUnits(lsb, c);
            // FIXME: Logic here for PCB version I diode mode
            if( mMeter.disp_ch3_mode != MooshimeterDevice.CH3_MODES.RESISTANCE ) {
                mMeter.offsets[2] /= isrc_current;
            }
        } else {
            // Current source is off, save as a simple voltage offset
            mMeter.offsets[2] = lsb;
        }
    }

    public void onZeroClick(View v) {
        Log.i(TAG,"onZeroClick");
        // TODO: Update firmware to allow saving of user offsets to flash
        // FIXME: Annoying special case: Channel 1 offset in current mode is stored as offset at the ADC
        // because current sense amp drift dominates the offset.  Hardware fix this in RevI.
        // FIXME: Annoyance number 2: When the ISRC is on, offset in the leads dominates
        // When iSRC is off, offset in the ADC dominates

        // Toggle
        mMeter.offset_on ^= true;
        if(mMeter.offset_on) {
            byte channel_setting = (byte) (mMeter.meter_settings.chset[0] & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK);
            switch(channel_setting) {
                case 0x00: // Electrode input
                    if(mMeter.meter_info.pcb_version == 7) {
                        mMeter.offsets[0] = mMeter.lsbToADCInVoltage(mMeter.meter_sample.reading_lsb[0],0);
                    } else {
                        mMeter.offsets[0] = mMeter.meter_sample.reading_lsb[0];
                    }
                    break;
                case 0x09:
                    auxZero(0);
            }
            channel_setting = (byte) (mMeter.meter_settings.chset[1] & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK);
            switch(channel_setting) {
                case 0x00: // Electrode input
                    mMeter.offsets[1] = mMeter.meter_sample.reading_lsb[1];
                    break;
                case 0x09:
                    auxZero(1);
            }
        } else {
            for(int i=0; i<mMeter.offsets.length; i++) {mMeter.offsets[i]=0;}
        }
        refreshAllControls();
    }
}
