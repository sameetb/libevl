/**
 * 
 */
package org.sb.libevl;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author sameetb
 * @since SMP6
 */
public class EvictingQueue<E> extends ArrayBlockingQueue<E>
{
    /**
     * 
     */
    private static final long serialVersionUID = -3479283668863118264L;
    
    
    private final Consumer<E> evCon;
    
    public EvictingQueue(int capacity)
    {
        this(capacity, e -> System.out.println("Evicting item " + e));
    }
    
    public EvictingQueue(int capacity, Consumer<E> evCon)
    {
        super(capacity, false);
        this.evCon = evCon;
    }

    @Override
    public boolean offer(E e)
    {
        evictIfNecessary();
        return super.offer(e);
    }

    @Override
    public void put(E e) throws InterruptedException
    {
        evictIfNecessary();
        super.put(e);
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException
    {
        evictIfNecessary();
        return super.offer(e, timeout, unit);
    }

    private void evictIfNecessary()
    {
        if(remainingCapacity() == 0) 
            Optional.ofNullable(super.poll()).ifPresent(evCon);
    }

}
