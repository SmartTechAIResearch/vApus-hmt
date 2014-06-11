Building hmtproxy:
gcc -c -fPIC HMTProxy.cpp
gcc --shared HMTProxy.o -o HMTProxy.so

Building my_sudo (called from within HMTProxy.so)
gcc my_sudo.cpp my_sudo
sudo chown root:<group, for example didjeeh> my_sudo
sudo chmod 4550 my_sudo

4550 = magic number to run hmtproxy stuff as sudo
changing owner and rights does not work in a vm share.
