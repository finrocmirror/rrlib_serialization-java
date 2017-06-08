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


/**
 * @author Max Reichardt
 *
 * Utility class to write updates on auto-published registers to a stream - and read them on receiver side.
 *
 * When serialized to a stream, writes updates on desired register and all registers to be updated on change.
 * After deserialization, the reader's data on these remote registers is up to date.
 */
public class RegisterUpdate implements BinarySerializable {

    public RegisterUpdate() {
        this(0);
    }

    /**
     * @param registerUid UID of register to update (registers to updated on_change are also updated)
     */
    public RegisterUpdate(int registerUid) {
        this.registerUid = registerUid;
    }

    /**
     * @return UID of register to update (registers to updated on_change are also updated)
     */
    public int getRegisterUid() {
        return registerUid;
    }

    @Override
    public void serialize(BinaryOutputStream stream) {
        if (!stream.writeRegisterUpdates(registerUid, Integer.MAX_VALUE, 0)) {
            stream.writeByte(-1);
        }
    }

    @Override
    public void deserialize(BinaryInputStream stream) throws Exception {
        stream.readRegisterUpdates();
    }

    /** UID of register to update (registers to updated on_change are also updated) */
    private int registerUid;
}
