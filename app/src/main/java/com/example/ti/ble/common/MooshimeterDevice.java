package com.example.ti.ble.common;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.Toast;

import com.example.ti.ble.sensortag.SensorTagGatt;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Created by First on 1/7/2015.
 */
public class MooshimeterDevice {
    private BluetoothLeService bt_service;
    private BluetoothGattService bt_gatt_service;
    private int rssi;
    public int adv_build_time;

    private Block cb = null;

    private void putInt24(ByteBuffer b, int arg) {
        // Puts the bottom 3 bytes of arg on to b
        ByteBuffer tmp = ByteBuffer.allocate(4);
        byte[] tb = new byte[3];
        tmp.putInt(arg);
        tmp.flip();
        tmp.get(tb);
        b.put( tb );
    }
    private int  getInt24(ByteBuffer b) {
        // Pulls out a 3 byte int, expands it to 4 bytes
        // Advances the buffer by 3 bytes
        byte[] tb = new byte[4];
        b.get(tb, 0, 3);                   // Grab 3 bytes of the input
        if(tb[2] < 0) {tb[3] = (byte)0xFF;}// Sign extend
        else          {tb[3] = (byte)0x00;}
        ByteBuffer tmp = ByteBuffer.wrap(tb);
        return tmp.getInt();
    }

    private abstract class Serializable {
        abstract byte[] pack();
        abstract void unpack(byte[] in);
    }
    public class MeterSettings    extends Serializable {
        byte present_meter_state;
        byte target_meter_state;
        byte trigger_setting;
        short trigger_x_offset;
        int trigger_crossing;
        byte measure_settings;
        byte calc_settings;
        byte ch1set;
        byte ch2set;
        byte adc_settings;

        @Override
        byte[] pack() {
            byte[] retval = new byte[13];
            ByteBuffer b = ByteBuffer.wrap(retval);

            b.put(      present_meter_state);
            b.put(      target_meter_state);
            b.put(      trigger_setting);
            b.putShort( trigger_x_offset);
            putInt24(b, trigger_crossing);
            b.put(      measure_settings);
            b.put(      calc_settings);
            b.put(      ch1set);
            b.put(      ch2set);
            b.put(      adc_settings);

            return retval;
        }

        @Override
        void unpack(byte[] in) {
            ByteBuffer b = ByteBuffer.wrap(in);

            present_meter_state = b.get();
            target_meter_state  = b.get();
            trigger_setting     = b.get();
            trigger_x_offset    = b.getShort();
            trigger_crossing    = getInt24(b);
            measure_settings    = b.get();
            calc_settings       = b.get();
            ch1set              = b.get();
            ch2set              = b.get();
            adc_settings        = b.get();
        }
    }
    public class MeterLogSettings extends Serializable {
        byte  sd_present;
        byte  present_logging_state;
        byte  logging_error;
        short file_number;
        int   file_offset;
        byte  target_logging_state;
        short logging_period_ms;
        int   logging_n_cycles;

        @Override
        byte[] pack() {
            byte[] retval = new byte[13];
            ByteBuffer b = ByteBuffer.wrap(retval);

            b.put(      sd_present );
            b.put(      present_logging_state );
            b.put(      logging_error );
            b.putShort( file_number );
            b.putInt(   file_offset );
            b.put(      target_logging_state );
            b.putShort( logging_period_ms );
            b.putInt(   logging_n_cycles );

            return retval;
        }

        @Override
        void unpack(byte[] in) {
            ByteBuffer b = ByteBuffer.wrap(in);

            sd_present              = b.get();
            present_logging_state   = b.get();
            logging_error           = b.get();
            file_number             = b.getShort();
            file_offset             = b.getInt();
            target_logging_state    = b.get();
            logging_period_ms       = b.getShort();
            logging_n_cycles        = b.get();
        }
    }
    public class MeterInfo        extends Serializable {
        byte  pcb_version;
        byte  assembly_variant;
        short lot_number;
        int   build_time;

        @Override
        byte[] pack() {
            byte[] retval = new byte[8];
            ByteBuffer b = ByteBuffer.wrap(retval);

            b.put     (pcb_version);
            b.put     (assembly_variant);
            b.putShort(lot_number);
            b.putInt  (build_time);

            return retval;
        }

        @Override
        void unpack(byte[] in) {
            ByteBuffer b = ByteBuffer.wrap(in);

            pcb_version      = b.get();
            assembly_variant = b.get();
            lot_number       = b.getShort();
            build_time       = b.getInt();
        }
    }
    public class MeterSample      extends Serializable {
        int   ch1_reading_lsb;
        int   ch2_reading_lsb;
        float ch1_ms;
        float ch2_ms;

        @Override
        byte[] pack() {
            byte[] retval = new byte[16];
            ByteBuffer b = ByteBuffer.wrap(retval);

            putInt24(b, ch1_reading_lsb);
            putInt24(b, ch2_reading_lsb);
            b.putFloat( ch1_ms);
            b.putFloat( ch2_ms);

            return retval;
        }

        @Override
        void unpack(byte[] in) {
            ByteBuffer b = ByteBuffer.wrap(in);

            ch1_reading_lsb = getInt24(b);
            ch2_reading_lsb = getInt24(b);
            ch1_ms          = b.getFloat();
            ch2_ms          = b.getFloat();
        }
    }

    public MeterSettings    meter_settings;
    public MeterLogSettings meter_log_settings;
    public MeterInfo        meter_info;
    public MeterSample      meter_sample;

    public MooshimeterDevice(Context context) {
        // Initialize internal structures
        meter_settings      = new MeterSettings();
        meter_log_settings  = new MeterLogSettings();
        meter_info          = new MeterInfo();
        meter_sample        = new MeterSample();

        bt_service = BluetoothLeService.getInstance();
        if(bt_service != null) {
            // Get the GATT service
            bt_gatt_service = null;
            for( BluetoothGattService s : bt_service.getSupportedGattServices() ) {
                if(s.getUuid().equals(SensorTagGatt.METER_SERVICE)) {
                    Log.i(null, "Found the meter service");
                    bt_gatt_service = s;
                    break;
                }
            }
            if(bt_gatt_service == null) {
                Log.i(null, "Did not find the meter service!");
            }
        }
        // FIXME:  I am unhappy with the way this class and DeviceActivity are structured
        // There are a lot of interdependencies that make them complicated to work with.
        // But I don't want to change too many things at once.
        final IntentFilter fi = new IntentFilter();
        fi.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        fi.addAction(BluetoothLeService.ACTION_DATA_NOTIFY);
        fi.addAction(BluetoothLeService.ACTION_DATA_WRITE);
        fi.addAction(BluetoothLeService.ACTION_DATA_READ);
        context.registerReceiver(mGattUpdateReceiver, fi);
    }

    ////////////////////////////////
    // Accessors
    ////////////////////////////////

    private void callCB() {
        if(cb!=null) {
            Block cbc = cb;
            cb = null;
            cbc.run();
        }
    }

    private void req(UUID uuid, Block new_cb) {
        BluetoothGattCharacteristic c = bt_gatt_service.getCharacteristic(uuid);
        cb = new_cb;
        bt_service.readCharacteristic(c);
    }
    private void send(UUID uuid, byte[] value, Block new_cb) {
        BluetoothGattCharacteristic c = bt_gatt_service.getCharacteristic(uuid);
        c.setValue(value);
        cb = new_cb;
        bt_service.writeCharacteristic(c);
    }

    public void reqMeterSettings   ( Block new_cb ) { req(SensorTagGatt.METER_SETTINGS, new_cb); }
    public void reqMeterLogSettings( Block new_cb ) { req(SensorTagGatt.METER_LOG_SETTINGS, new_cb); }
    public void reqMeterInfo       ( Block new_cb ) { req(SensorTagGatt.METER_INFO, new_cb); }
    public void reqMeterSample     ( Block new_cb ) { req(SensorTagGatt.METER_SAMPLE, new_cb); }

    public void sendMeterSettings   ( Block new_cb ) { send(SensorTagGatt.METER_SETTINGS, meter_settings.pack(), new_cb); }
    public void sendMeterLogSettings( Block new_cb ) { send(SensorTagGatt.METER_LOG_SETTINGS, meter_log_settings.pack(), new_cb); }
    public void sendMeterInfo       ( Block new_cb ) { send(SensorTagGatt.METER_INFO, meter_info.pack(), new_cb); }
    public void sendMeterSample     ( Block new_cb ) { send(SensorTagGatt.METER_SAMPLE, meter_sample.pack(), new_cb); }

    ////////////////////////////////
    // GATT Callbacks
    ////////////////////////////////

    private void handleValueUpdate(UUID uuid, byte[] value) {
        if(uuid.equals(SensorTagGatt.METER_SETTINGS)) {
            meter_settings.unpack(value);
        } else if(uuid.equals(SensorTagGatt.METER_LOG_SETTINGS)) {
            meter_log_settings.unpack(value);
        } else if(uuid.equals(SensorTagGatt.METER_INFO)) {
            meter_info.unpack(value);
        } else if(uuid.equals(SensorTagGatt.METER_SAMPLE)) {
            meter_sample.unpack(value);
        }
        callCB();
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            int status = intent.getIntExtra(BluetoothLeService.EXTRA_STATUS,
                    BluetoothGatt.GATT_SUCCESS);

            if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(null,"Service discovery complete");
                } else {
                    Log.d(null,"Service discovery failed");
                    return;
                }
            } else if (BluetoothLeService.ACTION_DATA_NOTIFY.equals(action)
                    || BluetoothLeService.ACTION_DATA_READ.equals(action) ) {
                Log.d(null, "onCharacteristicReadNotify");
                String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                UUID uuid = UUID.fromString(uuidStr);
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                handleValueUpdate(uuid,value);
            } else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action)) {
                Log.d(null, "onCharacteristicWrite");
                callCB();
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(null, "GATT error code: " + status);
            }
        }
    };
}
