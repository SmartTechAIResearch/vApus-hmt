/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.hmt.agent.cpu;

/**
 *
 * @author Didjeeh
 */
public interface CPUImplementation {
    public long getFamily();
    public long getModel();
    public float getBusClockFrequencyInMhz();
}
