/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG
 *
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.hmt.agent;

import be.sizingservers.vapus.agent.Agent;

/**
 *
 * @author Didjeeh
 */
public class HMTAgent extends Agent {
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Agent.main(args, new HMTServer(), new HMTMonitor());
    }
}
