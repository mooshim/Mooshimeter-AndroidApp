package com.mooshim.mooshimeter.common;

import android.util.Log;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.msgpack.MessagePack;
import org.msgpack.type.IntegerValue;
import org.msgpack.type.Value;

import static org.msgpack.template.Templates.*;

/**
 * Created by First on 2/4/2016.
 */
public class ConfigTree {
    //////////////////////
    // STATICS
    //////////////////////
    private static String TAG = "ConfigTree";

    public static class NTYPE {
        public static final byte NOTSET  =-1 ; // May be an informational node, or a choice in a chooser
        public static final byte PLAIN   =0 ; // May be an informational node, or a choice in a chooser
        public static final byte LINK    =1 ; // A link to somewhere else in the tree
        public static final byte CHOOSER =2 ; // The children of a CHOOSER can only be selected by one CHOOSER, and a CHOOSER can only select one child
        public static final byte VAL_U8  =3 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_U16 =4 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_U32 =5 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_S8  =6 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_S16 =7 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_S32 =8 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_STR =9 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_BIN =10; // These nodes have readable and writable values of the type specified
        public static final byte VAL_FLT =11; // These nodes have readable and writable values of the type specified
    }

    static abstract class NodeProcessor {
        abstract public void process(ConfigNode n);
    }

    //////////////////////
    // CONFIG NODES
    //////////////////////

    public static class ConfigNode {
        protected int code = -1;
        protected int ntype = NTYPE.NOTSET;
        protected String name = "";
        protected List<ConfigNode> children = new ArrayList<ConfigNode>();
        protected ConfigNode parent = null;
        protected ConfigTree tree   = null;
        public List<NotifyHandler> notify_handlers = new ArrayList<NotifyHandler>();

        private Object value = (Integer)0;
        String cache_longname=null;
        StatLockManager lock;

        public ConfigNode(ConfigTree tree_arg, int ntype_arg,String name_arg, List<ConfigNode> children_arg) {
            tree = tree_arg;
            ntype=ntype_arg;
            name=name_arg;
            if(children_arg!=null){
                for(ConfigNode c:children_arg) {
                    children.add(c);
                }
            }
            lock = new StatLockManager(tree.lock);
        }
        public ConfigNode(ConfigTree tree_arg, int ntype_arg) {
            this(tree_arg,ntype_arg,null,null);
        }
        public byte[] pack() throws IOException {
            List<Object> l = new ArrayList<Object>();
            packToEndOfList(l);
            MessagePack msgpack = new MessagePack();
            return msgpack.write(l);
        }
        public void unpack(byte[] arg) throws IOException {
            MessagePack msgpack = new MessagePack();
            List<Value> l = msgpack.read(arg, tList(TValue));
            unpackFromFrontOfList(l);
        }
        private Object msgPackValueToObject(Value v) {
            if(v==null) {return null;}
            if(v.isNilValue()){return null;}
            if(v.isIntegerValue()){return v.asIntegerValue().getInt();}
            if(v.isBooleanValue()){return v.asBooleanValue().isBooleanValue();}
            if(v.isFloatValue()){return v.asFloatValue().getFloat();}
            if(v.isArrayValue()){return v.asArrayValue();}
            if(v.isRawValue()){return v.asRawValue();}
            return null;
        }
        public void unpackFromFrontOfList(List<Value> l) {
            int check_ntype = l.remove(0).asIntegerValue().getInt();
            if(check_ntype != ntype) {
                Log.e(TAG,"WRONG NODE TYPE");
                // Exception?
                new Exception().printStackTrace();
            }
            // Forgive me this terrible line.  Value.toString returns a StringBuilder, StringBuilder.toString returns a String
            name = l.remove(0).asRawValue().getString();
            Value tmp_Value = l.remove(0);
            if(tmp_Value!=null && !tmp_Value.isNilValue()) {
                switch (ntype) {
                    case NTYPE.PLAIN:
                        value = null;
                        break;
                    case NTYPE.LINK:
                    case NTYPE.VAL_STR:
                        value = tmp_Value.asRawValue().getString();
                        break;
                    case NTYPE.CHOOSER:
                    case NTYPE.VAL_U8:
                    case NTYPE.VAL_S8:
                        value = tmp_Value.asIntegerValue().getByte();
                        break;
                    case NTYPE.VAL_U16:
                    case NTYPE.VAL_S16:
                        value = tmp_Value.asIntegerValue().getShort();
                        break;
                    case NTYPE.VAL_U32:
                    case NTYPE.VAL_S32:
                        value = tmp_Value.asIntegerValue().getInt();
                        break;
                    case NTYPE.VAL_BIN:
                        new Exception().printStackTrace();
                        break;
                    case NTYPE.VAL_FLT:
                        value = tmp_Value.asFloatValue().getFloat();
                        break;
                }
            }
            Log.d(TAG, this.toString());
            List<Value> children_packed = new ArrayList<Value>(l.remove(0).asArrayValue());
            while(children_packed.size()>0) {
                int c_ntype = children_packed.get(0).asIntegerValue().getInt();
                ConfigNode child = new ConfigNode(tree, c_ntype);
                assert child != null;
                child.unpackFromFrontOfList(children_packed);
                children.add(child);
            }
        }
        public void packToEndOfList(List<Object> l) {
            l.add(ntype);
            l.add(name);
            List<Object> children_packed = new ArrayList<Object>();
            for(ConfigNode c:children) {
                c.packToEndOfList(children_packed);
            }
            l.add(children_packed);
        }
        public String toString() {
            String s = "";
            if(code != -1) {
                s += code + ":" + name;
            } else {
                s = name;
            }
            return s;
        }
        public byte getIndex() { return (byte)parent.children.indexOf(this); }
        public Object getValue() {
            return value;
        }
        public String getShortName() { return name; }
        private void getLongName(StringBuffer rval, String sep) {
            // This is the recursive call
            if(parent!=null) {
                parent.getLongName(rval, sep);
            }
            rval.append(name);
            rval.append(sep);
        }
        public String getLongName(String sep) {
            if(cache_longname==null) {
                StringBuffer rval = new StringBuffer();
                getLongName(rval, sep);
                // This will have an extra seperator on the end and beginning
                rval.deleteCharAt(rval.length() - 1);
                rval.deleteCharAt(0);
                cache_longname = rval.toString();
            }
            return cache_longname;
        }
        public String getLongName() { return getLongName(":"); }
        public String getChoiceString() {
            // Returns the string necessary to choose this node
            // Presumes parent node is a chooser
            if(parent.ntype != NTYPE.CHOOSER && parent.children.size()>1) {
                Log.e(TAG,"getChoiceString being called on non-choice!");
            }
            return parent.getLongName() + " " + getIndex();
        }
        public ConfigNode getChosen() {
            ConfigNode rval = children.get((Integer) value);
            while(rval.ntype==NTYPE.LINK) {
                rval = tree.getNode((String) rval.getValue());
            }
            return rval;
        }
        public ConfigNode getChild(String name_arg) {
            for(ConfigNode c:children) {
                if(c.getShortName().equals(name_arg)) {
                    return c;
                }
            }
            return null;
        }
        public boolean needsShortCode() { return ntype>=NTYPE.CHOOSER; }
        public int getShortCode() { return code; }

        //////////////////
        // Helpers for interacting with remote device
        //////////////////

        public Object parseValueString(String str) {
            switch (ntype) {
                case NTYPE.PLAIN:
                    Log.e(TAG, "This command takes no payload");
                    return null;
                case NTYPE.CHOOSER:
                    return Byte.parseByte(str);
                case NTYPE.LINK:
                    Log.e(TAG, "This command takes no payload");
                    return null;
                case NTYPE.VAL_U8:
                case NTYPE.VAL_S8:
                    return Byte.parseByte(str);
                case NTYPE.VAL_U16:
                case NTYPE.VAL_S16:
                    return Short.parseShort(str);
                case NTYPE.VAL_U32:
                case NTYPE.VAL_S32:
                    return Integer.parseInt(str);
                case NTYPE.VAL_STR:
                    return str;
                case NTYPE.VAL_BIN:
                    Log.d(TAG, "Not implemented yet");
                    return null;
                case NTYPE.VAL_FLT:
                    return Float.parseFloat(str);
            }
            Log.e(TAG,"Bad ntype!");
            return null;
        }
        private void packToSerial(ByteBuffer b) {
            b.put((byte) code);
        }
        private void packToSerial(ByteBuffer b,Object new_value) {
            // Signify a write
            int opcode = code | 0x80;
            b.put((byte) opcode);
            switch (ntype) {
                case NTYPE.PLAIN:
                    Log.e(TAG, "This command takes no payload");
                    break;
                case NTYPE.CHOOSER:
                    b.put((Byte)new_value);
                    break;
                case NTYPE.LINK:
                    Log.e(TAG, "This command takes no payload");
                    return;
                case NTYPE.VAL_U8:
                case NTYPE.VAL_S8:
                    b.put((Byte)new_value);
                    break;
                case NTYPE.VAL_U16:
                case NTYPE.VAL_S16:
                    b.putShort((Short) new_value);
                    break;
                case NTYPE.VAL_U32:
                case NTYPE.VAL_S32:
                    b.putInt((Integer) new_value);
                    break;
                case NTYPE.VAL_STR:
                    String cast = (String)new_value;
                    b.putShort((short) cast.length());
                    for(char c:cast.toCharArray()) {
                        b.put((byte)c);
                    }
                    break;
                case NTYPE.VAL_BIN:
                    Log.d(TAG, "Not implemented yet");
                    break;
                case NTYPE.VAL_FLT:
                    b.putFloat((Float)new_value);
                    break;
                default:
                    Log.e(TAG,"Unhandled node type!");
                    break;
            }
        }
        public void choose() {
            if(parent.ntype==NTYPE.CHOOSER) {
                parent.sendValue(getIndex(),true);
            }
        }
        public Object reqValue() {
            // Forces a refresh of the value at this node
            if(code==-1) {
                Log.e(TAG,"Requested value for a node with no shortcode!");
                new Exception().printStackTrace();
                return null;
            }
            lock.l();
            tree.sendBytes(new byte[]{(byte)code});
            lock.awaitMilli(2000);
            lock.ul();
            return value;
        }
        public void sendValue(Object new_value, boolean blocking) {
            byte[] payload = new byte[20];
            ByteBuffer b = ByteBuffer.wrap(payload);
            packToSerial(b,new_value);
            payload = Arrays.copyOf(payload,b.position());
            if(blocking) {
                lock.l();
            } else {
                // Assume it will get through
                value = new_value;
            }
            tree.sendBytes(payload);
            if(blocking) {
                lock.awaitMilli(2000);
                lock.ul();
            }
        }

        //////////////////
        // Notification helpers
        //////////////////

        public void addNotifyHandler(NotifyHandler h) {
            if(h!=null) {
                notify_handlers.add(h);
            }
        }
        public void removeNotifyHandler(NotifyHandler h) {
            notify_handlers.remove(h);
        }
        public void clearNotifyHandlers() {
            notify_handlers.clear();
        }
        public void notify(Object notification) {
            notify(0,notification);
        }
        public void notify(final double time_utc, final Object notification) {
            Log.d(TAG, name + ":" + notification);
            value = notification;
            for(NotifyHandler handler:notify_handlers) {
                handler.onReceived(time_utc, notification);
            }
            lock.l();
            lock.sig();
            lock.ul();
        }
    }

    //////////////////////
    // Class Members
    //////////////////////

    ConfigNode root = null;
    PeripheralWrapper pwrap = null;
    UUID serin_uuid  = null;
    UUID serout_uuid = null;
    private int send_seq_n = 0;
    private int recv_seq_n = 0;
    private List<Byte> recv_buf = new ArrayList<Byte>();
    private Map<Integer,ConfigTree.ConfigNode> code_list = null;
    private Lock lock = new ReentrantLock(true);

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
            try{
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
                            n.notify(new String(Arrays.copyOfRange(b.array(),b.position(),b.position()+expecting_bytes)));
                            b.position(b.position() + expecting_bytes);
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
            } catch(BufferUnderflowException e){
                Log.e(TAG,"Underflow caught");
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

    //////////////////////
    // Methods for interacting with remote device
    //////////////////////

    public void attach(PeripheralWrapper p, UUID serin, UUID serout) {
        pwrap=p;
        serin_uuid=serin;
        serout_uuid = serout;
        pwrap.enableNotify(serout, true, serout_callback);

        // Load the tree from the remote device
        command("ADMIN:TREE");
    }

    private void sendBytes(byte[] payload) {
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
        pwrap.send(serin_uuid, buf);
    }

    //////////////////////
    // Methods
    //////////////////////

    public ConfigTree() {
        // Always assume a tree starts with this configuration
        root = new ConfigNode(this, NTYPE.PLAIN,"",Arrays.asList(new ConfigNode[] {
                    new ConfigNode(this, NTYPE.PLAIN,"ADMIN", Arrays.asList(new ConfigNode[] {
                        new ConfigNode(this, NTYPE.VAL_U32,"CRC32",null),
                        new ConfigNode(this, NTYPE.VAL_BIN,"TREE",null),
                        new ConfigNode(this, NTYPE.VAL_STR,"DIAGNOSTIC",null),
                })),
        }));
        assignShortCodes();
        code_list = getShortCodeMap();

        ConfigNode tree_bin = getNode("ADMIN:TREE");
        tree_bin.addNotifyHandler(new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                try {
                    // This will replace all the internal members of the tree!
                    unpack((byte[]) payload);
                    code_list = getShortCodeMap();
                    enumerate();
                } catch (DataFormatException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void enumerate(ConfigNode n, String indent) {
        Log.d(TAG, indent + n.toString());
        for(ConfigNode c:n.children) {
            enumerate(c,indent+"  ");
        }
    }
    public void enumerate(ConfigNode n) {
        enumerate(n, "");
    }
    public void enumerate() {
        enumerate(root);
    }

    public int addNotifyHandler(String node_name,NotifyHandler h) {
        ConfigNode n = getNode(node_name);
        if(n==null) {
            return -1;
        }
        n.addNotifyHandler(h);
        return 0;
    }

    public byte[] pack() throws IOException {
        List<Object> l = new ArrayList<Object>();
        root.packToEndOfList(l);
        MessagePack msgpack = new MessagePack();
        byte[] plain = msgpack.write(l);
        Deflater deflater = new Deflater();
        deflater.setInput(plain);
        deflater.finish();
        byte[] compressed = new byte[plain.length];
        int compressed_len = deflater.deflate(compressed);
        deflater.end();
        compressed = Arrays.copyOf(compressed,compressed_len);
        return compressed;
    }
    public void unpack(byte[] compressed) throws DataFormatException, IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] plain = new byte[2000]; // FIXME: How do I know how much to allocate?
        int plain_len = inflater.inflate(plain);
        plain = Arrays.copyOf(plain,plain_len);
        inflater.end();
        MessagePack msgpack = new MessagePack();
        List<Value> l = msgpack.read(plain,tList(TValue));
        Log.d(TAG,l.toString());
        root = new ConfigNode(this, l.get(0).asIntegerValue().getInt());
        root.unpackFromFrontOfList(l);
        assignShortCodes();
    }

    public void walk(ConfigNode n, NodeProcessor p) {
        p.process(n);
        for(ConfigNode c:n.children) {
            walk(c,p);
        }
    }
    public void walk(NodeProcessor p) {
        walk(root, p);
    }

    public void assignShortCodes() {
        final int[] g_code = {0};
        final ConfigTree self = this;
        NodeProcessor p = new NodeProcessor() {
            @Override
            public void process(ConfigNode n) {
                n.tree = self;
                if(n.children!=null) {
                    for(ConfigNode c:n.children) {
                        c.parent=n;
                    }
                }
                if(n.needsShortCode()) {
                    n.code = g_code[0];
                    g_code[0]++;
                }
            }
        };
        walk(p);
    }
    public ConfigNode getNode(String name) {
        String[] tokens = name.split(":");
        ConfigNode n = root;
        for(String t:tokens) {
            n = n.getChild(t);
            if(n==null) {
                // Not found!
                return null;
            }
        }
        return n;
    }
    public Object getValueAt(String name) {
        ConfigNode n = getNode(name);
        if(n==null) {
            return null;
        }
        return n.getValue();
    }
    public ConfigNode getChosenNode(String name) {
        ConfigNode n = getNode(name);
        assert n != null;
        if(n.value ==null) {
            // FIXME: Assume always initialized to zero!
            n.value = 0;
        }
        ConfigNode rval = n.children.get((Integer) n.value);
        // Follow link
        if(rval.ntype==NTYPE.LINK) {
            return getNode((String) rval.getValue());
        } else {
            return rval;
        }
    }
    public String getChosenName(String name) {
        return getChosenNode(name).name;
    }
    public Map<Integer,ConfigNode> getShortCodeMap() {
        final HashMap<Integer,ConfigNode> rval = new HashMap<Integer, ConfigNode>();
        NodeProcessor p = new NodeProcessor() {
            @Override
            public void process(ConfigNode n) {
                if(n.code != -1) {
                    rval.put(n.code,n);
                }
            }
        };
        walk(p);
        return rval;
    }

    public void command(String cmd) {
        // cmd might contain a payload, in which case split it out
        String[] tokens = cmd.split(" ", 2);
        String node_str = tokens[0];
        String payload_str;
        if(tokens.length==2) {
            payload_str = tokens[1];
        } else {
            payload_str = null;
        }
        node_str = node_str.toUpperCase();
        ConfigTree.ConfigNode node = getNode(node_str);
        if(node==null) {
            Log.e(TAG, "Node not found at " + node_str);
            return;
        }
        if (payload_str != null) {
            node.sendValue(node.parseValueString(payload_str),true);
        } else {
            node.reqValue();
        }
    }

    public void refreshAll() {
        // Shortcodes are guaranteed to be consecutive
        int n_codes = code_list.keySet().size();
        // Skip the first 3 codes (they are for CRC, tree and diagnostic
        for(int i = 3; i < n_codes; i++) {
            //code_list.get(i).reqValue();
            sendBytes(new byte[]{(byte) i});
        }
    }
}