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
    public enum Type {
        PLAIN, // Plain type
        LIST, // List of objects of same type
        PTR_LIST, // List of objects with possibly objects of different types
        NULL, // Null type
        OTHER, // Other data type
        UNKNOWN // Unknown data type in current process
    }

    /**
     * Maximum number of annotations
     */
    private static final int MAX_ANNOTATIONS = 10;

    /** we need to avoid reallocation in order to make ArrayList thread safe. Therefore it is created with this capacity. */
    private final static int MAX_TYPES = 2000;


    /** Data type info */
    static public class DataTypeInfoRaw {

        /** Type of data type */
        public Type type;

        /** Name of data type */
        public String name;

        /** New info? */
        boolean newInfo = true;

        /*! Is this the default name? - then it may be changed */
        public boolean defaultName = true;

        /** Data type uid */
        public short uid = -1;

        /** Java Class */
        public Class<?> javaClass;

        /** In case of list: type of elements */
        public DataTypeBase elementType;

        /** In case of element: list type (std::vector<T>) */
        public DataTypeBase listType;

        /** In case of element: shared pointer list type (std::vector<std::shared_ptr<T>>) */
        public DataTypeBase sharedPtrListType;

        /** Annotations to data type */
        public DataTypeAnnotation[] annotations = new DataTypeAnnotation[MAX_ANNOTATIONS];

        /** Enum Constants - if this a enum type */
        public Object[] enumConstants;

        public DataTypeInfoRaw() {
            defaultName = true;
        }

        /**
         * Set name of data type
         * (only valid if still default == not set before)
         *
         * @param newName New name of type
         */
        public void setName(String newName) {
            if (!defaultName) {
                assert(name.equals(newName)) : "Name already set";
                return;
            }

            defaultName = false;
            name = newName;
        }

        /**
         * @return Instance of type casted to Object
         */
        public Object createInstance() {
            return null;
        }

        /**
         * @return Instance of Datatype as Generic object
         */
        public GenericObject createInstanceGeneric() {
            return null;
        }

//        /**
//         * Deep copy objects
//         *
//         * @param src Src object
//         * @param dest Destination object
//         * @param f Factory to use
//         */
//        public void deepCopy(Object src, Object dest, Factory f) {}
    }

//    /** Maximum number of types */
//    public static final int MAX_TYPES = 2000;

    /** Pointer to data type info (should not be copied every time for efficiency reasons) */
    protected final DataTypeInfoRaw info;

    /** List with all registered data types */
    private static ArrayList<DataTypeBase> types = new ArrayList<DataTypeBase>(MAX_TYPES);

    /** Null type */
    private static DataTypeBase NULL_TYPE = new DataTypeBase(null);

    /** Lookup for data type annotation index */
    private static final HashMap < Class<?>, Integer > annotationIndexLookup = new HashMap < Class<?>, Integer > ();

    /** Last annotation index that was used */
    private static final AtomicInteger lastAnnotationIndex = new AtomicInteger(0);

    /**
     * @param name Name of data type
     */
    public DataTypeBase(DataTypeInfoRaw info) {
        this.info = info;

        if (info != null && info.newInfo == true) {
            synchronized (types) {
                addType(info);
            }
        }
    }

    /**
     * Helper for constructor (needs to be called in synchronized context)
     */
    private void addType(DataTypeInfoRaw nfo) {
        nfo.uid = (short)getTypes().size();
        if (getTypes().size() >= MAX_TYPES) {
            Log.log(LogLevel.ERROR, this, "Maximum number of data types exceeded. Increase cMAX_TYPES.");
            throw new RuntimeException("Maximum number of data types exceeded. Increase MAX_TYPES.");
        }
        getTypes().add(this);
        nfo.newInfo = false;
        String msg = "Adding data type " + getName();
        Log.log(LogLevel.DEBUG_VERBOSE_1, this, msg);
    }

    /**
     * @return Name of data type
     */
    public String getName() {
        String unknown = "NULL";
        if (info != null) {
            return info.name;
        }
        return unknown;
    }

    /**
     * @return Number of registered types
     */
    public static short getTypeCount() {
        return (short)getTypes().size();
    }

    /**
     * @return uid of data type
     */
    public short getUid() {
        if (info != null) {
            return info.uid;
        }
        return -1;
    }

    /**
     * @return return "Type" of data type (see enum)
     */
    public Type getType() {
        if (info != null) {
            return info.type;
        }
        return Type.NULL;
    }

    /**
     * @return In case of element: list type (std::vector<T>)
     */
    public DataTypeBase getListType() {
        if (info != null) {
            return info.listType;
        }
        return getNullType();
    }

    /**
     * @return In case of list: type of elements
     */
    public DataTypeBase getElementType() {
        if (info != null) {
            return info.elementType;
        }
        return getNullType();
    }

    /**
     * @return In case of element: shared pointer list type (std::vector<std::shared_ptr<T>>)
     */
    public DataTypeBase getSharedPtrListType() {
        if (info != null) {
            return info.sharedPtrListType;
        }
        return getNullType();
    }

    /**
     * @return Java Class of data type
     */
    public Class<?> getJavaClass() {
        if (info != null) {
            return info.javaClass;
        } else {
            return null;
        }
    }

//    /**
//     * Deep copy objects
//     *
//     * @param src Src object
//     * @param dest Destination object
//     * @param f Factory to use
//     */
//    public void deepCopy(Object src, Object dest, Factory f) {
//        if (info == null) {
//            return;
//        }
//        info.deepCopy(src, dest, f);
//    }

    /**
     * Helper method that safely provides static data type list
     */
    static private ArrayList<DataTypeBase> getTypes() {
        return types;
    }

    /**
     * Lookup data type for class c
     *
     * @param c Class
     * @return Data type object - null if there's none
     */
    static public DataTypeBase findType(Class<?> c) {
        for (DataTypeBase db : types) {
            if (db.info.javaClass == c) {
                return db;
            }
        }
        return null;
    }

    /**
     * @param uid Data type uid
     * @return Data type with specified uid
     */
    static public DataTypeBase getType(short uid) {
        if (uid == -1) {
            return getNullType();
        }
        return getTypes().get(uid);
    }

    /**
     * Lookup data type by name
     *
     * @param name Data Type name
     * @return Data type with specified name (NULL if it could not be found)
     */
    static public DataTypeBase findType(String name) {
        boolean nulltype = name.equals("NULL");
        if (nulltype) {
            return getNullType();
        }

        for (int i = 0; i < getTypes().size(); i++) {
            DataTypeBase dt = getTypes().get(i);
            boolean eq = name.equals(dt.getName());
            if (eq) {
                return dt;
            }
        }

        if (name.contains(".")) {
            return findType(removeNamespaces(name));
        }

        return null;
    }

    /**
     * @return Instance of Datatype T casted to void*
     */
    public Object createInstance() {
        if (info == null) {
            return null;
        }
        return info.createInstance();
    }

    /**
     * @return Instance of Datatype as Generic object
     */
    GenericObject createInstanceGeneric() {
        if (info == null) {
            return null;
        }
        return info.createInstanceGeneric();
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
     * @return Nulltype
     */
    public static DataTypeBase getNullType() {
        return NULL_TYPE;
    }

    /**
     * @return DataTypeInfo object
     */
    public DataTypeInfoRaw getInfo() {
        return info;
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
        if (info == null || dataType.info == null) {
            return false;
        }
        if (getType() == Type.UNKNOWN || dataType.getType() == Type.UNKNOWN) {
            return false;
        }
        if (getType() == Type.LIST && dataType.getType() == Type.LIST) {
            return getElementType().isConvertibleTo(dataType.getElementType());
        }
        if ((info.javaClass != null) && (dataType.info.javaClass != null)) {
            return dataType.getInfo().javaClass.isAssignableFrom(info.javaClass);
        }
        return false;
    }

    /**
     * Add annotation to this data type
     *
     * @param ann Annotation
     */
    public <T extends DataTypeAnnotation> void addAnnotation(T ann) {
        if (info != null) {

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
            assert(info.annotations[annIndex] == null);

            info.annotations[annIndex] = ann;
        } else {
            throw new RuntimeException("Nullptr");
        }
    }

    /**
     * Get annotation of specified class
     *
     * @param c Class of annotation we're looking for
     * @return Annotation. Null if data type has no annotation of this type.
     */
    @SuppressWarnings("unchecked")
    public <T extends DataTypeAnnotation> T getAnnotation(Class<T> c) {
        if (info != null) {
            return (T)info.annotations[annotationIndexLookup.get(c)];
        } else {
            throw new RuntimeException("Nullptr");
        }
    }

    public String toString() {
        return getName();
    }

    /**
     * \return If this is as enum type, returns enum constant names - otherwise NULL
     */
    public Object[] getEnumConstants() {
        if (info != null) {
            return info.enumConstants;
        }
        return null;
    }
}
