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


/**
 * @author Max Reichardt
 *
 * Register of Registers that are available for auto-publishing mechanism
 */
public class PublishedRegisters {

    /** Base class for type used as TRemoteEntry (class with identical members work also) */
    public static abstract class RemoteEntryBase<TLocalEntry> {

        /**
         * Method for serializing local elements (the handle needn't be serialized)
         *
         * @param stream Stream to serialize to
         * @param entry Local entry to serialize
         */
        public abstract void serializeLocalRegisterEntry(BinaryOutputStream stream, Object entry);

        /**
         * An equivalent method for deserializing element as TRemoteEntry to this object (the handle needn't be deserialized)
         *
         * @param stream Stream to deserialize from
         */
        public abstract void deserializeRegisterEntry(BinaryInputStream stream) throws Exception;

        /**
         * @return Size of this entry's handle
         */
        public abstract int getHandleSize();

        /**
         * @return Remote handle of entry
         */
        public int getHandle() {
            return handle;
        }

        /**
         * @param handle Remote handle of entry
         */
        protected void setHandle(int handle) {
            this.handle = handle;
        }

        /**
         * Initializes element. May be overridden.
         */
        protected void init() {
        }

        /** Remote handle of entry */
        int handle;
    }

    /**
     * @param stream Stream whose (shared) data on remote register to obtain
     * @param registerUid Uid of register to obtain
     * @return Returns stream's (shared) remote register with registerUid (possibly null if it contains no entries or has not been registered)
     */
    public static Register<RemoteEntryBase<?>> getRemoteRegister(BinaryInputStream stream, int registerUid) {
        return stream.remoteRegisters[registerUid];
    }

    /**
     * Registers Register for use in auto-publishing mechanism.
     * UIDs must be consistent across all processes that read/write the same serialized data created with the automatic register publishing mechanisms.
     * Therefore, typically a high-level entity that knows all relevant registers should manage assignment of the UIDs.
     *
     * @param r Register to register
     * @param uid Uid to assign to register (must be <= MAX_PUBLISHED_REGISTERS)
     * @param remoteEntryClass Type to deserialize in remote runtime environment. It needs to be derived from PublishedRegisters.RemoteEntryBase and be default-constructible.
     * @throw Throws Exception on already occupied uid
     */
    public static void register(Register<?> r, int uid, Class<? extends RemoteEntryBase<?>> remoteEntryClass) throws Exception {
        if (registeredRegisters[uid] != null && registeredRegisters[uid].register != r) {
            throw new Exception("Uid already occupied with different register");
        }
        r.uid = uid;
        if (registeredRegisters[uid] != null) {
            return;
        }
        registeredRegisters[uid] = new PerRegisterInfo();
        registeredRegisters[uid].register = r;
        registeredRegisters[uid].remoteEntryClass = remoteEntryClass;
        registeredRegisters[uid].serializer = remoteEntryClass.newInstance();
    }

    /**
     * @return Empty element returned whenever element with index -1 is encountered in stream
     */
    public static RemoteEntryBase<?> getMinusOneElement() {
        return minusOneElement;
    }

    /**
     * @param minusOneElement Empty element returned whenever element with index -1 is encountered in stream
     */
    public static void setMinusOneElement(RemoteEntryBase<?> minusOneElement) {
        PublishedRegisters.minusOneElement = minusOneElement;
    }

    /**
     * @param uid Local register UID
     * @return Local per register info registered with specified uid
     */
    static PerRegisterInfo get(int uid) {
        return registeredRegisters[uid];
    }

    /**
     * @return Registered registers
     */
    private static final PerRegisterInfo[] registeredRegisters = new PerRegisterInfo[SerializationInfo.MAX_PUBLISHED_REGISTERS];

    /** Empty element returned whenever element with index -1 is encountered in stream */
    private static RemoteEntryBase<?> minusOneElement;

    /** Remote register */
    static class RemoteRegister extends Register<RemoteEntryBase<?>> {

        RemoteRegister(int chunkCount, int chunkSize, int sizeOfHandle, Class<? extends RemoteEntryBase<?>> remoteEntryClass) {
            super(chunkCount, chunkSize, sizeOfHandle);
            this.remoteEntryClass = remoteEntryClass;
        }

        void deserializeEntries(BinaryInputStream stream) throws Exception {

            int oldSize = size();
            if (stream.getSourceInfo().getRevision() == 0) {
                if (size() == 0) {
                    stream.readShort();
                }

                // legacy type exchange support
                short s = stream.readShort();
                while (s != -1) {
                    RemoteEntryBase<?> remoteEntry = remoteEntryClass.newInstance();
                    remoteEntry.deserializeRegisterEntry(stream);
                    remoteEntry.handle = size();
                    add(remoteEntry);
                    s = stream.readShort();
                }
            } else {
                int count = stream.readInt();
                for (int i = 0; i < count; i++) {
                    RemoteEntryBase<?> remoteEntry = remoteEntryClass.newInstance();
                    remoteEntry.deserializeRegisterEntry(stream);
                    remoteEntry.handle = size();
                    add(remoteEntry);
                }
            }
            for (int i = oldSize, n = size(); i < n; i++) {
                this.get(i).init();
            }

        }

        /** Class to use for remote entries */
        final Class<? extends RemoteEntryBase<?>> remoteEntryClass;
    }

    /** Info on every registered register */
    static class PerRegisterInfo {

        /** Local register */
        Register<?> register;

        /** RemoteEntryBase instance for serialization */
        RemoteEntryBase<?> serializer;

        /** Class to use for remote entries */
        Class<? extends RemoteEntryBase<?>> remoteEntryClass;

        /**
         * @return Remote register to use in InputStream
         */
        RemoteRegister createRemoteRegister() {
            return new RemoteRegister(register.getChunkCount(), register.getChunkSize(), register.getSizeOfHandle(), remoteEntryClass);
        }

        /**
         * @param stream Stream to serialize to
         * @param startElement First element to serialize
         * @param endElement One past the last element to serialize
         */
        void serializeEntries(BinaryOutputStream stream, int startElement, int endElement) {
            for (int i = startElement; i < endElement; i++) {
                serializer.serializeLocalRegisterEntry(stream, register.get(i));
            }
        }
    }

}
