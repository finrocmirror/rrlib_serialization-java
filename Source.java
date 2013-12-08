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
 * Abstract Data Source that can be used with InputStreamBuffer.
 *
 * Somewhat similar to boost iostreams input devices.
 * Is responsible for buffer management.
 */
public interface Source {

    /**
     * Reset input stream buffer for reading.
     * This is called initially when associating source with InputStreamBuffer.
     *
     * Supporting multiple reset operations is optional.
     * Streaming buffers typically won't support this (typically an assert will fail)
     *
     * @param inputStreamBuffer InputStreamBuffer that requests reset operation.
     * @param buffer BufferInfo object that will contain result - about buffer to initially operate on
     */
    public void reset(BinaryInputStream inputStreamBuffer, BufferInfo buffer);

    /**
     * Fetch next bytes for reading.
     *
     * Source is responsible for managing buffers that is writes/creates in buffer object
     *
     * if len is <= zero, method will not block
     * if len is greater, method may block until number of bytes in available
     *
     * @param inputStreamBuffer InputStreamBuffer that requests fetch operation.
     * @param buffer BufferInfo object that contains result of read operation (buffer managed by Source)
     * @param len Minimum number of bytes to read
     */
    public void read(BinaryInputStream inputStreamBuffer, BufferInfo buffer, int len);

    /**
     * @return Does source support reading directly into target buffer?
     * (optional optimization - does not have to make sense, depending on source)
     */
    public boolean directReadSupport();

    /**
     * (Optional operation)
     * Fetch next bytes for reading - and copy them directly to target buffer.
     *
     * @param inputStreamBuffer InputStreamBuffer that requests fetch operation.
     * @param buffer Buffer to copy data to (buffer provided and managed by client)
     * @param len Exact number of bytes to read
     */
    public void directRead(BinaryInputStream inputStreamBuffer, FixedBuffer buffer, int offset, int len);

    /**
     * Close stream/source.
     *
     * Possibly clean up buffer(s).
     *
     * @param inputStreamBuffer InputStreamBuffer that requests fetch operation.
     * @param buffer BufferInfo object that may contain buffer that needs to be deleted
     */
    public void close(BinaryInputStream inputStreamBuffer, BufferInfo buffer);

    /**
     * Is any more data available?
     *
     * @param inputStreamBuffer Buffer that requests operation
     * @param buffer Current buffer (managed by source)
     * @return Answer
     */
    public boolean moreDataAvailable(BinaryInputStream inputStreamBuffer, BufferInfo buffer);
}
