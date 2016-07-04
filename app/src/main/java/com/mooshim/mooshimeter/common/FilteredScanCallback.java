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
            BLEDeviceBase m = MyActivity.getDeviceWithAddress(device.getAddress());
            if(m==null) {
                PeripheralWrapper p = new PeripheralWrapper(device, Util.getRootContext());
                m = new BLEDeviceBase(p);
                MyActivity.putDevice(m);
            }
            m.setRSSI(rssi);
            m.mOADMode = oad_mode;
            m.mBuildTime = build_time;
            FilteredCallback(m);
        }
    }
    public abstract void FilteredCallback(final BLEDeviceBase m);
}
