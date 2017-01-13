/**
 * 
 */
package org.sb.libevl;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author sameetb
 * @since 201612
 */
public class Commands 
{
    public enum PanicType {FIRE, AMBULANCE, POLICE};
    
    private static String parIdStr(int parId)
    {
        return String.valueOf(parId).substring(0, 1);
    }
    
    private static String codeStr(String code)
    {
        if(!code.matches("\\d{4,6}")) throw new IllegalArgumentException("Code does not match requirement of 4-6 digits");
        return code;
    }
    
    public Packet poll()
    {
        return new Packet(0);
    }
    
    public Packet statusReport()
    {
        return new Packet(1);
    }
    
    public Packet dumpZoneTimers()
    {
        return new Packet(8);
    }
    
    public Packet setDateTime(Date date)
    {
        return new Packet(10, new SimpleDateFormat("HHmmssMMDDyy").format(date));
    }
    
    public PacketFlow activateCmdOutput(int partitionId, int outputId, Supplier<String> code)
    {
        return makeCodePacketFlow(20, parIdStr(partitionId) + outputId, code);
    }

    public PacketFlow awayArm(int partitionId, Supplier<String> code)
    {
        return makeCodePacketFlow(30, parIdStr(partitionId), code);
    }
    
    public PacketFlow stayArm(int partitionId, Supplier<String> code)
    {
        return makeCodePacketFlow(31, parIdStr(partitionId), code);
    }

    public PacketFlow awayArmWithoutEntryDelay(int partitionId, Supplier<String> code)
    {
        return makeCodePacketFlow(32, parIdStr(partitionId), code);
    }
    
    private static boolean needsCode(int cmdCode)
    {
        return Arrays.stream(new int[]{900, 912, 921, 922}).anyMatch(code -> code == cmdCode);
    }
    
    private static PacketFlow makeCodePacketFlow(int cmdCode, String data, Supplier<String> code)
    {
        return new PacketFlow(new Packet(cmdCode, data),
                            pkt -> needsCode(pkt.getCmdCode()), () -> new Packet(200, codeStr(code.get()))); 
    }
    
    public Packet arm(int partitionId, String code)
    {
        return new Packet(33, parIdStr(partitionId) + codeStr(code)); 
    }

    public Packet disarm(int partitionId, String code)
    {
        return new Packet(40, parIdStr(partitionId) + codeStr(code)); 
    }
    
    public Packet broadcastSystemTime(boolean enable)
    {
        return new Packet(56, enable ? "1" : "0");
    }

    public Packet broadcastTemperature(boolean enable)
    {
        return new Packet(57, enable ? "1" : "0");
    }
    
    public Packet triggerPanicAlarm(PanicType at)
    {
        return new Packet(60, String.valueOf(at.ordinal() + 1));
    }

    public Packet sendKey(char ch)
    {
        return new Packet(70, String.valueOf(ch));
    }

    public Packet sendKeys(int partitionId, String keys)
    {
        return new Packet(71, parIdStr(partitionId) + keys.substring(0, Math.max(keys.length(), 6)));
    }

    private PacketFlow enterAccessCodeProgramming(int partitionId, Supplier<String> masterCode)
    {
        return makeCodePacketFlow(72, parIdStr(partitionId), masterCode);
    }

    private PacketFlow enterUserFunctionProgramming(int partitionId, Supplier<String> masterCode)
    {
        return makeCodePacketFlow(73, parIdStr(partitionId), masterCode);
    }

    public Packet sendKeepAlive(int partitionId)
    {
        return new Packet(74, parIdStr(partitionId));
    }

    public Packet requestHvacBroadcast()
    {
        return new Packet(80);
    }
    
    public Stream<PacketFlow> setUserAccessCode(int partitionId, Supplier<String> masterCode, Integer userId, String userCode)
    {
        if(userId < 1 || userId > 32) throw new IllegalArgumentException("UserId must be in the range [01-32]");
        return setAccessCode(partitionId, masterCode, userId, userCode);
    }

    public Stream<PacketFlow> setMasterCode(int partitionId, Supplier<String> masterCode, String newMasterCode)
    {
        return setAccessCode(partitionId, masterCode, 40, newMasterCode);
    }

    public Stream<PacketFlow> setDuressCode(int partitionId, Supplier<String> masterCode, Integer duressId, String duressCode)
    {
        if(duressId < 0 || duressId > 1) throw new IllegalArgumentException("DuressId must be in the range [0-1]");
        return setAccessCode(partitionId, masterCode, 33 + duressId, duressCode);
    }
    
    public Stream<PacketFlow> setPartitionMasterCode(int partitionId, Supplier<String> masterCode, 
                                                                            Integer partMasterId, String partMasterCode)
    {
        if(partMasterId < 0 || partMasterId > 1) throw new IllegalArgumentException("PartMasterId must be in the range [0-1]");
        return setAccessCode(partitionId, masterCode, 41 + partMasterId, partMasterCode);
    }
    
    private Stream<PacketFlow> setAccessCode(int partitionId, Supplier<String> masterCode, Integer id, String code)
    {
        return Stream.of(
            enterAccessCodeProgramming(partitionId, masterCode), 
            new PacketFlow(sendKeys(partitionId, String.format("%02d", id).substring(0, 2))),
            new PacketFlow(sendKeys(partitionId, codeStr(code))),
            exitProgramming(partitionId));
    }

    public Stream<PacketFlow> removeUserAccess(int partitionId, Supplier<String> masterCode, Integer userId)
    {
        if(userId < 1 || userId > 32) throw new IllegalArgumentException("UserId must be in the range [01-32]");
        return Stream.of(
            enterAccessCodeProgramming(partitionId, masterCode), 
            new PacketFlow(sendKeys(partitionId, String.format("%02d", userId).substring(0, 2) + "*")),
            exitProgramming(partitionId));
    }
    
    private PacketFlow exitProgramming(int partitionId)
    {
        return new PacketFlow(sendKeys(partitionId, "#"));
    }
}
