/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent;

import be.sizingservers.vapus.agent.util.Entities;
import com.google.gson.Gson;
import java.net.Socket;

/**
 * Extend your own monitor implementation from this.
 * A constructor must be present calling super(server, socket, id).
 *
 * @author didjeeh
 */
public abstract class Monitor {

    /**
     * Should only be written to in the setConfig() implementation. And only once, when the agent starts.
     */
    protected volatile static String config;
    /**
     * Should only be written to in the setWDYH() implementation. And only once, when the agent starts.
     */
    protected volatile static String wdyh;
    /**
     * Should only be written to in the setWDYH() implementation. And only once, when the agent starts.
     */
    protected volatile static Entities wdyhEntities;

    /**
     * The server that created this monitor. There should only be one server in an agent.
     */
    protected Server server;
    /**
     * A monitor is always linked to a socket.
     */
    protected Socket socket;
    
    private final long id;
    private String wiw;
    private Entities wiwEntities;
    /**
     * Set to true when started and to false when stopped. This can be used as a check to for not trying to start the monitor twice.
     */
    protected boolean running;

    /**
     * A empty instance only to be used to call getConfig() and getWDYH().
     * You must do the needed init stuff here.
     */
    public Monitor() {
        this.id = -1;
    }

    /**
     *
     * @param server
     * @param socket Monitor should always be used in Server. The connection
     * that uses this monitor is used for when new data is available. Then it is
     * send to the client.
     * @param id
     */
    public Monitor(Server server, Socket socket, long id) {
        this.server = server;
        this.socket = socket;
        this.id = id;
    }

    /**
     * Sets the hardware configuration. This must be in XML. (lshw -xml output
     * for instance). It may not contain line breaks, but if so these will be
     * trimmed for you. This will be called automatically for you when you call
     * the getter.
     */
    public abstract void setConfig();

    /**
     * Gets the headers and subheaders that can be monitored into one or more Entity objects (Ex: a machine).
     * This will be called automatically for you when you call the getter.
     */
    public abstract void setWDYH();

    /**
     * Start the monitor and send counters periodically using
     * super.server.send(String line, super.Socket)
     */
    public abstract void start();

    /**
     * Do not forget to cleanup sources.
     */
    public abstract void stop();

    /**
     * If is null: parsed from wdyh is that is not null.
     * @return
     */
    public static Entities getWDYHEntities() {
        if (Monitor.wdyhEntities == null && Monitor.wdyh != null) {
            Monitor.wdyhEntities = new Gson().fromJson(Monitor.wdyh, Entities.class);
        }
        return Monitor.wdyhEntities;
    }

    /**
     *
     * @return The unique id for this monitor.
     */
    public long getId() {
        return this.id;
    }

    /**
     * Gets the hardware configuration.
     *
     * @return
     */
    public static String getConfig() {
        return Monitor.config;
    }

    /**
     * Get what do you have.
     * If is null: parsed from wdyhEntities is that is not null.
     * @return
     */
    public static String getWDYH() {
        if (Monitor.wdyhEntities != null && Monitor.wdyh == null) {
            Monitor.wdyh = new Gson().toJson(Monitor.wdyhEntities);
        }
        return Monitor.wdyh;
    }

    /**
     * Removes line breaks.
     */
    public static void cleanupConfigAndWDYH() {
        Monitor.config = cleanUp(Monitor.getConfig());
        Monitor.wdyh = cleanUp(Monitor.getWDYH());
    }

    private static String cleanUp(String s) {
        if (s.contains("\n")) {
            s = s.replaceAll("\n", "");
        }
        if (s.contains("\r")) {
            s = s.replaceAll("\r", "");
        }
        return s;
    }

    /**
     * Get what I want
     *
     * @return wdyh if wiw is null.
     */
    public String getWIW() {
        if (this.wiw == null) {
            return Monitor.wdyh;
        }
        return this.wiw;
    }

    /**
     * Set what I want.
     *
     * @param wiw Should be formatted the same as wdyh.
     */
    public void setWIW(String wiw) {
        this.wiw = wiw;
    }

    /**
     * @return
     */
    public Entities getWIWEntities() {
        if (this.wiwEntities == null) {
            this.wiwEntities = new Gson().fromJson(getWIW(), Entities.class);
        }
        return wiwEntities;
    }
}
