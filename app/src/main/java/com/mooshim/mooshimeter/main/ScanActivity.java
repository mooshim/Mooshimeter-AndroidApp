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
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.MooshimeterDevice;
import com.mooshim.mooshimeter.common.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ScanActivity extends FragmentActivity {
    // Defines
    private static String TAG = "ScanActivity";
    private static final int NO_DEVICE = -1;
    private static final int REQ_ENABLE_BT = 0;
    private static final int REQ_DEVICE_ACT = 1;
    private static final int REQ_OAD_ACT = 2;

    final static byte[] mMeterServiceUUID = uuidToBytes(MooshimeterDevice.mUUID.METER_SERVICE);
    final static byte[] mOADServiceUUID   = uuidToBytes(MooshimeterDevice.mUUID.OAD_SERVICE_UUID);

    private static final List<MooshimeterDevice>        mMeterList = new ArrayList<MooshimeterDevice>();
    private static final Map<String,MooshimeterDevice>  mMeterDict = new HashMap<String, MooshimeterDevice>();
    private static final List<ViewGroup>                mTileList = new ArrayList<ViewGroup>();

    // Housekeeping
    private static final Lock utilLock = new ReentrantLock();

    // GUI Widgets
    private TextView mEmptyMsg;
    private TextView mStatus;
    private Button mBtnScan = null;
    private LinearLayout mDeviceScrollView = null;

    // Flags for the scan logic thread
    private static boolean mScanOngoing = false;

    private LayoutInflater mInflater;

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
        mStatus           = (TextView)     findViewById(R.id.status);
        mBtnScan          = (Button)       findViewById(R.id.btn_scan);
        mDeviceScrollView = (LinearLayout) findViewById(R.id.device_list);
        mEmptyMsg         = (TextView)     findViewById(R.id.no_device);

        mDeviceScrollView.setClickable(true);

        mInflater = LayoutInflater.from(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        startScan();
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
                startScan();
                break;
            case REQ_OAD_ACT:
                startScan();
                break;
            default:
                setError("Unknown request code");
                break;
        }
    }

    public static MooshimeterDevice getDeviceWithAddress(String addr) {
        return mMeterDict.get(addr);
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
            refreshAllMeterTiles();
        }
        if(mMeterDict.size() == 0) {
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

    private void addDevice(final MooshimeterDevice d) {
        mEmptyMsg.setVisibility(View.GONE);

        if(mMeterList.contains(d)) {
            // The meter as already been added
            Log.e(TAG, "Tried to add the same meter twice");
            return;
        }

        mMeterList.add(d);
        mMeterDict.put(d.getAddress(), d);

        final LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(mDeviceScrollView.getLayoutParams());

        wrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(    d.mConnectionState == BluetoothProfile.STATE_CONNECTED
                        || d.mConnectionState == BluetoothProfile.STATE_CONNECTING ) {
                    startSingleMeterActivity(d);
                } else {
                    final Button bv = (Button)view.findViewById(R.id.btnConnect);
                    toggleConnectionState(bv,d);
                }
            }
        });

        wrapper.addView(mInflater.inflate(R.layout.element_mm_titlebar, wrapper, false));
        //wrapper.setTag(Integer.valueOf(R.layout.element_mm_titlebar));

        refreshMeterTile(d, wrapper);
        mDeviceScrollView.addView(wrapper);
        mTileList.add(wrapper);

        if (mMeterList.size() > 1)
            setStatus(mMeterList.size() + " devices");
        else
            setStatus("1 device");
    }

    private void refreshMeterTile(final MooshimeterDevice d, final ViewGroup wrapper) {
        if(wrapper.getChildCount()==0) {
            Log.e(TAG,"Received empty wrapper");
        }
        if(wrapper.getChildCount()>0) {
            // Update the title bar
            int rssi = d.mRssi;
            String name;
            String build;
            if(d.mOADMode) {
                name = "Bootloader";
            } else {
                name = d.getBLEDevice().getName();
                if (name == null) {
                    name = "Unknown device";
                }
            }

            if(d.mBuildTime == 0) {
                build = "Invalid firmware";
            } else {
                build = "Build: "+d.mBuildTime;
            }

            String descr = name + "\n" + build + "\nRssi: " + rssi + " dBm";
            ((TextView) wrapper.findViewById(R.id.descr)).setText(descr);

            final Button bv = (Button)wrapper.findViewById(R.id.btnConnect);

            int bgid = d.mConnectionState==BluetoothProfile.STATE_CONNECTED ? R.drawable.connected:R.drawable.disconnected;
            bv.setBackground(getResources().getDrawable(bgid));

            // Set the click listeners
            bv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    toggleConnectionState(bv,d);
                }
            });
        }
        if(d.mInitialized && !d.isInOADMode()) {
            // We are representing a connected meter
            if(wrapper.getChildCount() != 2) {
                // We need to create a new value pane
                wrapper.addView(mInflater.inflate(R.layout.element_mm_readingsbar, mDeviceScrollView, false));
            }
            Util.dispatch(new Runnable() {
                @Override
                public void run() {
                    if(!d.isNotificationEnabled(d.getChar(MooshimeterDevice.mUUID.METER_SAMPLE))) {
                        // We need to enable notifications
                        d.playSampleStream(new Runnable() {
                            @Override
                            public void run() {
                                TextView ch1 = (TextView)wrapper.findViewById(R.id.ch1_value_label);
                                TextView ch2 = (TextView)wrapper.findViewById(R.id.ch2_value_label);
                                TextView ch1_unit = (TextView)wrapper.findViewById(R.id.ch1_unit_label);
                                TextView ch2_unit = (TextView)wrapper.findViewById(R.id.ch2_unit_label);
                                valueLabelRefresh(0,d, ch1, ch1_unit);
                                valueLabelRefresh(1,d, ch2, ch2_unit);
                            }
                        });
                    }
                }
            });
        } else {
            //We are representing a disconnected meter or a meter in OAD mode
            if(wrapper.getChildCount() == 2) {
                // We need to eliminate a pane
                wrapper.removeViewAt(1);
            }
        }
    }

    private void refreshAllMeterTiles() {
        for(int i = 0; i < mDeviceScrollView.getChildCount(); i++) {
            final ViewGroup vg = mTileList.get(i);
            final MooshimeterDevice d = mMeterList.get(i);
            refreshMeterTile(d,vg);
        }
    }

    /////////////////////////////
    // Data Structure Manipulation
    /////////////////////////////

    private void startDeviceActivity(MooshimeterDevice d) {
        if(DeviceActivity.isRunning) {
            return;
        }
        Intent deviceIntent = new Intent(this, DeviceActivity.class);
        deviceIntent.putExtra("addr",d.getAddress());
        startActivityForResult(deviceIntent, REQ_DEVICE_ACT);
    }
    private void stopDeviceActivity() {
        finishActivity(REQ_DEVICE_ACT);
    }

    private void startOADActivity(MooshimeterDevice d) {
        Intent deviceIntent = new Intent(this, FwUpdateActivity.class);
        deviceIntent.putExtra("addr",d.getAddress());
        startActivityForResult(deviceIntent, REQ_OAD_ACT);
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
                        MooshimeterDevice m = mMeterDict.get(device.getAddress());
                        if(m==null) {
                            m = new MooshimeterDevice(device,getApplicationContext());
                            addDevice(m);
                        }
                        m.mRssi = rssi;
                        m.mOADMode = oad_mode;
                        m.mBuildTime = build_time;
                        refreshAllMeterTiles();
                    }
                }
            });
        }
    };

    /////////////////////////////
    // Listeners for GUI Events
    /////////////////////////////

    public synchronized void startScan() {
        if(mScanOngoing) return;
        mScanOngoing = true;

        final Handler h = new Handler();
        mBtnScan.setEnabled(false);
        updateScanningButton(false);
        // Prune disconnected meters
        List<MooshimeterDevice> remove = new ArrayList<MooshimeterDevice>();
        for(MooshimeterDevice m : mMeterList) {
            if( m.mConnectionState == BluetoothProfile.STATE_DISCONNECTED ) {
                remove.add(m);
            }
        }
        for(MooshimeterDevice m : remove) {
            mDeviceScrollView.removeView(mTileList.remove(mMeterList.indexOf(m)));
            mMeterList.remove(m);
            mMeterDict.remove(m.getAddress());
        }
        refreshAllMeterTiles();

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if( !bluetoothAdapter.startLeScan(mLeScanCallback) ) {
            // Starting the scan failed!
            Log.e(TAG,"Failed to start BLE Scan");
            setError("Failed to start scan");
        }
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBtnScan.setEnabled(true);
                updateScanningButton(false);
                final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                bluetoothAdapter.stopLeScan(mLeScanCallback);
                mScanOngoing = false;
            }
        }, 5000);
    }

    public void onBtnScan(View view) {
        startScan();
    }

    private void startSingleMeterActivity(MooshimeterDevice m) {
        if(m.isInOADMode()) {
            startOADActivity(m);
        } else {
            startDeviceActivity(m);
        }
    }

    private void toggleConnectionState(final Button bv, final MooshimeterDevice m) {
        bv.setEnabled(false);
        bv.setAlpha((float)0.5);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                if(   m.mConnectionState == BluetoothProfile.STATE_CONNECTED
                   || m.mConnectionState == BluetoothProfile.STATE_CONNECTING ) {
                    m.disconnect();
                } else {
                    int rval;
                    setStatus("Connecting...");
                    rval = m.connect();
                    if(BluetoothGatt.GATT_SUCCESS != rval ) {
                        setStatus(String.format("Connection failed.  Status: %d", rval));
                        return; }
                    setStatus("Discovering Services...");
                    rval = m.discover();
                    if(BluetoothGatt.GATT_SUCCESS != rval ) {
                        setStatus(String.format("Discovery failed.  Status: %d", rval));
                        return; }
                    setStatus("Connected!");
                    startSingleMeterActivity(m);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bv.setEnabled(true);
                        bv.setAlpha((float)1.0);
                        int bgid = m.mConnectionState==BluetoothProfile.STATE_CONNECTED ? R.drawable.connected:R.drawable.disconnected;
                        bv.setBackground(getResources().getDrawable(bgid));
                    }
                });
            }
        });
        t.start();
    }

    private void valueLabelRefresh(final int c, final MooshimeterDevice mMeter, final TextView v,final TextView v_unit) {
        final boolean ac = mMeter.disp_ac[c];
        double val;
        int lsb_int;
        if(ac) { lsb_int = (int)(Math.sqrt(mMeter.meter_sample.reading_ms[c])); }
        else   { lsb_int = mMeter.meter_sample.reading_lsb[c]; }

        final String label_text;
        final String unit_text;

        if( mMeter.disp_hex[c]) {
            // If we've been requested to just show the raw hex
            lsb_int &= 0x00FFFFFF;
            label_text = String.format("0x%06X", lsb_int);
        } else if (MooshimeterDevice.METER_CALC_SETTINGS_RES == (mMeter.meter_settings.calc_settings & MooshimeterDevice.METER_CALC_SETTINGS_RES)
                &&  (mMeter.meter_info.build_time > 1445139447)  // And we have a firmware version late enough that the resistance is calculated in firmware
                && 0x09 == (mMeter.meter_settings.chset[c] & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK)) {
            //Resistance
            // FIXME: lsbToNativeUnits doesn't even look at lsb_int in this context...
            val = mMeter.lsbToNativeUnits(lsb_int, c);
            label_text = MooshimeterDevice.formatReading(val, mMeter.getSigDigits(c));
        } else {
            // If at the edge of your range, say overload
            // Remember the bounds are asymmetrical
            final int upper_limit_lsb = (int) (1.1*(1<<22));
            final int lower_limit_lsb = (int) (-0.9*(1<<22));

            if(   lsb_int > upper_limit_lsb
                    || lsb_int < lower_limit_lsb ) {
                label_text = "OVERLOAD";
            } else {
                // TODO: implement these methods and revive this segment of code
                val = mMeter.lsbToNativeUnits(lsb_int, c);
                label_text = MooshimeterDevice.formatReading(val, mMeter.getSigDigits(c));
            }
        }

        unit_text = mMeter.getUnits(c);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(v==null) {
                    return;
                }
                v.setText(label_text);
                v_unit.setText(unit_text);
            }
        });
    }
}
