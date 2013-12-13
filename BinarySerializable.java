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

import org.rrlib.serialization.rtti.DataType;


/**
 * @author Max Reichardt
 *
 * Classes that implement this interface can be serialized to and deserialized from binary data
 */
public interface BinarySerializable {

    /** So that binary serializable can be used as type (e.g. in Finroc ports) */
    public final static DataType<BinarySerializable> TYPE = new DataType<BinarySerializable>(BinarySerializable.class);

    /**
     * Serialize object to binary stream
     *
     * @param stream Binary stream to serialize to
     */
    public void serialize(BinaryOutputStream stream);

    /**
     * Deserialize object from binary stream
     *
     * @param node Binary stream to deserialize from
     */
    public void deserialize(BinaryInputStream stream) throws Exception;

    /**
     * Empty Binary serializable
     */
    public static class Empty implements BinarySerializable {

        public final static DataType<Empty> TYPE = new DataType<Empty>(Empty.class, "EmptyBinarySerializable");

        @Override
        public void serialize(BinaryOutputStream stream) {
        }

        @Override
        public void deserialize(BinaryInputStream stream) throws Exception {
        }
    }
}
