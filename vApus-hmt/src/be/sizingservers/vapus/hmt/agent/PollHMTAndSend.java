/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.hmt.agent;

import be.sizingservers.vapus.agent.Agent;
import be.sizingservers.vapus.agent.Server;
import be.sizingservers.vapus.agent.util.Entities;
import be.sizingservers.vapus.hmt.agent.cpu.CPU;
import com.google.gson.Gson;
import java.net.Socket;
import java.util.TimerTask;
import java.util.logging.Level;

/**
 *
 * @author Administrator
 */
public class PollHMTAndSend extends TimerTask {

    private final Server server;
    private final Socket socket;
    private final Entities wiwEntities;
    private final CPU cpu;

    public PollHMTAndSend(Entities wiwEntities, Server server, Socket socket, CPU cpu) {
        this.wiwEntities = wiwEntities;
        this.server = server;
        this.socket = socket;
        this.cpu = cpu;
    }

    @Override
    public void run() {
        try {
            Entities wiwWithCounters = this.cpu.getWIWWithCounters(this.wiwEntities);
            wiwWithCounters.setTimestamp();
       
            this.server.send(new Gson().toJson(wiwWithCounters), this.socket);
        } catch (Exception ex) {
            Agent.getLogger().log(Level.SEVERE, "Failed sending counters (stopping the timer task now): {0}", ex);
            super.cancel();
        }
    }
}
