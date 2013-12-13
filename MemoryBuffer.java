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
import org.rrlib.serialization.rtti.Copyable;
import org.rrlib.serialization.rtti.DataType;
import org.rrlib.serialization.rtti.DataTypeBase;
import org.rrlib.serialization.rtti.GenericChangeable;

/**
 * @author Max Reichardt
 *
 * Memory buffer that can be used as source and concurrent sink.
 *
 * When used as sink, it can grow when required. A resizeFactor <= 1 indicates that this should not happen.
 * The buffer size is limited to 2GB with respect to serialization.
 *
 * Writing and reading concurrently is not supported - due to resize.
 */
public class MemoryBuffer implements BinarySerializable, ConstSource, Sink, Copyable<MemoryBuffer>, GenericChangeable<MemoryBuffer> {

    /** Size of temporary array */
    public final static int TEMP_ARRAY_SIZE = 2048;

    /** Default size of temp buffer */
    public final static int DEFAULT_SIZE = 8192;

    /** Default factor for buffer size increase */
    public final static int DEFAULT_RESIZE_FACTOR = 2;

    /** Wrapped memory buffer */
    protected FixedBuffer backend;

    /** When buffer needs to be reallocated, new size is multiplied with this factor to have some bytes in reserve */
    protected float resizeReserveFactor;

    /** Current size of buffer */
    protected int curSize;

    /** Data type of this class */
    public final static DataTypeBase TYPE = new DataType<MemoryBuffer>(MemoryBuffer.class);

    public MemoryBuffer() {
        this(DEFAULT_SIZE);
    }

    public MemoryBuffer(int size) {
        this(size, DEFAULT_RESIZE_FACTOR, true);
    }

    /**
     * @param size Initial buffer size
     * @param resizeFactor When buffer needs to be reallocated, new size is multiplied with this factor to have some bytes in reserve
     * @param allocatDirect Allocate direct byte buffer? (in heap outside of JVM - mainly relevant for JNI code)
     */
    public MemoryBuffer(int size, float resizeFactor, boolean allocatDirect) {
        backend = new FixedBuffer(size, allocatDirect);
        resizeReserveFactor = resizeFactor;
    }

    /**
     * @return the resizeReserveFactor
     */
    public float getResizeReserveFactor() {
        return resizeReserveFactor;
    }

    /**
     * @param resizeReserveFactor the resizeReserveFactor to set
     */
    public void setResizeReserveFactor(float resizeReserveFactor) {
        this.resizeReserveFactor = resizeReserveFactor;
    }

    /**
     * Set size of memory buffer. Increases capacity if necessary.
     *
     * @param newSize New size of buffer
     */
    public void setSize(int newSize) {
        if (newSize > getCapacity()) {
            ensureCapacity(newSize, true, this.curSize);
        }
        this.curSize = newSize;
    }

    /**
     * Clear buffer
     */
    public void clear() {
        curSize = 0;
    }

    // ConstSource implementation

    @Override
    public void close(BinaryInputStream inputStreamBuffer, BufferInfo buffer) {
        // do nothing, really
        buffer.reset();
    }

    @Override
    public void directRead(BinaryInputStream inputStreamBuffer, FixedBuffer buffer, int offset, int len) {
        throw new RuntimeException("Unsupported - shouldn't be called");
    }

    @Override
    public boolean directReadSupport() {
        return false;
    }

    @Override
    public void read(BinaryInputStream inputStreamBuffer, BufferInfo buffer, int len) {
        buffer.setRange(0, curSize);
        if (buffer.position >= curSize) {
            throw new RuntimeException("Attempt to read outside of buffer");
        }
    }

    @Override
    public void reset(BinaryInputStream inputStreamBuffer, BufferInfo buffer) {
        buffer.buffer = backend;
        buffer.position = 0;
        buffer.setRange(0, curSize);
    }

    // Sink implementation

    @Override
    public void close(BinaryOutputStream outputStreamBuffer, BufferInfo buffer) {
        // do nothing, really
        buffer.reset();
    }

    @Override
    public void directWrite(BinaryOutputStream outputStreamBuffer, FixedBuffer buffer, int offset, int len) {
        throw new RuntimeException("Unsupported - shouldn't be called");
    }

    @Override
    public boolean directWriteSupport() {
        return false;
    }

    @Override
    public void reset(BinaryOutputStream outputStreamBuffer, BufferInfo buffer) {
        buffer.buffer = backend;
        buffer.position = 0;
        buffer.setRange(0, backend.capacity());
    }

    /**
     * Ensure that memory buffer has at least this size.
     * If not, backend will be reallocated.
     *
     * @param newSize New Size in bytes
     * @param keepContents Keep contents when reallocating?
     * @param oldSize Old Size (only relevant if contents are to be kept)
     */
    protected void ensureCapacity(int newSize, boolean keepContents, int oldSize) {
        if (resizeReserveFactor <= 1) {
            throw new RuntimeException("Attempt to write outside of buffer");
        }
        if (resizeReserveFactor <= 1.2) {
            //System.out.println("warning: small resizeReserveFactor");
            Log.log(LogLevel.DEBUG_WARNING, "MemoryBuffer", "Small resizeReserveFactor");
        }

        reallocate(newSize, keepContents, oldSize);
    }

    @Override
    public boolean write(BinaryOutputStream outputStreamBuffer, BufferInfo buffer, int hint) {

        // do we need size increase?
        if (hint >= 0) {
            int newSize = Math.max(8, (int)((backend.capacity() + hint) * resizeReserveFactor));
            ensureCapacity(newSize, true, buffer.position);
            buffer.buffer = backend;
        }
        buffer.end = backend.capacity(); // don't modify buffer start
        return false;
    }

    /**
     * Reallocate backend
     *
     * @param newSize New size
     * @param keepContents Keep contents of backend?
     * @param oldSize Old Size (only relevant of contents are to be kept)
     */
    protected void reallocate(int newSize, boolean keepContents, int oldSize) {
        if (newSize <= backend.capacity()) {
            return;
        }

        FixedBuffer newBuffer = new FixedBuffer(newSize, backend.getBuffer().isDirect());

        if (keepContents) {

            // Copy old contents
            newBuffer.put(0, backend, 0, oldSize);
        }

        deleteOldBackend(backend);
        backend = newBuffer;
    }

    /**
     * Delete old backend buffer
     * (may be overriden by subclass)
     *
     * @param b Buffer to delete
     */
    protected void deleteOldBackend(FixedBuffer b) {
    }

    // CustomSerializable implementation

    @Override
    public void deserialize(BinaryInputStream rv) {
        long size = rv.readLong();
        deserialize(rv, (int)size);
    }

    @Override
    public void serialize(BinaryOutputStream sb) {
        sb.writeLong(curSize);
        sb.write(backend, 0, curSize);
    }

    @Override
    public boolean moreDataAvailable(BinaryInputStream inputStreamBuffer, BufferInfo buffer) {
        return buffer.end < curSize;
    }

    /**
     * @return Buffer size
     */
    public int getSize() {
        return curSize;
    }

    /**
     * @return Buffer capacity
     */
    public int getCapacity() {
        return backend.capacity();
    }

    /**
     * @return Raw buffer backend
     */
    public FixedBuffer getBuffer() {
        return backend;
    }

    @Override
    public void flush(BinaryOutputStream outputStreamBuffer, BufferInfo buffer) {
        curSize = buffer.position; // update buffer size
    }

    public void dumpToFile(String filename) {
        backend.dumpToFile(filename, curSize);
    }

    public boolean equals(Object other) {
        // two memory buffers are equal if they have the same content (not necessarily the same backend size)
        if (other instanceof MemoryBuffer) {
            MemoryBuffer otherBuffer = (MemoryBuffer)other;
            if (curSize != otherBuffer.curSize) {
                return false;
            }
            for (int i = 0; i < curSize; i++) {
                if (backend.getByte(i) != otherBuffer.backend.getByte(i)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void applyChange(MemoryBuffer t, long offset, long dummy) {
        ensureCapacity((int)(t.getSize() + offset), true, getSize());
        backend.put((int)offset, t.backend, 0, t.getSize());
        int requiredSize = (int)offset + t.getSize();
        curSize = Math.max(curSize, requiredSize);
    }

    @Override
    public void copyFrom(MemoryBuffer source) {
        ensureCapacity(source.getSize(), false, getSize());
        backend.put(0, source.backend, 0, source.getSize());
        curSize = source.getSize();
    }

    /**
     * Reset this buffer and
     * copy data from stream to it
     *
     * @param size Number of bytes to
     */
    public void deserialize(BinaryInputStream rv, int size) {
        curSize = 0;
        reallocate(size, false, -1);
        rv.readFully(backend, 0, size);
        curSize = size;
    }
}
