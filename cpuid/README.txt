gcc -c -fPIC cpuid.cpp
gcc --shared cpuid.o -o cpuid.so

-fPIC not needed when compiling on Windows via Cygwin. cygwin1.dll needed for running on Windows.