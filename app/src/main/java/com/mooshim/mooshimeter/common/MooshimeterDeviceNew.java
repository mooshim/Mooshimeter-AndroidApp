package com.mooshim.mooshimeter.common;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.DataFormatException;

import static java.util.UUID.fromString;

public class MooshimeterDeviceNew extends MooshimeterDeviceBase{


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
    // MEMBERS
    ////////////////////////////////

    int send_seq_n = 0;
    int recv_seq_n = 0;
    ConfigTree tree = null;
    Map<Integer,ConfigTree.ConfigNode> code_list = null;
    List<Byte> recv_buf = new ArrayList<Byte>();

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
                        n.notify(b.get());
                        break;
                    case ConfigTree.NTYPE.LINK   :
                        Log.e(TAG, "Shouldn't receive notification here!");
                        return;
                    case ConfigTree.NTYPE.COPY   :
                        Log.e(TAG, "Shouldn't receive notification here!");
                        return;
                    case ConfigTree.NTYPE.VAL_U8 :
                    case ConfigTree.NTYPE.VAL_S8 :
                        n.notify(b.get());
                        break;
                    case ConfigTree.NTYPE.VAL_U16:
                    case ConfigTree.NTYPE.VAL_S16:
                        n.notify(b.getShort());
                        break;
                    case ConfigTree.NTYPE.VAL_U32:
                    case ConfigTree.NTYPE.VAL_S32:
                        n.notify(b.getInt());
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

    private NotifyCallback serout_callback = new NotifyCallback() {
        @Override
        public void notify(double timestamp_utc, byte[] payload) {
            int seq_n = (int)payload[0];
            if(seq_n != (recv_seq_n+1)%0x100) {
                Log.e(TAG,"OUT OF ORDER PACKET");
                Log.e(TAG,"EXPECTED: "+((recv_seq_n+1)%0x100));
                Log.e(TAG,"GOT:      "+(seq_n));
            } else {
                Log.d(TAG, "RECV: " + seq_n + " " + payload.length + " bytes");
            }
            recv_seq_n = seq_n;
            // Append to aggregate buffer
            for(int i = 1; i < payload.length; i++) {
                recv_buf.add(payload[i]);
            }
            interpretAggregate();
        }
    };

    ////////////////////////////////
    // METHODS
    ////////////////////////////////

    public MooshimeterDeviceNew(BluetoothDevice device, Context context) {
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

        tree_bin.notification_handler = new ConfigTree.ConfigNode.NotificationHandler() {
            @Override
            public void handle(Object notification) {
                try {
                    tree.unpack((byte[])notification);
                    code_list = tree.getShortCodeList();
                    tree.enumerate();
                } catch (DataFormatException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    void sendToMeter(byte[] payload) {
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
        // cmd might contain a payload, in which case split it out
        String[] tokens = cmd.split(" ", 1);
        String node_str = tokens[0];
        String payload_str;
        if(tokens.length==2) {
            payload_str = tokens[1];
        } else {
            payload_str = null;
        }
        node_str = node_str.toUpperCase();
        ConfigTree.ConfigNode node = tree.getNodeAtLongname(node_str);
        if(node==null) {
            Log.e(TAG,"Node not found at "+node_str);
            return;
        }
        if(node.code==-1) {
            Log.d(TAG,"This command does not have a value associated.");
            Log.d(TAG,"Children:");
            tree.enumerate(node);
            return;
        }
        ByteBuffer b = MooshimeterDeviceBase.wrap(new byte[19]);
        int opcode = node.code;
        if(payload_str!=null) {
            // Signify a write
            opcode |= 0x80;
            b.put((byte) opcode);
            switch (node.ntype) {
                case ConfigTree.NTYPE.PLAIN:
                    Log.d(TAG, "This command takes no payload");
                    return;
                case ConfigTree.NTYPE.CHOOSER:
                    b.put((byte) Integer.parseInt(payload_str));
                    break;
                case ConfigTree.NTYPE.LINK:
                    Log.d(TAG, "This command takes no payload");
                    return;
                case ConfigTree.NTYPE.COPY:
                    Log.d(TAG, "This command takes no payload");
                    return;
                case ConfigTree.NTYPE.VAL_U8:
                case ConfigTree.NTYPE.VAL_S8:
                    b.put((byte) Integer.parseInt(payload_str));
                    break;
                case ConfigTree.NTYPE.VAL_U16:
                case ConfigTree.NTYPE.VAL_S16:
                    b.putShort((short) Integer.parseInt(payload_str));
                    break;
                case ConfigTree.NTYPE.VAL_U32:
                case ConfigTree.NTYPE.VAL_S32:
                    b.putInt((int) Integer.parseInt(payload_str));
                    break;
                case ConfigTree.NTYPE.VAL_STR:
                    b.putShort((short) payload_str.length());
                    for(char c:payload_str.toCharArray()) {
                        b.put((byte)c);
                    }
                    break;
                case ConfigTree.NTYPE.VAL_BIN:
                    Log.d(TAG, "Not implemented yet");
                    return;
                case ConfigTree.NTYPE.VAL_FLT:
                    b.putFloat(Float.parseFloat(payload_str));
                    break;
            }
        } else {
            b.put((byte) opcode);
        }
        sendToMeter(Arrays.copyOfRange(b.array(), 0, b.position()));
    }

    void loadTree() {
        sendCommand("ADMIN:TREE");
    }

    ////////////////////////////////
    // Private helpers
    ////////////////////////////////

    private int cycleChoiceAt(String chooser_name) {
        int choice_i = (Integer)tree.getValueAt(chooser_name);
        choice_i++;
        choice_i %= tree.getNodeAtLongname(chooser_name).children.size();
        sendCommand(chooser_name+" "+choice_i);
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
        loadTree();
        return 0;
    }

    @Override
    public int getBufLen() {
        String dstring = tree.getChosenName("SAMPLING:DEPTH");
        return Integer.parseInt(dstring);
    }

    @Override
    public void getBuffer(Runnable onReceived) {

    }

    @Override
    public void pauseStream() {
        // Sampling off
        sendCommand("SAMPLING:TRIGGER 0");
    }

    @Override
    public void playSampleStream(NotifyCallback on_notify) {
        // Sampling Continuous
        sendCommand("SAMPLING:TRIGGER 2");
        // FIXME:
        ConfigTree.ConfigNode
    }

    @Override
    public boolean isStreaming() {
        return tree.getChosenName("SAMPLING:TRIGGER").equals("OFF");
    }

    @Override
    public void bumpRange(int channel, boolean expand, boolean wrap) {

    }

    @Override
    public boolean applyAutorange() {
        return false;
    }

    @Override
    public SignificantDigits getSigDigits(int channel) {
        SignificantDigits rval = new SignificantDigits();
        rval.high=3;
        rval.n_digits=7;
        return rval;
    }

    @Override
    public String getDescriptor(int channel) {
        return null;
    }

    @Override
    public String getUnits(int channel) {
        return null;
    }

    @Override
    public String getInputLabel(int channel) {
        return null;
    }

    @Override
    public int cycleSampleRate() {
        return cycleChoiceAt("SAMPLING:RATE");
    }

    @Override
    public int getSampleRateHz() {
        String dstring = tree.getChosenName("SAMPLING:RATE");
        return Integer.parseInt(dstring);
    }

    @Override
    public int cycleBufferDepth() {
        return cycleChoiceAt("SAMPLING:DEPTH");
    }

    @Override
    public int getBufferDepth() {
        String dstring = tree.getChosenName("SAMPLING:DEPTH");
        return Integer.parseInt(dstring);
    }

    @Override
    public boolean getLoggingOn() {
        Integer i = (Integer)tree.getValueAt("LOGGING:ON");
        return i!=0;
    }

    @Override
    public void setLoggingOn(boolean on) {
        int i=on?1:0;
        sendCommand("LOGGING:ON "+i);
    }

    @Override
    public int getLoggingStatus() {
        return (Integer)tree.getValueAt("LOGGING:STATUS");
    }

    @Override
    public String getRangeLabel(int c) {
        ConfigTree.ConfigNode mapnode = getInputNode(c);
        // Get range
        ConfigTree.ConfigNode rangenode = mapnode.getChildByName("RANGE");
        return rangenode.children.get((Integer) rangenode.getValue()).name;
    }

    @Override
    public String getValueLabel(int c) {
        // TODO: Respect significant digits
        ConfigTree.ConfigNode mapnode = getInputNode(c);
        ConfigTree.ConfigNode valuenode = mapnode.getChildByName("VALUE");
        return valuenode.getValue().toString();
    }

    @Override
    public int getInputMappingIndex(int c) {
        return (Integer)getInputNode(c).getValue();
    }

    @Override
    public int getInputSubMappingIndex(int c) {
        return 0;
    }

    @Override
    public int setInputMappingIndex(int c, int mapping) {
        String s = (c==0?"CH1":"CH2") + ":MAPPING " + mapping;
        sendCommand(s);
        return (Integer)tree.getValueAt(s);
    }

    @Override
    public int setInputSubMappingIndex(int c, int mapping) {
        return 0;
    }

    @Override
    public int cycleInputMapping(int c) {
        String s = (c==0?"CH1":"CH2") + ":MAPPING";
        return cycleChoiceAt(s);
    }

    @Override
    public int cycleInputSubMapping(int c) {
        return 0;
    }
}
