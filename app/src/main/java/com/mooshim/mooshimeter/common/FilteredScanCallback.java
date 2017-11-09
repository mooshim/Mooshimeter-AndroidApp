package com.mooshim.mooshimeter.common;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.mooshim.mooshimeter.activities.MyActivity;
import com.mooshim.mooshimeter.devices.BLEDeviceBase;
import com.mooshim.mooshimeter.devices.PeripheralWrapper;

import java.util.Arrays;
import java.util.UUID;

/**
 * Created by First on 7/3/2016.
 */
public abstract class FilteredScanCallback implements BluetoothAdapter.LeScanCallback {
    public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {

    }
    public abstract void FilteredCallback(final BLEDeviceBase m);
}
