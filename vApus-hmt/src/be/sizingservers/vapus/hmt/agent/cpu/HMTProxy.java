/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.hmt.agent.cpu;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * A JNA proxy to either a Windows or a Linux binary based on the os name.
 * Provides functionality to get basic cpu info, read and write MSRs and PCI device registers, and get acpi c states (+ Win Freqs).
 * @author Didjeeh
 */
public interface HMTProxy extends Library {
    HMTProxy INSTANCE = (HMTProxy) Native.loadLibrary(System.getProperty("os.name").startsWith("Windows") ? "/HMTProxy.dll" : "/HMTProxy.so", HMTProxy.class);

    //HMTProxy INSTANCE = (HMTProxy) Native.loadLibrary("/HMTProxy.dll", HMTProxy.class);
    
    public void init(String resolvePath);
    
    public int getLogicalCores();
    public int getPhysicalCores();
    public int getPackages();
    
    public long getMSREAX();
    public long getMSREDX();
   
    /**
     * 
     * @param msr
     * @param core Set thread affinity.
     * @return 
     */
    public String readMSRTx(long msr, int core);
    public String readMSR(long msr);
    
    /**
     * 
     * @param msr
     * @param eax
     * @param edx
     * @param core Set thread affinity.
     * @return error if any
     */
    public String writeMSRTx(long msr, long eax, long edx, int core);
    /**
     * 
     * @param msr
     * @param eax
     * @param edx
     * @return error if any
     */
    public String writeMSR(long msr, long eax, long edx);
    
    /**
     * 
     * @param bus
     * @param device
     * @param function
     * @param regAddress
     * @return 
     */
    public long readPciConfig(int bus, int device, int function, long regAddress);
    /**
     * 
     * @param bus
     * @param device
     * @param function
     * @param regAddress
     * @param value
     * @return 
     */
    public String writePciConfig(int bus, int device, int function, long regAddress, long value);
    
    /**
     * 
     * @param core The physical core.
     * @param hyperThreading
     * @return 
     */
    public int getACPICState(int core, boolean hyperThreading);
    /**
     * Calling this will not work on Linux obviously.
     * @param core The physical core.
     * @param hyperThreading
     * @return 
     */
    public int getWindowsFrequency(int core, boolean hyperThreading);
}
