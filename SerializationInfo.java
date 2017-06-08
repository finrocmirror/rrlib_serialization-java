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
 * Info on target or source of serialization.
 * This is used for a simple mechanism to support different versions of serialization.
 *
 * Immutable
 */
public class SerializationInfo {

    /**
     * Specifies encoding of register entries in stream
     * (including whether registers are published and when they are updated)
     */
    public static enum RegisterEntryEncoding {

        /** Type handles of current process are used. Efficient, but not suitable for transferring entries to another process nor making data persistent. */
        LOCAL_HANDLE,

        /**
         * UIDs (e.g. unique names) of register entries are written to stream.
         * Least efficient option for longer lasting streams: requires more bandwidth per entry and also overhead for lookup at deserialization.
         */
        UID,

        /**
         * Register entries are encoded and sent to partner once. After that, entries are encoded with their local handle.
         * Remote register is updated, whenever entries are sent which have not yet been transferred (all entries up to the one are sent -> so sending the last one, will update the whole register).
         * Increased bandwidth requirements initially for publishing register entries.
         */
        PUBLISH_REGISTER_ON_DEMAND,

        /**
         * Register entries are encoded and sent to partner once. After that, entries are encoded with their local handle.
         * Remote register is updated, whenever there are new entries locally and any register entry (possibly from another register) is written to stream.
         * Increased bandwidth requirements initially for publishing all register entries.
         */
        PUBLISH_REGISTER_ON_CHANGE
    }

    /** Maximum number of published registers */
    public static final int MAX_PUBLISHED_REGISTERS = 15;

    /**
     * Revision of serialization.
     * The release version is encoded in this number (yymm). If revision is zero, this indicates the 14.08 release or earlier.
     *
     * For serialization targets: The release version that is supported by the target.
     * For serialization sources: At least this revision is required for deserialization. This is typically the minimum of the release version that serialization was created with and for.
     */
    private int revision = 0;

    /** Contains encodings for all register uids (2 bit per register uid). First is default encoding. */
    private int registerEntryEncodings = 0x55555555;

    /** Custom information that user of binary streams can attach to streams */
    private int customInfo = 0;


    public SerializationInfo() {
    }

    /**
     * @param revision Revision of serialization (see above)
     * @param defaultRegisterEntryEncoding Register encoding set as default for all registers
     * @param customInfo Custom information that user of binary streams can attach to streams
     */
    public SerializationInfo(int revision, RegisterEntryEncoding defaultRegisterEntryEncoding, int customInfo) {
        this.revision = revision;
        this.registerEntryEncodings = defaultRegisterEntryEncoding.ordinal() * 0x55555555;
        this.customInfo = customInfo;
    }

    /**
     * @param revision Revision of serialization (see above)
     * @param registerEntryEncodings Contains encodings for all register uids (2 bit per register uid). First is default encoding.
     * @param customInfo Custom information that user of binary streams can attach to streams
     */
    public SerializationInfo(int revision, int registerEntryEncodings, int customInfo) {
        this.revision = revision;
        this.registerEntryEncodings = registerEntryEncodings;
        this.customInfo = customInfo;
    }


    /**
     * @param registerUid Uid of register to get entry encoding for (-1 returns default register encoding)
     * @return Register entry encoding to use
     */
    public RegisterEntryEncoding getRegisterEntryEncoding(int registerUid) {
        return RegisterEntryEncoding.class.getEnumConstants()[((registerEntryEncodings >> (2 * (registerUid + 1))) & 0x3)];
    }

    /**
     * @return Revision of serialization
     */
    public int getRevision() {
        return revision;
    }

    /**
     * @return Contains encodings for all register uids (2 bit per register uid). First is default encoding.
     */
    public int getRegisterEntryEncodings() {
        return registerEntryEncodings;
    }

    /**
     * @return Custom information that user of binary streams can attach to streams
     */
    public int getCustomInfo() {
        return customInfo;
    }

    /**
     * @return Whether any registers are published
     */
    public boolean hasPublishedRegisters() {
        return (registerEntryEncodings & 0xAAAAAAAA) != 0;
    }

    /**
     * Helper to create 'registerEntryEncodings'
     *
     * @param defaultEncoding Default encoding to set 'count' entries to
     * @param count Number of registers with default encoding
     * @return registerEntryEncodings value
     */
    public static int setDefaultRegisterEntryEncoding(RegisterEntryEncoding defaultEncoding, int count) {
        int result = 0;
        for (int i = 0; i < count; i++) {
            result |= (result << 2) | defaultEncoding.ordinal();
        }
        return result;
    }

    /**
     * Helper to create 'registerEntryEncodings'
     *
     * @param registerEntryEncodings Value to modify
     * @param registerUid Uid of register to set entry encoding for
     * @param encoding Encoding to set
     * @return Modified value
     */
    public static int setRegisterEntryEncoding(int registerEntryEncodings, int registerUid, RegisterEntryEncoding encoding) {
        int shift = (registerUid + 1) * 2;
        int mask = (0x3) << shift;
        registerEntryEncodings &= (~mask);
        registerEntryEncodings |= (encoding.ordinal() << shift);
        return registerEntryEncodings;
    }
}
