package com.example.ti.ble.common;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;

import java.nio.ByteBuffer;

/**
 * Created by First on 1/7/2015.
 */
public class MooshimeterDevice {
    private BluetoothDevice bluetooth_device;
    private BluetoothGatt bluetooth_gatt;
    private int rssi;
    public int adv_build_time;

    private abstract class Serializable {
        abstract byte[] pack();
        abstract void unpack(byte[] in);
    }
    private void putInt24(ByteBuffer b, int arg) {
        // Puts the bottom 3 bytes of arg on to b
        ByteBuffer tmp = ByteBuffer.allocate(4);
        byte[] tb = new byte[3];
        tmp.putInt(arg);
        tmp.flip();
        tmp.get(tb);
        b.put( tb );
    }
    private int getInt24(ByteBuffer b) {
        // Pulls out a 3 byte int, expands it to 4 bytes
        // Advances the buffer by 3 bytes
        byte[] tb = new byte[4];
        b.get(tb, 0, 3);                   // Grab 3 bytes of the input
        if(tb[2] < 0) {tb[3] = (byte)0xFF;}// Sign extend
        else          {tb[3] = (byte)0x00;}
        ByteBuffer tmp = ByteBuffer.wrap(tb);
        return tmp.getInt();
    }
    public class MeterSettings extends Serializable {
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
    public class MeterInfo extends Serializable {
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
    public class MeterSample extends Serializable {
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

    public MooshimeterDevice() {
        // Initialize internal structures
        meter_settings      = new MeterSettings();
        meter_log_settings  = new MeterLogSettings();
        meter_info          = new MeterInfo();
        meter_sample        = new MeterSample();
    }

    public void setBluetoothDevice(BluetoothDevice nb) {
        bluetooth_device = nb;
        //bluetooth_gatt = bluetooth_device.connectGatt(,true,)
        //
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetooth_device;
    }

    private BluetoothGattCallback cb;
}
