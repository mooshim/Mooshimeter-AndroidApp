/*
 * Copyright (c) Mooshim Engineering LLC 2015.
 *
 * This file is part of Mooshimeter-AndroidApp.
 *
 * Foobar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Mooshimeter-AndroidApp.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mooshim.mooshimeter.main;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.internal.view.menu.MenuWrapperFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.BLEUtil;
import com.mooshim.mooshimeter.common.BleDeviceInfo;
import com.mooshim.mooshimeter.common.MooshimeterDevice;
import com.mooshim.mooshimeter.util.WatchDog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class ScanActivity extends FragmentActivity {
    // Defines
    private static String TAG = "ScanActivity";
    private enum ScanViewState {
        IDLE,
        SCANNING,
        CONNECTING,
    };
    private static final int NO_DEVICE = -1;
    private static final int REQ_ENABLE_BT = 0;
    private static final int REQ_DEVICE_ACT = 1;
    private static final int REQ_OAD_ACT = 2;

    final static byte[] mMeterServiceUUID = uuidToBytes(MooshimeterDevice.mUUID.METER_SERVICE);
    final static byte[] mOADServiceUUID   = uuidToBytes(MooshimeterDevice.mUUID.OAD_SERVICE_UUID);

    // Housekeeping
    private ScanViewState mScanViewState = ScanViewState.IDLE;
    private WatchDog mWatchDog = null;

    private boolean mUpdateFirmwareFlag = false;// Update the firmware of the selected meter
    private int mConnIndex = NO_DEVICE;         // The list index of the connected device
    private List<BleDeviceInfo> mDeviceInfoList;// The list of detected Mooshimeters
    private BLEUtil mBleUtil = null;            // The singleton BLEUtil.  Used for managing connection


    // GUI Widgets
    private DeviceListAdapter mDeviceAdapter = null;
    private TextView mEmptyMsg;
    private TextView mStatus;
    private Button mBtnScan = null;
    private ListView mDeviceListView = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show();
        }

        // Checks if Bluetooth is supported on the device.
        if (BluetoothAdapter.getDefaultAdapter() == null) {
            Toast.makeText(this, R.string.bt_not_supported, Toast.LENGTH_LONG).show();
            setError("BLE not supported on this device");
        }

        if( !BluetoothAdapter.getDefaultAdapter().isEnabled() ) {
            // Request BT adapter to be turned on
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQ_ENABLE_BT);
        }

        setContentView(R.layout.fragment_scan);
        // Initialize widgets
        mStatus         = (TextView) findViewById(R.id.status);
        mBtnScan        = (Button)   findViewById(R.id.btn_scan);
        mDeviceListView = (ListView) findViewById(R.id.device_list);
        mEmptyMsg       = (TextView) findViewById(R.id.no_device);

        mDeviceListView.setClickable(true);
        mDeviceListView.setOnItemClickListener(mDeviceClickListener);

        mWatchDog = new WatchDog();
        mBleUtil = BLEUtil.getInstance(this);
        mDeviceInfoList = new ArrayList<BleDeviceInfo>();
        mDeviceAdapter = new DeviceListAdapter(this);
        mDeviceListView.setAdapter(mDeviceAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        moveState(ScanViewState.SCANNING);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.opt_prefs:
                break;
            //case R.id.opt_fwupdate:
            //    mUpdateFirmwareFlag ^= true;
            //    if(mUpdateFirmwareFlag) {
            //        setStatus("Select a meter to update firmware");
            //    } else {
            //        setStatus("");
            //    }
            //    break;
            case R.id.opt_exit:
                Toast.makeText(this, "Goodbye!", Toast.LENGTH_LONG).show();
                finish();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_DEVICE_ACT:
                moveState(ScanViewState.IDLE);
                break;
            case REQ_OAD_ACT:
                moveState(ScanViewState.IDLE);
                break;
            default:
                setError("Unknown request code");
                break;
        }
    }

    // Master state machine for the scan view

    private synchronized void moveState(ScanViewState newState) {
        switch(mScanViewState) {
            case IDLE:
                switch(newState) {
                    case SCANNING:
                        mWatchDog.setCB(new Runnable() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        moveState(ScanViewState.IDLE);
                                    }
                                });
                            }
                        });
                        mWatchDog.feed(10000);
                        updateScanningButton(true);
                        mDeviceInfoList.clear();
                        mDeviceAdapter.notifyDataSetChanged();
                        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                        if( !bluetoothAdapter.startLeScan(mLeScanCallback) ) {
                            // Starting the scan failed!
                            Log.e(TAG,"Failed to start BLE Scan");
                            setError("Failed to start scan");
                            mWatchDog.feed(500); // FIXME: There should be a way to abort state change
                        }
                        break;
                    case CONNECTING:
                        mWatchDog.setCB( new Runnable() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        moveState(ScanViewState.IDLE);
                                        setError("Connection timed out.");
                                    }
                                });
                            }
                        } );
                        mWatchDog.feed(10000);
                        mBtnScan.setEnabled(false);
                        mDeviceAdapter.notifyDataSetChanged(); // Force disabling of all Connect buttons
                        setStatus("Connecting...");
                        final BluetoothDevice bluetoothDevice   = mDeviceInfoList.get(mConnIndex).getBluetoothDevice();
                        mBleUtil.clear(); // fixme shouldn't be necessary
                        mBleUtil.connect(bluetoothDevice.getAddress(), new BLEUtil.BLEUtilCB() {
                            @Override
                            public void run() {
                                if (error == BluetoothGatt.GATT_SUCCESS) {
                                    mWatchDog.feed();
                                    mWatchDog.stop();
                                    if(mBleUtil.setPrimaryService(MooshimeterDevice.mUUID.METER_SERVICE)) {
                                        // Initialize the meter
                                        MooshimeterDevice.clearInstance();
                                        MooshimeterDevice.Initialize(getApplicationContext(), new Runnable() {
                                            @Override
                                            public void run() {
                                                if(mUpdateFirmwareFlag) {
                                                    // The user has indicated they want to load firmware
                                                    // Reboot the meter and connect in OAD mode
                                                    final MooshimeterDevice meter = MooshimeterDevice.getInstance();
                                                    setStatus("Booting to OAD mode...");
                                                    meter.reconnectInOADMode(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            setStatus("Success!  Starting firmware update");
                                                            mWatchDog.stop();
                                                            startOADActivity();
                                                        }
                                                    });
                                                } else {
                                                    mWatchDog.stop();
                                                    startDeviceActivity();
                                                }
                                            }
                                        });
                                    } else if( mBleUtil.setPrimaryService(MooshimeterDevice.mUUID.OAD_SERVICE_UUID)) {
                                        mWatchDog.stop();
                                        startOADActivity();
                                    } else {
                                        Log.e(TAG, "Couldn't find a service I recognized!");
                                    }
                                } else	{
                                    setError("Connect failed. Status: " + error);
                                    moveState(ScanViewState.IDLE);
                                }
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                if(mScanViewState == ScanViewState.CONNECTING) {
                                    mBleUtil.clear();
                                    setError("Device disconnected!");
                                    moveState(ScanViewState.IDLE);
                                    stopDeviceActivity();
                                }
                            }
                        });
                        break;
                    default:
                        Log.e(TAG, "Illegal transition");
                        break;
                }
                break;
            case SCANNING:
                switch(newState) {
                    case IDLE:
                        updateScanningButton(false);
                        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                        bluetoothAdapter.stopLeScan(mLeScanCallback);
                        break;
                    default:
                        Log.e(TAG, "Illegal transition");
                        break;
                }
                break;
            case CONNECTING:
                switch(newState) {
                    case IDLE:
                        mBleUtil.disconnect(null);
                        mBtnScan.setEnabled(true);
                        mDeviceAdapter.notifyDataSetChanged(); // Force enabling of all Connect buttons
                        break;
                    default:
                        Log.e(TAG, "Illegal transition");
                        break;
                }
                break;
        }
        mScanViewState = newState;
    }




    /////////////////////////////
    // GUI Element Manipulation
    /////////////////////////////

    void updateScanningButton(boolean scanning) {
        if (mBtnScan == null)
            return; // UI not ready
        if (scanning) {
            // Indicate that scanning has started
            mBtnScan.setText("Stop");
            mBtnScan.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_action_cancel, 0);
            mStatus.setTextAppearance(this, R.style.statusStyle_Busy);
            mStatus.setText("Scanning...");
            mEmptyMsg.setText(R.string.nodevice);
        } else {
            // Indicate that scanning has stopped
            mStatus.setTextAppearance(this, R.style.statusStyle_Success);
            mBtnScan.setText("Scan");
            mBtnScan.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_action_refresh, 0);
            mEmptyMsg.setText(R.string.scan_advice);
            mDeviceAdapter.notifyDataSetChanged();
        }
        if(mDeviceInfoList.size() == 0) {
            mEmptyMsg.setVisibility(View.VISIBLE);
            mStatus.setText("No devices found");
        } else {
            mEmptyMsg.setVisibility(View.GONE);
        }
    }

    void setStatus(final String txt) {
        final Activity a = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatus.setText(txt);
                mStatus.setTextAppearance(a, R.style.statusStyle_Success);
            }
        });
    }

    void setError(final String txt) {
        final Activity a = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatus.setText(txt);
                mStatus.setTextAppearance(a, R.style.statusStyle_Failure);
            }
        });
    }

    private void addDevice(BleDeviceInfo device) {
        mEmptyMsg.setVisibility(View.GONE);
        mDeviceInfoList.add(device);
        mDeviceAdapter.notifyDataSetChanged();
        if (mDeviceInfoList.size() > 1)
            setStatus(mDeviceInfoList.size() + " devices");
        else
            setStatus("1 device");
    }

    /////////////////////////////
    // Data Structure Manipulation
    /////////////////////////////

    private boolean deviceInfoExists(String address) {
        return findDeviceInfo(address) != null;
    }

    private BleDeviceInfo findDeviceInfo(String address) {
        for (int i = 0; i < mDeviceInfoList.size(); i++) {
            if (mDeviceInfoList.get(i).getBluetoothDevice().getAddress().equals(address)) {
                return mDeviceInfoList.get(i);
            }
        }
        return null;
    }

    private void startDeviceActivity() {
        Intent deviceIntent = new Intent(this, DeviceActivity.class);
        startActivityForResult(deviceIntent, REQ_DEVICE_ACT);
    }
    private void stopDeviceActivity() {
        finishActivity(REQ_DEVICE_ACT);
    }

    private void startOADActivity() {
        final Intent i = new Intent(this, FwUpdateActivity.class);
        startActivityForResult(i, REQ_OAD_ACT);
    }

    /////////////////////////////
    // Listeners for BLE Events
    /////////////////////////////

    private static byte[] uuidToBytes(final UUID arg) {
        final byte[] s = arg.toString().getBytes();
        byte[] rval = new byte[16];
        for(int i = 0; i < 16; i++){ rval[i]=0; }
        // We expect 16 bytes, but UUID strings are reverse order from byte arrays
        int i = 31;
        for(byte b:s) {
            if( b >= 0x30 && b < 0x3A ) {
                b -= 0x30;
            } else if( b >= 0x41 && b < 0x47 ) {
                b -= 0x41;
                b += 10;
            } else if( b >= 0x61 && b < 0x67) {
                b -= 0x61;
                b += 10;
            } else {
                // Unrecognized symbol, probably a dash
                continue;
            }
            // Is this the top or bottom nibble?
            b <<= (i%2 == 0)?0:4;
            rval[i/2] |= b;
            i--;
        }
        return rval;
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                public void run() {
                    // Filter devices
                    // FIXME: Android doesn't seem to be filtering devices correctly based on UUIDs
                    // FIXME: For now I will examine the scan record manually
                    boolean is_meter = false;
                    boolean oad_mode = false;
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
                                    // Type 6: This is a UUID listing
                                    // Check expected length
                                    if(field_length != 16) { break; }
                                    // Check that there's enough data in the buffer
                                    if(i+field_length >= scanRecord.length) {break;}
                                    // Check the value against the expected service UUID
                                    byte[] received_uuid = Arrays.copyOfRange(scanRecord, i, i + field_length);
                                    if(Arrays.equals(received_uuid, mMeterServiceUUID)) {
                                        Log.i(null, "Mooshimeter found");
                                        is_meter = true;
                                    } else if(Arrays.equals(received_uuid, mOADServiceUUID)) {
                                        Log.i(null, "Mooshimeter found in OAD mode");
                                        is_meter = true;
                                        oad_mode = true;
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
                            BleDeviceInfo deviceInfo = new BleDeviceInfo(device, rssi, build_time, oad_mode);
                            addDevice(deviceInfo);
                        } else {
                            // Already in list, update RSSI info
                            BleDeviceInfo deviceInfo = findDeviceInfo(device.getAddress());
                            deviceInfo.updateRssi(rssi);
                            mDeviceAdapter.notifyDataSetChanged();
                        }
                    }
                }
            });
        }
    };

    /////////////////////////////
    // Listeners for GUI Events
    /////////////////////////////

    public void onBtnScan(View view) {
        switch(mScanViewState) {
            case IDLE:
                moveState(ScanViewState.SCANNING);
                break;
            case SCANNING:
                moveState(ScanViewState.IDLE);
                break;
            case CONNECTING:
                Log.e(TAG,"This button should be disabled when connecting");
                break;
        }
    }

    // Listener for device list
    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            mConnIndex = pos;
            moveState(ScanViewState.IDLE);
            moveState(ScanViewState.CONNECTING);
        }
    };

    class DeviceListAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        public DeviceListAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        public int getCount() {
            return mDeviceInfoList.size();
        }

        public Object getItem(int position) {
            return mDeviceInfoList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if (convertView != null) {
                vg = (ViewGroup) convertView;
            } else {
                vg = (ViewGroup) mInflater.inflate(R.layout.element_device, null);
            }

            BleDeviceInfo deviceInfo = mDeviceInfoList.get(position);
            BluetoothDevice device = deviceInfo.getBluetoothDevice();
            int rssi = deviceInfo.getRssi();
            String name;
            String build;
            if(deviceInfo.mOADMode) {
                name = "Bootloader";
            } else {
                name = device.getName();
                if (name == null) {
                    name = new String("Unknown device");
                }
            }

            if(deviceInfo.mBuildTime == 0) {
                build = "Invalid firmware";
            } else {
                build = "Build: "+deviceInfo.mBuildTime;
            }

            String descr = name + "\n" + build + "\nRssi: " + rssi + " dBm";
            ((TextView) vg.findViewById(R.id.descr)).setText(descr);

            // Disable connect button when connecting or connected
            Button bv = (Button)vg.findViewById(R.id.btnConnect);
            bv.setEnabled(mScanViewState != ScanViewState.CONNECTING);

            return vg;
        }
    }
}
