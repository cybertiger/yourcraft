/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.yourcraft.net;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 *
 * @author antony
 */
public abstract class KeyAttachment<T extends SelectableChannel> {
    protected final T channel;
    private NIOThread thread;
    private int ops;

    public KeyAttachment(T channel) {
        this.channel = channel;
    }

    public T getChannel() {
        return channel;
    }

    public int interestOps() {
        return ops;
    }

    public void interestOps(int ops) {
        this.ops = ops;
        if (thread != null) {
            thread.updateAttachment(this);
        }
    }

    /* package private */
    void register(NIOThread thread, Selector selector) throws ClosedChannelException {
        if (thread != null) throw new IllegalStateException("Already registered to an NIOThread");
        this.thread = thread;
        channel.register(selector, ops, this);
    }

    /* package private */
    void deregister(NIOThread thread, Selector selector) {
        if (thread != this.thread) throw new IllegalStateException("Not registered");
        SelectionKey key = channel.keyFor(selector);
        if (key != null) 
            key.cancel();
        this.thread = null;
    }

    public abstract void accept() throws IOException;
    public abstract void connect() throws IOException;
    public abstract void read() throws IOException;
    public abstract void write() throws IOException;

}
