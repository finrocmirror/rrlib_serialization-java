//
// You received this file as part of RRLib
// Robotics Research Library
//
// Copyright (C) Finroc GbR (finroc.org)
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//----------------------------------------------------------------------
package org.rrlib.serialization;

import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.serialization.rtti.Factory;
import org.rrlib.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Helper class:
 * Serializes binary CoreSerializables to hex string - and vice versa.
 */
public class Serialization {

    /** Enum for different types of data encoding */
    public enum DataEncoding { BINARY, STRING, XML }

    /** int -> hex char */
    private static final char[] TO_HEX = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    /** hex char -> int */
    private static final int[] TO_INT = new int[256];

    /** Helper variable to trigger static initialization in C++ */
    @SuppressWarnings("unused")
    private static int INIT_HELPER = staticInit();

    /** may only be accessed in synchronized context */
    private static final ThreadLocal<MemoryBuffer> buffer = new ThreadLocal<MemoryBuffer>();

    public static int staticInit() {
        for (int i = 0; i < 256; i++) {
            TO_INT[i] = -1;
        }
        TO_INT['0'] = 0;
        TO_INT['1'] = 1;
        TO_INT['2'] = 2;
        TO_INT['3'] = 3;
        TO_INT['4'] = 4;
        TO_INT['5'] = 5;
        TO_INT['6'] = 6;
        TO_INT['7'] = 7;
        TO_INT['8'] = 8;
        TO_INT['9'] = 9;
        TO_INT['A'] = 0xA;
        TO_INT['B'] = 0xB;
        TO_INT['C'] = 0xC;
        TO_INT['D'] = 0xD;
        TO_INT['E'] = 0xE;
        TO_INT['F'] = 0xF;
        TO_INT['a'] = 0xA;
        TO_INT['b'] = 0xB;
        TO_INT['c'] = 0xC;
        TO_INT['d'] = 0xD;
        TO_INT['e'] = 0xE;
        TO_INT['f'] = 0xF;
        return 0;
    }

    /**
     * Serializes BinarySerializable to hex string
     *
     * @param cs BinarySerializable
     * @param os String output stream
     */
    public static void serializeToHexString(BinarySerializable cs, StringOutputStream os) {
        MemoryBuffer cb = new MemoryBuffer();
        BinaryOutputStream co = new BinaryOutputStream(cb);
        cs.serialize(co);
        co.close();
        BinaryInputStream ci = new BinaryInputStream(cb);
        convertBinaryToHexString(ci, os);
        ci.close();
    }

    /**
     * Converts binary to hex string
     *
     * @param src Input stream that contains binary data
     * @param co Output stream to write hex string to
     */
    public static void convertBinaryToHexString(BinaryInputStream src, StringOutputStream os) {
        while (src.moreDataAvailable()) {
            byte b = src.readByte();
            int bi = b & 0xFF;
            int b1 = bi >>> 4;
            int b2 = bi & 0xF;
            assert(b1 >= 0 && b1 < 16);
            assert(b2 >= 0 && b2 < 16);
            os.append(TO_HEX[b1]);
            os.append(TO_HEX[b2]);
        }
    }

    /**
     * Deserializes binary CoreSerializable from hex string
     *
     * @param cs CoreSerializable
     * @param s Hex String to deserialize from
     */
    public static void deserializeFromHexString(BinarySerializable cs, StringInputStream s) throws Exception {
        MemoryBuffer cb = new MemoryBuffer();
        BinaryOutputStream co = new BinaryOutputStream(cb);
        convertHexStringToBinary(s, co);
        co.close();
        BinaryInputStream ci = new BinaryInputStream(cb);
        cs.deserialize(ci);
        ci.close();
    }

    /**
     * Converts hex string from StringInputStream to binary
     *
     * @param src Input stream that contains hex string
     * @param co Output stream to write binary data to
     */
    public static void convertHexStringToBinary(StringInputStream src, BinaryOutputStream co) throws Exception {
        int c1;
        while ((c1 = src.read()) != -1) {
            int c2 = src.read();
            if (c2 == -1) {
                throw new Exception("not a valid hex string (should have even number of chars)");
            }
            if (TO_INT[c1] < 0 || TO_INT[c2] < 0) {
                throw new Exception("invalid hex chars: " + c1 + c2);
            }
            int b = (TO_INT[c1] << 4) | TO_INT[c2];
            co.writeByte((byte)b);
        }
    }

    /**
     * Standard XML serialization fallback implementation
     * (for Java, because we don't have multiple inheritance here)
     *
     * @param node XML node
     * @param rs Serializable object
     */
    public static void serialize(XMLNode node, StringSerializable rs) throws Exception {
        node.setContent(serialize(rs));
    }

    /**
     * Serializes string stream serializable object to string
     * (convenience function)
     *
     * @param cs Serializable
     * @return String
     */
    public static String serialize(StringSerializable rs) {
        StringOutputStream os = new StringOutputStream();
        rs.serialize(os);
        return os.toString();
    }

//    /**
//     * Serializes generic object to string
//     * (convenience function)
//     *
//     * @param cs Serializable
//     * @return String
//     */
//    public static String serialize(GenericObject go) {
//        StringOutputStream os = new StringOutputStream();
//        go.serialize(os);
//        return os.toString();
//    }

    /**
     * Serializes enum constant to string
     * (convenience function)
     *
     * @param e Enum constant
     * @return String
     */
    public static String serialize(Enum<?> e) {
        StringOutputStream os = new StringOutputStream();
        os.append(e);
        return os.toString();
    }

    /**
     * Deserializes enum constant from string
     * (convenience function)
     *
     * @param s String to deserialize from
     * @param eclass Enum class that enum belongs to
     * @return Enum constant
     */
    @SuppressWarnings("rawtypes")
    public static <E extends Enum> E deserialize(String s, Class<E> eclass) {
        StringInputStream os = new StringInputStream(s);
        return os.readEnum(eclass);
    }


    /**
     * Creates deep copy of serializable object
     *
     * @param src Object to be copied
     * @param dest Object to copy to
     */
    public static <T extends BinarySerializable> void deepCopy(T src, T dest, Factory f) {
        MemoryBuffer buf = buffer.get();
        if (buf == null) {
            buf = new MemoryBuffer(16384);
            buffer.set(buf);
        }
        deepCopyImpl(src, dest, f, buf);
    }

    /**
     * Creates deep copy of serializable object using serialization to and from specified memory buffer
     *
     * @param src Object to be copied
     * @param dest Object to copy to
     * @param buf Memory buffer to use
     */
    public static <T extends BinarySerializable> void deepCopyImpl(T src, T dest, Factory f, MemoryBuffer buf) {
        buf.clear();
        BinaryOutputStream os = new BinaryOutputStream(buf);
        src.serialize(os);

        os.close();
        BinaryInputStream ci = new BinaryInputStream(buf);
        //ci.setFactory(f);

        try {
            dest.deserialize(ci);
        } catch (Exception e) {
            Log.log(LogLevel.ERROR, e); // If this happens, serialization of objects of type T is not implemented correctly
        }
        ci.close();
    }

//    /**
//     * Serialization-based equals()-method
//     * (not very efficient/RT-capable - should therefore not be called regular loops)
//     *
//     * @param obj1 Object1
//     * @param obj2 Object2
//     * @returns true if both objects are serialized to the same binary data (usually they are equal then)
//     */
//    public static boolean equals(GenericObject obj1, GenericObject obj2) {
//        if (obj1 == obj2) {
//            return true;
//        }
//        if (obj1 == null || obj2 == null || obj1.getType() != obj2.getType()) {
//            return false;
//        }
//
//        MemoryBuffer buf1 = new MemoryBuffer();
//        MemoryBuffer buf2 = new MemoryBuffer();
//        BinaryOutputStream os1 = new BinaryOutputStream(buf1);
//        BinaryOutputStream os2 = new BinaryOutputStream(buf2);
//        obj1.serialize(os1);
//        obj2.serialize(os2);
//        os1.close();
//        os2.close();
//
//        if (buf1.getSize() != buf2.getSize()) {
//            return false;
//        }
//
//        BinaryInputStream is1 = new BinaryInputStream(buf1);
//        BinaryInputStream is2 = new BinaryInputStream(buf2);
//
//        for (int i = 0; i < buf1.getSize(); i++) {
//            if (is1.readByte() != is2.readByte()) {
//                return false;
//            }
//        }
//        return true;
//    }

    /**
     * Resize vector (also works for vectors with noncopyable types)
     *
     * @param vector Vector to resize
     * @param newSize New Size
     */
    static public <T> void resizeVector(PortDataList<?> vector, int newSize) {
        vector.resize(newSize);
    }

//    /**
//     * Serialize data to binary output stream - possibly using non-binary encoding
//     *
//     * @param os Binary output stream
//     * @param s Object to serialize
//     * @param enc Encoding to use
//     */
//    static public void serialize(BinaryOutputStream os, RRLibSerializable s, DataEncoding enc) {
//        if (enc == DataEncoding.BINARY) {
//            s.serialize(os);
//        } else if (enc == DataEncoding.STRING) {
//            os.writeString(serialize(s));
//        } else {
//            assert(enc == DataEncoding.XML);
//            XMLDocument d = new XMLDocument();
//            try {
//                XMLNode n = d.addRootNode("value");
//                s.serialize(n);
//                os.writeString(d.getXMLDump(true));
//            } catch (Exception e) {
//                e.printStackTrace();
//                os.writeString("error generating XML code.");
//            }
//        }
//    }
//
//    /**
//     * Deserialize data from binary input stream - possibly using non-binary encoding
//     *
//     * @param os Binary input stream
//     * @param s Object to deserialize
//     * @param enc Encoding to use
//     */
//    static public void deserialize(BinaryInputStream is, RRLibSerializable s, DataEncoding enc) {
//        if (enc == DataEncoding.BINARY) {
//            s.deserialize(is);
//        } else if (enc == DataEncoding.STRING) {
//            StringInputStream sis = new StringInputStream(is.readString());
//            try {
//                s.deserialize(sis);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            sis.close();
//        } else {
//            assert(enc == DataEncoding.XML);
//            try {
//                XMLDocument d = new XMLDocument(new InputSource(new StringReader(is.readString())), false);
//                XMLNode n = d.getRootNode();
//                s.deserialize(n);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }
}
