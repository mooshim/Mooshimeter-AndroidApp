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


import android.bluetooth.BluetoothGatt;

import com.mooshim.mooshimeter.common.Chooser;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.interfaces.MooshimeterDelegate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public abstract class MooshimeterDeviceBase extends BLEDeviceBase implements MooshimeterControlInterface {
    ////////////////////////////////
    // Statics
    ////////////////////////////////


    public static class RangeDescriptor {
        public String name="";
        public float max=0;
        public String toString() {
            return name;
        }
    }
    public static class InputDescriptor {
        public String name;
        public Chooser<RangeDescriptor> ranges = new Chooser<>();
        public String units;
        public InputDescriptor(String name,String units) {
            this.name=name;
            this.units=units;
        }
        public InputDescriptor() {
            this("","");
        }
        public String toString() {
            return name;
        }
    }

    private static final String TAG="MooshimeterDeviceBase";

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
        public void onRssiReceived(int rssi) {}
        @Override
        public void onBatteryVoltageReceived(float voltage) {}
        @Override
        public void onSampleReceived(double timestamp_utc, Channel c, MeterReading val) {}
        @Override
        public void onBufferReceived(double timestamp_utc, Channel c, float dt, float[] val) {}
        @Override
        public void onSampleRateChanged(int i, int sample_rate_hz) {        }
        @Override
        public void onBufferDepthChanged(int i, int buffer_depth) {        }
        @Override
        public void onLoggingStatusChanged(boolean on, int new_state, String message) {}
        @Override
        public void onRangeChange(Channel c, RangeDescriptor new_range) {        }
        @Override
        public void onInputChange(Channel c, InputDescriptor descriptor) {        }
        @Override
        public void onOffsetChange(Channel c, MeterReading offset) { }
    };
    public MooshimeterDelegate delegate = dummy_delegate;

    public int disconnect_handle;

    // Display control settings
    public TEMP_UNITS      disp_temp_units = TEMP_UNITS.CELSIUS;
    public final Map<Channel,Boolean> range_auto = new HashMap<>();
    public boolean         rate_auto = true;
    public boolean         depth_auto = true;

    public final Map<Channel,Boolean> speech_on = new HashMap<>();

    protected static void putInt24(ByteBuffer b, int arg) {
        // Puts the bottom 3 bytes of arg on to b
        ByteBuffer tmp = ByteBuffer.allocate(4);
        byte[] tb = new byte[3];
        tmp.putInt(arg);
        tmp.flip();
        tmp.get(tb);
        b.put(tb);
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
        range_auto.put(Channel.CH1,true);
        range_auto.put(Channel.CH2,true);
        speech_on.put(Channel.CH1, false);
        speech_on.put(Channel.CH2, false);
    }

    public int disconnect() {
        mInitialized = false;
        return super.disconnect();
    }

    @Override
    public int initialize() {
        super.initialize();
        rssi_cb = new Runnable() {
            @Override
            public void run() {
                delegate.onRssiReceived(getRSSI());
            }
        };

        disconnect_handle = mPwrap.addConnectionStateCB(BluetoothGatt.STATE_DISCONNECTED, new Runnable() {
            @Override
            public void run() {
                mPwrap.cancelConnectionStateCB(disconnect_handle);
                delegate.onDisconnect();
            }
        });
        return 0;
    }

    //////////////////////////////////////
    // MooshimeterControlInterface
    //////////////////////////////////////
    @Override
    public void addDelegate(MooshimeterDelegate d) {
        delegate = d;
    }
    @Override
    public void removeDelegate() {
        delegate = dummy_delegate;
    }
}
