package com.mooshim.mooshimeter.common;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        public static final byte CHOOSER =1 ; // The children of a CHOOSER can only be selected by one CHOOSER, and a CHOOSER can only select one child
        public static final byte LINK    =2 ; // A link to somewhere else in the tree
        public static final byte COPY    =3 ; // In a fully inflated tree, this value will not appear.  It's an instruction to the inflater to copy the value
        public static final byte VAL_U8  =4 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_U16 =5 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_U32 =6 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_S8  =7 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_S16 =8 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_S32 =9 ; // These nodes have readable and writable values of the type specified
        public static final byte VAL_STR =10; // These nodes have readable and writable values of the type specified
        public static final byte VAL_BIN =11; // These nodes have readable and writable values of the type specified
        public static final byte VAL_FLT =12; // These nodes have readable and writable values of the type specified
    }
    public static Class[] NodesByType = {
        StructuralNode.class,
        StructuralNode.class,
        RefNode       .class,
        RefNode       .class,
        ValueNode     .class,
        ValueNode     .class,
        ValueNode     .class,
        ValueNode     .class,
        ValueNode     .class,
        ValueNode     .class,
        ValueNode     .class,
        ValueNode     .class,
        ValueNode     .class,
    };

    public static ConfigNode nodeFactory(int ntype_arg){
        Class c = NodesByType[ntype_arg];
        try {
            return (ConfigNode) c.getConstructor(int.class).newInstance(ntype_arg);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
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

        public Object last_value = (Integer)0;

        public ConfigNode(int ntype_arg,String name_arg, List<ConfigNode> children_arg) {
            ntype=ntype_arg;
            name=name_arg;
            if(children_arg!=null){
                for(ConfigNode c:children_arg) {
                    children.add(c);
                }
            }
        }
        public ConfigNode(int ntype_arg) {
            this(ntype_arg,null,null);
        }
        public byte[] pack() throws IOException {
            List<Object> l = new ArrayList<Object>();
            packToEndOfList(l);
            MessagePack msgpack = new MessagePack();
            return msgpack.write(l);
        }
        public void unpack(byte[] arg) throws IOException {
            MessagePack msgpack = new MessagePack();
            List<Value> l = msgpack.read(arg,tList(TValue));
            unpackFromFrontOfList(l);
        }
        public void unpackFromFrontOfList(List<Value> l) {
            //Subclass implement
        }
        public void packToEndOfList(List<Object> l) {
            //Subclass implement
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
        public int getIndex() {
            return parent.children.indexOf(this);
        }
        private void getPath(List<Integer> rval) {
            if(parent!=null) {
                parent.getPath(rval);
                rval.add(getIndex());
            }
        }
        public Object getValue() {
            return last_value;
        }
        public List<Integer> getPath() {
            List<Integer> rval = new ArrayList<Integer>();
            getPath(rval);
            return rval;
        }
        public String getShortName() {
            return name;
        }
        private void getLongName(StringBuffer rval, String sep) {
            // This is the recursive call
            if(parent!=null) {
                parent.getLongName(rval, sep);
            }
            rval.append(name);
            rval.append(sep);
        }
        String cache_longname=null;
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
        public String getLongName() {
            return getLongName(":");
        }
        public String getChoiceString() {
            // Returns the string necessary to choose this node
            // Presumes parent node is a chooser
            if(parent.ntype != NTYPE.CHOOSER && parent.children.size()>1) {
                Log.e(TAG,"getChoiceString being called on non-choice!");
            }
            return parent.getLongName() + " " + getIndex();
        }
        public ConfigNode getChildByName(String name_arg) {
            for(ConfigNode c:children) {
                if(c.getShortName().equals(name_arg)) {
                    return c;
                }
            }
            return null;
        }
        public boolean needsShortCode() {
            return false;
        }
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
            Log.d(TAG,name+":"+notification);
            last_value = notification;
            for(NotifyHandler handler:notify_handlers) {
                handler.onReceived(time_utc, notification);
            }
        }
    }
    public static class StructuralNode extends ConfigNode {
        public StructuralNode(int ntype_arg,String name_arg, List<ConfigNode> children_arg) {
            super(ntype_arg,name_arg,children_arg);
        }
        public StructuralNode(int ntype_arg) {
            super(ntype_arg);
        }
        @Override
        public void unpackFromFrontOfList(List<Value> l) {
            Value tmp_Value = l.remove(0);
            IntegerValue tmp_Int = tmp_Value.asIntegerValue();
            int check_ntype = tmp_Int.getInt();
            if(check_ntype != ntype) {
                Log.e(TAG,"WRONG NODE TYPE");
                // Exception?
            }
            // Forgive me this terrible line.  Value.toString returns a StringBuilder, StringBuilder.toString returns a String
            name=l.remove(0).asRawValue().getString();
            Log.d(TAG,this.toString());
            List<Value> children_packed = new ArrayList<Value>(l.remove(0).asArrayValue());
            while(children_packed.size()>0) {
                int c_ntype = children_packed.get(0).asIntegerValue().getInt();
                ConfigNode child = ConfigTree.nodeFactory(c_ntype);
                assert child != null;
                child.unpackFromFrontOfList(children_packed);
                children.add(child);
            }
        }
        @Override
        public void packToEndOfList(List<Object> l) {
            l.add(ntype);
            l.add(name);
            List<Object> children_packed = new ArrayList<Object>();
            for(ConfigNode c:children) {
                c.packToEndOfList(children_packed);
            }
            l.add(children_packed);
        }
        @Override
        public boolean needsShortCode() {
            return (ntype==NTYPE.CHOOSER);
        }
    }
    public static class RefNode        extends ConfigNode {
        public String path = "";
        public RefNode(int ntype_arg,String name_arg, List<ConfigNode> children_arg) {
            super(ntype_arg,name_arg,children_arg);
        }
        public RefNode(int ntype_arg) {
            super(ntype_arg);
        }
        @Override
        public String getShortName() {
            return tree.getNodeAtLongname(path).getShortName();
        }
        @Override
        public void unpackFromFrontOfList(List<Value> l) {
            int check_ntype = l.remove(0).asIntegerValue().getInt();
            if(check_ntype != ntype) {
                Log.e(TAG,"WRONG NODE TYPE");
                // Exception?
            }
            path = l.remove(0).asRawValue().getString();
        }
        @Override
        public void packToEndOfList(List<Object> l) {
            l.add(ntype);
            l.add(path);
        }
        @Override
        public String toString() {
            String s = "";
            if(ntype==NTYPE.COPY) {
                s+="COPY: "+path;
            }
            if(ntype==NTYPE.LINK) {
                s +="LINK:"+ path + ":" + tree.getNodeAtLongname(path).getPath();
            }
            return s;
        }
    }
    public static class ValueNode      extends ConfigNode{
        public ValueNode(int ntype_arg,String name_arg, List<ConfigNode> children_arg) {
            super(ntype_arg,name_arg,children_arg);
        }
        public ValueNode(int ntype_arg) {
            super(ntype_arg);
        }

        @Override
        public void unpackFromFrontOfList(List<Value> l) {
            int check_ntype = l.remove(0).asIntegerValue().getInt();
            if(check_ntype != ntype) {
                Log.e(TAG,"WRONG NODE TYPE");
                // Exception?
            }
            name=l.remove(0).asRawValue().getString();
            Log.d(TAG,this.toString());
        }
        @Override
        public void packToEndOfList(List<Object> l) {
            l.add(ntype);
            l.add(name);
        }
        @Override
        public boolean needsShortCode() {
            return true;
        }
    }

    //////////////////////
    // Class Members
    //////////////////////

    ConfigNode root = null;

    //////////////////////
    // Methods
    //////////////////////

    public ConfigTree(ConfigNode new_root) {
        root = new_root;
    }

    private void enumerate(ConfigNode n,String indent) {
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
        root = nodeFactory(l.get(0).asIntegerValue().getInt());
        root.unpackFromFrontOfList(l);
        assignShortCodes();
    }

    static abstract class NodeProcessor {
        abstract public void process(ConfigNode n);
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
    public ConfigNode getNodeAtLongname(String name) {
        String[] tokens = name.split(":");
        ConfigNode n = root;
        for(String t:tokens) {
            n = n.getChildByName(t);
            if(n==null) {
                // Not found!
                return null;
            }
        }
        return n;
    }
    public ConfigNode getNodeAtPath(List<Integer> path) {
        ConfigNode n = root;
        for(Integer i:path) {
            n = n.children.get(i);
        }
        return n;
    }
    public Object getValueAt(String name) {
        ConfigNode n = getNodeAtLongname(name);
        if(n==null) {
            return null;
        }
        return n.getValue();
    }
    public ConfigNode getChosenNode(String name) {
        ConfigNode n = getNodeAtLongname(name);
        assert n != null;
        assert n.ntype == NTYPE.CHOOSER;
        if(n.last_value==null) {
            // FIXME: Assume always initialized to zero!
            n.last_value = 0;
        }
        return n.children.get((Integer) n.last_value);
    }
    public String getChosenName(String name) {
        return getChosenNode(name).name;
    }
    public Map<Integer,ConfigNode> getShortCodeList() {
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
    public byte[] getByteCmdForString(String cmd) {
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
        ConfigTree.ConfigNode node = getNodeAtLongname(node_str);
        if(node==null) {
            Log.e(TAG,"Node not found at "+node_str);
            return null;
        }
        if(node.code==-1) {
            Log.d(TAG,"This command does not have a value associated.");
            Log.d(TAG, "Children:");
            enumerate(node);
            return null;
        }
        ByteBuffer b = MooshimeterDeviceBase.wrap(new byte[19]);
        int opcode = node.code;
        if(payload_str!=null) {
            // Signify a write
            opcode |= 0x80;
            b.put((byte) opcode);
            switch (node.ntype) {
                case ConfigTree.NTYPE.PLAIN:
                    Log.e(TAG, "This command takes no payload");
                    return null;
                case ConfigTree.NTYPE.CHOOSER:
                    b.put((byte) Integer.parseInt(payload_str));
                    break;
                case ConfigTree.NTYPE.LINK:
                    Log.e(TAG, "This command takes no payload");
                    return null;
                case ConfigTree.NTYPE.COPY:
                    Log.e(TAG, "This command takes no payload");
                    return null;
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
                    return null;
                case ConfigTree.NTYPE.VAL_FLT:
                    b.putFloat(Float.parseFloat(payload_str));
                    break;
            }
        } else {
            b.put((byte) opcode);
        }
        return Arrays.copyOfRange(b.array(), 0, b.position());
    }
}