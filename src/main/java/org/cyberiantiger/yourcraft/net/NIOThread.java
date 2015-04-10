/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cyberiantiger.yourcraft.net;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author antony
 */
public class NIOThread implements Runnable, Closeable {
    private static final int MAX_WAIT_MILLIS = 1000;
    private static final Logger LOG = Logger.getLogger(NIOThread.class.getName());
    private Selector selector;
    private PriorityQueue<Task> internalTasks;
    private ConcurrentLinkedQueue<Runnable> externalTasks;
    private Set<KeyAttachment> updatedAttachments;
    private Thread thread;

    public NIOThread() throws IOException {
        this.selector = Selector.open();
        internalTasks = new PriorityQueue<>();
        externalTasks = new ConcurrentLinkedQueue<>();
        updatedAttachments = new HashSet<>();
    }
    
    // Threadsafe.
    public void addExternalTask(Runnable r) {
        externalTasks.offer(r);
        // This is a race, see comment in run() {}
        selector.wakeup();
    }

    public void addInternalTask(long nanoTime, Runnable r) {
        checkThread();
        internalTasks.offer(new Task(nanoTime, r));
    }

    public void addAttachment(KeyAttachment attachment) throws ClosedChannelException {
        checkThread();
        attachment.getChannel().register(selector, attachment.interestOps(), attachment);
    }

    public void removeAttachment(KeyAttachment attachment) {
        checkThread();
        SelectionKey key = attachment.getChannel().keyFor(selector);
        if (key != null) 
            key.cancel();
    }

    public void updateAttachment(KeyAttachment attachement) {
        checkThread();
        updatedAttachments.add(attachement);
    }

    @Override
    public void run() {
        if (this.thread != null) {
            throw new IllegalStateException("Already running");
        }
        this.thread = Thread.currentThread();
        while (selector.isOpen()) {
            // Process internal tasks.
            long now = System.nanoTime();
            Task task;
            while ( (task = internalTasks.peek()) != null && task.getWhen() <= now) {
                internalTasks.poll();
                try {
                    task.getTarget().run();
                } catch (Exception ex) {
                    Logger.getLogger(NIOThread.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            // Process external tasks.
            Runnable runnable;
            while ( (runnable = externalTasks.poll()) != null) {
                runnable.run();
            }
            // Update interestops, where appropriate.
            for (KeyAttachment attachment : updatedAttachments) {
                attachment.getChannel().keyFor(selector).interestOps(attachment.interestOps());
            }
            // There is a race condition here where if an external task is added it won't
            // get executed until the next time the selector returns, in practice this 
            // is highly unlikely to happen, and even if it does, we'll wait at most MAX_WAIT_MILLIS 
            // before checking the external task queue for new tasks.
            now = System.nanoTime();
            try {
                int selected;
                if (task != null) {
                    long wait = (task.getWhen() - now) / 1000000;
                    if (wait <= 0) {
                        selected = selector.selectNow();
                    } else {
                        selected = selector.select(Math.min(wait, MAX_WAIT_MILLIS));
                    }
                } else {
                    selected = selector.select(MAX_WAIT_MILLIS);
                }
                if (selected > 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    for (SelectionKey key : selectedKeys) {
                        KeyAttachment attachment = (KeyAttachment) key.attachment();
                        if (key.isAcceptable()) {
                            try {
                                attachment.accept();
                            } catch (Exception ex) {
                                Logger.getLogger(NIOThread.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        if (key.isConnectable()) {
                            try {
                                attachment.connect();
                            } catch (Exception ex) {
                                Logger.getLogger(NIOThread.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        if (key.isReadable()) {
                            try {
                                attachment.read();
                            } catch (Exception ex) {
                                Logger.getLogger(NIOThread.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        if (key.isWritable()) {
                            try {
                                attachment.write();
                            } catch (Exception ex) {
                                Logger.getLogger(NIOThread.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                    selectedKeys.clear();
                }
            } catch (IOException ex) {
                // Error on select() handled here.
                Logger.getLogger(NIOThread.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ClosedSelectorException ex) {
                // Don't log, happens when close() is called due to normal operation.
            }
        }
    }

    @Override
    public void close() throws IOException {
        selector.close();
    }

    private void checkThread() {
        if (this.thread == null) {
            throw new IllegalStateException("Not running");
        } else if (this.thread != Thread.currentThread()) {
            throw new IllegalStateException("Invalid thread");
        }
    }
}
