/**
 * 
 */
package org.sb.libevl;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author sameetb
 * @since 201612
 */
public class PacketFlow
{
    private final Supplier<Packet> pkt;
    private final Optional<Map.Entry<Predicate<Packet>, PacketFlow>> replyAction;
    
    public PacketFlow(Packet pkt, Predicate<Packet> pred, Supplier<Packet> respPkt)
    {
        this(() -> pkt, pred, new PacketFlow(respPkt, Optional.empty()));
    }
    
    public PacketFlow(Supplier<Packet> pkt, Predicate<Packet> pred, PacketFlow replyAction)
    {
        this(pkt, Optional.of(new SimpleEntry<>(pred, replyAction)));
    }
    
    public PacketFlow(Supplier<Packet> pkt, Optional<Entry<Predicate<Packet>, PacketFlow>> replyAction)
    {
        this.pkt = pkt;
        this.replyAction = replyAction;
    }
    
    public PacketFlow(Packet pkt)
    {
        this(() -> pkt, Optional.empty());
    }

    public Packet getPkt()
    {
        return pkt.get();
    }

    public Optional<Map.Entry<Predicate<Packet>, PacketFlow>> getReplyAction()
    {
        return replyAction;
    }
    
    public PacketFlow append(Predicate<Packet> pred, PacketFlow replyAction)
    {
        PacketFlow curr = this;
        while(curr.getReplyAction().isPresent()) curr = curr.getReplyAction().get().getValue();
        
        return new PacketFlow(curr.pkt, Optional.of(new SimpleEntry<>(pred, replyAction)));
    }
}
