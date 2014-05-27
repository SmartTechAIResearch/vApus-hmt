/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG
 *
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.hmt.agent;

import be.sizingservers.vapus.agent.Agent;
import be.sizingservers.vapus.agent.Monitor;
import be.sizingservers.vapus.agent.Server;
import be.sizingservers.vapus.agent.util.Directory;
import be.sizingservers.vapus.hmt.agent.cpu.CPUProvider;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.logging.Level;

/**
 *
 * @author Didjeeh
 */
public class HMTMonitor extends Monitor {

    /**
     * A empty instance only to be used to call getConfig() and getWDYH(). You
     * must do the needed init stuff here.
     */
    public HMTMonitor() {
        init();
    }

    /**
     *
     * @param server
     * @param socket
     * @param id
     */
    public HMTMonitor(Server server, Socket socket, long id) {
        super(server, socket, id);
    }

    private void init() {
        try {
            System.setProperty("jna.library.path", Directory.getExecutingDirectory(HMTAgent.class));
        } catch (URISyntaxException ex) {
            Agent.getLogger().log(Level.SEVERE, "Failed setting the jna.library.path: {0}", ex);
        }
        try {
           CPUProvider cpu = new CPUProvider();
        } catch (Exception ex) {
            Agent.getLogger().log(Level.SEVERE, "Failed loading cpu id: {0}", ex);
        }
    }

    @Override
    public void setConfig() {
        if (Monitor.config == null) {
            try {
                Monitor.config = "<list>Use the vApus-dstat agent or the vApus-wmi agent if you want the hardware config.</list>";
            } catch (Exception ex) {
                Agent.getLogger().log(Level.SEVERE, "Failed setting config: {0}", ex);
            }
        }
    }

    @Override
    public void setWDYH() {
        if (Monitor.wdyh == null) {
            //Dependend on architecture: AMD or Intel + families. AND OS: Windows or Linux.
        }
    }

    @Override
    public void start() {
        try {
            if (super.running) {
                return;
            }
            super.running = true;

//            this.poller = new Timer();
//            this.pollWmiAndSend = new PollWMIAndSend(super.getWIWEntities(), super.server, super.socket);
//
//            int interval = Properties.getSendCountersInterval();
//            this.poller.scheduleAtFixedRate(this.pollWmiAndSend, 0, interval);
        } catch (Exception ex) {
            stop();
            Agent.getLogger().log(Level.SEVERE, "Failed at starting the wmi monitor: {0}", ex);
        }
    }

    @Override
    public void stop() {
        if (!super.running) {
            return;
        }
        super.running = false;

//        this.poller.cancel();
//        this.poller.purge();
//        this.poller = null;
//
//        this.pollWmiAndSend.cancel();
//        this.pollWmiAndSend = null;
    }
}
