/**
 * 
 */
package org.sb.libevl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author sameetb
 * @since 201612
 */
public class DscPanel
{
    private static final Logger log = LoggerFactory.getLogger(DscPanel.class);

    enum LedState {OFF, ON, FLASH, UNKNOWN};
    
    private Date dateTime;
    
    static class Led
    {
        final String name;
        private LedState state = LedState.UNKNOWN;
        
        public Led(String name)
        {
            this.name = name;
        }

        public String toJson()
        {
            return JsonHelper.json(name, state);
        }
    }
    
    private final Map<String, AlarmState> alarms = new ConcurrentHashMap<>();
            
    private final Led[] keyPad = new Led[]  {
                                                new Led("READY"),
                                                new Led("ARMED"),
                                                new Led("MEMORY"),
                                                new Led("BYPASS"),
                                                new Led("TROUBLE"),
                                                new Led("PROGRAM"),
                                                new Led("FIRE"),
                                                new Led("BACKLIGHT")
                                            };
    enum AlarmState {NORMAL, ALARM, UNKNOWN};
    
    enum ZoneState {CLOSED, OPEN, UNKNOWN};
    
    enum FaultState {NORMAL, FAULT, UNKNOWN};
    
    enum TamperState {NORMAL, TAMPER, UNKNOWN};
    
    static class Zone
    {
        final int zid;
        ZoneState general = ZoneState.UNKNOWN;
        AlarmState alarm = AlarmState.UNKNOWN;
        int partition;
        public TamperState tamper = TamperState.UNKNOWN;
        public FaultState fault = FaultState.UNKNOWN;
        
        public Zone(int zid)
        {
            this.zid = zid;
        }

        boolean hasProblem()
        {
            return fault == FaultState.FAULT 
                || tamper == TamperState.TAMPER
                || alarm == AlarmState.ALARM;
        }
        
        @Override
        public String toString()
        {
            return "Zone [zid=" + zid + ", general=" + general + ", alarm=" + alarm + ", partition=" + partition
                    + ", tamper=" + tamper + ", fault=" + fault + "]";
        }

        public String toJson()
        {
            return JsonHelper.obj(
                    JsonHelper.json("id", zid), 
                    JsonHelper.json("state", general), 
                    JsonHelper.json("alarm", alarm ),
                    JsonHelper.json("tamper", tamper), 
                    JsonHelper.json("fault", fault ));
        }
    }
    
    enum PartitionState {ALARM, READY, NOTREADY, BUSY, ARMED, DISARMED, UNKNOWN}
    
    enum PartitionArmState {AWAYARMED, STAYARMED, ZERO_ENTRY_AWAY, ZERO_ENTRY_STAY, NONE};
    
    enum PartitionDelay {NONE, EXITDELAY, ENTRYDELAY}
    
    static class Partition
    {
        final int pid;
        PartitionState state = PartitionState.UNKNOWN;
        PartitionArmState arm = PartitionArmState.NONE;
        PartitionDelay delay = PartitionDelay.NONE;
        boolean keypadLockout = false;
        final EvictingQueue<Entry<Date, String>> events = new EvictingQueue<>(100);
        boolean installerMode = false;
        boolean trouble = false;
        public Partition(int pid)
        {
            this.pid = pid;
        }

        @Override
        public String toString()
        {
            return "Partition [pid=" + pid + ", state=" + state + ", arm=" + arm + ", delay=" + delay
                    + ", keypadLockout=" + keypadLockout + ", installerMode=" + installerMode + ", trouble=" + trouble
                    + "]";
        }

        public String toJson()
        {
            return JsonHelper.obj(
                    JsonHelper.json("id", pid), 
                    JsonHelper.json("state", state), 
                    JsonHelper.json("arm", arm), 
                    JsonHelper.json("delay", delay),
                    JsonHelper.json("keypadLockout", keypadLockout), 
                    JsonHelper.json("installerMode", installerMode), 
                    JsonHelper.json("trouble", trouble));
        }
    }
    
    private final ConcurrentHashMap<Integer, Zone> zones = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Partition> partitions = new ConcurrentHashMap<>();
    
    enum TroubleState {NORMAL, TROUBLE, UNKNOWN};
    private final ConcurrentHashMap<String, TroubleState> troubles = new ConcurrentHashMap<>();
    
    private Map<Integer, Consumer<String>> cmdHandlers = Collections.unmodifiableMap(
                  new HashMap<Integer, Consumer<String>>() {{
                        put(510, data -> updateLedState(data, false));
                        put(511, data -> updateLedState(data, true));
                        
                        put(550, DscPanel.this::updateDateTime);
                        
                        put(561, DscPanel.this::updateTemperature);
                        put(562, DscPanel.this::updateTemperature);
                        
                        put(601, data -> updateZoneA(data, Optional.of(AlarmState.ALARM), Optional.empty()));
                        put(602, data -> updateZoneA(data, Optional.of(AlarmState.NORMAL), Optional.empty()));
                        put(603, data -> updateZoneA(data, Optional.empty(), Optional.of(TamperState.TAMPER)));
                        put(604, data -> updateZoneA(data, Optional.empty(), Optional.of(TamperState.NORMAL)));
                        put(605, data -> updateZoneB(data, Optional.empty(), Optional.of(FaultState.FAULT)));
                        put(606, data -> updateZoneB(data, Optional.empty(), Optional.of(FaultState.NORMAL)));
                        put(609, data -> updateZoneB(data, Optional.of(ZoneState.OPEN), Optional.empty()));
                        put(610, data -> updateZoneB(data, Optional.of(ZoneState.CLOSED), Optional.empty()));
                        
                        put(620, data -> updateAlarm("Duress", false));
                        put(621, data -> updateAlarm("Fire", false));
                        put(622, data -> updateAlarm("Fire", true));
                        put(623, data -> updateAlarm("Auxillary", false));
                        put(624, data -> updateAlarm("Auxillary", true));
                        put(625, data -> updateAlarm("Panic", false));
                        put(626, data -> updateAlarm("Panic", true));
                        put(631, data -> updateAlarm("Smoke", false));
                        put(632, data -> updateAlarm("Smoke", true));
                        
                        put(650, data -> updatePartition(data, PartitionState.READY));
                        put(651, data -> updatePartition(data, PartitionState.NOTREADY));
                        put(652, data -> updatePartition(data, PartitionState.ARMED));
                        put(653, data -> updatePartition(data, PartitionState.READY));
                        put(654, data -> updatePartition(data, PartitionState.ALARM));
                        put(655, data -> updatePartition(data, PartitionState.DISARMED));

                        put(656, data -> updatePartition(data, PartitionDelay.EXITDELAY));
                        put(657, data -> updatePartition(data, PartitionDelay.ENTRYDELAY));
                        
                        put(658, DscPanel.this::keypadLockout);
                        
                        put(659, data -> updateEvent(data, "659 - Partition Failed to Arm. "
                                + "An attempt to arm the partition has failed."));
                        put(660, data -> updateEvent(data, "660 - PGM Output is in Progress."));

                        put(663, data -> updateEvent(data, "663 - Chime Enabled. "
                                + "The door chime feature has been enabled."));
                        put(664, data -> updateEvent(data, "664 - Chime Disabled. "
                                + "The door chime feature has been disabled."));

                        /*put(670, data -> updateEvent(data, "670 - Invalid Access Code. "
                                + "An access code that was entered was invalid."));*/
                        put(671, data -> updateEvent(data, "671 - Function Not Available. "
                                + "A function that was selected is not available."));
                        put(672, data -> updateEvent(data, "672 - Failure to Arm. "
                                + "An attempt was made to arm the partition and it failed."));
                        
                        put(673, data -> updatePartition(data, PartitionState.BUSY));
                        put(674, data -> updateEvent(data, "674 - System Arming in Progress. "
                                + "This system is auto-arming and is in arm warning delay."));

                        put(680, DscPanel.this::installerMode);
                        
                        put(700, data -> updateEvent(data, "700 - User Closing. "
                                + "A partition has been armed by a user '" + data.substring(1, 5)
                                + "' – sent at the end of exit delay."));
                        put(701, data -> updateEvent(data, "701 - Special Closing. "
                                + "A partition has been armed by one of the following methods: "
                                + "Quick Arm, Auto Arm, Keyswitch, DLS software, Wireless Key."));
                        put(702, data -> updateEvent(data, "702 - Partial Closing. "
                                + "A partition has been armed but one or more zones have been bypassed."));
                        
                        put(750, data -> updateEvent(data, "750 - User Opening."
                                + "A partition has been disarmed by a user " + data.substring(1, 5)));
                        put(751, data -> updateEvent(data, "751 - Special Opening. "
                                + "A partition has been disarmed by one of the following methods: "
                                + "Keyswitch, DLS software, Wireless Key"));

                        put(800, data -> updateTrouble("Panel Battery", false));
                        put(801, data -> updateTrouble("Panel Battery", true));
                        put(802, data -> updateTrouble("Panel AC", false));
                        put(803, data -> updateTrouble("Panel AC", true));
                        put(806, data -> updateTrouble("System Bell", false));
                        put(807, data -> updateTrouble("System Bell", true));
                        put(808, data -> updateTrouble("FTC", false));
                  
                        put(816, data -> updateTrouble("Buffer Near Full", false));
                        
                        put(829, data -> updateTrouble("General System Tamper", false));
                        put(830, data -> updateTrouble("General System Tamper", true));
                        
                        put(840, data -> troubleLed(data, true));
                        put(841, data -> troubleLed(data, false));
                        
                        put(842, data -> updateTrouble("Fire", false));
                        put(843, data -> updateTrouble("Fire", true));
                  }});

    private final ExecutorService asyncUpdater = Executors.newSingleThreadExecutor();
    
    public final Function<Packet, Boolean> stateHandler = 
            pkt -> Optional.ofNullable(cmdHandlers.get(pkt.getCmdCode()))
                           .map(con -> 
                               {
                                   asyncUpdater.submit(() -> {
                                        try
                                        {
                                            con.accept(pkt.getData());
                                        }
                                        catch(Exception e)
                                        {
                                            log("Failed to process packet " + pkt, e);
                                        }
                                   }); 
                                   return true;
                               })
                           .orElse(false); 
    
    private final Optional<Consumer<Notification>> notifier;
    
    public DscPanel()
    {
        notifier = Optional.empty();
    }
    
    public DscPanel(Consumer<Notification> notifier)
    {
        this.notifier = Optional.ofNullable(notifier);
    }
    
    private void updateLedState(String data, boolean flash)
    {
        int state = Integer.parseInt(data.substring(0, 2), 16);
        int bm = 0x80;
        for(int i = 7; i >= 0; i--)
        {
            keyPad[i].state = ((state & bm) == 0) ? LedState.OFF : (flash ? LedState.FLASH : LedState.ON);
            bm >>= 1;
        }
        sendNotification(Notification.Type.LED , "leds updated");
    }
    
    private void updateDateTime(String data)
    {
        try
        {
            dateTime = new SimpleDateFormat("HHmmssMMDDyy").parse(data);
        }
        catch (ParseException e)
        {
            log("Failed to parse date " + data, e);
        }
    }
    
    private void updateTemperature(String data)
    {
        log("Ignoring temperature reading " + data);
    }
    
    private Zone getOrCreateZone(int zoneId)
    {
        Zone z = zones.get(zoneId);
        if(z == null) zones.put(zoneId, z = new Zone(zoneId));
        return z;
    }
    
    private Partition getOrCreatePartition(int partId)
    {
        Partition z = partitions.get(partId);
        if(z == null) partitions.put(partId, z = new Partition(partId));
        return z;
    }

    private void updateZoneA(String data, Optional<AlarmState> aState, Optional<TamperState> tState)
    {
        int partition = Integer.parseInt(data.substring(0, 1));
        int zone = Integer.parseInt(data.substring(1, 4));
        log("Updating zone " + zone + ", partition=" + partition +  aState.map(a -> "alarm=" + a).orElse("") + tState.map(t -> ", tamper=" + t).orElse(""));
        Zone z = getOrCreateZone(zone); 
        aState.ifPresent(a -> {z.alarm = a;sendNotification(Notification.Type.ZONE , "Zone "  + zone + " alarm: " + a);});
        tState.ifPresent(t -> {z.tamper = t;sendNotification(Notification.Type.ZONE , "Zone "  + zone + " tamper: " + z);});
    }

    private void updateZoneB(String data, Optional<ZoneState> state, Optional<FaultState> fState)
    {
        int zone = Integer.parseInt(data.substring(0, 3));
        log("Updating zone " + zone + state.map(s -> ", state=" + s).orElse("") + fState.map(f -> ", fault=" + f).orElse(""));
        Zone z = getOrCreateZone(zone); 
        state.ifPresent(a -> {z.general = a;sendNotification(Notification.Type.ZONE , "Zone "  + zone + " state: " + a);});
        fState.ifPresent(t -> {z.fault = t;sendNotification(Notification.Type.ZONE , "Zone "  + zone + " fault: " + z);});
    }
    
    private void updateAlarm(String name, boolean restore)
    {
        log("Updating alarm " + name + "restore=" + false);
        alarms.put(name, restore ? AlarmState.NORMAL : AlarmState.ALARM);
        sendNotification(Notification.Type.ALARM , name);
    }
    
    private void updatePartition(String data, PartitionState state)
    {
        Partition p = parsePid(data);
        log("Updating partition=" + p.pid +  ", state=" + state);
        p.state = state;
        
        if(state == PartitionState.ARMED)
        { //set the armed mode
            int mode = Integer.parseInt(data.substring(1, 2));
            log("Updating partition=" + p.pid +  ", arm mode =" + mode);
            final PartitionArmState[] values = PartitionArmState.values();
            if(mode < values.length) p.arm = values[mode];
            else log("Ignoring unknown arm mode = " + mode);
            p.delay = PartitionDelay.NONE;
        }
        
        sendNotification(state != PartitionState.ALARM ? Notification.Type.ARM : Notification.Type.ALARM , state.name());
    }

    private Partition parsePid(String data)
    {
        int partition = Integer.parseInt(data.substring(0, 1));
        return getOrCreatePartition(partition);
    }
    
    private void updatePartition(String data, PartitionDelay state)
    {
        Partition p = parsePid(data);
        log("Updating partition=" + p.pid +  ", delay=" + state);
        sendNotification(Notification.Type.ARM, state.name());
        p.delay = state;
    }
    
    private void keypadLockout(String data)
    {
        Partition p = parsePid(data);
        log("Updating partition=" + p.pid +  ", keypad locked out");
        p.keypadLockout = true;
        sendNotification(Notification.Type.MISC , "keypad locked out");
    }

    private void installerMode(String data)
    {
        Partition p = parsePid(data);
        log("Updating partition=" + p.pid +  ", installer mode");
        p.installerMode = true;
        sendNotification(Notification.Type.MISC , "installer mode");
    }
    
    private void updateEvent(String data, String eventMsg)
    {
        Partition p = parsePid(data);
        log("Updating partition=" + p.pid +  ", event=" + eventMsg);
        p.events.add(new SimpleEntry<>(new Date(),  eventMsg));
        sendNotification(Notification.Type.MISC , eventMsg);
    }
    
    private void updateTrouble(String name, boolean restore)
    {
        log("Updating trouble " + name + "restore=" + false);
        troubles.put(name, restore ? TroubleState.NORMAL : TroubleState.TROUBLE);
        sendNotification(Notification.Type.TROUBLE , name);
    }
    
    private void troubleLed(String data, boolean on)
    {
        Partition p = parsePid(data);
        log("Updating partition=" + p.pid +  ", troubleLed=" + on);
        p.trouble = on;
        sendNotification(Notification.Type.MISC , "Partition " + p.pid + " is in trouble");
    }
    
    private void log(String string)
    {
        log.info(string);
    }

    private void log(String string, Exception e)
    {
        log.error(string, e);
    }

    /**
     * @return the dateTime
     */
    public Date getDateTime()
    {
        return dateTime;
    }

    public String getKeypadLeds()
    {
        return Arrays.stream(keyPad).map(led -> led.toJson()).collect(Collectors.joining(",\n", "{\n", "}\n"));
    }

    public Stream<String> alarms()
    {
        return alarms.entrySet().stream().filter(e -> e.getValue() == AlarmState.ALARM).map(e -> e.getKey());
    }

    public Stream<String> troubles()
    {
        return troubles.entrySet().stream().filter(e -> e.getValue() == TroubleState.TROUBLE).map(e -> e.getKey());
    }
    
    public Stream<Integer> problemZones()
    {
        return zones.values().stream().filter(z -> z.hasProblem()).map(z -> z.zid);
    }
    
    public Stream<Integer> openZones()
    {
        return zones.values().stream().filter(z -> z.general == ZoneState.OPEN).map(z -> z.zid);
    }
    
    public Optional<String> getZone(int zoneId)
    {
        return Optional.ofNullable(zones.get(zoneId)).map(z -> z.toJson());
    }
    
    public Optional<String> getPartition(int partId)
    {
        return Optional.ofNullable(partitions.get(partId)).map(z -> z.toJson());
    }

    public Stream<String> zones()
    {
        return zones.values().stream().map(z -> z.toJson());
    }
    
    public Stream<String> partitions()
    {
        return partitions.values().stream().map(z -> z.toJson());
    }
    
    public void close()
    {
        asyncUpdater.shutdown();
        try
        {
            asyncUpdater.awaitTermination(100, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
        }
    }
    
    private void sendNotification(Notification.Type type, String msg)
    {
        notifier.ifPresent(n -> asyncUpdater.submit(() -> n.accept(new Notification(type, new Date(), msg))));
    }   
}
