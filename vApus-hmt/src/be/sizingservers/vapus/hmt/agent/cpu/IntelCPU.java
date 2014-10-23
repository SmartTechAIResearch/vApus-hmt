/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.hmt.agent.cpu;

import be.sizingservers.vapus.agent.util.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

/**
 * Nehalem, Westmere, SandyBridge and IvyBridge supported.
 *
 * @author Didjeeh
 */
public class IntelCPU extends CPU {

    private boolean sandyBridge, ivyBridge;

    private final boolean hyperThreadingEnabled;

    private final ArrayList<Integer> packageRepresentativeCores;

    private final float baseFrequency, joulesPerEnergyUnit;
    private final long maxProcessorTemp; //ProcessorHot temp. From this point the core will be throtled (= maximum temp)

    private final boolean pp1Available; //Possible not available for servers (graphics core).
    private final boolean dramAvailable; //Only available for servers.

    private final boolean packageTemperatureAvailable;

    //To monitor the elapsed milliseconds between runs
    private long prevTimeStamp;

    private final BigInteger[] old_unhaltedCoreCycles;
    private final BigInteger[] old_unhaltedRefCycles;
    private final BigInteger[] old_core_C3;
    private final BigInteger[] old_core_C6;
    private final BigInteger[] old_timestampctr;
    private final BigInteger[] old_core_C7;
    private final BigInteger[] old_pkg_C2;
    private final BigInteger[] old_pkg_C3;
    private final BigInteger[] old_pkg_C6;
    private final BigInteger[] old_pkg_C7;
    private final BigInteger[] old_energy;
    private final BigInteger[] old_pp0;
    private final BigInteger[] old_pp1;
    private final BigInteger[] old_dram;

    public IntelCPU(long family, long model) throws Exception {
        super(family, model);

        determineArchitecture();

        try {
            initMSRs();
        } catch (Exception ex) {
        }

        this.hyperThreadingEnabled = this.logicalCores > this.physicalCores;

        this.packageRepresentativeCores = new ArrayList<Integer>();
        for (int p = 0; p != super.packages; p++) {
            this.packageRepresentativeCores.add(p * (super.physicalCores / super.packages));
        }

        this.baseFrequency = getBaseFrequency();

        this.joulesPerEnergyUnit = getJoulesPerEnergyUnit();

        this.maxProcessorTemp = super.readMSR(Registers.MSR_IA32_TEMPERATURE_TARGET, 23, 16).longValue();

        this.pp1Available = getPP1Available();
        this.dramAvailable = getDRAMAvailable();

        this.packageTemperatureAvailable = getPackageTemperatureAvailable();

        this.old_unhaltedCoreCycles = getBigIntegerArray(this.physicalCores);
        this.old_unhaltedRefCycles = getBigIntegerArray(this.physicalCores);

        this.old_core_C3 = getBigIntegerArray(this.physicalCores);
        this.old_core_C6 = getBigIntegerArray(this.physicalCores);
        this.old_core_C7 = getBigIntegerArray(this.physicalCores);
        this.old_timestampctr = getBigIntegerArray(this.physicalCores);

        this.old_pkg_C2 = getBigIntegerArray(this.packages);
        this.old_pkg_C3 = getBigIntegerArray(this.packages);
        this.old_pkg_C6 = getBigIntegerArray(this.packages);
        this.old_pkg_C7 = getBigIntegerArray(this.packages);
        this.old_energy = getBigIntegerArray(this.packages);
        this.old_pp0 = getBigIntegerArray(this.packages);
        this.old_pp1 = getBigIntegerArray(this.packages);
        this.old_dram = getBigIntegerArray(this.packages);

//        getWIWWithCounters(getWDYH());
//        Thread.sleep(1000);
//        getWIWWithCounters(getWDYH());
    }

    private void determineArchitecture() {
        //All the CPU ID model numbers can be found at http://software.intel.com/en-us/articles/intel-processor-identification-with-cpuid-model-and-family-numbers/
        if (this.model == 0x1E || this.model == 0x1A || this.model == 0x2E) {
            //Nehalem
        } else if (this.model == 0x25 || this.model == 0x2C || this.model == 0x2F) {
            //Westmere
        } else if (this.model == 0x2A || this.model == 0x2D) {
            this.sandyBridge = true;
        } else if (this.model == 0x3A || this.model == 0x3E || this.model == 0x3F) {
            this.ivyBridge = true;
        }
        //else not supported.
    }

    private void initMSRs() throws Exception {
        //enable all (4 programmable + 3 fixed) counters
        BigInteger value = BigInteger.ONE
                .add(BigInteger.ONE.shiftLeft(1))
                .add(BigInteger.ONE.shiftLeft(2))
                .add(BigInteger.ONE.shiftLeft(3))
                .add(BigInteger.ONE.shiftLeft(32))
                .add(BigInteger.ONE.shiftLeft(33))
                .add(BigInteger.ONE.shiftLeft(34));
        super.writeMSR(Registers.MSR_IA32_PERF_GLOBAL_CTRL, value, 0);

        BigInteger fixed_ctr_ctl = BigInteger.valueOf(819);
        BigInteger perf_global_ctrl = new BigInteger("30064771075");

        //LLC Misses
        //event number 0x2E
        //umask 0x41
        BigInteger eventValue = BigInteger.valueOf(0x2E)
                .add(BigInteger.valueOf(0x41).shiftLeft(8)) //valuemask //shift 8 bits
                .add(BigInteger.ONE.shiftLeft(16)) //usr
                .add(BigInteger.ONE.shiftLeft(17)) //os
                .add(BigInteger.ZERO.shiftLeft(18)) //edge detection
                .add(BigInteger.ZERO.shiftLeft(19)) //pin control
                .add(BigInteger.ZERO.shiftLeft(20)) //apic int
                .add(BigInteger.ZERO.shiftLeft(21))//any thread
                .add(BigInteger.ONE.shiftLeft(22)) //enables the corresponding performance counter
                .add(BigInteger.ZERO.shiftLeft(23)) //don't invert the following cmask
                .add(BigInteger.ZERO.shiftLeft(24)); //cmask

        //Enable fixed Architectural Performance Monitor counters 1 and 2 in the Global Performance Counter Control
        // IA32_PERF_GLOBAL_CTRL(38FH - 911) and the Fixed-Function Performance Counter Control IA32_FIXED_CTR_CTL (38DH).
        //enable bits are on position 0 & 1 = 0000 0000 0000 0011 = 3
        //Repeat this step for each logical processor in the system
        for (int core = 0; core != super.physicalCores; core++) {
            super.writeMSR(Registers.MSR_IA32_FIXED_CTR_CTL, fixed_ctr_ctl, core);
            super.writeMSR(Registers.MSR_IA32_PERF_GLOBAL_CTRL, perf_global_ctrl, core);

            super.writeMSR(Registers.MSR_IA32_PERFEVTSEL0_ADDR, eventValue, core);
            super.writeMSR(Registers.MSR_IA32_PMC0, BigInteger.ZERO, core);
        }
    }

    /**
     * Reads out the max non-turbo multiplier of the processor and multiplies it
     * with the bus clock frequency
     *
     * @param core
     * @return the max non-turbo frequency
     * @throws Exception
     */
    private float getBaseFrequency() throws Exception {
        float baseOperatingRatio = (float) super.readMSR(Registers.MSR_PLATFORM_INFO, 16, 8).longValue();
        if (baseOperatingRatio < 2f) {
            //error
        }

        return baseOperatingRatio * getBusClockFrequencyInMhz();
    }

    /**
     * Gets the amount of joules which correspond with 1 Energy Unit (= the unit
     * which is used in the MSR's, can be variable)
     *
     * @return The number of joules which correspond with 1 Energy Unit
     * @throws Exception
     */
    private float getJoulesPerEnergyUnit() throws Exception {
        long energyStatusUnits = 0L;

        try {
            energyStatusUnits = super.readMSR(Registers.MSR_RAPL_POWER_UNIT, 13, 8).longValue();
        } catch (Exception ex) {
            //Ignore
        }

        return 1f / (float) (1 << energyStatusUnits); //is the same as (1/2)^energy_status_units
    }

    private boolean getPP1Available() {
        try {
            super.readMSR(Registers.MSR_PP1_ENERGY_STATUS, 32, 0, 0);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean getDRAMAvailable() {
        try {
            super.readMSR(Registers.MSR_DRAM_ENERGY_STATUS, 32, 0, 0);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean getPackageTemperatureAvailable() {
    // http://www.intel.com/content/dam/www/public/us/en/documents/manuals/64-ia-32-architectures-software-developer-vol-3b-part-2-manual.pdf 1.4.8

        //first we need to check if the sensor is available (CPUID.06H:EAX[bit 6])
        CPUIDProxy.INSTANCE.load(0x6);
        long eax = CPUIDProxy.INSTANCE.EAX();
        return ((eax & 0x20) >> 5) == 1;
    }

    /**
     * Initiates an array containing BigInteger.ZERO entries.
     *
     * @param size
     * @return
     */
    private BigInteger[] getBigIntegerArray(int size) {
        BigInteger[] arr = new BigInteger[size];
        for (int i = 0; i != size; i++) {
            arr[i] = BigInteger.ZERO;
        }
        return arr;
    }

    @Override
    protected float getBusClockFrequencyInMhz() {
        if (this.sandyBridge || this.ivyBridge) {
            return 100f;
        }
        return 133.33f;
    }

    @Override
    public Entities getWDYH() {
        if (super.wih == null) {
            super.wih = new Entities();

            Entity entity = new Entity(HostName.get(), true);

            for (int core = 0; core != super.physicalCores; core++) {
                CounterInfo counterInfo = new CounterInfo("Physical core " + core);
                counterInfo.getSubs().add(new CounterInfo("C0(%)"));
                counterInfo.getSubs().add(new CounterInfo("C1(%)"));
                counterInfo.getSubs().add(new CounterInfo("C3(%)"));
                counterInfo.getSubs().add(new CounterInfo("C6(%)"));

                if (this.sandyBridge || this.ivyBridge) {
                    counterInfo.getSubs().add(new CounterInfo("C7(%)"));
                }

                counterInfo.getSubs().add(new CounterInfo("ACPI C State"));

                if (super.getOSIsWindows()) {
                    counterInfo.getSubs().add(new CounterInfo("Windows Frequency(Mhz)"));
                }

                if (getCoreTemperatureAvailable(core)) {
                    counterInfo.getSubs().add(new CounterInfo("Temp(C)"));
                }

                entity.getSubs().add(counterInfo);
            }

            for (int p = 0; p != super.packages; p++) {
                CounterInfo counterInfo = new CounterInfo("Package " + p);
                counterInfo.getSubs().add(new CounterInfo("Multiplier"));
                counterInfo.getSubs().add(new CounterInfo("Frequency(Mhz)"));

                if (this.sandyBridge || this.ivyBridge) {
                    counterInfo.getSubs().add(new CounterInfo("PC2(%)"));
                }

                counterInfo.getSubs().add(new CounterInfo("PC3(%)"));
                counterInfo.getSubs().add(new CounterInfo("PC6(%)"));

                if (this.sandyBridge || this.ivyBridge) {
                    counterInfo.getSubs().add(new CounterInfo("PC7(%)"));
                    counterInfo.getSubs().add(new CounterInfo("Energy(W)"));
                    counterInfo.getSubs().add(new CounterInfo("PP0(W)"));

                    if (this.pp1Available) {
                        counterInfo.getSubs().add(new CounterInfo("PP1(W)"));
                    }

                    if (this.dramAvailable) {
                        counterInfo.getSubs().add(new CounterInfo("DRAM(W)"));
                    }
                }

                if (this.packageTemperatureAvailable) {
                    counterInfo.getSubs().add(new CounterInfo("Temp(C)"));
                }

                entity.getSubs().add(counterInfo);
            }

            super.wih.add(entity);
        }
        return super.wih;
    }

    private boolean getCoreTemperatureAvailable(int core) {
        try {
            return super.readMSR(Registers.MSR_IA32_THERM_STATUS, 32, 31, core).compareTo(BigInteger.ONE) == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Entities getWIWWithCounters(Entities wiw) throws Exception {
        Entities wiwWithCounters = wiw.safeClone();
        Entity entity = wiwWithCounters.get(0);

        HashMap<Integer, BigInteger> currentTimestampCtrs = new HashMap<Integer, BigInteger>();
        ArrayList<Integer> updatedOldTimestampCtrs = new ArrayList<Integer>(); //Holds the physical cores

        for (int i = 0; i != entity.getSubs().size(); i++) {
            CounterInfo info = entity.getSubs().get(i);
            String name = info.getName();

            int core = -1;
            //If the timestampctr cannot be read, c states cannot be calculated.
            BigInteger timestampctr = BigInteger.ZERO;

            if (name.startsWith("Physical core ")) {
                core = new Integer(name.substring("physical core ".length()));

                timestampctr = getTimestampCtr(core, currentTimestampCtrs);

                calculateCoreCStates(core, timestampctr, info);
                calculateOtherCoreStuff(core, info);

            } else if (name.startsWith("Package ")) {
                int packageIndex = new Integer(name.substring("package ".length()));
                core = this.packageRepresentativeCores.get(packageIndex);

                timestampctr = getTimestampCtr(core, currentTimestampCtrs);

                calculatePackageCStates(packageIndex, core, timestampctr, info);
                calculateOtherPackageStuff(packageIndex, core, info);
            }

            if (core != -1) {
                if (updatedOldTimestampCtrs.contains(core)) {
                    this.old_timestampctr[core] = timestampctr;
                } else {
                    updatedOldTimestampCtrs.add(core);
                }
            }
        }

        //For reading the other stuff.
        this.prevTimeStamp = Calendar.getInstance().getTimeInMillis();

        return wiwWithCounters;
    }

    private BigInteger getTimestampCtr(int core, HashMap<Integer, BigInteger> currentTimestampCtrs) throws Exception {
        if (currentTimestampCtrs.containsKey(core)) {
            return currentTimestampCtrs.get(core);
        } else {
            BigInteger timestampctr = super.readMSR(Registers.MSR_TSC, core);
            currentTimestampCtrs.put(core, timestampctr);
            return timestampctr;
        }
    }

    private void calculateCoreCStates(int core, BigInteger timestampctr, CounterInfo info) throws Exception {
        if (!timestampctr.equals(BigInteger.ZERO)) {
            long diffTimestamp = getDifferenceBetweenValues(timestampctr, this.old_timestampctr[core]).longValue();

            float c0Time = -2f, c1Time = -2f, c3Time = -2f, c6Time = -2f, c7Time = -2f;

            for (int i = 0; i != info.getSubs().size(); i++) {
                CounterInfo sub = info.getSubs().get(i);
                String name = sub.getName();

                if (name.equalsIgnoreCase("C0(%)")) {
                    c0Time = calculateC0Time(core, diffTimestamp);
                    sub.setCounter(c0Time);
                } else if (name.equalsIgnoreCase("C1(%)")) {
                    //the C states > C0 are states where the core is halted.. so this amount of cycles
                    //can be calculated with (1 - (unhalted ref cycles / tsc cycles)) <-- http://software.intel.com/en-us/articles/measuring-the-halted-state/
                    //Please note that % C3 and % C6 (and greater...) is also included in this amount
                    //So I've chosen to extract the C1 time of the calculation; (total time (= 1) - c0 - c3 - c6 - ...) = c1 time 
                    if (c0Time == -2f) {
                        c0Time = calculateC0Time(core, diffTimestamp);
                    }
                    c3Time = calculateC3Time(core, diffTimestamp);
                    c6Time = calculateC6Time(core, diffTimestamp);

                    if (c0Time != -1f && c3Time != -1f && c6Time != -1f) {
                        c1Time = 100f - c0Time - c3Time - c6Time;

                        if (this.sandyBridge || this.ivyBridge) {
                            c7Time = calculateC7Time(core, diffTimestamp);
                            if (c7Time == -1f) {
                                c1Time = -1f;
                            } else {
                                c1Time -= c7Time;
                            }
                        }
                    }

                    sub.setCounter(c1Time);
                } else if (name.equalsIgnoreCase("C3(%)")) {
                    if (c3Time == -2f) {
                        c3Time = calculateC3Time(core, diffTimestamp);
                    }
                    sub.setCounter(c3Time);
                } else if (name.equalsIgnoreCase("C6(%)")) {
                    if (c6Time == -2f) {
                        c6Time = calculateC6Time(core, diffTimestamp);
                    }
                    sub.setCounter(c6Time);
                } else if (name.equalsIgnoreCase("C7(%)")) {
                    if (c7Time == -2f) {
                        c7Time = calculateC7Time(core, diffTimestamp);
                    }
                    sub.setCounter(c7Time);
                }
            }
        }
    }

    private float calculateC0Time(int core, long diffTimestamp) throws Exception {
        BigInteger unhaltedRefCycles = super.readMSR(Registers.MSR_IA32_FIXED_CTR2, core);
        float diff = (float) getDifferenceBetweenValues(unhaltedRefCycles, this.old_unhaltedRefCycles[core]).longValue();
        //saving values for next loop, so the difference can be made.
        this.old_unhaltedRefCycles[core] = unhaltedRefCycles;

        //C0 time
        //read the Fixed-Function Performance Counter 2 IA32_FIXED_CTR2 (30BH) and the TCS (10H - 16)
        //these things have already been computed above us.
        //%C0time = unhalted ref cycles/tsc
        return calculateCTime(diff, diffTimestamp);
    }

    private float calculateC3Time(int core, long diffTimestamp) throws Exception {
        BigInteger core_C3 = super.readMSR(Registers.MSR_CORE_C3_RESIDENCY, core);
        float diff = (float) getDifferenceBetweenValues(core_C3, this.old_core_C3[core]).longValue();
        //saving values for next loop, so the difference can be made.
        this.old_core_C3[core] = core_C3;

        return calculateCTime(diff, diffTimestamp);
    }

    private float calculateC6Time(int core, long diffTimestamp) throws Exception {
        BigInteger core_C6 = super.readMSR(Registers.MSR_CORE_C6_RESIDENCY, core);
        float diff = (float) getDifferenceBetweenValues(core_C6, this.old_core_C6[core]).longValue();
        //saving values for next loop, so the difference can be made.
        this.old_core_C6[core] = core_C6;

        return calculateCTime(diff, diffTimestamp);
    }

    private float calculateC7Time(int core, long diffTimestamp) throws Exception {
        BigInteger core_C7 = super.readMSR(Registers.MSR_CORE_C7_RESIDENCY, core);
        float diff = (float) getDifferenceBetweenValues(core_C7, this.old_core_C7[core]).longValue();
        //saving values for next loop, so the difference can be made.
        this.old_core_C7[core] = core_C7;

        return calculateCTime(diff, diffTimestamp);
    }

    private void calculateOtherCoreStuff(int core, CounterInfo info) throws Exception {
        for (int i = 0; i != info.getSubs().size(); i++) {
            CounterInfo sub = info.getSubs().get(i);
            String name = sub.getName();

            int value = -2;
            if (name.equalsIgnoreCase("ACPI C State")) {
                value = HMTProxy.INSTANCE.getACPICState(core, this.hyperThreadingEnabled);
            } else if (name.equalsIgnoreCase("Windows Frequency(Mhz)")) {
                value = HMTProxy.INSTANCE.getWindowsFrequency(core, this.hyperThreadingEnabled);
            } else if (name.equalsIgnoreCase("Temp(C)")) {
                value = getCoreTemperature(core);
            }

            if (value != -2) {
                sub.setCounter(value);
            }
        }
    }

    private void calculatePackageCStates(int packageIndex, int core, BigInteger timestampctr, CounterInfo info) throws Exception {
        if (!timestampctr.equals(BigInteger.ZERO)) {
            long diffTimestamp = getDifferenceBetweenValues(timestampctr, this.old_timestampctr[core]).longValue();
            for (int i = 0; i != info.getSubs().size(); i++) {
                CounterInfo sub = info.getSubs().get(i);
                String name = sub.getName();

                float value = -2f;
                if (name.equalsIgnoreCase("PC2(%)")) {
                    value = calculatePC2Time(core, packageIndex, diffTimestamp);
                } else if (name.equalsIgnoreCase("PC3(%)")) {
                    value = calculatePC3Time(core, packageIndex, diffTimestamp);
                } else if (name.equalsIgnoreCase("PC6(%)")) {
                    value = calculatePC6Time(core, packageIndex, diffTimestamp);
                } else if (name.equalsIgnoreCase("PC7(%)")) {
                    value = calculatePC7Time(core, packageIndex, diffTimestamp);
                }

                if (value != -2f) {
                    sub.setCounter(value);
                }
            }
        }
    }

    private float calculatePC2Time(int core, int packageIndex, long diffTimestamp) throws Exception {
        BigInteger pkg_C2 = super.readMSR(Registers.MSR_PKG_C2_RESIDENCY, core);
        float diff = (float) getDifferenceBetweenValues(pkg_C2, this.old_pkg_C2[packageIndex]).longValue();
        //saving values for next loop, so the difference can be made.
        this.old_pkg_C2[packageIndex] = pkg_C2;

        return calculateCTime(diff, diffTimestamp);
    }

    private float calculatePC3Time(int core, int packageIndex, long diffTimestamp) throws Exception {
        BigInteger pkg_C3 = super.readMSR(Registers.MSR_PKG_C3_RESIDENCY, core);
        float diff = (float) getDifferenceBetweenValues(pkg_C3, this.old_pkg_C3[packageIndex]).longValue();
        //saving values for next loop, so the difference can be made.
        this.old_pkg_C3[packageIndex] = pkg_C3;

        return calculateCTime(diff, diffTimestamp);
    }

    private float calculatePC6Time(int core, int packageIndex, long diffTimestamp) throws Exception {
        BigInteger pkg_C6 = super.readMSR(Registers.MSR_PKG_C6_RESIDENCY, core);
        float diff = (float) getDifferenceBetweenValues(pkg_C6, this.old_pkg_C6[packageIndex]).longValue();
        //saving values for next loop, so the difference can be made.
        this.old_pkg_C6[packageIndex] = pkg_C6;

        return calculateCTime(diff, diffTimestamp);
    }

    private float calculatePC7Time(int core, int packageIndex, long diffTimestamp) throws Exception {
        BigInteger pkg_C7 = super.readMSR(Registers.MSR_PKG_C7_RESIDENCY, core);
        float diff = (float) getDifferenceBetweenValues(pkg_C7, this.old_pkg_C7[packageIndex]).longValue();
        //saving values for next loop, so the difference can be made.
        this.old_pkg_C7[packageIndex] = pkg_C7;

        return calculateCTime(diff, diffTimestamp);
    }

    private void calculateOtherPackageStuff(int packageIndex, int core, CounterInfo info) throws Exception {
        float frequency = -2f;

        for (int i = 0; i != info.getSubs().size(); i++) {
            CounterInfo sub = info.getSubs().get(i);
            String name = sub.getName();

            float value = -2f;
            if (name.equalsIgnoreCase("Multiplier")) {
                frequency = getCurrentFrequency(core);
                value = frequency / getBusClockFrequencyInMhz();

            } else if (name.equalsIgnoreCase("Frequency(Mhz)")) {
                if (frequency == -2f) {
                    frequency = getCurrentFrequency(core);
                }
                value = frequency;

            } else if (name.equalsIgnoreCase("Energy(W)")) {
                value = getCurrentPackageEnergy(packageIndex, core);
            } else if (name.equalsIgnoreCase("PP0(W)")) {
                value = getCurrentPP0Energy(packageIndex, core);
            } else if (name.equalsIgnoreCase("PP1(W)")) {
                value = getCurrentPP1Energy(packageIndex, core);
            } else if (name.equalsIgnoreCase("DRAM(W)")) {
                value = getCurrentDRAMEnergy(packageIndex, core);
            } else if (name.equalsIgnoreCase("Temp(C)")) {
                //Temp as integer.
                sub.setCounter(getPackageTemperature(core));
            }

            if (value != -2f) {
                sub.setCounter(value);
            }
        }
    }

    /**
     *
     * @param diff
     * @param diffTimestamp
     * @return -1f if not valid.
     */
    private float calculateCTime(float diff, long diffTimestamp) {
        float cTime = (diff / diffTimestamp) * 100f;
        if (cTime > 100f || cTime < 0f) {
            return -1f;
        }

        return cTime;
    }

    /**
     * Reads out the current core temperature in Celcius (PROCHOT - readout
     * value = real temp)
     *
     * @param core
     * @return
     * @throws java.lang.Exception
     */
    private int getCoreTemperature(int core) throws Exception {
        //	 Digital temperature reading in 1 degree
        //	Celsius relative to the TCC activation temperature.
        //	0: TCC Activation temperature,
        //	1: (TCC Activation - 1) , etc. See the processors data sheet for details regarding
        //	TCC activation.
        //	A lower reading in the Digital Readout field (bits 22:16) indicates a higher actual
        //	temperature
        //(documented in the Intel 64 and IA-32 Architectures Software Developers Manual which can be found at 
        // http://www.intel.com/content/dam/www/public/us/en/documents/manuals/64-ia-32-architectures-software-developer-vol-3b-part-2-manual.pdf 14.7.5.2

        long value = super.readMSR(Registers.MSR_IA32_THERM_STATUS, 23, 16, core).longValue(); //highbit is exclusive!

        //actual temperature is prochot - digital readout
        int temp = (int) (this.maxProcessorTemp - value);
        if (temp < 0l || temp > this.maxProcessorTemp) {
            return -1;
        }
        return temp;
    }

    private int getPackageTemperature(int core) throws Exception {
        //	 Digital temperature reading in 1 degree
        //	Celsius relative to the TCC activation temperature.
        //	0: TCC Activation temperature,
        //	1: (TCC Activation - 1) , etc. See the processors data sheet for details regarding
        //	TCC activation.
        //	A lower reading in the Digital Readout field (bits 22:16) indicates a higher actual
        //	temperature
        //(documented in the Intel 64 and IA-32 Architectures Software Developers Manual which can be found at 
        // http://www.intel.com/content/dam/www/public/us/en/documents/manuals/64-ia-32-architectures-software-developer-vol-3b-part-2-manual.pdf 1.4.8

        long value = super.readMSR(Registers.MSR_IA32_PACKAGE_THERM_STATUS, 23, 16, core).longValue(); //highbit is exclusive!

        //actual temperature is prochot - digital readout
        int temp = (int) (this.maxProcessorTemp - value);
        if (temp < 0 || temp > this.maxProcessorTemp) {
            return -1;
        }
        return temp;
    }

    private float getCurrentFrequency(int core) throws Exception {
        BigInteger unhaltedRefCycles = super.readMSR(Registers.MSR_IA32_FIXED_CTR2, core);

        long diffRefCycles = getDifferenceBetweenValues(unhaltedRefCycles, this.old_unhaltedRefCycles[core]).longValue();

        float frequency = this.baseFrequency;
        if (diffRefCycles != 0l) {
            BigInteger unhaltedCoreCycles = super.readMSR(Registers.MSR_IA32_FIXED_CTR1, core);

            float diffUnhaltedCoreCycles = (float) getDifferenceBetweenValues(unhaltedCoreCycles, this.old_unhaltedCoreCycles[core]).longValue();
            frequency *= diffUnhaltedCoreCycles / diffRefCycles; //this will mostly be always 1 or 1.000000xxx so it's not broken :)

            this.old_unhaltedCoreCycles[core] = unhaltedCoreCycles;
        }
        this.old_unhaltedRefCycles[core] = unhaltedRefCycles;

        return frequency;
    }

    /**
     * Returns the energy consumption in W from the whole package (= includes
     * PP0 and PP1 but also other things!)
     *
     * @param packageIndex
     * @param core
     * @return
     * @throws Exception
     */
    private float getCurrentPackageEnergy(int packageIndex, int core) throws Exception {
        //this msr counts the number of "burned" energy units. Using the Energy Status Units we can know the Joules and by dividing with #seconds_elapsed we know the Watt :-)
        BigInteger value = super.readMSR(Registers.MSR_PKG_ENERGY_STATUS, 32, 0, core);

        float energy = 0f;
        if (this.prevTimeStamp != 0) {
            long elapsedMillis = Calendar.getInstance().getTimeInMillis() - this.prevTimeStamp;
            energy = (float) (getDifferenceBetweenValues(value, this.old_energy[packageIndex]).longValue()) * this.joulesPerEnergyUnit / elapsedMillis;
        }

        this.old_energy[packageIndex] = value;

        return energy;
    }

    /**
     * Returns the energy consumption in W from power plane 0 (= the cores)
     *
     * @param packageIndex
     * @param core
     * @return
     * @throws Exception
     */
    private float getCurrentPP0Energy(int packageIndex, int core) throws Exception {
        //this msr counts the number of "burned" energy units. Using the Energy Status Units we can know the Joules and by dividing with #seconds_elapsed we know the Watt :-)
        BigInteger value = super.readMSR(Registers.MSR_PP0_ENERGY_STATUS, 32, 0, core);

        float energy = 0f;
        if (this.prevTimeStamp != 0) {
            long elapsedMillis = Calendar.getInstance().getTimeInMillis() - this.prevTimeStamp;
            energy = (float) (getDifferenceBetweenValues(value, this.old_pp0[packageIndex]).longValue()) * this.joulesPerEnergyUnit / elapsedMillis;
        }

        this.old_pp0[packageIndex] = value;

        return energy;
    }

    /**
     * Returns the energy consumption in W from power plane 1 (= the graphics
     * core) It is possible that this is not available (servers).
     *
     * @param packageIndex
     * @param core
     * @return
     * @throws Exception
     */
    private float getCurrentPP1Energy(int packageIndex, int core) throws Exception {
        //this msr counts the number of "burned" energy units. Using the Energy Status Units we can know the Joules and by dividing with #seconds_elapsed we know the Watt :-)
        BigInteger value = super.readMSR(Registers.MSR_PP1_ENERGY_STATUS, 32, 0, core);

        float energy = 0f;
        if (this.prevTimeStamp != 0) {
            long elapsedMillis = Calendar.getInstance().getTimeInMillis() - this.prevTimeStamp;
            energy = (float) (getDifferenceBetweenValues(value, this.old_pp1[packageIndex]).longValue()) * this.joulesPerEnergyUnit / elapsedMillis;
        }

        this.old_pp1[packageIndex] = value;

        return energy;
    }

    /**
     * Returns the energy consumption in W from the DRAM part (what this
     * includes is unclear). DRAM readout is only supported on server platforms.
     *
     * @param packageIndex
     * @param core
     * @return
     * @throws Exception
     */
    private float getCurrentDRAMEnergy(int packageIndex, int core) throws Exception {
        //this msr counts the number of "burned" energy units. Using the Energy Status Units we can know the Joules and by dividing with #seconds_elapsed we know the Watt :-)
        BigInteger value = super.readMSR(Registers.MSR_DRAM_ENERGY_STATUS, 32, 0, core);

        float energy = 0f;
        if (this.prevTimeStamp != 0) {
            long elapsedMillis = Calendar.getInstance().getTimeInMillis() - this.prevTimeStamp;
            energy = (float) (getDifferenceBetweenValues(value, this.old_dram[packageIndex]).longValue()) * this.joulesPerEnergyUnit / elapsedMillis;
        }

        this.old_dram[packageIndex] = value;

        return energy;
    }
}
