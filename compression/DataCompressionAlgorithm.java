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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.serialization.BinaryInputStream;
import org.rrlib.serialization.BinaryOutputStream;


/**
 * @author Max Reichardt
 *
 * Base class for implementation of a single data compression algorithm
 * (e.g. MJPG or H264 for coviroa images).
 * One of these objects is instantiated per channel. Any information
 * needed to generate delta frames needs to be stored in this instance.
 *
 * Class also contains register of available compression algorithms.
 * Algorithms are added to this register by either calling Register() or
 * by instantiating an instance of the specific algorithm.
 *
 * Users of data compression should use a tDataCompressor object that
 * creates and wraps one tDataCompressionAlgorithm instance.
 */
public abstract class DataCompressionAlgorithm {

    /**
     * Information about a single data compression algorithm.
     * Single entry in register of available algorithms.
     */
    public static class Info {

        /** Java data type that is to be compressed; no type means that any binary serializable type can be compressed */
        public final Class<?> dataType;

        /** Name of compression algorithm/format */
        public final String compressionType;

        /** Java Class that implements data compression algorithm (should have empty default constructor) */
        public final Class<? extends DataCompressionAlgorithm> javaClass;

        /** Does algorithm create delta frames? */
        public final boolean deltaFrames;

        public Info(Class<?> dataType, String compressionType, Class<? extends DataCompressionAlgorithm> javaClass, boolean deltaFrames) {
            this.dataType = dataType;
            this.compressionType = compressionType;
            this.javaClass = javaClass;
            this.deltaFrames = deltaFrames;
        }
    }

    /**
     * List with registered compression algorithms
     * Access should always be synchronized on this list
     */
    private static ArrayList<Info> registeredAlgorithms = new ArrayList<Info>();

    protected DataCompressionAlgorithm() {}

    /**
     * Compress next frame and write to specified stream.
     *
     * @param stream Stream to write data to
     * @param object Frame to compress. Must have the type specified in the constructor.
     * @param enforceKeyFrame Enforce a key frame (data can be restored from the written data without info from the previous ones)
     * @return True if a key frame was written to stream. Always true for single-frame algorithms.
     */
    public abstract boolean compressNext(BinaryOutputStream stream, Object object, boolean enforceKeyFrame);

    /**
     * Decompress next frame to provided buffer.
     *
     * @param stream Stream to read data from
     * @param buffer Frame buffer to decompress data to.
     * @param maxBytesToRead the maximum number of bytes this method is allowed to read from the stream
     * @exception Throws exception if decompressing fails (e.g. because data is corrupted/incomplete).
     *            In this case, the provided buffer will be reset to an empty default (e.g. black image).
     */
    public abstract void decompressNext(BinaryInputStream stream, Object buffer, int maxBytesToRead) throws Exception;

    /**
     * @return List with registered compression algorithms. Access should always be synchronized on this list.
     */
    public static List<Info> getRegisteredCompressionAlgorithms() {
        return Collections.unmodifiableList(registeredAlgorithms);
    }

    /**
     * Registers data compression algorithm for all binary-serializable Java types so that tDataCompressor's
     * generic constructor is able to find and instantiate it.
     *
     * @param compressionType Name of compression algorithm/format
     * @param deltaFrames Does algorithm create delta frames?
     * @param javaClass is the specific data compression subclass to register. It needs to have an empty default constructor.
     * @param return Returns zero. This way, function can be assigned to an integer at static initialization to register algorithm at this time.
     */
    public static int register(String compressionType, boolean deltaFrames, Class<? extends DataCompressionAlgorithm> javaClass) {
        return register(null, compressionType, deltaFrames, javaClass);
    }

    /**
     * Registers data compression algorithm for all binary-serializable C++ types so that tDataCompressor's
     * generic constructor is able to find and instantiate it.
     *
     * @param compressionType Name of compression algorithm/format
     * @param deltaFrames Does algorithm create delta frames?
     * @param javaClass is the specific data compression subclass to register. It needs to have an empty default constructor.
     * @param return Returns zero. This way, function can be assigned to an integer at static initialization to register algorithm at this time.
     */
    public static int register(Class<?> dataType, String compressionType, boolean deltaFrames) {
        return register(dataType, compressionType, deltaFrames, CompressibleAdapter.class);
    }

    /**
     * Registers data compression algorithm for one Java type so that tDataCompressor's generic constructor
     * is able to find and instantiate it.
     *
     * @param dataType Java data type that compressor compresses
     * @param compressionType Name of compression algorithm/format
     * @param deltaFrames Does algorithm create delta frames?
     * @param javaClass is the specific data compression subclass to register. It needs to have an empty default constructor.
     * @param return Returns zero. This way, function can be assigned to an integer at static initialization to register algorithm at this time.
     */
    public static int register(Class<?> dataType, String compressionType, boolean deltaFrames, Class<? extends DataCompressionAlgorithm> javaClass) {
        synchronized (registeredAlgorithms) {
            for (Info info : registeredAlgorithms) {
                if (info.dataType == dataType && info.compressionType == compressionType) {
                    if (info.javaClass != javaClass) {
                        Log.log(LogLevel.WARNING, "Compression '" + compressionType + "' for Java type '" + dataType.getName() + "' was registered with different compression classes (" + info.javaClass.getSimpleName() + " and " + javaClass.getSimpleName() + ")");
                    }
                    return 0;
                }
            }
        }

        registeredAlgorithms.add(new Info(dataType, compressionType, javaClass, deltaFrames));
        return 0;
    }

    /**
     * Set compression-algorithm-specific parameter.
     * Parameters may be changed between frames.
     *
     * @param parameterName Parameter name
     * @param value New value for parameter
     * @exception Throws exception if compression algorithm does not have parameter of specified name - or if value is not supported
     */
    public abstract void setParameter(String parameterName, String value) throws Exception;


    /**
     * Adapter for classes that implement Compressible interface
     */
    static class CompressibleAdapter extends DataCompressionAlgorithm {

        /** Info about format to use */
        final Info algorithmInfo;

        public CompressibleAdapter(Info algorithmInfo) {
            this.algorithmInfo = algorithmInfo;
        }

        @Override
        public boolean compressNext(BinaryOutputStream stream, Object object, boolean enforceKeyFrame) {
            ((Compressible)object).compressNext(stream, algorithmInfo.compressionType);
            return true;
        }

        @Override
        public void decompressNext(BinaryInputStream stream, Object buffer, int maxBytesToRead) throws Exception {
            ((Compressible)buffer).decompressNext(stream, algorithmInfo.compressionType, maxBytesToRead);
        }

        @Override
        public void setParameter(String parameterName, String value) throws Exception {
            throw new Exception("Not implemented");
        }
    }
}
