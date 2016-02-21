package com.mooshim.mooshimeter.common;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.DataFormatException;

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
    // MEMBERS FOR TRACKING SERIALIZATION AND CONFIG TREE INTERACTIONS
    ////////////////////////////////

    private ConfigTree tree = null;

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
    private static final Map<String, String> units_map = makeUnitsMap();
    private static Map<String, String> makeUnitsMap() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("CURRENT", "A");
        m.put("VOLTAGE", "V");
        m.put("SHORT", "CNT");
        m.put("TEMP", "C");
        m.put("AUX_V", "V");
        m.put("RESISTANCE", "OHM");
        m.put("DIODE", "V");
        m = Collections.unmodifiableMap(m);
        return m;
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

    ////////////////////////////////
    // Private helpers
    ////////////////////////////////

    private ConfigTree.ConfigNode getInputNode(int channel) {
        assert channel<2;
        return input_descriptors[channel].get(input_descriptors_indices[channel]).input_node;
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

    @Override
    public int discover() {
        int rval = super.discover();
        if(rval!=0) {
            return rval;
        }
        tree.attach(this,mUUID.METER_SERIN,mUUID.METER_SEROUT);

        tree.refreshAll();

        // Now we need to process the tree to generate our abbreviations
        // Wrap inputs so we can access from inner class multiple times
        final List<InputDescriptor> inputs[] = new List[]{null};

        class localNodeProcessor extends ConfigTree.NodeProcessor {
            private ConfigTree.ConfigNode shared = null;
            @Override
            public void process(ConfigTree.ConfigNode n) {
                ConfigTree.ConfigNode a = n.getChildByName("ANALYSIS");
                if(a!=null) {
                    // This is an input node, generate a new InputDescriptor
                    // We only generate for AC and DC
                    ConfigTree.ConfigNode dc = a.getChildByName("MEAN");
                    ConfigTree.ConfigNode ac = a.getChildByName("RMS");
                    if(dc != null) {
                        InputDescriptor id = new InputDescriptor();
                        id.name = n.getShortName() + (ac!=null?" DC":"");
                        id.input_node = n;
                        id.analysis_node = dc;
                        if(units_map.containsKey(n.getShortName())) {
                            id.units = units_map.get(n.getShortName());
                        } else {
                            // We don't know what the units should be here...
                            id.units = "";
                        }
                        if(shared!=null) {
                            id.shared_node = shared;
                        }
                        addRangeDescriptors(id, n.getChildByName("RANGE"));
                        inputs[0].add(id);
                    }
                    if(ac != null) {
                        InputDescriptor id = new InputDescriptor();
                        id.name = n.getShortName() + " AC";
                        id.input_node = n;
                        id.analysis_node = ac;
                        if(units_map.containsKey(n.getShortName())) {
                            id.units = units_map.get(n.getShortName());
                        } else {
                            // We don't know what the units should be here...
                            id.units = "";
                        }
                        if(shared!=null) {
                            id.shared_node = shared;
                        }
                        addRangeDescriptors(id, n.getChildByName("RANGE"));
                        inputs[0].add(id);
                    }
                }
                // If this is a link node, follow the link
                if(n.ntype== ConfigTree.NTYPE.LINK) {
                    // TODO: God this is ugly.  Please forgive me.
                    shared = n;
                    tree.walk(tree.getNodeAtLongname(((ConfigTree.RefNode)n).path),this);
                }
            }
        }
        inputs[0] = input_descriptors[0];
        tree.walk(tree.getNodeAtLongname("CH1"),new localNodeProcessor());
        inputs[0] = input_descriptors[1];
        tree.walk(tree.getNodeAtLongname("CH2"),new localNodeProcessor());

        // Figure out which input we're presently reading based on the tree state
        //input_descriptors_indices[0] =

        return rval;
    }

    @Override
    public int getBufLen() {
        String dstring = tree.getChosenName("SAMPLING:DEPTH");
        return Integer.parseInt(dstring);
    }

    @Override
    public void getBuffer(NotifyHandler onReceived) {

    }

    @Override
    public void pauseStream() {
        // Sampling off
        tree.command("SAMPLING:TRIGGER 0");
    }

    @Override
    public void playSampleStream(final NotifyHandler ch1_notify, final NotifyHandler ch2_notify) {
        tree.getNodeAtLongname("CH1:VALUE").clearNotifyHandlers();
        tree.getNodeAtLongname("CH2:VALUE").clearNotifyHandlers();
        tree.getNodeAtLongname("CH1:VALUE").addNotifyHandler(ch1_notify);
        tree.getNodeAtLongname("CH2:VALUE").addNotifyHandler(ch2_notify);
        // Sampling Continuous
        tree.command("SAMPLING:TRIGGER 2");
    }

    @Override
    public boolean isStreaming() {
        return tree.getChosenName("SAMPLING:TRIGGER").equals("CONTINUOUS");
    }

    @Override
    public boolean bumpRange(int channel, boolean expand, boolean wrap) {
        ConfigTree.ConfigNode rnode = getInputNode(channel).getChildByName("RANGE");
        int cnum = (Integer)rnode.getValue();
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
        rnode.children.get(cnum).choose();
        return true;
    }

    private float getMinRangeForChannel(int c) {
        ConfigTree.ConfigNode rnode = getInputNode(c).getChildByName("RANGE");
        int cnum = (Integer)rnode.getValue();
        cnum = cnum>0?cnum-1:cnum;
        ConfigTree.ConfigNode choice = rnode.children.get(cnum);
        return (float)0.9 * Float.parseFloat(choice.getShortName());
    }

    private float getMaxRangeForChannel(int c) {
        ConfigTree.ConfigNode rnode = getInputNode(c).getChildByName("RANGE");
        ConfigTree.ConfigNode choice = rnode.children.get((Integer)rnode.getValue());
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
        if(rate_auto) {
        }
        if(depth_auto) {
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
    public String getDescriptor(int channel) {
        return input_descriptors[channel].get(input_descriptors_indices[channel]).name;
    }
    @Override
    public String getUnits(int channel) {
        // This one we will have to do manually
        String name = getInputLabel(channel);
        if(units_map.containsKey(name)) {
            return units_map.get(name);
        }
        return "NONE";
    }
    @Override
    public String getInputLabel(int channel) {
        // TODO: Clean this up
        return getDescriptor(channel);
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
        return getChildNameList(tree.getNodeAtLongname("SAMPLING:RATE"));
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
        return getChildNameList(tree.getNodeAtLongname("SAMPLING:DEPTH"));
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
    public int getLoggingStatus() {
        return (Integer)getValueAt("LOG:STATUS");
    }

    @Override
    public String getRangeLabel(int c) {
        InputDescriptor id = input_descriptors[c].get(input_descriptors_indices[c]);
        int range_i = (Integer)getInputNode(c).getChildByName("RANGE").getValue();
        // FIXME: This is borking because our internal descriptor structures are out of sync with the configtree updates
        RangeDescriptor rd =id.ranges.get(range_i);
        return rd.name;
    }

    @Override
    public List<String> getRangeList(int c) {
        InputDescriptor id = input_descriptors[c].get(input_descriptors_indices[c]);
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
}
