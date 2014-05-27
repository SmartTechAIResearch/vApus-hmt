/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package be.sizingservers.vapus.hmt.agent.cpu;

import com.sun.jna.Library;
import com.sun.jna.Native;
import java.math.BigInteger;

/**
 *
 * @author Didjeeh
 */
public interface HMTProxy extends Library {
    //HMTProxy INSTANCE = (HMTProxy) Native.loadLibrary(System.getProperty("os.name").startsWith("Windows") ? "/HMTProxy.dll" : "/HMTProxy.so", HMTProxy.class);

    HMTProxy INSTANCE = (HMTProxy) Native.loadLibrary("/HMTProxy.dll", HMTProxy.class);
    
    public void init(String resolvePath);
    
    public String getLastError();
    
    public int getLogicalCores();
    public int getLogicalCoresPerPackage();
    public int getPhysicalCores();
    public int getPackages();
    
    public long getMSREAX();
    public long getMSREDX();
   
    /**
     * 
     * @param msr
     * @param highBit
     * @param lowBit
     * @param core Set thread affinity.
     * @return 
     */
    public String readMSRTx(long msr, int highBit, int lowBit, int core);
    public String readMSR(long msr, int highBit, int lowBit);
    
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
    
    public long getPciAddress(byte bus, byte device, byte function);
    public long readPciConfig(long pciAddress, long regAddress);
    /**
     * 
     * @param pciAddress
     * @param regAddress
     * @param value
     * @return error if any
     */
    public String writePciConfig(long pciAddress, long regAddress, long value);
}
