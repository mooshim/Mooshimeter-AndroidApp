/**************************************************************************************************
  Filename:       DeviceView.java
  Revised:        $Date: 2013-08-30 12:02:37 +0200 (fr, 30 aug 2013) $
  Revision:       $Revision: 27470 $

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
package com.example.ti.ble.sensortag;

import java.text.DecimalFormat;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.example.ti.util.Point3D;

// Fragment for Device View
public class DeviceView extends Fragment {

	// Sensor table; the iD corresponds to row number
	private static final int ID_OFFSET = 0;

	public static DeviceView mInstance = null;

	// GUI
    private TextView ch1_value_label;
    private TextView ch2_value_label;

    private Button ch1_display_set_button;
    private Button ch1_input_set_button;
    private Button ch1_range_auto_button;
    private Button ch1_range_button;
    private Button ch1_units_button;

    private Button ch2_display_set_button;
    private Button ch2_input_set_button;
    private Button ch2_range_auto_button;
    private Button ch2_range_button;
    private Button ch2_units_button;

    private Button rate_auto_button;
    private Button rate_button;
    private Button logging_button;
    private Button depth_auto_button;
    private Button depth_button;
    private Button settings_button;


	// House-keeping
	private DecimalFormat decimal = new DecimalFormat("+0.00;-0.00");
	private DeviceActivity mActivity;
	private boolean mBusy;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	    Bundle savedInstanceState) {
		mInstance = this;
		mActivity = (DeviceActivity) getActivity();

		// The last two arguments ensure LayoutParams are inflated properly.
		View view;

        view = inflater.inflate(R.layout.meter_view, container, false);

		// Notify activity that UI has been inflated
		mActivity.onViewInflated(view);

        // Bind the GUI elements
        ch1_value_label = (TextView) view.findViewById(R.id.ch1_value_label);
        ch2_value_label = (TextView) view.findViewById(R.id.ch2_value_label);

        ch1_display_set_button = (Button) view.findViewById(R.id.ch1_display_set_button);
        ch1_input_set_button   = (Button) view.findViewById(R.id.ch1_input_set_button);
        ch1_range_auto_button  = (Button) view.findViewById(R.id.ch1_range_auto_button);
        ch1_range_button       = (Button) view.findViewById(R.id.ch1_range_button);
        ch1_units_button       = (Button) view.findViewById(R.id.ch1_units_button);

        ch2_display_set_button = (Button) view.findViewById(R.id.ch2_display_set_button);
        ch2_input_set_button   = (Button) view.findViewById(R.id.ch2_input_set_button);
        ch2_range_auto_button  = (Button) view.findViewById(R.id.ch2_range_auto_button);
        ch2_range_button       = (Button) view.findViewById(R.id.ch2_range_button);
        ch2_units_button       = (Button) view.findViewById(R.id.ch2_units_button);

        rate_auto_button  = (Button) view.findViewById(R.id.rate_auto_button);
        rate_button       = (Button) view.findViewById(R.id.rate_button);
        logging_button    = (Button) view.findViewById(R.id.logging_button);
        depth_auto_button = (Button) view.findViewById(R.id.depth_auto_button);
        depth_button      = (Button) view.findViewById(R.id.depth_button);
        settings_button   = (Button) view.findViewById(R.id.settings_button);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	/**
	 * Handle changes in sensor values
	 * */
	public void onCharacteristicChanged(String uuidStr, byte[] rawValue) {
		Point3D v;
		String msg;
/*
		if (uuidStr.equals(SensorTagGatt.UUID_HUM_DATA.toString())) {
			v = Sensor.HUMIDITY.convert(rawValue);
			msg = decimal.format(v.x) + "\n";
			mHumValue.setText(msg);
		}*/
	}

	void setBusy(boolean f) {
		if (f != mBusy)
		{
			mActivity.showBusyIndicator(f);
			mBusy = f;
		}
	}
}
