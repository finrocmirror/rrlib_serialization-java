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
package org.rrlib.serialization.rtti;

import java.lang.reflect.Modifier;
import java.util.ArrayList;

import org.rrlib.serialization.EnumValue;
import org.rrlib.serialization.PortDataListImpl;
import org.rrlib.serialization.Serialization;

/**
 * @author Max Reichardt
 *
 * Objects of this class contain info about the data type T
 */

public class DataType<T> extends DataTypeBase {

    public DataType(Class<T> javaClass) {
        this(javaClass, null);
    }

    public DataType(Class<?> javaClass, String name) {
        this(javaClass, name, true);
    }

    @SuppressWarnings("rawtypes")
    public DataType(Class<?> javaClass, String name, boolean createListTypes) {
        super(name != null ? name : javaClass.getSimpleName());
        this.javaClass = javaClass;
        typeTraits = IS_DATA_TYPE |
                     (Serialization.isBinarySerializable(javaClass) ? IS_BINARY_SERIALIZABLE : 0) |
                     (Serialization.isStringSerializable(javaClass) ? IS_STRING_SERIALIZABLE : 0) |
                     (Serialization.isXmlSerializable(javaClass) ? IS_XML_SERIALIZABLE : 0);
        if (javaClass.isEnum()) {
            ArrayList<String> constants = new ArrayList<String>();
            for (Object o : javaClass.getEnumConstants()) {
                constants.add(EnumValue.doNaturalFormatting(o.toString()));
            }
            enumConstants = constants.toArray();
            typeTraits |= IS_ENUM;
        }

        if (createListTypes && listType == null) {
            listType = new DataType(this);
        }
    }

    @SuppressWarnings("rawtypes")
    public DataType(Class<?> javaClass, Class<?> dedicatedListType, String name) {
        this(javaClass, name, false);
        listType = new DataType(this);
        listType.javaClass = dedicatedListType;
    }

    /**
     * Constructor for list types
     */
    private DataType(DataTypeBase e) {
        super("List<" + e.getName() + ">");
        this.elementType = e;
        this.typeTraits = (e.typeTraits & (IS_BINARY_SERIALIZABLE | IS_STRING_SERIALIZABLE | IS_XML_SERIALIZABLE) | IS_DATA_TYPE | IS_LIST_TYPE);
    }

    @SuppressWarnings({ "rawtypes" })
    @Override
    public Object createInstance() {
        Object result = null;
        if (javaClass == null && (typeTraits & IS_LIST_TYPE) != 0) {
            return new PortDataListImpl(getElementType());
        }

        try {
            if (enumConstants != null) {
                return new EnumValue(this);
            } else if (!(javaClass.isInterface() || Modifier.isAbstract(javaClass.getModifiers()))) {
                result = javaClass.newInstance();
            } else { // whoops we have an interface - look for inner class that implements interface
                for (Class<?> cl : javaClass.getDeclaredClasses()) {
                    if (javaClass.isAssignableFrom(cl)) {
                        result = cl.newInstance();
                        break;
                    }
                }
                if (result == null) {
                    throw new RuntimeException("Interface and no suitable inner class");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    @Override
    public GenericObject createInstanceGeneric() {
        return new GenericObject(createInstance(), this, null);
    }
}
