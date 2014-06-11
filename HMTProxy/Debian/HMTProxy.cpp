#ifndef HMTPROXY_H
#define HMTPROXY_H

#define __USE_ISOC99 //to define snprintf from stdio.h
#define __STDC_LIMIT_MACROS //to define UINT64_MAX in stdint.h
#define __STDC_FORMAT_MACROS //to define PRIu64

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h> //standard integer
#include <errno.h> //error handling
#include <string.h> //string operations
#include <fcntl.h> //filecontrol
#include <sstream> //char to unint64_t conversion
#include <inttypes.h>
#include <stdexcept> //Exception handling

using namespace std;
int m_logicalCores = 0, m_physicalCores = 0, m_packages = 0;
long m_msrEAX, m_msrEDX;

extern "C" void init(char* resolvePath){
	system("modprobe msr");
}

char* readString(const char *command) {
	const char *s = " > /tmp/tmpHMTProxy";	
	char buffer [strlen(command) + strlen(s)];
	
	sprintf(buffer, "%s%s", command, s);


	char *tmp = (char*) malloc(100);
		
	try {
		FILE *tmp_file = fopen("/tmp/tmpHMTProxy", "r");
		fgets(tmp, 30, tmp_file);
		fclose(tmp_file);
	} catch(...) {
		free(tmp);
		throw runtime_error(string("Failed to read the output from the temp file."));
	}
	return tmp;
}
int readInt(const char *command) {	
	char *tmp = readString(command);
	int i = atoi(tmp);
	free(tmp);
	return i;
}
long readLong(const char *command) {
	char *tmp = readString(command);
	long l;
	istringstream(tmp) >> l;
	free(tmp);
	return l;
}
uint64_t readUnsignedLong(const char *command) {
	char *tmp = readString(command);
	uint64_t ul;
	istringstream(tmp) >> ul;
	free(tmp);
	return ul;
}

extern "C" int getLogicalCores() {
	if (m_logicalCores == 0)
		m_logicalCores = readInt("grep \"processor\" /proc/cpuinfo |wc -l");
    return m_logicalCores;
}
extern "C" int getPackages() {
	if(m_packages == 0) 
		m_packages = readInt("grep \"physical id\" /proc/cpuinfo | sort | uniq |wc -l");
    return m_packages;
}
extern "C" int getPhysicalCores(){
	if (m_physicalCores == 0) {
		int cores = readInt("grep -m 1 \"cpu cores.*:.[0-9]*\" /proc/cpuinfo | cut -d':' -f2 | tr -d ' '");

		m_physicalCores = getPackages() * cores;
	}
	return m_physicalCores;
}



//msr-tools
extern "C" long getMSREAX() { return m_msrEAX; }
extern "C" long getMSREDX() { return m_msrEDX; }

void toEAX_EDX(uint64_t ul){
	m_msrEDX = ul>>32;
	m_msrEAX = ul - (m_msrEDX<<32); 
}
extern "C" char* readMSRTx(long msr, int core){
	try {
		const char *s = "rdmsr -p";	
		char buffer [strlen(s) + 100];
	
		sprintf(buffer, "%s%d %ld", s, core, msr);
	
		uint64_t ul = readUnsignedLong(buffer);
		toEAX_EDX(ul);
	} catch (...) {
		stringstream stream;
		stream << "readMSRTx " << msr << ", " << core << " failed.";
		return (char*) stream.str().c_str();
	}
	return (char*) "";
}
extern "C" char* readMSR(long msr) {
	try {
		const char *s = "rdmsr";	
		char buffer [strlen(s) + 100];
	
		sprintf(buffer, "%s %ld", s, msr);
	
		uint64_t ul = readUnsignedLong(buffer);
		toEAX_EDX(ul);
	} catch (...) {
		stringstream stream;
		stream << "readMSRTx " << msr << " failed.";
		return (char*) stream.str().c_str();
	}
	return (char*) "";
}

uint64_t fromEAX_EDX(long eax, long edx) {
	return (edx<<32) | eax; 
}
extern "C" char* writeMSRTx(long msr, long eax, long edx, int core) {
	try {
		uint64_t ul = fromEAX_EDX(eax, edx);
	
		const char *s = "wrmsr -p";	
		char buffer [strlen(s) + 100];
		
		sprintf(buffer, "%s%d %ld %" PRIu64, s, core, msr, ul);
		system(buffer);
	} catch (...) {
		stringstream stream;
		stream << "writeMSRTx " << msr << core << " failed.";
		return (char*) stream.str().c_str();
	}
	return (char*) "";
}
extern "C" char* writeMSR(long msr, long eax, long edx) {
	try {
		uint64_t ul = fromEAX_EDX(eax, edx);
	
		const char *s = "wrmsr";	
		char buffer [strlen(s) + 100];
		
		sprintf(buffer, "%s %ld %" PRIu64, s, msr, ul);
		system(buffer);
	} catch (...) {
		stringstream stream;
		stream << "writeMSR " << msr << " failed.";
		return (char*) stream.str().c_str();
	}
	return (char*) "";
}

//setpci
extern "C" long readPciConfig(int bus, int device, int function, long regAddress){
	const char *s = "setpci -s";	
	char buffer [strlen(s) + 100];
	
	sprintf(buffer, "%s%i:%i.%i %ld.L", s, bus, device, function, regAddress);
		
	return readLong(buffer);
}
extern "C" char* writePciConfig(short bus, short device, short function, long regAddress, long value){
	const char *s = "setpci -s";	
	char buffer [strlen(s) + 100];
	
	sprintf(buffer, "%s%i:%i.%i %ld.L=%ld", s, bus, device, function, regAddress, value);
	
	system(buffer);
	return (char*) "";
}

extern "C" int getACPICState(int core, bool hyperThreading){
	char str[300], temp[130], filename[200];

    sprintf(filename, "/proc/acpi/processor/CPU%X/power", core);

    FILE *power_fp = fopen(filename, "r");

    if (power_fp == NULL)
        return 0;

    if (power_fp) {
        fgets(str, 100, power_fp); // active state
        char temp[80];
        temp[0] = '\0';
        strncpy(temp, str + 26, 2);

        fclose(power_fp);

        return atoi(temp);
    }
    return -1;
}

#endif // HMTPROXY_H