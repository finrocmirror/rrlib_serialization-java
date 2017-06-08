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
 * Buffer information
 * (can be passed to and modified by Manager (by reference))
 */
public class BufferInfo {

    /** Buffer that read view currently operates on */
    public FixedBuffer buffer = null;

    /** Start of buffer */
    public int start = 0;

    /** End of buffer */
    public int end = 0;

    /** Current read or write position */
    public int position = 0;

    /** Custom data that can be filled by source/sink that manages this buffer */
    public Object customData = null;

    /**
     * @param other Other Buffer Info to copy values from
     */
    public void assign(BufferInfo other) {
        buffer = other.buffer;
        start = other.start;
        end = other.end;
        position = other.position;
        customData = other.customData;
    }

    /**
     * Reset info to default/null values
     */
    public void reset() {
        buffer = null;
        start = 0;
        end = 0;
        position = 0;
        customData = null;
    }

    /**
     * @return Remaining bytes in buffer
     */
    public int remaining() {
        return end - position;
    }

    /**
     * @return Number of bytes to write (for Sinks)
     */
    public int getWriteLen() {
        return position - start;
    }

    /**
     * Set start and end position in buffer backend
     *
     * @param start Start position (inclusive)
     * @param end End position (exclusive
     */
    public void setRange(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public String toString() {
        if (buffer == null) {
            return "no buffer backend";
        } else {
            return "start: " + start + " position: " + position + " end: " + end;
        }
    }

    /**
     * @return Total size of buffer - as described by this BufferInfo object: end - start
     */
    public int capacity() {
        return end - start;
    }
}
