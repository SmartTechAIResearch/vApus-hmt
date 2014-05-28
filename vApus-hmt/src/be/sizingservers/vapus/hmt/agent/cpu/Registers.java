/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package be.sizingservers.vapus.hmt.agent.cpu;

/**
 *
 * @author Didjeeh
 */
public class Registers {

    //Intel & AMD
    public static final long MSR_TSC = 0x10; //Timestamp Counter

    //Intel
    public static final int MSR_PLATFORM_INFO = 0xCE;
    public static final int MSR_MPERF = 0xE7;
    public static final int MSR_APERF = 0xE8;
    public static final int MSR_CORE_C3_RESIDENCY = 0x3FC;
    public static final int MSR_CORE_C6_RESIDENCY = 0x3FD;
    public static final int MSR_CORE_C7_RESIDENCY = 0x3FE; //SandyBridge
    public static final int MSR_PKG_C2_RESIDENCY = 0x60D; //SandyBridge
    public static final int MSR_PKG_C3_RESIDENCY = 0x3F8;
    public static final int MSR_PKG_C6_RESIDENCY = 0x3F9;
    public static final int MSR_PKG_C7_RESIDENCY = 0x3FA; //SandyBridge
    public static final int MSR_TEMPERATURE_TARGET = 0x1A2;
    public static final int MSR_TURBO_RATIO_LIMIT = 0x1AD;
    public static final int MSR_IA32_FIXED_CTR1 = 0x30A;
    public static final int MSR_IA32_FIXED_CTR2 = 0x30B;
    public static final int MSR_IA32_PERF_GLOBAL_CTRL = 0x38F;
    public static final int MSR_IA32_FIXED_CTR_CTL = 0x38D;
    public static final int MSR_IA32_THERM_STATUS = 0x19C;
    public static final int MSR_IA32_MISC_ENABLE = 0x1A0;
    public static final int MSR_Logical_Destination_Register = 0x80D;
    public static final int MSR_IA32_PERFEVTSEL0_ADDR = 0x186;  //holds the first programmable event, +1 for each available event
    public static final int MSR_IA32_PMC0 = 0xC1; //Performance monitor counter.
    public static final int MSR_RAPL_POWER_UNIT = 0x606;
    public static final int MSR_PKG_ENERGY_STATUS = 0x611;
    public static final int MSR_PP0_ENERGY_STATUS = 0x639;
    public static final int MSR_PP1_ENERGY_STATUS = 0x641;
    public static final int MSR_DRAM_ENERGY_STATUS = 0x619;

    //AMD
    public static final long COFVID_STATUS = 0xC0010071;
    public static final byte PCI_BUS = 0;
    public static final long PCI_BASE_DEVICE = 0x18;
    public static final byte DEVICE_VENDOR_ID_REGISTER = 0;
    public static final int AMD_VENDOR_ID = 0x1022;
    /* D18F4 */
    public static final long REG_BASE_PROCESSOR_TDP = 0x1b8; //Upper 16 bits = base tdp, lower 16 = processor tdp
    /* D18F5 */
    public static final long REG_TDP_RUNNING_AVERAGE = 0xe0;
    public static final long REG_TDP_LIMIT3 = 0xe8;
    public static final long REPORTED_TEMPERATURE_CONTROL_REGISTER = 0xA4;
}
