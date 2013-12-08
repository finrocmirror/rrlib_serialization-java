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

import org.rrlib.serialization.rtti.Copyable;
import org.rrlib.serialization.rtti.DataTypeBase;

/**
 * @author Max Reichardt
 *
 * Generic enum value.
 * Currently only meant for use in static parameters.
 * (In port-classes it's probably better to wrap port classes)
 */
public class EnumValue implements BinarySerializable, StringSerializable, Copyable<EnumValue>, NumericRepresentation {

    /** Data Type of this enum value */
    private DataTypeBase type;

    /** Current wrapped enum value */
    private int value;

    public EnumValue() {}

    public EnumValue(DataTypeBase type) {
        this.type = type;
    }

    public EnumValue(String s) {}

    @Override
    public void serialize(BinaryOutputStream os) {
        os.writeEnum(value, type.getEnumConstants());
    }

    @Override
    public void deserialize(BinaryInputStream is) {
        value = is.readEnum(type.getEnumConstants());
    }

    @Override
    public void serialize(StringOutputStream os) {
        os.append(type.getEnumConstants()[value].toString()).append(" (").append(value).append(")");
    }

    @Override
    public void deserialize(StringInputStream is) throws Exception {
        value = is.readEnum(type.getEnumConstants());
    }

    /**
     * @return Enum value's ordinal
     */
    public int getOrdinal() {
        return value;
    }

    @Override
    public void copyFrom(EnumValue source) {
        value = source.value;
        type = source.type;
    }

    /**
     * Perform "natural" formatting on enum name (see C++ enum_strings_builder)
     *
     * @param enumConstant Enum constant string to format
     * @return Naturally formatted string
     */
    public static String doNaturalFormatting(String enumConstant) {
        String[] parts = enumConstant.split("_");
        String result = "";
        for (String part : parts) {
            part = part.substring(0, 1).toUpperCase() + part.substring(1).toLowerCase();
            result += part + " ";
        }
        return result.trim();
    }

    @Override
    public Number getNumericRepresentation() {
        return value;
    }

    /**
     * @param ordinal New ordinal value
     */
    public void set(int ordinal) {
        value = ordinal;
    }

    /**
     * @return Data Type of this enum value
     */
    public DataTypeBase getType() {
        return type;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof EnumValue) {
            EnumValue o = (EnumValue)other;
            return value == o.value && type == o.type;
        }
        return false;
    }

    public String toString() {
        return Serialization.serialize(this);
    }
}
