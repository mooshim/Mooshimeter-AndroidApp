package com.mooshim.mooshimeter.devices;

import android.util.Log;

import com.mooshim.mooshimeter.common.Chooser;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.ThermocoupleHelper;
import com.mooshim.mooshimeter.common.Util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.UUID.fromString;

public class MooshimeterDevice extends MooshimeterDeviceBase{
    ////////////////////////////////
    // STATICS
    ////////////////////////////////

    static String TAG="MOOSHIMETER";

    /*
    mUUID stores the UUID values of all the Mooshimeter fields.
    Note that the OAD fields are only accessible when connected to the Mooshimeter in OAD mode
    and the METER_ fields are only accessible when connected in meter mode.
     */
    public static class mUUID {
        private mUUID() {}
        public final static UUID
            METER_SERVICE       = fromString("1BC5FFA0-0200-62AB-E411-F254E005DBD4"),
            METER_SERIN         = fromString("1BC5FFA1-0200-62AB-E411-F254E005DBD4"),
            METER_SEROUT        = fromString("1BC5FFA2-0200-62AB-E411-F254E005DBD4"),

            OAD_SERVICE_UUID    = fromString("1BC5FFC0-0200-62AB-E411-F254E005DBD4"),
            OAD_IMAGE_IDENTIFY  = fromString("1BC5FFC1-0200-62AB-E411-F254E005DBD4"),
            OAD_IMAGE_BLOCK     = fromString("1BC5FFC2-0200-62AB-E411-F254E005DBD4"),
            OAD_REBOOT          = fromString("1BC5FFC3-0200-62AB-E411-F254E005DBD4");
    }

    ////////////////////////////////
    // CONFIG TREE
    ////////////////////////////////

    public ConfigTree tree = null;

    ////////////////////////////////
    // MEMBERS FOR TRACKING AVAILABLE INPUTS AND RANGES
    ////////////////////////////////

    public static class RangeDescriptor extends MooshimeterDeviceBase.RangeDescriptor {
        public ConfigTree.ConfigNode node;
    }

    public static abstract class MathInputDescriptor extends MooshimeterDeviceBase.InputDescriptor {
        public MathInputDescriptor(String name, String units) {super(name,units);}
        public abstract void onChosen();
        public abstract boolean meterSettingsAreValid();
        public abstract MeterReading calculate();
    }

    public static class InputDescriptor extends MooshimeterDeviceBase.InputDescriptor{
        public ConfigTree.ConfigNode input_node;
        public ConfigTree.ConfigNode analysis_node;
        public ConfigTree.ConfigNode shared_node;
    }

    final Map<Channel,Chooser<MooshimeterDeviceBase.InputDescriptor>> input_descriptors = new ConcurrentHashMap<>();

    ////////////////////////////////
    // Private methods for dealing with config tree
    ////////////////////////////////

    public static String cleanFloatFmt(float d) {
        if(d == (long) d)
            return String.format("%d",(long)d);
        else
            return String.format("%s",d);
    }
    private String toRangeLabel(float max){
        final String prefixes[] = new String[]{"n","?","m","","k","M","G"};
        int prefix_i = 3;
        while(max >= 1000.0) {
            max /= 1000;
            prefix_i++;
        }
        while(max < 1.0) {
            max *= 1000;
            prefix_i--;
        }
        return cleanFloatFmt(max)+prefixes[prefix_i];
    }
    private void addRangeDescriptors(InputDescriptor id, ConfigTree.ConfigNode rangenode) {
        for(ConfigTree.ConfigNode r:rangenode.children) {
            RangeDescriptor rd = new RangeDescriptor();
            rd.node = r;
            rd.max  = Float.parseFloat(r.getShortName());
            rd.name = toRangeLabel(rd.max)+id.units;
            id.ranges.add(rd);
        }
    }

    ////////////////////////////////
    // METHODS
    ////////////////////////////////

    public MooshimeterDevice(PeripheralWrapper wrap) {
        super(wrap);
        tree = new ConfigTree();
        input_descriptors.put(Channel.CH1,new Chooser<MooshimeterDeviceBase.InputDescriptor>());
        input_descriptors.put(Channel.CH2,new Chooser<MooshimeterDeviceBase.InputDescriptor>());
        input_descriptors.put(Channel.MATH,new Chooser<MooshimeterDeviceBase.InputDescriptor>());
    }
    public void attachCallback(String nodestr,NotifyHandler cb) {
        ConfigTree.ConfigNode n = tree.getNode(nodestr);
        if(n==null) {return;}
        n.addNotifyHandler(cb);
    }
    public void removeCallback(String nodestr,NotifyHandler cb) {
        ConfigTree.ConfigNode n = tree.getNode(nodestr);
        if(n==null) {return;}
        n.removeNotifyHandler(cb);
    }

    ////////////////////////////////
    // Private helpers
    ////////////////////////////////

    private ConfigTree.ConfigNode getInputNode(Channel c) {
        ConfigTree.ConfigNode rval = tree.getNode(c.name() + ":MAPPING");
        while(true) {
            if (rval.ntype == ConfigTree.NTYPE.LINK) {
                rval = tree.getNode((String)rval.getValue());
            } else if(rval.ntype== ConfigTree.NTYPE.CHOOSER) {
                rval = rval.getChosen();
            } else {
                return rval;
            }
        }
    }
    private Object getValueAt(String p) {
        Object rval = tree.getValueAt(p);
        if(rval==null) {
            // FIXME: hack
            return 0;
        }
        return rval;
    }
    private static List<String> getChildNameList(ConfigTree.ConfigNode n) {
        List<String> inputs = new ArrayList<String>();
        for(ConfigTree.ConfigNode child:n.children) {
            inputs.add(child.getShortName());
        }
        return inputs;
    }

    private InputDescriptor makeInputDescriptor(Channel c, String name, String node_name, String analysis, String units, boolean shared) {
        InputDescriptor i = new InputDescriptor();
        i.analysis_node = tree.getNode(c.name()+":ANALYSIS:"+analysis);
        i.name          = name;
        i.units         = units;
        if(shared) {
            i.shared_node   = tree.getNode(c.name()+":MAPPING:SHARED");
            i.input_node    = tree.getNode("SHARED:"+node_name);
        } else {
            i.input_node    = tree.getNode(c.name()+":MAPPING:"+node_name);
        }
        addRangeDescriptors(i, i.input_node);
        return i;
    }
    private void determineInputDescriptorIndex(Channel c) {
        if(c==Channel.MATH) {
            // Math input can be set arbitrarily
            return;
        }
        Chooser<MooshimeterDeviceBase.InputDescriptor> chooser = input_descriptors.get(c);
        for(InputDescriptor d:(List<InputDescriptor>)(List<?>)chooser.getChoices()) {
            if(getInputNode(c) == d.input_node) {
                if(d.analysis_node == tree.getNode(c.name()+":ANALYSIS").getChosen()) {
                    chooser.choose(d);
                }
            }
        }
    }
    private float[] interpretSampleBuffer(Channel c, byte[] payload) {
        ByteBuffer b = ByteBuffer.wrap(payload);
        int bytes_per_sample = (Integer)tree.getValueAt(c.name()+":BUF_BPS");
        bytes_per_sample /= 8;
        float lsb2native = (Float)tree.getValueAt(c.name()+":BUF_LSB2NATIVE");
        int n_samples = payload.length/bytes_per_sample;
        float[] rval = new float[n_samples];
        for(int i = 0; i < n_samples; i++) {
            int val=0;
            if(bytes_per_sample==3)      {val = getInt24(b);}
            else if(bytes_per_sample==2) {val = b.getShort();}
            else if(bytes_per_sample==1) {val = b.get();}
            else{new Exception().printStackTrace();}
            rval[i] = ((float)val)*lsb2native;
        }
        return rval;
    }

    private MeterReading wrapMeterReading(Channel c,float val) {
        MooshimeterDeviceBase.InputDescriptor id = getSelectedDescriptor(c);
        final double enob = getEnob(c);
        float max = getMaxRangeForChannel(c);
        MeterReading rval;
        if(id.units.equals("K")) {
            // Nobody likes Kelvin!  C or F?
            if(Util.getPreference(Util.preference_keys.USE_FAHRENHEIT)) {
                rval = new MeterReading(Util.TemperatureUnitsHelper.AbsK2F(val),
                                        (int)Math.log10(Math.pow(2.0, enob)),
                                        Util.TemperatureUnitsHelper.AbsK2F(max),
                                        "F");
            } else {
                rval = new MeterReading(Util.TemperatureUnitsHelper.AbsK2C(val),
                                        (int)Math.log10(Math.pow(2.0, enob)),
                                        Util.TemperatureUnitsHelper.AbsK2C(max),
                                        "C");
            }
        } else {
            rval = new MeterReading(val,
                                    (int)Math.log10(Math.pow(2.0, enob)),
                                    max,
                                    id.units);
        }
        return rval;
    }

    ////////////////////////////////
    // BLEDeviceBase methods
    ////////////////////////////////

    @Override
    public int initialize() {
        super.initialize();
        int rval=0;

        if(mPwrap.getChar(mUUID.METER_SERIN)==null||mPwrap.getChar(mUUID.METER_SEROUT)==null) {
            return -1;
        }
        tree.attach(mPwrap, mUUID.METER_SERIN, mUUID.METER_SEROUT);

        // At this point the tree is loaded.  Refresh all values in the tree.
        tree.refreshAll();

        setTime(Util.getUTCTime());

        Channel c = Channel.CH1;
        Chooser<MooshimeterDeviceBase.InputDescriptor> l = input_descriptors.get(c);

        l.add(makeInputDescriptor(c, "CURRENT DC", "CURRENT", "MEAN", "A", false));
        l.add(makeInputDescriptor(c, "CURRENT AC", "CURRENT", "RMS", "A", false));
        l.add(makeInputDescriptor(c, "INTERNAL TEMPERATURE", "TEMP", "MEAN", "K", false));
        // Create a hook for this input because thermocouple input will need to refer to it later
        final MooshimeterDeviceBase.InputDescriptor auxv_id = makeInputDescriptor(c, "AUXILIARY VOLTAGE DC", "AUX_V", "MEAN", "V", true);
        l.add(auxv_id);
        l.add(makeInputDescriptor(c, "AUXILIARY VOLTAGE AC", "AUX_V", "RMS", "V", true));
        l.add(makeInputDescriptor(c, "RESISTANCE", "RESISTANCE", "MEAN", "\u03A9", true));
        l.add(makeInputDescriptor(c, "DIODE DROP", "DIODE", "MEAN", "V", true));

        c = Channel.CH2;
        l = input_descriptors.get(c);

        l.add(makeInputDescriptor(c, "VOLTAGE DC", "VOLTAGE", "MEAN", "V", false));
        l.add(makeInputDescriptor(c, "VOLTAGE AC", "VOLTAGE", "RMS", "V", false));
        // Create a hook for this input because thermocouple input will need to refer to it later
        final MooshimeterDeviceBase.InputDescriptor temp_id = makeInputDescriptor(c, "INTERNAL TEMPERATURE", "TEMP", "MEAN", "K", false);
        l.add(temp_id);
        l.add(makeInputDescriptor(c, "AUXILIARY VOLTAGE DC", "AUX_V", "MEAN", "V", true));
        l.add(makeInputDescriptor(c, "AUXILIARY VOLTAGE AC", "AUX_V", "RMS", "V", true));
        l.add(makeInputDescriptor(c, "RESISTANCE", "RESISTANCE", "MEAN", "\u03a9", true));
        l.add(makeInputDescriptor(c, "DIODE DROP", "DIODE", "MEAN", "V", true));

        c = Channel.MATH;
        l = input_descriptors.get(c);

        MathInputDescriptor mid = new MathInputDescriptor("REAL POWER","W") {
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
                MeterReading rval = MeterReading.mult(getValue(Channel.CH1),getValue(Channel.CH2));
                rval.value = (Float)tree.getValueAt("REAL_PWR");
                return rval;
            }
        };
        l.add(mid);
        mid = new MathInputDescriptor("APPARENT POWER","W") {
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
                MeterReading rval = MeterReading.mult(getValue(Channel.CH1),getValue(Channel.CH2));
                return rval;
            }
        };
        l.add(mid);
        mid = new MathInputDescriptor("POWER FACTOR","") {
            @Override
            public void onChosen() {}
            @Override
            public boolean meterSettingsAreValid() {
                InputDescriptor id0 = (InputDescriptor)getSelectedDescriptor(Channel.CH1);
                InputDescriptor id1 = (InputDescriptor)getSelectedDescriptor(Channel.CH2);
                boolean valid = true;
                valid &= id0.units.equals("A")||id0.units.equals("V");
                valid &= id1.units.equals("V");
                return valid;
            }
            @Override
            public MeterReading calculate() {
                // We use MeterReading.mult to ensure we get the decimals right
                MeterReading rval = MeterReading.mult(getValue(Channel.CH1),getValue(Channel.CH2));
                // Then overload the value
                rval.value = (Float)tree.getValueAt("REAL_PWR")/rval.value;
                rval.units = "";
                return rval;
            }
        };
        l.add(mid);
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
                float delta_c = (float) ThermocoupleHelper.K.voltsToDegC(volts);
                float internal_temp = getValue(Channel.CH2).value;
                MeterReading rval;
                if(Util.getPreference(Util.preference_keys.USE_FAHRENHEIT)) {
                    delta_c = Util.TemperatureUnitsHelper.RelK2F(delta_c);
                    rval = new MeterReading(internal_temp+delta_c,5,2000,"F");
                } else {
                    rval = new MeterReading(internal_temp+delta_c,5,1000,"C");
                }
                return rval;
            }
        };
        l.add(mid);

        // Figure out which input we're presently reading based on the tree state
        determineInputDescriptorIndex(Channel.CH1);
        determineInputDescriptorIndex(Channel.CH2);

        // Stitch together updates on nodes of the config tree with calls to the delegate

        attachCallback("CH1:MAPPING", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                determineInputDescriptorIndex(Channel.CH1);
                delegate.onInputChange(Channel.CH1, getSelectedDescriptor(Channel.CH1));
            }
        });
        attachCallback("CH1:ANALYSIS", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                determineInputDescriptorIndex(Channel.CH1);
                delegate.onInputChange(Channel.CH1, getSelectedDescriptor(Channel.CH1));
            }
        });
        attachCallback("CH2:MAPPING", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                determineInputDescriptorIndex(Channel.CH2);
                delegate.onInputChange(Channel.CH2, getSelectedDescriptor(Channel.CH2));
            }
        });
        attachCallback("CH2:ANALYSIS", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                determineInputDescriptorIndex(Channel.CH2);
                delegate.onInputChange(Channel.CH2, getSelectedDescriptor(Channel.CH2));
            }
        });
        attachCallback("CH1:VALUE",new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                MeterReading val = wrapMeterReading(Channel.CH1,(Float)payload);
                delegate.onSampleReceived(timestamp_utc, Channel.CH1, val);
            }
        });
        attachCallback("CH1:OFFSET",new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                delegate.onOffsetChange(Channel.CH1, wrapMeterReading(Channel.CH1,(Float)payload));
            }
        });
        attachCallback("CH2:VALUE",new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                MeterReading val = wrapMeterReading(Channel.CH2, (Float) payload);
                delegate.onSampleReceived(timestamp_utc, Channel.CH2, val);
        }
        });
        attachCallback("CH2:OFFSET",new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                delegate.onOffsetChange(Channel.CH1, wrapMeterReading(Channel.CH2, (Float) payload));
            }
        });
        attachCallback("CH1:BUF", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                // payload is a byte[] which we must translate in to
                float[] samplebuf = interpretSampleBuffer(Channel.CH1,(byte[])payload);
                float dt = (float)getSampleRateHz();
                dt = (float)1.0/dt;
                delegate.onBufferReceived(timestamp_utc, Channel.CH1, dt, samplebuf);
            }
        });
        attachCallback("CH2:BUF", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                // payload is a byte[] which we must translate in to
                float[] samplebuf = interpretSampleBuffer(Channel.CH2,(byte[])payload);
                float dt = (float)getSampleRateHz();
                dt = (float)1.0/dt;
                delegate.onBufferReceived(timestamp_utc, Channel.CH2, dt, samplebuf);
            }
        });
        attachCallback("REAL_PWR", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                delegate.onSampleReceived(timestamp_utc,Channel.MATH, getValue(Channel.MATH));
            }
        });
        attachCallback("CH1:RANGE_I", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                int i = (Integer)payload;
                delegate.onRangeChange(Channel.CH1, (RangeDescriptor) getSelectedDescriptor(Channel.CH1).ranges.get(i));
            }
        });
        attachCallback("CH2:RANGE_I", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                int i = (Integer)payload;
                delegate.onRangeChange(Channel.CH2, (RangeDescriptor) getSelectedDescriptor(Channel.CH2).ranges.get(i));
            }
        });
        attachCallback("SAMPLING:RATE", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                int i = (Integer)payload;
                delegate.onSampleRateChanged(i, getSampleRateHz());
            }
        });
        attachCallback("SAMPLING:DEPTH", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                int i = (Integer)payload;
                delegate.onBufferDepthChanged(i, getBufferDepth());
            }
        });
        attachCallback("LOG:ON", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                int i = (Integer)payload;
                delegate.onLoggingStatusChanged(i != 0, getLoggingStatus(), getLoggingStatusMessage());
            }
        });
        attachCallback("LOG:STATUS", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                delegate.onLoggingStatusChanged(getLoggingOn(), getLoggingStatus(), getLoggingStatusMessage());
            }
        });
        attachCallback("BAT_V", new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                delegate.onBatteryVoltageReceived((Float) payload);
            }
        });

        // Start a heartbeat.  The Mooshimeter needs to hear from the phone every 20 seconds or it
        // assumes the Android device has fallen in to a phantom connection mode and disconnects itself.
        // We will just read out the PCB version every 10 seconds to satisfy this constraint.
        Util.postDelayed(heartbeat_cb, 1000);

        return rval;
    }

    private Runnable heartbeat_cb = new Runnable() {
        @Override
        public void run() {
            if(!isConnected()) {
                return;
            }
            tree.command("PCB_VERSION");
            Util.postDelayed(heartbeat_cb,10000);
        }
    };

    ////////////////////////////////
    // MooshimeterControlInterface methods
    ////////////////////////////////

    @Override
    public void pause() {
        tree.command("SAMPLING:TRIGGER 0");
    }
    @Override
    public void oneShot() {
        tree.command("SAMPLING:TRIGGER 1");
    }
    @Override
    public void stream() {
        tree.command("SAMPLING:TRIGGER 2");
    }
    @Override
    public void enterShippingMode() {
        tree.command("HIBERNATE 1");
    }
    @Override
    public int getPCBVersion() {
        Object o = tree.getValueAt("PCB_VERSION");
        if(o==null) {
            return 8;
        }
        return (Integer)o;
    }

    @Override
    public double getUTCTime() {
        return (Double)tree.getValueAt("TIME_UTC");
    }
    @Override
    public void setTime(double utc_time) {
        tree.command("TIME_UTC "+(int)Util.getUTCTime());
    }
    @Override
    public MeterReading getOffset(Channel c) {
        return wrapMeterReading(c,(Float)tree.getValueAt(c.name()+":OFFSET"));
    }
    @Override
    public void setOffset(Channel c, float offset) {
        tree.command(c.name()+":OFFSET "+Float.toString(offset));
    }
    @Override
    public boolean bumpRange(Channel channel, boolean expand) {
        ConfigTree.ConfigNode rnode = getInputNode(channel);
        int cnum = (Integer)tree.getValueAt(channel.name()+":RANGE_I");
        int n_choices = rnode.children.size();
        // If we're not wrapping and we're against a wall
        if (cnum == 0 && !expand) {
            return false;
        }
        if(cnum == n_choices-1 && expand) {
            return false;
        }
        cnum += expand?1:-1;
        cnum %= n_choices;
        tree.command(channel.name() + ":RANGE_I " + cnum);
        return true;
    }
    private float getMinRangeForChannel(Channel c) {
        ConfigTree.ConfigNode rnode = getInputNode(c);
        int cnum = (Integer)tree.getValueAt(c.name()+":RANGE_I");
        cnum = cnum>0?cnum-1:cnum;
        ConfigTree.ConfigNode choice = rnode.children.get(cnum);
        return (float)0.9*Float.parseFloat(choice.getShortName());
    }
    private float getMaxRangeForChannel(Channel c) {
        ConfigTree.ConfigNode rnode = getInputNode(c);
        int cnum = (Integer)tree.getValueAt(c.name()+":RANGE_I");
        ConfigTree.ConfigNode choice = rnode.children.get(cnum);
        return (float)1.1*Float.parseFloat(choice.getShortName());
    }
    private boolean applyAutorange(Channel c) {
        if(!range_auto.get(c)) {
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
    @Override
    public boolean applyAutorange() {
        boolean rval = false;
        rval |= applyAutorange(Channel.CH1);
        rval |= applyAutorange(Channel.CH2);
        boolean rms_on = tree.getNode("CH1:ANALYSIS").getChosen().getShortName().equals("RMS")
                ||       tree.getNode("CH2:ANALYSIS").getChosen().getShortName().equals("RMS");
        if(rate_auto) {
            if( rms_on ) {
                if(!tree.getChosenName("SAMPLING:RATE").equals("4000")) {
                    tree.command("SAMPLING:RATE 5");
                }
            } else {
                if(!tree.getChosenName("SAMPLING:RATE").equals("125")) {
                    tree.command("SAMPLING:RATE 0");
                }
            }
        }
        if(depth_auto) {
            if( rms_on ) {
                if(!tree.getChosenName("SAMPLING:DEPTH").equals("256")) {
                    tree.command("SAMPLING:DEPTH 3");
                }
            } else {
                if(!tree.getChosenName("SAMPLING:DEPTH").equals("64")) {
                    tree.command("SAMPLING:DEPTH 1");
                }
            }
        }
        return rval;
    }

    @Override
    public void setName(String name) {
        tree.command("NAME "+name);
    }

    @Override
    public String getName() {
        return (String)tree.getValueAt("NAME");
    }

    private double getEnob(final Channel c) {
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
        final int samplerate_setting = getSampleRateIndex();
        final double buffer_depth_log4 = Math.log(getBufferDepth())/Math.log(4);
        double enob = base_enob_table[ samplerate_setting ];
        // Oversampling adds 1 ENOB per factor of 4
        enob += (buffer_depth_log4);

        if(getPCBVersion()==7 && c == Channel.CH1 && ((InputDescriptor)getSelectedDescriptor(Channel.CH1)).input_node == tree.getNode("CH1:MAPPING:CURRENT") ) {
            // This is compensation for a bug in RevH, where current sense chopper noise dominates
            enob -= 2;
        }

        return enob;
    }
    public String getUnits(Channel c) {
        return getSelectedDescriptor(c).units;
    }
    @Override
    public String getInputLabel(Channel channel) {
        return getSelectedDescriptor(channel).name;
    }
    public int getSampleRateIndex() {
        return (Integer)getValueAt("SAMPLING:RATE");
    }
    @Override
    public int getSampleRateHz() {
        String dstring = tree.getChosenName("SAMPLING:RATE");
        return Integer.parseInt(dstring);
    }
    @Override
    public int setSampleRateIndex(int i) {
        String cmd = "SAMPLING:RATE " + i;
        tree.command(cmd);
        return 0;
    }
    @Override
    public List<String> getSampleRateList() {
        return getChildNameList(tree.getNode("SAMPLING:RATE"));
    }
    @Override
    public int getBufferDepth() {
        String dstring = tree.getChosenName("SAMPLING:DEPTH");
        return Integer.parseInt(dstring);
    }
    @Override
    public int setBufferDepthIndex(int i) {
        String cmd = "SAMPLING:DEPTH " + i;
        tree.command(cmd);
        return 0;
    }
    @Override
    public List<String> getBufferDepthList() {
        return getChildNameList(tree.getNode("SAMPLING:DEPTH"));
    }
    int[] preBufferModeStash = new int[]{0,0};
    @Override
    public void setBufferMode(Channel c, boolean on) {
        String cmd = c.name()+":ANALYSIS";
        if(on) {
            preBufferModeStash[c.ordinal()] = (Integer)tree.getValueAt(cmd);
            cmd += " 2";
        } else {
            cmd += " " + Integer.toString(preBufferModeStash[c.ordinal()]);
        }
        tree.command(cmd);
    }
    @Override
    public boolean getLoggingOn() {
        int i = (Integer)getValueAt("LOG:ON");
        return i!=0;
    }
    @Override
    public void setLoggingOn(boolean on) {
        int i=on?1:0;
        tree.command("LOG:ON "+i);
    }
    @Override
    public String getLoggingStatusMessage() {
        final String messages[] = {
                "OK",
                "NO_MEDIA",
                "MOUNT_FAIL",
                "INSUFFICIENT_SPACE",
                "WRITE_ERROR",
                "END_OF_FILE",
        };
        return messages[getLoggingStatus()];
    }
    @Override
    public void setLoggingInterval(int ms) {
        tree.command("LOG:INTERVAL "+Integer.toString(ms/1000));
    }
    @Override
    public int getLoggingIntervalMS() {
        return 1000*(Integer)getValueAt("LOG:INTERVAL");
    }
    @Override
    public MeterReading getValue(Channel c) {
        switch(c) {
            case CH1:
            case CH2:
                return wrapMeterReading(c, (Float) tree.getValueAt(c.name() + ":VALUE"));
            case MATH:
                MathInputDescriptor id = (MathInputDescriptor)input_descriptors.get(Channel.MATH).getChosen();
                if(id.meterSettingsAreValid()) {
                    return id.calculate();
                } else {
                    MeterReading rval = new MeterReading(0,0,0,"INVALID INPUTS");
                    return rval;
                }
        }
        return new MeterReading();
    }
    @Override
    public int getLoggingStatus() {
        return (Integer)getValueAt("LOG:STATUS");
    }
    @Override
    public String getRangeLabel(Channel c) {
        InputDescriptor id = (InputDescriptor)getSelectedDescriptor(c);
        int range_i = (Integer)tree.getValueAt(c.name() + ":RANGE_I");
        // FIXME: This is borking because our internal descriptor structures are out of sync with the configtree updates
        RangeDescriptor rd =(RangeDescriptor)id.ranges.get(range_i);
        return rd.name;
    }
    @Override
    public List<String> getRangeList(Channel c) {
        return Util.stringifyCollection(getSelectedDescriptor(c).ranges.getChoices());
    }
    @Override
    public int setRange(Channel c, MooshimeterDeviceBase.RangeDescriptor r) {
        getSelectedDescriptor(c).ranges.choose(r);
        tree.command(c.name() + ":RANGE_I " + getSelectedDescriptor(c).ranges.getChosenI());
        return 0;
    }
    @Override
    public int setInput(Channel c, MooshimeterDeviceBase.InputDescriptor descriptor) {
        Chooser<MooshimeterDeviceBase.InputDescriptor> chooser = input_descriptors.get(c);
        if(chooser.isChosen(descriptor)) {
            // No action required
            return 0;
        }
        switch(c) {
            case CH1:
            case CH2:
                InputDescriptor cast = (InputDescriptor)descriptor;
                if(cast.shared_node!=null) {
                    // Make sure we're not about to jump on to a channel that's in use
                    Channel other = c==Channel.CH1?Channel.CH2:Channel.CH1;
                    InputDescriptor other_id = (InputDescriptor)input_descriptors.get(other).getChosen();
                    if(other_id.shared_node!=null) {
                        Log.e(TAG, "Tried to select an input already in use!");
                        return -1;
                    }
                }

                chooser.choose(cast);
                // Reset range manually... probably a cleaner way to do this
                tree.getNode(c.name()+":RANGE_I").setValue(0);

                cast.input_node.choose();
                if(cast.shared_node!=null) {
                    cast.shared_node.choose();
                }
                cast.analysis_node.choose();
                break;
            case MATH:
                MathInputDescriptor mcast = (MathInputDescriptor)descriptor;
                mcast.onChosen();
                chooser.choose(mcast);
                break;
        }
        return 0;
    }
    @Override
    public List<MooshimeterDeviceBase.InputDescriptor> getInputList(Channel c) {
        return input_descriptors.get(c).getChoices();
    }
    @Override
    public MooshimeterDeviceBase.InputDescriptor getSelectedDescriptor(final Channel channel) {
        return input_descriptors.get(channel).getChosen();
    }
}
