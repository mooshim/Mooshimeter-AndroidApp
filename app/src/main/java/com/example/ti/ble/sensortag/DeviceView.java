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
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.example.ti.util.Point3D;

// Fragment for Device View
public class DeviceView extends Fragment {

	// Sensor table; the iD corresponds to row number
	private static final int ID_OFFSET = 0;
	private static final int ID_ACC = 1;
	private static final int ID_MAG = 2;
	private static final int ID_OPT = 2;
	private static final int ID_GYR = 3;
	private static final int ID_OBJ = 4;
	private static final int ID_AMB = 5;
	private static final int ID_HUM = 6;
	private static final int ID_BAR = 7;

	public static DeviceView mInstance = null;

	// GUI
	private TableLayout table;
	private TextView mAccValue;
	private TextView mMagValue;
	private TextView mLuxValue;
	private TextView mGyrValue;
	private TextView mObjValue;
	private TextView mAmbValue;
	private TextView mHumValue;
	private TextView mBarValue;
	private ImageView mButton;
	private ImageView mRelay;
	private TableRow mMagPanel;
	private TableRow mBarPanel;

	// House-keeping
	private DecimalFormat decimal = new DecimalFormat("+0.00;-0.00");
	private DeviceActivity mActivity;
	private static final double PA_PER_METER = 12.0;
	private boolean mIsSensorTag2;
	private boolean mBusy;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	    Bundle savedInstanceState) {
		mInstance = this;
		mActivity = (DeviceActivity) getActivity();

		// The last two arguments ensure LayoutParams are inflated properly.
		View view;

		if (mIsSensorTag2) {
			view = inflater.inflate(R.layout.services_browser2, container, false);
			table = (TableLayout) view.findViewById(R.id.services_browser_layout2);
			mLuxValue = (TextView) view.findViewById(R.id.luxometerTxt);
			mMagPanel = null;
			mRelay = (ImageView) view.findViewById(R.id.relay);
		} else {
			view = inflater.inflate(R.layout.services_browser, container, false);
			table = (TableLayout) view.findViewById(R.id.services_browser_layout);
			mMagValue = (TextView) view.findViewById(R.id.magnetometerTxt);
			mMagPanel = (TableRow) view.findViewById(R.id.magPanel);
			mRelay = null;
		}

		// UI widgets
		mAccValue = (TextView) view.findViewById(R.id.accelerometerTxt);
		mGyrValue = (TextView) view.findViewById(R.id.gyroscopeTxt);
		mObjValue = (TextView) view.findViewById(R.id.objTemperatureText);
		mAmbValue = (TextView) view.findViewById(R.id.ambientTemperatureTxt);
		mHumValue = (TextView) view.findViewById(R.id.humidityTxt);
		mBarValue = (TextView) view.findViewById(R.id.barometerTxt);
		mButton = (ImageView) view.findViewById(R.id.buttons);

		// Support for calibration
		mBarPanel = (TableRow) view.findViewById(R.id.barPanel);

		// Notify activity that UI has been inflated
		mActivity.onViewInflated(view);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		updateVisibility();
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

		if (uuidStr.equals(SensorTagGatt.UUID_ACC_DATA.toString())) {
			v = Sensor.ACCELEROMETER.convert(rawValue);
			msg = decimal.format(v.x) + "\n" + decimal.format(v.y) + "\n"
			    + decimal.format(v.z) + "\n";
			mAccValue.setText(msg);
		}

		if (uuidStr.equals(SensorTagGatt.UUID_MAG_DATA.toString())) {
			v = Sensor.MAGNETOMETER.convert(rawValue);
			msg = decimal.format(v.x) + "\n" + decimal.format(v.y) + "\n"
			    + decimal.format(v.z) + "\n";
			mMagValue.setText(msg);
		}

		if (uuidStr.equals(SensorTagGatt.UUID_OPT_DATA.toString())) {
			v = Sensor.LUXOMETER.convert(rawValue);
			msg = decimal.format(v.x) + "\n";
			mLuxValue.setText(msg);
		}

		if (uuidStr.equals(SensorTagGatt.UUID_GYR_DATA.toString())) {
			v = Sensor.GYROSCOPE.convert(rawValue);
			msg = decimal.format(v.x) + "\n" + decimal.format(v.y) + "\n"
			    + decimal.format(v.z) + "\n";
			mGyrValue.setText(msg);
		}

		if (uuidStr.equals(SensorTagGatt.UUID_IRT_DATA.toString())) {
			v = Sensor.IR_TEMPERATURE.convert(rawValue);
			msg = decimal.format(v.x) + "\n";
			mAmbValue.setText(msg);
			msg = decimal.format(v.y) + "\n";
			mObjValue.setText(msg);
		}

		if (uuidStr.equals(SensorTagGatt.UUID_HUM_DATA.toString())) {
			v = Sensor.HUMIDITY.convert(rawValue);
			msg = decimal.format(v.x) + "\n";
			mHumValue.setText(msg);
		}
	}

	void updateVisibility() {
		showItem(ID_ACC, mActivity.isEnabledByPrefs(Sensor.ACCELEROMETER));
		if (mIsSensorTag2)
			showItem(ID_OPT, mActivity.isEnabledByPrefs(Sensor.LUXOMETER));
		else
			showItem(ID_MAG, mActivity.isEnabledByPrefs(Sensor.MAGNETOMETER));
		showItem(ID_GYR, mActivity.isEnabledByPrefs(Sensor.GYROSCOPE));
		showItem(ID_OBJ, mActivity.isEnabledByPrefs(Sensor.IR_TEMPERATURE));
		showItem(ID_AMB, mActivity.isEnabledByPrefs(Sensor.IR_TEMPERATURE));
		showItem(ID_HUM, mActivity.isEnabledByPrefs(Sensor.HUMIDITY));
		showItem(ID_BAR, mActivity.isEnabledByPrefs(Sensor.BAROMETER));
	}

	private void showItem(int id, boolean visible) {
		View hdr = table.getChildAt(id * 2 + ID_OFFSET);
		View txt = table.getChildAt(id * 2 + ID_OFFSET + 1);
		int vc = visible ? View.VISIBLE : View.GONE;
		hdr.setVisibility(vc);
		txt.setVisibility(vc);
	}


	void setBusy(boolean f) {
		if (f != mBusy)
		{
			mActivity.showBusyIndicator(f);
			mBusy = f;
		}
	}
}
