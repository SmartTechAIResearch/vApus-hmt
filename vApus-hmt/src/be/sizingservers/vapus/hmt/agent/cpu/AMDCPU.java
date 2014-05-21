/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package be.sizingservers.vapus.hmt.agent.cpu;

import be.sizingservers.vapus.agent.util.Entities;
import be.sizingservers.vapus.agent.util.Entity;

/**
 *
 * @author Didjeeh
 */
public class AMDCPU implements CPUImplementation {

    private final long family, model;
    private boolean magnyCours, bulldozer, piledriver; //Piledriver BKDG is available, but they have an internal document with info we need for getting C-states. They do not want to give it to us.
    private Entities wih;

    public AMDCPU(long family, long model) {
        this.family = family;
        this.model = model;
        init();
    }

    private void init() {
        determineArchitecture();
        determineWih();
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

    private void determineWih(){
        this.wih = new Entities();
        Entity entity = new Entity("foo", true);
        
        int logicalCores = Runtime.getRuntime().availableProcessors();
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
        return 200f;
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
