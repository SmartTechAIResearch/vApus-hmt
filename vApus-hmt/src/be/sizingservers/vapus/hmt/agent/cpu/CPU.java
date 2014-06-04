/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
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

    protected Entities wih;

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

    public abstract Entities getWDYH();

    public abstract Entities getWIWWithCounters(Entities wiw) throws Exception;

    public long getFamily() {
        return this.family;
    }

    public long getModel() {
        return this.model;
    }

    public boolean getOSIsWindows() {
        return System.getProperty("os.name").startsWith("Windows");
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
        String error = HMTProxy.INSTANCE.readMSR(msr);
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
     * @param highBit exclusive!
     * @param lowBit inclusive!
     * @param core
     * @return
     * @throws Exception If the MSR could not be read.
     */
    protected BigInteger readMSR(long msr, int highBit, int lowBit, int core) throws Exception {
        String error = HMTProxy.INSTANCE.readMSRTx(msr, core);
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
            bits = bits.add(BigInteger.valueOf(2).pow(i));
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

    /**
     * handle overflow, taking unsigned long long max as upper bound.
     *
     * @param newValue
     * @param oldValue
     * @return
     */
    protected BigInteger getDifferenceBetweenValues(BigInteger newValue, BigInteger oldValue) {
        if (oldValue.compareTo(newValue) > 0) {
            return BigInteger.valueOf(2).pow(64).subtract(oldValue).add(newValue);
        }
        return newValue.subtract(oldValue);
    }
}
