/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent;

/**
 * To be used in the Agent constructor like so: Runtime.getRuntime().addShutdownHook(new ShutdownThread(server));
 *    
 * @author didjeeh
 */
public class ShutdownThread extends Thread {

    private final Server server;

    /**
     * To be used in the Agent constructor like so: Runtime.getRuntime().addShutdownHook(new ShutdownThread(server));
     * 
     * @param server 
     */
    public ShutdownThread(Server server) {
        this.server = server;
    }

    /**
     * 
     */
    @Override
    public void run() {
        this.server.stop();
    }
}
