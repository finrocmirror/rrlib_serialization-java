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
package org.rrlib.serialization.compression;

import org.rrlib.serialization.BinaryInputStream;
import org.rrlib.serialization.BinaryOutputStream;


/**
 * @author Max Reichardt
 *
 * Classes that implement this interface can be compressed to binary streams
 */
public interface Compressible {

    /**
     * Compress and write to specified stream.
     *
     * @param stream Stream to write data to
     * @param compressionType Format to compress data in
     */
    public abstract void compressNext(BinaryOutputStream stream, String compressionType);

    /**
     * Decompress next frame to provided buffer.
     *
     * @param stream Stream to read data from
     * @param compressionType Format to compress data in
     * @param maxBytesToRead the maximum number of bytes this method is allowed to read from the stream
     * @exception Throws exception if decompressing fails (e.g. because data is corrupted/incomplete).
     *            In this case, the provided buffer will be reset to an empty default (e.g. black image).
     */
    public abstract void decompressNext(BinaryInputStream stream, String compressionType, int maxBytesToRead) throws Exception;

}
