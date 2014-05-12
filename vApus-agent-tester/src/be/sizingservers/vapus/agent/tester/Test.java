/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent.tester;

import be.sizingservers.vapus.agent.util.Combiner;
import be.sizingservers.vapus.agent.util.CounterInfo;
import be.sizingservers.vapus.agent.util.Entities;
import be.sizingservers.vapus.agent.util.Entity;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

/**
 *
 * @author didjeeh
 */
public class Test extends Thread {

    private final int id;
    private final String ip;
    private final boolean verbose;
    private final int port;

    private final Gson gson;

    private Entities wiwEntities;

    /**
     *
     * @param id
     * @param ip
     * @param port
     * @param verbose
     */
    public Test(int id, String ip, int port, boolean verbose) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        this.verbose = verbose;

        this.gson = new Gson();
    }

    /**
     * Run this test.
     */
    @Override
    public void run() {
        Socket socket = null;
        try {
            System.out.println("Test " + this.id + " Started");
            if (this.verbose) {
                System.out.println("Test " + this.id + " Connecting to " + ip + ":" + port + "...");
            }

            socket = new Socket(this.ip, this.port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF8"));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF8"));

            writeRead(out, "version", in);
            writeRead(out, "config", in);
            writeRead(out, "sendCountersInterval", in);
            writeRead(out, "decimalSeparator", in);

            String wdyh = writeRead(out, "wdyh", in);

            determineRandomWiwEntities(wdyh);
            String wiw = (new Gson()).toJson(this.wiwEntities);
            if (this.verbose) {
                System.out.println("Test " + this.id + " Random wiw determined: " + wiw);
            }

            writeRead(out, wiw, in);

            writeRead(out, "start", in);

            if (this.verbose) {
                System.out.println("Test " + this.id + " Reading and parsing counters 3 times...");
            }
            for (int i = 0; i != 3; i++) {
                parseCounters(read(in, "[{\"name\":\"entity\",\"isAvailable\":true,\"subs\":[{\"name\":\"header\",\"subs\":..."));
            }

            writeRead(out, "stop", in);

            socket.close();

            System.out.println("Test " + this.id + " Finished succesfully");

        } catch (IOException ex) {
            System.err.println("Test " + this.id + " Failed: " + ex);
        } catch (Exception ex) {
            System.err.println("Test " + this.id + " Failed: " + ex);
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
                System.err.println("Test " + this.id + " Failed closing the socket: " + ex);
            }
        }
    }

    private String writeRead(BufferedWriter out, String write, BufferedReader in) throws IOException, Exception {
        if (this.verbose) {
            System.out.println("Test " + this.id + " Out: " + write.trim());
        }
        if (!write.endsWith("\n")) {
            write += '\n';
        }
        out.write(write);
        out.flush();

        String read = read(in, write);

        return read;
    }

    private String read(BufferedReader in, String expectedResponse) throws IOException, Exception {

        String read = in.readLine();
        if (this.verbose) {
            System.out.println("Test " + this.id + " In: " + read);
        }

        if (read == null) {
            throw new IOException("Test " + this.id + " 500: expected " + expectedResponse.trim());
        } else if (read.equals("404")) {
            throw new Exception("Test " + this.id + " 404: expected " + expectedResponse.trim());
        } else if (read.length() == 0) {
            throw new Exception("Test " + this.id + " The read message is empty.");
        }

        return read;
    }

    private void determineRandomWiwEntities(String wdyh) throws Exception {
        try {
            Entities entities = this.gson.fromJson(wdyh, Entities.class);
            this.wiwEntities = new Entities();

            boolean hasAvailableEntities = false;
            for (int i = 0; i != entities.size(); i++) {
                if (entities.get(i).isAvailable()) {
                    hasAvailableEntities = true;
                    break;
                }
            }

            if (hasAvailableEntities) {
                boolean addedOne = false;
                // Add minimum one, otherwise parsing the values can go wrong if headers are missing at the last level.
                while (!addedOne) {
                    for (int i = 0; i != entities.size(); i++) {
                        //Random seed, otherwise System.currentTimeMillis() is used and I do not want to let the thread sleep.
                        Random random = new Random(UUID.randomUUID().hashCode());
                        if (random.nextBoolean()) {
                            Entity entity = entities.get(i);
                            if (entity.isAvailable()) {
                                addedOne = true;
                                
                                Entity newEntity = new Entity(entity.getName(), entity.isAvailable());
                                this.wiwEntities.add(newEntity);

                                chanceCopySubs(entity, newEntity);
                            }
                        }
                    }
                }
            }
        } catch (JsonSyntaxException ex) {
            throw new Exception("Could not determine a random wiw, because the given wdyh is malformed: " + ex);
        }
    }

    private void chanceCopySubs(Entity from, Entity to) {
        ArrayList<CounterInfo> subs = from.getSubs();
        if (!subs.isEmpty()) {
            boolean addedOne = false;
            // Add minimum one, otherwise parsing the values can go wrong if headers are missing at the last level.
            while (!addedOne) {
                for (int i = 0; i != subs.size(); i++) {
                    //Random seed, otherwise System.currentTimeMillis() is used and I do not want to let the thread sleep.
                    Random random = new Random(UUID.randomUUID().hashCode());
                    if (random.nextBoolean()) {
                        addedOne = true;
                        CounterInfo counterInfo = subs.get(i);
                        CounterInfo newCounterInfo = new CounterInfo(counterInfo.getName(), counterInfo.getCounter());
                        to.getSubs().add(newCounterInfo);

                        chanceCopySubs(counterInfo, newCounterInfo);
                    }
                }
            }
        }
    }

    private void chanceCopySubs(CounterInfo from, CounterInfo to) {
        ArrayList<CounterInfo> subs = from.getSubs();
        if (!subs.isEmpty()) {
            boolean addedOne = false;

            // Add minimum one, otherwise parsing the values can go wrong if headers are missing at the last level.
            while (!addedOne) {
                for (int i = 0; i != subs.size(); i++) {
                    Random random = new Random(UUID.randomUUID().hashCode());
                    if (random.nextBoolean()) {
                        addedOne = true;
                        CounterInfo counterInfo = subs.get(i);
                        CounterInfo newCounterInfo = new CounterInfo(counterInfo.getName(), counterInfo.getCounter());
                        to.getSubs().add(newCounterInfo);

                        chanceCopySubs(counterInfo, newCounterInfo);
                    }
                }
            }
        }
    }

    /**
     * Checks if the counters are valid.
     *
     * @param counters
     * @throws NumberFormatException
     * @throws Exception
     */
    private void parseCounters(String counters) throws Exception {
        Entities entities = this.gson.fromJson(counters, Entities.class);

        String warning = entities.validateCounters();
        if (warning.length() != 0) {
            System.out.println("Test " + this.id + " " + warning);
        }

        if (entities.hasDuplicateNames()) {
            throw new Exception("The Entities contain duplicate Entity or CounterInfo names. CounterInfo names must be unique for the level the CounterInfos are on.");
        }

        if (entities.getDeepCount() != this.wiwEntities.getDeepCount()) {
            throw new Exception("The number of counters (" + entities.size() + ") is not equal to the number of CounterInfos (" + wiwEntities.size() + ").");
        }

        ArrayList<String> parsedCounters = new ArrayList<String>();

        if (!wiwEntities.match(entities, false)) {
            throw new Exception("The counter Entities do not match the wiw Entities.");
        }

        parsedCounters.addAll(entities.getCountersLastLevel());

        if (this.verbose) {
            DateFormat df = new SimpleDateFormat("HH:mm:ss");
            Date time = Calendar.getInstance().getTime();
            System.out.println("Test " + this.id + " Parsed (" + df.format(time) + "): " + Combiner.combine(parsedCounters, " "));
        }
    }
}
