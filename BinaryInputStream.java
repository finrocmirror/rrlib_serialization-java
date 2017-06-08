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

import java.io.ByteArrayOutputStream;
import java.io.StringReader;

import org.rrlib.serialization.compression.Compressible;
import org.rrlib.serialization.compression.DataCompressor;
import org.rrlib.xml.XMLDocument;
import org.rrlib.xml.XMLNode;
import org.xml.sax.InputSource;

/**
 * @author Max Reichardt
 *
 * Reasonably efficient, flexible, universal reading interface.
 * A manager class customizes its behaviour (whether it reads from file, memory block, chunked buffer, etc.)
 * It handles, where the data blocks actually come from.
 */
public class BinaryInputStream {

    /** Buffer that is managed by source */
    protected BufferInfo sourceBuffer = new BufferInfo();

    /** Small buffer to enable reading data that crosses buffer boundaries */
    protected BufferInfo boundaryBuffer = new BufferInfo();

    /** Actual boundary buffer backend - symmetric layout: 7 bit old bytes - 7 bit new bytes */
    protected FixedBuffer boundaryBufferBackend = new FixedBuffer(14);

    /** Current buffer - either sourceBuffer or boundary buffer */
    protected BufferInfo curBuffer = null;

    /** Manager that handles, where the data blocks come from etc. */
    protected Source source = null;

    /** Current absolute buffer read position of buffer start - relevant when using Source; 64bit value, because we might transfer several GB over a stream */
    protected long absoluteReadPos = 0;

    /** (Absolute) skip offset target - if one has been read - otherwise -1 */
    protected long curSkipOffsetTarget = -1;

    /** Has stream/source been closed? */
    protected boolean closed = false;

    /** Is direct read support available with this sink? */
    protected boolean directReadSupport = false;

    /** timeout for blocking calls (<= 0 when disabled) */
    protected int timeout = -1;

    /** ByteArrayInputStream helper for reading strings in Java */
    protected ByteArrayOutputStream baos = new ByteArrayOutputStream();

    /** Info on source that created data currently read (at least the included revision is required for deserialization) */
    private SerializationInfo serializationSource;

    /** Replicated remote registers */
    PublishedRegisters.RemoteRegister[] remoteRegisters;


    public BinaryInputStream() {}

    /**
     * @param source Source to use
     */
    public BinaryInputStream(Source source) {
        this(source, new SerializationInfo());
    }

    /**
     * @param source Source to use
     * @param sourceInfo Info on source that created data currently read (at least the included revision is required for deserialization)
     */
    public BinaryInputStream(Source source, SerializationInfo sourceInfo) {
        reset(source, sourceInfo);
    }

    /**
     * @param source Source to use
     * @param sharedSerializationInfoFrom Serialization info (SerializationInfo and published registers) is taken from and shared with this input stream
     */
    public BinaryInputStream(Source source, BinaryInputStream sharedSerializationInfoFrom) {
        reset(source, sharedSerializationInfoFrom);
    }

    /**
     * @return Info on source that created data currently read (at least the included revision is required for deserialization)
     */
    public SerializationInfo getSourceInfo() {
        return serializationSource;
    }

    /**
     * @return Remaining size of data in wrapped current intermediate buffer.
     * There might actually be more to read in following buffers though.
     * (Therefore, this is usually pretty useless - some internal implementations use it though)
     */
    public int remaining() {
        return curBuffer.remaining();
    }

    /**
     * Resets buffer for reading - may not be supported by all managers
     */
    public void reset() {
        source.reset(this, sourceBuffer);
        directReadSupport = source.directReadSupport();
        closed = false;
        curBuffer = sourceBuffer;
        absoluteReadPos = 0;
    }

    /**
     * Use this object with different source.
     * Current source will be closed.
     *
     * @param source New Source
     */
    public void reset(Source source) {
        reset(source, new SerializationInfo());
    }

    /**
     * Use this object with different source.
     * Current source will be closed.
     *
     * @param source New Source
     * @param sourceInfo Info on source that created data currently read (at least the included revision is required for deserialization)
     */
    public void reset(Source source, SerializationInfo sourceInfo) {
        close();
        this.source = source;
        setSerializationSource(sourceInfo);
        reset();
    }

    /**
     * Sets shared serialization source of this stream without changing sink
     *
     * @param sourceInfo Info on source that created data currently read (at least the included revision is required for deserialization)
     */
    public void setSerializationSource(SerializationInfo sourceInfo) {
        this.serializationSource = sourceInfo;
        if (sourceInfo.hasPublishedRegisters()) {
            this.remoteRegisters = new PublishedRegisters.RemoteRegister[SerializationInfo.MAX_PUBLISHED_REGISTERS];
            for (int i = 0; i < SerializationInfo.MAX_PUBLISHED_REGISTERS; i++) {
                if (sourceInfo.getRegisterEntryEncoding(i) == SerializationInfo.RegisterEntryEncoding.PUBLISH_REGISTER_ON_CHANGE || sourceInfo.getRegisterEntryEncoding(i) == SerializationInfo.RegisterEntryEncoding.PUBLISH_REGISTER_ON_DEMAND) {
                    this.remoteRegisters[i] = PublishedRegisters.get(i).createRemoteRegister();
                }
            }

        } else {
            this.remoteRegisters = null;
        }
    }


    /**
     * Use this object with different source.
     * Current source will be closed.
     *
     * @param source New Source
     * @param sharedSerializationInfoFrom Serialization info (SerializationInfo and published registers) is taken from and shared with this input stream
     */
    public void reset(Source source, BinaryInputStream sharedSerializationInfoFrom) {
        close();
        this.source = source;
        this.serializationSource = sharedSerializationInfoFrom.serializationSource;
        this.remoteRegisters = sharedSerializationInfoFrom.remoteRegisters;
        reset();
    }

    /**
     * Sets shared serialization info of this stream without changing source
     *
     * @param sharedSerializationInfoFrom Serialization info (SerializationInfo and published registers) is taken from and shared with this input stream
     */
    public void setSharedSerializationInfo(BinaryInputStream sharedSerializationInfoFrom) {
        this.serializationSource = sharedSerializationInfoFrom.serializationSource;
        this.remoteRegisters = sharedSerializationInfoFrom.remoteRegisters;
    }

    /**
     * Sets shared serialization info of this stream without changing source
     *
     * @param sourceInfo Info on source that created data currently read
     * @param remoteRegistersFrom Remote registers from this stream are used
     */
    public void setSharedSerializationInfo(SerializationInfo sourceInfo, BinaryInputStream remoteRegistersFrom) {
        this.serializationSource = sourceInfo;
        this.remoteRegisters = remoteRegistersFrom.remoteRegisters;
    }

    /**
     * In case of source change: Cleanup
     */
    public void close() {
        if (!closed) {
            if (source != null) {
                source.close(this, sourceBuffer);
            }
        }
        closed = true;
    }

    /**
     * Read null-terminated string (16 Bit Characters)
     *
     * @param s String
     */
    public String readUnicode() {
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = readChar();
            if (c == 0) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Read string (16 Bit Characters). Stops at null-termination or specified length.
     *
     * @param length Length of string to read (including possible termination)
     */
    public String readUnicode(int length) {
        StringBuilder sb = new StringBuilder(length);
        int count = 0;
        while (true) {
            char c = readChar();
            if (c == 0 || count == length) {
                break;
            }
            sb.append(c);
            count++;
        }
        return sb.toString();
    }

    /**
     * Skips null-terminated string (16 Bit Characters)
     */
    public void skipUnicode() {
        while (true) {
            char c = readChar();
            if (c == 0) {
                break;
            }
        }
    }

    /**
     * Read null-terminated string (8 Bit Characters - Suited for ASCII)
     *
     * @return String
     */
    public String readString() {
        StringBuilder sb = new StringBuilder(); // no shortcut in C++, since String could be in this chunk only partly
        readStringImpl(sb);
        return sb.toString();
    }

    /**
     * Read null-terminated string (8 Bit Characters - Suited for ASCII)
     *
     * @param sb StringOutputStream object to write result to
     */
    public void readString(StringOutputStream sb) {
        sb.clear();
        readStringImpl(sb.wrapped);
    }

    /**
     * Read null-terminated string (8 Bit Characters - Suited for ASCII)
     *
     * @param sb StringBuilder object to write result to
     */
    public void readString(StringBuilder sb) {
        sb.delete(0, sb.length());
        readStringImpl(sb);
    }

    /**
     * Read null-terminated string (8 Bit Characters - Suited for ASCII)
     *
     * @param sb StringBuilder object to write result to
     */
    public void readStringImpl(StringBuilder sb) {

        baos.reset();

        while (true) {
            byte b = readByte();
            if (b == 0) {
                break;
            }
            baos.write(b);
        }
        sb.append(baos.toString());

    }

    /**
     * Read string (8 Bit Characters - Suited for ASCII). Stops at null-termination or specified length.
     *
     * @param length Length of string to read (including possible termination)
     * @return String
     */
    public String readString(int length) {
        StringOutputStream sb = new StringOutputStream(); // no shortcut in C++, since String could be in this chunk only partly
        readString(sb, length);
        return sb.toString();
    }

    /**
     * Read string (8 Bit Characters - Suited for ASCII). Stops at null-termination or specified length.
     *
     * @param sb StringBuilder object to write result to
     * @param length Length of string to read (including possible termination)
     */
    public void readString(StringOutputStream sb, int length) {
        int count = 0;
        while (true) {
            byte b = readByte();
            if (b == 0 || count == length) {
                break;
            }
            sb.append(b);
            count++;
        }
    }

    /**
     * Skips null-terminated string (8 Bit Characters)
     */
    public void skipString() {
        while (true) {
            byte c = readByte();
            if (c == 0) {
                break;
            }
        }
    }

    /**
     * Ensures that the specified number of bytes is available for reading
     */
    protected void ensureAvailable(int required) {
        assert(!closed);
        int available = remaining();
        if (available < required) {
            // copy rest to beginning and get next bytes from input
            fetchNextBytes(required - available);
            assert(remaining() >= required);
            //  throw new RuntimeException("Attempt to read outside of buffer");
        }
    }

    /**
     * Fills buffer with contents from input stream
     *
     * @param minRequired2 Minimum number of bytes to read (block otherwise)
     */
    protected void fetchNextBytes(int minRequired2) {
        assert(minRequired2 <= 8);
        assert(source != null);

        // are we finished using boundary buffer?
        if (usingBoundaryBuffer() && boundaryBuffer.position >= 7) {
            absoluteReadPos += 7;
            curBuffer = sourceBuffer;
            ensureAvailable(minRequired2);
            return;
        }

        // put any remaining bytes in boundary buffer
        int remain = remaining();
        absoluteReadPos += (curBuffer.end - curBuffer.start);
        if (remain > 0) {
            boundaryBuffer.position = 7 - remain;
            boundaryBuffer.start = 0;
            absoluteReadPos -= 7;
            //boundaryBuffer.buffer.put(boundaryBuffer.position, sourceBuffer.buffer, sourceBuffer.position, remain);
            sourceBuffer.buffer.get(sourceBuffer.position, boundaryBuffer.buffer, boundaryBuffer.position, remain); // equivalent, but without problem that SourceBuffer limit is changed in java
            curBuffer = boundaryBuffer;
        }

        // if we have a timeout set - wait until more data is available
        // TODO: this doesn't ensure that there are minRequired2 bytes available. However, it should be sufficient in 99.9% of the cases.
        if (timeout > 0) {
            int initialSleep = 20; // timeout-related
            int slept = 0; // timeout-related
            while (timeout > 0 && (!source.moreDataAvailable(this, sourceBuffer))) {
                initialSleep *= 2;
                try {
                    Thread.sleep(initialSleep);
                } catch (InterruptedException e) {}
                slept += initialSleep;
                if (slept > timeout) {
                    throw new RuntimeException("Read Timeout");
                }
            }
        }

        // read next block
        source.read(this, sourceBuffer, minRequired2);
        assert(sourceBuffer.remaining() >= minRequired2);
        assert(sourceBuffer.position >= 0);

        // (possibly) fill up boundary buffer
        if (remain > 0) {
            //boundaryBuffer.buffer.put(7, sourceBuffer.buffer, 0, minRequired2);
            sourceBuffer.buffer.get(0, boundaryBuffer.buffer, 7, minRequired2);
            boundaryBuffer.end = 7 + minRequired2;
            sourceBuffer.position += minRequired2;
        }
    }

    /**
     * @return boolean value (byte is read from stream and compared against zero)
     */
    public boolean readBoolean() {
        return readByte() != 0;
    }

    /**
     * @return 8 bit integer
     */
    public byte readByte() {
        ensureAvailable(1);
        byte b = curBuffer.buffer.getByte(curBuffer.position);
        curBuffer.position++;
        return b;
    }

    /**
     * @return Next byte - without forwarding read position though
     */
    public byte peek() {
        ensureAvailable(1);
        byte b = curBuffer.buffer.getByte(curBuffer.position);
        return b;
    }

    /**
     * @return 16 bit character
     */
    public char readChar() {
        ensureAvailable(2);
        char c = curBuffer.buffer.getChar(curBuffer.position);
        curBuffer.position += 2;
        return c;
    }

    /**
     * @return 64 bit floating point
     */
    public double readDouble() {
        ensureAvailable(8);
        double d = curBuffer.buffer.getDouble(curBuffer.position);
        curBuffer.position += 8;
        return d;
    }

    /**
     * @return 32 bit floating point
     */
    public float readFloat() {
        ensureAvailable(4);
        float f = curBuffer.buffer.getFloat(curBuffer.position);
        curBuffer.position += 4;
        return f;
    }

    /**
     * Fill destination array with the next n bytes (possibly blocks with streams)
     *
     * @param b destination array
     */
    public void readFully(byte[] b) {
        readFully(b, 0, b.length);
    }

    /**
     * Fill destination array with the next n bytes (possibly blocks with streams)
     *
     * @param b destination array
     * @param off Offset
     * @param len Number of bytes to read
     */
    public void readFully(byte[] b, int off, int len) {
        while (true) {
            int read = Math.min(curBuffer.remaining(), len);
            curBuffer.buffer.get(curBuffer.position, b, off, read);
            len -= read;
            off += read;
            curBuffer.position += read;
            assert(len >= 0);
            if (len == 0) {
                break;
            }
            fetchNextBytes(1);
        }
    }

    /**
     * Fill destination buffer (complete buffer)
     *
     * @param b destination buffer
     */
    public void readFully(FixedBuffer bb) {
        readFully(bb, 0, bb.capacity());
    }

    /**
     * Fill destination buffer
     *
     * @param bb destination buffer
     * @param offset offset in buffer
     * @param len number of bytes to copy
     */
    public void readFully(FixedBuffer bb, int off, int len) {
        while (true) {
            int read = Math.min(curBuffer.remaining(), len);
            curBuffer.buffer.get(curBuffer.position, bb, off, read);
            len -= read;
            off += read;
            curBuffer.position += read;
            assert(len >= 0);
            if (len == 0) {
                break;
            }
            if (usingBoundaryBuffer() || (!directReadSupport)) {
                fetchNextBytes(1);
            } else {
                source.directRead(this, bb, off, len); // shortcut
                absoluteReadPos += len;
                assert(curBuffer.position == curBuffer.end);
                break;
            }
        }
    }

    /**
     * @return Is current buffer currently set to boundaryBuffer?
     */
    private boolean usingBoundaryBuffer() {
        return curBuffer.buffer == boundaryBuffer.buffer;
    }

    /**
     * @return 32 bit integer
     */
    public int readInt() {
        ensureAvailable(4);
        int i = curBuffer.buffer.getInt(curBuffer.position);
        curBuffer.position += 4;
        return i;
    }

    /**
     * @return sizeOf Size of integer in byte (1, 2, 4, or 8)
     */
    public long readInt(int sizeOf) {
        switch (sizeOf) {
        case 1:
            return readByte();
        case 2:
            return readShort();
        case 4:
            return readInt();
        case 8:
            return readLong();
        default:
            throw new RuntimeException("Invalid size");
        }
    }


    /**Info on source that created data currently read (at least the included revision is required for deserialization)
     * @return String/Line from stream (ends either at line delimiter or 0-character)
     */
    public String readLine() {
        StringOutputStream sb = new StringOutputStream();
        while (true) {
            byte b = readByte();
            if (b == 0 || b == '\n') {
                break;
            }
            sb.append((char)b);
        }
        return sb.toString();
    }

    /**
     * @return 8 byte integer
     */
    public long readLong() {
        ensureAvailable(8);
        long l = curBuffer.buffer.getLong(curBuffer.position);
        curBuffer.position += 8;
        return l;
    }

    /**
     * @return 2 byte integer
     */
    public short readShort() {
        ensureAvailable(2);
        short s = curBuffer.buffer.getShort(curBuffer.position);
        curBuffer.position += 2;
        return s;
    }

    /**
     * @return unsigned 1 byte integer
     */
    public int readUnsignedByte() {
        ensureAvailable(1);
        int i = curBuffer.buffer.getUnsignedByte(curBuffer.position);
        curBuffer.position += 1;
        return i;
    }

    /**
     * @return unsigned 2 byte integer
     */
    public int readUnsignedShort() {
        ensureAvailable(2);
        int i = curBuffer.buffer.getUnsignedShort(curBuffer.position);
        curBuffer.position += 2;
        return i;
    }

    /**
     * @return Enum value
     */
    @SuppressWarnings({ "rawtypes"})
    public <E extends Enum> E readEnum(Class<E> c) {
        return c.getEnumConstants()[readEnum(c.getEnumConstants())];
    }

    /**
     * @return Enum value
     */
    public int readEnum(Object[] strings) {
        if (strings.length == 0) {
            return readInt();
        } else if (strings.length <= 0x100) {
            return readByte();
        } else if (strings.length <= 0x10000) {
            return readShort();
        } else {
            assert(strings.length < 0x7FFFFFFF);
            return readInt();
        }
    }

    /**
     * Skip specified number of bytes
     */
    public void skip(int n) {
        /*      if (this.streamBuffer.source == null) {
                    readPos += n;
                } else {*/

        while (true) {
            if (remaining() >= n) {
                curBuffer.position += n;
                return;
            }
            n -= remaining();
            curBuffer.position = curBuffer.end;
            fetchNextBytes(1);
        }
    }

    /**
     * Read "skip offset" at current position and store it internally
     */
    public void readSkipOffset() {
        curSkipOffsetTarget = absoluteReadPos + curBuffer.position;
        curSkipOffsetTarget += readInt();
        curSkipOffsetTarget += 4; // from readInt()
    }

    /**
     * Move to target of last read skip offset
     */
    public void toSkipTarget() {
        long pos = curBuffer.position;
        assert(curSkipOffsetTarget >= absoluteReadPos + pos);
        skip((int)(curSkipOffsetTarget - absoluteReadPos - pos));
        curSkipOffsetTarget = 0;
    }

    public String toString() {
        if (curBuffer.buffer != null) {
            return "InputStreamBuffer - position: " + curBuffer.position + " start: " + curBuffer.start + " end: " + curBuffer.end;
        } else {
            return "InputStreamBuffer - no buffer backend";
        }
    }

    /**
     * @return Is further data available?
     */
    public boolean moreDataAvailable() {
        if (remaining() > 0) {
            return true;
        }
        //System.out.println("Not here");
        return source.moreDataAvailable(this, sourceBuffer);
    }

    /**
     * @return Number of bytes ever read from this stream
     */
    public long getAbsoluteReadPosition() {
        return absoluteReadPos + curBuffer.position;
    }

    /**
     * @return timeout for blocking calls (<= 0 when disabled)
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * @param timeout for blocking calls (<= 0 when disabled)
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

//    /**
//     * Deserialize object with yet unknown type from stream
//     * (should have been written to stream with OutputStream.WriteObject() before; typeencoder should be of the same type)
//     *
//     * @param expectedType expected type (optional, may be null)
//     * @param factoryParameter Custom parameter for possibly user defined factory
//     * @param enc Data type encoding to use
//     * @return Buffer with read object (caller needs to take care of deleting it)
//     */
//    public GenericObject readObject(DataTypeBase expectedType, Object factoryParameter, Serialization.DataEncoding enc) {
//        //readSkipOffset();
//        DataTypeBase dt = readType();
//        if (dt == null) {
//            return null;
//        }
//
//        if (expectedType != null && (!dt.isConvertibleTo(expectedType))) {
//            dt = expectedType; // fix to cope with mca2 legacy blackboards
//        }
//
//        GenericObject buffer = factory.createGenericObject(dt, factoryParameter);
//        buffer.deserialize(this, enc);
//        return buffer;
//    }

    /**
     * @return Duration in milliseconds
     */
    public long readDuration() {
        return readLong() / 1000000;
    }

    /**
     * Deserializes object of specified type
     *
     * @param type Type object must have
     * @return Deserialized object (new object for immutable types, provided object in case of a mutable type)
     */
    public Object readObject(Class<?> type) throws Exception {
        return readObject(null, type);
    }

    /**
     * Deserializes object of specified type
     *
     * @param deserializeTo Object to call deserialize() on (optional; will contain result of deserialization, in case type is a mutable type)
     * @param type Type object must have
     * @return Deserialized object (new object for immutable types, provided object in case of a mutable type)
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object readObject(Object deserializeTo, Class<?> type) throws Exception {
        if (BinarySerializable.class.isAssignableFrom(type)) {
            if (deserializeTo == null) {
                deserializeTo = type.newInstance();
            }
            ((BinarySerializable)deserializeTo).deserialize(this);
            return deserializeTo;
        } else if (type.isPrimitive()) {
            if (type == byte.class) {
                return readByte();
            } else if (type == short.class) {
                return readShort();
            } else if (type == int.class) {
                return readInt();
            } else if (type == long.class) {
                return readLong();
            } else if (type == float.class) {
                return readFloat();
            } else if (type == double.class) {
                return readDouble();
            } else if (type == boolean.class) {
                return readBoolean();
            } else {
                throw new Exception("Unsupported primitive type");
            }
        } else if (type.isEnum()) {
            if (deserializeTo != null && deserializeTo.getClass().equals(EnumValue.class)) {
                ((EnumValue)deserializeTo).deserialize(this);
                return deserializeTo;
            }
            return readEnum((Class <? extends Enum >)type);
        } else if (type == String.class) {
            return readString();
        } else {
            throw new Exception("Unsupported type");
        }
    }

    /**
     * Deserializes object of specified type
     *
     * @param type Type object must have
     * @param encoding Data encoding
     * @return Deserialized object (new object for immutable types, provided object in case of a mutable type)
     */
    public Object readObject(Class<?> type, Serialization.DataEncoding encoding) throws Exception {
        return readObject(null, type, encoding);
    }

    /**
     * Deserializes object of specified type
     *
     * @param deserializeTo Object to call deserialize() on (optional; will contain result of deserialization, in case type is a mutable type)
     * @param type Type object must have
     * @param encoding Data encoding
     * @return Deserialized object (new object for immutable types, provided object in case of a mutable type)
     */
    public Object readObject(Object deserializeTo, Class<?> type, Serialization.DataEncoding encoding) throws Exception {
        if (encoding == Serialization.DataEncoding.BINARY) {
            return readObject(deserializeTo, type);
        } else if (encoding == Serialization.DataEncoding.STRING) {
            StringInputStream sis = new StringInputStream(readString());
            try {
                deserializeTo = sis.readObject(deserializeTo, type);
            } finally {
                sis.close();
            }
            return deserializeTo;
        } else if (encoding == Serialization.DataEncoding.BINARY_COMPRESSED) {
            String format = this.readString();
            if (format.length() == 0) {
                return readObject(deserializeTo, type);
            } else {
                int size = this.readInt();
                if (deserializeTo == null) {
                    deserializeTo = type.newInstance();
                }
                if (deserializeTo instanceof Compressible) {
                    ((Compressible)deserializeTo).decompressNext(this, format, size);
                } else {
                    DataCompressor compressor = new DataCompressor(type, format);
                    compressor.decompressNext(this, deserializeTo, size);
                }
                return deserializeTo;
            }
        } else {
            assert(encoding == Serialization.DataEncoding.XML);
            XMLDocument d = new XMLDocument(new InputSource(new StringReader(readString())), false);
            XMLNode n = d.getRootNode();
            return Serialization.deserialize(n, deserializeTo, type);
        }
    }

    /**
     * @param registerUid Uid of register to serialize remote entry of
     * @return Remote register entry of specified type
     */
    public PublishedRegisters.RemoteEntryBase<?> readRegisterEntry(int registerUid) throws Exception {
        if (remoteRegisters == null) {
            throw new Exception("BinaryInputStream: No shared serialization info set");
        }
        Register<?> localRegister = PublishedRegisters.get(registerUid).register;
        int handle = (int)readInt(localRegister.getSizeOfHandle());
        if (handle == -2) {
            readRegisterUpdates();
            handle = (int)readInt(localRegister.getSizeOfHandle());
        }
        if (handle == -1) {
            return PublishedRegisters.getMinusOneElement();
        }
        return remoteRegisters[registerUid].get(handle);
    }

    /**
     * @return Reads register updates
     */
    void readRegisterUpdates() throws Exception {

        // Read register updates
        if (getSourceInfo().getRevision() == 0) {
            remoteRegisters[0].deserializeEntries(this);
        } else {
            byte uid = readByte();
            while (uid != -1) {
                if (uid >= remoteRegisters.length || uid < 0) {
                    throw new RuntimeException("Invalid register uid " + uid);
                }
                remoteRegisters[uid].deserializeEntries(this);
                uid = readByte();
            }
        }
    }
}
