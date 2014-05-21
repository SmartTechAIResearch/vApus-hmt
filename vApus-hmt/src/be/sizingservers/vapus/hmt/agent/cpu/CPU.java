/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package be.sizingservers.vapus.hmt.agent.cpu;

import java.nio.ByteBuffer;

/**
 *
 * @author Didjeeh
 */
public class CPU implements CPUImplementation {

    private String vendor;

    private CPUImplementation cpuImplementation;

    public CPU() {
        loadCPUID();
    }

    private void loadCPUID() {

        //Function 0 is used to get the Vendor String from the CPU. It also tells us the maximum function supported by cpuid. Every cpuid-supporting CPU will allow at least this function. I'll describe as many functions as I find information about, but please keep in mind that not all CPUs will handle all function values.
        //It is stored in ASCII format as found on wikipedia http://en.wikipedia.org/wiki/CPUID
        //When called, eax gets the maximum function call value.
        //ebx gets the first 4 bytes of the Vendor String.
        //edx gets the second 4 bytes of the Vendor String.
        //ecx gets the last 4 bytes of the Vendor String.
        CPUIDProxy.INSTANCE.load(0x0);

        this.vendor = convert(CPUIDProxy.INSTANCE.EBX()) + convert(CPUIDProxy.INSTANCE.EDX()) + convert(CPUIDProxy.INSTANCE.ECX());

        //Function 0x1 returns the Processor Family, Model, and Stepping information in eax. edx gets the Standard Feature Flags.
        //bits (eax)	field
        //0-3	Stepping number
        //4-7	Model number
        //8-11	Family number
        //12-13	Processor Type
        //16-19	Extended Model Number
        //20-27	Extended Family Number
        CPUIDProxy.INSTANCE.load(0x00000001);

        //Intel has suggested applications to display the family of a CPU as the sum of the "Family" and the "Extended Family" fields shown above, and the model as the sum of the "Model" and the 4-bit left-shifted "Extended Model" fields.[4]
        //AMD recommends the same only if "Family" is equal to 15 (i.e. all bits set to 1). If "Family" is lower than 15, only the "Family" and "Model" fields should be used while the "Extended Family" and "Extended Model" bits are reserved. If "Family" is set to 15, then "Extended Family" and the 4-bit left-shifted "Extended Model" should be added to the respective base values.[5]
        //0xF = 15 = 1111 necessary to filter out the wanted bits
        long family = (CPUIDProxy.INSTANCE.EAX() >> 8) & 0xF; //family
        long model = (CPUIDProxy.INSTANCE.EAX() >> 4) & 0xF; //model

        if (this.vendor.equals("GenuineIntel") || family >= 15) {
            family += (CPUIDProxy.INSTANCE.EAX() >> 20) & 0xFF; //extended family
            model += ((CPUIDProxy.INSTANCE.EAX() >> 16) & 0xF) << 4; //extended model
        }

        int logicalCores = Runtime.getRuntime().availableProcessors(); 

        CPUIDProxy.INSTANCE.load(0x4);
        long cores = ((CPUIDProxy.INSTANCE.EAX() >> 26) & 0x3F) + 1;//Niet betrouwbaar volgens het internet. Zeer spijtig.

        if (this.vendor.equals("GenuineIntel")) {
            this.cpuImplementation = new IntelCPU(family, model);
        } else {
            this.cpuImplementation = new AMDCPU(family, model);
        }
    }

    private String convert(long l) {
        return new StringBuilder(new String(ByteBuffer.allocate(Long.SIZE / 8).putLong(l).array()).trim()).reverse().toString();
    }

    public String getVendor() {
        return this.vendor;
    }

    @Override
    public long getFamily() {
        return cpuImplementation.getFamily();
    }

    @Override
    public long getModel() {
        return cpuImplementation.getModel();
    }

    @Override
    public float getBusClockFrequencyInMhz() {
        return cpuImplementation.getBusClockFrequencyInMhz();
    }
}
