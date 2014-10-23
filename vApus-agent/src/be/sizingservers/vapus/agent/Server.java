/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * Extend your own server implementation from this.
 *
 * @author didjeeh
 */
public abstract class Server {

    private boolean running;
    private ServerSocket serverSocket;
    private AcceptThread acceptThread;

    private final ArrayList<Monitor> monitors = new ArrayList<Monitor>();

    /**
     * Extend your own server implementation from this.
     */
    public Server() {
    }

    /**
     *
     * @return
     */
    public boolean isRunning() {
        return this.running;
    }

    /**
     * Logs if fails to start.
     *
     * @param port
     *
     */
    public void start(int port) {
        if (this.running) {
            return;
        }
        try {
            this.running = true;

            this.serverSocket = new ServerSocket(port);
            //Accept connections and handle requests on another thread.
            this.acceptThread = new AcceptThread(this);
            this.acceptThread.start();

            System.out.println("Listening at port " + port);
        } catch (IOException ex) {
            Agent.getLogger().log(Level.SEVERE, "Failed starting server: {0}", ex);
            stop();
        }
    }

    /**
     *
     * @param server
     * @param socket
     * @param id
     * @return a new instance of a class that handles getting the monitor data
     * and hw config.
     */
    protected abstract Monitor getNewMonitor(Server server, Socket socket, long id);

    /**
     *
     * @param socket The socket that is used to send counters to the client.
     * @return
     */
    private Monitor suscribeMonitorForAGivenSocket(Socket socket) {
        Monitor monitor = this.getNewMonitor(this, socket, getUniqueMonitorId());
        this.monitors.add(monitor);

        return monitor;
    }

    private long getUniqueMonitorId() {
        long id = 1;
        for (int i = 0; i != this.monitors.size(); i++) {
            if (this.monitors.get(i).getId() == id) {
                ++id;
            }
        }
        return id;
    }

    /**
     * Threadsafe
     *
     * @param monitor
     * @return
     */
    private boolean unsuscribeMonitor(Monitor monitor) {
        synchronized (monitors) {
            return this.monitors.remove(monitor);
        }
    }

    /**
     * The text is delimited with a '\n'. Can be used to send counters.
     *
     * @param message
     * @param socket
     */
    public void send(String message, Socket socket) {
        try {
            send(message, new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8")));
        } catch (IOException ex) {
            if (this.running) {
                Agent.getLogger().log(Level.SEVERE, "Error sending message: {0}", ex);
            }
        }
    }

    /**
     *
     * @param message
     * @param out
     * @throws java.io.IOException
     */
    public void send(String message, BufferedWriter out) throws IOException {
        out.write(message + '\n');
        out.flush();
    }

    /**
     * Stops the server, closes all sockets, all monitors should get cleaned up.
     */
    public void stop() {
        this.running = false;
        try {
            if (this.serverSocket != null) {
                this.serverSocket.close();
            }
        } catch (IOException ex) {
            Agent.getLogger().log(Level.SEVERE, "Error stopping server: {0}", ex);
        }
    }

    private class AcceptThread extends Thread {

        private final Server server;
        private final ArrayList<HandleRequestThread> handleRequestThreads;

        /**
         *
         * @param server
         */
        public AcceptThread(Server server) {
            this.server = server;
            this.handleRequestThreads = new ArrayList<HandleRequestThread>();
        }

        @Override
        public void run() {
            while (this.server.running) {
                accept();
            }
            for (int i = 0; i != this.handleRequestThreads.size(); i++) {
                this.handleRequestThreads.get(i).forceStop();
            }
        }

        private void accept() {
            try {
                Socket socket = this.server.serverSocket.accept();
                Monitor monitor = suscribeMonitorForAGivenSocket(socket);

                try {
                    HandleRequestThread handleRequestThread = new HandleRequestThread(this.server, socket, monitor);
                    this.handleRequestThreads.add(handleRequestThread);
                    handleRequestThread.start();
                } catch (IOException ex) {
                    Agent.getLogger().log(Level.INFO, "Socket was closed from the other side: {0}", ex);

                    monitor.stop();
                    this.server.unsuscribeMonitor(monitor);

                    try {
                        socket.close();
                    } catch (IOException ex1) {
                        Agent.getLogger().log(Level.SEVERE, "Failed closing the socket: {0}", ex1);
                    }
                }
            } catch (IOException ex) {
                if (this.server.running) {
                    Agent.getLogger().log(Level.SEVERE, "Error accepting connection: {0}", ex);
                }
            }
        }

        private class HandleRequestThread extends Thread {

            private final Server server;
            private final Socket socket;
            private final BufferedReader in;
            private final BufferedWriter out;

            private final Monitor monitor;

            /**
             *
             * @param server
             * @param socket
             * @param monitor
             * @throws IOException
             */
            public HandleRequestThread(Server server, Socket socket, Monitor monitor) throws IOException {
                this.server = server;
                this.socket = socket;
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF8"));
                this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"));

                this.monitor = monitor;
            }

            @Override
            public void run() {
                //isConnected() is not equal to isClosed() apparently :s, stupid java.
                while (this.server.running && !this.socket.isClosed()) {
                    try {
                        handleRequest();
                    } catch (Exception ex) {
                        if (this.server.running) {
                            Agent.getLogger().log(Level.SEVERE, "Error handling request (closing the socket now): {0}", ex);
                            try {
                                this.socket.close();
                            } catch (IOException ex1) {
                                Agent.getLogger().log(Level.SEVERE, "Failed closing the socket: {0}", ex1);
                            }
                        }
                    }
                }
                cleanup();
            }

            /**
             * Example: String message = in.readLine(); if (message == "a") {
             * String s = "reply"; out.writeBytes(s + '\n'); } Handle(in, out);
             *
             * @param socket
             * @param in
             * @param out
             * @throws IOException
             */
            private void handleRequest() throws Exception {

                try {
                    String message = this.in.readLine();
                    //Check if the stream was not closed client-side.
                    if (message == null) {
                        cleanup();
                        return;
                    }

                    if (message.equals("version")) {
                        message = Properties.getVersion();
                    } else if (message.equals("copyright")) {
                        message = Properties.getCopyright();
                    } else if (message.equals("config")) {
                        message = Monitor.getConfig();
                    } else if (message.equals("sendCountersInterval")) {
                        message = "" + Properties.getSendCountersInterval();
                    } else if (message.equals("decimalSeparator")) {
                        message = Properties.getDecimalSeparator();
                    } else if (message.equals("wdyh")) {
                        message = Monitor.getWDYH();
                    } else if (message.equals("start")) {
                        this.monitor.start();
                        message = "200";
                    } else if (message.equals("stop")) {
                        this.monitor.stop();
                        this.server.unsuscribeMonitor(this.monitor);
                        this.server.send("200", this.out);
                        try {
                            this.socket.close();
                        } catch (IOException ex1) {
                            Agent.getLogger().log(Level.SEVERE, "Failed closing the socket: {0}", ex1);
                        }
                        return;
                    } else if (message.startsWith("[{\"name\":\"")) {
                        this.monitor.setWIW(message);
                        message = "200";
                    } else {
                        message = "404";
                    }
                    this.server.send(message, this.out);
                } catch (IOException ex) {
                    cleanup();
                    throw ex;
                }
            }

            public void forceStop() {
                try {
                    if (!this.socket.isClosed()) {
                        this.socket.close();
                    }
                } catch (Exception ex) {
                    //Ignore
                }
            }

            /**
             * Stops and unsuscibes the monitor, closes the socket.
             *
             * @param monitor
             */
            private void cleanup() {
                this.monitor.stop();
                this.server.unsuscribeMonitor(this.monitor);

                try {
                    this.socket.close();
                } catch (IOException ex) {
                    Agent.getLogger().log(Level.SEVERE, "Failed closing the socket: {0}", ex);
                }
            }

        }
    }
}
