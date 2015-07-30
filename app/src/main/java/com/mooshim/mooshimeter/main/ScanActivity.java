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
import android.bluetooth.BluetoothProfile;
import android.content.Context;
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
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.MooshimeterDevice;

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

    // Housekeeping
    private static final Lock utilLock = new ReentrantLock();
    private static final Condition scanThreadCondition = utilLock.newCondition();

    // GUI Widgets
    private DeviceListAdapter mDeviceAdapter = null;
    private TextView mEmptyMsg;
    private TextView mStatus;
    private Button mBtnScan = null;
    private ListView mDeviceListView = null;

    // Flags for the scan logic thread
    private static boolean mScanOngoing = false;
    private static MooshimeterDevice mConnectionRequester = null;

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

        mDeviceAdapter = new DeviceListAdapter(this);
        mDeviceListView.setAdapter(mDeviceAdapter);
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            final MooshimeterDevice m = mMeterList.get(pos);
            if(    m.mConnectionState == BluetoothProfile.STATE_CONNECTED
                || m.mConnectionState == BluetoothProfile.STATE_CONNECTING ) {
                startDeviceActivity(m);
            } else {
                final Button bv = (Button)view.findViewById(R.id.btnConnect);
                toggleConnectionState(bv,m);
            }
        }
    };

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
            mDeviceAdapter.notifyDataSetChanged();
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

    private void addDevice(MooshimeterDevice d) {
        mEmptyMsg.setVisibility(View.GONE);

        mMeterList.add(d);
        mMeterDict.put(d.getAddress(), d);

        mDeviceAdapter.notifyDataSetChanged();
        if (mMeterList.size() > 1)
            setStatus(mMeterList.size() + " devices");
        else
            setStatus("1 device");
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

    private void startOADActivity() {
        //final Intent i = new Intent(this, FwUpdateActivity.class);
        //startActivityForResult(i, REQ_OAD_ACT);
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
                        mDeviceAdapter.notifyDataSetChanged();
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
            mMeterList.remove(m);
            mMeterDict.remove(m.getAddress());
        }
        mDeviceAdapter.notifyDataSetChanged();
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
                    setStatus("Connecting...");
                    m.connect();
                    setStatus("Discovering Services...");
                    m.discover();
                    setStatus("Connected!");
                    startDeviceActivity(m);
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

    private void valueLabelRefresh(final int c, final MooshimeterDevice mMeter, final TextView v) {
        final boolean ac = mMeter.disp_ac[c];
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
                // TODO: implement these methods and revive this segment of code
                val = mMeter.lsbToNativeUnits(lsb_int, c);
                label_text = mMeter.formatReading(val, mMeter.getSigDigits(c));
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                v.setText(label_text);
            }
        });
    }

    class DeviceListAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        private Map<Integer,ViewGroup> mViews;

        public DeviceListAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
            mViews = new HashMap<Integer, ViewGroup>();
        }

        public int getCount() {
            return mMeterDict.size();
        }

        public Object getItem(int position) {
            return mMeterList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2; // Count of different layouts
        }

        @Override
        public int getItemViewType(int position) {
            final MooshimeterDevice m = mMeterList.get(position);
            if(m.mInitialized) {
                return 1;
            } else {
                return 0;
            }
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            final ViewGroup vg;

            final MooshimeterDevice m = mMeterList.get(position);

            //final int desired_view_id = getItemViewType(position)==1?R.layout.element_mm_full:R.layout.element_mm_titlebar;

            if(m.mInitialized) {
                vg = (ViewGroup) mInflater.inflate(R.layout.element_mm_full, parent, false);
                vg.setTag(Integer.valueOf(R.layout.element_mm_full));
                View tmp = mViews.get(position);
                if(tmp != null) {
                    // Copy the text over from the last incarnation
                    ((TextView)vg.findViewById(R.id.ch1_value_label)).setText(((TextView)mViews.get(position).findViewById(R.id.ch1_value_label)).getText());
                    ((TextView)vg.findViewById(R.id.ch2_value_label)).setText(((TextView)mViews.get(position).findViewById(R.id.ch2_value_label)).getText());
                }
                mViews.put(position,vg);
                if(!m.isNotificationEnabled(m.getChar(MooshimeterDevice.mUUID.METER_SAMPLE))) {
                    m.playSampleStream(new Runnable() {
                        @Override
                        public void run() {
                            TextView ch1 = (TextView)mViews.get(position).findViewById(R.id.ch1_value_label);
                            TextView ch2 = (TextView)mViews.get(position).findViewById(R.id.ch2_value_label);
                            valueLabelRefresh(0,m, ch1);
                            valueLabelRefresh(1,m, ch2);
                        }
                    });
                }
            } else {
                vg = (ViewGroup) mInflater.inflate(R.layout.element_mm_titlebar, parent, false);
                vg.setTag(Integer.valueOf(R.layout.element_mm_titlebar));
            }

            int rssi = m.mRssi;
            String name;
            String build;
            if(m.mOADMode) {
                name = "Bootloader";
            } else {
                name = m.getBLEDevice().getName();
                if (name == null) {
                    name = new String("Unknown device");
                }
            }

            if(m.mBuildTime == 0) {
                build = "Invalid firmware";
            } else {
                build = "Build: "+m.mBuildTime;
            }

            String descr = name + "\n" + build + "\nRssi: " + rssi + " dBm";
            ((TextView) vg.findViewById(R.id.descr)).setText(descr);

            final Button bv = (Button)vg.findViewById(R.id.btnConnect);

            int bgid = m.mConnectionState==BluetoothProfile.STATE_CONNECTED ? R.drawable.connected:R.drawable.disconnected;
            bv.setBackground(getResources().getDrawable(bgid));

            // Set the click listeners
            bv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    toggleConnectionState(bv,m);
                }
            });
            return vg;
        }
    }
}
