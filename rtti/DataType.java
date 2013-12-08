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

import org.rrlib.serialization.BinaryInputStream;
import org.rrlib.serialization.BinaryOutputStream;
import org.rrlib.serialization.BinarySerializable;
import org.rrlib.serialization.EnumValue;
import org.rrlib.serialization.PortDataListImpl;
import org.rrlib.serialization.Serialization;

/**
 * @author Max Reichardt
 *
 * Objects of this class contain info about the data type T
 */

public class DataType<T> extends DataTypeBase {

    /**
     * Data type info with factory functions
     */
    static class DataTypeInfo extends DataTypeInfoRaw {

        DataTypeBase dataType;

        public DataTypeInfo(Class<?> c) {
            type = Type.PLAIN;
            javaClass = c;
            name = c.getSimpleName();
            if (c.isEnum()) {
                ArrayList<String> constants = new ArrayList<String>();
                for (Object o : c.getEnumConstants()) {
                    constants.add(EnumValue.doNaturalFormatting(o.toString()));
                }
                enumConstants = constants.toArray();
            }
        }

        public DataTypeInfo(DataTypeBase e, Type type) {
            this.type = type;
            this.elementType = e;
            if (type == Type.LIST) {
                name = "List<" + e.getName() + ">";
            } else if (type == Type.PTR_LIST) {
                name = "List<" + e.getName() + "*>";
            } else {
                throw new RuntimeException("Unsupported");
            }
        }

        public DataTypeInfo() {
            throw new RuntimeException("do not call in Java");
        }

        @SuppressWarnings({ "rawtypes" })
        @Override
        public Object createInstance() {
            Object result = null;
            if (dataType.getType() == Type.LIST || dataType.getType() == Type.PTR_LIST) {
                return new PortDataListImpl(dataType.getElementType());
            }

            try {
                if (enumConstants != null) {
                    return new EnumValue(dataType);
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

        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public GenericObject createInstanceGeneric() {
            return new GenericObject(createInstance(), dataType, null);
        }

//        @Override
//        public void deepCopy(Object src, Object dest, Factory f) {
//            RRLibSerializable s = (RRLibSerializable)src;
//            RRLibSerializable d = (RRLibSerializable)dest;
//
//            Serialization.deepCopy(s, d, f);
//        }
    }

    public DataType(Class<T> c) {
        this(c, null);
    }

    public DataType(Class<?> c, String name) {
        this(c, name, true);
    }

    @SuppressWarnings("rawtypes")
    public DataType(Class<?> c, String name, boolean createListTypes) {
        super(getDataTypeInfo(c));
        if (name != null) {
            info.setName(name);
        }
        ((DataTypeInfo)info).dataType = this;

        if (createListTypes && ((DataTypeInfo)info).type == Type.PLAIN && info.listType == null) {
            info.listType = new DataType(this, Type.LIST);
            info.sharedPtrListType = new DataType(this, Type.PTR_LIST);
        }
    }

    /**
     * Constructor for list types
     */
    private DataType(DataTypeBase e, Type t) {
        super(new DataTypeInfo(e, t));
        ((DataTypeInfo)info).dataType = this;
    }

    private static DataTypeInfoRaw getDataTypeInfo(Class<?> c) {
        DataTypeBase dt = findType(c);
        if (dt != null) {
            return dt.info;
        }
        return new DataTypeInfo(c);
    }
}
