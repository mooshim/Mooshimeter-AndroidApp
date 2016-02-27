package com.mooshim.mooshimeter.common;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        public final static UUID
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

    class RangeDescriptor {
        public String name;
        public float max;
        public ConfigTree.ConfigNode node;
    }

    class InputDescriptor {
        public String name;
        public List<RangeDescriptor> ranges = new ArrayList<RangeDescriptor>();
        public String units;
        public ConfigTree.ConfigNode input_node;
        public ConfigTree.ConfigNode analysis_node;
        public ConfigTree.ConfigNode shared_node; // ugly
    }

    final List<InputDescriptor> input_descriptors[];
    final int input_descriptors_indices[] = new int[]{0,0};

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
        return String.format("%s%s",cleanFloatFmt(max),prefixes[prefix_i]);
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

    public MooshimeterDevice(BluetoothDevice device, Context context) {
        super(device, context);
        tree = new ConfigTree();
        input_descriptors = new List[2];
        input_descriptors[0] = new ArrayList<InputDescriptor>();
        input_descriptors[1] = new ArrayList<InputDescriptor>();
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

    private ConfigTree.ConfigNode getInputNode(int channel) {
        assert channel<2;
        ConfigTree.ConfigNode rval = tree.getNode(getChString(channel) + ":MAPPING");
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
    private static String getChString(int channel) {
        return (channel==0?"CH1":"CH2");
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

    ////////////////////////////////
    // MooshimeterBaseDevice methods
    ////////////////////////////////

    private InputDescriptor getSelectedDescriptor(final int channel) {
        return input_descriptors[channel].get(input_descriptors_indices[channel]);
    }

    InputDescriptor makeInputDescriptor(int c, String name, boolean analysis_in_name, String analysis, String units, boolean shared) {
        InputDescriptor i = new InputDescriptor();
        i.analysis_node = tree.getNode(getChString(c)+":ANALYSIS:"+analysis);
        i.name          = name;
        if(analysis_in_name) {
            if(analysis.equals("MEAN")) {
                i.name += " DC";
            } else {
                i.name += " AC";
            }
        }
        i.units         = units;
        if(shared) {
            i.shared_node   = tree.getNode(getChString(c)+":MAPPING:SHARED");
            i.input_node    = tree.getNode("SHARED:"+name);
        } else {
            i.input_node    = tree.getNode(getChString(c)+":MAPPING:"+name);
        }
        addRangeDescriptors(i,i.input_node);
        return i;
    }

    @Override
    public int discover() {
        int rval = super.discover();
        if(rval!=0) {
            return rval;
        }
        tree.attach(this, mUUID.METER_SERIN, mUUID.METER_SEROUT);

        tree.refreshAll();

        input_descriptors[0].add(makeInputDescriptor(0,"CURRENT"   ,true, "MEAN","A"  ,false));
        input_descriptors[0].add(makeInputDescriptor(0,"CURRENT"   ,true, "RMS" ,"A"  ,false));
        input_descriptors[0].add(makeInputDescriptor(0,"TEMP"      ,false,"MEAN","K"  ,false));
        input_descriptors[0].add(makeInputDescriptor(0,"AUX_V"     ,true, "MEAN","V"  ,true));
        input_descriptors[0].add(makeInputDescriptor(0,"AUX_V"     ,true, "RMS" ,"V"  ,true));
        input_descriptors[0].add(makeInputDescriptor(0,"RESISTANCE",false,"MEAN","Ohm",true));
        input_descriptors[0].add(makeInputDescriptor(0,"DIODE"     ,false,"MEAN","V"  ,true));

        input_descriptors[1].add(makeInputDescriptor(1,"VOLTAGE"   ,true, "MEAN","V"  ,false));
        input_descriptors[1].add(makeInputDescriptor(1,"VOLTAGE"   ,true, "RMS", "V"  ,false));
        input_descriptors[1].add(makeInputDescriptor(1,"TEMP"      ,false,"MEAN","K"  ,false));
        input_descriptors[1].add(makeInputDescriptor(1,"AUX_V"     ,true ,"MEAN","V"  ,true));
        input_descriptors[1].add(makeInputDescriptor(1,"AUX_V"     ,true, "RMS", "V"  ,true));
        input_descriptors[1].add(makeInputDescriptor(1,"RESISTANCE",false,"MEAN","Ohm",true));
        input_descriptors[1].add(makeInputDescriptor(1,"DIODE"     ,false,"MEAN","V"  ,true));

        // Figure out which input we're presently reading based on the tree state
        for(InputDescriptor d:input_descriptors[0]) {
            if(getInputNode(0) == d.input_node) {
                if(d.analysis_node == tree.getNode("CH1:ANALYSIS").getChosen()) {
                    input_descriptors_indices[0] = input_descriptors[0].indexOf(d);
                }
            }
        }
        for(InputDescriptor d:input_descriptors[1]) {
            if(getInputNode(1) == d.input_node) {
                if(d.analysis_node == tree.getNode("CH2:ANALYSIS").getChosen()) {
                    input_descriptors_indices[1] = input_descriptors[1].indexOf(d);
                }
            }
        }
        return rval;
    }

    @Override
    public void pauseStream() {
        // Sampling off
        tree.command("SAMPLING:TRIGGER 0");
    }
    @Override
    public void playSampleStream(final NotifyHandler ch1_notify, final NotifyHandler ch2_notify) {
        tree.getNode("CH1:VALUE").clearNotifyHandlers();
        tree.getNode("CH2:VALUE").clearNotifyHandlers();
        tree.getNode("CH1:VALUE").addNotifyHandler(ch1_notify);
        tree.getNode("CH2:VALUE").addNotifyHandler(ch2_notify);
        // Sampling Continuous
        tree.command("SAMPLING:TRIGGER 2");
    }
    @Override
    public boolean isStreaming() {
        return tree.getChosenName("SAMPLING:TRIGGER").equals("CONTINUOUS");
    }
    @Override
    public boolean bumpRange(int channel, boolean expand, boolean wrap) {
        ConfigTree.ConfigNode rnode = getInputNode(channel);
        int cnum = (Integer)tree.getNode(getChString(channel)+":RANGE_I").getValue();
        int n_choices = rnode.children.size();
        if(!wrap) {
            // If we're not wrapping and we're against a wall
            if (cnum == 0 && !expand) {
                return false;
            }
            if(cnum == n_choices-1 && expand) {
                return false;
            }
        }
        cnum += expand?1:-1;
        cnum %= n_choices;
        tree.command(getChString(channel) + ":RANGE_I " + cnum);
        return true;
    }
    private float getMinRangeForChannel(int c) {
        ConfigTree.ConfigNode rnode = getInputNode(c);
        int cnum = (Integer)tree.getNode(getChString(c)+":RANGE_I").getValue();
        cnum = cnum>0?cnum-1:cnum;
        ConfigTree.ConfigNode choice = rnode.children.get(cnum);
        return (float)0.9 * Float.parseFloat(choice.getShortName());
    }
    private float getMaxRangeForChannel(int c) {
        ConfigTree.ConfigNode rnode = getInputNode(c);
        int cnum = (Integer)tree.getNode(getChString(c)+":RANGE_I").getValue();
        ConfigTree.ConfigNode choice = rnode.children.get(cnum);
        return Float.parseFloat(choice.getShortName());
    }
    private boolean applyAutorange(int c) {
        if(!range_auto[c]) {
            return false;
        }
        float max = getMaxRangeForChannel(c);
        float min = getMinRangeForChannel(c);
        String s = getChString(c)+":VALUE";
        float val = (Float)getValueAt(s);
        val = val<0?-val:val; //abs
        if(val > max) {
            return bumpRange(c,true,false);
        }
        if(val < min) {
            return bumpRange(c,false,false);
        }
        return false;
    }
    @Override
    public boolean applyAutorange() {
        boolean rval = false;
        rval |= applyAutorange(0);
        rval |= applyAutorange(1);
        boolean rms_on = tree.getNode(getChString(0)+":ANALYSIS").getChosen().getShortName().equals("RMS")
                ||tree.getNode(getChString(1)+":ANALYSIS").getChosen().getShortName().equals("RMS");
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
        final int samplerate_setting = getSampleRateIndex();
        final double buffer_depth_log4 = Math.log(getBufferDepth())/Math.log(4);
        double enob = base_enob_table[ samplerate_setting ];
        // Oversampling adds 1 ENOB per factor of 4
        enob += (buffer_depth_log4);
        return enob;
    }
    @Override
    public SignificantDigits getSigDigits(int channel) {
        SignificantDigits rval = new SignificantDigits();
        float max = getMaxRangeForChannel(channel);
        final double enob = getEnob(channel);

        rval.high     = (int)Math.log10(max);
        rval.n_digits = (int)Math.log10(Math.pow(2.0, enob));
        return rval;
    }
    @Override
    public String getUnits(int channel) {
        return getSelectedDescriptor(channel).units;
    }
    @Override
    public String getInputLabel(int channel) {
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
    public List<String> getSampleRateListHz() {
        return getChildNameList(tree.getNode("SAMPLING:RATE"));
    }
    @Override
    public int getBufferDepth() {
        String dstring = tree.getChosenName("SAMPLING:DEPTH");
        return Integer.parseInt(dstring);
    }
    public int getBufferDepthIndex() {
        return (Integer)getValueAt("SAMPLING:DEPTH");
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
    public int getLoggingStatus() {
        return (Integer)getValueAt("LOG:STATUS");
    }
    @Override
    public String getRangeLabel(int c) {
        InputDescriptor id = getSelectedDescriptor(c);
        int range_i = (Integer)tree.getNode(getChString(c) + ":RANGE_I").getValue();
        // FIXME: This is borking because our internal descriptor structures are out of sync with the configtree updates
        RangeDescriptor rd =id.ranges.get(range_i);
        return rd.name;
    }
    @Override
    public List<String> getRangeList(int c) {
        InputDescriptor id = getSelectedDescriptor(c);
        List<String> rval = new ArrayList<String>();
        for(RangeDescriptor rd:id.ranges) {
            rval.add(rd.name);
        }
        return rval;
    }
    @Override
    public int setRangeIndex(int c, int r) {
        InputDescriptor id = input_descriptors[c].get(input_descriptors_indices[c]);
        RangeDescriptor rd =id.ranges.get(r);
        rd.node.choose();
        return 0;
    }
    @Override
    public String getValueLabel(int c) {
        String value_str = getChString(c)+":VALUE";
        Object d = getValueAt(value_str);
        if(d==null) {
            Log.e(TAG,"WHAT");
            return "FAIL";
        }
        SignificantDigits digits = getSigDigits(c);
        return formatReading((Float) d, digits);
    }
    @Override
    public int getInputIndex(int c) {
        return input_descriptors_indices[c];
    }
    @Override
    public int setInputIndex(int c, int mapping) {
        if(input_descriptors_indices[c] == mapping) {
            // No action required
            return 0;
        }
        input_descriptors_indices[c] = mapping;
        InputDescriptor inputDescriptor = input_descriptors[c].get(mapping);
        inputDescriptor.input_node.choose();
        if(inputDescriptor.shared_node!=null) {
            inputDescriptor.shared_node.choose();
        }
        inputDescriptor.analysis_node.choose();
        return 0;
    }
    @Override
    public List<String> getInputList(int c) {
        List<String> rval = new ArrayList<String>();
        for(InputDescriptor d:input_descriptors[c]) {
            rval.add(d.name);
        }
        return rval;
    }

    @Override
    public float getRealPower() {
        if(getSelectedDescriptor(0).units.equals("A") && getSelectedDescriptor(1).units.equals("V")) {
            return getPowerFactor()*getApparentPower();
        }
        return(0/0);
    }

    @Override
    public float getApparentPower() {
        if(getSelectedDescriptor(0).units.equals("A") && getSelectedDescriptor(1).units.equals("V")) {
            float ch1 = (Float)getValueAt("CH1:VALUE");
            float ch2 = (Float)getValueAt("CH2:VALUE");
            return ch1*ch2; // Return watts
        }
        return(0/0);
    }

    @Override
    public float getPowerFactor() {
        if(getSelectedDescriptor(0).units.equals("A") && getSelectedDescriptor(1).units.equals("V")) {
            return (Float)getValueAt("PWR_FACTOR");
        }
        return (0/0);
    }

    @Override
    public float getKTypeThermoTemp() {
        return 0;
    }
}
