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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Didjeeh
 */
public class IntelCPU extends CPU {

    private boolean sandyBridge, ivyBridge;

    private final boolean hyperThreadingEnabled;

    private final ArrayList<Integer> packageRepresentativeCores;

    private final BigInteger[] old_unhaltedCoreCycles;
    private final BigInteger[] old_unhaltedRefCycles;
    private final BigInteger[] old_core_C3;
    private final BigInteger[] old_core_C6;
    private final BigInteger[] old_timestampctr;
    private final BigInteger[] old_mperfctr;
    private final BigInteger[] old_aperfctr;
    private final BigInteger[] old_core_C7;
    private final BigInteger[] old_pkg_C2;
    private final BigInteger[] old_pkg_C3;
    private final BigInteger[] old_pkg_C6;
    private final BigInteger[] old_pkg_C7;
    private final BigInteger[] old_energy;
    private final BigInteger[] old_pp0;
    private final BigInteger[] old_pp1;
    private final BigInteger[] old_dram;

    public IntelCPU(long family, long model) {
        super(family, model);

        this.hyperThreadingEnabled = this.logicalCores > this.physicalCores;

        this.packageRepresentativeCores = new ArrayList<Integer>();
        for (int p = 0; p != super.packages; p++) {
            this.packageRepresentativeCores.add(p * (super.physicalCores / super.packages));
        }

        this.old_unhaltedCoreCycles = new BigInteger[this.physicalCores];
        this.old_unhaltedRefCycles = new BigInteger[this.physicalCores];

        this.old_core_C3 = new BigInteger[this.physicalCores];
        this.old_core_C6 = new BigInteger[this.physicalCores];
        this.old_core_C7 = new BigInteger[this.physicalCores];
        this.old_timestampctr = new BigInteger[this.physicalCores];
        this.old_mperfctr = new BigInteger[this.physicalCores];
        this.old_aperfctr = new BigInteger[this.physicalCores];

        this.old_pkg_C2 = new BigInteger[this.packages];
        this.old_pkg_C3 = new BigInteger[this.packages];
        this.old_pkg_C6 = new BigInteger[this.packages];
        this.old_pkg_C7 = new BigInteger[this.packages];
        this.old_energy = new BigInteger[this.packages];
        this.old_pp0 = new BigInteger[this.packages];
        this.old_pp1 = new BigInteger[this.packages];
        this.old_dram = new BigInteger[this.packages];

        determineArchitecture();
        try {
            initMSRs();
        } catch (Exception ex) {
            Logger.getLogger(IntelCPU.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            foo();
        } catch (Exception ex) {
            Logger.getLogger(IntelCPU.class.getName()).log(Level.SEVERE, null, ex);
        }
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
        BigInteger value = BigInteger.ONE;
        value.add(BigInteger.ONE.shiftLeft(1));
        value.add(BigInteger.ONE.shiftLeft(2));
        value.add(BigInteger.ONE.shiftLeft(3));
        value.add(BigInteger.ONE.shiftLeft(32));
        value.add(BigInteger.ONE.shiftLeft(33));
        value.add(BigInteger.ONE.shiftLeft(34));
        super.writeMSR(Registers.MSR_IA32_PERF_GLOBAL_CTRL, value);

        //LLC Misses
        //event number 0x2E
        //umask 0x41
        BigInteger eventValue = BigInteger.valueOf(0x2E);
        eventValue.add(BigInteger.valueOf(0x41).shiftLeft(8)); //valuemask //shift 8 bits
        eventValue.add(BigInteger.ONE.shiftLeft(16)); //usr
        eventValue.add(BigInteger.ONE.shiftLeft(17)); //os
        eventValue.add(BigInteger.ZERO.shiftLeft(18)); //edge detection
        eventValue.add(BigInteger.ZERO.shiftLeft(19)); //pin control
        eventValue.add(BigInteger.ZERO.shiftLeft(20)); //apic int
        eventValue.add(BigInteger.ZERO.shiftLeft(21));//any thread
        eventValue.add(BigInteger.ONE.shiftLeft(22)); //enables the corresponding performance counter
        eventValue.add(BigInteger.ZERO.shiftLeft(23)); //don't invert the following cmask
        eventValue.add(BigInteger.ZERO.shiftLeft(24)); //cmask

        //Enable fixed Architectural Performance Monitor counters 1 and 2 in the Global Performance Counter Control
        // IA32_PERF_GLOBAL_CTRL(38FH - 911) and the Fixed-Function Performance Counter Control IA32_FIXED_CTR_CTL (38DH).
        //enable bits are on position 0 & 1 = 0000 0000 0000 0011 = 3
        //Repeat this step for each logical processor in the system
        for (int core = 0; core != super.physicalCores; core++) {
            core = getCorrectPhysicalCoreId(core);
            super.writeMSR(Registers.MSR_IA32_FIXED_CTR_CTL, BigInteger.valueOf(819), core);
            super.writeMSR(Registers.MSR_IA32_PERF_GLOBAL_CTRL, new BigInteger("30064771075"), core);

            super.writeMSR(Registers.MSR_IA32_PERFEVTSEL0_ADDR, eventValue, core);
            super.writeMSR(Registers.MSR_IA32_PMC0, BigInteger.ZERO, core);
        }
    }

    /**
     * Sets the affinity of this process to a specified core, give the physical
     * core as input! Override for hyper threading.
     *
     * @param physicalCore number of physical core, will automatically adjust
     * when hyper threading is enabled!
     * @return
     */
    private int getCorrectPhysicalCoreId(int physicalCore) {
        //we're only allowed to this quick thingie because we dont readout the hyperthreaded cores (= physically same msr's as the real core)
        return this.hyperThreadingEnabled ? physicalCore * 2 : physicalCore;
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
                    counterInfo.getSubs().add(new CounterInfo("PP1(W)"));
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

    public void foo() throws Exception {
        double currentFrequency = 0, currentMultiplier = 0, currentPackageEnergy = 0, currentPP0 = 0, currentPP1 = 0, currentDRAM = 0;

        int packageIndex = -1;

        for (int core = 0; core < this.physicalCores; core++) {
            boolean representsNewPackageCore = packageRepresentativeCores.contains(core);
            if (representsNewPackageCore) {
                packageIndex++;
            }

            /* read all the values first, so we get minimum skew */
            BigInteger timestampctr = super.readMSR(Registers.MSR_TSC, core);
            BigInteger mperfctr = super.readMSR(Registers.MSR_MPERF, core);
            BigInteger aperfcounter = super.readMSR(Registers.MSR_APERF, core);
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
            BigInteger diffTimestamp = getDifferenceBetweenValues(timestampctr, this.old_timestampctr[core]);
            BigInteger diffmperf = getDifferenceBetweenValues(mperfctr, this.old_mperfctr[core]);
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
                    currentFrequency = _baseFrequency;
                } else {
                    currentFrequency = _baseFrequency * (double) diffUnhaltedCoreCycles / diffRefCycles; //this will mostly be always 1 or 1.000000xxx so it's not broken :)
                }
                currentMultiplier = currentFrequency / getBusClockFrequencyInMhz();

                //Package consumed energy
                currentPackageEnergy = GetCurrentPackageEnergy(packageIndex);
                currentPP0 = GetCurrentPP0Energy(packageIndex);
                currentPP1 = GetCurrentPP1Energy(packageIndex);
                currentDRAM = GetCurrentDRAMEnergy(packageIndex);

                pkg_c3_diff = getDifferenceBetweenValues(pkg_C3, this.old_pkg_C3[packageIndex]);
                pkg_c6_diff = getDifferenceBetweenValues(pkg_C6, this.old_pkg_C6[packageIndex]);

                //would not crash, just cleaner
                if (this.sandyBridge) {
                    pkg_c2_diff = getDifferenceBetweenValues(pkg_C2, this.old_pkg_C2[packageIndex]);
                    pkg_c7_diff = getDifferenceBetweenValues(pkg_C7, this.old_pkg_C7[packageIndex]);
                }
            }
        }
    }

    protected BigInteger getDifferenceBetweenValues(BigInteger newValue, BigInteger oldValue) {
        if (oldValue.compareTo(newValue) > 0) { //handle overflow, taking unsigned long long max as upper bound.
            return BigInteger.valueOf(2).pow(64).subtract(oldValue).add(newValue);
        }
        return newValue.subtract(oldValue);
    }

}
