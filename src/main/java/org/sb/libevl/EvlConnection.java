/**
 * 
 */
package org.sb.libevl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.net.SocketException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author sameetb
 * @since 201612
 */
public class EvlConnection
{
    private static final Logger log = LoggerFactory.getLogger(EvlConnection.class);

    private static final int ACK_TIMEOUT_MS = 100;

    private static final int CONN_TIMEOUT_MS = 60000;
    
    private static final String EOT = "\r\n";
    
    private final Socket socket;
    
    private final BufferedReader sockIn;
    
    private final PrintWriter sockOut;
    
    private final Function<Packet, Boolean> stateHandler;
    
    private final ConcurrentHashMap<Predicate<Packet>, PacketFlow> replyActions;
    
    private final ExecutorService asyncSender;
    
    private final SimpleEntry<Integer, Packet> ack = new SimpleEntry<>(0, null);
    
    public EvlConnection(InetAddress ip, Optional<Integer> port, Supplier<String> creds,
           Function<Packet, Boolean> stateHandler) throws IOException, InterruptedException
    {
        socket = new Socket();
        log.info("Connecting to {}:{}", ip, port.orElse(4025));
        socket.connect(new InetSocketAddress(ip, port.orElse(4025)), CONN_TIMEOUT_MS);
        
        sockIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
        sockOut = new PrintWriter(socket.getOutputStream(), true);
        
        asyncSender = Executors.newSingleThreadExecutor();
        try
        {
            IOException io = 
            asyncSender.submit(() -> {
                try
                {
                    login(creds);
                    return null;
                }
                catch(IOException ex)
                {
                    return ex;
                }
            }).get(30, TimeUnit.SECONDS);
            if(io != null)
	    {
                 close();
		 throw io;
	    }
        }
        catch(TimeoutException to)
        {
            close();
            throw new IOException("Login interaction timed out", to);
        }
        catch (ExecutionException e)
        {
            close();
            Throwable th = e.getCause();
            if(th instanceof IOException) throw (IOException)th;
            throw new IOException(th);
        }
        
        replyActions = new ConcurrentHashMap<>();
        this.stateHandler = stateHandler;
        Thread th = new Thread(new MsgReader());
        th.setDaemon(true);
        th.start();
    }
    
    private void login(Supplier<String> creds) throws IOException
    {
        Packet pkt = receive();
        if(pkt.getCmdCode() == 505 && pkt.getData().equals("3"))
        {
            send(new Packet(005, Optional.ofNullable(creds.get()).orElse("")), false);
            pkt = receive();
	    if(pkt.getCmdCode() != 500) throw new IOException("Login command failed ack " + pkt);
            pkt = receive();
            if(pkt.getCmdCode() == 505)
            {
                switch(Integer.valueOf(pkt.getData()))
                {
                    case 0: throw new IOException("Password provided was incorrect, resp=" + pkt);
                    case 1: log("Password Correct, session established");
                            return;
                    case 2: throw new IOException("Time out. You did not send a password within 10 seconds, resp=" + pkt);
                    case 3: throw new IOException("Password not provided, resp=" + pkt);
                }
            }
        }
        throw new IOException("Login interaction failed, resp=" + pkt);
    }
    
    private void log(String string)
    {
        log.info(string);
    }

    private void log(String string, Exception e)
    {
        log.error(string, e);
    }

    public void send(Packet pkt) throws IOException
    {
        send(pkt, true);
    }
    
    private void send(Packet pkt, boolean checkAck) throws IOException
    {
        synchronized (sockOut)
        {
            log.trace("sending {}", pkt);
            ack.setValue(null);
	    String line = pkt.serialize();
            sockOut.print(line);
            sockOut.print(EOT);
	    sockOut.flush();
            log.trace("sent packet code:{}", pkt.getCmdCode());
            if(checkAck)checkAck(pkt);
        }
    }

    private void checkAck(Packet pkt) throws IOException
    {
        synchronized(ack)
        {
            try
            {
                ack.wait(ACK_TIMEOUT_MS);
                Packet ap = ack.getValue();
                if(ap != null) throw new IOException("Command packet " + pkt + ", received negative ack " + ap);
            }
            catch (InterruptedException e)
            {
                log.trace("ack check interrupted");
            }
        }
    }

    public void send(PacketFlow pktFlow) throws IOException
    {
        send(pktFlow.getPkt());
        pktFlow.getReplyAction().ifPresent(ra -> replyActions.put(ra.getKey(), ra.getValue()));
    }
    
    private Packet receive() throws IOException
    {
        final Packet pkt = Packet.deserialize(sockIn.readLine());
        log.trace("Received {}", pkt);
        return pkt;
    }
    
    private class MsgReader implements Runnable
    {
        public void run()
        {
            while(!socket.isInputShutdown())
            try
            {
                processPacket(receive());
            }
            catch (Exception e) 
            {
		        if((e instanceof SocketException || e instanceof IOException) && socket.isClosed())
		        {
			        log.info("socket closed.");
			        return;
		        }
                log.error("Exception during packet receive/processing", e);
           }
        }
    }
    
    public void close()
    {
        sockOut.close();
        try
        {
            sockIn.close();
        }
        catch (IOException e)
        {
            log.warn("", e);
        }
        try
        {
            socket.close();
        }
        catch (IOException e)
        {
            log.warn("", e);
        }
        asyncSender.shutdown();
        try
        {
            asyncSender.awaitTermination(100, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            log.warn("", e);
        }
    }

    public void processPacket(Packet pkt)
    {
        if(!commandAck((pkt))
            && !processReplyAction(pkt) 
            && !updateState(pkt))
            log.warn("Did not process packet {}", pkt);
    }

    private boolean commandAck(Packet pkt)
    {
        switch(pkt.getCmdCode())
        {
            case 500: 
                log.trace("Got ack for command {}", pkt.getData());
                notifyAck(null);
                return true;
            case 501:
                log.error("A command had been sent with a bad checksum");
                notifyAck(pkt);
                return true;
            case 502:
                log.error("A system error has been detected");
                processSystemError(pkt);
                return true;
            case 670:
                log.error("An access code that was entered was invalid");
                notifyAck(pkt);
                return true;
            default:
                return false;
        }
    }

    private void notifyAck(Packet pkt)
    {
        synchronized(ack)
        {
            ack.setValue(pkt);
            ack.notify();
        }
    }
    
    private static final Map<Integer, String> sysErrorCodes = Collections.unmodifiableMap(new HashMap<Integer, String>()
            {{
                put(0,"No Error");
                put(1,"Receive Buffer Overrun (a command is received while another is still being processed)");
                put(2,"Receive Buffer Overflow");
                put(3,"Transmit Buffer Overflow");
                put(10,"Keybus Transmit Buffer Overrun");
                put(11,"Keybus Transmit Time Timeout");
                put(12,"Keybus Transmit Mode Timeout");
                put(13,"Keybus Transmit Keystring Timeout");
                put(14,"Keybus Interface Not Functioning (the TPI cannot communicate with the security system)");
                put(15,"Keybus Busy (Attempting to Disarm or Arm with user code)");
                put(16,"Keybus Busy – Lockout (The panel is currently in Keypad Lockout – too many disarm attempts)");
                put(17,"Keybus Busy – Installers Mode (Panel is in installers mode, most functions are unavailable)");
                put(18,"Keybus Busy – General Busy (The requested partition is busy)");
                put(20,"API Command Syntax Error");
                put(21,"API Command Partition Error (Requested Partition is out of bounds)");
                put(22,"API Command Not Supported");
                put(23,"API System Not Armed (sent in response to a disarm command)");
                put(24,"API System Not Ready to Arm (system is either not-secure, in exit-delay, or already armed)");
                put(25,"API Command Invalid Length");
                put(26,"API User Code not Required");
                put(27,"API Invalid Characters in Command (no alpha characters are allowed except for checksum)");
            }});
    
    private void processSystemError(Packet pkt)
    {
        int errCode = Integer.parseInt(pkt.getData().substring(0, 3));
        log.info("System error code = " + errCode +  ", message = " + sysErrorCodes.getOrDefault(errCode, "unknown"));
        if(errCode >= 20) notifyAck(pkt);
    }

    private boolean updateState(Packet pkt)
    {
        return stateHandler.apply(pkt);
    }

    private boolean processReplyAction(Packet pkt)
    {
        return 
        replyActions.keySet().stream().filter(pred -> pred.test(pkt))
                 .findAny()
                 .map(pred -> replyActions.remove(pred))
                 .map(s ->   {
                                 log.trace("processing reply action for packet {}", pkt);
                                 sendAsync(s); 
                                 return true;
                             })
                 .orElse(false);
    }

    public Future<Boolean> sendAsync(PacketFlow pktFlow)
    {
        return asyncSender.submit(() ->   {
            try
            {
                send(pktFlow);
                return true;
            }
            catch(Exception io)
            {
                log.error("Failed to send packet " + pktFlow.getPkt(), io);
                return false;
            }
        });
    }
    
    public boolean isAlive()
    {
    	return !socket.isClosed();
    }
}
