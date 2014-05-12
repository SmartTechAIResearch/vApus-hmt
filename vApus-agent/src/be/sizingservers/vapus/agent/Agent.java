/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent;

import java.io.*;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Let your class with main(String[] args) extend this class. Call
 * Agent.main(args, new serverImplementation(), new monitorImplementation()) in
 * the body of the main fx. All stuff is automated for you.
 *
 * @author didjeeh
 */
public class Agent {

    private static final Logger logger = Logger.getLogger("Agent");
    private static final String loggingFile = "/logging.properties";
    private static int port = Properties.getDefaultPort();

    /**
     *
     * @return
     */
    public static Logger getLogger() {
        return logger;
    }

    /**
     *
     * @param args
     * @param server new serverImplementation()
     * @param monitor new monitorImplementation using empty constructor.
     */
    protected static void main(String[] args, Server server, Monitor monitor) {
        Runtime.getRuntime().addShutdownHook(new ShutdownThread(server));

        System.out.println(Properties.getName() + " " + Properties.getVersion());
        System.out.println(Properties.getCopyright());
        System.out.println();

        InputStream stream = null;
        try {
            stream = Agent.class.getResourceAsStream(Agent.loggingFile);
            LogManager.getLogManager().readConfiguration(stream);
        } catch (IOException ex) {
            System.err.println("Failed reading the logging configuration file: " + ex);
            return;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    System.err.println("Failed closing the stream for the logging configuration file: " + ex);
                }
            }
        }

        try {
            if (!analyzeArgs(args)) {
                throw new Exception("Failed analyzing args.");
            }
        } catch (Exception ex) {
            Agent.logger.log(Level.SEVERE, ex.toString());
            return;
        }

        System.out.print("Fetching the hardware configuration...");
        if (Monitor.getConfig() == null) {
            monitor.setConfig();
        }
        System.out.println("OK");
        System.out.print("Fetching the available counters...");
        if (Monitor.getWDYH() == null) {
            monitor.setWDYH();
        }
        System.out.println("OK");
        Monitor.cleanupConfigAndWDYH();

        server.start(Agent.port);

        if (!server.isRunning()) {
            return;
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "UTF8"));

            String line = reader.readLine();

            //First check if this is not running as a background process.
            if (line == null) {
                while (true) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        System.out.println(ex.toString() + Arrays.toString(ex.getStackTrace()));
                    }
                }
            }
            while (line == null || !line.equals("q")) {
                line = reader.readLine(); //Wait.
            }
        } catch (IOException ex) {
            Agent.logger.log(Level.SEVERE, "Failed reading input stream: {0}", ex);
        }

        server.stop();
    }

    /**
     * @param args
     * @return true if the server must be started.
     */
    private static boolean analyzeArgs(String[] args) {
        boolean startServer = true;
        boolean nextIsPort = false;

        for (int i = 0; i != args.length; i++) {
            String arg = args[i];
            if (arg.equals("--help") || arg.equals("-h")) {
                System.out.println(Properties.getHelp());
                startServer = false;
            } else if (arg.equals("--version") || arg.equals("-v")) {
                System.out.println(Properties.getVersion());
                startServer = false;
            } else if (arg.equals("--copyright") || arg.equals("-co")) {
                System.out.println(Properties.getCopyright());
                startServer = false;
            } else if (arg.equals("--config") || arg.equals("-c")) {
                System.out.println(Monitor.getConfig());
                startServer = false;
            } else if (arg.equals("--sendCountersInterval") || arg.equals("-s")) {
                System.out.println(Properties.getSendCountersInterval());
                startServer = false;
            } else if (arg.equals("--decimalSeparator") || arg.equals("-d")) {
                System.out.println(Properties.getDecimalSeparator());
                startServer = false;
            } else if (arg.equals("--whatDoYouHave") || arg.equals("-wdyh")) {
                System.out.println(Monitor.getWDYH());
                startServer = false;
            } else if (arg.equals("--daemon") || arg.equals("-d")) {
                startServer = true;
            } else if (arg.equals("--port") || arg.equals("-p")) {
                nextIsPort = true;
            } else if (nextIsPort) {
                nextIsPort = false;
                Agent.port = Integer.parseInt(arg);
                startServer = true;
            }
        }
        return startServer;
    }
}
