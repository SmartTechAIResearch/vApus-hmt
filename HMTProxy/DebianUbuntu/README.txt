Building hmtproxy:
gcc -c -fPIC HMTProxy.cpp
gcc --shared HMTProxy.o -o HMTProxy.so

The agent must be run with elevated rights, otherwise reading msrs and such will not work.
