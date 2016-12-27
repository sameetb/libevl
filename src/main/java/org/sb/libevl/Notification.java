/**
 * 
 */
package org.sb.libevl;

import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.text.SimpleDateFormat;

/**
 * @author sam
 *
 */
 
public class Notification
{
    public enum Type {

	    LED,
	    ALARM,
	    TROUBLE,
	    ARM,
	    ZONE,
	    MISC
    };
    
    private final Type type;
    private final Date ts;
    private final String msg;
    
    public Notification(Type type, Date ts, String msg)
    {
        this.type = type;
        this.ts = ts;
        this.msg = msg;
    }
    
    public String toJson()
    {
        return JsonHelper.obj(
	    	JsonHelper.json("type", type.name()),
	    	JsonHelper.json("ts", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())),
	    	JsonHelper.json("msg", Stream.of(msg).collect(Collectors.joining(" "))));        
    }    
}
