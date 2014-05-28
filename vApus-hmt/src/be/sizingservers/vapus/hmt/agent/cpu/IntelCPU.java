/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package be.sizingservers.vapus.hmt.agent.cpu;

import be.sizingservers.vapus.agent.util.CounterInfo;
import be.sizingservers.vapus.agent.util.Entities;
import be.sizingservers.vapus.agent.util.Entity;
import be.sizingservers.vapus.agent.util.HostName;
import java.math.BigInteger;
import java.util.ArrayList;
import org.springframework.util.StopWatch;

/**
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

    //this stopwatch will monitor the elapsed milliseconds between runs
    private final StopWatch elapsedTimeStopwatch;

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

        this.hyperThreadingEnabled = this.logicalCores > this.physicalCores; //Used in initMSRs amongst others.

        this.packageRepresentativeCores = new ArrayList<Integer>();
        for (int p = 0; p != super.packages; p++) {
            this.packageRepresentativeCores.add(p * (super.physicalCores / super.packages));
        }

        initMSRs();

        this.baseFrequency = getBaseFrequency();

        this.joulesPerEnergyUnit = getJoulesPerEnergyUnit();

        this.maxProcessorTemp = super.readMSR(Registers.MSR_TEMPERATURE_TARGET, 23, 16).longValue();

        this.pp1Available = getPP1Available();

        this.elapsedTimeStopwatch = new StopWatch();

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

        foo();
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
        long energyStatusUnits = super.readMSR(Registers.MSR_RAPL_POWER_UNIT, 13, 8).longValue();

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
    public float getBusClockFrequencyInMhz() {
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

            for (int core = 0; core != this.physicalCores; core++) {
                CounterInfo counterInfo = new CounterInfo("physical core" + core);
                counterInfo.getSubs().add(new CounterInfo("C0(%)"));
                counterInfo.getSubs().add(new CounterInfo("C1(%)"));
                counterInfo.getSubs().add(new CounterInfo("C3(%)"));
                counterInfo.getSubs().add(new CounterInfo("C6(%)"));

                if (this.sandyBridge || this.ivyBridge) {
                    counterInfo.getSubs().add(new CounterInfo("C7(%)"));
                }

                counterInfo.getSubs().add(new CounterInfo("Temp(C)"));
                counterInfo.getSubs().add(new CounterInfo("ACPI C state"));

                //If windows...
                counterInfo.getSubs().add(new CounterInfo("Windows Frequency(Mhz)"));

                entity.getSubs().add(counterInfo);
            }

            for (int p = 0; p != this.packages; p++) {
                CounterInfo counterInfo = new CounterInfo("package" + p);
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

                    counterInfo.getSubs().add(new CounterInfo("DRAM(W)"));
                }

                entity.getSubs().add(counterInfo);
            }

            super.wih.add(entity);
        }
        return super.wih;
    }

    @Override
    public Entities getWiwWithCounters() {

        return super.wiwWithCounters;
    }

    private void foo() throws Exception {
        float currentFrequency = 0, currentMultiplier = 0, currentPackageEnergy = 0, currentPP0 = 0, currentPP1 = 0, currentDRAM = 0;

        int packageIndex = -1;

        for (int core = 0; core < this.physicalCores; core++) {
            boolean representsNewPackageCore = packageRepresentativeCores.contains(core);
            if (representsNewPackageCore) {
                packageIndex++;
            }

            /* read all the values first, so we get minimum skew */
            BigInteger timestampctr = super.readMSR(Registers.MSR_TSC, core);
            BigInteger unhaltedCoreCycles = super.readMSR(Registers.MSR_IA32_FIXED_CTR1, core);
            BigInteger unhaltedRefCycles = super.readMSR(Registers.MSR_IA32_FIXED_CTR2, core);
            BigInteger core_C3 = super.readMSR(Registers.MSR_CORE_C3_RESIDENCY, core);
            BigInteger core_C6 = super.readMSR(Registers.MSR_CORE_C6_RESIDENCY, core);
            BigInteger core_C7 = super.readMSR(Registers.MSR_CORE_C7_RESIDENCY, core);

            BigInteger pkg_C2 = BigInteger.ZERO, pkg_C3 = BigInteger.ZERO, pkg_C6 = BigInteger.ZERO, pkg_C7 = BigInteger.ZERO;
            if (representsNewPackageCore) {
                pkg_C3 = super.readMSR(Registers.MSR_PKG_C3_RESIDENCY, core);
                pkg_C6 = super.readMSR(Registers.MSR_PKG_C6_RESIDENCY, core);

                //will cause an error if we try this on a something else
                if (this.sandyBridge || this.ivyBridge) {
                    pkg_C2 = super.readMSR(Registers.MSR_PKG_C2_RESIDENCY, core);
                    pkg_C7 = super.readMSR(Registers.MSR_PKG_C7_RESIDENCY, core);
                }
            }

            //differences
            float diffTimestamp = (float) getDifferenceBetweenValues(timestampctr, this.old_timestampctr[core]).longValue();
            BigInteger diffUnhaltedCoreCycles = getDifferenceBetweenValues(unhaltedCoreCycles, this.old_unhaltedCoreCycles[core]);
            BigInteger diffRefCycles = getDifferenceBetweenValues(unhaltedRefCycles, this.old_unhaltedRefCycles[core]);
            BigInteger c3_diff = getDifferenceBetweenValues(core_C3, this.old_core_C3[core]);
            BigInteger c6_diff = getDifferenceBetweenValues(core_C6, this.old_core_C6[core]);
            BigInteger c7_diff = getDifferenceBetweenValues(core_C7, this.old_core_C7[core]);

            //8. Compute the actual frequency value for each logical processor as follows:
            // currentFreq = Base Operating Frequency * (Unhalted Core Cycles / Unhalted Ref Cycles)
            //int sock;
            //for (sock = 0; sock < _sockets; sock++)
            //    if (sock * ((physical_cpus / _sockets)) == cpu) //new socket
            BigInteger pkg_c2_diff = BigInteger.ZERO, pkg_c3_diff = BigInteger.ZERO, pkg_c6_diff = BigInteger.ZERO, pkg_c7_diff = BigInteger.ZERO;

            if (representsNewPackageCore) {
                //Multiplier
                if (diffRefCycles.equals(BigInteger.ZERO)) {
                    currentFrequency = this.baseFrequency;
                } else {
                    currentFrequency = this.baseFrequency * ((float) diffUnhaltedCoreCycles.longValue()) / diffRefCycles.longValue(); //this will mostly be always 1 or 1.000000xxx so it's not broken :)
                }
                currentMultiplier = currentFrequency / getBusClockFrequencyInMhz();

                //Package consumed energy
                currentPackageEnergy = getCurrentPackageEnergy(packageIndex, core);
                currentPP0 = getCurrentPP0Energy(packageIndex, core);

                if (this.pp1Available) {
                    currentPP1 = getCurrentPP1Energy(packageIndex, core);
                }
                currentDRAM = getCurrentDRAMEnergy(packageIndex, core);

                pkg_c3_diff = getDifferenceBetweenValues(pkg_C3, this.old_pkg_C3[packageIndex]);
                pkg_c6_diff = getDifferenceBetweenValues(pkg_C6, this.old_pkg_C6[packageIndex]);

                //would not crash, just cleaner
                if (this.sandyBridge || this.ivyBridge) {
                    pkg_c2_diff = getDifferenceBetweenValues(pkg_C2, this.old_pkg_C2[packageIndex]);
                    pkg_c7_diff = getDifferenceBetweenValues(pkg_C7, this.old_pkg_C7[packageIndex]);
                }
            }

            float c0amount = -1f, c1amount = -1f, c3amount = -1f, c6amount = -1f, c7amount = -1f;
            float pkgc2amount = -1f, pkgc3amount = -1f, pkgc6amount = -1f, pkgc7amount = -1f;

            if (diffTimestamp != 0f) {
                //C0 time
                //read the Fixed-Function Performance Counter 2 IA32_FIXED_CTR2 (30BH) and the TCS (10H - 16)
                //these things have already been computed above us.
                //%C0time = unhalted ref cycles/tsc
                c0amount = ((float) diffRefCycles.longValue() / diffTimestamp) * 100f;

                //%C3time = core_C3 / timestamp
                c3amount = ((float) c3_diff.longValue() / diffTimestamp) * 100f;
                c6amount = ((float) c6_diff.longValue() / diffTimestamp) * 100f;
                c7amount = ((float) c7_diff.longValue() / diffTimestamp) * 100f;

                //C1 time
                //the C states > C0 are states where the core is halted.. so this amount of cycles
                //can be calculated with (1 - (unhalted ref cycles / tsc cycles)) <-- http://software.intel.com/en-us/articles/measuring-the-halted-state/
                //Please note that % C3 and % C6 (and greater...) is also included in this amount
                //So I've chosen to extract the C1 time of the calculation; (total time (= 1) - c0 - c3 - c6 - ...) = c1 time 
                c1amount = 1 - c0amount - c3amount - c6amount - (this.sandyBridge || this.ivyBridge ? c7amount : 0f); //or some extra calculations but same results --> 1 - ((double) diffRefCycles / diffTimestamp) - c3amount - c6amount;       

                pkgc2amount = ((float) pkg_c2_diff.longValue() / diffTimestamp) * 100f;
                pkgc3amount = ((float) pkg_c3_diff.longValue() / diffTimestamp) * 100f;
                pkgc6amount = ((float) pkg_c6_diff.longValue() / diffTimestamp) * 100f;
                pkgc7amount = ((float) pkg_c7_diff.longValue() / diffTimestamp) * 100f;
            }
            //saving values for next loop
            this.old_timestampctr[core] = timestampctr;
            this.old_unhaltedCoreCycles[core] = unhaltedCoreCycles;
            this.old_unhaltedRefCycles[core] = unhaltedRefCycles;
            this.old_core_C3[core] = core_C3;
            this.old_core_C6[core] = core_C6;
            this.old_core_C7[core] = core_C7;

            if (representsNewPackageCore) {
                this.old_pkg_C2[packageIndex] = pkg_C2;
                this.old_pkg_C3[packageIndex] = pkg_C3;
                this.old_pkg_C6[packageIndex] = pkg_C6;
                this.old_pkg_C7[packageIndex] = pkg_C7;
            }

            if (c0amount > 100) {
                c0amount = -1f;
            }
            if (c1amount > 100) {
                c1amount = -1f;
            }
            if (c3amount > 100) {
                c3amount = -1f;
            }
            if (c6amount > 100) {
                c6amount = -1f;
            }
        }
        if (this.elapsedTimeStopwatch.isRunning()) {
            this.elapsedTimeStopwatch.stop();
        }
        this.elapsedTimeStopwatch.start();
    }

    protected BigInteger getDifferenceBetweenValues(BigInteger newValue, BigInteger oldValue) {
        if (oldValue.compareTo(newValue) > 0) { //handle overflow, taking unsigned long long max as upper bound.
            return BigInteger.valueOf(2).pow(64).subtract(oldValue).add(newValue);
        }
        return newValue.subtract(oldValue);
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
        if (elapsedTimeStopwatch.isRunning()) {
            energy = (float) (getDifferenceBetweenValues(value, this.old_energy[packageIndex]).longValue() * this.joulesPerEnergyUnit / this.elapsedTimeStopwatch.getTotalTimeSeconds());
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
        if (elapsedTimeStopwatch.isRunning()) {
            energy = (float) (getDifferenceBetweenValues(value, this.old_pp0[packageIndex]).longValue() * this.joulesPerEnergyUnit / this.elapsedTimeStopwatch.getTotalTimeSeconds());
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

        if (elapsedTimeStopwatch.isRunning()) {
            energy = (float) (getDifferenceBetweenValues(value, this.old_pp1[packageIndex]).longValue() * this.joulesPerEnergyUnit / this.elapsedTimeStopwatch.getTotalTimeSeconds());
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
        if (elapsedTimeStopwatch.isRunning()) {
            energy = (float) (getDifferenceBetweenValues(value, this.old_dram[packageIndex]).longValue() * this.joulesPerEnergyUnit / this.elapsedTimeStopwatch.getTotalTimeSeconds());
        }

        this.old_dram[packageIndex] = value;

        return energy;
    }

    /**
     * Reads out the current core temperature in Celcius (PROCHOT - readout
     * value = real temp)
     *
     * @param core
     * @return
     * @throws java.lang.Exception
     */
    public int getCoreTemperature(int core) throws Exception {
     //	 Digital temperature reading in 1 degree
        //	Celsius relative to the TCC activation temperature.
        //	0: TCC Activation temperature,
        //	1: (TCC Activation - 1) , etc. See the processors data sheet for details regarding
        //	TCC activation.
        //	A lower reading in the Digital Readout field (bits 22:16) indicates a higher actual
        //	temperature
        //(documented in the Intel 64 and IA-32 Architectures Software Developers Manual which can be found at http://www.intel.com/Assets/PDF/manual/253668.pdf

        //first we need to check if the readings are valid (bit 31)
        //invalid values
        if (super.readMSR(Registers.MSR_TEMPERATURE_TARGET, 32, 31, core).compareTo(BigInteger.ONE) != 0) {
            return -1;
        }

        long value = super.readMSR(Registers.MSR_TEMPERATURE_TARGET, 23, 16, core).longValue();

        //actual temperature is prochot - digital readout
        return (int) (this.maxProcessorTemp - value);
    }
}
