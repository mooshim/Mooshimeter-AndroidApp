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
import android.content.SharedPreferences;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import static java.util.UUID.fromString;

public abstract class MooshimeterDeviceBase extends BLEDeviceBase implements MooshimeterControlInterface {
    ////////////////////////////////
    // Statics
    ////////////////////////////////
    public static final class SignificantDigits {
        public int high;
        public int n_digits;
    }

    private static final String TAG="MooshimeterDevice";

    public enum TEMP_UNITS {
        CELSIUS,
        FAHRENHEIT,
        KELVIN
    }

    private static MooshimeterDelegate dummy_delegate = new MooshimeterDelegate() {
        @Override
        public void onInit() {        }
        @Override
        public void onDisconnect() {        }
        @Override
        public void onBatteryVoltageReceived(float voltage) {}
        @Override
        public void onSampleReceived(final double timestamp_utc, int channel, float val) {        }
        @Override
        public void onBufferReceived(final double timestamp_utc, int channel, float dt, float[] val) {        }
        @Override
        public void onSampleRateChanged(int i, int sample_rate_hz) {        }
        @Override
        public void onBufferDepthChanged(int i, int buffer_depth) {        }
        @Override
        public void onLoggingStatusChanged(boolean on, int new_state, String message) {        }
        @Override
        public void onRangeChange(int c, int i, MooshimeterDevice.RangeDescriptor new_range) {        }
        @Override
        public void onInputChange(int c, int i, MooshimeterDevice.InputDescriptor descriptor) {        }
        @Override
        public void onRealPowerCalculated(final double timestamp_utc, float val) {        }
        @Override
        public void onOffsetChange(int c, float offset) { }
    };
    public MooshimeterDelegate delegate = dummy_delegate;

    // Display control settings
    public TEMP_UNITS      disp_temp_units = TEMP_UNITS.CELSIUS;
    public final boolean[] range_auto = new boolean[]{true,true};
    public boolean         rate_auto = true;
    public boolean         depth_auto = true;

    public final boolean[] speech_on = new boolean[]{false,false};

    protected static void putInt24(ByteBuffer b, int arg) {
        // Puts the bottom 3 bytes of arg on to b
        ByteBuffer tmp = ByteBuffer.allocate(4);
        byte[] tb = new byte[3];
        tmp.putInt(arg);
        tmp.flip();
        tmp.get(tb);
        b.put( tb );
    }
    protected static int  getInt24(ByteBuffer b) {
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

    protected static ByteBuffer wrap(byte[] in) {
        // Generates a little endian byte buffer wrapping the byte[]
        ByteBuffer b = ByteBuffer.wrap(in);
        b.order(ByteOrder.LITTLE_ENDIAN);
        return b;
    }

    public MooshimeterDeviceBase(PeripheralWrapper wrap) {
        // Initialize super
        super(wrap);
        mInstance = this;
    }

    public int disconnect() {
        mInitialized = false;
        return super.disconnect();
    }

    ////////////////////////////////
    // Convenience functions
    ////////////////////////////////

    public static String formatReading(float val, MooshimeterDeviceBase.SignificantDigits digits) {
        final String prefixes[] = new String[]{"n","?","m","","k","M","G"};
        int prefix_i = 3;
        while(digits.high > 3) {
            prefix_i++;
            digits.high -= 3;
            val /= 1000;
        }
        while(digits.high <= 0) {
            prefix_i--;
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
        retval += prefixes[prefix_i];
        return retval;
    }

    //////////////////////////////////////
    // Representation helpers
    //////////////////////////////////////

    public abstract SignificantDigits getSigDigits(final int channel);
    public abstract String getUnits(final int c);

    //////////////////////////////////////
    // MooshimeterControlInterface
    //////////////////////////////////////
    @Override
    public void setDelegate(MooshimeterDelegate d) {
        delegate = d;
    }
    @Override
    public void removeDelegate() {
        delegate = dummy_delegate;
    }
}
