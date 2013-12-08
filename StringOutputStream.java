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

    public StringBuilder append(String str) {
        return wrapped.append(str);
    }

    public StringBuilder append(StringBuffer sb) {
        return wrapped.append(sb);
    }

    public StringBuilder append(CharSequence s) {
        return wrapped.append(s);
    }

    public StringBuilder append(CharSequence s, int start, int end) {
        return wrapped.append(s, start, end);
    }

    public StringBuilder append(char[] str) {
        return wrapped.append(str);
    }

    public StringBuilder append(char[] str, int offset, int len) {
        return wrapped.append(str, offset, len);
    }

    public StringBuilder append(boolean b) {
        return wrapped.append(b);
    }

    public StringBuilder append(char c) {
        return wrapped.append(c);
    }

    public StringBuilder append(int i) {
        return wrapped.append(i);
    }

    public StringBuilder append(long lng) {
        return wrapped.append(lng);
    }

    public StringBuilder append(float f) {
        return wrapped.append(f);
    }

    public StringBuilder append(double d) {
        return wrapped.append(d);
    }

    public StringBuilder append(Enum<?> d) {
        return wrapped.append(EnumValue.doNaturalFormatting(d.toString())).append(" (").append(d.ordinal()).append(")");
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
