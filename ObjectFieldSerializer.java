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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Class for generic serialization of objects.
 *
 * It can collect all (public, non-transient, non-static) fields of an object
 * and serialize them to XML (somewhat similar to xstream, but more basic and lightweight).
 * Alternatively, it can do this with a predefined set of fields.
 *
 * In order to be able to extend this for further types, this class is meant
 * to be subclassed.
 */
public class ObjectFieldSerializer {

    /**
     * Collects all (public, non-transient, non-static) fields of an object
     * and serializes them to XML.
     *
     * @param node Node to serialize to
     * @param object Object to scan for fields and serialize
     */
    public void serialize(XMLNode node, Object object) throws Exception {
        ArrayList<Field> fields = getFieldsToSerialize(object.getClass());
        serializeFields(node, fields, object);
    }

    /**
     * Collects all (public, non-transient, non-static) fields of an object
     * and deserializes them from an XML node.
     *
     * @param node Node to deserialize from
     * @param object Object to scan for fields and deserialize
     * @param catchExceptionsForEveryField If a field cannot be deserialized, catch Exception and print error message to console? (otherwise, this method throws an exception)
     */
    public void deserialize(XMLNode node, Object object, boolean catchExceptionsForEveryField) throws Exception {
        ArrayList<Field> fields = getFieldsToSerialize(object.getClass());
        deserializeFields(node, fields, object, catchExceptionsForEveryField);
    }

    /**
     * Gets values of specified fields of an object and serializes them to XML.
     *
     * @param node Node to serialize to
     * @param fields Fields to serialize
     * @param object Object to scan for fields and serialize
     */
    public void serializeFields(XMLNode node, ArrayList<Field> fields, Object object) throws Exception {
        for (Field field : fields) {
            Object value = field.get(object);
            if (value != null) {
                serializeFieldValue(node.addChildNode(field.getName()), value);
            }
        }
    }

    /**
     * Deserializes values of specified fields of an object from XML
     *
     * @param node Node to deserialize from
     * @param fields Fields to deserialize
     * @param object Object to scan for fields and deserialize
     * @param catchExceptionsForEveryField If a field cannot be deserialized, catch Exception and print error message to console? (otherwise, this method throws an exception)
     */
    public void deserializeFields(XMLNode node, ArrayList<Field> fields, Object object, boolean catchExceptionsForEveryField) throws Exception {
        for (XMLNode child : node.children()) {
            for (Field field : fields) {
                if (field.getName().equalsIgnoreCase(child.getName())) {
                    //System.out.println("Deserializing field " + field.getName());
                    if (catchExceptionsForEveryField) {
                        try {
                            field.set(object, deserializeFieldValue(child, field.get(object), field.getType()));
                        } catch (Exception e) {
                            Log.log(LogLevel.ERROR, e);
                        }
                    } else {
                        field.set(object, deserializeFieldValue(child, field.get(object), field.getType()));
                    }
                    break;
                }
            }
        }
    }

    /**
     * Gets fields to serialize and deserialize (from object's class and all base classes).
     * Skips transient, non-public and static fields by default.
     *
     * @param c Class to check
     * @return List of (public) fields
     */
    public ArrayList<Field> getFieldsToSerialize(Class<?> c) {
        ArrayList<Field> result = new ArrayList<Field>();
        for (Field field : c.getFields()) {
            if ((!Modifier.isTransient(field.getModifiers())) && (!Modifier.isStatic(field.getModifiers()))) {
                result.add(field);
            }
        }
        if (c.getSuperclass() != null && (!c.getSuperclass().equals(Object.class))) {
            result.addAll(getFieldsToSerialize(c.getSuperclass()));
        }
        return result;
    }

    /**
     * Generic serialization of an object to an XML node
     * (calls Serialization.serialize by default - support for additional types can be added by overriding)
     *
     * @param node Node to serialize to
     * @param object Object to serialize
     */
    protected void serializeFieldValue(XMLNode node, Object object) throws Exception {
        Serialization.serialize(node, object);
    }

    /**
     * Generic deserialization of an object from an XML node
     * (calls Serialization.deserialize by default - support for additional types can added by overriding)
     *
     * @param node Node to deserialize from
     * @param deserializeTo Object to call deserialize() on (optional; will contain result of deserialization, in case type is a mutable type)
     * @param type Type object must have
     * @return Deserialized object (new object for immutable types, provided object in case of a mutable type)
     */
    protected Object deserializeFieldValue(XMLNode node, Object deserializeTo, Class<?> type) throws Exception {
        return Serialization.deserialize(node, deserializeTo, type);
    }

}
