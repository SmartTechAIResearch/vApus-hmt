/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent.tester;

import be.sizingservers.vapus.agent.util.PropertyHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

/**
 *
 * @author didjeeh
 */
public class AgentTester {

    /**
     * @param args The command line arguments.
     */
    public static void main(String[] args) {
        try {

            //Get and parse all the properties.
            Properties properties = PropertyHelper.getProperties("/vApus-agent-tester.properties");

            String version = properties.getProperty("version");
            System.out.println("vApus-agent-tester " + version);

            System.out.println("Testing with properties: " + properties.toString());

            boolean verbose = false;
            if (properties.containsKey("verbosity")) {
                String verbosity = properties.getProperty("verbosity");
                verbose = verbosity.equals("verbose");
            }

            String[] ipsOrHostNames = properties.getProperty("ipsOrHostNames").split(",");
            if (ipsOrHostNames.length == 0) {
                throw new Exception("ipsOrHostNames must have a value assigned.");
            }
            int port = Integer.parseInt(properties.getProperty("port"));

            int[] testCounts = new int[]{ipsOrHostNames.length};
            if (properties.containsKey("testCounts")) {
                String[] arr = properties.getProperty("testCounts").split(",");
                testCounts = new int[arr.length];
                for (int i = 0; i != arr.length; i++) {
                    int testCount = Integer.parseInt(arr[i]);
                    if (testCount < 1) {
                        throw new Exception("testCounts cannot contain a value smaller than 1.");
                    }
                    testCounts[i] = testCount;
                }
            }

            int[] repeats = new int[]{1};
            if (properties.containsKey("repeats")) {
                String[] arr = properties.getProperty("repeats").split(",");
                repeats = new int[arr.length];
                for (int i = 0; i != arr.length; i++) {
                    int repeat = Integer.parseInt(arr[i]);
                    if (repeat < 0) {
                        throw new Exception("repeat cannot contain a value smaller than 0.");
                    }
                    repeats[i] = repeat;
                }
            }

            if (testCounts.length != repeats.length) {
                throw new Exception("testCounts must have as many values as repeats has.");
            }

            for (int i = 0; i != testCounts.length; i++) {
                int testCount = testCounts[i];
                int repeat = repeats[i];
                for (int k = 0; k <= repeat; k++) {
                    //Start tests in parallel.
                    startTests(verbose, ipsOrHostNames, port, testCount, k);
                }
            }

            System.out.println("--- Done ---");
        } catch (IOException ex) {
            System.err.println("Failed reading /vApus-agent-tester.properties: " + ex);
        } catch (Exception ex) {
            System.err.println("Failed reading /vApus-agent-tester.properties: " + ex);
        }
    }

    private static void startTests(boolean verbose, String[] ipsOrHostNames, int port, int testCount, int repeat) {
        String start = "--- Starting ";
        if (testCount == 1) {
            start += "test ";
        } else {
            start += testCount + " tests in parallel ";
        }
        if (repeat != 0) {
            start += "(repeat " + repeat + ")";
        }
        start += "---";

        System.out.println(start);

        ArrayList<Test> tests = new ArrayList<Test>(testCount);
        for (int i = 1; i <= testCount; i++) {
            //Determine a new ip from the array.
            int ipIndex = i;
            while (ipIndex >= ipsOrHostNames.length) {
                ipIndex -= ipsOrHostNames.length;
            }
            tests.add(startTest(i, ipsOrHostNames[ipIndex], port, verbose));
        }

        for (int i = 0; i != tests.size(); i++) {
            try {
                tests.get(i).join();
            } catch (InterruptedException ex) {
                System.err.println("Failed joining test " + i + " thread : " + ex);
            }
        }
    }

    private static Test startTest(int id, String ip, int port, boolean verbose) {
        Test test = new Test(id, ip, port, verbose);
        test.start();
        return test;
    }
}
