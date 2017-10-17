//
// You received this file as part of Finroc
// A framework for intelligent robot control
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

import java.nio.ByteBuffer;

import org.rrlib.serialization.rtti.DataType;

/**
 * @author Max Reichardt
 *
 * Interface for all data types that contain a raw memory buffer with constant-size records
 * (typically made up of integer and floating point data entries).
 *
 * The name is derived from OpenGL Array Buffers - and the major purpose
 * of this interface is actually using the data in OpenGL Buffers.
 */
public interface ArrayBuffer extends BinarySerializable {

    /** Data Type */
    public final static DataType<ArrayBuffer> TYPE = new DataType<ArrayBuffer>(ArrayBuffer.class);

    /** OpenGL attribute types */
    enum AttributeType {
        BYTE,
        UNSIGNED_BYTE,
        SHORT,
        UNSIGNED_SHORT,
        INT,
        UNSIGNED_INT,
        FIXED,
        FLOAT,
        HALF_FLOAT,
        DOUBLE,
        INT_2_10_10_10_REV,
        UNSIGNED_INT_2_10_10_10_REV
    };

    /** Size of OpenGL attribute types */
    public static final int ATTRIBUTE_TYPE_SIZE[] = { 1, 1, 2, 2, 4, 4, 4, 4, 2, 8, 4, 4 };

    /**
     * @return Byte Buffer containing actual data
     */
    public ByteBuffer getByteBuffer();

    /**
     * @return The number of entries (records) in array buffer in each array dimension
     */
    public int[] getArrayDimensions();

    /**
     * @return Channels in this array buffer (e.g. R, G, and B)
     */
    public Channel[] getChannels();

    public class Empty implements ArrayBuffer {

        @Override
        public void serialize(BinaryOutputStream stream) {}

        @Override
        public void deserialize(BinaryInputStream stream) throws Exception {}

        private static final ByteBuffer buffer = ByteBuffer.allocate(1);
        private static final int[] dimensions = { 0 };
        private static final Channel[] channels = new Channel[0];

        @Override
        public ByteBuffer getByteBuffer() {
            return buffer;
        }

        @Override
        public int[] getArrayDimensions() {
            return dimensions;
        }

        @Override
        public Channel[] getChannels() {
            return channels;
        }
    }

    /**
     * Data channels in array buffer (e.g. R, G, and B for RGB24 images)
     */
    public class Channel {

        /**
         * @param dataType Data type of channel
         * @param offset Offset of this channel's entry of first array element in byte buffer
         * @param stride Byte offset between consecutive array elements of this channel (in byte buffer)
         * @param name Name of data channel
         */
        public Channel(AttributeType dataType, int offset, int stride, String name) {
            this.name = name;
            this.offset = offset;
            this.stride = stride;
            this.dataType = dataType;
        }

        /**
         * @param dataType Data type of channels
         * @param offset Offset of this channels' entry of first array element in byte buffer
         * @param stride Byte offset between consecutive array elements of these channels (in byte buffer)
         * @param names Names of data channels (multiple names can be entered if several channels of the same type lie in memory consecutively)
         */
        public static Channel[] create(AttributeType dataType, int offset, int stride, String... names) {
            Channel[] result = new Channel[names.length];
            for (int i = 0; i < names.length; i++) {
                result[i] = new Channel(dataType, offset + i * ATTRIBUTE_TYPE_SIZE[dataType.ordinal()], stride, names[i]);
            }
            return result;
        }

        /**
         * @return Data type of channel
         */
        public AttributeType getDataType() {
            return dataType;
        }

        /**
         * @return Name of data channel
         */
        public String getName() {
            return name;
        }

        /**
         * @return Offset of this channel's entry of first array element in byte buffer
         */
        public int getOffset() {
            return offset;
        }

        /**
         * @return Byte offset between consecutive array elements of this channel (in byte buffer)
         */
        public int getStride() {
            return stride;
        }

        /**
         * @param offset Offset of this channel's entry of first array element in byte buffer
         */
        public void setOffset(int offset) {
            this.offset = offset;
        }

        /**
         * @param channels Channels as received from array buffer object
         * @param nameOrIndex Name or index of channel
         * @return Channel in channel array with specified name or index; null if no such channel exists
         */
        public static Channel get(Channel[] channels, String nameOrIndex) {
            for (int i = 0; i < channels.length; i++) {
                if (channels[i].getName().equalsIgnoreCase(nameOrIndex)) {
                    return channels[i];
                }
            }
            try {
                return channels[Integer.parseInt(nameOrIndex)];
            } catch (Exception e) {}
            return null;
        }


        /** Names of data channels */
        private final String name;

        /** Offset of this channel's entry of first array element in byte buffer */
        private int offset;

        /** Byte offset between consecutive array elements of this channel (in byte buffer) */
        private final int stride;

        /** Data type of channel */
        private final AttributeType dataType;
    }
}
