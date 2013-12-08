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

import org.rrlib.serialization.rtti.Copyable;
import org.rrlib.serialization.rtti.GenericChangeable;

/**
 * @author Max Reichardt
 *
 * List of type T that can be used in ports - or in a blackboard
 *
 * For reasons of complexity, objects in PortDataList must not be used/stored outside of PortDataList in Java.
 * They have to be copied, before lock on list is released.
 */
public interface PortDataList<T> extends BinarySerializable, Copyable<PortDataList<T>>, GenericChangeable<PortDataList<T>> {

    /**
     * @return Size of list
     */
    public int size();

    /**
     * @param index Index of element
     * @return Element at index
     */
    public T get(int index);

    /**
     * @param newSize New size of list
     */
    public void resize(int newSize);
}
