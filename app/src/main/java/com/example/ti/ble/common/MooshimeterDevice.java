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
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Created by First on 1/7/2015.
 */

public class MooshimeterDevice {
    public final byte METER_SHUTDOWN  = 0;
    public final byte METER_STANDBY   = 1;
    public final byte METER_PAUSED    = 2;
    public final byte METER_RUNNING   = 3;
    public final byte METER_HIBERNATE = 4;

    private BluetoothLeService bt_service;
    private BluetoothGattService bt_gatt_service;
    private int rssi;
    public int adv_build_time;

    public enum CH3_MODES {
        VOLTAGE,
        RESISTANCE,
        DIODE
    };

    // Display control settings
    public final boolean[] disp_ac  = new boolean[]{false,false};
    public final boolean[] disp_hex = new boolean[]{false,false};
    public CH3_MODES disp_ch3_mode;

    public final int[] offsets = new int[]{0,0};

    private Block cb = null;
    private Block stream_cb = null;

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
        tmp.order(ByteOrder.LITTLE_ENDIAN);
        return tmp.getInt();
    }

    private abstract class Serializable {
        abstract byte[] pack();
        abstract void unpack(byte[] in);
    }
    public class MeterSettings    extends Serializable {
        public byte present_meter_state;
        public byte target_meter_state;
        public byte trigger_setting;
        public short trigger_x_offset;
        public int trigger_crossing;
        public byte measure_settings;
        public byte calc_settings;
        public byte ch1set;
        public byte ch2set;
        public byte adc_settings;

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
            b.order(ByteOrder.LITTLE_ENDIAN);
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
        public byte  sd_present;
        public byte  present_logging_state;
        public byte  logging_error;
        public short file_number;
        public int   file_offset;
        public byte  target_logging_state;
        public short logging_period_ms;
        public int   logging_n_cycles;

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
            b.order(ByteOrder.LITTLE_ENDIAN);
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
            b.order(ByteOrder.LITTLE_ENDIAN);
            pcb_version      = b.get();
            assembly_variant = b.get();
            lot_number       = b.getShort();
            build_time       = b.getInt();
        }
    }
    public class MeterSample      extends Serializable {
        public final int   reading_lsb[] = new int[2];
        public final float reading_ms[]  = new float[2];

        @Override
        byte[] pack() {
            byte[] retval = new byte[16];
            ByteBuffer b = ByteBuffer.wrap(retval);

            putInt24(b, reading_lsb[0]);
            putInt24(b, reading_lsb[1]);
            b.putFloat( reading_ms[0]);
            b.putFloat( reading_ms[1]);

            return retval;
        }

        @Override
        void unpack(byte[] in) {
            ByteBuffer b = ByteBuffer.wrap(in);
            b.order(ByteOrder.LITTLE_ENDIAN);

            reading_lsb[0] = getInt24(b);
            reading_lsb[1] = getInt24(b);
            reading_ms[0]          = b.getFloat();
            reading_ms[1]          = b.getFloat();
        }
    }

    public MeterSettings    meter_settings;
    public MeterLogSettings meter_log_settings;
    public MeterInfo        meter_info;
    public MeterSample      meter_sample;

    public MooshimeterDevice(Context context, final Block on_init) {
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
        fi.addAction(BluetoothLeService.ACTION_DESCRIPTOR_WRITE);
        fi.addAction(BluetoothLeService.ACTION_DATA_READ);
        context.registerReceiver(mGattUpdateReceiver, fi);

        // Grab the initial settings
        reqMeterSettings( new Block() {
            @Override
            public void run() {
                reqMeterLogSettings( new Block() {
                    @Override
                    public void run() {
                        reqMeterInfo( new Block() {
                            @Override
                            public void run() {
                                reqMeterSample( on_init );
                                Log.i(null,"Meter initialization complete");
                            }
                        });
                    }
                });
            }
        });
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

    public void enableMeterStreamSample( boolean enable, Block new_cb, Block new_stream_cb ) {
        stream_cb = new_stream_cb;
        BluetoothGattCharacteristic c = bt_gatt_service.getCharacteristic(SensorTagGatt.METER_SAMPLE);
        cb = new_cb;
        bt_service.setCharacteristicNotification(c,enable);
    }

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

            String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
            UUID uuid = UUID.fromString(uuidStr);
            byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

            if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(null, "onServiceDiscovery");
                callCB();
            } else if ( BluetoothLeService.ACTION_DATA_READ.equals(action) ) {
                Log.d(null, "onCharacteristicRead");
                handleValueUpdate(uuid,value);
            } else if ( BluetoothLeService.ACTION_DATA_NOTIFY.equals(action) ) {
                Log.d(null, "onCharacteristicNotify");
                handleValueUpdate(uuid,value);
                stream_cb.run();
            } else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action)) {
                Log.d(null, "onCharacteristicWrite");
                callCB();
            } else if (BluetoothLeService.ACTION_DESCRIPTOR_WRITE.equals(action)) {
                Log.d(null, "onDescriptorWrite");
                callCB();
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(null, "GATT error code: " + status);
            }
        }
    };

    //////////////////////////////////////
    // Data conversion
    //////////////////////////////////////

    private final int METER_MEASURE_SETTINGS_ISRC_ON         = 0x01;
    private final int METER_MEASURE_SETTINGS_ISRC_LVL        = 0x02;
    private final int METER_MEASURE_SETTINGS_ACTIVE_PULLDOWN = 0x04;

    private final int METER_CALC_SETTINGS_DEPTH_LOG2 = 0x0F;
    private final int METER_CALC_SETTINGS_MEAN       = 0x10;
    private final int METER_CALC_SETTINGS_ONESHOT    = 0x20;
    private final int METER_CALC_SETTINGS_MS         = 0x40;

    private final int ADC_SETTINGS_SAMPLERATE_MASK = 0x07;
    private final int ADC_SETTINGS_GPIO_MASK = 0x30;

    private final int METER_CH_SETTINGS_PGA_MASK = 0x70;
    private final int METER_CH_SETTINGS_INPUT_MASK = 0x0F;

    public class SignificantDigits {
        public int high;
        public int n_digits;
    }

    private double getEnob(final int channel) {
        // Return a rough appoximation of the ENOB of the channel
        // For the purposes of figuring out how many digits to display
        // Based on ADS1292 datasheet and some special sauce.
        // And empirical measurement of CH1 (which is super noisy due to chopper)
        final double base_enob_table[] = {
                20.10,
                19.58,
                19.11,
                18.49,
                17.36,
                14.91,
                12.53};
        final int pga_gain_table[] = {6,1,2,3,4,8,12};
        final int samplerate_setting =meter_settings.adc_settings & ADC_SETTINGS_SAMPLERATE_MASK;
        final int buffer_depth_log2 = meter_settings.calc_settings & METER_CALC_SETTINGS_DEPTH_LOG2;
        double enob = base_enob_table[ samplerate_setting ];
        int pga_setting = channel==1? meter_settings.ch1set:meter_settings.ch2set;
        pga_setting &= METER_CH_SETTINGS_PGA_MASK;
        pga_setting >>= 4;
        int pga_gain = pga_gain_table[pga_setting];
        // At lower sample frequencies, pga gain affects noise
        // At higher frequencies it has no effect
        double pga_degradation = (1.5/12) * pga_gain * ((6-samplerate_setting)/6.0);
        enob -= pga_degradation;
        // Oversampling adds 1 ENOB per factor of 4
        enob += ((double)buffer_depth_log2)/2.0;
        //
        if(channel == 1 && (meter_settings.ch1set & METER_CH_SETTINGS_INPUT_MASK) == 0 ) {
            // This is compensation for a bug in RevH, where current sense chopper noise dominates
            enob -= 2;
        }
        return enob;
    }

    public SignificantDigits getSigDigits(final int channel) {
        SignificantDigits retval = new SignificantDigits();
        final double enob = getEnob(channel);
        final double max = lsbToNativeUnits((1<<22),channel);
        final double max_dig  = Math.log10(max);
        final double n_digits = Math.log10(Math.pow(2.0, enob));
        retval.high = (int)(max_dig+1);
        retval.n_digits = (int) n_digits;
        return retval;
    }

    public double lsbToADCInVoltage(final int reading_lsb, final int channel) {
        // This returns the input voltage to the ADC,
        final double Vref = 2.5;
        final double pga_lookup[] = {6,1,2,3,4,8,12};
        int pga_setting=0;
        switch(channel) {
            case 0:
                pga_setting = meter_settings.ch1set >> 4;
                break;
            case 1:
                pga_setting = meter_settings.ch2set >> 4;
                break;
            default:
                Log.i(null,"Should not be here");
                break;
        }
        double pga_gain = pga_lookup[pga_setting];
        return ((double)reading_lsb/(double)(1<<23))*Vref/pga_gain;
    }

    public double adcVoltageToHV(final double adc_voltage) {
        switch( (meter_settings.adc_settings & ADC_SETTINGS_GPIO_MASK) >> 4 ) {
            case 0x00:
                // 1.2V range
                return adc_voltage;
            case 0x01:
                // 60V range
                return ((10e6+160e3)/(160e3)) * adc_voltage;
            case 0x02:
                // 1000V range
                return ((10e6+11e3)/(11e3)) * adc_voltage;
            default:
                Log.w(null,"Invalid setting!");
                return 0.0;
        }
    }

    public double adcVoltageToCurrent(final double adc_voltage) {
        final double rs = 1e-3;
        final double amp_gain = 80.0;
        return adc_voltage/(amp_gain*rs);
    }

    public double adcVoltageToTemp(double adc_voltage) {
        adc_voltage -= 145.3e-3; // 145.3mV @ 25C
        adc_voltage /= 490e-6;   // 490uV / C
        return 25.0 + adc_voltage;
    }

    public double lsbToNativeUnits(int lsb, final int ch) {
        double adc_volts = 0;
        byte channel_setting = ch==0?meter_settings.ch1set:meter_settings.ch2set;
        channel_setting &= METER_CH_SETTINGS_INPUT_MASK;
        if(disp_hex[ch]) {
            return lsb;
        }
        switch(channel_setting) {
            case 0x00:
                // Regular electrode input
                switch(ch) {
                    case 0:
                        // FIXME: CH1 offset is treated as an extrinsic offset because it's dominated by drift in the isns amp
                        adc_volts = lsbToADCInVoltage(lsb,ch);
                        adc_volts -= offsets[0];
                        return adcVoltageToCurrent(adc_volts);
                    case 1:
                        lsb -= offsets[1];
                        adc_volts = lsbToADCInVoltage(lsb,ch);
                        return adcVoltageToHV(adc_volts);
                    default:
                        Log.w(null,"Invalid channel");
                        return 0;
                }
            case 0x04:
                adc_volts = lsbToADCInVoltage(lsb,ch);
                return adcVoltageToTemp(adc_volts);
            case 0x09:
                // Apply offset
                lsb -= offsets[1];
                adc_volts = lsbToADCInVoltage(lsb,ch);
                if( disp_ch3_mode == CH3_MODES.RESISTANCE ) {
                    // Convert to Ohms
                    double retval = adc_volts;
                    if( 0 != (meter_settings.measure_settings & METER_MEASURE_SETTINGS_ISRC_LVL) ) {
                        retval /= 100e-6;
                    } else {
                        retval /= 100e-9;
                    }
                    retval -= 7.9; // Compensate for the PTC
                    return retval;
                } else {
                    return adc_volts;
                }
            default:
                Log.w(null,"Unrecognized channel setting");
                return adc_volts;
        }
    }

    public String getDescriptor(final int channel) {
        byte channel_setting = channel==0?meter_settings.ch1set:meter_settings.ch2set;
        channel_setting &= METER_CH_SETTINGS_INPUT_MASK;
        switch( channel_setting ) {
            case 0x00:
                switch (channel) {
                    case 0:
                        if(disp_ac[channel]){return "Current AC";}
                        else {return "Current DC";}
                    case 1:
                        if(disp_ac[channel]){return "Voltage AC";}
                        else {return "Voltage DC";}
                    default:
                        return "Invalid";
                }
            case 0x04:
                // Temperature sensor
                return "Temperature";
            case 0x09:
                // Channel 3 in
                switch( disp_ch3_mode ) {
                    case VOLTAGE:
                        if(disp_ac[channel]){return "Aux Voltage AC";}
                        else {return "Aux Voltage DC";}
                    case RESISTANCE:
                        return "Resistance";
                    case DIODE:
                        return "Diode Test";
                }
                break;
            default:
                Log.w(null,"Unrecognized setting");
        }
        return "";
    }

    public String getUnits(final int channel) {
        byte channel_setting = channel==0?meter_settings.ch1set:meter_settings.ch2set;
        channel_setting &= METER_CH_SETTINGS_INPUT_MASK;
        if(disp_hex[channel]) {
            return "RAW";
        }
        switch( channel_setting ) {
            case 0x00:
                switch (channel) {
                    case 0:
                        return "A";
                    case 1:
                        return "V";
                    default:
                        return "?";
                }
            case 0x04:
                return "C";
            case 0x09:
                switch( disp_ch3_mode ) {
                    case VOLTAGE:
                        return "V";
                    case RESISTANCE:
                        return "Ω";
                    case DIODE:
                        return "V";
                }
            default:
                Log.w(null,"Unrecognized CH1SET setting");
                return "";
        }
    }

    String getInputLabel(final int channel) {
        byte channel_setting = channel==0?meter_settings.ch1set:meter_settings.ch2set;
        channel_setting &= METER_CH_SETTINGS_INPUT_MASK;
        switch( channel_setting ) {
            case 0x00:
                switch (channel) {
                    case 0:
                        return "A";
                    case 1:
                        return "V";
                    default:
                        return "?";
                }
            case 0x04:
                return "INT";
            case 0x09:
                return "Ω";
            default:
                Log.w(null,"Unrecognized setting");
                return "";
        }
    }
}
