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

import org.rrlib.serialization.BinaryInputStream;
import org.rrlib.serialization.BinaryOutputStream;
import org.rrlib.serialization.Serialization;
import org.rrlib.serialization.StringInputStream;
import org.rrlib.serialization.StringOutputStream;
import org.rrlib.xml.XMLNode;


/**
 * @author Max Reichardt
 *
 * Container/wrapper for an arbitrary object.
 *
 * Provides base functionality such as deep copying, type information
 * and serialization.
 * It also assert that casting back is only possible to the original type.
 *
 * This allows to handle objects in a uniform way.
 *
 * Memory Layout of all subclasses: vtable ptr | datatype ptr | object ptr | management info raw memory of size M
 */
public class GenericObject extends TypedObjectImpl {

    /** Wrapped object */
    protected Object wrapped;

    /** Management information for this generic object. */
    protected GenericObjectManager jmanager;

    /**
     * @param wrappedObject Wrapped object
     * @param dt Data type of wrapped object
     * @param manager Manager of wrapped object (may be null)
     */
    public GenericObject(Object wrappedObject, DataTypeBase dt, GenericObjectManager manager) {
        this.wrapped = wrappedObject;
        this.type = dt;
        this.jmanager = manager;
    }

    /**
     * @return Wrapped object
     */
    public Object getData() {
        return wrapped;
    }

    /**
     * Deep copy source object to this object
     *
     * @param source Source object
     */
    public void deepCopyFrom(GenericObject source, Factory f) {
        wrapped = Serialization.deepCopy(source.wrapped, wrapped, f);
        type = source.type;
    }

    /**
     * @return Management information for this generic object.
     */
    public GenericObjectManager getManager() {
        return jmanager;
    }

    /**
     * Clear any shared resources that this object holds on to
     * (e.g. for reusing object in pool)
     */
    public void clear() {
        if (getData() instanceof Clearable) {
            ((Clearable)getData()).clearObject();
        }
    }

    /**
     * Deserialize data from binary input stream - possibly using non-binary encoding.
     *
     * @param stream Binary input stream
     * @param enc Encoding to use
     */
    public void deserialize(BinaryInputStream stream, Serialization.DataEncoding enc) throws Exception {
        wrapped = stream.readObject(wrapped, type.getJavaClass(), enc);
    }

    /**
     * Serialize data to binary output stream - possibly using non-binary encoding.
     *
     * @param stream Binary output stream
     * @param enc Encoding to use
     */
    public void serialize(BinaryOutputStream stream, Serialization.DataEncoding enc) {
        stream.writeObject(getData(), type.getJavaClass(), enc);
    }

    /**
     * Deserialize data from string stream
     *
     * @param stream String output stream
     */
    public void deserialize(StringInputStream stream) throws Exception {
        wrapped = stream.readObject(wrapped, type.getJavaClass());
    }

    /**
     * Serialize data to string stream
     *
     * @param node String input stream
     */
    public void serialize(StringOutputStream stream) throws Exception {
        stream.appendObject(wrapped, type.getJavaClass());
    }

    /**
     * Deserialize data from XML node
     *
     * @param node XML node
     */
    public void deserialize(XMLNode node) throws Exception {
        wrapped = Serialization.deserialize(node, wrapped, type.getJavaClass());
    }

    /**
     * Serialize data to XML node
     *
     * @param node XML node
     */
    public void serialize(XMLNode node) throws Exception {
        Serialization.serialize(node, this);
    }

}
