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

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.serialization.BinaryInputStream;
import org.rrlib.serialization.BinaryOutputStream;
import org.rrlib.serialization.PublishedRegisters;
import org.rrlib.serialization.Register;
import org.rrlib.serialization.PublishedRegisters.RemoteEntryBase;


/**
 * @author Max Reichardt
 *
 * Untyped base class for all data types.
 *
 * Assigns unique type-id to each data type.
 * Can be used as factory for data types (necessary for deserializing
 * vectors containing pointers).
 *
 * A instance of DataType<T> must be created for each type T
 * this mechanism should work with.
 *
 * To avoid that the list of types is modified while accessing it,
 * synchronize on 'DataTypeBase.class'.
 */
public class DataTypeBase {

    /** Type classification */
    public enum Classification {
        RPC_TYPE,
        ARRAY,
        LIST,
        PAIR,
        TUPLE,
        INTEGRAL,
        OTHER_DATA_TYPE
    }

    /** Relevant type traits across runtime environments (equals C++ traits) */
    public static final int
    IS_BINARY_SERIALIZABLE = 1 << 8,
    IS_STRING_SERIALIZABLE = 1 << 9,
    IS_XML_SERIALIZABLE = 1 << 10,
    IS_ENUM = 1 << 11,  // not part of Classification enum for legacy compatibility

    // Classification bits (enum)
    CLASSIFICATION_BITS = (1 << 12) | (1 << 13) | (1 << 14) | (1 << 15),
    CLASSIFICATION_ARRAY = 0 << 12,
    CLASSIFICATION_LIST = 1 << 12,
    CLASSIFICATION_PAIR = 2 << 12,
    CLASSIFICATION_TUPLE = 3 << 12,
    CLASSIFICATION_ENUM_BASED_FLAGS = 4 << 12,
    CLASSIFICATION_AUTO_NAMED = CLASSIFICATION_ENUM_BASED_FLAGS,
    CLASSIFICATION_INTEGRAL = 12 << 12,
    CLASSIFICATION_OTHER_DATA_TYPE = 13 << 12,
    CLASSIFICATION_NULL_TYPE = 14 << 12,
    CLASSIFICATION_RPC_TYPE = 15 << 12,

    // The traits below are only set in C++
    HAS_UNDERLYING_TYPE = 1 << 16,
    IS_CAST_TO_UNDERLYING_TYPE_IMPLICIT = 1 << 17,
    IS_REINTERPRET_CAST_FROM_UNDERLYING_TYPE_VALID = 1 << 18,
    IS_CAST_FROM_UNDERLYING_TYPE_IMPLICIT = 1 << 19,
    IS_UNDERLYING_TYPE_BINARY_SERIALIZATION_DIFFERENT = 1 << 20,
    SUPPORTS_BITWISE_COPY = 1 << 21,
    HAS_TRIVIAL_DESTRUCTOR = 1 << 22;


    /** Register with all registered data types */
    private static Register<DataTypeBase> types = new Register<DataTypeBase>(32, 128, 2);

    /** Null type */
    public static DataTypeBase NULL_TYPE = new DataTypeBase("NULL");

    /** Maximum number of annotations */
    private static final int MAX_ANNOTATIONS = 10;

    /** we need to avoid reallocation in order to make ArrayList thread safe. Therefore it is created with this capacity. */
    private final static int MAX_TYPES = 2000;

    /** Name of data type */
    private final String name;

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

    /** Array with enum values - if this is a remote enum type with non-standard values */
    protected long[] enumValues;

    /** Lookup for data type annotation index */
    private static final HashMap < Class<?>, Integer > annotationIndexLookup = new HashMap < Class<?>, Integer > ();

    /** Last annotation index that was used */
    private static final AtomicInteger lastAnnotationIndex = new AtomicInteger(0);

    /** Type traits of this type (bit vector - see constants above) */
    protected int typeTraits;

    static {
        NULL_TYPE.typeTraits |= CLASSIFICATION_NULL_TYPE;
    }

    public DataTypeBase(String name) {
        synchronized (DataTypeBase.class) {
            uid = (short)types.size();
            this.name = name;
            for (int i = 0, n = types.size(); i < n; i++) {
                if (types.get(i).getName().equals(name)) {
                    Log.log(LogLevel.WARNING, "Two types with the same name were registered: " + name);
                }
            }
            if (types.size() >= MAX_TYPES) {
                Log.log(LogLevel.ERROR, this, "Maximum number of data types exceeded. Increase cMAX_TYPES.");
                throw new RuntimeException("Maximum number of data types exceeded. Increase MAX_TYPES.");
            }

            types.add(this);
            Log.log(LogLevel.DEBUG_VERBOSE_1, this, "Adding data type " + getName());
        }
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
     * @return Type classification
     */
    public int getTypeClassification() {
        return typeTraits & CLASSIFICATION_BITS;
    }

    /**
     * @return Bit vector of type traits
     */
    public int getTypeTraits() {
        return typeTraits;
    }

    /**
     * Lookup data type for Java class c.
     * Note that this lookup can be ambiguous (e.g. for remote enums).
     * Therefore, an expected type can be specified in order to resolve these cases.
     *
     * @param c Java class
     * @param expected Expected type
     * @return Data type object - null if there's none
     */
    static public DataTypeBase findType(Class<?> c, DataTypeBase expected) {
        if (c == null) {
            return null;
        }
        if (expected != null && c.equals(expected.javaClass)) {
            return expected;
        }
        for (int i = 0; i < types.size(); i++) {
            DataTypeBase type = types.get(i);
            if (type.javaClass == c) {
                return type;
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
     *
     * @param dataType Other type
     * @return Answer
     */
    public boolean isConvertibleTo(DataTypeBase dataType) {

        if (dataType == this) {
            return true;
        }
        if (getTypeClassification() == CLASSIFICATION_LIST) {
            return getElementType() != null && dataType.getElementType() != null && getElementType().isConvertibleTo(dataType.getElementType());
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
        synchronized (DataTypeBase.class) {
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
     * @return If this is as enum type, returns enum constant names - otherwise NULL
     */
    public Object[] getEnumConstants() {
        return enumConstants;
    }

    /**
     * @return Array with enum values - if this is a remote enum type with non-standard values
     */
    public long[] getNonStandardEnumValues() {
        return enumValues;
    }

    /**
     * @param stream Stream to serialize this type to
     */
    public void serialize(BinaryOutputStream stream) {
        if (types.writeEntry(stream, getUid())) {
            stream.writeString(getName());
        }
    }

    /**
     * @param stream Stream to deserialize (local) type from
     * @return Deserialized type
     */
    public static DataTypeBase deserialize(BinaryInputStream stream) throws Exception {
        DataTypeBase result = types.readEntry(stream);
        if (result == null) {
            return findType(stream.readString());
        }
        return result;
    }

    /**
     * Registers type register for use in auto-publishing mechanism.
     *
     * @param uid Uid to assign to register (must be <= MAX_PUBLISHED_REGISTERS)
     * @param remoteEntryClass Type to deserialize in remote runtime environment. It needs to be derived from PublishedRegisters.RemoteEntryBase and be default-constructible.
     * @throw Throws exception on already occupied uid
     */
    public static void registerForPublishing(int uid, Class<? extends RemoteEntryBase<?>> remoteEntryClass) throws Exception {
        PublishedRegisters.register(types, uid, remoteEntryClass);
    }
}
