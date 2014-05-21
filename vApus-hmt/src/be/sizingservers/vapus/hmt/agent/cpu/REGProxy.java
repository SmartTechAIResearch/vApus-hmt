/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package be.sizingservers.vapus.hmt.agent.cpu;

import com.sun.jna.Library;
import com.sun.jna.Native;
import java.math.BigInteger;

/**
 *
 * @author Didjeeh
 */
public interface REGProxy extends Library {
     REGProxy INSTANCE = (REGProxy) Native.loadLibrary("/reg.so", REGProxy.class);
     
     /**
      * Write an msr value.
      * @param msr
      * @param value BigInteger == unsigned 64-bit integer 
      */
     public void rwmsr(long msr, BigInteger value);
    
     /**
      * Read an msr value.
      * @param msr
      * @return BigInteger == unsigned 64-bit integer 
      */
     public BigInteger rdmsr(long msr);
}
