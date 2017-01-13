/**
 * 
 */
package org.sb.libevl;

import java.io.InvalidObjectException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author sameetb
 * @since 201612
 */
public class Packet
{
    private final int cmdCode;
    private final String data;
    
    
    public Packet(int command)
    {
        this(command, "");
    }
    
    public Packet(int command, String data)
    {
        this.cmdCode = command;
        this.data = data;
    }

    private String cmdStr()
    {
        return String.format("%03d", cmdCode);
    }
    
    private String checksum()
    {
        return String.format("%02X", Stream.of(cmdStr(), data)
                                           .flatMap(str -> str.chars().mapToObj(i -> (int)i))
                                           .collect(Collectors.summingInt(i -> i)) & 0xFF);
    }

    public String serialize()
    {
        return Stream.of(cmdStr(), data, checksum())
                     .collect(Collectors.joining());
    }

    public static Packet deserialize(String pkt) throws InvalidObjectException
    {
        final int len = pkt.length();
        String cmd = pkt.substring(0, 3);
        String data = pkt.substring(3, len - 2);
        String chksm = pkt.substring(len - 2);
        final Packet packet = new Packet(Integer.parseInt(cmd), data);
        final String checksum = packet.checksum();
        if(!checksum.equals(chksm)) throw new InvalidObjectException("Checksum match failed, "
                + "expected=" + checksum + ", found=" + chksm);
        return packet;
    }
    
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + cmdCode;
        result = prime * result + ((data == null) ? 0 : data.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Packet other = (Packet) obj;
        if (cmdCode != other.cmdCode)
            return false;
        if (data == null)
        {
            if (other.data != null)
                return false;
        }
        else if (!data.equals(other.data))
            return false;
        return true;
    }

    public int getCmdCode()
    {
        return cmdCode;
    }

    public String getData()
    {
        return data;
    }

    @Override
    public String toString()
    {
        return "Packet [cmdCode=" + cmdCode + ", data=" + data + "]";
    }
}
