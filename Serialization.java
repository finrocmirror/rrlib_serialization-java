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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.Arrays;

import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.serialization.rtti.Copyable;
import org.rrlib.serialization.rtti.Factory;
import org.rrlib.serialization.rtti.GenericObject;
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

    /** Classloader to use for deep copies via Java native binary serialization */
    private static ClassLoader deepCopyClassLoader = Serialization.class.getClassLoader();

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
     * Serialize object to XML node (without any type information)
     *
     * @param node XML node
     * @param object Serializable object
     */
    public static void serialize(XMLNode node, Object object) throws Exception {
        Class<?> type = object.getClass();
        if (XMLSerializable.class.isAssignableFrom(type)) {
            ((XMLSerializable)object).serialize(node);
        } else { // try string serialization (if this does not work, we would have to throw an exception anyway, because there are currently no more alternatives)
            node.setContent(serialize(object));
        }
    }

    /**
     * Deserialize object from XML node (without any type information)
     *
     * @param node XML node
     * @param deserializeTo Object to call deserialize() on (optional; will contain result of deserialization, in case type is a mutable type)
     * @param type Type object must have
     * @return Deserialized object (new object for immutable types, provided object in case of a mutable type)
     */
    public static Object deserialize(XMLNode node, Object deserializeTo, Class<?> type) throws Exception {
        if (XMLSerializable.class.isAssignableFrom(type)) {
            if (deserializeTo == null) {
                deserializeTo = type.newInstance();
            }
            ((XMLSerializable)deserializeTo).deserialize(node);
            return deserializeTo;
        } else { // try string deserialization (if this does not work, we would have to throw an exception anyway, because there are currently no more alternatives)
            StringInputStream sis = new StringInputStream(node.getTextContent());
            deserializeTo = sis.readObject(deserializeTo, type);
            sis.close();
            return deserializeTo;
        }
    }

    /**
     * Serializes string stream serializable object to string
     * (convenience function)
     *
     * @param s Serializable
     * @return String
     */
    public static String serialize(Object s) {
        StringOutputStream os = new StringOutputStream();
        os.appendObject(s);
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
     * Creates deep copy of object - using the most efficient available way of creating such a copy:
     *
     * (1) Immutable objects are returned directly (as the JVM would provide the same objects for some types anyway)
     * (2) If the Copyable interface is implemented, it is used
     * (3) Binary serialization if available
     * (4) java.io binary serialization if available
     * (5) String serialization if available
     * (6) XML serialization if available
     *
     * @param src Object to be copied
     * @return Object that is a deep copy of src
     */
    public static <T> T deepCopy(T src) {
        return deepCopy(src, null, null);
    }

    /**
     * Creates deep copy of object - using the most efficient available way of creating such a copy:
     *
     * (1) Immutable objects are returned directly (as the JVM would provide the same objects for some types anyway)
     * (2) If the Copyable interface is implemented, it is used
     * (3) Binary serialization if available
     * (4) java.io binary serialization if available
     * (5) String serialization if available
     * (6) XML serialization if available
     *
     * @param src Object to be copied
     * @param dest Object to copy to (optional; will contain result of copy, in case type is a mutable type)
     * @return Object that is a deep copy of src
     */
    public static <T> T deepCopy(T src, T dest) {
        return deepCopy(src, dest, null);
    }

    /**
     * Creates deep copy of object - using the most efficient available way of creating such a copy:
     *
     * (1) Immutable objects are returned directly (as the JVM would provide the same objects for some types anyway)
     * (2) If the Copyable interface is implemented, it is used
     * (3) Binary serialization if available
     * (4) java.io binary serialization if available
     * (5) String serialization if available
     * (6) XML serialization if available
     *
     * @param src Object to be copied
     * @param dest Object to copy to (optional; will contain result of copy, in case type is a mutable type)
     * @param f Factory to use (optional)
     * @return Object that is a deep copy of src
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> T deepCopy(T src, T dest, Factory f) {
        if (src == null) {
            return null;
        }
        Class<?> type = src.getClass();
        boolean immutable = type.isPrimitive() || Number.class.isAssignableFrom(type) || Boolean.class.equals(type) || String.class.equals(type);
        immutable &= (!Copyable.class.isAssignableFrom(type));
        if (immutable) {
            return src;
        }
        boolean useNativeSerialization = Serializable.class.isAssignableFrom(type) && (!Copyable.class.isAssignableFrom(type)) && (!BinarySerializable.class.isAssignableFrom(type));
        if ((dest == null || dest.getClass() != type) && (!useNativeSerialization)) {
            try {
                dest = (T)type.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (Copyable.class.isAssignableFrom(type)) {
            ((Copyable)dest).copyFrom(src);
            return dest;
        } else if (BinarySerializable.class.isAssignableFrom(type)) {
            MemoryBuffer buf = buffer.get();
            if (buf == null) {
                buf = new MemoryBuffer(16384);
                buffer.set(buf);
            }
            deepCopyImpl((BinarySerializable)src, (BinarySerializable)dest, f, buf);
            return dest;
        }
        try {
            if (useNativeSerialization) {
                ObjectInputStream ois = new ObjectInputStreamUsingPluginClassLoader(new ByteArrayInputStream(toByteArray((Serializable)src)));
                dest = (T)ois.readObject();
                ois.close();
            } else if (StringSerializable.class.isAssignableFrom(type)) {
                String string = serialize(src);
                dest = (T)new StringInputStream(string).readObject(dest, type);
            } else if (XMLSerializable.class.isAssignableFrom(type)) {
                MemoryBuffer buf = buffer.get();
                if (buf == null) {
                    buf = new MemoryBuffer(16384);
                    buffer.set(buf);
                }
                buf.clear();
                BinaryOutputStream stream = new BinaryOutputStream(buf);
                stream.writeObject(src, type, DataEncoding.XML);
                stream.close();
                BinaryInputStream istream = new BinaryInputStream(buf);
                dest = (T)istream.readObject(dest, type);
            } else {
                throw new RuntimeException("Object of type " + src.getClass().getName() + " cannot be deep-copied");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return dest;
    }

    /**
     * Creates deep copy of serializable object using serialization to and from specified memory buffer
     *
     * @param src Object to be copied
     * @param dest Object to copy to
     * @param buf Memory buffer to use
     */
    private static <T extends BinarySerializable> void deepCopyImpl(T src, T dest, Factory f, MemoryBuffer buf) {
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

    /**
     * Serialization-based equals()-method
     * (not very efficient - should therefore not be called regular loops)
     *
     * @param obj1 Object1
     * @param obj2 Object2
     * @returns True if both objects are serialized to the same data (usually they are equal then)
     */
    public static boolean equals(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null || obj1.getClass() != obj2.getClass()) {
            return false;
        }
        if (obj1 instanceof GenericObject) {
            return equals(((GenericObject)obj1).getData(), ((GenericObject)obj2).getData());
        }
        if (obj1 instanceof BinarySerializable) {
            MemoryBuffer buf1 = new MemoryBuffer();
            MemoryBuffer buf2 = new MemoryBuffer();
            BinaryOutputStream os1 = new BinaryOutputStream(buf1);
            BinaryOutputStream os2 = new BinaryOutputStream(buf2);
            os1.writeObject(obj1);
            os2.writeObject(obj2);
            os1.close();
            os2.close();
            return buf1.equals(buf2);
        } else if (obj1 instanceof Serializable) {
            return Arrays.equals(toByteArray((Serializable)obj1), toByteArray((Serializable)obj2));
        } else if (obj1 instanceof StringSerializable) {
            return serialize(obj1).equals(serialize(obj2));
        } else if (obj1 instanceof XMLSerializable) {
            MemoryBuffer buf1 = new MemoryBuffer();
            MemoryBuffer buf2 = new MemoryBuffer();
            BinaryOutputStream os1 = new BinaryOutputStream(buf1);
            BinaryOutputStream os2 = new BinaryOutputStream(buf2);
            os1.writeObject(obj1, obj1.getClass(), DataEncoding.XML);
            os2.writeObject(obj2, obj2.getClass(), DataEncoding.XML);
            os1.close();
            os2.close();
            return buf1.equals(buf2);
        } else {
            throw new RuntimeException("Objects of type " + obj1.getClass().getName() + " cannot be serialized and compared");
        }
    }

    /**
     * Serializes Java-Serializable object to byte array
     *
     * @param t Object to serialize
     * @return Byte array containing serialized object
     */
    public static byte[] toByteArray(Serializable t) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(t);
            oos.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Converting object to byte array failed", e);
        }
    }

    /**
     * Resize vector (also works for vectors with noncopyable types)
     *
     * @param vector Vector to resize
     * @param newSize New Size
     */
    static public <T> void resizeVector(PortDataList<?> vector, int newSize) {
        vector.resize(newSize);
    }

    /**
     * TODO: check whether there's a more elegant way
     *
     * Set class loader used for deep copies.
     * This is only necessary if classes are to be cloned that are accessible
     * by a different class loader only.
     *
     * @param classLoader Class Loader to use
     */
    static public void setDeepCopyClassLoader(ClassLoader classLoader) {
        deepCopyClassLoader = classLoader;
    }

    /**
     * @author jens
     *
     * Local helper class for resolving classes via plugin class loader.
     */
    private static class ObjectInputStreamUsingPluginClassLoader extends ObjectInputStream {

        @Override
        public Class<?> resolveClass(ObjectStreamClass descriptor) throws IOException, ClassNotFoundException {
            try {
                return deepCopyClassLoader.loadClass(descriptor.getName());
            } catch (Exception e) {
            }
            return super.resolveClass(descriptor);
        }

        public ObjectInputStreamUsingPluginClassLoader(InputStream in) throws IOException {
            super(in);
        }

    }
}
