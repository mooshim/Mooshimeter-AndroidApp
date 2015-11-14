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

package com.mooshim.mooshimeter.common;


import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import static java.util.UUID.fromString;

public class MooshimeterDevice extends PeripheralWrapper {

    /*
    mUUID stores the UUID values of all the Mooshimeter fields.
    Note that the OAD fields are only accessible when connected to the Mooshimeter in OAD mode
    and the METER_ fields are only accessible when connected in meter mode.
     */

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
                OAD_IMAGE_IDENTIFY  = fromString("1BC5FFC1-0200-62AB-E411-F254E005DBD4"),
                OAD_IMAGE_BLOCK     = fromString("1BC5FFC2-0200-62AB-E411-F254E005DBD4"),
                OAD_REBOOT          = fromString("1BC5FFC3-0200-62AB-E411-F254E005DBD4");
    }

    private static final String TAG="MooshimeterDevice";

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

    public enum CH3_MODES {
        VOLTAGE,
        RESISTANCE,
        DIODE
    }

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

    private static ByteBuffer wrap(byte[] in) {
        // Generates a little endian byte buffer wrapping the byte[]
        ByteBuffer b = ByteBuffer.wrap(in);
        b.order(ByteOrder.LITTLE_ENDIAN);
        return b;
    }

    /*
    All fields in the Mooshimeter BLE profile are stored as structs
    MeterStructure provides a common framework to access them.
     */

    private abstract class MeterStructure {
        /**
         * Requests a read of the structure from the Mooshimeter, unpacks the response in to the
         * member variables.  Unpacking and packing are implemented by the subclass.
         * This function is asynchronous - the function returns before the structure is updated -
         * you must wait for cb to be called.
         */
        public void update() {
            try {
                unpack(mInstance.req(getUUID()));
            }
            catch(BufferUnderflowException e){
                Log.e(TAG,"Received incorrect pack length while unpacking!");
                Log.e(TAG, Log.getStackTraceString(new Exception()));
            }
        }
        /**
         * Sends the struct to the Mooshimeter, unpacks the response in to the
         * member variables.  Unpacking and packing are implemented by the subclass.
         * This function is asynchronous - the function returns before the structure is updated -
         * you must wait for cb to be called.
         */
        public int send() {
            return mInstance.send(getUUID(),pack());
        }

        /**
         * Enable or disable notifications on this field and set the callbacks.
         * @param enable        If true, enable the notification.  If false, disable.
         * @param on_notify     When a notify event is received, this is called.
         */
        public int enableNotify(boolean enable, final Runnable on_notify) {
            return mInstance.enableNotify(getUUID(),enable,new NotifyCallback() {
                @Override
                public void run() {
                    boolean success = true;
                    try {
                        unpack(this.payload);
                    }
                    catch(BufferUnderflowException e){
                        success = false;
                        Log.e(TAG,"Received incorrect pack length while unpacking!");
                        Log.e(TAG, Log.getStackTraceString(new Exception()));
                    }
                    finally {
                        if(success && on_notify!=null) {
                            on_notify.run();
                        }
                    }
                }
            });
        }
        public void setWriteType(int wtype){
            mInstance.setWriteType(getUUID(),wtype);
        }

        /**
         * Serialize the instance members
         * @return  A byte[] suitable for transmission as a BLE payload
         */
        public abstract byte[] pack();

        /**
         * Interpret a BLE payload and set the instance members
         * @param in A byte[] received as a BLE payload
         */
        public abstract void unpack(byte[] in);

        /**
         *
         * @return The UUID of this structure
         */
        public abstract UUID getUUID();
    }

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
        public void unpack(byte[] in) {
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
            ByteBuffer b = wrap(new byte[16]);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.put(      sd_present );
            b.put(      present_logging_state );
            b.put(      logging_error );
            b.putShort( file_number );
            b.putInt(   file_offset );
            b.put(      target_logging_state );
            b.putShort( logging_period_ms );
            b.putInt(   logging_n_cycles );

            return b.array();
        }

        @Override
        public void unpack(byte[] in) {
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
    public class MeterInfo        extends MeterStructure {
        public byte  pcb_version;
        public byte  assembly_variant;
        public short lot_number;
        public int   build_time;

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
        public void unpack(byte[] in) {
            ByteBuffer b = wrap(in);
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
            ByteBuffer b = ByteBuffer.wrap(new byte[16]);

            putInt24(b, reading_lsb[0]);
            putInt24(b, reading_lsb[1]);
            b.putFloat( reading_ms[0]);
            b.putFloat( reading_ms[1]);

            return b.array();
        }

        @Override
        public void unpack(byte[] in) {
            ByteBuffer b = wrap(in);

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
        public void unpack(final byte[] in) {
            name = new String(in);
        }
    }
    public class MeterTime        extends MeterStructure {
        public long utc_time;

        @Override
        public UUID getUUID() { return mUUID.METER_UTC_TIME; }

        @Override
        public byte[] pack() {
            ByteBuffer b = wrap(new byte[4]);
            b.putInt((int) utc_time);
            return b.array();
        }
        @Override
        public void unpack(final byte[] in) {
            ByteBuffer b = wrap(in);
            utc_time = b.getInt();
            // Prevent sign extension since Java will assume the int being unpacked is signed
            utc_time &= 0xFFFFFFFF;
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

                ByteBuffer bb = wrap(buf);
                for(int i = 0; i < getBufLen(); i++) {
                    floatBuf[i] = (float)lsbToNativeUnits(getInt24(bb),0);
                }
            }
            String s = String.format("CH1 Progress: %d of %d", buf_i, nBytes);
            Log.i(TAG,s);
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
                Log.d(TAG, "CH2 full");
                if (buf_i > nBytes) {
                    return;
                }

                ByteBuffer bb = wrap(buf);
                for (int i = 0; i < getBufLen(); i++) {
                    floatBuf[i] = (float) lsbToNativeUnits(getInt24(bb), 1);
                }
                if (buf_full_cb != null) {
                    buf_full_cb.run();
                }
            }
            String s = String.format("CH2 Progress: %d of %d", buf_i, nBytes);
            Log.i(TAG,s);
        }
    }

    public class OADIdentity extends MeterStructure {
        public short crc0;
        public short crc1;
        public short ver;
        public int len;
        public int build_time;
        public byte[] res = new byte[4];

        @Override
        public UUID getUUID() { return mUUID.OAD_IMAGE_IDENTIFY; }

        @Override
        public void unpack(byte[] buf) {

        }

        public void unpackFromFile(byte[] fbuf) {
            ByteBuffer b = ByteBuffer.wrap(fbuf);
            b.order(ByteOrder.LITTLE_ENDIAN);
            crc0 = b.getShort();
            crc1 = b.getShort();
            ver = b.getShort();
            len = 0xFFFF & ((int) b.getShort());
            build_time = b.getInt();
            for (int i = 0; i < 4; i++) {
                res[i] = b.get();
            }
        }

        public byte[] packForFile() {
            byte[] retval = new byte[16];
            ByteBuffer b = ByteBuffer.wrap(retval);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putShort(crc0);
            b.putShort(crc1);
            b.putShort(ver);
            b.putShort((short) len);
            b.putInt(build_time);
            for (int i = 0; i < 4; i++) {
                b.put(res[i]);
            }
            return retval;
        }

        @Override
        public byte[] pack() {
            byte[] retval = new byte[8];
            ByteBuffer b = ByteBuffer.wrap(retval);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putShort(ver);
            b.putShort((short) len);
            b.putInt(build_time);
            return retval;
        }
    }
    public class OADBlock    extends MeterStructure {
        public short requestedBlock;

        public short blockNum;
        public byte[] bytes;

        @Override
        public UUID getUUID() { return mUUID.OAD_IMAGE_BLOCK; }

        @Override
        public byte[] pack() {
            ByteBuffer b = wrap(new byte[18]);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putShort(blockNum);
            for( byte c : bytes ) {
                b.put(c);
            }
            return b.array();
        }

        @Override
        public void unpack(byte[] in) {
            ByteBuffer b = wrap(in);
            b.order(ByteOrder.LITTLE_ENDIAN);
            requestedBlock = b.getShort();
        }
    }

    // Used so the inner classes have something to grab
    public MooshimeterDevice mInstance;

    public int              mBuildTime;
    public boolean          mOADMode;
    public boolean          mInitialized = false;

    public MeterSettings    meter_settings;
    public MeterLogSettings meter_log_settings;
    public MeterInfo        meter_info;
    public MeterSample      meter_sample;
    public MeterName        meter_name;
    public MeterCH1Buf      meter_ch1_buf;
    public MeterCH2Buf      meter_ch2_buf;
    public MeterTime        meter_time;

    public OADIdentity      oad_identity;
    public OADBlock         oad_block;

    public MooshimeterDevice(final BluetoothDevice device, final Context context) {
        // Initialize super
        super(device,context);

        mInstance = this;

        // Initialize internal structures
        meter_name          = new MeterName();
        meter_settings      = new MeterSettings();
        meter_log_settings  = new MeterLogSettings();
        meter_info          = new MeterInfo();
        meter_sample        = new MeterSample();
        meter_ch1_buf       = new MeterCH1Buf();
        meter_ch2_buf       = new MeterCH2Buf();
        meter_time          = new MeterTime();

        oad_identity        = new OADIdentity();
        oad_block           = new OADBlock();
    }

    public int discover() {
        int rval = super.discover();
        if(rval != 0) {
            return rval;
        }
        if(!isInOADMode()) {
            // Grab the initial settings
            meter_settings.update();
            meter_settings.target_meter_state = meter_settings.present_meter_state;
            meter_log_settings.update();
            meter_info.update();
            meter_name.update();

            // Automatically sync the meter's clock to the phone clock
            meter_time.utc_time = System.currentTimeMillis()/1000;
            meter_time.send();
        }
        mInitialized = true;
        return rval;
    }

    public int disconnect() {
        mInitialized = false;
        return super.disconnect();
    }

    ////////////////////////////////
    // Convenience functions
    ////////////////////////////////

    public void setOADMode(boolean scanned) {
        // We receive OAD mode information from scan requests, so this
        // function is helpful for providing a mode hint before we actually connect
        // to the meter
        mOADMode = scanned;
    }

    public boolean isInOADMode() {
        // If we've already connected and discovered characteristics,
        // we can just see what's in the service dictionary.
        // If we haven't connected, revert to whatever the scan
        // hinted at.
        if(mServices.containsKey(mUUID.METER_SERVICE)){
            mOADMode = false;
        }
        if(mServices.containsKey(mUUID.OAD_SERVICE_UUID)) {
            mOADMode = true;
        }
        return mOADMode;
    }

    /**
     * Returns the number of ADC samples that constitute a sample buffer
     * @return number of samples
     */
    public int getBufLen() {
        return (1<<(meter_settings.calc_settings & METER_CALC_SETTINGS_DEPTH_LOG2));
    }

    /**
     * Downloads the complete sample buffer from the Mooshimeter.
     * This interaction spans many connection intervals, the exact length depends on the number of samples in the buffer
     * @param onReceived Called when the complete buffer has been downloaded
     */
    public void getBuffer(final Runnable onReceived) {
        // Set up for oneshot, turn off all math in firmware
        if(0==(meter_settings.calc_settings & METER_CALC_SETTINGS_ONESHOT)) {
            // Avoid sending the same meter settings over and over - check and see if we're set up for oneshot
            // and if we are, don't send the meter settings again.  Due to a firmware bug in the wild (Feb 2 2015)
            // sending meter settings will cause the ADC to run for one buffer fill even if the state is METER_PAUSED
            meter_settings.calc_settings &=~(METER_CALC_SETTINGS_MS|METER_CALC_SETTINGS_MEAN);
            meter_settings.calc_settings |= METER_CALC_SETTINGS_ONESHOT;
            meter_settings.target_meter_state = METER_PAUSED;
            meter_settings.send();
        }
        meter_sample.enableNotify(false,null);
        meter_ch1_buf.enableNotify(true,null);
        meter_ch2_buf.enableNotify(true,null);
        meter_ch2_buf.buf_full_cb = onReceived;
        meter_settings.target_meter_state = METER_RUNNING;
        meter_ch1_buf.buf_i = 0;
        meter_ch2_buf.buf_i = 0;
        meter_settings.send();
    }

    /**
     * Stop the meter from sending samples.  Opposite of playSampleStream
     */
    public void pauseStream() {
        meter_sample.enableNotify(false, null);
        if(meter_settings.target_meter_state != METER_PAUSED) {
            meter_settings.target_meter_state = METER_PAUSED;
            meter_settings.send();
        }
    }

    /**
     * Start streaming samples from the meter.  Samples will be streamed continuously until pauseStream is called
     * @param on_notify Called whenever a new sample is received
     */
    public void playSampleStream(final Runnable on_notify) {
        meter_settings.calc_settings |= MooshimeterDevice.METER_CALC_SETTINGS_MEAN | MooshimeterDevice.METER_CALC_SETTINGS_MS;
        meter_settings.calc_settings &=~MooshimeterDevice.METER_CALC_SETTINGS_ONESHOT;
        meter_settings.target_meter_state = MooshimeterDevice.METER_RUNNING;

        meter_sample.enableNotify(true,on_notify);
        meter_settings.send();
    }

    public static String formatReading(double val, MooshimeterDevice.SignificantDigits digits) {
        //TODO: Unify prefix handling.  Right now assume that in the area handling the units the correct prefix
        // is being applied
        while(digits.high > 4) {
            digits.high -= 3;
            val /= 1000;
        }
        while(digits.high <=0) {
            digits.high += 3;
            val *= 1000;
        }

        // TODO: Prefixes for units.  This will fail for wrong values of digits
        boolean neg = val<0;
        int left  = digits.high;
        int right = digits.n_digits - digits.high;
        String formatstring = String.format("%s%%0%d.%df",neg?"":" ", left+right+(neg?1:0), right); // To live is to suffer
        String retval;
        try {
            retval = String.format(formatstring, val);
        } catch ( java.util.UnknownFormatConversionException e ) {
            // Something went wrong with the string formatting, provide a default and log the error
            Log.e(TAG, "BAD FORMAT STRING");
            Log.e(TAG, formatstring);
            retval = "%f";
        }
        //Truncate
        retval = retval.substring(0, Math.min(retval.length(), 8));
        return retval;
    }

    //////////////////////////////////////
    // Autoranging
    //////////////////////////////////////

    /**
     * Reads a channel PGA setting and shifts the PGA gain
     * @param chx_set   Either the CH1SET or CH2SET register
     * @param inc       If true, PGA gain will be lowered (expanding the range) and vice versa
     * @param wrap      If at a maximum or minimum gain, whether to wrap over to the other side of the range
     * @return          The modified CHXSET register
     */
    byte pga_cycle(byte chx_set, boolean inc, boolean wrap) {
        // These are the PGA settings we will entertain
        // { 12, 4, 1 }
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

    /**
     * Returns the threshold, in LSB, below which the meter should shift its measurement settings down.
     * @param channel   The channel index (0 or 1)
     * @return          Threshold in LSB
     */

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
                    if(meter_info.pcb_version==7) {
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
                    } else {
                        // Assuming RevI
                        int lvl = getResLvl();
                        switch(lvl) {
                            case 0:
                                switch(pga_setting) {
                                    case 0x60:
                                        return 0;
                                    case 0x40:
                                        return (int) (0.33 * (1 << 22));
                                    case 0x10:
                                        return (int) (0.25 * (1 << 22));
                                }
                            case 1:
                                switch(pga_setting) {
                                    case 0x60:
                                    case 0x40:
                                        return (int) (0.33 * (1 << 22));
                                    case 0x10:
                                        return (int) (0.25 * (1 << 22));
                                }
                            case 2:
                                switch(pga_setting) {
                                    case 0x60:
                                    case 0x40:
                                        return (int) (0.33 * (1 << 22));
                                    case 0x10:
                                        return (int) (0.25 * (1 << 22));
                                }
                            case 3:
                                switch(pga_setting) {
                                    case 0x60:
                                        return 0;
                                    case 0x40:
                                        return (int) (0.33 * (1 << 22));
                                    case 0x10:
                                        return (int) (0.25 * (1 << 22));
                                }
                        }
                    }

                    break;
            }
            break;
        }
        return 0;
    }

    int getResLvl() {
        int rval = meter_settings.measure_settings & (METER_MEASURE_SETTINGS_ISRC_ON|METER_MEASURE_SETTINGS_ISRC_LVL);
        return rval;
    }
    void  setResLvl(int new_lvl) {
        meter_settings.measure_settings &=~(METER_MEASURE_SETTINGS_ISRC_ON|METER_MEASURE_SETTINGS_ISRC_LVL);
        meter_settings.measure_settings |= new_lvl;
    }

    void bumpResLvl(boolean expand, boolean wrap) {
        int lvl = getResLvl();
        if(expand) {
            if(--lvl==0) {
                if(wrap) {lvl=3;}
                else     {lvl++;}
            }
        } else {
            if(++lvl==4) {
                if(wrap) {lvl=1;}
                else     {lvl--;}
            }
        }
        setResLvl(lvl);
    }

    /**
     * Changes the measurement settings for a channel to expand or contract the measurement range
     * @param channel   The channel index (0 or 1)
     * @param expand    Expand (true) or contract (false) the range.
     * @param wrap      If at a maximum or minimum gain, whether to wrap over to the other side of the range
     */
    public void bumpRange(int channel, boolean expand, boolean wrap) {
        byte channel_setting    = meter_settings.chset[channel];
        int tmp;

        switch(channel_setting & METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input
                switch(channel) {
                    case 0:
                        // We are measuring current.  We can boost PGA, but that's all.
                        channel_setting = pga_cycle(channel_setting,expand,wrap);
                        break;
                    case 1:
                        // Switch the ADC GPIO to activate dividers
                        // NOTE: Don't bother with the 1.2V range for now.  Having a floating autoranged input leads to glitchy behavior.
                        tmp = (meter_settings.adc_settings & ADC_SETTINGS_GPIO_MASK)>>4;
                        if(expand) {
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
                    channel_setting = pga_cycle(channel_setting,expand,wrap);
                    break;
                case RESISTANCE:
                case DIODE:
                    if(meter_info.pcb_version==7) {
                        // This case is annoying.  We want PGA to always wrap if we are in the low range and going up OR in the high range and going down
                        if( 0 != ((expand?0:METER_MEASURE_SETTINGS_ISRC_LVL) ^ (meter_settings.measure_settings & METER_MEASURE_SETTINGS_ISRC_LVL))) {
                            wrap = true;
                        }
                        channel_setting = pga_cycle(channel_setting,expand,wrap);
                        tmp = channel_setting & METER_CH_SETTINGS_PGA_MASK;
                        tmp >>=4;
                        if(   ( expand && tmp == 6) || (!expand && tmp == 1) ) {
                            meter_settings.measure_settings ^= METER_MEASURE_SETTINGS_ISRC_LVL;
                        }
                    } else {
                        int lvl = getResLvl();
                        boolean inner_wrap = true;
                        if(lvl==1) {
                            // Res src is 10M
                            if(expand) {inner_wrap = wrap;}
                        } else if(lvl==3) {
                            // Res src is 10k
                            if(!expand) {inner_wrap = wrap;}
                        }
                        channel_setting = pga_cycle(channel_setting,expand,inner_wrap);
                        tmp = channel_setting & METER_CH_SETTINGS_PGA_MASK;
                        tmp >>=4;
                        if( (expand && tmp == 6) || (!expand && tmp == 1)) {
                            // The PGA wrapped, bump the macro range
                            bumpResLvl(expand, wrap);
                        }
                    }
                    break;
            }
            break;
        }
        meter_settings.chset[channel] = channel_setting;
    }

    public void applyAutorange() {
        final boolean ac_used = disp_ac[0] || disp_ac[1];
        final int upper_limit_lsb = (int)( 0.85*(1<<22));
        final int lower_limit_lsb = (int)(-0.85*(1<<22));

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
    public static final int METER_CALC_SETTINGS_RES        = 0x80;
    
    public static final int ADC_SETTINGS_SAMPLERATE_MASK = 0x07;
    public static final int ADC_SETTINGS_GPIO_MASK = 0x30;
    
    public static final int METER_CH_SETTINGS_PGA_MASK = 0x70;
    public static final int METER_CH_SETTINGS_INPUT_MASK = 0x0F;

    public class SignificantDigits {
        public int high;
        public int n_digits;
    }

    /**
     * Examines the measurement settings for the given channel and returns the effective number of bits
     * @param channel The channel index (0 or 1)
     * @return Effective number of bits
     */

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
        if(meter_info.pcb_version==7 && channel == 0 && (meter_settings.chset[0] & METER_CH_SETTINGS_INPUT_MASK) == 0 ) {
            // This is compensation for a bug in RevH, where current sense chopper noise dominates
            enob -= 2;
        }
        return enob;
    }

    /**
     * Based on the ENOB and the measurement range for the given channel, determine which digits are
     * significant in the output.
     * @param channel The channel index (0 or 1)
     * @return  A SignificantDigits structure, "high" is the number of digits to the left of the decimal point and "digits" is the number of significant digits
     */

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

    /**
     * Examines the measurement settings and converts the input (in LSB) to the voltage at the input
     * of the AFE.  Note this is at the input of the AFE, not the input of the ADC (there is a PGA)
     * between them
     * @param reading_lsb   Input reading [LSB]
     * @param channel       The channel index (0 or 1)
     * @return  Voltage at AFE input [V]
     */

    public double lsbToADCInVoltage(final int reading_lsb, final int channel) {
        // This returns the input voltage to the ADC,
        final double Vref;
        final double pga_lookup[] = {6,1,2,3,4,8,12};
        if(meter_info.pcb_version==7){
            Vref=2.5;
        } else if(meter_info.pcb_version==8){
            Vref=2.42;
        } else {Vref=0;}
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

    /**
     * Converted the voltage at the input of the AFE to the voltage at the HV input by examining the
     * meter settings
     * @param adc_voltage   Voltage at the AFE [V]
     * @return  Voltage at the HV input terminal [V]
     */

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

    /**
     * Convert voltage at the input of the AFE to current through the A terminal
     * @param adc_voltage   Voltage at the AFE [V]
     * @return              Current through the A terminal [A]
     */

    public double adcVoltageToCurrent(final double adc_voltage) {
        final double rs;
        final double amp_gain;
        if(meter_info.pcb_version==7){
            rs = 1e-3;
            amp_gain = 80.0;
        } else if(meter_info.pcb_version==8) {
            rs = 10e-3;
            amp_gain = 1.0;
        } else {
            // We want to raise an error
            rs=0;
            amp_gain=0;
        }
        return adc_voltage/(amp_gain*rs);
    }

    /**
     * Convert voltage at the input of the AFE to temperature
     * @param adc_voltage   Voltage at the AFE [V]
     * @return              Temperature [C]
     */

    public double adcVoltageToTemp(double adc_voltage) {
        adc_voltage -= 145.3e-3; // 145.3mV @ 25C
        adc_voltage /= 490e-6;   // 490uV / C
        return 25.0 + adc_voltage;
    }

    /**
     * Examines the meter settings to determine how much current is flowing out of the current source
     * (flows out the Active terminal)
     * @return  Current from the active terminal [A]
     */

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

    public double getIsrcRes() {
        int tmp = meter_settings.measure_settings & (METER_MEASURE_SETTINGS_ISRC_ON|METER_MEASURE_SETTINGS_ISRC_LVL);
        if(tmp == 0) {
            throw new Error();
        } else if(tmp == METER_MEASURE_SETTINGS_ISRC_ON) {
            return 10e6+10e3+7.9;
        } else if(tmp == METER_MEASURE_SETTINGS_ISRC_LVL) {
            return 300e3+10e3+7.9;
        } else if(tmp == (METER_MEASURE_SETTINGS_ISRC_ON|METER_MEASURE_SETTINGS_ISRC_LVL)) {
            return 10e3+7.9;
        } else {
            throw new Error();
        }
    }

    /**
     * Converts an ADC reading to the reading at the terminal input
     * @param lsb   Input reading in LSB
     * @param ch    Channel index (0 or 1)
     * @return      Value at the input terminal.  Depending on measurement settings, can be V, A or Ohms
     */

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
                        if(meter_info.pcb_version==7){
                            // CH1 offset is treated as an extrinsic offset because it's dominated by drift in the isns amp
                            adc_volts = lsbToADCInVoltage(lsb,ch);
                            adc_volts -= offsets[0];
                            return adcVoltageToCurrent(adc_volts);
                        } else {
                            lsb -= offsets[0];
                            adc_volts = lsbToADCInVoltage(lsb,ch);
                            return adcVoltageToCurrent(adc_volts);
                        }
                    case 1:
                        // CH2 offset is treated as an intrinsic offset because it's dominated by offset in the ADC itself
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
                final double ohms;
                if(0!=(meter_settings.measure_settings & (METER_MEASURE_SETTINGS_ISRC_ON|METER_MEASURE_SETTINGS_ISRC_LVL))) {
                    if(meter_info.pcb_version==7) {
                        final double isrc_current = getIsrcCurrent();
                        adc_volts = lsbToADCInVoltage(lsb,ch);
                        adc_volts -= ptc_resistance*isrc_current;
                        adc_volts -= offsets[2]*isrc_current;
                        ohms = adc_volts/isrc_current;
                    } else if(meter_info.pcb_version==8) {
                        final double isrc_res = getIsrcRes();
                        final double avdd=3-1.21; // Make this better

                        adc_volts = lsbToADCInVoltage(lsb,ch);
                        ohms = ((adc_volts/(avdd-adc_volts))*isrc_res) - ptc_resistance;
                    } else {
                        throw new Error();
                    }
                } else {
                    // Current source is off, offset is intrinsic
                    lsb -= offsets[2];
                    adc_volts = lsbToADCInVoltage(lsb,ch);
                    ohms = 0;
                }
                if( disp_ch3_mode == CH3_MODES.RESISTANCE ) {
                    // Convert to Ohms
                    return ohms;
                } else {
                    return adc_volts;
                }
            default:
                Log.w(TAG,"Unrecognized channel setting");
                return adc_volts;
        }
    }

    /**
     *
     * @param channel The channel index (0 or 1)
     * @return A string describing what the channel is measuring
     */

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

    /**
     *
     * @param channel The channel index (0 or 1)
     * @return A string containing the units label for the channel
     */

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

    /**
     *
     * @param channel The channel index (0 or 1)
     * @return        A String containing the input label of the channel (V, A, Omega or Internal)
     */

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
