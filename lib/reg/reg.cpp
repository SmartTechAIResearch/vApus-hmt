#ifndef REG_H
#define REG_H

#ifdef _WIN32
#include <intrin.h>
#include <limits.h>
#define EXPORTIT __declspec( dllexport )
typedef unsigned __int32  uint32_t;
#else
#include <stdint.h>
#define EXPORTIT
#endif

extern "C" EXPORTIT void wrmsr(uint32_t msr_id, uint64_t msr_value) {
	#ifdef _WIN32
	#else
		asm volatile ( "wrmsr" : : "c" (msr_id), "A" (msr_value) );
	#endif
}

extern "C" EXPORTIT uint64_t rdmsr(uint32_t msr_id) {
    uint64_t msr_value;
	#ifdef _WIN32
	#else
		asm volatile ( "rdmsr" : "=A" (msr_value) : "c" (msr_id) );
    #endif
	return msr_value;
}

#endif // REG_H