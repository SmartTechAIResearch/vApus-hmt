/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *
 * @author didjeeh
 */
public class HostName {

    public static String get() {
        try {
            return InetAddress.getLocalHost().getHostName();      
        } catch (UnknownHostException ex) {
            //Ignore.
        }
        return null;
    }
}
