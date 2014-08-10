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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;


/**
 * @author Max Reichardt
 *
 * Untyped base class for all data types.
 *
 * Assigns unique type-id to each data type.
 * Can be used as factory for data types (necessary for deserializing)
 * vectors containing pointers).
 *
 * A instance of DataType<T> must be created for each type T
 * this mechanism should work with.
 *
 * This class is passed by value
 */
public class DataTypeBase {

    /** type of data type */
    public enum Classification {
        PLAIN, // Plain type
        LIST, // List of objects of same type
        PTR_LIST, // List of objects with possibly objects of different types
        NULL, // Null type
        OTHER, // Other data type
        UNKNOWN // Unknown data type in current process
    }

    /** Relevant type traits across runtime environments */
    public static final byte IS_BINARY_SERIALIZABLE = 1 << 0;
    public static final byte IS_STRING_SERIALIZABLE = 1 << 1;
    public static final byte IS_XML_SERIALIZABLE = 1 << 2;
    public static final byte IS_ENUM = 1 << 3;

    /** Null type */
    public static DataTypeBase NULL_TYPE = new DataTypeBase(true);

    /** Maximum number of annotations */
    private static final int MAX_ANNOTATIONS = 10;

    /** we need to avoid reallocation in order to make ArrayList thread safe. Therefore it is created with this capacity. */
    private final static int MAX_TYPES = 2000;

    /** Type of data type */
    protected Classification type;

    /** Name of data type */
    private String name;

    /** Is this the default name? - then it may be changed */
    protected boolean defaultName = true;

    /** Data type uid */
    private short uid = -1;

    /** Java Class */
    protected Class<?> javaClass;

    /** In case of list: type of elements */
    protected DataTypeBase elementType;

    /** In case of element: list type (std::vector<T>) */
    protected DataTypeBase listType;

    /** In case of element: shared pointer list type (std::vector<std::shared_ptr<T>>) */
    protected DataTypeBase sharedPtrListType;

    /** Annotations to data type */
    private DataTypeAnnotation[] annotations = new DataTypeAnnotation[MAX_ANNOTATIONS];

    /** Enum Constants - if this a enum type */
    protected Object[] enumConstants;

    /** List with all registered data types (preallocated to avoid reallocations => concurrent use is possible) */
    private static ArrayList<DataTypeBase> types = new ArrayList<DataTypeBase>(MAX_TYPES);

    /** Lookup for data type annotation index */
    private static final HashMap < Class<?>, Integer > annotationIndexLookup = new HashMap < Class<?>, Integer > ();

    /** Last annotation index that was used */
    private static final AtomicInteger lastAnnotationIndex = new AtomicInteger(0);

    /** Type traits of this type (bit vector - see constants above) */
    protected byte typeTraits;

    ///** Is this a remote type? */
    //protected boolean remoteType = false;

    public DataTypeBase() {
        synchronized (types) {
            uid = (short)types.size();
            if (types.size() >= MAX_TYPES) {
                Log.log(LogLevel.ERROR, this, "Maximum number of data types exceeded. Increase cMAX_TYPES.");
                throw new RuntimeException("Maximum number of data types exceeded. Increase MAX_TYPES.");
            }
            types.add(this);
            Log.log(LogLevel.DEBUG_VERBOSE_1, this, "Adding data type " + getName());
        }
    }

    private DataTypeBase(boolean nulltype) {
        this.name = "NULL";
        this.uid = -1;
        this.type = Classification.NULL;
    }

    /**
     * @return Name of data type
     */
    public String getName() {
        return name;
    }

    /**
     * @return Number of registered types
     */
    public static short getTypeCount() {
        return (short)types.size();
    }

    /**
     * @return Uid of data type
     */
    public short getUid() {
        return uid;
    }

    /**
     * @return return "Type" of data type (see enum)
     */
    public Classification getType() {
        return type;
    }

    /**
     * @return In case of element: list type (std::vector<T>)
     */
    public DataTypeBase getListType() {
        return listType;
    }

    /**
     * @return In case of list: type of elements
     */
    public DataTypeBase getElementType() {
        return elementType;
    }

    /**
     * @return In case of element: shared pointer list type (std::vector<std::shared_ptr<T>>)
     */
    public DataTypeBase getSharedPtrListType() {
        return sharedPtrListType;
    }

    /**
     * @return Java Class of data type
     */
    public Class<?> getJavaClass() {
        return javaClass;
    }

    /**
     * @return Bit vector of type traits
     */
    public byte getTypeTraits() {
        return typeTraits;
    }

    /**
     * Lookup data type for class c
     *
     * @param c Class
     * @return Data type object - null if there's none
     */
    static public DataTypeBase findType(Class<?> c) {
        for (DataTypeBase db : types) {
            if (db.javaClass == c) {
                return db;
            }
        }
        return null;
    }

    /**
     * @param uid Data type uid
     * @return Data type with specified uid (possibly NULL_TYPE); null if no data type with this uid exists
     */
    static public DataTypeBase getType(short uid) {
        if (uid == -1) {
            return NULL_TYPE;
        }
        return types.get(uid);
    }

    /**
     * Lookup data type by name
     *
     * @param name Data Type name
     * @return Data type with specified name (possibly NULL_TYPE); null if it could not be found
     */
    static public DataTypeBase findType(String name) {
        if (name.equals(NULL_TYPE.name)) {
            return NULL_TYPE;
        }

        for (int i = 0; i < types.size(); i++) {
            DataTypeBase dt = types.get(i);
            if (name.equals(dt.getName())) {
                return dt;
            }
        }

        if (name.contains(".")) {
            return findType(removeNamespaces(name));
        }

        return null;
    }

    /**
     * @return Instance of Datatype T
     */
    public Object createInstance() {
        return null;
    }

    /**
     * @return Instance of Datatype as Generic object
     */
    protected GenericObject createInstanceGeneric() {
        return null;
    }

    /**
     * @param manager Manager for generic object
     * @return Instance of Datatype as Generic object
     */
    public GenericObject createInstanceGeneric(GenericObjectManager manager) {
        GenericObject result = createInstanceGeneric();
        result.jmanager = manager;

        if (manager != null) {
            manager.setObject(result);
        }

        return result;
    }

    /**
     * @param typeName Type name (in rrlib::rtti) format
     * @param The same type name without any namespaces (e.g. returns 'Pose2D' for 'rrlib.math.Pose2D')
     */
    static String removeNamespaces(String typeName) {
        StringBuilder sb = new StringBuilder();
        boolean inNamespace = false;
        for (int i = typeName.length() - 1; i >= 0; i--) {
            char c = typeName.charAt(i);
            if (c == '.') {
                inNamespace = true;
            }
            if (c == ',' || c == '<' || c == ' ') {
                inNamespace = false;
            }
            if (!inNamespace) {
                sb.append(c);
            }
        }
        sb.reverse();

        Log.log(LogLevel.DEBUG_VERBOSE_2, "DataTypeBase", "Input: " + typeName + " Output: " + sb.toString());
        return sb.toString();
    }

    /**
     * Can object of this data type be converted to specified type?
     * (In C++ currently only returns true, when types are equal)
     *
     * @param dataType Other type
     * @return Answer
     */
    public boolean isConvertibleTo(DataTypeBase dataType) {

        if (dataType == this) {
            return true;
        }
        if (type == Classification.NULL || dataType.type == Classification.NULL) {
            return false;
        }
        if (getType() == Classification.UNKNOWN || dataType.getType() == Classification.UNKNOWN) {
            return false;
        }
        if (getType() == Classification.LIST && dataType.getType() == Classification.LIST) {
            return getElementType().isConvertibleTo(dataType.getElementType());
        }
        if ((javaClass != null) && (dataType.javaClass != null)) {
            return dataType.javaClass.isAssignableFrom(javaClass);
        }
        return false;
    }

    /**
     * Add annotation to this data type
     *
     * @param ann Annotation
     */
    public <T extends DataTypeAnnotation> void addAnnotation(T ann) {
        assert(ann.annotatedType == null) : "Already used as annotation in other object. Not allowed (double deleteting etc.)";
        ann.annotatedType = this;
        int annIndex = -1;
        synchronized (types) {
            Integer i = annotationIndexLookup.get(ann.getClass());
            if (i == null) {
                i = lastAnnotationIndex.incrementAndGet();
                annotationIndexLookup.put(ann.getClass(), i);
            }
            annIndex = i;
        }

        assert(annIndex > 0 && annIndex < MAX_ANNOTATIONS);
        assert(annotations[annIndex] == null);

        annotations[annIndex] = ann;
    }

    /**
     * Get annotation of specified class
     *
     * @param c Class of annotation we're looking for
     * @return Annotation. Null if data type has no annotation of this type.
     */
    @SuppressWarnings("unchecked")
    public <T extends DataTypeAnnotation> T getAnnotation(Class<T> c) {
        return (T)annotations[annotationIndexLookup.get(c)];
    }

    public String toString() {
        return getName();
    }

    /**
     * \return If this is as enum type, returns enum constant names - otherwise NULL
     */
    public Object[] getEnumConstants() {
        return enumConstants;
    }

    /**
     * Set name of data type
     * (only valid if still default == not set before)
     *
     * @param newName New name of type
     */
    protected void setName(String newName) {
        if (!defaultName) {
            assert(name.equals(newName)) : "Name already set";
            return;
        }
        defaultName = false;
        name = newName;

        for (int i = 0; i < types.size(); i++) {
            if (i != uid && types.get(i).getName().equals(newName)) {
                Log.log(LogLevel.WARNING, "Two types with the same name were registered: " + newName);
            }
        }
    }
}
