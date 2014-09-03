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

import java.io.IOException;
import java.io.StringReader;

import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;

/**
 * @author Max Reichardt
 *
 * String input stream.
 * Used for completely deserializing object from a string stream (UTF-8).
 */
public class StringInputStream {

    /** Wrapped string stream */
    StringReader wrapped;

    /** Constants for character flags */
    public static final byte LCASE = 1, UCASE = 2, LETTER = 4, DIGIT = 8, WHITESPACE = 16;

    /** Map with flags of all 256 UTF Characters */
    private static byte[] charMap = new byte[256];

    static {
        initCharMap();
    }

    public StringInputStream(String s) {
        wrapped = new StringReader(s);
    }

    public void close() {
        wrapped.close();
    }

    /**
     * Initializes char map
     *
     * @return dummy value
     */
    public static int initCharMap() {
        for (int i = 0; i < 256; i++) {
            byte mask = 0;
            if (Character.isLowerCase(i)) {
                mask |= LCASE;
            }
            if (Character.isUpperCase(i)) {
                mask |= UCASE;
            }
            if (Character.isLetter(i)) {
                mask |= LETTER;
            }
            if (Character.isDigit(i)) {
                mask |= DIGIT;
            }
            if (Character.isWhitespace(i)) {
                mask |= WHITESPACE;
            }
            charMap[i] = mask;
        }
        return 0;
    }

    /**
     * @return String until end of stream
     */
    public String readAll() {
        return readUntil("", 0, false);
    }

    /**
     * @return String util end of line
     */
    public String readLine() {
        return readUntil("\n", 0, false);
    }

    /**
     * Read characters until a "stop character" is encountered
     *
     * @param stopAtChars List of "stop characters"
     * @param stopAtFlags Make all characters with specified flags "stop characters"
     * @param trimWhitespace Trim whitespace after reading?
     * @return String
     */
    public String readUntil(String stopAtChars, int stopAtFlags, boolean trimWhitespace) {

        StringBuilder sb = new StringBuilder();
        int validCharLen = stopAtChars.length();
        char[] ca = stopAtChars.toCharArray();
        while (true) {
            int c = read();
            if (c == -1) {
                break;
            }

            if ((charMap[c] & stopAtFlags) != 0) {
                unget();
                break;
            }

            boolean stop = false;
            for (int i = 0; i < validCharLen; i++) {
                if (c == ca[i]) {
                    stop = true;
                    break;
                }
            }
            if (stop) {
                unget();
                break;
            }

            sb.append((char)c);
        }

        if (trimWhitespace) {
            return sb.toString().trim();
        }

        return sb.toString();
    }

    /**
     * Read "valid" characters. Stops at "invalid" character
     *
     * @param validChars List of "valid characters"
     * @param validFlags Make all characters with specified flags "valid characters"
     * @param trimWhitespace Trim whitespace after reading?
     * @return String
     */
    public String readWhile(String validChars, int validFlags, boolean trimWhitespace) {

        StringBuilder sb = new StringBuilder();
        int validCharLen = validChars.length();
        char[] ca = validChars.toCharArray();
        while (true) {
            int c = read();
            if (c == -1) {
                break;
            }

            if ((charMap[c] & validFlags) == 0) {
                boolean valid = false;
                for (int i = 0; i < validCharLen; i++) {
                    if (c == ca[i]) {
                        valid = true;
                        break;
                    }
                }
                if (!valid) {
                    unget();
                    break;
                }
            }

            sb.append((char)c);
        }

        if (trimWhitespace) {
            return sb.toString().trim();
        }

        return sb.toString();
    }

    /**
     * @return next character in stream. -1 when end of stream is reached
     */
    public int read() {
        try {
            wrapped.mark(256);
            return wrapped.read();
        } catch (IOException e) {
        }
        return -1;
    }

    /**
     * @return next character in stream (without advancing in stream). -1 when end of stream is reached
     */
    public int peek() {
        try {
            wrapped.mark(256);
            int result = wrapped.read();
            wrapped.reset();
            return result;
        } catch (IOException e) {
        }
        return -1;
    }

    /**
     * Put read character back to stream
     */
    public void unget() {
        try {
            wrapped.reset();
        } catch (IOException e) {
        }
    }

    public String getLogDescription() {
        return "StringInputStream()";
    }

    /**
     * @return Enum value
     */
    @SuppressWarnings({ "rawtypes" })
    public <E extends Enum> E readEnum(Class<E> eclass) {
        return eclass.getEnumConstants()[readEnum(eclass.getEnumConstants(), null)];
    }

    /**
     * @param enumConstants Enum constants (only their toString() method is used)
     * @param enumValue Non-standard enum value - otherwise null
     * @return Enum value (index in case of non-standard values)
     */
    public int readEnum(Object[] enumConstants, long[] enumValues) {
        // parse input
        String enumString = readWhile("", DIGIT | LETTER | WHITESPACE, true).trim();
        int c1 = read();
        String numString = "";
        if (c1 == '(') {
            numString = readUntil(")", 0, true).trim();
            int c2 = read();
            if (c2 != ')') {
                throw new RuntimeException("Did not read expected bracket");
            }
        }

        // deal with input
        if (enumString.length() > 0) {
            for (int i = 0; i < enumConstants.length; i++) {
                if (enumConstants[i] instanceof Enum) {
                    if (enumString.equalsIgnoreCase(EnumValue.doNaturalFormatting(enumConstants[i].toString()))) {
                        return i;
                    }
                } else if (enumString.equalsIgnoreCase(enumConstants[i].toString())) {
                    return i;
                }
            }
        }

        if (enumString.length() > 0) {
            Log.log(LogLevel.WARNING, this, "Could not find enum constant for string '" + enumString + "'. Trying number '" + numString + "'");
        }
        if (numString.length() == 0) {
            throw new RuntimeException("No Number String specified");
        }
        long num = Long.parseLong(numString);
        if (enumValues != null) {
            boolean found = false;
            for (int i = 0; i < enumValues.length; i++) {
                if (num == enumValues[i]) {
                    found = true;
                    num = i;
                    break;
                }
            }
            if (!found) {
                throw new RuntimeException("Number not a valid enum constant");
            }
        }

        if (num >= enumConstants.length) {
            Log.log(LogLevel.ERROR, this, "Number " + num + " out of range for enum (" + enumConstants.length + ")");
            throw new RuntimeException("Number out of range");
        }
        return (int)num;
    }

    /**
     * Deserialize boolean from string stream
     *
     * @return Boolean value
     */
    public boolean readBoolean() {
        String s = readWhile("", StringInputStream.LETTER | StringInputStream.DIGIT | StringInputStream.WHITESPACE, true);
        return s.toLowerCase().equals("true") || s.equals("1");
    }

    /**
     * Deserializes object of specified type
     *
     * @param type Type object must have
     * @return Deserialized object (new object for immutable types, provided object in case of a mutable type)
     */
    public Object readObject(Class<?> type) throws Exception {
        return readObject(null, type);
    }

    /**
     * Deserializes object of specified type
     *
     * @param deserializeTo Object to call deserialize() on (optional; will contain result of deserialization, in case type is a mutable type)
     * @param type Type object must have
     * @return Deserialized object (new object for immutable types, provided object in case of a mutable type)
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object readObject(Object deserializeTo, Class<?> type) throws Exception {
        if (StringSerializable.class.isAssignableFrom(type)) {
            if (deserializeTo == null) {
                deserializeTo = type.newInstance();
            }
            ((StringSerializable)deserializeTo).deserialize(this);
            return deserializeTo;
        } else if (type.isPrimitive()) {
            if (type == boolean.class) {
                return readBoolean();
            } else if (type == float.class || type == double.class) {
                String s = readWhile("-.", StringInputStream.DIGIT | StringInputStream.WHITESPACE | StringInputStream.LETTER, true);
                if (type == float.class) {
                    return Float.parseFloat(s);
                } else {
                    return Double.parseDouble(s);
                }
            } else {
                String s = readWhile("-", StringInputStream.DIGIT | StringInputStream.WHITESPACE, true);
                if (type == byte.class) {
                    return Byte.parseByte(s);
                } else if (type == short.class) {
                    return Short.parseShort(s);
                } else if (type == int.class) {
                    return Integer.parseInt(s);
                } else if (type == long.class) {
                    return Long.parseLong(s);
                } else {
                    throw new Exception("Unsupported primitive type");
                }
            }
        } else {
            assert(deserializeTo != null && (deserializeTo.getClass() == type));
            if (type.isEnum()) {
                if (deserializeTo != null && deserializeTo.getClass().equals(EnumValue.class)) {
                    ((EnumValue)deserializeTo).deserialize(this);
                    return deserializeTo;
                }
                return readEnum((Class<? extends Enum>)type);
            } else if (type == String.class) {
                return readAll();
            } else {
                throw new RuntimeException("Unsupported type");
            }
        }
    }
}
