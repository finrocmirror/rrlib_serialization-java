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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Max Reichardt
 *
 * This is a simple fixed-size memory buffer.
 *
 * In Java it wraps a java.nio.ByteBuffer (this allows sharing the buffer with C++).
 * Its methods are also very similar to ByteBuffer's - which makes ByteBuffer
 * a good source for documentation ;-)
 *
 * Writing arrays/buffers to the buffer is not thread-safe in Java. Everything else is.
 */
public class FixedBuffer {

    /** Byte order for this buffer */
    protected static final ByteOrder BYTE_ORDER = ByteOrder.nativeOrder();

    /** Number of temp buffers to allocate for each fixed buffer */
    private final int TEMP_BUFFER_COUNT = 4;

    /** Pool of temporary buffers - e.g. for some copy operations */
    private final TempBuffer[] tempBufs = new TempBuffer[TEMP_BUFFER_COUNT];

    /** Actual (wrapped) buffer - may be replaced by subclasses */
    protected ByteBuffer buffer;

    /** Raw pointer to buffer (variable is set and used by JNI classes) */
    private long pointer;


    public FixedBuffer(int capacity) {
        this(capacity, false);
    }

    /**
     * @param capacity Capacity of buffer to allocate
     * @param allocateDirect Allocate direct byte buffer? (in heap outside of JVM - mainly relevant for JNI code)
     */
    public FixedBuffer(int capacity, boolean allocateDirect) {
        this(allocateDirect ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity));
    }

    /**
     * @param array Array to wrap
     */
    public FixedBuffer(byte[] array) {
        this(ByteBuffer.wrap(array));
    }

    public FixedBuffer(ByteBuffer bb) {
        buffer = bb;
        buffer.order(BYTE_ORDER);
        for (int i = 0; i < TEMP_BUFFER_COUNT; i++) {
            tempBufs[i] = new TempBuffer();
            tempBufs[i].init();
        }
    }

    /**
     * @return Capacity of buffer (in bytes)
     */
    public int capacity() {
        return buffer.capacity();
    }

    /**
     * @return Wrapped ByteBuffer
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    /**
     * @return Temporary buffer
     */
    public TempBuffer getTempBuffer() {
        for (int i = 0; i < TEMP_BUFFER_COUNT; i++) {
            TempBuffer tb = tempBufs[i];
            if (tb.inUse.compareAndSet(false, true)) {
                return tb;
            }
        }

        // No spare temp buffer ... create one (not really expensive)
        TempBuffer tb = new TempBuffer();
        tb.init();
        return tb;
    }

    /**
     * Copy Data to destination array
     *
     * @param offset Offset in this buffer
     * @param dst Destination array
     * @param off offset in destination array
     * @param length number of bytes to copy
     */
    public void get(int offset, byte[] dst, int off, int len) {
        TempBuffer buffer = getTempBuffer();
        buffer.buffer.position(offset);
        buffer.buffer.get(dst, off, len);
        buffer.inUse.set(false);
    }

    /**
     * Copy Data from source array
     *
     * @param offset Offset in this buffer
     * @param src Source array
     * @param off offset in source array
     * @param length number of bytes to copy
     */
    public void put(int offset, byte[] src, int off, int len) {
        buffer.position(offset);
        buffer.put(src, off, len);
    }

    /**
     * Copy Data to destination array
     *
     * @param offset Offset in this buffer
     * @param dst Destination array
     */
    public void get(int offset, byte[] dst) {
        TempBuffer buffer = getTempBuffer();
        buffer.buffer.position(offset);
        buffer.buffer.get(dst);
        buffer.inUse.set(false);
    }

    /**
     * Copy Data from source array
     *
     * @param offset Offset in this buffer
     * @param src Source array
     */
    public void put(int offset, byte[] src) {
        buffer.position(offset);
        buffer.put(src);
    }

    /**
     * Copy Data to destination buffer
     *
     * @param offset Offset in this buffer
     * @param dst Destination array
     * @param off offset in source array
     * @param length number of bytes to copy
     */
    public void get(int offset, ByteBuffer dst, int off, int len) {
        TempBuffer buffer = getTempBuffer();
        dst.clear();
        dst.position(off);
        buffer.buffer.position(offset);
        int oldLimit = buffer.buffer.limit();
        buffer.buffer.limit(offset + len);
        dst.put(buffer.buffer);
        buffer.buffer.limit(oldLimit);
        buffer.inUse.set(false);
    }

    /**
     * Copy Data to destination buffer
     *
     * @param offset Offset in this buffer
     * @param dst Destination array
     * @param off offset in source array
     * @param length number of bytes to copy
     */
    public void get(int offset, FixedBuffer dst, int off, int len) {
        get(offset, dst.buffer, off, len);
    }

    /**
     * Copy Data from source buffer
     *
     * @param offset Offset in this buffer
     * @param src Source Buffer
     * @param off offset in source buffer
     * @param length number of bytes to copy
     */
    public void put(int offset, ByteBuffer src, int off, int len) {
        if (len <= 0) {
            return;
        }
        src.position(off);
        int oldLimit = src.limit();
        //int oldLimit2 = buffer.limit();
        src.limit(off + len);
        buffer.rewind();
        buffer.position(offset);
        //buffer.limit(buffer.capacity());
        buffer.put(src);
        src.limit(oldLimit);
        //buffer.limit(oldLimit2);
    }

    /**
     * Copy Data from source buffer
     *
     * @param offset Offset in this buffer
     * @param src Source Buffer
     * @param off offset in source array
     * @param length number of bytes to copy
     */
    public void put(int offset, FixedBuffer src, int off, int len) {
        TempBuffer buffer = src.getTempBuffer();
        put(offset, buffer.buffer, off, len);
        buffer.inUse.set(false);
    }

    /**
     * Copy Data to destination buffer
     * (whole buffer is filled)
     *
     * @param offset Offset in this buffer
     * @param dst Destination array
     */
    public void get(int offset, FixedBuffer dst) {
        TempBuffer buffer = getTempBuffer();
        dst.buffer.clear();
        buffer.buffer.position(offset);
        int oldLimit = buffer.buffer.limit();
        buffer.buffer.limit(offset + dst.buffer.capacity());
        dst.buffer.put(buffer.buffer);
        buffer.buffer.limit(oldLimit);
        buffer.inUse.set(false);
    }

    /**
     * Copy Data from source buffer
     * (whole buffer is copied)
     *
     * @param offset Offset in this buffer
     * @param src Source Buffer
     */
    public void put(int offset, FixedBuffer src) {
        put(offset, src, 0, src.capacity());
        /*src.buffer.clear();
        buffer.rewind();
        buffer.position(offset);
        buffer.put(src.buffer);*/
    }


    /**
     * @param offset absolute offset
     * @param v 8 bit integer
     */
    public void putByte(int offset, int v) {
        buffer.put(offset, (byte)(v & 0xFF));
    }
    /**
     * @param offset absolute offset
     * @return 8 bit integer
     */
    public byte getByte(int offset) {
        return buffer.get(offset);
    }

    /**
     * @param offset absolute offset
     * @param v (1-byte) boolean
     */
    public void putBoolean(int offset, boolean v) {
        putByte(offset, v ? 1 : 0);
    }
    /**
     * @param offset absolute offset
     * @return (1-byte) boolean
     */
    public boolean getBoolean(int offset) {
        return getByte(offset) != 0;
    }

    /**
     * @param offset absolute offset
     * @param v Character
     */
    public void putChar(int offset, char v) {
        buffer.putChar(offset, v);
    }
    /**
     * @param offset absolute offset
     * @return Character
     */
    public char getChar(int offset) {
        return buffer.getChar(offset);
    }

    /**
     * @param offset absolute offset
     * @param v 16 bit integer
     */
    public void putShort(int offset, int v) {
        buffer.putShort(offset, (short)v);
    }
    /**
     * @param offset absolute offset
     * @return 16 bit integer
     */
    public short getShort(int offset) {
        return buffer.getShort(offset);
    }

    /**
     * @param offset absolute offset
     * @param v 32 bit integer
     */
    public void putInt(int offset, int v) {
        buffer.putInt(offset, v);
    }
    /**
     * @param offset absolute offset
     * @return 32 bit integer
     */
    public int getInt(int offset) {
        return buffer.getInt(offset);
    }

    /**
     * @param offset absolute offset
     * @param v 64 bit integer
     */
    public void putLong(int offset, long v) {
        buffer.putLong(offset, v);
    }
    /**
     * @param offset absolute offset
     * @return 64 bit integer
     */
    public long getLong(int offset) {
        return buffer.getLong(offset);
    }

    /**
     * @param offset absolute offset
     * @param v 32 bit floating point
     */
    public void putFloat(int offset, float v) {
        buffer.putFloat(offset, v);
    }

    /**
     * @param offset absolute offset
     * @return 32 bit floating point
     */
    public float getFloat(int offset) {
        return buffer.getFloat(offset);
    }

    /**
     * @param offset absolute offset
     * @param v 64 bit floating point
     */
    public void putDouble(int offset, double v) {
        buffer.putDouble(offset, v);
    }
    /**
     * @param offset absolute offset
     * @return 64 bit floating point
     */
    public double getDouble(int offset) {
        return buffer.getDouble(offset);
    }

    /**
     * @param offset absolute offset
     * @return unsigned 1 byte integer
     */
    public int getUnsignedByte(int offset) {
        int b = getByte(offset);
        return b >= 0 ? b : b + 256;
    }

    /**
     * @param offset absolute offset
     * @return unsigned 2 byte integer
     */
    public int getUnsignedShort(int offset) {
        short s = getShort(offset);
        return s >= 0 ? s : s + 65536;
    }

    /**
     * Write null-terminated string (16 Bit Characters)
     *
     * @param offset absolute offset in buffer
     * @param s String
     * @param terminate Terminate string with zero?
     */
    public void putUnicode(int offset, String s, boolean terminate) {
        int len = s.length();
        int off = offset;
        for (int i = 0; i < len; i++) {
            putChar(off, s.charAt(i));
            off += 2;
        }
        if (terminate) {
            putChar(off, (char)0);
        }
    }

    /**
     * Read null-terminated string (16 Bit Characters)
     *
     * @param offset absolute offset in buffer
     * @param s String
     */
    public String getUnicode(int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset, n = buffer.capacity() - 1; i < n; i += 2) {
            char c = buffer.getChar(i);
            if (c == 0) {
                return sb.toString();
            }
            sb.append(c);
        }
        throw new RuntimeException("String not terminated");
    }

    /**
     * Read string (16 Bit Characters). Stops at null-termination or specified length.
     *
     * @param offset absolute offset
     * @param s String
     * @param length Length of string to read (including possible termination)
     */
    public String getUnicode(int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset, n = Math.max(i + (length * 2), buffer.capacity() - 1); i < n; i += 2) {
            char c = buffer.getChar(i);
            if (c == 0) {
                return sb.toString();
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Write null-terminated string (8 Bit Characters - Suited for ASCII)
     *
     * @param offset absolute offset in buffer
     * @param s String
     */
    public void putString(int offset, String s) {
        putString(offset, s, true);
    }

    /**
     * Write string (8 Bit Characters - Suited for ASCII)
     *
     * @param offset absolute offset in buffer
     * @param s String
     * @param terminate Terminate string with zero?
     */
    public void putString(int offset, String s, boolean terminate) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            putByte(offset + i, (byte)s.charAt(i));
        }
        if (terminate) {
            putByte(offset + len, 0);
        }
    }

    /**
     * Read null-terminated string (8 Bit Characters - Suited for ASCII)
     *
     * @param offset absolute offset in buffer
     */
    public String getString(int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset, n = capacity(); i < n; i++) {
            char c = (char)getByte(i);
            if (c == 0) {
                return sb.toString();
            }
            sb.append(c);
        }
        throw new RuntimeException("String not terminated");
    }

    /**
     * Read String/Line from buffer (ends either at line delimiter or 0-character - 8bit)
     *
     * @param offset absolute offset in buffer
     */
    public String getLine(int offset) {
        StringOutputStream sb = new StringOutputStream();
        for (int i = offset, n = capacity(); i < n; i++) {
            char c = (char)getByte(i);
            if (c == 0 || c == '\n') {
                return sb.toString();
            }
            sb.append(c);
        }

        throw new RuntimeException("String not terminated");
    }

    /**
     * Read string (8 Bit Characters - Suited for ASCII). Stops at null-termination or specified length.
     *
     * @param offset absolute offset
     * @param length Length of string to read
     */
    public String getString(int offset, int length) {
        StringOutputStream sb = new StringOutputStream(length);
        for (int i = offset, n = Math.min(capacity(), offset + length); i < n; i++) {
            char c = (char)getByte(i);
            if (c == 0) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Zero out specified bytes
     *
     * @param offset Offset in buffer to start at
     * @param length Length of area to zero out
     */
    public void zeroOut(int offset, int length) {
        for (int i = offset, n = offset + length; i < n; i++) {
            buffer.put(i, (byte)0);
        }
    }

    /**
     * Set wrapped buffer to buffer contained in other fixed buffer
     * (only call, when FixedBuffer doesn't own a buffer himself)
     *
     * @param fb
     */
    protected void setCurrentBuffer(FixedBuffer fb) {
        buffer = fb.buffer;
    }

    class TempBuffer {
        ByteBuffer buffer;
        AtomicBoolean inUse = new AtomicBoolean();
//      private int allocationView = -1;

        void init() {
            //if (allocationView != allocationCount) {
            buffer = FixedBuffer.this.buffer.duplicate();
            //}
        }
    }

    public void dumpToFile(String filename, int size) {
        try {
            BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(filename));
            int n = size <= 0 ? capacity() : size;
            for (int i = 0; i < n; i++) {
                fos.write(getByte(i));
            }
            fos.close();
        } catch (Exception e) {
        }
    }

    /**
     * @return Raw pointer to buffer
     */
    public long getBufferPointer() {
        return pointer;
    }

    /**
     * @param pointer Raw pointer of the buffer
     */
    public void setBufferPointer(long pointer) {
        if (this.pointer != 0 && this.pointer != pointer) {
            throw new RuntimeException("Pointer already set to different address");
        }
        this.pointer = pointer;
    }
}
