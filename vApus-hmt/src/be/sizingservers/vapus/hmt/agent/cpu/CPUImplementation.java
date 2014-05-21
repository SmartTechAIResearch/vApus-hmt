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
public interface CPUImplementation {
    public long getFamily();
    public long getModel();
    public float getBusClockFrequencyInMhz();
}
