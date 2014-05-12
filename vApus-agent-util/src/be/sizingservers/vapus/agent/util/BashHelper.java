/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * A helper class that handles bash commands.
 *
 * @author didjeeh
 */
public class BashHelper {

    private static String bash = "/bin/bash";
    private static boolean allowStopProcessGracefully;

    /**
     * @return the bash
     */
    public static String getBash() {
        return BashHelper.bash;
    }

    /**
     * Set this if bash is not located in /bin (default: /bin/bash)
     *
     * @param bash the bash to set
     */
    public static void setBash(String bash) {
        BashHelper.bash = bash;
    }

    /**
     * @return
     */
    public static boolean getAllowStopProcessGracefully() {
        return BashHelper.allowStopProcessGracefully;
    }

    /**
     * This allowStopProcessGracefully is used in stopProcess(...).
     *
     * If allowStopProcessGracefully == true, there is a chance that a race
     * condition occurs when calling stopProcess(...). Meaning that the wrong
     * process (with the same name) can get closed, if you have multiple java
     * apps running calling that fx.
     *
     * If you only have one app running at a time that calls stopProcess(...),
     * you can safely set this to true, otherwise set it it to false (default).
     *
     * Nothing to be done about it I guess, wait for Oracle to add a pid
     * property to java.io.Process.
     *
     * @param allowStopProcessGracefully
     */
    public static void setAllowStopProcessGracefully(boolean allowStopProcessGracefully) {
        BashHelper.allowStopProcessGracefully = allowStopProcessGracefully;
    }

    /**
     * Returns the output of a bash command (or an empty String) and stops the
     * process gracefully (if possible).
     *
     * @param command
     * @return
     * @throws java.io.IOException
     */
    public static String getOutput(String command) throws IOException {
        Process p = runCommand(command);
        String pid = getPIDExecutedCommand(command);

        String output = getOutput(p);

        stopProcess(p, pid);

        return output;
    }

    /**
     * Gets the output of a process without stopping it.
     *
     * @param p
     * @return The text for the input stream of the given process or an empty
     * String.
     * @throws IOException
     */
    public static String getOutput(Process p) throws IOException {
        String output = getText(p.getInputStream());

        return output;
    }

    /**
     * Executes a command and stops it gracefully (if possible) afer the given
     * wait. (SIGTERM)
     *
     * @param command A bash command that outputs to a file.
     * @param file The file where to the command has written.
     * @param waitForOutputInMillis Wait this amount of time before reading the
     * file.
     * @return The contents of the file or an empty String.
     * @throws IOException
     * @throws InterruptedException
     */
    public static String getOutput(String command, String file, int waitForOutputInMillis) throws IOException, InterruptedException {
        Process p = runCommand(command);
        String pid = getPIDExecutedCommand(command);
        Thread.sleep(waitForOutputInMillis);

        String output = readFile(file);

        stopProcess(p, pid);

        return output;
    }

    /**
     *
     * @param file
     * @return The contents of the file or an empty String.
     * @throws IOException
     */
    public static String readFile(String file) throws IOException {
        return getText(new FileInputStream(new File(file)));
    }

    /**
     *
     * @param stream The input stream of a bash command for instance.
     * @return The text for the input stream or an empty String.
     * @throws IOException
     */
    public static String getText(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        ArrayList al = getLines(stream);

        for (int i = 0; i != al.size(); i++) {
            sb.append(al.get(i));
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     *
     * @param stream The input stream of a bash command for instance. It is read
     * using the systems default encoding.
     * @return
     * @throws IOException
     */
    public static ArrayList<String> getLines(InputStream stream) throws IOException {
        Scanner scan = new Scanner(new InputStreamReader(stream, "UTF8"));
        ArrayList<String> al = new ArrayList<String>();

        while (scan.hasNextLine()) {
            al.add(scan.nextLine());
        }
        return al;
    }

    /**
     * Do not forget to stop the process when you don't need it anymore. (Call
     * getPIDLastExecutedCommand() after this, call stopExecutedCommand(String
     * pid))
     *
     * @param command
     * @return
     * @throws IOException
     */
    public static Process runCommand(String command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(BashHelper.bash, "-c", command);
        builder.redirectErrorStream(true);
        return builder.start();
    }

    /**
     * Tries to gracefully stop the executed command. (SIGTERM) If this was not
     * possible, after 2 seconds, the process is killed instead.
     *
     * setAllowStopProcessGracefully(...) to allow this.
     * 
     * @param p
     * @param pid
     * @return 
     */
    public static boolean stopProcess(Process p, String pid) {
        return stopProcess(p, pid, 2000);
    }

    /**
     * Tries to gracefully stop the executed command. (SIGTERM) If this was not
     * possible, the process is killed instead.
     *
     * setAllowStopProcessGracefully(...) to allow this.
     *
     * @param p
     * @param pid
     * @param processExitTimeout In millis.
     * @return True when gracefully stopped.
     */
    public static boolean stopProcess(Process p, String pid, int processExitTimeout) {
        if (BashHelper.allowStopProcessGracefully) {
            try {
                if (pid != null) {
                    stopExecutedCommand(pid);

                    int slept = 0;
                    String[] pids;
                    do {
                        ProcessBuilder builder = new ProcessBuilder(BashHelper.bash, "-c", "ps aux | grep -w " + pid + " | grep -v grep | awk '{print $2}'");
                        builder.redirectErrorStream(true);

                        pids = getOutput(builder.start()).split("\\r?\\n");
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            break;
                        }
                    } while (pids.length != 0 && (slept += 100) != processExitTimeout);

                    if (pids.length != 0) {
                        return true;
                    }
                }
            } catch (IOException ex) {
            }
        }
        p.destroy();
        return false;
    }

    /**
     * Gracefully stops the executed command with given pid. (SIGTERM)
     *
     * @param pid
     * @throws IOException
     */
    public static void stopExecutedCommand(String pid) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(BashHelper.bash, "-c", "kill -s SIGTERM " + pid);
        builder.redirectErrorStream(true);

        builder.start();
    }

    /**
     * The last pid of the given command is returned. Call this directly after
     * runCommand(...).
     *
     * Remarks: there is a chance that a race condition occurs. Meaning that the
     * wrong process (with the same name) can get closed, if you have multiple
     * java apps running calling this fx.
     *
     * Nothing to be done about it I guess, wait for Oracle to add a pid
     * property to java.io.Process.
     *
     * @param command
     * @return The pid or null if the pid for the given command does not exist.
     * @throws IOException
     */
    public static String getPIDExecutedCommand(String command) throws IOException {
        String processName = command;
        if (processName.contains(" ")) {
            processName = processName.substring(0, processName.indexOf(' '));
        }

        ProcessBuilder builder = new ProcessBuilder(BashHelper.bash, "-c", "ps aux | grep -w " + processName + " | grep -v grep | awk '{print $2}'");
        builder.redirectErrorStream(true);

        String[] pids = getOutput(builder.start()).split("\\r?\\n");
        String pid = pids[pids.length - 1].trim();
        if (pid.length() != 0) {
            return pid;
        }

        return null;
    }
}
