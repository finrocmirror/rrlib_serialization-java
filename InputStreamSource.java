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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;

/**
 * @author Max Reichardt
 *
 * Wraps Java Input Stream as source
 */
public class InputStreamSource implements Source {

    /** Wrapped input stream */
    private final InputStream wrapped;

    /** Source state */
    public enum State { INITIAL, OPENED, CLOSED }
    State state = State.INITIAL;

    public InputStreamSource(InputStream is) {
        wrapped = is;
    }

    @Override
    public void close(BinaryInputStream inputStreamBuffer, BufferInfo buffer) {
        try {
            wrapped.close();
            buffer.reset();
            state = State.CLOSED;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void directRead(BinaryInputStream inputStreamBuffer, FixedBuffer buffer, int offset, int len) {
        try {
            byte[] tmp = new byte[MemoryBuffer.TEMP_ARRAY_SIZE];
            while (len > 0) {
                int r = wrapped.read(tmp, 0, Math.min(len, tmp.length));
                if (r < 0) {
                    throw new EOFException();
                }
                buffer.put(offset, tmp, 0, r);
                offset += r;
                len -= r;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean directReadSupport() {
        return true;
    }

    @Override
    public void read(BinaryInputStream inputStreamBuffer, BufferInfo buffer, int len) {
        assert(len < buffer.buffer.capacity());
        try {
            int offset = 0;
            int av = wrapped.available();
            int read = Math.min(av, buffer.buffer.capacity() - offset);
            read = Math.max(read, len);
            byte[] tmp = new byte[2048];
            while (read > 0 || len > 0) {
                int r = wrapped.read(tmp, 0, Math.min(read, tmp.length));
                if (r < 0) {
                    throw new EOFException();
                }
                buffer.buffer.put(offset, tmp, 0, r);
                offset += r;
                read -= r;
                len -= r;
            }
            buffer.setRange(0, offset);
            buffer.position = 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reset(BinaryInputStream inputStreamBuffer, BufferInfo buffer) {
        assert(state == State.INITIAL);
        state = State.OPENED;
        assert(buffer.buffer == null);
        buffer.reset();
        buffer.buffer = new FixedBuffer(MemoryBuffer.DEFAULT_SIZE);
    }

    @Override
    public boolean moreDataAvailable(BinaryInputStream inputStreamBuffer, BufferInfo buffer) {
        try {
            return wrapped.available() > 0;
        } catch (IOException e) {
            Log.log(LogLevel.ERROR, this, e);
            return false;
        }
    }
}
