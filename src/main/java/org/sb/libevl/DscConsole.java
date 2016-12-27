/**
 * 
 */
package org.sb.libevl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * @author sameetb
 * @since SMP6
 */
public class DscConsole
{
    public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException, TimeoutException
    {
        new DscConsole(args);
    }
    
    public DscConsole(String[] args) throws UnknownHostException, IOException, InterruptedException, TimeoutException
    {
        usage(args);
        final DscPanel panel = new DscPanel(DscConsole::println);
        final EvlConnection conn = new EvlConnection(InetAddress.getByName(args[0]), 
                                                Optional.ofNullable(args.length > 1 ? args[1] : null).map(p -> Integer.parseInt(p)), 
                                                DscConsole::getPassword, panel.stateHandler);
        Commands cmds = new Commands();
        conn.send(cmds.statusReport());
        
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        engine.put("panel", panel);
        engine.put("cmds", cmds);
        engine.put("conn", conn);
        
        String input;
        while(!"exit".equalsIgnoreCase(input = readLine()))
        {
            try
            {
                Optional.ofNullable(engine.eval(input)).ifPresent(o -> System.out.println(o));
            }
            catch (ScriptException e)
            {
                System.err.println(e.getMessage());
            }
        }
        
        conn.close();
        panel.close();
        System.console().flush();
    }
    
    private static void usage(String[] args)
    {
        if(args.length > 0) return;
        System.out.println("Usage: " + DscConsole.class.getSimpleName() + " <ipAddress>");
        System.exit(1);
    }
    
    private static String getPassword()
    {
        return String.valueOf(System.console().readPassword("Envisalink Password:"));
    }
    
    private static String readLine()
    {
        return System.console().readLine();
    }
    
    private static void println(Notification note)
    {
        System.out.println(note.toJson());
    }
}
