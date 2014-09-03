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

    /** Current wrapped enum value (index of enum) */
    private int enumIndex;

    public EnumValue() {}

    public EnumValue(DataTypeBase type) {
        this.type = type;
    }

    public EnumValue(String s) {}

    @Override
    public void serialize(BinaryOutputStream os) {
        os.writeEnum(enumIndex, type.getEnumConstants());
    }

    @Override
    public void deserialize(BinaryInputStream is) {
        enumIndex = is.readEnum(type.getEnumConstants());
    }

    @Override
    public void serialize(StringOutputStream os) {
        os.append(toString());
    }

    @Override
    public void deserialize(StringInputStream is) throws Exception {
        enumIndex = is.readEnum(type.getEnumConstants(), type.getNonStandardEnumValues());
    }

    /**
     * @return Enum value's ordinal
     */
    public long getOrdinal() {
        if (type.getNonStandardEnumValues() != null) {
            return type.getNonStandardEnumValues()[enumIndex];
        }
        return enumIndex;
    }

    /**
     * @return Enum value index
     */
    public int getIndex() {
        return enumIndex;
    }

    @Override
    public void copyFrom(EnumValue source) {
        enumIndex = source.enumIndex;
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
        return getOrdinal();
    }

    /**
     * @param ordinal New ordinal value
     */
    public void setOrdinal(long ordinal) throws Exception {
        if (type.getNonStandardEnumValues() != null) {
            for (int i = 0; i < type.getNonStandardEnumValues().length; i++) {
                if (type.getNonStandardEnumValues()[i] == ordinal) {
                    enumIndex = i;
                    return;
                }
            }
            throw new Exception("No enum constant found for value " + ordinal);
        }
        enumIndex = (int)ordinal;
    }

    /**
     * @param ordinal New enum index
     */
    public void setIndex(int index) {
        if (index > type.getEnumConstants().length) {
            throw new IndexOutOfBoundsException("Invalid index " + index);
        }
        enumIndex = index;
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
            return enumIndex == o.enumIndex && type == o.type;
        }
        return false;
    }

    public String toString() {
        return type.getEnumConstants()[enumIndex].toString() + " (" + getOrdinal() + ")";
    }
}
