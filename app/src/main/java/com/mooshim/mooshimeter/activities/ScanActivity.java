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

package com.mooshim.mooshimeter.activities;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
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
import com.mooshim.mooshimeter.devices.BLEDeviceBase;
import com.mooshim.mooshimeter.devices.PeripheralWrapper;
import com.mooshim.mooshimeter.common.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ScanActivity extends MyActivity {
    // Defines
    private static String TAG = "ScanActivity";
    private static final int REQ_ENABLE_BT = 0;

    // GUI Widgets
    private TextView mEmptyMsg;
    private TextView mStatus;
    private Button mBtnScan = null;
    private LinearLayout mDeviceScrollView = null;

    // Helpers
    private static FilteredScanCallback mScanCb = null;
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

        // Register to receive all the bluetooth actions
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        filter.addAction(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Find if we have any connected meters, if so make sure they resume streaming
        for(BLEDeviceBase m : mMeterDict.values()) {
            if(m.isConnected()) {
                addDeviceToTileList(m);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        /*
        * Here I attempted to implement a feature addressing the "persistent connection" bug -
        * sometimes Android will maintain a connection to a BLE device after the app has closed
        * and since it  is already connected to Android it doesn't show up in any scan results.
        * Ideally, we'd inherit the connection and set up a MooshimeterDevice with it.
        * Less ideally, we'd disconnect it so we could scan it.
        * But I can only find an interface to get BluetoothDevice's from the OS, which have no way
        * to disconnect them.
        */
        // Check if there are any presently connected Mooshimeters
        // If there are, disconnect them.
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        List<BluetoothDevice> connectedDevices = manager.getConnectedDevices(BluetoothProfile.GATT);
        for( BluetoothDevice device : connectedDevices ) {
            int type = device.getType();
            if(type != BluetoothDevice.DEVICE_TYPE_LE) {
                continue;
            }
            Log.d(TAG,"FOUND AN ALREADY CONNECTED BLE DEVICE!");
            // Is this already in our list or is it an orphan?
            if(null == getDeviceWithAddress(device.getAddress())) {
                // This is an orphan device.  Just disconnect it.
                final BluetoothDevice inner_device = device;
                final Context inner_context = this;
                Util.dispatch(new Runnable() {
                    @Override
                    public void run() {
                        PeripheralWrapper p = new PeripheralWrapper(inner_device, inner_context);
                        // This song and dance is simply because you can't call "disconnect" on a BLEDevice
                        // you have to call it on a BluetoothGatt, so we're using PeripheralWrapper to handle
                        // that
                        p.connect();
                        p.disconnect();
                    }
                });
            }
        }
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
                transitionToActivity(null,GlobalPreferencesActivity.class);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
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

    private View findTileForMeter(final BLEDeviceBase m) {
        for(int i = 0; i < mDeviceScrollView.getChildCount(); i++) {
            View v = mDeviceScrollView.getChildAt(i);
            BLEDeviceBase test = (BLEDeviceBase) v.getTag();
            if(test==m) {
                return v;
            }
        }
        // Could not find the tile, return null
        return null;
    }

    private void addDeviceToTileList(final BLEDeviceBase d) {
        mEmptyMsg.setVisibility(View.GONE);

        if(findTileForMeter(d) != null) {
            // The meter as already been added
            Log.e(TAG, "Tried to add the same meter twice");
            return;
        }

        final LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(mDeviceScrollView.getLayoutParams());

        wrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                stopScan();
                Util.dispatch(new Runnable() {
                    @Override
                    public void run() {
                        if (d.isConnected()
                                || d.isConnecting()) {
                            startSingleMeterActivity(d);
                        } else {
                            toggleConnectionState(d);
                        }
                    }
                });
            }
        });

        wrapper.addView(mInflater.inflate(R.layout.element_mm_titlebar, wrapper, false));
        wrapper.setTag(d);

        mDeviceScrollView.addView(wrapper);
        refreshMeterTile(wrapper);

        if (mMeterDict.size() > 1)
            setStatus(mMeterDict.size() + " devices");
        else
            setStatus("1 device");
    }

    private void refreshMeterTile(final ViewGroup wrapper) {
        final BLEDeviceBase d = (BLEDeviceBase) wrapper.getTag();
        if(wrapper.getChildCount()==0) {
            Log.e(TAG,"Received empty wrapper");
            return;
        }
        // Update the title bar
        int rssi = d.getRSSI();
        String name;
        SpannableStringBuilder build = new SpannableStringBuilder();
        if(d.mOADMode) {
            name = "Bootloader";
        } else {
            name = d.getBLEDevice().getName();
            if (name == null) {
                name = "Unknown device";
            }
        }

        if(d.mBuildTime == 0) {
            build.append("Invalid firmware");
        } else {
            build.append("Build: ");
            int color = 0xFF000000;
            if(d.mBuildTime<1454355414) {
                color = 0xFFFF0000;
            } else if(d.mBuildTime<Util.getBundledFirmwareVersion()) {
                color = 0xFFFF8000;
            }
            SpannableString bt= new SpannableString(Integer.toString(d.mBuildTime));
            bt.setSpan(new ForegroundColorSpan(color), 0, bt.length(), 0);
            build.append(bt);
        }

        SpannableStringBuilder descr = new SpannableStringBuilder();
        descr.append(name+"\n");
        descr.append(build);
        descr.append("\nRssi: " + rssi + " dBm");
        ((TextView) wrapper.findViewById(R.id.descr)).setText(descr, TextView.BufferType.SPANNABLE);

        final Button bv = (Button)wrapper.findViewById(R.id.btnConnect);

        int bgid = d.isConnected() ? R.drawable.connected:R.drawable.disconnected;
        bv.setBackground(getResources().getDrawable(bgid));

        // Set the click listeners
        bv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Util.dispatch(new Runnable() {
                    @Override
                    public void run() {
                        toggleConnectionState(d);
                    }
                });
            }
        });
    }

    private void refreshAllMeterTiles() {
        for(int i = 0; i < mDeviceScrollView.getChildCount(); i++) {
            final ViewGroup vg = (ViewGroup) mDeviceScrollView.getChildAt(i);
            refreshMeterTile(vg);
        }
    }

    /////////////////////////////
    // Listeners for BLE Events
    /////////////////////////////

    private abstract class FilteredScanCallback implements BluetoothAdapter.LeScanCallback {
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
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
                            // Bytes are reversed in the scan record
                            byte[] uuid_reversed_bytes = Arrays.copyOfRange(scanRecord, i, i + field_length);
                            for(int j = 0; j < 8; j++) {
                                uuid_reversed_bytes[   j] ^= uuid_reversed_bytes[15-j];
                                uuid_reversed_bytes[15-j] ^= uuid_reversed_bytes[   j];
                                uuid_reversed_bytes[   j] ^= uuid_reversed_bytes[15-j];
                            }
                            UUID received_uuid = Util.uuidFromBytes(uuid_reversed_bytes);
                            if(received_uuid.equals(BLEDeviceBase.mServiceUUIDs.METER_SERVICE)) {
                                Log.d(null, "Mooshimeter found");
                                is_meter = true;
                            } else if(received_uuid.equals(BLEDeviceBase.mServiceUUIDs.OAD_SERVICE_UUID)) {
                                Log.d(null, "Mooshimeter found in OAD mode");
                                is_meter = true;
                                oad_mode = true;
                            } else {
                                Log.d(null, "Scanned device is not a meter");
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

            if(is_meter) {
                BLEDeviceBase m = mMeterDict.get(device.getAddress());
                if(m==null) {
                    PeripheralWrapper p = new PeripheralWrapper(device, getApplicationContext());
                    m = new BLEDeviceBase(p);
                    mMeterDict.put(m.getAddress(), m);
                    final BLEDeviceBase wrapped = m;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addDeviceToTileList(wrapped);
                        }
                    });
                }
                m.setRSSI(rssi);
                m.mOADMode = oad_mode;
                m.mBuildTime = build_time;
                FilteredCallback(m);
            }
        }

        abstract void FilteredCallback(final BLEDeviceBase m);
    }

    private class MainScanCallback extends FilteredScanCallback {
        void FilteredCallback(final BLEDeviceBase m) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshMeterTile((ViewGroup) findTileForMeter(m));
                }
            });
            if(   m.getPreference(BLEDeviceBase.mPreferenceKeys.AUTOCONNECT)) {
                // We've found a meter with the autoconnect feature enabled
                // Connect to it!
                setStatus("Autoconnecting...");
                stopScan();
                // Why the crazy nesting?
                // At this point there might be a few runnables on the main queue that need to run
                // before we can toggle the connection state.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Util.dispatch(new Runnable() {
                            @Override
                            public void run() {
                                toggleConnectionState(m);
                            }
                        });
                    }
                });
            }
        }
    }

    /////////////////////////////
    // Listeners for GUI Events
    /////////////////////////////

    public synchronized void startScan() {
        if(mScanCb != null){return;}

        mBtnScan.setEnabled(false);
        updateScanningButton(false);
        // Prune disconnected meters
        List<BLEDeviceBase> remove = new ArrayList<BLEDeviceBase>();
        for(BLEDeviceBase m : mMeterDict.values()) {
            if( m.isDisconnected() ) {
                remove.add(m);
            }
        }
        for(BLEDeviceBase m : remove) {
            for(int i = 0; i < mDeviceScrollView.getChildCount(); i++) {
                ViewGroup vg = (ViewGroup) mDeviceScrollView.getChildAt(i);
                if(vg.getTag() == m) {
                    mDeviceScrollView.removeView(vg);
                    break;
                }
            }
            mMeterDict.remove(m.getAddress());
        }
        refreshAllMeterTiles();

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mScanCb = new MainScanCallback();

        if( !bluetoothAdapter.startLeScan(mScanCb) ) {
            // Starting the scan failed!
            Log.e(TAG,"Failed to start BLE Scan");
            setError("Failed to start scan");
        }
        Util.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, 5000);
    }

    public void stopScan() {
        Log.d(TAG,"stopScan called");
        if(mScanCb==null) {
            Log.d(TAG,"Scan not running!  Not going to call stopLeScan.");
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBtnScan.setEnabled(true);
                updateScanningButton(false);
            }
        });
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.stopLeScan(mScanCb);
        mScanCb = null;
    }

    public void onBtnScan(View view) {
        startScan();
    }

    private void startSingleMeterActivity(BLEDeviceBase m) {
        if (m.isInOADMode()) {
            transitionToActivity(m, OADActivity.class);
        } else {
            // Check the firmware version against our bundled version
        /*if(     m.meter_info.build_time < Util.getBundledFirmwareVersion()
                && offerFirmwareUpgrade() ) {
            // Perform a firmware upgrade!
            if(reconnectInOADMode(m)) {
                // If we reconnect successfully, our original meter reference is no longer valid
                // but its address still is
                m = getDeviceWithAddress(m.getAddress());
                startOADActivity(m);
            } else {
                Util.blockOnAlertBox(this, "Reboot to OAD failed", "Sorry, this version of Android can't manually reconnect to the Mooshimeter in bootloader mode.  \n\nYou can do it manually by resetting the Mooshimeter and connecting while it is blinking slowly.");
                Log.e(TAG,"FAILED TO RECONNECT IN OAD");
                m.disconnect();
            }
        } else {
            startDeviceActivity(m);
        }*/
            if (!m.getPreference(BLEDeviceBase.mPreferenceKeys.SKIP_UPGRADE) && m.mBuildTime < Util.getBundledFirmwareVersion()) {
                String[] choices = {"See Instructions", "Continue without updating"};
                int choice = Util.offerChoiceDialog(this, "Firmware update available", "A newer firmware version is available for this Mooshimeter, upgrading is recommended.", choices);
                switch (choice) {
                    case 0:
                        // View the instructions
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://moosh.im/upgrading-mooshimeter-firmware/"));
                        startActivity(browserIntent);
                        m.disconnect();
                        break;
                    case 1:
                        // Continue without viewing
                        m.setPreference(BLEDeviceBase.mPreferenceKeys.SKIP_UPGRADE,true);
                        transitionToActivity(m, whichActivity(m.mBuildTime));
                        break;
                }
            } else {
                transitionToActivity(m, whichActivity(m.mBuildTime));
            }
        }
    }

    private static Class whichActivity(int build_time) {
        return DeviceActivity.class;
        /*if(build_time > 1454355414) {
            return DeviceActivity.class;
        } else {
            return LegacyDeviceActivity.class;
        }*/
    }

    private void toggleConnectionState(BLEDeviceBase m) {
        ViewGroup vg = (ViewGroup) findTileForMeter(m);
        if(vg==null) {
            Log.e(TAG, "trying to toggle connection state on a tile that hasn't been instantiated");
            new Exception().printStackTrace();
            return;
        }
        final Button bv = (Button) vg.findViewById(R.id.btnConnect);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bv.setEnabled(false);
                bv.setAlpha((float) 0.5);
            }
        });
        if(    m.isConnected()
            || m.isConnecting() ) {
            m.setPreference(BLEDeviceBase.mPreferenceKeys.AUTOCONNECT,false);
            m.disconnect();
        } else {
            do {
                int rval = BluetoothGatt.GATT_FAILURE;
                int attempts = 0;
                while(attempts++ < 3 && rval != BluetoothGatt.GATT_SUCCESS) {
                    setStatus("Connecting... Attempt "+attempts);
                    rval = m.connect();
                }
                if (BluetoothGatt.GATT_SUCCESS != rval) {
                    setStatus("Connection failed.  Status: "+rval);
                    break;
                }
                setStatus("Discovering Services...");
                rval = m.discover();
                if (BluetoothGatt.GATT_SUCCESS != rval) {
                    // We may have failed because
                    setStatus("Discovery failed.  Status: "+rval);
                    m.disconnect();
                    break;
                }
                // At this point we are connected and have discovered characteristics for the BLE
                // device.  We need to figure out exactly what kind it is and start the right
                // activity for it.
                m = m.chooseSubclass();
                // Replace the copy in the singleton dict
                mMeterDict.put(m.getAddress(),m);
                setStatus("Initializing...");
                rval = m.initialize();
                if(rval != 0) {
                    setStatus("Initialization failed.  Status: "+rval);
                    m.disconnect();
                    break;
                }
                setStatus("Connected!");
                startSingleMeterActivity(m);
            }while(false);
        }
        final BLEDeviceBase finalM = m;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bv.setEnabled(true);
                bv.setAlpha((float) 1.0);
                int bgid = finalM.isConnected() ? R.drawable.connected : R.drawable.disconnected;
                bv.setBackground(getResources().getDrawable(bgid));
            }
        });
    }

    ///////////////////////////////
    // Adapter state change receiver
    ///////////////////////////////

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d("TAG", "ADAPTER" + action);
            // TODO: This is here as a template for later.

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                                     BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };
}
