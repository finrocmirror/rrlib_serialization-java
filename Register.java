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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Max Reichardt
 *
 * Global concurrent register that is suitable for auto-publishing via binary streams
 * (see C++ class for more elaborate comments)
 */
public class Register<Entry> {

    /** Register listener */
    public static interface Listener<Entry> {

        /**
         * Called whenever register changes (== element is added)
         *
         * @param register Register that changed
         * @param entry Entry that was added
         */
        void onRegisterEntryAdd(Register<Entry> register, Entry entry);
    }

    /**
     * @param chunkCount Number of chunks
     * @param chunkSize Size of chunks
     * @param sizeOfHandle Size of handle in serialization (1 or 2 or 4)
     */
    public Register(int chunkCount, int chunkSize, int sizeOfHandle) {
        this.chunkCount = chunkCount;
        this.chunkSize = chunkSize;
        this.sizeOfHandle = sizeOfHandle;
        assert(sizeOfHandle <= 2 || sizeOfHandle == 4);
        chunks = new Object[chunkCount][];
        chunks[0] = new Object[chunkSize];
    }

    /**
     * Adds entry to register
     *
     * @param entry Entry to add
     * @return Index in register
     */
    public synchronized int add(Entry entry) {
        int size = size();
        int chunkIndex = size / chunkSize;
        if (chunkIndex >= chunkCount) {
            throw new IndexOutOfBoundsException("Adding element exceeds register size (possibly increase register's chunkCount or chunkSize)");
        }
        int chunkElementIndex = size % chunkSize;
        if (chunkIndex > 0 && chunkElementIndex == 0) {
            // Allocate new chunk
            assert(chunks[chunkIndex] == null);
            chunks[chunkIndex] = new Object[chunkSize];
        }
        chunks[chunkIndex][chunkElementIndex] = entry;
        size++;
        this.size.set(size);
        for (Listener<Entry> listener : listeners) {
            listener.onRegisterEntryAdd(this, entry);
        }
        return size - 1;
    }

    /**
     * Adds listener to register.
     *
     * @param listener Listener to add
     */
    public synchronized void addListener(Listener<Entry> listener) {
        listeners.add(listener);
    }

    /**
     * @param index Index of entry
     * @return Entry at specified index
     */
    @SuppressWarnings("unchecked")
    public Entry get(int index) {
        int chunkIndex = index / chunkSize;
        int chunkElementIndex = index % chunkSize;
        return (Entry)chunks[chunkIndex][chunkElementIndex];
    }

    /**
     * @return Number of chunks
     */
    public int getChunkCount() {
        return chunkCount;
    }

    /**
     * @return Size of chunks
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * @return Size of handle in serialization (1 or 2 or 4)
     */
    public int getSizeOfHandle() {
        return sizeOfHandle;
    }

    /**
     * @return Uid of this (global) register. -1 means that no uid has been assigned yet.
     */
    public int getUid() {
        return uid;
    }

    /**
     * Reads local entry of this register from stream
     *
     * @param stream Stream to deserialize entry from
     * @return Returns nullptr if 'UID' encoding has been specified in stream. In this case, the caller must deserialize the entry's uid.
     * @throw Throws exception if read handle is out of bounds
     */
    public Entry readEntry(BinaryInputStream stream) {
        SerializationInfo.RegisterEntryEncoding encoding = stream.getSourceInfo().getRegisterEntryEncoding(uid);
        if (encoding == SerializationInfo.RegisterEntryEncoding.UID) {
            return null;
        }
        int handle = (int)stream.readInt(sizeOfHandle);
        if (handle >= size()) {
            throw new RuntimeException("Register.ReadEntry: handle is out of bounds");
        }
        return get(handle);
    }

    /**
     * Remove listener from register.
     *
     * @param listener Listener to remove
     * @return Whether listener was removed
     */
    public synchronized boolean removeListener(Listener<Entry> listener) {
        return listeners.remove(listener);
    }

    /**
     * @return Current number of elements in register
     */
    public int size() {
        return size.get();
    }

    /**
     * @return Atomic containing current size
     */
    AtomicInteger sizeAtomic() {
        return size;
    }

    /**
     * Writes entry of this register to stream - using encoding specified in stream.
     *
     * @param stream Stream to serialize entry to
     * @param handle Handle of entry to write (must be its index in this register)
     * @return Returns true if 'UID' encoding has been specified in stream. In this case, the caller must serialize the entry's uid.
     */
    public boolean writeEntry(BinaryOutputStream stream, int handle) {
        SerializationInfo.RegisterEntryEncoding encoding = stream.getTargetInfo().getRegisterEntryEncoding(uid);
        if (encoding == SerializationInfo.RegisterEntryEncoding.UID) {
            return true;
        }
        if (encoding.ordinal() >= SerializationInfo.RegisterEntryEncoding.PUBLISH_REGISTER_ON_DEMAND.ordinal()) {
            stream.writeRegisterUpdates(uid, handle, sizeOfHandle);
        }
        stream.writeInt(handle, sizeOfHandle);
        return false;
    }

    /**
     * Write last entry of this register stream.
     * If this register is published, this will update the complete remote register (main purpose of this convenience method).
     *
     * @param stream Stream to serialize to
     * @return Returns true if 'UID' encoding has been specified in stream. In this case, the caller must serialize the entry's uid.
     */
    public boolean writeLastEntry(BinaryOutputStream stream) {
        return writeEntry(stream, this.size() - 1);
    }


    /** Size and number of chunks */
    private final int chunkCount, chunkSize;

    /** Size of handle in serialization (1 or 2 or 4) */
    private final int sizeOfHandle;

    /** Uid of this (global) register. -1 means that no uid has been assigned yet */
    int uid;

    /** Array with chunks */
    private final Object[][] chunks;

    /** Internal size variable */
    private AtomicInteger size = new AtomicInteger();

    /** Change listener list */
    private ArrayList<Listener<Entry>> listeners = new ArrayList<>();

}
