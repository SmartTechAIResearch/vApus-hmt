/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.hmt.agent.cpu;

import be.sizingservers.vapus.agent.util.CounterInfo;
import be.sizingservers.vapus.agent.util.Entities;
import be.sizingservers.vapus.agent.util.Entity;
import be.sizingservers.vapus.agent.util.HostName;
import java.math.BigInteger;
import java.util.ArrayList;

/**
 * MagnyCours, Bulldozer and Piledriver supported. Temperature reading should not be trusted.
 * @author Didjeeh
 */
public class AMDCPU extends CPU {

    private boolean magnyCours, bulldozer, piledriver; //Piledriver BKDG is available, but they have an internal document with info we need for getting C-states. They do not want to give it to us.
    private final int nodes;
    private final ArrayList<Integer> nodeRepresentativeCores, powerSensorOffsets;
    private final boolean[] canCalculatePowerNodes; //Not for Magny-Cours

    public AMDCPU(long family, long model) throws Exception {
        super(family, model);

        determineArchitecture();

        this.nodes = super.packages * 2; //2 nodes (fused) per package, since MagnyCours

        this.nodeRepresentativeCores = new ArrayList<Integer>();
        for (int n = 0; n != this.nodes; n++) {
            this.nodeRepresentativeCores.add(n * (super.logicalCores / this.nodes));
        }

        //1 sensor per package, the node index is used to calculate the pci device id.
        this.powerSensorOffsets = new ArrayList<Integer>();
        for (int offset = 0; offset < this.nodes; offset += 2) {
            this.powerSensorOffsets.add(offset);
        }

        this.canCalculatePowerNodes = new boolean[this.nodes];

//        getWIWWithCounters(getWDYH());
//        Thread.sleep(1000);
//        getWIWWithCounters(getWDYH());
    }

    private void determineArchitecture() {
        //All the CPU ID model numbers can be found at http://software.intel.com/en-us/articles/intel-processor-identification-with-cpuid-model-and-family-numbers/
        if (this.family == 0x10) {
            this.magnyCours = true;
        } else if (this.family == 0x15) {
            this.bulldozer = true;
        } else if (this.family == 0x20) {
            this.piledriver = true;
        }
        //else not supported.
    }

    @Override
    protected float getBusClockFrequencyInMhz() {
        return 200f;
    }

    @Override
    public Entities getWDYH() {
        if (super.wih == null) {
            super.wih = new Entities();

            Entity entity = new Entity(HostName.get(), true);

            for (int core = 0; core != this.logicalCores; core++) {
                CounterInfo counterInfo;

                // Buldozer and up: logical cores from HMTProxy: 2 integer en 1 floating point module per physical core. Magny-Cours: #locical == #physical
                if (this.magnyCours) {
                    counterInfo = new CounterInfo("Physical core " + core);
                    counterInfo.getSubs().add(new CounterInfo("Multiplier")); //Multiplier per real Physical core
                    counterInfo.getSubs().add(new CounterInfo("Frequency(Mhz)")); //As is the frequency
                } else {
                    counterInfo = new CounterInfo("Pseudo-physical core " + core);
                }

                counterInfo.getSubs().add(new CounterInfo("ACPI C State"));

                if (super.getOSIsWindows()) {
                    counterInfo.getSubs().add(new CounterInfo("Windows Frequency(Mhz)"));
                }

                entity.getSubs().add(counterInfo);
            }

            if (!this.magnyCours) {
                for (int n = 0; n != this.nodes; n++) {
                    CounterInfo counterInfo = new CounterInfo("Node " + n); //2 nodes per (fused) package, since MagnyCours

                    counterInfo.getSubs().add(new CounterInfo("Multiplier")); //Multiplier per node
                    counterInfo.getSubs().add(new CounterInfo("Frequency(Mhz)"));

                    counterInfo.getSubs().add(new CounterInfo("Energy(W)"));
                    counterInfo.getSubs().add(new CounterInfo("Temp(C)"));

                    entity.getSubs().add(counterInfo);
                }
            }

            super.wih.getSubs().add(entity);
        }
        return super.wih;
    }

    @Override
    public Entities getWIWWithCounters(Entities wiw) throws Exception {
        Entities wiwWithCounters = wiw.safeClone();
        Entity entity = wiwWithCounters.getSubs().get(0);

        //NOTE: simple trick of today (27/02/2013) is that AMD has max 8 'logical' cores per node, so when we have 32 logical cpu's we have 4 nodes with each one having a temperature sensor
        //Per package there is only 1 power sensor.
        for (int i = 0; i != entity.getSubs().size(); i++) {
            CounterInfo info = entity.getSubs().get(i);
            String name = info.getName();

            if (name.startsWith("Pseudo-physical core ")) {
                int core = new Integer(name.substring("Pseudo-physical core ".length()));

                calculateCoreStuff(core, info);
            } else if (name.startsWith("Physical core ")) {
                int core = new Integer(name.substring("Physical core ".length()));

                calculateCoreStuff(core, info);

            } else if (name.startsWith("Node ")) {
                int nodeIndex = new Integer(name.substring("Node ".length()));
                int core = this.nodeRepresentativeCores.get(nodeIndex);

                calculateNodeStuff(nodeIndex, core, info);
            }
        }

        return wiwWithCounters;
    }

    private void calculateNodeStuff(int nodeIndex, int core, CounterInfo info) throws Exception {
        //TEMPERATURE and POWER
        // Temperature --> OpenHardwareMonitor
        // Power --> fam15h_power

        float multiplier = -1f;
        for (int i = 0; i != info.getSubs().size(); i++) {
            CounterInfo sub = info.getSubs().get(i);
            String name = sub.getName();

            float value = -2f;
            if (name.equalsIgnoreCase("Multiplier")) {
                multiplier = getCurrentMultiplier(core);
                value = multiplier;
            } else if (name.equalsIgnoreCase("Frequency(Mhz)")) {
                if (multiplier == -1f) {
                    multiplier = getCurrentMultiplier(core);
                }
                value = multiplier * getBusClockFrequencyInMhz();
            } else if (name.equalsIgnoreCase("Energy(W)")) {
                value = getCurrentNodeEnergy(nodeIndex);
            } else if (name.equalsIgnoreCase("Temp(C)")) {
                value = getCurrentNodeTemperature(nodeIndex);
            }

            if (value != -2f) {
                sub.setCounter(value);
            }
        }
    }

    private float getCurrentNodeEnergy(int nodeIndex) {
        int pciDeviceId = Registers.PCI_BASE_DEVICE + nodeIndex; //The id of the sensor

        if (canCalculatePower(nodeIndex, pciDeviceId)) {
            if (!powerSensorOffsets.contains(nodeIndex)) {
                //Use the previous node index.
                pciDeviceId = Registers.PCI_BASE_DEVICE + nodeIndex - 1;
            }

            
            long value = HMTProxy.INSTANCE.readPciConfig(0x0, pciDeviceId, 0x4, Registers.REG_BASE_PROCESSOR_TDP);
            long base_tdp = value >> 16;

            value = HMTProxy.INSTANCE.readPciConfig(0x0, pciDeviceId, 0x5, Registers.REG_TDP_RUNNING_AVERAGE);
            long running_avg_capture = (value >> 4) & 0x3fffff;
            int running_avg_range = (int) ((value & 0xf) + 1);

            value = HMTProxy.INSTANCE.readPciConfig(0x0, pciDeviceId, 0x5, Registers.REG_TDP_LIMIT3);
            long tdp_limit = value >> 16;
            long tdp2watt = ((value & 0x3ff) << 6) | ((value >> 10) & 0x3ff);

            BigInteger currPwrMicroWatts = BigInteger.valueOf(tdp_limit);
            currPwrMicroWatts = currPwrMicroWatts.add(BigInteger.valueOf(base_tdp))
                    .shiftLeft(running_avg_range)
                    .subtract(BigInteger.valueOf(running_avg_capture))
                    .multiply(BigInteger.valueOf(tdp2watt));

            /* Convert to microWatt
             *
             * power is in Watt provided as fixed point integer with
             * scaling factor 1/(2^16).  For conversion we use
             * (10^6)/(2^16) = 15625/(2^10)
             */
            currPwrMicroWatts = currPwrMicroWatts.multiply(BigInteger.valueOf(15625))
                    .shiftRight(10 + running_avg_range);

            if (!currPwrMicroWatts.equals(BigInteger.ZERO)) {
                return (float) ((double) currPwrMicroWatts.longValue()) / 1000000; //µW to W;
            }
            return -1f;
        }
        return 0f;
    }

    private boolean canCalculatePower(int nodeIndex, int pciDeviceId) {
        //Strange quirk that must be applied to the current northbridge, otherwise the tdp running average register will not be updated.
        //Must be done per "node device id", aka for non existing devices. I guess there are duplicate registers.
        if (!this.canCalculatePowerNodes[nodeIndex]) {
            
            long value = HMTProxy.INSTANCE.readPciConfig(0x0, pciDeviceId, 0x5, Registers.REG_TDP_RUNNING_AVERAGE);

            if ((value & 0xF) == 0xe) {
                value &= 0xfffffff0;
                value |= 0x9;

                String error = HMTProxy.INSTANCE.writePciConfig(0x0, pciDeviceId, 0x5, Registers.REG_TDP_RUNNING_AVERAGE, value);

                if (error.length() == 0) {
                    this.canCalculatePowerNodes[nodeIndex] = true;
                }
                //else Calculate this the next time so the hardware has time to update the tdp running average register.
            } else {
                this.canCalculatePowerNodes[nodeIndex] = true;
            }
        }
        return this.canCalculatePowerNodes[nodeIndex];
    }

    private float getCurrentNodeTemperature(int nodeIndex) {
        int pciDeviceId =  Registers.PCI_BASE_DEVICE + nodeIndex; //The id of the sensor

        long value = HMTProxy.INSTANCE.readPciConfig(0x0, pciDeviceId, 0x3, Registers.REPORTED_TEMPERATURE_CONTROL_REGISTER);

        return ((float) ((value >> 21) & 0x7FF)) / 8.0f; //+ coreTemperature.Parameters[0].Value; //this is value 0, meaning 0 offset
    }

    /**
     *
     * @param core the 'logical core'
     * @param info
     * @throws Exception
     */
    private void calculateCoreStuff(int core, CounterInfo info) throws Exception {
        float multiplier = -1f;
        for (int i = 0; i != info.getSubs().size(); i++) {
            CounterInfo sub = info.getSubs().get(i);
            String name = sub.getName();

            int value = -2;
            if (name.equalsIgnoreCase("Multiplier")) {
                multiplier = getCurrentMultiplier(core);
                sub.setCounter(multiplier); //float
            } else if (name.equalsIgnoreCase("Frequency(Mhz)")) {
                if (multiplier == -1f) {
                    multiplier = getCurrentMultiplier(core);
                }
                sub.setCounter(multiplier * getBusClockFrequencyInMhz());
            }
            if (name.equalsIgnoreCase("ACPI C State")) {
                value = HMTProxy.INSTANCE.getACPICState(core, false); // HyperThreading nvt
            } else if (name.equalsIgnoreCase("Windows Frequency(Mhz)")) {
                value = HMTProxy.INSTANCE.getWindowsFrequency(core, false);
            }

            if (value != -2) {
                sub.setCounter(value);
            }
        }
    }

    private float getCurrentMultiplier(int core) throws Exception {
        HMTProxy.INSTANCE.readMSRTx(Registers.COFVID_STATUS, core);
        long cofvidEax = HMTProxy.INSTANCE.getMSREAX();

        long cpuDid = 0, cpuFid = 0;
        //int family = 0x15; //Glenn
        if (super.family == 0x10 || super.family == 0x11) {
            // 8:6 CpuDid: current core divisor ID
            // 5:0 CpuFid: current core frequency ID
            cpuDid = (cofvidEax >> 6) & 7;
            cpuFid = cofvidEax & 0x1F;
            return 0.5f * (cpuFid + 0x10) / (1 << (int) cpuDid);
        }

        if (super.family == 0x12) {
            // 8:4 CpuFid: current CPU core frequency ID
            // 3:0 CpuDid: current CPU core divisor ID
            cpuFid = (cofvidEax >> 4) & 0x1F;
            cpuDid = cofvidEax & 0xF;
            float divisor;
            if (cpuDid == 0l) {
                divisor = 1f;
            } else if (cpuDid == 1l) {
                divisor = 1.5f;
            } else if (cpuDid == 2l) {
                divisor = 2f;
            } else if (cpuDid == 3l) {
                divisor = 3f;
            } else if (cpuDid == 4l) {
                divisor = 4f;
            } else if (cpuDid == 5l) {
                divisor = 6f;
            } else if (cpuDid == 6l) {
                divisor = 8f;
            } else if (cpuDid == 7l) {
                divisor = 12f;
            } else if (cpuDid == 8l) {
                divisor = 16f;
            } else {
                divisor = 1f;
            }
            return ((float) (cpuFid + 0x10)) / divisor;
        }
        if (super.family == 0x14 || super.family == 0x15) {
            // 8:4: current CPU core divisor ID most significant digit
            // 3:0: current CPU core divisor ID least significant digit
            //uint divisorIdMSD = (cofvidEax >> 4) & 0x1F;
            //uint divisorIdLSD = cofvidEax & 0xF;
            //uint value = 0;
            //Ring0.ReadPciConfig(miscellaneousControlAddress,
            //  CLOCK_POWER_TIMING_CONTROL_0_REGISTER, out value);
            //uint frequencyId = value & 0x1F;
            //return (frequencyId + 0x10) /
            //  (divisorIdMSD + (divisorIdLSD * 0.25) + 1);
            cpuDid = (cofvidEax >> 6) & 7;
            cpuFid = cofvidEax & 0x1F;
            return (0.5f * (cpuFid + 0x10)) / (1 << (int) cpuDid);
        }
        // 0x20:
        return 1f;
    }
}

/*
 192.168.35.71 (dual-piledriver) heeft de piledrivers en supermicro mobo (tijdelijk omgewisseld 29-5-2013)
 192.168.35.59 (dell-r815) heeft de bulldozers en dell mobo

 Monitoring Interlagos B2

 Temperature readings are way too low, I get around 10 degrees Celsius per node:

 uint pciAddress = Ring0.GetPciAddress(0x0, pciDeviceId, 0x3);
 if (Ring0.ReadPciConfig(pciAddress, REPORTED_TEMPERATURE_CONTROL_REGISTER, out value)) {
 temp = ((value >> 21) & 0x7FF) / 8.0f;
 }

 pciDeviceId  = 0x18, 0x19, 0x1a, 0x1b, …
 GetPciAddress on bus 0, pciDevice, function 3
 REPORTED_TEMPERATURE_CONTROL_REGISTER = 0xA4

 I followed the BKDG.

 How do I calculate the percentage of the time that a core was in c0, c3, c6 and c7?

 %c0 = unhaltedRefCycles of the core / TSC

 Core reference clocks not halted should be available through Lightweight Profiling, but when I check the capabilities (CPUID Fn8000_001C_EAX) I see that there are non available.

 Is there another way to calculate the %c0?

 Intel has specific MSRs for c3, c6 and c7, does AMD have something like that?


 From: "Detwiler, Michael" <michael.detwiler@amd.com>
 Date: May 15, 2013 4:10 PM
 Subject: RE: Making sure that AMD is well supported ….
 To: "Johan De Gelas" <johan@anandtech.com>
 Cc: 

 Johan,
 Below is the response from the engineers:
 1) Reading out power
 Is APM enabled?  (D18F4x15C[ApmMasterEn]) -- ApmMasterEn == bit 7 + andere, enkele die setten doet niks --> CPB op auto zetten in de BIOS (AMD TurboBoost) --> temp en energy readings zijn nu goed voor dual-piledriver; voor de Dell, not so good, ligt dit aan buldozer? --> cpu's eens wisselen; Airco's staan op 20 graden C, thermometer geeft 23,8 aan.
 Schrijven naar een PCI device lukt niet voor de dual-piledriver, optie in BIOS?

 ACPI en Fan Speed Control kan interessant zijn in ons energie verhaal.
 
 2) Monitoring temperature  
 ***Michael note: He sent me a link to an internal wiki for something called Tcontrol.  Not sure if you’re familiar with that.  I’m trying to find whatever information we have that can be shared with you.
 
 3) Calculating the percentage of the time that a core was in c0, c3, c6…
 I don’t think Orochi has dedicated perf counters for this.  However, you can get this information from NBPMCx6E6 and NBPMCx6EA.  Perf counters are accessed through a MSR.
 
 
 Thanks,
 Michael
 
 Michael Detwiler
 AMD Server Product Marketing
 Office: 512-602-1553
 Mobile: 512-560-6434

 ------------

 Power readings werken, CBP op auto gezet in de BIOS van de dual-piledriver  (wegschrijven naar PCI devices vanuit HMT lukt niet (SuperMicro mobo)), dus geen vragen hier, misschien wel een dankjewel.

 Temp readings berekening die nu in HMT zit moet worden gecontroleerd, is er al nieuws over die TControl?

 BKDG zegt niets over MSRs NBPMCx6E6 and NBPMCx6EA voor het percentage dat een core in een bepaalde c-state is, hoe moet de berekening er dan uit zien?

 */
