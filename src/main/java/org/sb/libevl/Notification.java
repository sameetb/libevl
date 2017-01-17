/**
 * 
 */
package org.sb.libevl;

import java.text.SimpleDateFormat;
import java.util.Date;

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
    
    public final Type type;
    public final Date ts;
    public final String msg;
    
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
	    	JsonHelper.json("ts", date()),
	    	JsonHelper.json("msg", msg));        
    } 
    
    public String date()
    {
    	return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
    }
}
