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

import org.rrlib.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Classes that implement this interface can be serialized to and deserialized from XML
 */
public interface XMLSerializable {

    /**
     * Serialize object to XML node
     *
     * @param node XML node (node name shouldn't be changed, attributes "name" and "type" neither)
     */
    public void serialize(XMLNode node) throws Exception;

    /**
     * Deserialize from XML node
     *
     * @param node XML node to deserialize from
     */
    public void deserialize(XMLNode node) throws Exception;
}
