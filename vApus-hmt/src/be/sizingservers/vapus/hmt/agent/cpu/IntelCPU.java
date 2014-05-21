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
public class IntelCPU implements CPUImplementation {

    private final long family, model;
    private boolean sandyBridge, ivyBridge;

    public IntelCPU(long family, long model) {
        this.family = family;
        this.model = model;
        init();
    }

    private void init() {
        determineArchitecture();
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

    @Override
    public long getFamily() {
        return this.family;
    }

    @Override
    public long getModel() {
        return this.model;
    }

    @Override
    public float getBusClockFrequencyInMhz() {
        if (this.sandyBridge || this.ivyBridge) {
            return 100f;
        }
        return 133.33f;
    }
}
