/**************************************************************************************************
  Filename:       MainActivity.java
  Revised:        $Date: 2014-01-02 18:55:00 +0100 (to, 02 jan 2014) $
  Revision:       $Revision: 28743 $

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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.BLEUtil;
import com.mooshim.mooshimeter.common.BleDeviceInfo;
import com.mooshim.mooshimeter.util.CustomToast;

public class MainActivity extends ViewPagerActivity {
	// URLs
	private static final Uri URL_FORUM = Uri
	    .parse("http://e2e.ti.com/support/low_power_rf/default.aspx?DCMP=hpa_hpa_community&HQS=NotApplicable+OT+lprf-forum");

	// Requests to other activities
	private static final int REQ_ENABLE_BT = 0;
	private static final int REQ_DEVICE_ACT = 1;

	// GUI
	private static MainActivity mThis = null;
	private ScanView mScanView;
	private Intent mDeviceIntent;
	private static final int STATUS_DURATION = 5;

	// BLE management
	private boolean mBtAdapterEnabled = false;
	private boolean mBleSupported = true;
	private boolean mScanning = false;
	private int mNumDevs = 0;
	private int mConnIndex = NO_DEVICE;
	private List<BleDeviceInfo> mDeviceInfoList;
	private static BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBtAdapter = null;
	private BluetoothDevice mBluetoothDevice = null;
    private BLEUtil mBleUtil = null;
	private IntentFilter mFilter;

	// Housekeeping
	private static final int NO_DEVICE = -1;
	private boolean mInitialised = false;
	SharedPreferences prefs = null;

	public MainActivity() {
		mThis = this;
		mResourceFragmentPager = R.layout.fragment_pager;
		mResourceIdPager = R.id.pager;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// Start the application
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// Use this check to determine whether BLE is supported on the device. Then
		// you can selectively disable BLE-related features.
		if (!getPackageManager().hasSystemFeature(
		    PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG)
			    .show();
			mBleSupported = false;
		}

		// Initializes a Bluetooth adapter. For API level 18 and above, get a
		// reference to BluetoothAdapter through BluetoothManager.
		mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        mBleUtil = BLEUtil.getInstance(this);

		// Checks if Bluetooth is supported on the device.
		if (mBtAdapter == null) {
			Toast.makeText(this, R.string.bt_not_supported, Toast.LENGTH_LONG).show();
			mBleSupported = false;
		}

		// Initialize device list container and device filter
		mDeviceInfoList = new ArrayList<BleDeviceInfo>();
		Resources res = getResources();

		// Create the fragments and add them to the view pager and tabs
		mScanView = new ScanView();
		mSectionsPagerAdapter.addSection(mScanView, "BLE Device List");
	}

	@Override
	public void onDestroy() {
		// Log.e(TAG,"onDestroy");
		super.onDestroy();
		mBtAdapter = null;
        mBleUtil.close();
        unregisterReceiver(mReceiver);
		
		// Clear cache
		File cache = getCacheDir();
		String path = cache.getPath();
    try {
	    Runtime.getRuntime().exec(String.format("rm -rf %s", path));
    } catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
    }
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case android.R.id.home:
			onBackPressed();
			return true;
		case R.id.opt_bt:
			onBluetooth();
			break;
		case R.id.opt_about:
			//onAbout();
			break;
		case R.id.opt_exit:
			Toast.makeText(this, "Exit...", Toast.LENGTH_SHORT).show();
			finish();
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		this.optionsMenu = menu;
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	private void onBluetooth() {
		Intent settingsIntent = new Intent(
		    android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
		startActivity(settingsIntent);
	}

	void onScanViewReady(View view) {
		// Initial state of widgets
		updateGuiState();

		if (!mInitialised) {
			// Broadcast receiver
            mFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            mFilter.addAction(BLEUtil.ACTION_GATT_DISCONNECTED);
			registerReceiver(mReceiver, mFilter);
			mBtAdapterEnabled = mBtAdapter.isEnabled();
			mInitialised = true;
		} else {
			mScanView.notifyDataSetChanged();
		}
        startScan();
	}

	public void onBtnScan(View view) {
		if (mScanning) {
			stopScan();
		} else {
			startScan();
		}
	}

	void onDeviceClick() {
		if (mNumDevs > 0) {
			int connState = mBluetoothManager.getConnectionState(mBluetoothDevice,
			    BluetoothGatt.GATT);

			switch (connState) {
			case BluetoothGatt.STATE_CONNECTED:
				mBleUtil.disconnect();
				break;
			case BluetoothGatt.STATE_DISCONNECTED:
				mBleUtil.connect(mBluetoothDevice.getAddress(), new BLEUtil.BLEUtilCB() {
                    @Override
                    public void run() {
                        if (error == BluetoothGatt.GATT_SUCCESS) {
                            setBusy(false);
                            startDeviceActivity();
                        } else	{
                            setError("Connect failed. Status: " + error);
                        }
                    }
                });
				break;
			default:
				setError("Device busy (connecting/disconnecting)");
				break;
			}
		}
	}

	private void startScan() {
		// Start device discovery
		if (mBleSupported) {
			mNumDevs = 0;
			mDeviceInfoList.clear();
			mScanView.notifyDataSetChanged();
			scanLeDevice(true);
			mScanView.updateGui(mScanning);
			if (!mScanning) {
				setError("Device discovery start failed");
				setBusy(false);
			}
		} else {
			setError("BLE not supported on this device");
		}

	}

	private void stopScan() {
		mScanning = false;
		mScanView.updateGui(false);
		scanLeDevice(false);
	}

	private void startDeviceActivity() {
		mDeviceIntent = new Intent(this, DeviceActivity.class);
		mDeviceIntent.putExtra(DeviceActivity.EXTRA_DEVICE, mBluetoothDevice);
		startActivityForResult(mDeviceIntent, REQ_DEVICE_ACT);
	}

	private void stopDeviceActivity() {
		finishActivity(REQ_DEVICE_ACT);
	}

	public void onDeviceClick(final int pos) {

		if (mScanning)
			stopScan();

		setBusy(true);
		mBluetoothDevice = mDeviceInfoList.get(pos).getBluetoothDevice();
		if (mConnIndex == NO_DEVICE) {
			mScanView.setStatus("Connecting");
			mConnIndex = pos;
			onDeviceClick();
		} else {
			mScanView.setStatus("Disconnecting");
			if (mConnIndex != NO_DEVICE) {
                mBleUtil.disconnect();
			}
		}
	}

	public void onScanTimeout() {
		runOnUiThread(new Runnable() {
			public void run() {
				stopScan();
			}
		});
	}

	public void onConnectTimeout() {
		runOnUiThread(new Runnable() {
			public void run() {
				setError("Connection timed out");
			}
		});
		if (mConnIndex != NO_DEVICE) {
            mBleUtil.disconnect();
			mConnIndex = NO_DEVICE;
		}
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// GUI methods
	//
	public void updateGuiState() {
		boolean mBtEnabled = mBtAdapter.isEnabled();

		if (mBtEnabled) {
			if (mScanning) {
				// BLE Host connected
				if (mConnIndex != NO_DEVICE) {
					String txt = mBluetoothDevice.getName() + " connected";
					mScanView.setStatus(txt);
				} else {
					mScanView.setStatus(mNumDevs + " devices");
				}
			}
		} else {
			mDeviceInfoList.clear();
			mScanView.notifyDataSetChanged();
		}
	}

	private void setBusy(final boolean f) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mScanView.setBusy(f);
            }
        });
	}

	void setError(String txt) {
		mScanView.setError(txt);
		CustomToast.middleBottom(this, "Turning BT adapter off and on again may fix Android BLE stack problems");
	}

	private void addDevice(BleDeviceInfo device) {
		mNumDevs++;
		mDeviceInfoList.add(device);
		mScanView.notifyDataSetChanged();
		if (mNumDevs > 1)
			mScanView.setStatus(mNumDevs + " devices");
		else
			mScanView.setStatus("1 device");
	}

	private boolean deviceInfoExists(String address) {
		for (int i = 0; i < mDeviceInfoList.size(); i++) {
			if (mDeviceInfoList.get(i).getBluetoothDevice().getAddress()
			    .equals(address)) {
				return true;
			}
		}
		return false;
	}

	private BleDeviceInfo findDeviceInfo(BluetoothDevice device) {
		for (int i = 0; i < mDeviceInfoList.size(); i++) {
			if (mDeviceInfoList.get(i).getBluetoothDevice().getAddress()
			    .equals(device.getAddress())) {
				return mDeviceInfoList.get(i);
			}
		}
		return null;
	}

	private boolean scanLeDevice(boolean enable) {
		if (enable) {
            /*
            // FIXME: jwhong: I've had no luck getting the filtered scan to work.
            // I'm leaving this code here in case it starts working in a future android release.
            UUID[] service_uuids = new UUID[1];
            service_uuids[0] = UUID.fromString("1bc5ffa0-0200-62ab-e411-f254e005dbd4");
			mScanning = mBtAdapter.startLeScan(service_uuids, mLeScanCallback);
			*/
            mScanning = mBtAdapter.startLeScan(mLeScanCallback);
		} else {
			mScanning = false;
			mBtAdapter.stopLeScan(mLeScanCallback);
		}
		return mScanning;
	}

	List<BleDeviceInfo> getDeviceInfoList() {
		return mDeviceInfoList;
	}

	// Activity result handling
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case REQ_DEVICE_ACT:
			// When the device activity has finished: disconnect the device
			if (mConnIndex != NO_DEVICE) {
                mBleUtil.disconnect();
			}
			break;

		case REQ_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {

				Toast.makeText(this, R.string.bt_on, Toast.LENGTH_SHORT).show();
			} else {
				// User did not enable Bluetooth or an error occurred
				Toast.makeText(this, R.string.bt_not_on, Toast.LENGTH_SHORT).show();
				finish();
			}
			break;
		default:
			CustomToast.middleBottom(this, "Unknown request code: " + requestCode);

			// Log.e(TAG, "Unknown request code");
			break;
		}
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// Broadcasted actions from Bluetooth adapter
	//
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
				// Bluetooth adapter state change
				switch (mBtAdapter.getState()) {
				case BluetoothAdapter.STATE_ON:
					mConnIndex = NO_DEVICE;
					break;
				case BluetoothAdapter.STATE_OFF:
					Toast.makeText(context, R.string.app_closing, Toast.LENGTH_LONG)
					    .show();
					finish();
					break;
				default:
                    // Log.w(TAG, "Action STATE CHANGED not processed ");
					break;
				}
				updateGuiState();
            } else if (BLEUtil.ACTION_GATT_DISCONNECTED.equals(action)) {
				// GATT disconnect
				int status = intent.getIntExtra(BLEUtil.EXTRA_STATUS,
				    BluetoothGatt.GATT_FAILURE);
				stopDeviceActivity();
				if (status == BluetoothGatt.GATT_SUCCESS) {
					setBusy(false);
					//mScanView.setStatus(mBluetoothDevice.getName() + " disconnected",
					//    STATUS_DURATION);
				} else {
					setError("Disconnect failed. Status: " + status);
				}
				mConnIndex = NO_DEVICE;
			}
		}
	};

	// Device scan callback.
	// NB! Nexus 4 and Nexus 7 (2012) only provide one scan result per scan
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		public void onLeScan(final BluetoothDevice device, final int rssi,
		    final byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				public void run() {
					// Filter devices
                    // FIXME: Android doesn't seem to be filtering devices correctly based on UUIDs
                    // FIXME: For now I will examine the scan record manually
                    Log.i(null, String.valueOf(scanRecord));
                    boolean is_meter = false;
                    int build_time = 0;
                    int field_length = 0;
                    int field_id = 0;
                    for(int i = 0; i < scanRecord.length;) {
                        if(field_length == 0) {
                            field_length = scanRecord[i] & 0xFF;
                            field_id = 0;
                            i++;
                        }
                        else if(field_id == 0) {
                            field_id = scanRecord[i] & 0xFF;
                            field_length--;
                            i++;
                        } else {
                            switch(field_id) {
                                case 6:
                                    // This is a UUID listing
                                    // TODO: Move these hardcoded values to a seperate file
                                    final byte[] moosh_service_uuid = {(byte)0xd4, (byte)0xdb, (byte)0x05, (byte)0xe0, (byte)0x54, (byte)0xf2, (byte)0x11, (byte)0xe4, (byte)0xab, (byte)0x62, (byte)0x00, (byte)0x02, (byte)0xa0, (byte)0xff, (byte)0xc5, (byte)0x1b};
                                    // Check expected length
                                    if(field_length != 16) { break; }
                                    // Check that there's enough data in the buffer
                                    if(i+field_length >= scanRecord.length) {break;}
                                    // Check the value against the expected service UUID
                                    byte[] received_uuid = Arrays.copyOfRange(scanRecord, i, i+field_length);
                                    if(Arrays.equals(received_uuid, moosh_service_uuid)) {
                                        Log.i(null, "Mooshimeter found");
                                        is_meter = true;
                                    } else {
                                        Log.i(null, "Scanned device is not a meter");
                                    }
                                    break;
                                case 255:
                                    // This is a manufacturer-specific data listing
                                    // In the case of the Mooshimeter this is the build time
                                    // in UTC seconds
                                    // Check expected length
                                    if(field_length != 4) { break; }
                                    // Check that there's enough data in the buffer
                                    if(i+field_length >= scanRecord.length) {break;}
                                    for(int j = 3; j >= 0; j--) {
                                        build_time |= (scanRecord[i+j] & 0xFF) << (8*j);
                                    }
                                    break;
                            }
                            // Cleanup
                            i += field_length;
                            field_length = 0;
                            field_id = 0;
                        }
                    }

                    // TODO: Check the firmware version against the bundled binary and prompt for upload if meter is out of date
                    // TODO: Display the firmware version in the scan result
                    if(is_meter) {
                        if (!deviceInfoExists(device.getAddress())) {
                            // New device
                            BleDeviceInfo deviceInfo = new BleDeviceInfo(device, rssi, build_time);
                            addDevice(deviceInfo);
                        } else {
                            // Already in list, update RSSI info
                            BleDeviceInfo deviceInfo = findDeviceInfo(device);
                            deviceInfo.updateRssi(rssi);
                            mScanView.notifyDataSetChanged();
                        }
                    }
				}
			});
		}
	};

}
