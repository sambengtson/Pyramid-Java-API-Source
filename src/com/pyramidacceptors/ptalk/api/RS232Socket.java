/*
 * Copyright (C) 2014 Pyramid Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.pyramidacceptors.ptalk.api;

import com.pyramidacceptors.ptalk.api.event.PTalkEvent;
import com.pyramidacceptors.ptalk.api.interfaces.CustomEscrowEvents;
import org.apache.commons.collections4.queue.CircularFifoQueue;

/**
 * A socket acts as the transport layer between a master and slave devce.<br>
 * This transport is responsible for tracking packets and calling the <br>
 * appropriate routines the protocol requires.
 *
 * <a href="http://www.pyramidacceptors.com/files/RS_232.pdf">RS-232 Spec.</a>
 *
 * @author Cory Todd <cory@pyramidacceptors.com>
 * @since 1.0.0.0
 */
final class RS232Socket implements ISocket {

    enum CreditActions {

        ACCEPT, RETURN, NONE
    }

    private static final int MAX_PACKET_SIZE = 11;
    //# basic message           0      1     2      3       4      5      6      7
    //                         start, len,  ack,  bills, escrow, resv'd, end, checksum
    private final byte[] base = new byte[]{0x02, 0x08, 0x10, 0x7F, 0x10, 0x00, 0x03};
    // Can be dumped for debugging    
    private final CircularFifoQueue<IPacket> debugQ;

    private CreditActions creditAction = CreditActions.NONE;

    private CustomEscrowEvents escrowEvents = null;
    private APIConstants.BillNames lastBillName = null;

    /**
     * Generate a new RS-232 packet. By default, it is configured <br>
     * start with a standard polling message<br>
     */
    RS232Socket() {
        debugQ = new CircularFifoQueue<>();
    }

    public void addCustomEscrowEvent(CustomEscrowEvents e) {
        escrowEvents = e;
    }

    @Override
    public byte[] generateCommand() {
        RS232Packet packet = new RS232Packet(base);
        if (RS232Configuration.INSTANCE.getAck()) {
            packet.replace(2, (byte) 0x11);
        }

        // Set the accept, return bits or clear them out
        //If true, some time of escrow event has been fired
        if (creditAction == CreditActions.ACCEPT || creditAction == CreditActions.RETURN) {
            if (escrowEvents != null) {
                if (escrowEvents.AcceptBill(lastBillName)) {
                    packet.or(4, (byte) 0x20);
                } else {
                    packet.or(4, (byte) 0x40);
                }
            }
        } else {
            packet.replace(4, (byte) 0x10);
        }

//        switch (creditAction) {
//            case ACCEPT:
//                packet.or(4, (byte) 0x20);
//                break;
//            case RETURN:
//                packet.or(4, (byte) 0x40);
//                break;
//
//            case NONE:
//            default:
//                packet.replace(4, (byte) 0x10);
//                break;
//        }

        // Finally check if escrow mode is enabled
        if (!RS232Configuration.INSTANCE.getEnabled()) {
            packet.replace(4, (byte) 0);
        }

        // Checksum it
        packet.pack();
        return packet.toBytes();
    }

    @Override
    public PTalkEvent parseResponse(byte[] bytes) {
        IPacket packet = new RS232Packet().parseAsNew(bytes);
        creditAction = packet.getCreditAction();

        if (packet.getBillName() != null) {
            lastBillName = packet.getBillName();
        }

        PTalkEvent e = new PTalkEvent(this, packet.getBillName(),
                packet.getMessage(), packet.getInterrpretedEvents());
        debugQ.offer(packet);
        return e;
    }

    @Override
    public int getMaxPacketRespSize() {
        return MAX_PACKET_SIZE;
    }

}
