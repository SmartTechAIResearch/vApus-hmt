#ifndef CPUID_H
#define CPUID_H

#ifdef _WIN32
#include <intrin.h>
#include <limits.h>
#define EXPORTIT __declspec( dllexport )
typedef unsigned __int32  uint32_t;
#else
#include <stdint.h>
#define EXPORTIT
#endif

uint32_t regs[4];

extern "C" EXPORTIT void load(unsigned i) {
#ifdef _WIN32
    __cpuid((int *)regs, (int)i);
#else
    asm volatile
      ("cpuid" : "=a" (regs[0]), "=b" (regs[1]), "=c" (regs[2]), "=d" (regs[3])
       : "a" (i), "c" (0));
    // ECX is set to zero for CPUID function 4
#endif
}

extern "C" EXPORTIT uint32_t EAX() {return regs[0];}
extern "C" EXPORTIT uint32_t EBX() {return regs[1];}
extern "C" EXPORTIT uint32_t ECX() {return regs[2];}
extern "C" EXPORTIT uint32_t EDX() {return regs[3];}

#endif // CPUID_H