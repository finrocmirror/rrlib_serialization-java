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
 * String output stream.
 * Used for completely serializing object to a string stream (UTF-8).
 */
public class StringOutputStream {

    /** Wrapped string stream */
    StringBuilder wrapped = new StringBuilder();

    public StringOutputStream() {
    }

    /**
     * @param length Initial length of buffer (TODO: in C++ this currently has now effect)
     */
    public StringOutputStream(int length) {
        wrapped.setLength(length);
    }

    public StringOutputStream append(String str) {
        wrapped.append(str);
        return this;
    }

    public StringOutputStream append(StringBuffer sb) {
        wrapped.append(sb);
        return this;
    }

    public StringOutputStream append(CharSequence s) {
        wrapped.append(s);
        return this;
    }

    public StringOutputStream append(CharSequence s, int start, int end) {
        wrapped.append(s, start, end);
        return this;
    }

    public StringOutputStream append(char[] str) {
        wrapped.append(str);
        return this;
    }

    public StringOutputStream append(char[] str, int offset, int len) {
        wrapped.append(str, offset, len);
        return this;
    }

    public StringOutputStream append(boolean b) {
        wrapped.append(b);
        return this;
    }

    public StringOutputStream append(char c) {
        wrapped.append(c);
        return this;
    }

    public StringOutputStream append(int i) {
        wrapped.append(i);
        return this;
    }

    public StringOutputStream append(long lng) {
        wrapped.append(lng);
        return this;
    }

    public StringOutputStream append(float f) {
        wrapped.append(f);
        return this;
    }

    public StringOutputStream append(double d) {
        wrapped.append(d);
        return this;
    }

    public StringOutputStream append(Enum<?> d) {
        wrapped.append(EnumValue.doNaturalFormatting(d.toString())).append(" (").append(d.ordinal()).append(")");
        return this;
    }

    /**
     * Write object to stream (without any type information)
     *
     * @param object Object to write to stream
     */
    public StringOutputStream appendObject(Object object) {
        return appendObject(object, object.getClass());
    }

    /**
     * Write object to stream (without any type information)
     *
     * @param object Object to write to stream
     * @param type Type of object (if serialization is consistent, could be base class)
     */
    @SuppressWarnings("rawtypes")
    public StringOutputStream appendObject(Object object, Class<?> type) {
        if (StringSerializable.class.isAssignableFrom(type)) {
            ((StringSerializable)object).serialize(this);
        } else if (type.isPrimitive() || Number.class.isAssignableFrom(type) || Boolean.class.equals(type)) {
            append(object.toString());
        } else {
            assert(object != null && (object.getClass() == type));
            if (type.isEnum()) {
                append((Enum)object);
            } else if (type == String.class) {
                append(object.toString());
            } else {
                throw new RuntimeException("Unsupported type");
            }
        }
        return this;
    }

    public String toString() {
        return wrapped.toString();
    }

    /**
     * Clear contents and reset
     */
    public void clear() {
        wrapped.delete(0, wrapped.length());
    }
}
