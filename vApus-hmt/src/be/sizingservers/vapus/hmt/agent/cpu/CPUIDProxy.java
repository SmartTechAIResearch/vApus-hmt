/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package be.sizingservers.vapus.hmt.agent.cpu;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 *
 * @author Didjeeh
 */
public interface CPUIDProxy extends Library {
    CPUIDProxy INSTANCE = (CPUIDProxy) Native.loadLibrary("/cpuid.so", CPUIDProxy.class);
    
    public long load(long i);
    public long EAX();
    public long EBX();
    public long ECX();
    public long EDX();
}
