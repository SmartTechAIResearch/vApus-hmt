gcc -c -fPIC cpuid.cpp
gcc --shared cpuid.o -o cpuid.so

cygwin1.dll needed for running on Windows.