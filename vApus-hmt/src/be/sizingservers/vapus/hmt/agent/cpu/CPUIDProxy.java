/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.hmt.agent.cpu;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * JNA proxy to a binary that works on Windows and Linux.
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
