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

package com.mooshim.mooshimeter.devices;


import android.util.Log;

import com.mooshim.mooshimeter.common.Alerter;
import com.mooshim.mooshimeter.common.BroadcastIntentData;
import com.mooshim.mooshimeter.common.Chooser;
import com.mooshim.mooshimeter.common.LogFile;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.ThermocoupleHelper;
import com.mooshim.mooshimeter.common.Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.UUID.fromString;

public class LegacyMooshimeterDevice extends MooshimeterDeviceBase {

    /*
    mUUID stores the UUID values of all the Mooshimeter fields.
    Note that the OAD fields are only accessible when connected to the Mooshimeter in OAD mode
    and the METER_ fields are only accessible when connected in meter mode.
     */

    public static class mUUID {
        private mUUID() {}
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
                METER_BAT          = fromString("1BC5FFAC-0200-62AB-E411-F254E005DBD4");
    }

    private static final String TAG="LegacyMooshimeterDevice";

    /*
    These are the run levels of the meter state machine.  By setting meter_settings.target_meter_state,
    a run level change is requested.  The present state can be read from meter_settings.present_meter_state
    SHUTDOWN = Requesting this state will reboot the meter.
    STANDBY  = The meter will turn off the ADC and go to sleep but remains connectable over BLE.  The meter reverts to this state on disconnection.
    PAUSED   = The meter keeps the MCU on and the ADC ready but not sampling.  When the meter is connected over BLE but not sampling, it is here
    RUNNING  = The meter is actively sampling the ADC.  If notifications are enabled, samples will be sent when ready.  If ONESHOT is enabled, the meter will drop itself back to PAUSED when the sampling is complete
    HIBERNATE= The meter is asleep and not connectable over BLE.  The meter wakes once every 10 seconds to check the resistance at the Active terminal, if low enough the meter will switch itself to STANDBY
     */

    public static final byte METER_SHUTDOWN  = 0;
    public static final byte METER_STANDBY   = 1;
    public static final byte METER_PAUSED    = 2;
    public static final byte METER_RUNNING   = 3;
    public static final byte METER_HIBERNATE = 4;

    // Logging States
    // TODO: These states are deprecated in the newest version of firmware (June 22 2015)
    // Only LOGGING_OFF and LOGGING_ON exist now, but the firmware will treat anything that's not
    // LOGGING_OFF as LOGGING_ON so we can leave this for now and maintain backwards compatibility
    public static final byte LOGGING_OFF=0;     // No logging activity, revert here on error
    public static final byte LOGGING_SAMPLING=3;// Meter is presently sampling for writing to the log

    public enum INPUT_MODE {
        NATIVE,
        TEMP,
        AUX_V,
        RESISTANCE,
        DIODE,
    }
    public enum PGA_GAIN {
        PGA_GAIN_1,
        PGA_GAIN_4,
        PGA_GAIN_12,
        IGNORE,
    }
    public enum GPIO_SETTING {
        IGNORE,
        GPIO0,
        GPIO1,
        GPIO2,
        GPIO3,
    }
    public enum ISRC_SETTING {
        IGNORE,
        ISRC_OFF,
        ISRC_LOW,
        ISRC_MID,
        ISRC_HIGH,
    }

    // Display control settings
    public final Map<Channel,Float> offsets;

    /**
     * MeterSettings
     *
     * This structure controls the sampling behavior of the Mooshimeter.
     *
     * present_meter_state
     * Read-only - Writing to this value has no effect
     * The present state of the Mooshimeter.  State values can be one of
     * METER_SHUTDOWN
     * METER_STANDBY
     * METER_PAUSED
     * METER_RUNNING
     * METER_HIBERNATE
     *
     * target_meter_state
     * The desired state of the Mooshimeter.  Write to this value to request a state change.  Valid
     * values same as present_meter_state above.
     *
     * trigger_setting
     * Unused
     *
     * trigger_x_offset
     * Unused
     *
     * trigger_crossing
     * Unused
     *
     * measure_settings
     * Toggles the current source on the Active port and controls the current source level
     * Bit 0x01   SET: Current source on
     * Bit 0x01 UNSET: Current source off
     * Bit 0x02   SET: Current source level 100uA
     * Bit 0x02 UNSET: Current source level 100nA
     *
     * calc_settings
     * Controls what processing is performs on the ADC samples.
     * Bits 0x0F: Sample depth log2 - Sets how many ADC samples to take.
     *  Value 0 = 1 sample
     *  Value 4 = 16 samples
     *  etc.
     *  Valid up to 256 samples
     * Bit 0x10: Turn on mean calculation
     * Bit 0x20: Turn on ONESHOT mode - meter will fill the sample buffer once and then revert to METER_PAUSED state
     * Bit 0x40: Turn on RMS calculation.  Note that the meter will not bother calculating RMS on buffers less than 8 samples long
     *
     * chset
     * Channel input settings - these map directly to the CH1SET and CH2SET registers in the ADS1292
     *
     * adc_settings
     * This register controls ADC sample rate and the high voltage resistor divider
     *
     */
    public class MeterSettings    extends LegacyMeterStructure {
        public byte present_meter_state;
        public byte target_meter_state;
        public byte trigger_setting;
        public short trigger_x_offset;
        public int trigger_crossing;
        public byte measure_settings;
        public byte calc_settings;
        public byte[] chset = new byte[2];
        public byte adc_settings;
        public MeterSettings(PeripheralWrapper p) {super(p);}
        @Override
        public UUID getUUID() { return mUUID.METER_SETTINGS; }

        @Override
        public byte[] pack() {
            ByteBuffer b = wrap(new byte[13]);
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

            return b.array();
        }

        @Override
        public void unpackInner(byte[] in) {
            ByteBuffer b = wrap(in);

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
    public class MeterLogSettings extends LegacyMeterStructure {
        public byte  sd_present;
        public byte  present_logging_state;
        public byte  logging_error;
        public short file_number;
        public int   file_offset;
        public byte  target_logging_state;
        public short logging_period_ms;
        public int   logging_n_cycles;
        public MeterLogSettings(PeripheralWrapper p) {super(p);}
        @Override
        public UUID getUUID() { return mUUID.METER_LOG_SETTINGS; }

        @Override
        public byte[] pack() {
            ByteBuffer b = wrap(new byte[16]);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.put(      sd_present );
            b.put(      present_logging_state );
            b.put(      logging_error );
            b.putShort( file_number );
            b.putInt(file_offset);
            b.put(target_logging_state);
            b.putShort(logging_period_ms);
            b.putInt(logging_n_cycles);
            return b.array();
        }

        @Override
        public void unpackInner(byte[] in) {
            ByteBuffer b = wrap(in);

            sd_present              = b.get();
            present_logging_state   = b.get();
            logging_error           = b.get();
            file_number             = b.getShort();
            file_offset             = b.getInt();
            target_logging_state    = b.get();
            logging_period_ms       = b.getShort();
            logging_n_cycles        = b.getInt();
        }
    }
    public class MeterInfo        extends LegacyMeterStructure {
        public byte  pcb_version;
        public byte  assembly_variant;
        public short lot_number;
        public int   build_time;
        public MeterInfo(PeripheralWrapper p) {super(p);}
        @Override
        public UUID getUUID() { return mUUID.METER_INFO; }

        @Override
        public byte[] pack() {
            ByteBuffer b = ByteBuffer.wrap(new byte[8]);

            b.put     (pcb_version);
            b.put     (assembly_variant);
            b.putShort(lot_number);
            b.putInt  (build_time);

            return b.array();
        }

        @Override
        public void unpackInner(byte[] in) {
            ByteBuffer b = wrap(in);
            b.order(ByteOrder.LITTLE_ENDIAN);
            pcb_version      = b.get();
            assembly_variant = b.get();
            lot_number       = b.getShort();
            build_time       = b.getInt();
        }
    }
    public class MeterSample      extends LegacyMeterStructure {
        public final int[]   reading_lsb = new int[2];
        public final float[] reading_ms  = new float[2];
        public MeterSample(PeripheralWrapper p) {super(p);}
        @Override
        public UUID getUUID() { return mUUID.METER_SAMPLE; }

        @Override
        public byte[] pack() {
            ByteBuffer b = ByteBuffer.wrap(new byte[16]);

            putInt24(b, reading_lsb[0]);
            putInt24(b, reading_lsb[1]);
            b.putFloat( reading_ms[0]);
            b.putFloat(reading_ms[1]);

            return b.array();
        }

        @Override
        public void unpackInner(byte[] in) {
            ByteBuffer b = wrap(in);

            reading_lsb[0] = getInt24(b);
            reading_lsb[1] = getInt24(b);
            reading_ms[0]          = b.getFloat();
            reading_ms[1]          = b.getFloat();
        }
    }
    public class MeterName        extends LegacyMeterStructure {
        public String name = "Mooshimeter V.1";
        public MeterName(PeripheralWrapper p) {super(p);}
        @Override
        public UUID getUUID() { return mUUID.METER_NAME; }

        @Override
        public byte[] pack() {
            byte[] rval = new byte[18];
            byte[] name_bytes = name.getBytes();
            for(int i = 0; i < 17; i++) {
                if(i < name_bytes.length) {
                    rval[i] = name_bytes[i];
                } else {
                    rval[i] = 0;
                }
            }
            rval[17] = 0;
            return rval;
        }
        @Override
        public void unpackInner(final byte[] in) {
            name = new String(in);
        }
    }
    public class MeterTime        extends LegacyMeterStructure {
        public long utc_time;
        public MeterTime(PeripheralWrapper p) {super(p);}
        @Override
        public UUID getUUID() { return mUUID.METER_UTC_TIME; }

        @Override
        public byte[] pack() {
            ByteBuffer b = wrap(new byte[4]);
            b.putInt((int) utc_time);
            return b.array();
        }
        @Override
        public void unpackInner(final byte[] in) {
            ByteBuffer b = wrap(in);
            utc_time = b.getInt();
            // Prevent sign extension since Java will assume the int being unpacked is signed
            utc_time &= 0xFFFFFFFF;
        }
    }
    // FIXME: The channel buffer system uses a lot of repeated code.  Really should be merged on the firmware side.
    public class MeterCH1Buf      extends LegacyMeterStructure {
        public int buf_i = 0;
        public byte[] buf = new byte[1024];
        public float[] floatBuf = new float[256];
        public MeterCH1Buf(PeripheralWrapper p) {super(p);}
        @Override
        public UUID getUUID() { return mUUID.METER_CH1BUF; }

        @Override
        public byte[] pack() {
            // SHOULD NEVER BE CALLED
            return null;
        }
        @Override
        public void unpackInner(byte[] arg) {
            // Nasty synchonization hack
            meter_ch2_buf.buf_i = 0;
            final int nBytes = getBufferDepth()*3;
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

                ByteBuffer bb = wrap(buf);
                for(int i = 0; i < getBufferDepth(); i++) {
                    floatBuf[i] = lsbToNativeUnits(getInt24(bb),Channel.CH1);
                }
            }
            String s = String.format("CH1 Progress: %d of %d", buf_i, nBytes);
            Log.i(TAG, s);
        }
    }
    public class MeterCH2Buf      extends LegacyMeterStructure {
        public int buf_i = 0;
        public byte[] buf = new byte[1024];
        public float[] floatBuf = new float[256];
        public Runnable buf_full_cb;
        public MeterCH2Buf(PeripheralWrapper p) {super(p);}
        @Override
        public UUID getUUID() { return mUUID.METER_CH2BUF; }

        @Override
        public byte[] pack() {
            // SHOULD NEVER BE CALLED
            return null;
        }
        @Override
        public void unpackInner(byte[] arg) {
            // Nasty synchonization hack
            meter_ch1_buf.buf_i = 0;
            final int nBytes = getBufferDepth()*3;
            for(byte b : arg) {
                if(buf_i >= nBytes) {
                    Log.e(TAG,"CH2 OVERFLOW");
                    break;
                }
                buf[buf_i++] = b;
            }
            if(buf_i >= nBytes) {
                // Sample buffer is full
                Log.d(TAG, "CH2 full");
                if (buf_i > nBytes) {
                    return;
                }

                ByteBuffer bb = wrap(buf);
                for (int i = 0; i < getBufferDepth(); i++) {
                    floatBuf[i] = lsbToNativeUnits(getInt24(bb), Channel.CH2);
                }
                if (buf_full_cb != null) {
                    buf_full_cb.run();
                }
            }
            String s = "CH2 Progress: "+buf_i+" of "+nBytes;
            Log.i(TAG,s);
        }
    }
    public class MeterBat         extends LegacyMeterStructure {
        public float bat_v;
        public MeterBat(PeripheralWrapper p) {super(p);}
        @Override
        public UUID getUUID() { return mUUID.METER_BAT; }
        @Override
        public byte[] pack() {
            // SHOULD NEVER BE CALLED
            return null;
        }
        @Override
        public void unpackInner(byte[] in) {
            ByteBuffer b = wrap(in);
            float lsb = (float)b.getShort();
            // Compensate for Java not having unsigned...
            if(lsb<0){ lsb += 0x10000; }
            bat_v = 3.0f*1.24f*(lsb/(1<<12));
        }
    }

    public MeterSettings    meter_settings;
    public MeterLogSettings meter_log_settings;
    public MeterInfo        meter_info;
    public MeterSample      meter_sample;
    public MeterName        meter_name;
    public MeterCH1Buf      meter_ch1_buf;
    public MeterCH2Buf      meter_ch2_buf;
    public MeterTime        meter_time;
    public MeterBat         meter_bat;

    ///////////////////////////
    // Available range and input options
    ///////////////////////////

    protected abstract static class Lsb2NativeConverter {
        public abstract float convert(int lsb);
    }
    private static Lsb2NativeConverter dummy_converter = new Lsb2NativeConverter() {
        @Override
        public float convert(int lsb) {
            Log.e(TAG,"DUMMY CONVERTER CALLED!");
            return 0;
        }
    };
    private float lsb2PGAVoltage(int lsb) {
        final float Vref;
        switch(meter_info.pcb_version){
            case 7:
                Vref=(float)2.5;
                break;
            case 8:
                Vref=(float)2.42;
                break;
            default:
                Log.e(TAG,"UNSUPPORTED:Unknown board type");
                return 0;
        }
        return (float)((double)lsb/(double)(1<<23))*Vref;
    }
    float pgaGain(PGA_GAIN pga) {
        switch(pga){
            case PGA_GAIN_1:
                return 1;
            case PGA_GAIN_4:
                return 4;
            case PGA_GAIN_12:
                return 12;
            default:
                Log.e(TAG,"UNSUPPORTED:Unknown PGA value");
                return 0;
        }
    }
    private Lsb2NativeConverter generateSimpleConverter(final float mult,PGA_GAIN pga) {
        // "mult" should be the multiple to convert from voltage at the input to the ADC (after PGA)
        // to native units
        final float pga_mult = 1/pgaGain(pga);
        return new Lsb2NativeConverter() {
            @Override
            public float convert(int lsb) {
                return lsb2PGAVoltage(lsb)*pga_mult*mult;
            }
        };
    }
    private Lsb2NativeConverter generateResistiveBridgeConverter(PGA_GAIN pga,ISRC_SETTING isrc) {
        // "mult" should be the multiple to convert from voltage at the input to the ADC (after PGA)
        // to native units
        final float pga_mult = pgaGain(pga);
        final float isrc_res;
        final float ptc_res = (float)7.9;
        final float avdd=(float)(3-1.21);
        switch(isrc) {
            case ISRC_LOW:
                isrc_res = (float)10310e3;
                break;
            case ISRC_MID:
                isrc_res = (float)310e3;
                break;
            case ISRC_HIGH:
                isrc_res = (float)10e3;
                break;
            default:
                Log.e(TAG,"UNSUPPORTED:Unknown ISRC setting");
                isrc_res = 0;
        }
        if(meter_info.pcb_version!=8) {
            Log.e(TAG,"UNSUPPORTED:Only PCB RevI can use this function!");
        }
        return new Lsb2NativeConverter() {
            @Override
            public float convert(int lsb) {
                float adc_volts = lsb2PGAVoltage(lsb)/pga_mult;
                return ((adc_volts/(avdd-adc_volts))*isrc_res) - ptc_res;
            }
        };
    }
    public static class RangeDescriptor extends  MooshimeterDeviceBase.RangeDescriptor{
        byte chset=0;
        GPIO_SETTING gpio=GPIO_SETTING.IGNORE;
        ISRC_SETTING isrc=ISRC_SETTING.IGNORE;
        Lsb2NativeConverter converter = dummy_converter;
    }
    public class InputDescriptor extends  MooshimeterDeviceBase.InputDescriptor{
        INPUT_MODE input;
        boolean isAC;
        public void addRange(String name, float max, Lsb2NativeConverter converter, PGA_GAIN gain,GPIO_SETTING gpio, ISRC_SETTING isrc) {
            RangeDescriptor ret = new RangeDescriptor();
            ret.name=name;
            ret.max = max;
            ret.chset = 0;
            switch(input) {
                case NATIVE:
                    ret.chset = 0x00;
                    break;
                case TEMP:
                    ret.chset = 0x04;
                    break;
                case AUX_V:
                case RESISTANCE:
                case DIODE:
                    ret.chset = 0x09;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            switch(gain) {
                case PGA_GAIN_1:
                    ret.chset |= 0x10;
                    break;
                case PGA_GAIN_4:
                    ret.chset |= 0x40;
                    break;
                case PGA_GAIN_12:
                    ret.chset |= 0x60;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            ret.gpio = gpio;
            ret.isrc = isrc;
            ret.converter = converter;
            ranges.add(ret);
        }
        public void addRange(String name, float max, Lsb2NativeConverter converter, PGA_GAIN gain,ISRC_SETTING isrc) {
            addRange(name, max, converter, gain, GPIO_SETTING.IGNORE, isrc);
        }
        public void addRange(String name, float max, float adc2native, PGA_GAIN gain,GPIO_SETTING gpio,ISRC_SETTING isrc) {
            Lsb2NativeConverter converter = generateSimpleConverter(adc2native,gain);
            addRange(name,max,converter,gain,gpio,isrc);
        }
        public void addRange(String name, float max, float adc2native, PGA_GAIN gain) {
            addRange(name,max,adc2native,gain,GPIO_SETTING.IGNORE,ISRC_SETTING.IGNORE);
        }
        public void addRange(String name, float max, float adc2native, PGA_GAIN gain,GPIO_SETTING gpio) {
            addRange(name,max,adc2native,gain,gpio,ISRC_SETTING.IGNORE);
        }
        public void addRange(String name, float max, float adc2native, PGA_GAIN gain, ISRC_SETTING isrc) {
            addRange(name,max,adc2native,gain,GPIO_SETTING.IGNORE,isrc);
        }
    }
    public static abstract class MathInputDescriptor extends MooshimeterDeviceBase.InputDescriptor {
        public MathInputDescriptor(String name, String units) {super(name, units);}
        public abstract void onChosen();
        public abstract boolean meterSettingsAreValid();
        public abstract MeterReading calculate();
    }

    Map<Channel,Chooser<MooshimeterDeviceBase.InputDescriptor>> input_descriptors;

    private InputDescriptor addInputDescriptor(Channel channel, String name, INPUT_MODE in, boolean ac, String units) {
        InputDescriptor rval = new InputDescriptor();
        rval.name=name;
        rval.input = in;
        rval.isAC = ac;
        rval.units = units;
        input_descriptors.get(channel).add(rval);
        return rval;
    }

    public LegacyMooshimeterDevice(PeripheralWrapper wrap) {
        // Initialize super
        super(wrap);

        mInstance = this;

        // Initialize internal structures
        meter_name          = new MeterName       (mPwrap);
        meter_settings      = new MeterSettings   (mPwrap);
        meter_log_settings  = new MeterLogSettings(mPwrap);
        meter_info          = new MeterInfo       (mPwrap);
        meter_sample        = new MeterSample     (mPwrap);
        meter_ch1_buf       = new MeterCH1Buf     (mPwrap);
        meter_ch2_buf       = new MeterCH2Buf     (mPwrap);
        meter_time          = new MeterTime       (mPwrap);
        meter_bat           = new MeterBat        (mPwrap);

        offsets = new ConcurrentHashMap<>();
        offsets.put(Channel.CH1, (float) 0);
        offsets.put(Channel.CH2,(float)0);

        input_descriptors = new ConcurrentHashMap<>();
        input_descriptors.put(Channel.CH1, new Chooser<MooshimeterDeviceBase.InputDescriptor>());
        input_descriptors.put(Channel.CH2,new Chooser<MooshimeterDeviceBase.InputDescriptor>());
        input_descriptors.put(Channel.MATH, new Chooser<MooshimeterDeviceBase.InputDescriptor>());
    }

    private void addSharedInputs(Channel channel) {
        InputDescriptor h;
        h=addInputDescriptor(channel,"AUX VOLTAGE DC",INPUT_MODE.AUX_V,false,"V");
        h.addRange("100mV",(float)0.1,1,PGA_GAIN.PGA_GAIN_12  ,ISRC_SETTING.ISRC_OFF);
        h.addRange("300mV",(float)0.3,1,PGA_GAIN.PGA_GAIN_4   ,ISRC_SETTING.ISRC_OFF);
        h.addRange("1.2V", (float) 1.2, 1, PGA_GAIN.PGA_GAIN_1,ISRC_SETTING.ISRC_OFF);
        h=addInputDescriptor(channel,"AUX VOLTAGE AC",INPUT_MODE.AUX_V,true,"V");
        h.addRange("100mV",(float)0.1,1,PGA_GAIN.PGA_GAIN_12,ISRC_SETTING.ISRC_OFF);
        h.addRange("300mV",(float)0.3,1,PGA_GAIN.PGA_GAIN_4 ,ISRC_SETTING.ISRC_OFF);
        h.addRange("1.2V", (float)1.2,1,PGA_GAIN.PGA_GAIN_1 ,ISRC_SETTING.ISRC_OFF);
        h=addInputDescriptor(channel,"RESISTANCE",INPUT_MODE.AUX_V,false,"\u03A9");
        switch (meter_info.pcb_version){
            case 7:
                h.addRange("1k\u03a9"  ,(float)1e3,(float)(1/100e-6),PGA_GAIN.PGA_GAIN_12,ISRC_SETTING.ISRC_HIGH);
                h.addRange("10k\u03a9" ,(float)1e4,(float)(1/100e-6),PGA_GAIN.PGA_GAIN_1 ,ISRC_SETTING.ISRC_HIGH);
                h.addRange("100k\u03a9",(float)1e5,(float)(1/100e-9),PGA_GAIN.PGA_GAIN_12,ISRC_SETTING.ISRC_LOW);
                h.addRange("1M\u03a9"  ,(float)1e6,(float)(1/100e-9),PGA_GAIN.PGA_GAIN_12,ISRC_SETTING.ISRC_LOW);
                h.addRange("10M\u03a9" ,(float)1e7,(float)(1/100e-9),PGA_GAIN.PGA_GAIN_1 ,ISRC_SETTING.ISRC_LOW);
            case 8:
                // FIXME: I don't like that I have to repeat myself in the arguments below...
                h.addRange("1k\u03a9"  ,(float)1e3,generateResistiveBridgeConverter(PGA_GAIN.PGA_GAIN_12,ISRC_SETTING.ISRC_HIGH),PGA_GAIN.PGA_GAIN_12,ISRC_SETTING.ISRC_HIGH);
                h.addRange("10k\u03a9" ,(float)1e4,generateResistiveBridgeConverter(PGA_GAIN.PGA_GAIN_1 ,ISRC_SETTING.ISRC_HIGH),PGA_GAIN.PGA_GAIN_1 ,ISRC_SETTING.ISRC_HIGH);
                h.addRange("100k\u03a9",(float)1e5,generateResistiveBridgeConverter(PGA_GAIN.PGA_GAIN_4 ,ISRC_SETTING.ISRC_MID) ,PGA_GAIN.PGA_GAIN_4 ,ISRC_SETTING.ISRC_MID);
                h.addRange("1M\u03a9"  ,(float)1e6,generateResistiveBridgeConverter(PGA_GAIN.PGA_GAIN_12,ISRC_SETTING.ISRC_LOW) ,PGA_GAIN.PGA_GAIN_12,ISRC_SETTING.ISRC_LOW);
                h.addRange("10M\u03a9" ,(float)1e7,generateResistiveBridgeConverter(PGA_GAIN.PGA_GAIN_1 ,ISRC_SETTING.ISRC_LOW) ,PGA_GAIN.PGA_GAIN_1 ,ISRC_SETTING.ISRC_LOW);
                break;
            default:
                Log.e(TAG,"UNSUPPORTED:Unrecognized pcb version!");
        }
        h=addInputDescriptor(channel,"DIODE DROP",INPUT_MODE.DIODE,false,"V");
        Lsb2NativeConverter beepy_converter = new Lsb2NativeConverter() {
            @Override
            public float convert(int lsb) {
                float volts = lsb2PGAVoltage(lsb);
                // PGA gain is 1, so PGA voltage=ADC voltage
                if(volts < 0.1) {
                    Alerter.alert();
                } else {
                    Alerter.stopAlerting();
                }
                return volts;
            }
        };
        h.addRange("1.7V",1.7f,beepy_converter,PGA_GAIN.PGA_GAIN_1,GPIO_SETTING.IGNORE,ISRC_SETTING.ISRC_HIGH);

        h=addInputDescriptor(channel,"INTERNAL TEMP",INPUT_MODE.TEMP,false,"K");
        Lsb2NativeConverter temperature_converter = new Lsb2NativeConverter() {
            @Override
            public float convert(int lsb) {
                float volts = lsb2PGAVoltage(lsb);
                // PGA gain is 1, so PGA voltage=ADC voltage
                return (float)(volts/490e-6);
            }
        };
        h.addRange("350K",350,temperature_converter,PGA_GAIN.PGA_GAIN_1,GPIO_SETTING.IGNORE,ISRC_SETTING.IGNORE);
    }

    private Runnable bat_poller = new Runnable() {
        @Override
        public void run() {
            if(!isConnected()) {
                return;
            }
            meter_bat.update();
            delegate.onBatteryVoltageReceived(meter_bat.bat_v);
            Util.postDelayed(bat_poller, 10000);
        }
    };

    public int initialize() {
        super.initialize();
        if(isInOADMode()) {
            throw new RuntimeException("LegacyMooshimeterDevice should not be connected to an OAD!");
        }
        // Grab the initial settings
        meter_settings.update();
        meter_settings.target_meter_state = meter_settings.present_meter_state;
        meter_log_settings.update();
        meter_info.update();
        meter_name.update();

        // Automatically sync the meter's clock to the phone clock
        setTime(Util.getUTCTime());

        input_descriptors.get(Channel.CH1).clear();
        input_descriptors.get(Channel.CH2).clear();
        input_descriptors.get(Channel.MATH).clear();

        InputDescriptor h;
        // Add channel 1 ranges and inputs
        float i_gain;
        switch(meter_info.pcb_version){
            case 7:
                i_gain = (float)(1/80e-3);
                break;
            case 8:
                i_gain = (float)(1/10e-3);
                break;
            default:
                Log.e(TAG,"UNSUPPORTED:Unknown board type");
                i_gain = 0;
        }
        h=addInputDescriptor(Channel.CH1,"CURRENT DC",INPUT_MODE.NATIVE,false,"A");
        h.addRange("10A",10,i_gain,PGA_GAIN.PGA_GAIN_1);
        h=addInputDescriptor(Channel.CH1,"CURRENT AC",INPUT_MODE.NATIVE, true, "A");
        h.addRange("10A", 10, i_gain, PGA_GAIN.PGA_GAIN_1);
        addSharedInputs(Channel.CH1);

        // Add channel 2 ranges and inputs
        h=addInputDescriptor(Channel.CH2,"VOLTAGE DC",INPUT_MODE.NATIVE,false,"V");
        h.addRange("60V", 60, (float) ((10e6 + 160e3) / 160e3), PGA_GAIN.PGA_GAIN_1, GPIO_SETTING.GPIO1);
        h.addRange("600V", 600, (float) ((10e6 + 11e3) / 11e3), PGA_GAIN.PGA_GAIN_1, GPIO_SETTING.GPIO2);
        h=addInputDescriptor(Channel.CH2,"VOLTAGE AC",INPUT_MODE.NATIVE,true,"V");
        h.addRange("60V", 60, (float) ((10e6 + 160e3) / 160e3), PGA_GAIN.PGA_GAIN_1, GPIO_SETTING.GPIO1);
        h.addRange("600V", 600, (float) ((10e6 + 11e3) / 11e3), PGA_GAIN.PGA_GAIN_1, GPIO_SETTING.GPIO2);
        addSharedInputs(Channel.CH2);

        // On the math channel, input mode, AC and units are ignored
        Chooser<MooshimeterDeviceBase.InputDescriptor> l = input_descriptors.get(Channel.MATH);
        MathInputDescriptor mid = new MathInputDescriptor("APPARENT POWER","W") {
            @Override
            public void onChosen() {}
            @Override
            public boolean meterSettingsAreValid() {
                InputDescriptor id0 = (InputDescriptor)getSelectedDescriptor(Channel.CH1);
                InputDescriptor id1 = (InputDescriptor)getSelectedDescriptor(Channel.CH2);
                boolean valid = true;
                valid &= id0.units.equals("A");
                valid &= id1.units.equals("V");
                return valid;
            }
            @Override
            public MeterReading calculate() {
                return MeterReading.mult(getValue(Channel.CH1),getValue(Channel.CH2));
            }
        };
        l.add(mid);
        // Find the inputs we need to set and bind them
        h=null;
        for(InputDescriptor id:(List<InputDescriptor>)(List<?>)getInputList(Channel.CH1)) {
            if(id.input==INPUT_MODE.AUX_V) {
                h=id;
                break;
            }
        }
        final InputDescriptor auxv_id=h;
        for(InputDescriptor id:(List<InputDescriptor>)(List<?>)getInputList(Channel.CH2)) {
            if(id.input==INPUT_MODE.TEMP) {
                h=id;
                break;
            }
        }
        final InputDescriptor temp_id=h;
        mid = new MathInputDescriptor("THERMOCOUPLE K","C") {
            @Override
            public void onChosen() {
                setInput(Channel.CH1,auxv_id);
                setInput(Channel.CH2,temp_id);
            }
            @Override
            public boolean meterSettingsAreValid() {
                InputDescriptor id0 = (InputDescriptor)getSelectedDescriptor(Channel.CH1);
                InputDescriptor id1 = (InputDescriptor)getSelectedDescriptor(Channel.CH2);
                boolean valid = true;
                valid &= id0 == auxv_id;
                valid &= id1 == temp_id;
                return valid;
            }
            @Override
            public MeterReading calculate() {
                float volts = getValue(Channel.CH1).value;
                float delta = (float) ThermocoupleHelper.K.voltsToDegC(volts);
                float internal_temp = getValue(Channel.CH2).value;
                MeterReading rval;
                if(Util.getPreferenceBoolean(Util.preference_keys.USE_FAHRENHEIT)) {
                    rval = new MeterReading(internal_temp+Util.TemperatureUnitsHelper.relK2F(delta), 5, 2000, "F");
                } else {
                    rval = new MeterReading(internal_temp+delta,5,1000,"C");
                }
                return rval;
            }
        };
        l.add(mid);

        Util.cancelDelayedCB(log_status_checker);
        Util.postDelayed(log_status_checker, 5000);

        Util.cancelDelayedCB(bat_poller);
        Util.postDelayed(bat_poller,1000);

        determineInputDescriptorIndex(Channel.CH1);
        determineInputDescriptorIndex(Channel.CH2);

        mInitialized = true;
        return 0;
    }

    void determineInputDescriptorIndex(Channel c) {
        GPIO_SETTING gs = GPIO_SETTING.IGNORE;
        ISRC_SETTING is = ISRC_SETTING.IGNORE;
        switch(meter_settings.chset[c.ordinal()]&METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                if(c==Channel.CH2) {
                    switch(meter_settings.adc_settings&ADC_SETTINGS_GPIO_MASK) {
                        case 0x00:
                            gs=GPIO_SETTING.GPIO0;
                            break;
                        case 0x10:
                            gs=GPIO_SETTING.GPIO1;
                            break;
                        case 0x20:
                            gs=GPIO_SETTING.GPIO2;
                            break;
                        case 0x30:
                            gs=GPIO_SETTING.GPIO3;
                            break;
                    }
                }
                break;
            case 0x04:
                break;
            case 0x09:
                switch(meter_settings.adc_settings) {
                    case 0:
                        break;
                    case METER_MEASURE_SETTINGS_ISRC_ON:
                        is=ISRC_SETTING.ISRC_LOW;
                        break;
                    case METER_MEASURE_SETTINGS_ISRC_LVL:
                        is=ISRC_SETTING.ISRC_MID;
                        break;
                    case METER_MEASURE_SETTINGS_ISRC_ON|METER_MEASURE_SETTINGS_ISRC_LVL:
                        is=ISRC_SETTING.ISRC_HIGH;
                        break;
                }
                break;
        }
        boolean found=false;
        for(InputDescriptor id:(List<InputDescriptor>)(List<?>)input_descriptors.get(c).getChoices()) {
            for(MooshimeterDeviceBase.RangeDescriptor uncast_rd:id.ranges.getChoices()) {
                RangeDescriptor rd = (RangeDescriptor)uncast_rd;
                if(     rd.chset   == meter_settings.chset[c.ordinal()]
                        && rd.gpio == gs
                        && rd.isrc == is) {
                    input_descriptors.get(c).choose(id);
                    id.ranges.choose(uncast_rd);
                    found=true;
                    break;
                }
            }
            if(found) { break; }
        }
    }

    //////////////////////////////////////
    // Notification callbacks
    //////////////////////////////////////

    private NotifyHandler meter_sample_handler = new NotifyHandler() {
        @Override
        public void onReceived(double timestamp_utc, Object payload) {
            MeterReading r1,r2,r3;
            r1 = getValue(Channel.CH1);
            r2 = getValue(Channel.CH2);
            r3 = getValue(Channel.MATH);
            delegate.onSampleReceived(timestamp_utc, Channel.CH1, r1);
            delegate.onSampleReceived(timestamp_utc, Channel.CH2, r2);
            delegate.onSampleReceived(timestamp_utc, Channel.MATH, r3);
            if(Util.getPreferenceBoolean(Util.preference_keys.BROADCAST_INTENTS)) {
                BroadcastIntentData.broadcastMeterReading(r1,"CH1");
                BroadcastIntentData.broadcastMeterReading(r2,"CH2");
                BroadcastIntentData.broadcastMeterReading(r3,"MATH");
            }
        }
    };

    private Runnable meter_buffer_handler = new Runnable() {
        @Override
        public void run() {
            float dt = 1/(float)getSampleRateHz();
            double timestamp = Util.getUTCTime();
            delegate.onBufferReceived(timestamp, Channel.CH1, dt, Arrays.copyOfRange(meter_ch1_buf.floatBuf, 0, getBufferDepth()));
            delegate.onBufferReceived(timestamp, Channel.CH2, dt, Arrays.copyOfRange(meter_ch2_buf.floatBuf, 0, getBufferDepth()));
        }
    };

    private Runnable log_status_checker = new Runnable() {
        @Override
        public void run() {
            if(!isConnected()) {
                return;
            }
            meter_log_settings.update();
            delegate.onLoggingStatusChanged(getLoggingOn(), getLoggingStatus(), getLoggingStatusMessage());
            Util.postDelayed(log_status_checker, 5000);
        }
    };

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
    public static final int METER_CALC_SETTINGS_RES        = 0x80;

    public static final int ADC_SETTINGS_SAMPLERATE_MASK = 0x07;
    public static final int ADC_SETTINGS_GPIO_MASK = 0x30;

    public static final int METER_CH_SETTINGS_PGA_MASK = 0x70;
    public static final int METER_CH_SETTINGS_INPUT_MASK = 0x0F;

    /**
     * Examines the measurement settings for the given channel and returns the effective number of bits
     * @param channel The channel index (0 or 1)
     * @return Effective number of bits
     */

    protected float getEnob(final Channel channel) {
        // Return a rough appoximation of the ENOB of the channel
        // For the purposes of figuring out how many digits to display
        // Based on ADS1292 datasheet and some special sauce.
        // And empirical measurement of CH1 (which is super noisy due to chopper)
        final float[] base_enob_table = {
                20.10f,
                19.58f,
                19.11f,
                18.49f,
                17.36f,
                14.91f,
                12.53f};
        final int[] pga_gain_table = {6,1,2,3,4,8,12};
        final int samplerate_setting =meter_settings.adc_settings & ADC_SETTINGS_SAMPLERATE_MASK;
        final int buffer_depth_log2 = meter_settings.calc_settings & METER_CALC_SETTINGS_DEPTH_LOG2;
        float enob = base_enob_table[ samplerate_setting ];
        int pga_setting = meter_settings.chset[channel.ordinal()];
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
        if(meter_info.pcb_version==7 && channel == Channel.CH1 && (meter_settings.chset[0] & METER_CH_SETTINGS_INPUT_MASK) == 0 ) {
            // This is compensation for a bug in RevH, where current sense chopper noise dominates
            enob -= 2;
        }
        return enob;
    }

    @Override
    public MooshimeterDeviceBase.InputDescriptor getSelectedDescriptor(Channel c) {
        return input_descriptors.get(c).getChosen();
    }

    @Override
    public void pollLogInfo() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void downloadLog(LogFile log) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancelLogDownload() {
        throw new UnsupportedOperationException();
    }

    @Override
    public LogFile getLogInfo(int index) {
        return null;
    }

    @Override
    public void setLogOffset(int offset) {
        throw new UnsupportedOperationException();
    }


    private RangeDescriptor getRangeDescriptorForChannel(Channel c) {
        return (RangeDescriptor) getSelectedDescriptor(c).ranges.getChosen();
    }

    /**
     * Converts an ADC reading to the reading at the terminal input
     * @param lsb   Input reading in LSB
     * @param ch    Channel index (0 or 1)
     * @return      Value at the input terminal.  Depending on measurement settings, can be V, A or Ohms
     */

    public float lsbToNativeUnits(int lsb, final Channel ch) {
        return getRangeDescriptorForChannel(ch).converter.convert(lsb);
    }

    public float lsbToNativeUnits(float lsb, final Channel ch) {
        return lsbToNativeUnits((int) lsb, ch);
    }

    /**
     *
     * @param channel The channel index (0 or 1)
     * @return A string describing what the channel is measuring
     */
    public String getDescriptor(final Channel channel) {
        return getSelectedDescriptor(channel).name;
    }

    /**
     *
     * @param channel The channel index (0 or 1)
     * @return        A String containing the input label of the channel (V, A, Omega or Internal)
     */

    public String getInputLabel(final Channel channel) {
        return getDescriptor(channel);
    }

    @Override
    public int setInput(Channel c, MooshimeterDeviceBase.InputDescriptor descriptor) {
        Chooser<MooshimeterDeviceBase.InputDescriptor> chooser = input_descriptors.get(c);
        switch(c) {
            case CH1:
            case CH2:
                InputDescriptor cast = (InputDescriptor)descriptor;
                if(input_descriptors.get(c).isChosen(cast)) {
                    // No action required
                    return 0;
                }

                if(isSharedInput(cast)) {
                    // Make sure we're not about to jump on to a channel that's in use
                    Channel other = c==Channel.CH1?Channel.CH2:Channel.CH1;
                    InputDescriptor other_id = (InputDescriptor)input_descriptors.get(other).getChosen();
                    if(isSharedInput(other_id)) {
                        Log.e(TAG, "Tried to select an input already in use!");
                        return -1;
                    }
                }

                chooser.choose(cast);
                delegate.onInputChange(c, descriptor);
                setRange(c, cast.ranges.get(0));
                return 0;
            case MATH:
                MathInputDescriptor mcast = (MathInputDescriptor)descriptor;
                mcast.onChosen();
                chooser.choose(mcast);
                return 0;
        }
        return 0;
    }
    private static boolean isSharedInput(InputDescriptor id) {
        return       id.input == INPUT_MODE.AUX_V
                ||   id.input == INPUT_MODE.RESISTANCE
                ||   id.input == INPUT_MODE.DIODE;
    }
    @Override
    public List<MooshimeterDeviceBase.InputDescriptor> getInputList(Channel c) {
        if(c==Channel.MATH) {
            return input_descriptors.get(c).getChoices();
        }
        Channel other = c==Channel.CH1?Channel.CH2:Channel.CH1;
        InputDescriptor other_id = (InputDescriptor)getSelectedDescriptor(other);
        if( isSharedInput(other_id)) {
            ArrayList<InputDescriptor> rval = new ArrayList(input_descriptors.get(c).getChoices());
            for (Iterator<InputDescriptor> iterator = rval.iterator(); iterator.hasNext();) {
                InputDescriptor descriptor = iterator.next();
                if(isSharedInput(descriptor)) {
                    iterator.remove();
                }
            }
            return (List<MooshimeterDeviceBase.InputDescriptor>)(List<?>)rval;
        }
        return input_descriptors.get(c).getChoices();
    }

    ////////////////////////
    // MooshimeterControlInterface methods
    ////////////////////////

    /**
     * Changes the measurement settings for a channel to expand or contract the measurement range
     * @param c   The channel index (0 or 1)
     * @param expand    Expand (true) or contract (false) the range.
     */
    @Override
    public boolean bumpRange(Channel c, boolean expand) {
        InputDescriptor id = (InputDescriptor)input_descriptors.get(c).getChosen();
        Chooser<MooshimeterDeviceBase.RangeDescriptor> range_chooser = id.ranges;
        int choice_i = range_chooser.getChosenI();
        int n_choices = getSelectedDescriptor(c).ranges.size();
        // If we're not wrapping and we're against a wall
        if (choice_i == 0 && !expand) {
            return false;
        }
        if(choice_i == n_choices-1 && expand) {
            return false;
        }
        choice_i += expand?1:-1;
        choice_i %= n_choices;
        setRange(c, range_chooser.chooseIndex(choice_i));
        return true;
    }

    private float getMinRangeForChannel(Channel c) {
        return (float)0.9 * getSelectedDescriptor(c).ranges.getChoiceBelow().max;
    }
    protected float getMaxRangeForChannel(Channel c) {
        return (float)1.1*getRangeDescriptorForChannel(c).max;
    }
    private boolean applyAutorange(Channel c) {
        if(!getRangeAuto(c)) {
            return false;
        }
        float max = getMaxRangeForChannel(c);
        float min = getMinRangeForChannel(c);
        float val = getValue(c).value + getOffset(c).value;
        val = Math.abs(val);
        if(val > max) {
            return bumpRange(c,true);
        }
        if(val < min) {
            return bumpRange(c,false);
        }
        return false;
    }

    private boolean applyRateAndDepthRange() {
        InputDescriptor id0 = (InputDescriptor)getSelectedDescriptor(Channel.CH1);
        InputDescriptor id1 = (InputDescriptor)getSelectedDescriptor(Channel.CH2);
        final boolean ac_used = id0.isAC|| id1.isAC;
        byte adc_stash = meter_settings.adc_settings;
        byte calc_stash = meter_settings.calc_settings;
        // Autorange sample rate and buffer depth.
        // If anything is doing AC, we need a deep buffer and fast sample
        if(getRateAuto()) {
            meter_settings.adc_settings &= ~ADC_SETTINGS_SAMPLERATE_MASK;
            if(ac_used) {meter_settings.adc_settings |= 5;} // 4kHz
            else        {meter_settings.adc_settings |= 0;} // 125Hz
        }
        if(getDepthAuto()) {
            meter_settings.calc_settings &=~METER_CALC_SETTINGS_DEPTH_LOG2;
            if(ac_used) {meter_settings.calc_settings |= 8;} // 256 samples
            else        {meter_settings.calc_settings |= 5;} // 32 samples
        }
        boolean rate_change  = adc_stash!=meter_settings.adc_settings;
        boolean depth_change = calc_stash!=meter_settings.calc_settings;
        if(rate_change){
            // FIXME: This -1 is supposed to be the index of the sample rate within the sample rate options
            // but I couldn't be bothered... will probably deprecate
            delegate.onSampleRateChanged(-1,getSampleRateHz());
        }
        if(depth_change) {
            // FIXME: This -1 is supposed to be the index of the sample rate within the sample rate options
            // but I couldn't be bothered... will probably deprecate
            delegate.onBufferDepthChanged(-1,getBufferDepth());
        }
        return rate_change||depth_change;
    }

    @Override
    public boolean applyAutorange() {
        boolean rval = false;
        rval |= applyRateAndDepthRange();
        rval |= applyAutorange(Channel.CH1);
        rval |= applyAutorange(Channel.CH2);
        return rval;
    }

    @Override
    public void setName(String name) {
        meter_name.name = name;
        meter_name.send();
    }

    @Override
    public String getName() {
        return meter_name.name;
    }

    @Override
    public void pause() {
        meter_sample.enableNotify(false, null);
        if(meter_settings.target_meter_state != METER_PAUSED) {
            meter_settings.target_meter_state = METER_PAUSED;
            meter_settings.send();
        }
    }

    @Override
    public void oneShot() {
        meter_settings.calc_settings |= LegacyMooshimeterDevice.METER_CALC_SETTINGS_MEAN | LegacyMooshimeterDevice.METER_CALC_SETTINGS_MS | LegacyMooshimeterDevice.METER_CALC_SETTINGS_ONESHOT;
        meter_settings.target_meter_state = LegacyMooshimeterDevice.METER_RUNNING;
        meter_sample.enableNotify(true, meter_sample_handler);
        meter_settings.send();
    }

    @Override
    public void stream() {
        meter_settings.calc_settings |= LegacyMooshimeterDevice.METER_CALC_SETTINGS_MEAN | LegacyMooshimeterDevice.METER_CALC_SETTINGS_MS;
        meter_settings.calc_settings &=~LegacyMooshimeterDevice.METER_CALC_SETTINGS_ONESHOT;
        meter_settings.target_meter_state = LegacyMooshimeterDevice.METER_RUNNING;
        meter_sample.enableNotify(true, meter_sample_handler);
        meter_settings.send();
    }

    @Override
    public void reboot() {
        meter_settings.target_meter_state = METER_SHUTDOWN;
        meter_settings.send();
    }
    @Override
    public void enterShippingMode() {
        meter_settings.target_meter_state = METER_HIBERNATE;
        meter_settings.send();
    }
    @Override
    public double getUTCTime() {
        return meter_time.utc_time;
    }
    @Override
    public void setTime(double utc_time) {
        meter_time.utc_time = (long)utc_time;
        meter_time.send();
    }
    @Override
    public MeterReading getOffset(Channel c) {
        return wrapMeterReading(c, offsets.get(c),true);
    }

    @Override
    public void setOffset(Channel c, float offset) {
        offsets.put(c,offset);
        delegate.onOffsetChange(c,wrapMeterReading(c,offset));
    }

    @Override
    public int getSampleRateHz() {
        final int[] samplerates = new int[]{125,250,500,1000,2000,4000,8000};
        final int samplerate_setting =meter_settings.adc_settings & ADC_SETTINGS_SAMPLERATE_MASK;
        return samplerates[samplerate_setting];
    }

    @Override
    public int setSampleRateIndex(int i) {
        meter_settings.adc_settings &=~ADC_SETTINGS_SAMPLERATE_MASK;
        meter_settings.adc_settings |= i;
        meter_settings.send();
        return 0;
    }

    @Override
    public List<String> getSampleRateList() {
        final int[] samplerates = new int[]{125,250,500,1000,2000,4000,8000};
        List<String> rval = new ArrayList<>(samplerates.length);
        for(int i:samplerates) {
            rval.add(Integer.toString(i));
        }
        return rval;
    }

    @Override
    public int getBufferDepth() {
        int depth_log2 = meter_settings.calc_settings & METER_CALC_SETTINGS_DEPTH_LOG2;
        return (1<<depth_log2);
    }

    @Override
    public int setBufferDepthIndex(int i) {
        meter_settings.calc_settings&=~METER_CALC_SETTINGS_DEPTH_LOG2;
        meter_settings.calc_settings|=i+5;
        meter_settings.send();
        return 0;
    }

    @Override
    public List<String> getBufferDepthList() {
        final int[] depths = new int[]{32,64,128,256};
        List<String> rval = new ArrayList<>(depths.length);
        for(int i:depths) {
            rval.add(Integer.toString(i));
        }
        return rval;
    }

    @Override
    public void setBufferMode(Channel c, boolean on) {
        /**
         * Downloads the complete sample buffer from the Mooshimeter.
         * This interaction spans many connection intervals, the exact length depends on the number of samples in the buffer
         * @param onReceived Called when the complete buffer has been downloaded
         */
        if(on) {
            // Set up for oneshot, turn off all math in firmware
            if ((mBuildTime < 1424473383)
                    && (0 == (meter_settings.calc_settings & METER_CALC_SETTINGS_ONESHOT))) {
                // Avoid sending the same meter settings over and over - check and see if we're set up for oneshot
                // and if we are, don't send the meter settings again.  Due to a firmware bug in the wild (Feb 2 2015)
                // sending meter settings will cause the ADC to run for one buffer fill even if the state is METER_PAUSED
                meter_settings.calc_settings &= ~(METER_CALC_SETTINGS_MS | METER_CALC_SETTINGS_MEAN);
                meter_settings.calc_settings |= METER_CALC_SETTINGS_ONESHOT;
                //meter_settings.target_meter_state = METER_PAUSED;
                //meter_settings.send();
            } else if (meter_settings.present_meter_state != METER_PAUSED) {
                //meter_settings.target_meter_state = METER_PAUSED;
                //meter_settings.send();
            }

            meter_sample.enableNotify(false, null);
            meter_ch1_buf.enableNotify(true, null);
            meter_ch2_buf.enableNotify(true, null);
            meter_ch2_buf.buf_full_cb = meter_buffer_handler;

            meter_ch1_buf.buf_i = 0;
            meter_ch2_buf.buf_i = 0;
        } else {
            meter_sample.enableNotify(true, meter_sample_handler);
            meter_ch1_buf.enableNotify(false, null);
            meter_ch2_buf.enableNotify(false, null);
        }
    }

    @Override
    public boolean getLoggingOn() {
        boolean rval = meter_log_settings.present_logging_state != LOGGING_OFF;
        rval &= meter_log_settings.target_logging_state != LOGGING_OFF;
        return rval;
    }

    @Override
    public void setLoggingOn(boolean on) {
        meter_log_settings.target_logging_state = on?LOGGING_SAMPLING:LOGGING_OFF;
        meter_log_settings.send();
        meter_log_settings.update();
    }

    @Override
    public int getLoggingStatus() {
        return meter_log_settings.logging_error;
    }

    private enum LogCodes {
        OK,
        NO_MEDIA,
        MOUNT_FAIL,
        INSUFFICIENT_SPACE,
        WRITE_ERROR,
        END_OF_FILE,
    };
    @Override
    public String getLoggingStatusMessage() {
        return LogCodes.values()[meter_log_settings.logging_error].toString();
    }

    @Override
    public void setLoggingInterval(int ms) {
        meter_log_settings.logging_period_ms = (short)ms;
        meter_log_settings.send();
    }

    @Override
    public int getLoggingIntervalMS() {
        return meter_log_settings.logging_period_ms;
    }

    private static MeterReading invalid_inputs = new MeterReading(0,0,0,"INVALID INPUTS");

    @Override
    public MeterReading getValue(Channel c) {
        switch(c) {
            case CH1:
            case CH2:
                if(((InputDescriptor)getSelectedDescriptor(c)).isAC) {
                    return wrapMeterReading(c,lsbToNativeUnits((float) Math.sqrt(meter_sample.reading_ms[c.ordinal()]), c));
                } else {
                    return wrapMeterReading(c,lsbToNativeUnits(meter_sample.reading_lsb[c.ordinal()], c) + getOffset(c).value);
                }
            case MATH:
                MathInputDescriptor id = (MathInputDescriptor)input_descriptors.get(Channel.MATH).getChosen();
                if(id.meterSettingsAreValid()) {
                    return id.calculate();
                } else {
                    return invalid_inputs;
                }
        }
        return new MeterReading();
    }

    @Override
    public String getRangeLabel(Channel c) {
        return getRangeDescriptorForChannel(c).name;
    }

    @Override
    public int setRange(Channel c, MooshimeterDeviceBase.RangeDescriptor uncast) {
        RangeDescriptor rd = (RangeDescriptor)uncast;
        getSelectedDescriptor(c).ranges.choose(rd);
        meter_settings.chset[c.ordinal()] = rd.chset;
        applyRateAndDepthRange();
        switch(rd.isrc) {
            case IGNORE:
                break;
            case ISRC_OFF:
                meter_settings.measure_settings=0;
                break;
            case ISRC_LOW:
                meter_settings.measure_settings=METER_MEASURE_SETTINGS_ISRC_ON;
                break;
            case ISRC_MID:
                meter_settings.measure_settings=METER_MEASURE_SETTINGS_ISRC_LVL;
                break;
            case ISRC_HIGH:
                meter_settings.measure_settings=METER_MEASURE_SETTINGS_ISRC_ON|METER_MEASURE_SETTINGS_ISRC_LVL;
                break;
            default:
                throw new UnsupportedOperationException();
        }
        switch(rd.gpio){
            case IGNORE:
                break;
            case GPIO0:
                meter_settings.adc_settings&=~ADC_SETTINGS_GPIO_MASK;
                break;
            case GPIO1:
                meter_settings.adc_settings&=~ADC_SETTINGS_GPIO_MASK;
                meter_settings.adc_settings |= 0x10;
                break;
            case GPIO2:
                meter_settings.adc_settings&=~ADC_SETTINGS_GPIO_MASK;
                meter_settings.adc_settings |= 0x20;
                break;
            case GPIO3:
                meter_settings.adc_settings&=~ADC_SETTINGS_GPIO_MASK;
                meter_settings.adc_settings |= 0x30;
                break;
            default:
                throw new UnsupportedOperationException();
        }
        meter_settings.send();
        delegate.onRangeChange(c,rd);
        return 0;
    }

    @Override
    public List<String> getRangeList(Channel c) {
        return input_descriptors.get(c).getChosen().ranges.getChoiceNames();
    }
    @Override
    public int getPCBVersion() {
        return meter_info.pcb_version;
    }
}
