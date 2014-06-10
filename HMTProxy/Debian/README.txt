gcc -c -fPIC HMTProxy.cpp
gcc --shared HMTProxy.o -o HMTProxy.so