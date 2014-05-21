/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package be.sizingservers.vapus.hmt.agent.cpu;

import java.math.BigInteger;

/**
 *
 * @author Didjeeh
 */
public class REG {

    public static BigInteger readMSR(long msr, int highBit, int lowBit) {
        BigInteger value = REGProxy.INSTANCE.rdmsr(msr);

        //check if we need to do some parsing of bits to get what we want
        if (highBit == 64 && lowBit == 0) {
            return value;
        }

        //construct the big integer with the bits we're interested in
        BigInteger bits = BigInteger.ZERO;
        for (int i = lowBit; i < highBit; i++) {
            bits.add(BigInteger.valueOf((long) Math.pow(2, i)));
        }

        value = value.and(bits).shiftRight(lowBit);

        return value;
    }

    public static void writeMSR(long msr, BigInteger value) {
//        long eax, edx;
//        edx = (value.shiftRight(32)).longValue();
//        eax = (value.subtract(BigInteger.valueOf(edx << 32))).longValue();
//        if (!Ring0.Wrmsr(registerAdress, eax, edx)) {
        REGProxy.INSTANCE.rwmsr(msr, value);
    }
}
