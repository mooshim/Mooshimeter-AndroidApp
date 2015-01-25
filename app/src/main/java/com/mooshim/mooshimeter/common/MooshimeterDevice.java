package com.mooshim.mooshimeter.common;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.mooshim.mooshimeter.main.SensorTagGatt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Created by First on 1/7/2015.
 */

public class MooshimeterDevice {
    public static final byte METER_SHUTDOWN  = 0;
    public static final byte METER_STANDBY   = 1;
    public static final byte METER_PAUSED    = 2;
    public static final byte METER_RUNNING   = 3;
    public static final byte METER_HIBERNATE = 4;

    private Context mContext;
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
    public final boolean[] disp_ac         = new boolean[]{false,false};
    public final boolean[] disp_hex        = new boolean[]{false,false};
    public CH3_MODES       disp_ch3_mode   = CH3_MODES.VOLTAGE;
    public final boolean[] disp_range_auto = new boolean[]{true,true};
    public boolean         disp_rate_auto  = true;
    public boolean         disp_depth_auto = true;

    public boolean offset_on      = false;
    public final double[] offsets = new double[]{0,0,0};

    private boolean ch1_last_received = false;
    private int buf_i = 0;
    private final double[][] buffers = new double[2][256];

    private Block cb = null;
    private Block stream_cb = null;
    private Block buffer_done_cb = null;

    private static void putInt24(ByteBuffer b, int arg) {
        // Puts the bottom 3 bytes of arg on to b
        ByteBuffer tmp = ByteBuffer.allocate(4);
        byte[] tb = new byte[3];
        tmp.putInt(arg);
        tmp.flip();
        tmp.get(tb);
        b.put( tb );
    }
    private static int  getInt24(ByteBuffer b) {
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
        public abstract byte[] pack();
        public abstract void unpack(byte[] in);
    }
    public class MeterSettings    extends Serializable {
        public byte present_meter_state;
        public byte target_meter_state;
        public byte trigger_setting;
        public short trigger_x_offset;
        public int trigger_crossing;
        public byte measure_settings;
        public byte calc_settings;
        public byte[] chset = new byte[2];
        public byte adc_settings;

        @Override
        public byte[] pack() {
            byte[] retval = new byte[13];
            ByteBuffer b = ByteBuffer.wrap(retval);

            b.put(      present_meter_state);
            b.put(      target_meter_state);
            b.put(      trigger_setting);
            b.putShort( trigger_x_offset);
            putInt24(b, trigger_crossing);
            b.put(      measure_settings);
            b.put(      calc_settings);
            b.put(      chset[0]);
            b.put(      chset[1]);
            b.put(      adc_settings);

            return retval;
        }

        @Override
        public void unpack(byte[] in) {
            ByteBuffer b = ByteBuffer.wrap(in);
            b.order(ByteOrder.LITTLE_ENDIAN);
            present_meter_state = b.get();
            target_meter_state  = b.get();
            trigger_setting     = b.get();
            trigger_x_offset    = b.getShort();
            trigger_crossing    = getInt24(b);
            measure_settings    = b.get();
            calc_settings       = b.get();
            chset[0]              = b.get();
            chset[1]              = b.get();
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
        public byte[] pack() {
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
        public void unpack(byte[] in) {
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
        public byte[] pack() {
            byte[] retval = new byte[8];
            ByteBuffer b = ByteBuffer.wrap(retval);

            b.put     (pcb_version);
            b.put     (assembly_variant);
            b.putShort(lot_number);
            b.putInt  (build_time);

            return retval;
        }

        @Override
        public void unpack(byte[] in) {
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
        public byte[] pack() {
            byte[] retval = new byte[16];
            ByteBuffer b = ByteBuffer.wrap(retval);

            putInt24(b, reading_lsb[0]);
            putInt24(b, reading_lsb[1]);
            b.putFloat( reading_ms[0]);
            b.putFloat( reading_ms[1]);

            return retval;
        }

        @Override
        public void unpack(byte[] in) {
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
    public String           meter_name;

    private static MooshimeterDevice mInstance = null;

    public static MooshimeterDevice getInstance() {
        return mInstance;
    }

    public static MooshimeterDevice Initialize(Context context, final Block on_init) {
        if(mInstance==null) {
            mInstance = new MooshimeterDevice(context, on_init);
        } else {
            Log.e(null, "Already initialized!");
        }
        return mInstance;
    }

    public static void Destroy() {
        // Clear the global instance
        if(mInstance != null) {
            mInstance.close();
            mInstance = null;
        }
    }

    protected MooshimeterDevice(Context context, final Block on_init) {
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
        mContext = context;

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
                                reqMeterSample( new Block() {
                                    @Override
                                    public void run() {
                                        reqMeterName(on_init);
                                        Log.i(null,"Meter initialization complete");
                                    }
                                } );
                            }
                        });
                    }
                });
            }
        });
    }

    public void close() {
        stream_cb = null;
        //mContext.unregisterReceiver(mGattUpdateReceiver);
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
    public void reqMeterName       ( Block new_cb ) { req(SensorTagGatt.METER_NAME, new_cb); }

    public void sendMeterSettings   ( Block new_cb ) { send(SensorTagGatt.METER_SETTINGS, meter_settings.pack(), new_cb); }
    public void sendMeterLogSettings( Block new_cb ) { send(SensorTagGatt.METER_LOG_SETTINGS, meter_log_settings.pack(), new_cb); }
    public void sendMeterInfo       ( Block new_cb ) { send(SensorTagGatt.METER_INFO, meter_info.pack(), new_cb); }
    public void sendMeterSample     ( Block new_cb ) { send(SensorTagGatt.METER_SAMPLE, meter_sample.pack(), new_cb); }
    public void sendMeterName       ( Block new_cb ) { send(SensorTagGatt.METER_NAME, meter_name.getBytes(), new_cb); }

    public void enableMeterStreamSample( boolean enable, Block new_cb, Block new_stream_cb ) {
        stream_cb = new_stream_cb;
        BluetoothGattCharacteristic c = bt_gatt_service.getCharacteristic(SensorTagGatt.METER_SAMPLE);
        cb = new_cb;
        bt_service.setCharacteristicNotification(c,enable);
    }

    public void enableMeterStreamBuf( boolean enable, Block new_cb ) {
        // TODO: The buffers are only streamed together, why not unite them in firmware?
        BluetoothGattCharacteristic c;
        c = bt_gatt_service.getCharacteristic(SensorTagGatt.METER_CH1BUF);
        cb = new_cb;
        bt_service.setCharacteristicNotification(c,enable);
        c = bt_gatt_service.getCharacteristic(SensorTagGatt.METER_CH2BUF);
        bt_service.setCharacteristicNotification(c,enable);
    }

    public void getBuffer(Block onReceived) {
        meter_settings.calc_settings &=~(METER_CALC_SETTINGS_MS|METER_CALC_SETTINGS_MEAN);
        meter_settings.calc_settings |= METER_CALC_SETTINGS_ONESHOT;
        meter_settings.target_meter_state = METER_RUNNING;

        buffer_done_cb = onReceived;

        enableMeterStreamSample(false, new Block() {
            @Override
            public void run() {
                enableMeterStreamBuf(true, new Block() {
                    @Override
                    public void run() {
                        sendMeterSettings(null);
                    }
                });
            }
        }, null);
    }

    private int getBufLen() {
        return (1<<(meter_settings.calc_settings & METER_CALC_SETTINGS_DEPTH_LOG2));
    }

    private void handleBufStreamUpdate(byte[] data, int channel) {
        int buf_len_bytes = getBufLen()*3;
        double[] target = buffers[channel];
        ByteBuffer bbuf = ByteBuffer.wrap(data);

        for(int i = 0; i < data.length; i+=3) {
            // Unload readings 3 bytes at a time, append them to the sample buffer
            final int lsb = getInt24(bbuf);
            target[buf_i] = lsbToNativeUnits(lsb,channel);
            buf_i++;
        }

        if(buf_i >= buf_len_bytes) {
            Log.d(null,"Complete sample buffer received");
            if(buffer_done_cb != null) {
                buffer_done_cb.run();
            }
        }

        // This is a primitive way to synchronize buffers
        if(ch1_last_received ^ (channel==0)) { buf_i = 0; }
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
        } else if(uuid.equals(SensorTagGatt.METER_CH1BUF)) {
            handleBufStreamUpdate(value,0);
        } else if(uuid.equals(SensorTagGatt.METER_CH2BUF)) {
            handleBufStreamUpdate(value,1);
        } else if(uuid.equals(SensorTagGatt.METER_NAME)) {
            meter_name = new String(value);
        }
        callCB();
    }

    private void handleDescriptorWrite(UUID uuid) {
        if(uuid.equals(SensorTagGatt.METER_SAMPLE)) {
            callCB();
        } else if(uuid.equals(SensorTagGatt.METER_CH1BUF)) {
            // FIXME: Right now skip any CB calling on METER_CH1BUF because it is only done in a pair with
            // CH2BUF, so we should only call the CB when the confirmation of METER_CH2BUF comes in.  This is sloppy.
        } else if(uuid.equals(SensorTagGatt.METER_CH2BUF)) {
            callCB();
        }
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
                if(stream_cb != null) { stream_cb.run(); }
            } else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action)) {
                Log.d(null, "onCharacteristicWrite");
                callCB();
            } else if (BluetoothLeService.ACTION_DESCRIPTOR_WRITE.equals(action)) {
                Log.d(null, "onDescriptorWrite");
                handleDescriptorWrite(uuid);
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(null, "GATT error code: " + status);
            }
        }
    };

    //////////////////////////////////////
    // Autoranging
    //////////////////////////////////////

    byte pga_cycle(byte chx_set, boolean inc, boolean wrap) {
        // These are the PGA settings we will entertain
        final byte ps[] = {0x60,0x40,0x10};
        byte i;
        // Find the index of the present PGA setting
        for(i = 0; i < ps.length; i++) {
            if(ps[i] == (chx_set & METER_CH_SETTINGS_PGA_MASK)) break;
        }

        if(i>=ps.length) {
            // If we didn't find it, default to setting 0
            i = 0;
        } else {
            // Increment or decrement the PGA setting
            if(inc){
                if(++i >= ps.length) {
                    if(wrap){i=0;}
                    else    {i--;}
                }
            }
            else {
                if(--i < 0) {
                    if(wrap){i = (byte)(ps.length-1);}
                    else    {i++;}
                }
            }
        }
        // Mask the new setting back in
        chx_set &=~METER_CH_SETTINGS_PGA_MASK;
        chx_set |= ps[i];
        return chx_set;
    }

    int getLowerRange(int channel) {
        int tmp;
        final int pga_setting = meter_settings.chset[channel] & METER_CH_SETTINGS_PGA_MASK;

        switch(meter_settings.chset[channel] & METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input
                switch(channel) {
                    case 0:
                        // We are measuring current.  We can boost PGA, but that's all.
                        switch(pga_setting) {
                            case 0x60:
                                return 0;
                            case 0x40:
                                return (int)(0.33*(1<<22));
                            case 0x10:
                                return (int)(0.25*(1<<22));
                        }
                        break;
                    case 1:
                        // Switch the ADC GPIO to activate dividers
                        tmp = (meter_settings.adc_settings & ADC_SETTINGS_GPIO_MASK)>>4;
                        switch(tmp) {
                            case 1:
                                return 0;
                            case 2:
                                return (int)(0.1*(1<<22));
                        }
                        break;
                }
                break;
            case 0x04:
                // Temp input
                return 0;
            case 0x09:
                switch(disp_ch3_mode) {
                case VOLTAGE:
                    switch(pga_setting) {
                        case 0x60:
                            return 0;
                        case 0x40:
                            return (int)(0.33*(1<<22));
                        case 0x10:
                            return (int)(0.25*(1<<22));
                    }
                    break;
                case RESISTANCE:
                case DIODE:
                    switch(pga_setting) {
                        case 0x60:
                            if(0==(meter_settings.measure_settings&METER_MEASURE_SETTINGS_ISRC_LVL))
                            {return (int)(0.012*(1<<22));}
                            else {return 0;}
                        case 0x40:
                            return (int)(0.33*(1<<22));
                        case 0x10:
                            return (int)(0.25*(1<<22));
                    }
                    break;
            }
            break;
        }
        return 0;
    }

    private void bumpRange(int channel, boolean raise, boolean wrap) {
        byte channel_setting    = meter_settings.chset[channel];
        int tmp;

        switch(channel_setting & METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input
                switch(channel) {
                    case 0:
                        // We are measuring current.  We can boost PGA, but that's all.
                        channel_setting = pga_cycle(channel_setting,raise,wrap);
                        break;
                    case 1:
                        // Switch the ADC GPIO to activate dividers
                        // NOTE: Don't bother with the 1.2V range for now.  Having a floating autoranged input leads to glitchy behavior.
                        tmp = (meter_settings.adc_settings & ADC_SETTINGS_GPIO_MASK)>>4;
                        if(raise) {
                            if(++tmp >= 3) {
                                if(wrap){tmp=1;}
                                else    {tmp--;}
                            }
                        } else {
                            if(--tmp < 1) {
                                if(wrap){tmp=2;}
                                else    {tmp++;}
                            }
                        }
                        tmp<<=4;
                        meter_settings.adc_settings &= ~ADC_SETTINGS_GPIO_MASK;
                        meter_settings.adc_settings |= tmp;
                        channel_setting &=~METER_CH_SETTINGS_PGA_MASK;
                        channel_setting |= 0x10;
                        break;
                }
                break;
            case 0x04:
                // Temp input
                break;
            case 0x09:
                switch(disp_ch3_mode) {
                case VOLTAGE:
                    channel_setting = pga_cycle(channel_setting,raise,wrap);
                    break;
                case RESISTANCE:
                case DIODE:
                    // This case is annoying.  We want PGA to always wrap if we are in the low range and going up OR in the high range and going down
                    if( 0 != ((raise?0:METER_MEASURE_SETTINGS_ISRC_LVL) ^ (meter_settings.measure_settings & METER_MEASURE_SETTINGS_ISRC_LVL))) {
                    wrap = true;
                }
                channel_setting = pga_cycle(channel_setting,raise,wrap);
                tmp = channel_setting & METER_CH_SETTINGS_PGA_MASK;
                tmp >>=4;
                if(   ( raise && tmp == 6) || (!raise && tmp == 1) ) {
                    meter_settings.measure_settings ^= METER_MEASURE_SETTINGS_ISRC_LVL;
                }
                break;
            }
            break;
        }
        meter_settings.chset[channel] = channel_setting;
    }

    public void applyAutorange() {
        final boolean ac_used = disp_ac[0] || disp_ac[1];
        final int upper_limit_lsb = (int)( 0.9*(1<<22));
        final int lower_limit_lsb = (int)(-0.9*(1<<22));

        // Autorange sample rate and buffer depth.
        // If anything is doing AC, we need a deep buffer and fast sample
        if(disp_rate_auto) {
            meter_settings.adc_settings &= ~ADC_SETTINGS_SAMPLERATE_MASK;
            if(ac_used) {meter_settings.adc_settings |= 5;} // 4kHz
            else        {meter_settings.adc_settings |= 0;} // 125Hz
        }
        if(disp_depth_auto) {
            meter_settings.calc_settings &=~METER_CALC_SETTINGS_DEPTH_LOG2;
            if(ac_used) {meter_settings.calc_settings |= 8;} // 256 samples
            else        {meter_settings.calc_settings |= 5;} // 32 samples
        }
        for(byte i = 0; i < 2; i++) {
            int inner_limit_lsb = (int)(0.7*(getLowerRange(i)));
            if(disp_range_auto[i]) {
                // Note that the ranges are asymmetrical - we have 1.8V of headroom above and 1.2V below
                int    mean_lsb;
                double rms_lsb;
                mean_lsb = meter_sample.reading_lsb[i];
                rms_lsb  = Math.sqrt(meter_sample.reading_ms[i]);
                if(   mean_lsb > upper_limit_lsb
                        || mean_lsb < lower_limit_lsb
                        || rms_lsb*Math.sqrt(2.) > Math.abs(lower_limit_lsb) ) {
                    bumpRange(i,true,false);
                } else if(   Math.abs(mean_lsb)    < inner_limit_lsb
                        && rms_lsb*Math.sqrt(2.) < inner_limit_lsb ) {
                    bumpRange(i,false,false);
                }
            }
        }
    }

    //////////////////////////////////////
    // Data conversion
    //////////////////////////////////////

    public static final int METER_MEASURE_SETTINGS_ISRC_ON         = 0x01;
    public static final int METER_MEASURE_SETTINGS_ISRC_LVL        = 0x02;
    public static final int METER_MEASURE_SETTINGS_ACTIVE_PULLDOWN = 0x04;
    
    public static final int METER_CALC_SETTINGS_DEPTH_LOG2 = 0x0F;
    public static final int METER_CALC_SETTINGS_MEAN       = 0x10;
    public static final int METER_CALC_SETTINGS_ONESHOT    = 0x20;
    public static final int METER_CALC_SETTINGS_MS         = 0x40;
    
    public static final int ADC_SETTINGS_SAMPLERATE_MASK = 0x07;
    public static final int ADC_SETTINGS_GPIO_MASK = 0x30;
    
    public static final int METER_CH_SETTINGS_PGA_MASK = 0x70;
    public static final int METER_CH_SETTINGS_INPUT_MASK = 0x0F;

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
        int pga_setting = meter_settings.chset[channel];
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
        if(channel == 0 && (meter_settings.chset[0] & METER_CH_SETTINGS_INPUT_MASK) == 0 ) {
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
                pga_setting = meter_settings.chset[0] >> 4;
                break;
            case 1:
                pga_setting = meter_settings.chset[1] >> 4;
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
        final byte channel_setting = (byte) (meter_settings.chset[ch] & METER_CH_SETTINGS_INPUT_MASK);
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
                lsb -= offsets[2];
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
        final byte channel_setting = (byte) (meter_settings.chset[channel] & METER_CH_SETTINGS_INPUT_MASK);
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
        final byte channel_setting = (byte) (meter_settings.chset[channel] & METER_CH_SETTINGS_INPUT_MASK);
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
                Log.w(null,"Unrecognized chset[0] setting");
                return "";
        }
    }

    public String getInputLabel(final int channel) {
        final byte channel_setting = (byte) (meter_settings.chset[channel] & METER_CH_SETTINGS_INPUT_MASK);
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
