package com.mooshim.mooshimeter.common;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import static java.util.UUID.fromString;

/**
 * Created by First on 1/7/2015.
 */

public class MooshimeterDevice {
    public static class mUUID {
        public final static UUID
                METER_SERVICE      = fromString("1BC5FFA0-0200-62AB-E411-F254E005DBD4"),
                METER_INFO         = fromString("1BC5FFA1-0200-62AB-E411-F254E005DBD4"),
                METER_NAME         = fromString("1BC5FFA2-0200-62AB-E411-F254E005DBD4"),
                METER_SETTINGS     = fromString("1BC5FFA3-0200-62AB-E411-F254E005DBD4"),
                METER_LOG_SETTINGS = fromString("1BC5FFA4-0200-62AB-E411-F254E005DBD4"),
                METER_UTC_TIME     = fromString("1BC5FFA5-0200-62AB-E411-F254E005DBD4"),
                METER_SAMPLE       = fromString("1BC5FFA6-0200-62AB-E411-F254E005DBD4"),
                METER_CH1BUF       = fromString("1BC5FFA7-0200-62AB-E411-F254E005DBD4"),
                METER_CH2BUF       = fromString("1BC5FFA8-0200-62AB-E411-F254E005DBD4"),
                METER_CAL          = fromString("1BC5FFA9-0200-62AB-E411-F254E005DBD4"),
                METER_LOG_DATA     = fromString("1BC5FFAA-0200-62AB-E411-F254E005DBD4"),
                METER_TEMP         = fromString("1BC5FFAB-0200-62AB-E411-F254E005DBD4"),
                METER_BAT          = fromString("1BC5FFAC-0200-62AB-E411-F254E005DBD4"),

                OAD_SERVICE_UUID    = fromString("1BC5FFC0-0200-62AB-E411-F254E005DBD4"),
                OAD_IMAGE_NOTIFY    = fromString("1BC5FFC1-0200-62AB-E411-F254E005DBD4"),
                OAD_IMAGE_Runnable_REQ = fromString("1BC5FFC2-0200-62AB-E411-F254E005DBD4"),
                OAD_REBOOT          = fromString("1BC5FFC3-0200-62AB-E411-F254E005DBD4");
    }

    private static final String TAG="MooshimeterDevice";

    public static final byte METER_SHUTDOWN  = 0;
    public static final byte METER_STANDBY   = 1;
    public static final byte METER_PAUSED    = 2;
    public static final byte METER_RUNNING   = 3;
    public static final byte METER_HIBERNATE = 4;

    private BLEUtil mBLEUtil;
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

    private abstract class MeterStructure {
        public void update(final Runnable cb) {
            mBLEUtil.req(getUUID(), new BLEUtil.BLEUtilCB() {
                @Override
                public void run() {
                    if(error != BluetoothGatt.GATT_SUCCESS) {
                        final String s = String.format("Read has error code %d",error);
                        Log.e(TAG,s);
                    } else {
                        unpack(value);
                    }
                    if(cb != null) {
                        cb.run();
                    }
                }
            });
        }
        public void send(final Runnable cb) {
            mBLEUtil.send(getUUID(), pack(), new BLEUtil.BLEUtilCB() {
                @Override
                public void run() {
                    if(cb != null) {
                        if(error != BluetoothGatt.GATT_SUCCESS) {
                            final String s = String.format("Write has error code %d",error);
                            Log.e(TAG,s);
                        }
                        cb.run();
                    }
                }
            });
        }
        public void enableNotify(boolean enable, final Runnable on_complete, final Runnable on_notify) {
            mBLEUtil.enableNotify(getUUID(),enable,new BLEUtil.BLEUtilCB() {
                @Override
                public void run() {
                    if(on_complete != null) {
                        on_complete.run();
                    }
                }
            }, new BLEUtil.BLEUtilCB() {
                @Override
                public void run() {
                    if(error != BluetoothGatt.GATT_SUCCESS) {
                        final String s = String.format("Notification has error code %d",error);
                        Log.e(TAG,s);
                    } else {
                        unpack(value);
                    }
                    if(on_notify != null) {
                        on_notify.run();
                    }
                }
            });
        }
        public abstract byte[] pack();
        public abstract void unpack(byte[] in);
        public abstract UUID getUUID();
    }
    public class MeterSettings    extends MeterStructure {
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
        public UUID getUUID() { return mUUID.METER_SETTINGS; }

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
    public class MeterLogSettings extends MeterStructure {
        public byte  sd_present;
        public byte  present_logging_state;
        public byte  logging_error;
        public short file_number;
        public int   file_offset;
        public byte  target_logging_state;
        public short logging_period_ms;
        public int   logging_n_cycles;

        @Override
        public UUID getUUID() { return mUUID.METER_LOG_SETTINGS; }

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
    public class MeterInfo        extends MeterStructure {
        byte  pcb_version;
        byte  assembly_variant;
        short lot_number;
        int   build_time;

        @Override
        public UUID getUUID() { return mUUID.METER_INFO; }

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
    public class MeterSample      extends MeterStructure {
        public final int   reading_lsb[] = new int[2];
        public final float reading_ms[]  = new float[2];

        @Override
        public UUID getUUID() { return mUUID.METER_SAMPLE; }

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
    public class MeterName        extends MeterStructure {
        public String name = "Mooshimeter V.1";

        @Override
        public UUID getUUID() { return mUUID.METER_NAME; }

        @Override
        public byte[] pack() {
            return name.getBytes();
        }
        @Override
        public void unpack(byte[] in) {
            name = in.toString();
        }
    }
    // FIXME: The channel buffer system uses a lot of repeated code.  Really should be merged on the firmware side.
    public class MeterCH1Buf      extends MeterStructure {
        public int buf_i = 0;
        public byte[] buf = new byte[1024];
        public float[] floatBuf = new float[256];

        @Override
        public UUID getUUID() { return mUUID.METER_CH1BUF; }

        @Override
        public byte[] pack() {
            // SHOULD NEVER BE CALLED
            return null;
        }
        @Override
        public void unpack(byte[] arg) {
            // Nasty synchonization hack
            meter_ch2_buf.buf_i = 0;
            final int nBytes = getBufLen()*3;
            for(byte b : arg) {
                if(buf_i >= nBytes) {
                    Log.e(TAG,"CH1 OVERFLOW");
                    break;
                }
                buf[buf_i++] = b;
            }
            if(buf_i >= nBytes) {
                // Sample buffer is full
                Log.d(TAG,"CH1 full");
                if(buf_i > nBytes) {
                    return;
                }

                ByteBuffer bb = ByteBuffer.wrap(buf);
                for(int i = 0; i < getBufLen(); i++) {
                    floatBuf[i] = (float)lsbToNativeUnits(getInt24(bb),0);
                }
            }
        }
    }
    public class MeterCH2Buf      extends MeterStructure {
        public int buf_i = 0;
        public byte[] buf = new byte[1024];
        public float[] floatBuf = new float[256];
        public Runnable buf_full_cb;

        @Override
        public UUID getUUID() { return mUUID.METER_CH2BUF; }

        @Override
        public byte[] pack() {
            // SHOULD NEVER BE CALLED
            return null;
        }
        @Override
        public void unpack(byte[] arg) {
            // Nasty synchonization hack
            meter_ch1_buf.buf_i = 0;
            final int nBytes = getBufLen()*3;
            for(byte b : arg) {
                if(buf_i >= nBytes) {
                    Log.e(TAG,"CH2 OVERFLOW");
                    break;
                }
                buf[buf_i++] = b;
            }
            if(buf_i >= nBytes) {
                // Sample buffer is full
                Log.d(TAG,"CH2 full");
                if(buf_i > nBytes) {
                    return;
                }

                ByteBuffer bb = ByteBuffer.wrap(buf);
                for(int i = 0; i < getBufLen(); i++) {
                    floatBuf[i] = (float)lsbToNativeUnits(getInt24(bb),1);
                }
                if(buf_full_cb!=null){
                    buf_full_cb.run();
                }
            }
        }
    }

    public MeterSettings    meter_settings;
    public MeterLogSettings meter_log_settings;
    public MeterInfo        meter_info;
    public MeterSample      meter_sample;
    public MeterName        meter_name;
    public MeterCH1Buf      meter_ch1_buf;
    public MeterCH2Buf      meter_ch2_buf;

    private static MooshimeterDevice mInstance = null;

    public static synchronized MooshimeterDevice getInstance() {
        return mInstance;
    }

    public static synchronized MooshimeterDevice Initialize(Context context, final Runnable on_init) {
        if(mInstance==null) {
            mInstance = new MooshimeterDevice(context, on_init);
        } else {
            Log.e(TAG, "Already initialized!");
        }
        return mInstance;
    }

    public static void Destroy() {
        // Clear the global instance
        if(mInstance != null) {
            mInstance.close();
            mInstance.mBLEUtil.Destroy();
            mInstance = null;
        }
    }

    protected MooshimeterDevice(Context context, final Runnable on_init) {
        // Initialize internal structures
        mBLEUtil = BLEUtil.getInstance(context);
        mContext = context;
        meter_settings      = new MeterSettings();
        meter_log_settings  = new MeterLogSettings();
        meter_info          = new MeterInfo();
        meter_sample        = new MeterSample();
        meter_ch1_buf       = new MeterCH1Buf();
        meter_ch2_buf       = new MeterCH2Buf();

        // Grab the initial settings
        meter_settings.update(new Runnable() {
            @Override
            public void run() {
                meter_log_settings.update(new Runnable() {
                    @Override
                    public void run() {
                        meter_info.update(new Runnable() {
                            @Override
                            public void run() {
                                on_init.run();
                                // FIXME: This name grab causes a crash.  Strongly suspect it's in the packing and unpacking.
                                //meter_name.update(on_init);
                                Log.i(TAG, "Meter initialization complete");
                            }
                        });
                    }
                });
            }
        });
    }

    public void close() {
        mBLEUtil.close();
    }

    ////////////////////////////////
    // Convenience functions
    ////////////////////////////////
    public int getBufLen() {
        return (1<<(meter_settings.calc_settings & METER_CALC_SETTINGS_DEPTH_LOG2));
    }

    public void getBuffer(final Runnable onReceived) {
        // Set up for oneshot, turn off all math in firmware
        meter_settings.calc_settings &=~(METER_CALC_SETTINGS_MS|METER_CALC_SETTINGS_MEAN);
        meter_settings.calc_settings |= METER_CALC_SETTINGS_ONESHOT;
        meter_settings.target_meter_state = METER_PAUSED;

        meter_settings.send(new Runnable() {
            @Override
            public void run() {
                meter_sample.enableNotify(false,new Runnable() {
                    @Override
                    public void run() {
                        meter_ch1_buf.enableNotify(true,new Runnable() {
                            @Override
                            public void run() {
                                meter_ch2_buf.enableNotify(true,new Runnable() {
                                    @Override
                                    public void run() {
                                        meter_ch2_buf.buf_full_cb = onReceived;
                                        meter_settings.target_meter_state = METER_RUNNING;
                                        meter_ch1_buf.buf_i = 0;
                                        meter_ch2_buf.buf_i = 0;
                                        meter_settings.send(null);
                                    }
                                },null);
                            }
                        },null);
                    }
                }, null);
            }
        });
    }

    public void pauseStream(final Runnable cb) {
        meter_sample.enableNotify(false, new Runnable() {
            @Override
            public void run() {
                if(meter_settings.target_meter_state != METER_PAUSED) {
                    meter_settings.target_meter_state = METER_PAUSED;
                    meter_settings.send(cb);
                } else {
                    cb.run();
                }
            }
        }, null);
    }

    public void playSampleStream(final Runnable cb, final Runnable on_notify) {
        meter_settings.calc_settings |= MooshimeterDevice.METER_CALC_SETTINGS_MEAN | MooshimeterDevice.METER_CALC_SETTINGS_MS;
        meter_settings.calc_settings &=~MooshimeterDevice.METER_CALC_SETTINGS_ONESHOT;

        meter_sample.enableNotify(true, new Runnable() {
            @Override
            public void run() {
                meter_settings.send(cb);
            }
        }, on_notify);
    }

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
                Log.i(TAG,"Should not be here");
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
                Log.w(TAG,"Invalid setting!");
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

    public double getIsrcCurrent() {
        if( 0 == (meter_settings.measure_settings & METER_MEASURE_SETTINGS_ISRC_ON) ) {
            return 0;
        }
        if( 0 != (meter_settings.measure_settings & METER_MEASURE_SETTINGS_ISRC_LVL) ) {
            return 100e-6;
        } else {
            return 100e-9;
        }
    }

    public double lsbToNativeUnits(int lsb, final int ch) {
        double adc_volts = 0;
        final double ptc_resistance = 7.9;
        final byte channel_setting = (byte) (meter_settings.chset[ch] & METER_CH_SETTINGS_INPUT_MASK);
        if(disp_hex[ch]) {
            return lsb;
        }
        switch(channel_setting) {
            case 0x00:
                // Regular electrode input
                switch(ch) {
                    case 0:
                        // CH1 offset is treated as an extrinsic offset because it's dominated by drift in the isns amp
                        adc_volts = lsbToADCInVoltage(lsb,ch);
                        adc_volts -= offsets[0];
                        return adcVoltageToCurrent(adc_volts);
                    case 1:
                        // CH2 offset is treaded as an intrinsic offset because it's dominated by offset in the ADC itself
                        lsb -= offsets[1];
                        adc_volts = lsbToADCInVoltage(lsb,ch);
                        return adcVoltageToHV(adc_volts);
                    default:
                        Log.w(TAG,"Invalid channel");
                        return 0;
                }
            case 0x04:
                adc_volts = lsbToADCInVoltage(lsb,ch);
                return adcVoltageToTemp(adc_volts);
            case 0x09:
                // CH3 is complicated.  When measuring aux voltage, offset is dominated by intrinsic offsets in the ADC
                // When measuring resistance, offset is a resistance and must be treated as such
                final double isrc_current = getIsrcCurrent();
                if( isrc_current != 0 ) {
                    // Current source is on, apply compensation for PTC drop
                    adc_volts = lsbToADCInVoltage(lsb,ch);
                    adc_volts -= ptc_resistance*isrc_current;
                    adc_volts -= offsets[2]*isrc_current;
                } else {
                    // Current source is off, offset is intrinsic
                    lsb -= offsets[2];
                    adc_volts = lsbToADCInVoltage(lsb,ch);
                }
                if( disp_ch3_mode == CH3_MODES.RESISTANCE ) {
                    // Convert to Ohms
                    return adc_volts/isrc_current;
                } else {
                    return adc_volts;
                }
            default:
                Log.w(TAG,"Unrecognized channel setting");
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
                Log.w(TAG,"Unrecognized setting");
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
                Log.w(TAG,"Unrecognized chset[0] setting");
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
                Log.w(TAG,"Unrecognized setting");
                return "";
        }
    }
}
