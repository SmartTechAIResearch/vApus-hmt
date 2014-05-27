/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package be.sizingservers.vapus.hmt.agent.cpu;

import be.sizingservers.vapus.agent.Agent;
import be.sizingservers.vapus.agent.util.Directory;
import be.sizingservers.vapus.agent.util.Entities;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.logging.Level;

/**
 *
 * @author Didjeeh
 */
public abstract class CPU {

    protected String vendor;
    protected final long family, model;
    protected int logicalCores, logicalCoresPerPackage, physicalCores, packages;

    protected Entities wih, wiw, wiwWithCounters;

    public CPU(long family, long model) {
        this.family = family;
        this.model = model;

        try {
            HMTProxy.INSTANCE.init(Directory.getExecutingDirectory(CPUProvider.class));
        } catch (URISyntaxException ex) {
            Agent.getLogger().log(Level.SEVERE, "Could not init HMTProxy: {0}", ex);
        }
        this.logicalCores = HMTProxy.INSTANCE.getLogicalCores();
        this.logicalCoresPerPackage = HMTProxy.INSTANCE.getLogicalCoresPerPackage();
        this.physicalCores = HMTProxy.INSTANCE.getPhysicalCores();
        this.packages = HMTProxy.INSTANCE.getPackages();
    }

    abstract Entities getWDYH();

    public void setWiw(Entities wiw) {
        this.wiw = wiw;
        this.wiwWithCounters = wiw.safeClone();
    }

    abstract Entities getWiwWithCounters();

    public long getFamily() {
        return this.family;
    }

    public long getModel() {
        return this.model;
    }

    abstract float getBusClockFrequencyInMhz();

    /**
     *
     * @param msr
     * @return
     * @throws Exception
     */
    protected BigInteger readMSR(long msr) throws Exception {
        return readMSR(msr, 64, 0);
    }

    /**
     *
     * @param msr
     * @param highBit
     * @param lowBit
     * @return
     * @throws Exception
     */
    protected BigInteger readMSR(long msr, int highBit, int lowBit) throws Exception {
        String error = HMTProxy.INSTANCE.readMSR(msr, highBit, lowBit);
        if (error.length() != 0) {
            throw new Exception(error);
        }

        return fromEAX_EDX(highBit, lowBit);
    }

    /**
     *
     * @param msr
     * @param core
     * @return
     * @throws Exception
     */
    protected BigInteger readMSR(long msr, int core) throws Exception {
        return readMSR(msr, 64, 0, core);
    }

    /**
     *
     * @param msr
     * @param highBit
     * @param lowBit
     * @param core
     * @return
     * @throws Exception If the MSR could not be read.
     */
    protected BigInteger readMSR(long msr, int highBit, int lowBit, int core) throws Exception {
        String error = HMTProxy.INSTANCE.readMSRTx(msr, highBit, lowBit, core);
        if (error.length() != 0) {
            throw new Exception(error);
        }

        return fromEAX_EDX(highBit, lowBit);
    }

    private BigInteger fromEAX_EDX(int highBit, int lowBit) {
        BigInteger eax = BigInteger.valueOf(HMTProxy.INSTANCE.getMSREAX());
        BigInteger edx = BigInteger.valueOf(HMTProxy.INSTANCE.getMSREDX());

        BigInteger value = edx.shiftLeft(32).or(eax);

        //check if we need to do some parsing of bits to get what we want
        if (highBit == 64 && lowBit == 0) {
            return value;
        }

        //construct the ulong with the bits we're interested in
        BigInteger bits = BigInteger.ZERO;
        for (int i = lowBit; i < highBit; i++) {
            bits.add(BigInteger.valueOf(2).pow(i));
        }

        return (value.and(bits)).shiftRight(lowBit);
    }

    /**
     * 
     * @param msr
     * @param value
     * @throws Exception 
     */
    protected void writeMSR(long msr, BigInteger value) throws Exception {
        long edx = getEDX(value);
        long eax = getEAX(value, edx);

        String error = HMTProxy.INSTANCE.writeMSR(msr, eax, edx);
        if (error.length() != 0) {
            throw new Exception(error);
        }
    }

    /**
     * 
     * @param msr
     * @param value
     * @param core
     * @throws Exception 
     */
    protected void writeMSR(long msr, BigInteger value, int core) throws Exception {
        long edx = getEDX(value);
        long eax = getEAX(value, edx);

        String error = HMTProxy.INSTANCE.writeMSRTx(msr, eax, edx, core);
        if (error.length() != 0) {
            throw new Exception(error);
        }
    }

    private long getEDX(BigInteger value) {
        return value.shiftRight(32).longValue();
    }

    private long getEAX(BigInteger value, long edx) {
        return value.subtract(BigInteger.valueOf(edx).shiftLeft(32)).longValue();
    }
}
