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


/**
 * @author Max Reichardt
 *
 * Management information for generic object.
 * May be subclassed to store more custom info such as reference counting.
 *
 * GenericObjectManagers are always written into memory allocated
 * by GenericObject subclass.
 * Therefore, their destructor should never be called. Instead, the
 * GenericObject should be deallocated.
 */
public interface GenericObjectManager {

    /**
     * @return Generic object that this class manages
     */
    public GenericObject getObject();

    /**
     * @param managedObject Set object to be managed
     */
    public void setObject(GenericObject managedObject);
}

