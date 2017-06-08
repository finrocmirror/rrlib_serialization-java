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
import org.rrlib.serialization.rtti.DataTypeBase;


/**
 * @author Max Reichardt
 *
 * Classes that implement this interface can be serialized to and deserialized from Strings
 */
public interface StringSerializable {

    /**
     * Serialize object to string stream
     *
     * @param stream String stream to serialize to
     */
    public void serialize(StringOutputStream stream);

    /**
     * Deserialize object from string stream
     *
     * @param node String stream to deserialize from
     */
    public void deserialize(StringInputStream stream) throws Exception;


    /** Data type of this class */
    public final static DataTypeBase TYPE = new DataType<StringSerializable>(StringSerializable.class);

    /**
     * Empty String serializable
     */
    public static class Empty extends BinarySerializable.Empty implements StringSerializable {

        public final static DataType<StringSerializable.Empty> TYPE = new DataType<>(StringSerializable.Empty.class, "EmptyStringSerializable");

        @Override
        public void serialize(StringOutputStream stream) {
        }

        @Override
        public void deserialize(StringInputStream stream) throws Exception {
        }

        @Override
        public String toString() {
            return "";
        }
    }
}
