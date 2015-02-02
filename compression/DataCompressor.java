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
 * Java equiavalent to rrlib_data_compression::tDataCompressor
 *
 * Compression instance for one channel of data of any supported type in any supported format.
 * Can be used to (de)compress single data entries ("frames" in the following) as well as a series frames
 * (e.g. in a video stream).
 * Can wrap compression algorithms for single frames (e.g. JPEG) as well as ones that calculate deltas
 * between frames (e.g. h264).
 */
public class DataCompressor {

    /** Wrapped data compression algorithm instance */
    private DataCompressionAlgorithm compressionAlgorithm;

    /** Info about wrapped compression algorithm */
    private DataCompressionAlgorithm.Info compressionAlgorithmInfo;

    /**
     * @param dataType Java data type that is to be compressed by this instance
     * @param compressionType Type of compression to use (e.g. "jpg")
     * @exception Throws exception if no compression algorithm for the specified parameters is available
     */
    public DataCompressor(Class<?> dataType, String compressionType) throws Exception {
        synchronized (DataCompressionAlgorithm.getRegisteredCompressionAlgorithms()) {
            for (DataCompressionAlgorithm.Info info : DataCompressionAlgorithm.getRegisteredCompressionAlgorithms()) {
                if ((info.dataType == dataType || info.dataType == null) && info.compressionType == compressionType) {
                    if (info.javaClass == DataCompressionAlgorithm.CompressibleAdapter.class) {
                        compressionAlgorithm = new DataCompressionAlgorithm.CompressibleAdapter(info);
                    } else {
                        compressionAlgorithm = info.javaClass.newInstance();
                    }
                    compressionAlgorithmInfo = info;
                    return;
                }
            }
            throw new Exception("No compression algorithm for Java type '" + dataType.getName() + "' with name '" + compressionType + "' registered.");
        }
    }

    /**
     * Compress next frame and write to specified stream.
     *
     * @param stream Stream to write data to
     * @param object Frame to compress. Must have the type specified in the constructor.
     * @param enforceKeyFrame Enforce a key frame (data can be restored from the written data without info from the previous ones)
     * @return True if a key frame was written to stream. Always true for single-frame algorithms.
     */
    public boolean compressNext(BinaryOutputStream stream, Object object, boolean enforceKeyFrame) {
        return compressionAlgorithm.compressNext(stream, object, enforceKeyFrame);
    }

    /**
     * Decompress next frame to provided buffer.
     *
     * @param stream Stream to read data from
     * @param buffer Frame buffer to decompress data to.
     * @param maxBytesToRead the maximum number of bytes this method is allowed to read from the stream
     * @exception Throws Exception if decompressing fails (e.g. because data is corrupted/incomplete).
     *            In this case, the provided buffer will be reset to an empty default (e.g. black image).
     */
    public void decompressNext(BinaryInputStream stream, Object buffer, int maxBytesToRead) throws Exception {
        compressionAlgorithm.decompressNext(stream, buffer, maxBytesToRead);
    }

    /**
     * @return Type of compression used (e.g. "jpg"). It is safe to store this reference (const char*) for program lifetime.
     */
    public String getCompressionType() {
        return compressionAlgorithmInfo.compressionType;
    }

    /**
     * @return Java data type that is to be compressed by this instance
     */
    public Class<?> getDataType() {
        return compressionAlgorithmInfo.dataType;
    }

    /**
     * Set compression-algorithm-specific parameter.
     * Parameters may be changed between frames.
     *
     * @param parameterName Parameter name
     * @param value New value for parameter
     * @exception Throws std::exception if compression algorithm does not have parameter of specified name - or if value is not supported
     */
    void setParameter(String parameterName, String value) throws Exception {
        compressionAlgorithm.setParameter(parameterName, value);
    }
}
