/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.yourcraft.net;

import java.nio.channels.SelectableChannel;

/**
 *
 * @author antony
 */
public interface KeyAttachment<T extends SelectableChannel> {

    public T getChannel();
    public int interestOps();
    public void accept();
    public void connect();
    public void read();
    public void write();
    
}
