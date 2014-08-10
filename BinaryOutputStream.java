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

import org.rrlib.serialization.rtti.DataTypeBase;
import org.rrlib.xml.XMLDocument;
import org.rrlib.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Reasonably efficient, flexible, universal writing interface.
 *
 * Flexible "all-in-one" output stream memory buffer interface that implements various interfaces.
 * (in Java it can be combined with Streams and ByteBuffers, in C++ with output streams and boost iostreams)
 *
 * This class provides a universal data writing interface for memory buffers.
 * A manager class needs to be specified, which will customize what is actually done with the data.
 *
 * The implementation is designed to be reasonably efficient (no virtual function calls in C++; except of committing/fetching
 * data chunks from streams... however, this doesn't happen often and should not harm performance) -
 * that's why no interfaces are used for serialization, but rather the class itself. Support for further
 * read and write methods can be easily added.
 *
 * Size checking is performed for every write and read operation. For maximum performance, arrays/buffers can be used to
 * write and read data to/from this class. Buffers can be forwarded to a sink directly (they don't need to be buffered) avoiding
 * additional copying operations.
 *
 * The Class is explicitly _not_ thread-safe for writing - meaning multiple threads may not write to the same object at any given
 * moment in time.
 *
 * There are two modes of operation with respect to print-methods:
 *  1) flush immediately
 *  2) flush when requested or full
 */
public class BinaryOutputStream {

    /** Committed buffers are buffered/copied (not forwarded directly), when smaller than 1/(2^n) of buffer capacity */
    private static double BUFFER_COPY_FRACTION = 0.25;

    /** Source that determines where buffers that are written to come from and how they are handled */
    private Sink sink = null;

    /** Immediately flush buffer after printing? */
    private final boolean immediateFlush = false;

    /** Has stream been closed? */
    private boolean closed = true;

    /** Buffer that is currently written to - is managed by sink */
    private BufferInfo buffer = new BufferInfo();

    /** -1 by default - buffer position when a skip offset placeholder has been set/written */
    private int curSkipOffsetPlaceholder = -1;

    /** if true, indicates that only 1 byte has been reserved for skip offset placeholder */
    private boolean shortSkipOffset;

    /** hole Buffers are only buffered/copied, when they are smaller than this */
    private int bufferCopyFraction;

    /** Is direct write support available with this sink? */
    private boolean directWriteSupport = false;

    /** Data type encoding */
    public enum TypeEncoding {
        LocalUids, // use local uids. fastest. Can, however, only be decoded in this runtime.
        Names, // use names. Can be decoded in any runtime that knows types.
        Custom // use custom type codec
    }

    /** Data type encoding that is used */
    private TypeEncoding encoding;

    /** Custom type encoder */
    private TypeEncoder customEncoder;

    public BinaryOutputStream() {
        this(TypeEncoding.LocalUids);
    }

    public BinaryOutputStream(Sink sink_) {
        this(sink_, TypeEncoding.LocalUids);
    }

    /**
     * @param encoding Data type encoding that is used
     */
    public BinaryOutputStream(TypeEncoding encoding) {
        this.encoding = encoding;
    }

    public BinaryOutputStream(TypeEncoder encoder) {
        customEncoder = encoder;
        encoding = TypeEncoding.Custom;
    }

    /**
     * @param sink_ Sink to write to
     * @param encoding Data type encoding that is used
     */
    public BinaryOutputStream(Sink sink_, TypeEncoding encoding) {
        this.encoding = encoding;
        reset(sink_);
    }

    /**
     * @param sink_ Sink to write to
     * @param encoder Custom type encoder
     */
    public BinaryOutputStream(Sink sink_, TypeEncoder encoder) {
        customEncoder = encoder;
        encoding = TypeEncoding.Custom;
        reset(sink_);
    }

    /**
     * @return Size of data that was written to buffer
     * (typically useless - because of flushing etc. - only used by some internal stuff)
     */
    public int getWriteSize() {
        return buffer.position - buffer.start;
    }

    /**
     * @return Bytes remaining (for writing) in this buffer
     */
    public int remaining() {
        return buffer.remaining();
    }

    /**
     * Use buffer with different sink (closes old one)
     *
     * @param sink New Sink to use
     */
    public void reset(Sink sink) {
        close();
        this.sink = sink;
        reset();
    }

    /**
     * Resets/clears buffer for writing
     */
    public void reset() {
        sink.reset(this, buffer);
        assert(buffer.remaining() >= 8);
        closed = false;
        bufferCopyFraction = (int)(buffer.capacity() * BUFFER_COPY_FRACTION);
        directWriteSupport = sink.directWriteSupport();
    }

    /**
     * Write null-terminated string
     *
     * @param s String
     * @param terminate Terminate string with zero?
     */
    public void writeUnicode(String s, boolean terminate) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            writeChar(s.charAt(i));
        }
        if (terminate) {
            writeChar((char)0);
        }
    }

    /**
     * Write null-terminated string (8 Bit Characters - Suited for ASCII)
     *
     * @param s String
     */
    public void writeString(String s) {
        writeString(s, true);
    }

    /**
     * Write null-terminated string (8 Bit Characters - Suited for ASCII)
     *
     * @param sb StringBuilder object
     */
    public void writeString(StringBuilder sb) {
        writeString(sb.toString());
    }

    /**
     * Write string (8 Bit Characters - Suited for ASCII)
     *
     * @param s String
     * @param terminate Terminate string with zero?
     */
    public void writeString(String s, boolean terminate) {
        write(s.getBytes());
        if (terminate) {
            writeByte(0);
        }
    }

    /**
     * Flush current buffer contents to sink and clear buffer.
     * (with no immediate intent to write further data to buffer)
     */
    public void flush() {
        commitData(-1);
        sink.flush(this, buffer);
    }

    /**
     * Write current buffer contents to sink and clear buffer.
     *
     * @param addSizeHint Hint at how many additional bytes we want to write; -1 indicates manual flush without need for size increase
     */
    protected void commitData(int addSizeHint) {
        if (getWriteSize() > 0) {
            if (sink.write(this, buffer, addSizeHint)) {
                assert(curSkipOffsetPlaceholder < 0);
            }
            assert(addSizeHint < 0 || buffer.remaining() >= 8);
            bufferCopyFraction = (int)(buffer.capacity() * BUFFER_COPY_FRACTION);
        }
    }

    /**
     * Writes all bytes from array to buffer
     *
     * @param b Array
     */
    public void write(byte[] b) {
        write(b, 0, b.length);
    }

    /**
     * @return Whole Buffers are only buffered/copied, when they are smaller than this
     */
    protected int getCopyFraction() {
        assert(bufferCopyFraction > 0);
        return bufferCopyFraction;
    }

    /**
     * Writes specified bytes from array to buffer
     *
     * @param b Array
     * @param off Offset in array
     * @param len Number of bytes to copy
     */
    public void write(byte[] b, int off, int len) {
        while (len > 0) {
            int write = Math.min(len, remaining());
            buffer.buffer.put(buffer.position, b, off, write);
            buffer.position += write;
            len -= write;
            off += write;
            assert(len >= 0);
            if (len == 0) {
                return;
            }
            commitData(len);
        }
    }

    /**
     * Writes specified byte buffer contents to stream
     * Regarding streams:
     *   Large buffers are directly copied to output device
     *   avoiding an unnecessary copying operation.
     *
     * @param bb ByteBuffer (whole buffer will be copied)
     */
    public void write(FixedBuffer bb) {
        write(bb, 0, bb.capacity());
    }

    /**
     * (Non-virtual variant of above)
     * Writes specified byte buffer contents to stream
     * Regarding streams:
     *   Large buffers are directly copied to output device
     *   avoiding an unnecessary copying operation.
     *
     * @param bb ByteBuffer
     * @param off Offset in buffer
     * @param len Number of bytes to write
     */
    public void write(FixedBuffer bb, int off, int len) {
        if ((remaining() >= len) && (len < getCopyFraction() || curSkipOffsetPlaceholder >= 0)) {
            buffer.buffer.put(buffer.position, bb, off, len);
            buffer.position += len;
        } else {
            if (directWriteSupport && curSkipOffsetPlaceholder < 0) {
                commitData(-1);
                sink.directWrite(this, bb, off, len);
            } else {
                while (len > 0) {
                    int write = Math.min(len, remaining());
                    buffer.buffer.put(buffer.position, bb, off, write);
                    buffer.position += write;
                    len -= write;
                    off += write;
                    assert(len >= 0);
                    if (len == 0) {
                        return;
                    }
                    commitData(len);
                }
            }
        }
    }

    /**
     * Ensure that the specified number of bytes is available in buffer.
     * Possibly resize or flush.
     *
     * @param c Number of Bytes.
     */
    public void ensureAdditionalCapacity(int c) {
        if (remaining() < c) {
            commitData(c - remaining());
        }
    }

    /**
     * Immediately flush buffer if appropriate option is set
     * Used in print methods
     */
    protected void checkFlush() {
        if (immediateFlush) {
            flush();
        }
    }

    /**
     * @param v (1-byte) boolean
     */
    public void writeBoolean(boolean v) {
        writeByte(v ? 1 : 0);
    }

    /**
     * @param v 8 bit integer
     */
    public void writeByte(int v) {
        ensureAdditionalCapacity(1);
        buffer.buffer.putByte(buffer.position, v);
        buffer.position++;
    }

    /**
     * @param v Character
     */
    public void writeChar(char v) {
        ensureAdditionalCapacity(2);
        buffer.buffer.putChar(buffer.position, v);
        buffer.position += 2;
    }

    /**
     * @param v 32 bit integer
     */
    public void writeInt(int v) {
        ensureAdditionalCapacity(4);
        buffer.buffer.putInt(buffer.position, v);
        buffer.position += 4;
    }

    /**
     * @param v 64 bit integer
     */
    public void writeLong(long v) {
        ensureAdditionalCapacity(8);
        buffer.buffer.putLong(buffer.position, v);
        buffer.position += 8;
    }

    /**
     * @param v 16 bit integer
     */
    public void writeShort(int v) {
        ensureAdditionalCapacity(2);
        buffer.buffer.putShort(buffer.position, (short)v);
        buffer.position += 2;
    }

    /**
     * @param v 64 bit floating point
     */
    public void writeDouble(double v) {
        ensureAdditionalCapacity(8);
        buffer.buffer.putDouble(buffer.position, v);
        buffer.position += 8;
    }

    /**
     * @param v 32 bit floating point
     */
    public void writeFloat(float v) {
        ensureAdditionalCapacity(4);
        buffer.buffer.putFloat(buffer.position, v);
        buffer.position += 4;
    }

    /**
     * @param e Enum constant
     */
    public void writeEnum(Enum<?> e) {
        writeEnum(e.ordinal(), e.getDeclaringClass().getEnumConstants());
    }

    /**
     * @param value Enum value (ordinal)
     * @param constants All enum constants
     */
    public void writeEnum(int value, Object[] constants) {
        if (constants.length == 0) {
            assert(value < 0x7FFFFFFF) : "What?";
            writeInt(value);
        } else if (constants.length <= 0x100) {
            writeByte((byte) value);
        } else if (constants.length <= 0x1000) {
            writeShort((short) value);
        } else {
            assert(constants.length < 0x7FFFFFFF) : "What?";
            writeInt(value);
        }
    }

    /**
     * Print line to StreamBuffer.
     *
     * @param s Line to print
     */
    public void println(String s) {
        writeString(s, false);
        writeByte('\n');
        checkFlush();
    }

    /**
     * Print String to StreamBuffer.
     *
     * @param s Line to print
     */
    public void print(String s) {
        writeString(s, false);
        checkFlush();
    }

    public String toString() {
        return "OutputStreamBuffer - " + buffer.toString();
    }

    public void close() {
        if (!closed) {
            flush();
            sink.close(this, buffer);
        }
        closed = true;
    }

    /**
     * A "skip offset" will be written to this position in the stream.
     *
     * (only one such position can be set/remembered at a time)
     *
     * As soon as the stream has reached the position to which are reader might want to skip
     * call setSkipTargetHere()
     *
     * @param short_skip_offset If skip offset will be smaller than 256, can be set to true, to make stream 3 bytes shorter
     */
    public void writeSkipOffsetPlaceholder(boolean shortSkipOffset) {
        assert(curSkipOffsetPlaceholder < 0);
        curSkipOffsetPlaceholder = buffer.position;
        this.shortSkipOffset = shortSkipOffset;

        if (shortSkipOffset) {
            writeByte(Byte.MIN_VALUE);
        } else {
            writeInt(Integer.MIN_VALUE);
        }
    }

    /**
     * A "skip offset" will be written to this position in the stream.
     *
     * (only one such position can be set/remembered at a time)
     *
     * As soon as the stream has reached the position to which are reader might want to skip
     * call setSkipTargetHere()
     */
    public void writeSkipOffsetPlaceholder() {
        writeSkipOffsetPlaceholder(false);
    }

    /**
     * Set target for last "skip offset" to this position.
     */
    public void skipTargetHere() {
        assert(curSkipOffsetPlaceholder >= 0);
        if (shortSkipOffset) {
            buffer.buffer.putByte(curSkipOffsetPlaceholder, buffer.position - curSkipOffsetPlaceholder - 1);
        } else {
            buffer.buffer.putInt(curSkipOffsetPlaceholder, buffer.position - curSkipOffsetPlaceholder - 4);
        }
        curSkipOffsetPlaceholder = -1;
    }

    /**
     * Write all available data from input stream to this output stream buffer
     *
     * @param inputStream Input Stream
     */
    public void writeAllAvailable(BinaryInputStream inputStream) {
        while (inputStream.moreDataAvailable()) {
            inputStream.ensureAvailable(1);
            write(inputStream.curBuffer.buffer, inputStream.curBuffer.position, inputStream.curBuffer.remaining());
            inputStream.curBuffer.position = inputStream.curBuffer.end;
        }
    }

    /**
     * @param type Data type to write/reference (using encoding specified in constructor)
     */
    public void writeType(DataTypeBase type) {
        type = type == null ? DataTypeBase.NULL_TYPE : type;

        if (encoding == TypeEncoding.LocalUids) {
            writeShort(type.getUid());
        } else if (encoding == TypeEncoding.Names) {
            writeString(type.getName());
        } else {
            customEncoder.writeType(this, type);
        }
    }

    /**
     * Write object to stream (without any type information)
     *
     * @param object Object to write to stream
     * @param type Type of object (if serialization is consistent, could be base class)
     */
    public void writeObject(Object object) {
        writeObject(object, object.getClass());
    }

    /**
     * Write object to stream (without any type information)
     *
     * @param object Object to write to stream
     * @param type Type of object (if serialization is consistent, could be base class)
     */
    @SuppressWarnings("rawtypes")
    public void writeObject(Object object, Class<?> type) {
        if (BinarySerializable.class.isAssignableFrom(type)) {
            ((BinarySerializable)object).serialize(this);
        } else if (type.isPrimitive()) {
            if (type == byte.class) {
                writeByte((Byte)object);
            } else if (type == short.class) {
                writeShort((Short)object);
            } else if (type == int.class) {
                writeInt((Integer)object);
            } else if (type == long.class) {
                writeLong((Long)object);
            } else if (type == float.class) {
                writeFloat((Float)object);
            } else if (type == double.class) {
                writeDouble((Double)object);
            } else if (type == boolean.class) {
                writeBoolean((Boolean)object);
            } else {
                throw new RuntimeException("Unsupported primitive type");
            }
        } else {
            assert(object != null && (object.getClass() == type));
            if (type.isEnum()) {
                if (object.getClass().equals(EnumValue.class)) {
                    ((EnumValue)object).serialize(this);
                } else {
                    writeEnum((Enum)object);
                }
            } else if (type == String.class) {
                writeString(object.toString());
            } else {
                throw new RuntimeException("Unsupported type");
            }
        }
    }

    /**
     * Write object to stream (without any type information)
     *
     * @param object Object to write to stream
     * @param type Type of object (if serialization is consistent, could be base class)
     * @param encoding Desired data encoding
     */
    public void writeObject(Object object, Class<?> type, Serialization.DataEncoding encoding) {
        if (encoding == Serialization.DataEncoding.BINARY) {
            writeObject(object, type);
        } else if (encoding == Serialization.DataEncoding.STRING) {
            writeString(Serialization.serialize(object));
        } else {
            assert(encoding == Serialization.DataEncoding.XML);
            XMLDocument d = new XMLDocument();
            try {
                XMLNode n = d.addRootNode("value");
                Serialization.serialize(n, object);
                writeString(d.getXMLDump(true));
            } catch (Exception e) {
                e.printStackTrace();
                writeString("error generating XML code.");
            }
        }
    }

//    /**
//     * Serialize Object of arbitrary type to stream
//     * (including type information)
//     *
//     * @param to Object to write (may be null)
//     * @param enc Data encoding to use
//     */
//    public void writeObject(GenericObject to, Serialization.DataEncoding enc) {
//        if (to == null) {
//            writeType(null);
//            return;
//        }
//
//        //writeSkipOffsetPlaceholder();
//        writeType(to.getType());
//        to.serialize(this, enc);
//        //skipTargetHere();
//    }

    /**
     * @param duration Duration in ms
     */
    public void writeDuration(long duration) {
        writeLong(duration * 1000000);
    }
}
