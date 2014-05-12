/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent;

import be.sizingservers.vapus.agent.util.PropertyHelper;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Holds all the default properties.
 *
 * @author didjeeh
 */
public class Properties {

    private static final String propertiesFile = "/vApus-agent.properties";

    private static volatile String name, version, copyright, help, decimalSeparator;
    private static volatile int defaultPort = -1, sendCountersInterval = -1;

    private static final Object locker = new Object();

    /**
     * Reads the name from the file vApus-agent.properties (must be in the
     * default package). The file must contain an entry like this: name=foo
     *
     * @return
     */
    public static String getName() {
        if (Properties.name == null) {
            setName();
        }
        return Properties.name;
    }

    private static void setName() {
        try {
            synchronized (locker) {
                Properties.name = PropertyHelper.getProperty(Properties.propertiesFile, "name");
            }
        } catch (IOException ex) {
            Agent.getLogger().log(Level.SEVERE, "Could not set name: {0}", ex);
        }
    }

    /**
     * Reads the version from the file vApus-agent.properties (must be in the
     * default package). The file must contain an entry like this: version=0.1
     *
     * @return
     */
    public static String getVersion() {
        if (Properties.version == null) {
            setVersion();
        }
        return Properties.version;
    }

    private static void setVersion() {
        try {
            synchronized (locker) {
                Properties.version = PropertyHelper.getProperty(Properties.propertiesFile, "version");
            }
        } catch (IOException ex) {
            Agent.getLogger().log(Level.SEVERE, "Could not set version: {0}", ex);
        }
    }

    /**
     * Reads the copyright notice from the file vApus-agent.properties (must be in the
     * default package). The file must contain an entry like this: copyright=Copyright 2014 (c) Sizing Servers Lab\nUniversity College of West-Flanders, Department GKG
     *
     * @return
     */
    public static String getCopyright() {
        if (Properties.copyright == null) {
            setCopyright();
        }
        return Properties.copyright;
    }

    private static void setCopyright() {
        try {
            synchronized (locker) {
                Properties.copyright = PropertyHelper.getProperty(Properties.propertiesFile, "copyright");
            }
        } catch (IOException ex) {
            Agent.getLogger().log(Level.SEVERE, "Could not set copyright: {0}", ex);
        }
    }

    /**
     * Constructed using name and defaultPort.
     *
     * @return
     */
    public static String getHelp() {
        if (Properties.help == null) {
            setHelp();
        }
        return Properties.help;
    }

    private static void setHelp() {
        synchronized (locker) {
            Properties.help = " Synopsis: " + getName()
                    + " [--help (-h) | --version (-v) | --copyright (-co) | --config (-c) | --sendCountersInterval (-s) | --decimalSeparator (-d) | --whatDoYouHave (-wdyh) [ --port (-p) X ] ]; ommitting --port X will start listening at "
                    + getDefaultPort()
                    + "Remarks: Use ./start-as-daemon.sh [--port (-p) X] to run the agent as a daemon (service). Stop it using ./stop-daemon.sh";
        }
    }

    /**
     * Reads the default port from the file vApus-agent.properties (must be in
     * the default package). The file must contain an entry like this:
     * defaultPort=1234
     *
     * @return
     */
    public static int getDefaultPort() {
        if (Properties.defaultPort == -1) {
            setDefaultPort();
        }
        return Properties.defaultPort;
    }

    private static void setDefaultPort() {
        try {
            synchronized (locker) {
                Properties.defaultPort = Integer.parseInt(PropertyHelper.getProperty(Properties.propertiesFile, "defaultPort"));
            }
        } catch (IOException ex) {
            Agent.getLogger().log(Level.SEVERE, "Could not set default port: {0}", ex);
        }
    }

    /**
     * Reads the send counters interval in milliseconds from the file
     * vApus-agent.properties (must be in the default package). The file must
     * contain an entry like this: sendCountersInterval=1000
     *
     * @return
     */
    public static int getSendCountersInterval() {
        if (Properties.sendCountersInterval == -1) {
            setSendCountersInterval();
        }
        return Properties.sendCountersInterval;
    }

    private static void setSendCountersInterval() {
        try {
            synchronized (locker) {
                Properties.sendCountersInterval = Integer.parseInt(PropertyHelper.getProperty(Properties.propertiesFile, "sendCountersInterval"));
            }
        } catch (IOException ex) {
            Agent.getLogger().log(Level.SEVERE, "Could not set send counters interval: {0}", ex);
        }
    }

    /**
     * Reads the decimal separator from the file vApus-agent.properties (must be
     * in the default package). The file must contain an entry like this:
     * decimalSeparator=.
     *
     * @return
     */
    public static String getDecimalSeparator() {
        if (Properties.decimalSeparator == null) {
            setDecimalSeparator();
        }
        return Properties.decimalSeparator;
    }

    private static void setDecimalSeparator() {
        try {
            synchronized (locker) {
                Properties.decimalSeparator = PropertyHelper.getProperty(Properties.propertiesFile, "decimalSeparator");
            }
        } catch (IOException ex) {
            Agent.getLogger().log(Level.SEVERE, "Could not set decimal separator: {0}", ex);
        }
    }
}
