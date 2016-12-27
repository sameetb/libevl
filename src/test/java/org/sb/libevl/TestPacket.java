package org.sb.libevl;

import static org.junit.Assert.*;

import java.io.InvalidObjectException;

import org.junit.Test;

public class TestPacket
{

    private static final Packet PACKET = new Packet(654, "3");
    private static final Packet PACKET1 = new Packet(654, "3456789123456789");
    private static final Packet PACKET2 = new Packet(005, "jkfdhgfdhgjf");

    @Test
    public void test1()
    {
        System.out.println(PACKET.serialize());
        System.out.println(PACKET1.serialize());
        System.out.println(PACKET2.serialize());
    }

    @Test
    public void test2() throws InvalidObjectException
    {
        assertEquals(Packet.deserialize(PACKET.serialize()), PACKET);
        assertEquals(Packet.deserialize(PACKET1.serialize()), PACKET1);
        assertEquals(Packet.deserialize(PACKET2.serialize()), PACKET2);
    }
    
}
