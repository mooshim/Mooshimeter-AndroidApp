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
import android.os.Bundle;
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

import com.idevicesinc.sweetblue.BleNodeConfig;
import com.idevicesinc.sweetblue.BleStatuses;
import com.idevicesinc.sweetblue.utils.Interval;
import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.FirmwareFile;
import com.mooshim.mooshimeter.devices.BLEDeviceBase;
import com.mooshim.mooshimeter.devices.PeripheralWrapper;
import com.mooshim.mooshimeter.common.Util;

import com.idevicesinc.sweetblue.BleDevice;
import com.idevicesinc.sweetblue.BleManager;
import com.idevicesinc.sweetblue.BleManagerConfig;
import com.idevicesinc.sweetblue.utils.BluetoothEnabler;

import java.util.UUID;

public class ScanActivity extends MyActivity {
    // Defines
    private static String TAG = "ScanActivity";
    private static final int REQ_ENABLE_BT = 0;

    // GUI Widgets
    private TextView mStatus;
    private Button mBtnScan = null;
    private LinearLayout mDeviceScrollView = null;

    // Helpers
    private LayoutInflater mInflater;

    private BleManager mBleManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        BluetoothEnabler.start(this);

        mBleManager = BleManager.get(this);
        BleManagerConfig tmp = mBleManager.getConfigClone();
        tmp.loggingEnabled = true;
        tmp.rssiAutoPollRate = Interval.secs(2);
        tmp.delayBetweenTasks = Interval.millis(20);
        tmp.scanReportDelay = Interval.DISABLED;
        tmp.reconnectFilter = new BleNodeConfig.ReconnectFilter() {
            @Override
            public Please onEvent(ReconnectEvent e) {
                return Please.stopRetrying();
            }
        };
        mBleManager.setConfig(tmp);
        mBleManager.setListener_UhOh(new BleManager.UhOhListener() {
            @Override
            public void onEvent(UhOhEvent e) {
                Log.e(TAG,"UhOh Caught "+e.toString());
            }
        });
/*
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
*/
        setContentView(R.layout.fragment_scan);
        // Initialize widgets
        mStatus           = (TextView)     findViewById(R.id.status);
        mBtnScan          = (Button)       findViewById(R.id.btn_scan);
        mDeviceScrollView = (LinearLayout) findViewById(R.id.device_list);

        mDeviceScrollView.setClickable(true);

        mInflater = LayoutInflater.from(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Find if we have any connected meters, if so make sure they resume streaming
        for(BLEDeviceBase m : getDevices()) {
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
        /*
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
        */
        mBleManager.onResume();
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
                pushActivityToStack(null,GlobalPreferencesActivity.class);
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
        } else {
            // Indicate that scanning has stopped
            mStatus.setTextAppearance(this, R.style.statusStyle_Success);
            mBtnScan.setText("Scan");
            mBtnScan.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_action_refresh, 0);
            refreshAllMeterTiles();
        }
        int n_devices = getNDevices();
        switch(n_devices) {
            case 0:
                mStatus.setText("No devices found");
                break;
            case 1:
                mStatus.setText("Found 1 Mooshimeter");
                break;
            default:
                mStatus.setText("Found "+n_devices+" Mooshimeters");
                break;
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
            if(test.getAddress().equals(m.getAddress())) {
                return v;
            }
        }
        // Could not find the tile, return null
        return null;
    }

    private void addDeviceToTileList(final BLEDeviceBase d) {
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
            name = d.getName();
            if (name == null) {
                name = "Unknown device";
            }
        }

        if(d.mBuildTime == 0) {
            build.append("Invalid firmware");
        } else {
            FirmwareFile f = Util.getLatestFirmware();
            build.append("Build: ");
            int color = 0xFF000000;
            /*if(d.mBuildTime<1454355414) {
                color = 0xFFFF0000;
            } else if(d.mBuildTime<f.getVersion()) {
                color = 0xFFFF8000;
            }*/
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
        for(BLEDeviceBase m : getDevices()) {
            if(findTileForMeter(m)==null) {
                addDeviceToTileList(m);
            }
        }
        for(int i = 0; i < mDeviceScrollView.getChildCount(); i++) {
            final ViewGroup vg = (ViewGroup) mDeviceScrollView.getChildAt(i);
            refreshMeterTile(vg);
        }
    }

    /////////////////////////////
    // Listeners for GUI Events
    /////////////////////////////

    private boolean isScanning() {
        return mBleManager.isScanning();
    }

    public synchronized void startScan() {
        if(isScanning()){return;}
        // Prune disconnected meters
        for(BLEDeviceBase m : getDevices()) {
            if( !m.isConnected() ) {
                for(int i = 0; i < mDeviceScrollView.getChildCount(); i++) {
                    ViewGroup vg = (ViewGroup) mDeviceScrollView.getChildAt(i);
                    if (vg.getTag() == m) {
                        mDeviceScrollView.removeView(vg);
                        break;
                    }
                }
                removeDevice(m);
            }
        }
        refreshAllMeterTiles();

        if( mBleManager.startScan(new BleManager.DiscoveryListener()
        {
            @Override public void onEvent(DiscoveryEvent event)
            {
                if( event.was(LifeCycle.DISCOVERED)
                 || event.was(LifeCycle.REDISCOVERED))
                {
                    ////////////////////////////////////////////////////////////
                    ////////////////////////////////////////////////////////////
                    ////////////////////////////////////////////////////////////
                    ////////////////////////////////////////////////////////////

                    final BleDevice d = event.device();
                    final UUID[] uuids = d.getAdvertisedServices();
                    final byte[] manu_data = d.getManufacturerData();

                    // Filter devices
                    boolean oad_mode = false;
                    int build_time = 0;

                    // Always expecting a single service UUID
                    if(uuids.length != 1) {
                        return;
                    }
                    // Always expecting a 4 bytes of manufacturer data
                    if(manu_data.length != 4) {
                        return;
                    }
                    // In the case of the Mooshimeter this is the build time
                    // in UTC seconds
                    for(int j = 3; j >= 0; j--) {
                        build_time |= (manu_data[j] & 0xFF) << (8*j);
                    }

                    BLEDeviceBase m = MyActivity.getDeviceWithAddress(d.getMacAddress());
                    if(m==null) {
                        PeripheralWrapper p = new PeripheralWrapper(event.device(), Util.getRootContext());
                        m = new BLEDeviceBase(p);
                        MyActivity.putDevice(m);
                    }
                    m.mOADMode = oad_mode;
                    m.mBuildTime = build_time;
                    if(findTileForMeter(m)==null) {
                        final BLEDeviceBase wrapped = m;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                addDeviceToTileList(wrapped);
                            }
                        });
                    }
                    final BLEDeviceBase inner_meter = m;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshMeterTile((ViewGroup) findTileForMeter(inner_meter));
                        }
                    });
                    /*boolean should_automatically_connect_for_firmware_upload =
                        m.isInOADMode() && (m.mBuildTime < Util.getBundledFirmwareVersion());*/
                        if(   m.getPreference(BLEDeviceBase.mPreferenceKeys.AUTOCONNECT)
                   /*|| should_automatically_connect_for_firmware_upload*/ ) {
                            // We've found a meter with the autoconnect feature enabled, or it's in OAD mode
                            // which almost certainly means the user wants to do a firmware update
                            // Connect to it!
                            setStatus("Autoconnecting...");
                    /*if(should_automatically_connect_for_firmware_upload) {
                        Toast.makeText(mInstance, "Found an out of date meter in bootloader mode, connecting", Toast.LENGTH_LONG).show();
                    }*/
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
                                            toggleConnectionState(inner_meter);
                                        }
                                    });
                                }
                            });
                        }
                }
            }
        }) ) {
            updateScanningButton(true);
            Util.postDelayedToMain(new Runnable() {
                @Override
                public void run() {
                    stopScan();
                }
            }, 5000);
        } else {
            // Starting the scan failed!
            Log.e(TAG,"Failed to start BLE Scan");
            setError("Failed to start scan");
            stopScan();
        }
    }

    public void stopScan() {
        Log.d(TAG,"stopScan called");
        if(!isScanning()){return;}
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateScanningButton(false);
                if(getNDevices()==0) {
                    Toast.makeText(Util.getRootContext(), "Is your meter in shipping mode?  Connect the Ω and C terminals to wake it.", Toast.LENGTH_LONG).show();
                }
            }
        });
        mBleManager.stopScan();
    }

    public void onBtnScan(View view) {
        if(isScanning()){
            stopScan();
        } else {
            startScan();
        }
    }

    private void startSingleMeterActivity(BLEDeviceBase m) {
        if (m.isInOADMode()) {
            pushActivityToStack(m, OADActivity.class);
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
            if (!m.getPreference(BLEDeviceBase.mPreferenceKeys.SKIP_UPGRADE) && m.mBuildTime < Util.getLatestFirmware().getVersion()) {
                String[] choices = {"Update now", "Continue without updating"};
                int choice = Util.offerChoiceDialog(this, "Firmware update available", "A newer firmware version is available for this Mooshimeter, upgrading is recommended.", choices);
                switch (choice) {
                    case 0:
                        // Update now
                        pushActivityToStack(m, OADActivity.class);
                        break;
                    case 1:
                        // Continue without viewing
                        m.setPreference(BLEDeviceBase.mPreferenceKeys.SKIP_UPGRADE,true);
                        pushActivityToStack(m, DeviceActivity.class);
                        break;
                }
            } else {
                pushActivityToStack(m, DeviceActivity.class);
            }
        }
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
                int rval = -1;
                int attempts = 0;
                while(attempts++ < 3 && rval != BleStatuses.CONN_SUCCESS) {
                    setStatus("Connecting... Attempt "+attempts);
                    rval = m.connect();
                }
                if (BleStatuses.CONN_SUCCESS != rval) {
                    setStatus("Connection failed.  Status: "+rval);
                    break;
                }
                // At this point we are connected and have discovered characteristics for the BLE
                // device.  We need to figure out exactly what kind it is and start the right
                // activity for it.
                BLEDeviceBase tmp_m = m.chooseSubclass();
                if(tmp_m==null) {
                    //Couldn't choose a subclass for some reason...
                    setStatus("I don't recognize this device... aborting");
                    m.disconnect();
                    break;
                } else {
                    m = tmp_m;
                }
                // Replace the copy in the singleton dict
                putDevice(m);
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
}
