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
package com.example.ti.ble.sensortag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
// import android.util.Log;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ti.ble.common.BluetoothLeService;
import com.example.ti.ble.common.GattInfo;
import com.example.ti.util.Point3D;

public class DeviceActivity extends ViewPagerActivity {
	// Activity
	public static final String EXTRA_DEVICE = "EXTRA_DEVICE";
	private static final int PREF_ACT_REQ = 0;
	private static final int FWUPDATE_ACT_REQ = 1;

	// BLE
	private BluetoothLeService mBtLeService = null;
	private BluetoothDevice mBluetoothDevice = null;
	private BluetoothGatt mBtGatt = null;
	private List<BluetoothGattService> mServiceList = null;
	private static final int GATT_TIMEOUT = 250; // milliseconds
	private boolean mServicesRdy = false;
	private boolean mIsReceiving = false;

	// SensorTagGatt
	private List<Sensor> mEnabledSensors = new ArrayList<Sensor>();
	private BluetoothGattService mOadService = null;
	private BluetoothGattService mConnControlService = null;
	private String mFwRev;

    // GUI
    ViewGroup base_view;

	public DeviceActivity() {
		mResourceFragmentPager = R.layout.fragment_pager;
		mResourceIdPager = R.id.pager;
		mFwRev = new String("1.5"); // Assuming all SensorTags are up to date until actual FW revision is read
	}

	public static DeviceActivity getInstance() {
		return (DeviceActivity) mThis;
}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();

		// BLE
		mBtLeService = BluetoothLeService.getInstance();
		mBluetoothDevice = intent.getParcelableExtra(EXTRA_DEVICE);
		mServiceList = new ArrayList<BluetoothGattService>();

        final ChannelView test_view = new ChannelView(this);

        //ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(this,null);
        //lp.width = getWindow().getDecorView().getWidth();
        //lp.height = getWindow().getDecorView().getHeight();

        int x = getWindow().getDecorView().getWidth();
        int y = getWindow().getDecorView().getHeight();
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(x,y);

        base_view = new ViewGroup(this) {
            @Override
            protected void onLayout(boolean b, int i, int i2, int i3, int i4) {
                int w = (i3-i )/4;
                int h = (i4-i2)/6;
                test_view.layout(i,i2,i3,i4);
                Log.i(null, "Layout!");
            }
        };
        setContentView(base_view,lp);
        base_view.addView(test_view);

        return;
        /*
        mSectionsPagerAdapter.addSection(mDeviceView, "Meter View");

        // Creating a new RelativeLayout
        RelativeLayout relativeLayout = new RelativeLayout(this);

        // Defining the RelativeLayout layout parameters.
        // In this case I want to fill its parent
        RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.FILL_PARENT,
                RelativeLayout.LayoutParams.FILL_PARENT);

        // Creating a new TextView
        TextView tv = new TextView(this);
        tv.setText("Test");

        // Defining the layout parameters of the TextView
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);

        // Setting the parameters on the TextView
        tv.setLayoutParams(lp);

        // Adding the TextView to the RelativeLayout as a child
        relativeLayout.addView(tv);

        // Setting the RelativeLayout as our content view
        setContentView(relativeLayout, rlp);*/
/*
		// Determine type of SensorTagGatt
		String deviceName = mBluetoothDevice.getName();
		mIsSensorTag2 = deviceName.equals("SensorTag2");
		if (mIsSensorTag2)
			PreferenceManager.setDefaultValues(this, R.xml.preferences2, false);
		else
			PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		// Log.i(TAG, "Preferences for: " + deviceName);

		// GUI
		mDeviceView = new DeviceView();
		mSectionsPagerAdapter.addSection(mDeviceView, "Sensors");

		// GATT database
		Resources res = getResources();
		XmlResourceParser xpp = res.getXml(R.xml.gatt_uuid);
		new GattInfo(xpp);

		// Initialize sensor list
		updateSensorList();*/
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		finishActivity(PREF_ACT_REQ);
		finishActivity(FWUPDATE_ACT_REQ);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		this.optionsMenu = menu;
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
		case R.id.opt_fwupdate:
			startOadActivity();
			break;
		case R.id.opt_about:
			//openAboutDialog();
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	@Override
	protected void onResume() {
		// Log.d(TAG, "onResume");
		super.onResume();
		if (!mIsReceiving) {
			registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
			mIsReceiving = true;
		}
	}

	@Override
	protected void onPause() {
		// Log.d(TAG, "onPause");
		super.onPause();
		if (mIsReceiving) {
			unregisterReceiver(mGattUpdateReceiver);
			mIsReceiving = false;
		}
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter fi = new IntentFilter();
		fi.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		fi.addAction(BluetoothLeService.ACTION_DATA_NOTIFY);
		fi.addAction(BluetoothLeService.ACTION_DATA_WRITE);
		fi.addAction(BluetoothLeService.ACTION_DATA_READ);
		return fi;
	}

	void onViewInflated(View view) {
		// Log.d(TAG, "Gatt view ready");
		setBusy(true);

		// Set title bar to device name
		setTitle(mBluetoothDevice.getName());

		// Create GATT object
		mBtGatt = BluetoothLeService.getBtGatt();

		// Start service discovery
		if (!mServicesRdy && mBtGatt != null) {
			if (mBtLeService.getNumServices() == 0)
				discoverServices();
			else {
				displayServices();
				enableDataCollection(true);
			}
		}
	}

	//
	// Application implementation
	//
	private void updateSensorList() {
		mEnabledSensors.clear();

		for (int i = 0; i < Sensor.SENSOR_LIST.length; i++) {
			Sensor sensor = Sensor.SENSOR_LIST[i];
			if (isEnabledByPrefs(sensor)) {
				mEnabledSensors.add(sensor);
			}
		}
	}

	String firmwareRevision() {
		return mFwRev;
	}

	boolean isEnabledByPrefs(final Sensor sensor) {
		String preferenceKeyString = "pref_"
		    + sensor.name().toLowerCase(Locale.ENGLISH) + "_on";

		SharedPreferences prefs = PreferenceManager
		    .getDefaultSharedPreferences(this);

		Boolean defaultValue = true;
		return prefs.getBoolean(preferenceKeyString, defaultValue);
	}

	BluetoothGattService getOadService() {
		return mOadService;
	}

	BluetoothGattService getConnControlService() {
		return mConnControlService;
	}

	private void startOadActivity() {
    // For the moment OAD does not work on Galaxy S3 (disconnects on parameter update)
    if (Build.MODEL.contains("I9300")) {
			Toast.makeText(this, "OAD not available on this Android device",
			    Toast.LENGTH_LONG).show();
			return;
    }
    	
		if (mOadService != null && mConnControlService != null) {
			// Disable sensors and notifications when the OAD dialog is open
			enableDataCollection(false);
			// Launch OAD
			final Intent i = new Intent(this, FwUpdateActivity.class);
			startActivityForResult(i, FWUPDATE_ACT_REQ);
		} else {
			Toast.makeText(this, "OAD not available on this BLE device",
			    Toast.LENGTH_LONG).show();
		}
	}

	private void startPreferenceActivity() {
		// Disable sensors and notifications when the settings dialog is open
		enableDataCollection(false);
		// Launch preferences
		final Intent i = new Intent(this, PreferencesActivity.class);
		i.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT,
		    PreferencesFragment.class.getName());
		i.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
		i.putExtra(EXTRA_DEVICE, mBluetoothDevice);
		startActivityForResult(i, PREF_ACT_REQ);
	}

	private void checkOad() {
		// Check if OAD is supported (needs OAD and Connection Control service)
		mOadService = null;
		mConnControlService = null;

		for (int i = 0; i < mServiceList.size()
		    && (mOadService == null || mConnControlService == null); i++) {
			BluetoothGattService srv = mServiceList.get(i);
			if (srv.getUuid().equals(GattInfo.OAD_SERVICE_UUID)) {
				mOadService = srv;
			}
			if (srv.getUuid().equals(GattInfo.CC_SERVICE_UUID)) {
				mConnControlService = srv;
			}
		}
	}

	private void discoverServices() {
		if (mBtGatt.discoverServices()) {
			mServiceList.clear();
			setBusy(true);
			setStatus("Service discovery started");
		} else {
			setError("Service discovery start failed");
		}
	}

	private void setBusy(boolean b) {
		//mDeviceView.setBusy(b);
	}

	private void displayServices() {
		mServicesRdy = true;

		try {
			mServiceList = mBtLeService.getSupportedGattServices();
		} catch (Exception e) {
			e.printStackTrace();
			mServicesRdy = false;
		}

		// Characteristics descriptor readout done
		if (!mServicesRdy) {
			setError("Failed to read services");
		}
	}

	private void setError(String txt) {
		setBusy(false);
		Toast.makeText(this, txt, Toast.LENGTH_LONG).show();
	}

	private void setStatus(String txt) {
		Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
	}

	private void enableSensors(boolean f) {
		final boolean enable = f;

		for (Sensor sensor : mEnabledSensors) {
			UUID servUuid = sensor.getService();
			UUID confUuid = sensor.getConfig();

			// Skip keys
			if (confUuid == null)
				break;


			BluetoothGattService serv = mBtGatt.getService(servUuid);
			if (serv != null) {
				BluetoothGattCharacteristic charac = serv.getCharacteristic(confUuid);
				byte value = enable ? sensor.getEnableSensorCode()
				    : Sensor.DISABLE_SENSOR_CODE;
				if (mBtLeService.writeCharacteristic(charac, value)) {
					mBtLeService.waitIdle(GATT_TIMEOUT);
				} else {
					setError("Sensor config failed: " + serv.getUuid().toString());
					break;
				}
			}
		}
	}

	private void enableNotifications(boolean f) {
		final boolean enable = f;

		for (Sensor sensor : mEnabledSensors) {
			UUID servUuid = sensor.getService();
			UUID dataUuid = sensor.getData();
			BluetoothGattService serv = mBtGatt.getService(servUuid);
			if (serv != null) {
				BluetoothGattCharacteristic charac = serv.getCharacteristic(dataUuid);

				if (mBtLeService.setCharacteristicNotification(charac, enable)) {
					mBtLeService.waitIdle(GATT_TIMEOUT);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					setError("Sensor notification failed: " + serv.getUuid().toString());
					break;
				}
			}
		}
	}

	private void enableDataCollection(boolean enable) {
		setBusy(true);
		enableSensors(enable);
		enableNotifications(enable);
		setBusy(false);
	}

	/*
	 * Calibrating the barometer includes
	 *
	 * 1. Write calibration code to configuration characteristic. 2. Read
	 * calibration values from sensor, either with notifications or a normal read.
	 * 3. Use calibration values in formulas when interpreting sensor values.
	 */
	private void calibrateBarometer() {

		UUID servUuid = Sensor.BAROMETER.getService();
		UUID configUuid = Sensor.BAROMETER.getConfig();
		BluetoothGattService serv = mBtGatt.getService(servUuid);
		BluetoothGattCharacteristic config = serv.getCharacteristic(configUuid);

		// Write the calibration code to the configuration registers
		mBtLeService.writeCharacteristic(config, Sensor.CALIBRATE_SENSOR_CODE);
		mBtLeService.waitIdle(GATT_TIMEOUT);
		BluetoothGattCharacteristic calibrationCharacteristic = serv
		    .getCharacteristic(SensorTagGatt.UUID_BAR_CALI);
		mBtLeService.readCharacteristic(calibrationCharacteristic);
		mBtLeService.waitIdle(GATT_TIMEOUT);
	}

	private void getFirmwareRevison() {
		UUID servUuid = SensorTagGatt.UUID_DEVINFO_SERV;
		UUID charUuid = SensorTagGatt.UUID_DEVINFO_FWREV;
		BluetoothGattService serv = mBtGatt.getService(servUuid);
		BluetoothGattCharacteristic charFwrev = serv.getCharacteristic(charUuid);

		// Write the calibration code to the configuration registers
		mBtLeService.readCharacteristic(charFwrev);
		mBtLeService.waitIdle(GATT_TIMEOUT);

	}

	// Activity result handling
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case PREF_ACT_REQ:
			//mDeviceView.updateVisibility();
			Toast.makeText(this, "Applying preferences", Toast.LENGTH_SHORT).show();
			if (!mIsReceiving) {
				mIsReceiving = true;
				registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
			}

			updateSensorList();
			enableDataCollection(true);
			break;
		case FWUPDATE_ACT_REQ:
			// FW update cancelled so resume
			enableDataCollection(true);
			break;
		default:
			setError("Unknown request code");
			break;
		}
	}

	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			int status = intent.getIntExtra(BluetoothLeService.EXTRA_STATUS,
			    BluetoothGatt.GATT_SUCCESS);

			if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				if (status == BluetoothGatt.GATT_SUCCESS) {
					setStatus("Service discovery complete");
					displayServices();
					checkOad();
					enableDataCollection(true);
					getFirmwareRevison();
				} else {
					Toast.makeText(getApplication(), "Service discovery failed",
					    Toast.LENGTH_LONG).show();
					return;
				}
			} else if (BluetoothLeService.ACTION_DATA_NOTIFY.equals(action)) {
				// Notification
				byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
				String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
				onCharacteristicChanged(uuidStr, value);
			} else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action)) {
				// Data written
				String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
				onCharacteristicWrite(uuidStr, status);
			} else if (BluetoothLeService.ACTION_DATA_READ.equals(action)) {
				// Data read
				String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
				byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
				onCharacteristicsRead(uuidStr, value, status);
			}

			if (status != BluetoothGatt.GATT_SUCCESS) {
				setError("GATT error code: " + status);
			}
		}
	};

	private void onCharacteristicWrite(String uuidStr, int status) {
		// Log.d(TAG, "onCharacteristicWrite: " + uuidStr);
	}

	private void onCharacteristicChanged(String uuidStr, byte[] value) {
		//if (mDeviceView != null) {
		//	mDeviceView.onCharacteristicChanged(uuidStr, value);
		//}
	}

	private void onCharacteristicsRead(String uuidStr, byte[] value, int status) {
		// Log.i(TAG, "onCharacteristicsRead: " + uuidStr);

		if (uuidStr.equals(SensorTagGatt.UUID_DEVINFO_FWREV.toString())) {
			mFwRev = new String(value, 0, 3);
			Toast.makeText(this, "Firmware revision: " + mFwRev,Toast.LENGTH_LONG).show();
		}

	}

}
