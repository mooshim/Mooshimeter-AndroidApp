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
import android.util.Log;

import com.mooshim.mooshimeter.common.Chooser;
import com.mooshim.mooshimeter.common.LogFile;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.common.Util;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.interfaces.MooshimeterDelegate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
        public RangeDescriptor() {}
        public RangeDescriptor(String name_arg,float max_arg) {
            name=name_arg;
            max=max_arg;
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

    Set<MooshimeterDelegate> delegates = new HashSet<>();

    protected MooshimeterDelegate delegate = new MooshimeterDelegate() {
        @Override
        public void onDisconnect() {
            for(MooshimeterDelegate d:delegates) {
                d.onDisconnect();
            }
        }
        @Override
        public void onRssiReceived(int rssi) {
            for(MooshimeterDelegate d:delegates) {
                d.onRssiReceived(rssi);
            }
        }
        @Override
        public void onBatteryVoltageReceived(float voltage) {
            for(MooshimeterDelegate d:delegates) {
                d.onBatteryVoltageReceived(voltage);
            }
        }
        @Override
        public void onSampleReceived(double timestamp_utc, Channel c, MeterReading val) {
            for(MooshimeterDelegate d:delegates) {
                d.onSampleReceived(timestamp_utc,c,val);
            }
        }
        @Override
        public void onBufferReceived(double timestamp_utc, Channel c, float dt, float[] val) {
            for(MooshimeterDelegate d:delegates) {
                d.onBufferReceived(timestamp_utc,c,dt,val);
            }
        }
        @Override
        public void onSampleRateChanged(int i, int sample_rate_hz) {
            for(MooshimeterDelegate d:delegates) {
                d.onSampleRateChanged(i,sample_rate_hz);
            }
        }
        @Override
        public void onBufferDepthChanged(int i, int buffer_depth) {
            for(MooshimeterDelegate d:delegates) {
                d.onBufferDepthChanged(i,buffer_depth);
            }
        }
        @Override
        public void onLoggingStatusChanged(boolean on, int new_state, String message) {
            for(MooshimeterDelegate d:delegates) {
                d.onLoggingStatusChanged(on,new_state,message);
            }
        }
        @Override
        public void onRangeChange(Channel c, RangeDescriptor new_range) {
            for(MooshimeterDelegate d:delegates) {
                d.onRangeChange(c,new_range);
            }
        }
        @Override
        public void onInputChange(Channel c, InputDescriptor descriptor) {
            for(MooshimeterDelegate d:delegates) {
                d.onInputChange(c,descriptor);
            }
        }
        @Override
        public void onOffsetChange(Channel c, MeterReading offset) {
            for(MooshimeterDelegate d:delegates) {
                d.onOffsetChange(c, offset);
            }
        }
        @Override
        public void onLogInfoReceived(LogFile log) {
            for(MooshimeterDelegate d:delegates) {
                d.onLogInfoReceived(log);
            }
        }
        @Override
        public void onLogFileReceived(LogFile log) {
            for(MooshimeterDelegate d:delegates) {
                d.onLogFileReceived(log);
            }
        }

        @Override
        public void onLogDataReceived(LogFile log, String data) {
            for(MooshimeterDelegate d:delegates) {
                d.onLogDataReceived(log,data);
            }
        }
    };

    public Runnable disconnect_runnable;

    // Display control settings
    private final String ch1_auto_key = "CH1_AUTO";
    private final String ch2_auto_key = "CH2_AUTO";
    private final String rate_auto_key = "RATE_AUTO";
    private final String depth_auto_key = "DEPTH_AUTO";

    public final Map<Channel,Boolean> speech_on = new ConcurrentHashMap<>();

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
        disconnect_runnable = new Runnable() {
            @Override
            public void run() {
                mPwrap.cancelDisconnectCB(disconnect_runnable);
                delegate.onDisconnect();
            }
        };
        mPwrap.addDisconnectCB(disconnect_runnable);
        return 0;
    }

    abstract float getEnob(Channel c);
    abstract float getMaxRangeForChannel(Channel c);

    public MeterReading wrapMeterReading(Channel c,float val,boolean relative) {
        MooshimeterDeviceBase.InputDescriptor id = getSelectedDescriptor(c);
        final float enob = getEnob(c);
        float max = getMaxRangeForChannel(c);
        MeterReading rval;
        if(id.units.equals("K")) {
            // Nobody likes Kelvin!  C or F?
            abstract class Converter {
                abstract float convert(float val);
            }
            Converter converter;
            String units;

            if(Util.getPreferenceBoolean(Util.preference_keys.USE_FAHRENHEIT)) {
                units = "F";
                if(relative) {
                    converter = new Converter() {
                        @Override
                        float convert(float val) {
                            return Util.TemperatureUnitsHelper.relK2F(val);
                        }
                    };
                } else {
                    converter = new Converter() {
                        @Override
                        float convert(float val) {
                            return Util.TemperatureUnitsHelper.absK2F(val);
                        }
                    };
                }
            } else {
                units = "C";
                if(relative) {
                    converter = new Converter() {
                        @Override
                        float convert(float val) {
                            return Util.TemperatureUnitsHelper.relK2C(val);
                        }
                    };
                } else {
                    converter = new Converter() {
                        @Override
                        float convert(float val) {
                            return Util.TemperatureUnitsHelper.absK2C(val);
                        }
                    };
                }
            }
            rval = new MeterReading(converter.convert(val),
                                    (int)Math.log10(Math.pow(2.0, enob)),
                                    converter.convert(max),
                                    units);
        } else {
            rval = new MeterReading(val,
                                    (int)Math.log10(Math.pow(2.0, enob)),
                                    max,
                                    id.units);
        }
        return rval;
    }

    public MeterReading wrapMeterReading(Channel c,float val) {
        return wrapMeterReading(c,val,false);
    }

    public boolean getRangeAuto(Channel c) {
        final String key;
        switch(c) {
            case CH1:
                key = ch1_auto_key;
                break;
            case CH2:
                key = ch2_auto_key;
                break;
            default:
                Log.e(TAG,"EGADS");
                key = "";
                break;
        }
        return getPreference(key,true);
    }

    public void setRangeAuto(Channel c, boolean b) {
        final String key;
        switch(c) {
            case CH1:
                key = ch1_auto_key;
                break;
            case CH2:
                key = ch2_auto_key;
                break;
            default:
                Log.e(TAG,"EGADS");
                key = "";
                break;
        }
        setPreference(key,b);
    }

    public boolean getRateAuto() {
        return  getPreference(rate_auto_key, true);
    }
    public void setRateAuto(boolean arg) {
        setPreference(rate_auto_key,arg);
    }
    public boolean getDepthAuto() {
        return getPreference(depth_auto_key, true);
    }
    public void setDepthAuto(boolean arg) {
        setPreference(depth_auto_key,arg);
    }

    //////////////////////////////////////
    // MooshimeterControlInterface
    //////////////////////////////////////
    @Override
    public void addDelegate(MooshimeterDelegate d) {
        delegates.add(d);
    }
    @Override
    public void removeDelegate(MooshimeterDelegate d) {
        delegates.remove(d);
    }
}
