/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.yourcraft.net;

/**
 *
 * @author antony
 */
class Task implements Comparable<Task> {
    private final long when;
    private final Runnable target;

    public Task(long when, Runnable target) {
        this.when = when;
        this.target = target;
    }

    public long getWhen() {
        return when;
    }

    public Runnable getTarget() {
        return target;
    }

    @Override
    public int compareTo(Task o) {
        if (o == this) {
            return 0;
        }
        return this.when < o.when ? -1 : this.when > o.when ? 1 : 0;
    }
    
}
