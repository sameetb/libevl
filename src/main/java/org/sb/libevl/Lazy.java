package org.sb.libevl;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 
 * @author sameetb
 * @since SMP6
 * @param <T>
 */
public class Lazy<T> implements Supplier<T>, Serializable
{
    private static final long serialVersionUID = -383503376354386631L;
    private T val;
    private transient Supplier<? extends T> sup;
    
    public static <T> Lazy<T> wrap(Supplier<? extends T> sup)
    {
        return new Lazy<T>(sup);
    }
    
    private Lazy(Supplier<? extends T> sup)
    {
        this.sup = Objects.requireNonNull(sup);
    }

    @Override
    public T get()
    {
        Supplier<? extends T> tmp = sup;
        if(tmp != null)
        synchronized (this)
        {
            tmp = sup;
            if(tmp != null)
            {
                val = tmp.get();
                tmp = sup = null;
            }
        }
        return val;
    }
    
    public boolean isInitialized()
    {
        return sup == null;
    }
}
