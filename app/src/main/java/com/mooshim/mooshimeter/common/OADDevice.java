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


import android.util.Log;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import static java.util.UUID.fromString;

public class OADDevice extends BLEDeviceBase {

    /*
    mUUID stores the UUID values of all the Mooshimeter fields.
    Note that the OAD fields are only accessible when connected to the Mooshimeter in OAD mode
    and the METER_ fields are only accessible when connected in meter mode.
     */

    public static class mUUID {
        public final static UUID
        OAD_SERVICE_UUID    = fromString("1BC5FFC0-0200-62AB-E411-F254E005DBD4"),
        OAD_IMAGE_IDENTIFY  = fromString("1BC5FFC1-0200-62AB-E411-F254E005DBD4"),
        OAD_IMAGE_BLOCK     = fromString("1BC5FFC2-0200-62AB-E411-F254E005DBD4"),
        OAD_REBOOT          = fromString("1BC5FFC3-0200-62AB-E411-F254E005DBD4");
    }

    private static final String TAG="OADDevice";

    private static ByteBuffer wrap(byte[] in) {
        // Generates a little endian byte buffer wrapping the byte[]
        ByteBuffer b = ByteBuffer.wrap(in);
        b.order(ByteOrder.LITTLE_ENDIAN);
        return b;
    }

    /*
    All fields in the Mooshimeter BLE profile are stored as structs
    MeterStructure provides a common framework to access them.
     */

    public class OADIdentity extends LegacyMeterStructure {
        public short crc0;
        public short crc1;
        public short ver;
        public int len;
        public int build_time;
        public byte[] res = new byte[4];
        public OADIdentity(PeripheralWrapper p) {super(p);}
        @Override
        public UUID getUUID() { return mUUID.OAD_IMAGE_IDENTIFY; }

        @Override
        public void unpack_inner(byte[] buf) {

        }

        public void unpackFromFile(byte[] fbuf) {
            ByteBuffer b = ByteBuffer.wrap(fbuf);
            b.order(ByteOrder.LITTLE_ENDIAN);
            crc0 = b.getShort();
            crc1 = b.getShort();
            ver = b.getShort();
            len = 0xFFFF & ((int) b.getShort());
            build_time = b.getInt();
            for (int i = 0; i < 4; i++) {
                res[i] = b.get();
            }
        }

        public byte[] packForFile() {
            byte[] retval = new byte[16];
            ByteBuffer b = ByteBuffer.wrap(retval);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putShort(crc0);
            b.putShort(crc1);
            b.putShort(ver);
            b.putShort((short) len);
            b.putInt(build_time);
            for (int i = 0; i < 4; i++) {
                b.put(res[i]);
            }
            return retval;
        }

        @Override
        public byte[] pack() {
            byte[] retval = new byte[8];
            ByteBuffer b = ByteBuffer.wrap(retval);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putShort(ver);
            b.putShort((short) len);
            b.putInt(build_time);
            return retval;
        }
    }
    public class OADBlock    extends LegacyMeterStructure {
        public OADBlock(PeripheralWrapper p) {
            super(p);
        }
        public short requestedBlock;

        public short blockNum;
        public byte[] bytes;

        @Override
        public UUID getUUID() { return mUUID.OAD_IMAGE_BLOCK; }

        @Override
        public byte[] pack() {
            ByteBuffer b = wrap(new byte[18]);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putShort(blockNum);
            for( byte c : bytes ) {
                b.put(c);
            }
            return b.array();
        }

        @Override
        public void unpack_inner(byte[] in) {
            ByteBuffer b = wrap(in);
            b.order(ByteOrder.LITTLE_ENDIAN);
            requestedBlock = b.getShort();
        }
    }

    // Used so the inner classes have something to grab
    public OADDevice mInstance;

    public OADIdentity      oad_identity;
    public OADBlock         oad_block;

    public OADDevice(PeripheralWrapper wrap) {
        // Initialize super
        super(wrap);

        mInstance = this;

        // Initialize internal structures
        oad_identity        = new OADIdentity(mPwrap);
        oad_block           = new OADBlock(mPwrap);
    }

    public int initialize() {
        mInitialized = true;
        return 0;
    }
}
