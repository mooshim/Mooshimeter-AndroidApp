package com.mooshim.mooshimeter.common;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.text.InputType;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
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

    private int send_seq_n = 0;
    private int recv_seq_n = 0;
    private ConfigTree tree = null;
    private Map<Integer,ConfigTree.ConfigNode> code_list = null;
    private List<Byte> recv_buf = new ArrayList<Byte>();

    ////////////////////////////////
    // MEMBERS FOR TRACKING AVAILABLE INPUTS AND RANGES
    ////////////////////////////////

    class RangeDescriptor {
        public String name;
        public CommandSequence cmd = new CommandSequence();
        public float max;
    }

    class InputDescriptor {
        public String name;
        public CommandSequence cmd = new CommandSequence();
        public List<RangeDescriptor> ranges = new ArrayList<RangeDescriptor>();
        public String units;
    }

    final List<InputDescriptor> input_descriptors[];
    final int input_descriptors_indices[] = new int[]{0,0};

    ////////////////////////////////
    // NOTIFICATION CALLBACKS
    ////////////////////////////////

    private void interpretAggregate() {
        int expecting_bytes;
        while(recv_buf.size()>0) {
            // FIXME: Can't find a nice way of wrapping a ByteBuffer around a list of bytes.
            // Create a new array and copy bytes over.
            byte[] bytes= new byte[recv_buf.size()];
            ByteBuffer b = MooshimeterDeviceBase.wrap(bytes);
            for(byte t:recv_buf) {
                b.put(t);
            }
            b.rewind();
            int opcode = (int)b.get();
            if(code_list.containsKey(opcode)) {
                ConfigTree.ConfigNode n = code_list.get(opcode);
                switch(n.ntype) {
                    case ConfigTree.NTYPE.PLAIN  :
                        Log.e(TAG, "Shouldn't receive notification here!");
                        return;
                    case ConfigTree.NTYPE.CHOOSER:
                        n.notify((int)b.get());
                        break;
                    case ConfigTree.NTYPE.LINK   :
                        Log.e(TAG, "Shouldn't receive notification here!");
                        return;
                    case ConfigTree.NTYPE.COPY   :
                        Log.e(TAG, "Shouldn't receive notification here!");
                        return;
                    case ConfigTree.NTYPE.VAL_U8 :
                    case ConfigTree.NTYPE.VAL_S8 :
                        n.notify((int)b.get());
                        break;
                    case ConfigTree.NTYPE.VAL_U16:
                    case ConfigTree.NTYPE.VAL_S16:
                        n.notify((int)b.getShort());
                        break;
                    case ConfigTree.NTYPE.VAL_U32:
                    case ConfigTree.NTYPE.VAL_S32:
                        n.notify((int)b.getInt());
                        break;
                    case ConfigTree.NTYPE.VAL_STR:
                        expecting_bytes = b.getShort();
                        if(b.remaining()<expecting_bytes) {
                            // Wait for the aggregator to fill up more
                            return;
                        }
                        //n.notify(new String(b.array()));
                        break;
                    case ConfigTree.NTYPE.VAL_BIN:
                        expecting_bytes = b.getShort();
                        if(b.remaining()<expecting_bytes) {
                            // Wait for the aggregator to fill up more
                            return;
                        }
                        n.notify(Arrays.copyOfRange(b.array(),b.position(),b.position()+expecting_bytes));
                        b.position(b.position()+expecting_bytes);
                        break;
                    case ConfigTree.NTYPE.VAL_FLT:
                        n.notify(b.getFloat());
                        break;
                }
            } else {
                Log.e(TAG,"UNRECOGNIZED SHORTCODE "+opcode);
                new Exception().printStackTrace();
                return;
            }
            // Advance recv_buf
            for(int i = 0; i < b.position();i++) {
                recv_buf.remove(0);
            }
        }
    }

    private NotifyHandler serout_callback = new NotifyHandler() {
        @Override
        public void onReceived(double timestamp_utc, Object payload) {
            byte[] bytes = (byte[])payload;
            int seq_n = (int)bytes[0];
            if(seq_n<0){
                // Because java is stupid
                seq_n+=0x100;
            }
            if(seq_n != (recv_seq_n+1)%0x100) {
                Log.e(TAG,"OUT OF ORDER PACKET");
                Log.e(TAG,"EXPECTED: "+((recv_seq_n+1)%0x100));
                Log.e(TAG,"GOT:      "+(seq_n));
            } else {
                Log.d(TAG, "RECV: " + seq_n + " " + bytes.length + " bytes");
            }
            recv_seq_n = seq_n;
            // Append to aggregate buffer
            for(int i = 1; i < bytes.length; i++) {
                recv_buf.add(bytes[i]);
            }
            interpretAggregate();
        }
    };

    ////////////////////////////////
    // Private methods for dealing with config tree
    ////////////////////////////////

    class CommandSequence {
        public List<byte[]> commands = new ArrayList<byte[]>();
        public void add(String cmd) {
            byte[] bc = tree.getByteCmdForString(cmd);
            if(bc!=null && bc.length>0) {
                commands.add(bc);
            }
        }
    }

    public void executeSequence(CommandSequence seq) {
        for(byte[] b:seq.commands) {
            sendToMeter(b);
        }
    }

    private void sendToMeter(byte[] payload) {
        if (payload.length > 19) {
            Log.e(TAG, "Payload too long!");
            new Exception().printStackTrace();
            return;
        }
        byte[] buf = new byte[payload.length + 1];
        buf[0] = (byte) send_seq_n;
        send_seq_n++;
        send_seq_n &= 0xFF;
        System.arraycopy(payload, 0, buf, 1, payload.length);
        send(mUUID.METER_SERIN, buf);
    }

    public void sendCommand(String cmd) {
        sendToMeter(tree.getByteCmdForString(cmd));
    }

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
            rd.cmd.add(r.getChoiceString());
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

    int loadTree() {
        final StatLockManager lock = new StatLockManager(new ReentrantLock(true));
        lock.l();
        NotifyHandler h = new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                lock.l();
                lock.sig();
                lock.ul();
            }
        };
        ConfigTree.ConfigNode n = tree.getNodeAtLongname("ADMIN:TREE");
        n.addNotifyHandler(h);
        sendCommand("ADMIN:TREE");
        int rval = 0; // 0=success
        if (lock.await()) {
            // FAIL!
            Log.e(TAG,"Tree load failed");
            rval=-1;
        }
        lock.ul();
        n.removeNotifyHandler(h);

        // Now we need to process the tree to generate our abbreviations
        // Wrap inputs so we can access from inner class multiple times
        final List<InputDescriptor> inputs[] = new List[]{null};

        ConfigTree.NodeProcessor p = new ConfigTree.NodeProcessor() {
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
                        id.cmd.add(n.getChoiceString());  // Select the input
                        id.cmd.add(dc.getChoiceString()); // Select the analysis option
                        if(units_map.containsKey(n.getShortName())) {
                            id.units = units_map.get(n.getShortName());
                        } else {
                            // We don't know what the units should be here...
                            id.units = "";
                        }
                        addRangeDescriptors(id, n.getChildByName("RANGE"));
                        inputs[0].add(id);
                    }
                    if(ac != null) {
                        InputDescriptor id = new InputDescriptor();
                        id.name = n.getShortName() + " AC";
                        id.cmd.add(n.getChoiceString());  // Select the input
                        id.cmd.add(ac.getChoiceString()); // Select the analysis option
                        if(units_map.containsKey(n.getShortName())) {
                            id.units = units_map.get(n.getShortName());
                        } else {
                            // We don't know what the units should be here...
                            id.units = "";
                        }
                        addRangeDescriptors(id, n.getChildByName("RANGE"));
                        inputs[0].add(id);
                    }
                }
                // If this is a link node, follow the link
                if(n.ntype== ConfigTree.NTYPE.LINK) {
                    // TODO: God this is ugly.  Please forgive me.
                    tree.walk(tree.getNodeAtLongname(((ConfigTree.RefNode)n).path),this);
                }
            }
        };

        inputs[0] = input_descriptors[0];
        tree.walk(tree.getNodeAtLongname("CH1"),p);
        inputs[0] = input_descriptors[1];
        tree.walk(tree.getNodeAtLongname("CH2"),p);

        return rval;
    }

    ////////////////////////////////
    // METHODS
    ////////////////////////////////

    public MooshimeterDevice(BluetoothDevice device, Context context) {
        super(device, context);
        ConfigTree.ConfigNode root = new ConfigTree.ConfigNode(ConfigTree.NTYPE.PLAIN,"",Arrays.asList(new ConfigTree.ConfigNode[] {
                new ConfigTree.StructuralNode(ConfigTree.NTYPE.PLAIN,"ADMIN", Arrays.asList(new ConfigTree.ConfigNode[] {
                        new ConfigTree.ValueNode(ConfigTree.NTYPE.VAL_U32,"CRC32",null),
                        new ConfigTree.ValueNode(ConfigTree.NTYPE.VAL_BIN,"TREE",null),
                        new ConfigTree.ValueNode(ConfigTree.NTYPE.VAL_STR,"DIAGNOSTIC",null),
                })),
        }));
        tree = new ConfigTree(root);
        tree.assignShortCodes();
        code_list = tree.getShortCodeList();
        ConfigTree.ConfigNode tree_bin = tree.getNodeAtLongname("ADMIN:TREE");

        tree_bin.addNotifyHandler(new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                try {
                    tree.unpack((byte[]) payload);
                    code_list = tree.getShortCodeList();
                    tree.enumerate();
                } catch (DataFormatException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        input_descriptors = new List[2];
        input_descriptors[0] = new ArrayList<InputDescriptor>();
        input_descriptors[1] = new ArrayList<InputDescriptor>();
    }

    ////////////////////////////////
    // Private helpers
    ////////////////////////////////

    private int cycleChoiceAt(String chooser_name) {
        int choice_i = (Integer)tree.getValueAt(chooser_name);
        choice_i++;
        choice_i %= tree.getNodeAtLongname(chooser_name).children.size();
        sendCommand(chooser_name + " " + choice_i);
        return choice_i;
    }

    private ConfigTree.ConfigNode getInputNode(int channel) {
        assert channel<2;
        String channel_str = (channel==0?"CH1":"CH2");
        ConfigTree.ConfigNode mapnode = tree.getChosenNode(channel_str+":MAPPING");
        // Follow link
        while(mapnode.ntype== ConfigTree.NTYPE.LINK) {
            mapnode = tree.getChosenNode(((ConfigTree.RefNode)mapnode).path);
        }
        return mapnode;
    }

    private static String getChString(int channel) {
        return (channel==0?"CH1":"CH2");
    }

    private int refreshValueAt(ConfigTree.ConfigNode n) {
        String p = n.getLongName();
        return refreshValueAt(p);
    }

    private int refreshValueAt(String p) {
        int rval=0;
        final StatLockManager lock = new StatLockManager(new ReentrantLock(true));
        lock.l();
        NotifyHandler h = new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                lock.l();
                lock.sig();
                lock.ul();
            }
        };
        ConfigTree.ConfigNode n = tree.getNodeAtLongname(p);
        if(n==null) {
            return -1;
        }
        n.addNotifyHandler(h);
        sendCommand(p);
        //if (lock.awaitMilli(5000)) {
        if (lock.await()) {
            // FAIL!
            Log.e(TAG,"Failed to refresh value!");
            rval=-1;
        }
        lock.ul();
        n.removeNotifyHandler(h);
        return rval;
    }

    private Object getValueAt(ConfigTree.ConfigNode n) {
        return getValueAt(n.getLongName());
    }

    private Object getValueAt(String p) {
        Object rval = tree.getValueAt(p);
        if(rval==null) {
            // FIXME: hack
            return 0;
            //refreshValueAt(p);
            //rval = tree.getValueAt(p);
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
        enableNotify(mUUID.METER_SEROUT, true, serout_callback);
        rval = loadTree();
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
        sendCommand("SAMPLING:TRIGGER 0");
    }

    @Override
    public void playSampleStream(final NotifyHandler ch1_notify, final NotifyHandler ch2_notify) {
        tree.getNodeAtLongname("CH1:VALUE").clearNotifyHandlers();
        tree.getNodeAtLongname("CH2:VALUE").clearNotifyHandlers();
        tree.getNodeAtLongname("CH1:VALUE").addNotifyHandler(ch1_notify);
        tree.getNodeAtLongname("CH2:VALUE").addNotifyHandler(ch2_notify);
        // Sampling Continuous
        sendCommand("SAMPLING:TRIGGER 2");
    }

    @Override
    public boolean isStreaming() {
        return tree.getChosenName("SAMPLING:TRIGGER").equals("CONTINUOUS");
    }

    @Override
    public void bumpRange(int channel, boolean expand, boolean wrap) {

    }

    private float getMinRangeForChannel(int c) {
        ConfigTree.ConfigNode rnode = getInputNode(c).getChildByName("RANGE");
        int cnum = (Integer)rnode.last_value;
        cnum = cnum>0?cnum-1:cnum;
        ConfigTree.ConfigNode choice = rnode.children.get(cnum);
        return (float)1.1 * Float.parseFloat(choice.getShortName());
    }

    private float getMaxRangeForChannel(int c) {
        ConfigTree.ConfigNode rnode = getInputNode(c).getChildByName("RANGE");
        ConfigTree.ConfigNode choice = rnode.children.get((Integer)rnode.last_value);
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
        if(val > max) {
            bumpRange(0,true,false);
            return true;
        }
        if(val < min) {
            bumpRange(0,false,false);
            return true;
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
        sendCommand(cmd);
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
        return (Integer)getValueAt("SAMPLING:DEPTH ");
    }

    @Override
    public int setBufferDepthIndex(int i) {
        String cmd = "SAMPLING:DEPTH " + i;
        sendCommand(cmd);
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
        sendCommand("LOG:ON "+i);
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
        executeSequence(rd.cmd);
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
        executeSequence(inputDescriptor.cmd);
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
