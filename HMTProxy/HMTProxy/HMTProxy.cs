/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG
 * 
 * Author(s):
 *    Dieter Vandroemme
 */
using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;

namespace HMTProxy {
    public class HMTProxy {
        private static string _resolvePath;
        private static int _logicalCores, _logicalCoresPerPackage, _physicalCores, _packages;

        private static uint _msrEAX, _msrEDX;

        /// <summary>
        /// Sets the resolve path, sets the current process priority class to high and installs the winring0 driver
        /// </summary>
        /// <param name="resolvePath"></param>
        [RGiesecke.DllExport.DllExport]
        public static void init(string resolvePath) {
            if (_resolvePath == null) {
                _resolvePath = resolvePath;

                Process.GetCurrentProcess().PriorityClass = ProcessPriorityClass.High;

                AppDomain.CurrentDomain.AssemblyResolve += CurrentDomain_AssemblyResolve;
                Process.Start(Path.Combine(_resolvePath, "installdriver.exe"), "-installonly");

                Ring0.Open();
            }
        }

        private static Assembly CurrentDomain_AssemblyResolve(object sender, ResolveEventArgs args) {
            var assemblyName = new AssemblyName(args.Name);

            if (!assemblyName.Name.EndsWith(".resources")) {
                string fileName = Path.Combine(_resolvePath, assemblyName.Name + ".dll");
                try {
                    return Assembly.LoadFile(fileName);
                } catch {
                    //If the assembly is in a share.
                    return Assembly.Load(File.ReadAllBytes(fileName));
                }
            }

            return null;
        }

        [RGiesecke.DllExport.DllExport]
        public static int getLogicalCores() {
            if (_logicalCores == 0) _logicalCores = Convert.ToInt32(GetActiveProcessorCount(0xFFFF)); //to include all processor groups
            return _logicalCores;
        }

        [RGiesecke.DllExport.DllExport]
        public static int getLogicalCoresPerPackage() {
            if (_logicalCoresPerPackage == 0) _logicalCoresPerPackage = Convert.ToInt32(GetActiveProcessorCount(0)); //The same for all groups.
            return _logicalCoresPerPackage;
        }

        [RGiesecke.DllExport.DllExport]
        public static int getPhysicalCores() {
            if (_physicalCores == 0) DetermineNumberOfPhysicalCoresAndPackages();
            return _physicalCores;
        }
        [RGiesecke.DllExport.DllExport]
        public static int getPackages() {
            if (_packages == 0) DetermineNumberOfPhysicalCoresAndPackages();
            return _packages;
        }

        /// <summary>
        /// Calculates the number of physical processors and will also calculate the number of sockets.
        /// </summary>
        /// <returns>number of physical cores</returns>
        private static void DetermineNumberOfPhysicalCoresAndPackages() {
            foreach (var item in new System.Management.ManagementObjectSearcher("Select * from Win32_Processor").Get()) {
                _physicalCores += int.Parse(item["NumberOfCores"].ToString());
                _packages++;
            }
        }


        [RGiesecke.DllExport.DllExport]
        public static uint getMSREAX() { return _msrEAX; }

        [RGiesecke.DllExport.DllExport]
        public static uint getMSREDX() { return _msrEDX; }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="msr"></param>
        /// <param name="highBit"></param>
        /// <param name="lowBit"></param>
        /// <param name="core">Set thread affinity.</param>
        /// <returns></returns>
        [RGiesecke.DllExport.DllExport]
        public static string readMSRTx(uint msr, int highBit, int lowBit, int core) {
            SetCoreAffinity(core);
            return sharedReadMSR(msr, highBit, lowBit);
        }

        /// <summary>
        /// </summary>
        /// <param name="msr"></param>
        /// <param name="highBit"></param>
        /// <param name="lowBit"></param>
        /// <returns></returns>
        [RGiesecke.DllExport.DllExport]
        public static string readMSR(uint msr, int highBit, int lowBit) {
            return sharedReadMSR(msr, highBit, lowBit);
        }
        private static string sharedReadMSR(uint msr, int highBit, int lowBit) {
            string error = string.Empty;

            //uint eax, edx;
            if (!Ring0.Rdmsr(msr, out _msrEAX, out _msrEDX)) {
                error = "Error reading MSR 0x" + msr.ToString("X8") + " (" + msr + ")";

                try { error += "\n" + getLastError(); } catch { }
                //ulong value = ((ulong)edx << 32 | eax);

                ////check if we need to do some parsing of bits to get what we want
                //if (highBit == 64 && lowBit == 0)
                //    return value;

                ////construct the ulong with the bits we're interested in
                //ulong bits = 0;
                //for (int i = lowBit; i < highBit; i++)
                //    bits += (ulong)Math.Pow(2, i);

                //ulong interestedValue = (value & bits) >> lowBit;

                //return interestedValue;
            }
            //return 0;

            return error;
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="msr"></param>
        /// <param name="value"></param>
        /// <param name="core">Set thread affinity.</param>
        /// <returns>error if any</returns>
        [RGiesecke.DllExport.DllExport]
        public static string writeMSRTx(uint msr, uint eax, uint edx, int core) {
            SetCoreAffinity(core);
            return sharedWriteMSR(msr, eax, edx);
        }
        /// <summary>
        /// </summary>
        /// <param name="msr"></param>
        /// <param name="value"></param>
        /// <returns>error if any</returns>
        [RGiesecke.DllExport.DllExport]
        public static string writeMSR(uint msr, uint eax, uint edx) {
            return sharedWriteMSR(msr, eax, edx);
        }

        private static string sharedWriteMSR(uint msr, uint eax, uint edx) {
            string error = string.Empty;

            //uint eax, edx;

            //edx = (uint)(value >> 32);
            //eax = (uint)(value - ((ulong)edx << 32));

            if (!Ring0.Wrmsr(msr, eax, edx)) {
                error = "Error writing to MSR 0x" + msr.ToString("X8") + " (" + msr + ")";

                try { error += "\n" + getLastError(); } catch { }

                error += "\nIf you get all counters there should not be a problem.";
            }
            return error;
        }

        private static string getLastError() {
            ulong errorCode = Ring0.GetLastError();
            return "Win32Exception 0x" + errorCode.ToString("X8") + " (" + errorCode + "): " + (new Win32Exception((int)errorCode)).Message;//error codes http://msdn.microsoft.com/en-us/library/cc231199.aspx
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="core">Zero-based</param>
        private static void SetCoreAffinity(int core) {
            var process = Process.GetCurrentProcess();
            var affinity = new IntPtr((long)Math.Pow(2, core));
            if (process.ProcessorAffinity != affinity)
                process.ProcessorAffinity = affinity;
        }

        [RGiesecke.DllExport.DllExport]
        public static uint getPciAddress(byte bus, byte device, byte function) {
            return Ring0.GetPciAddress(bus, device, function);
        }

        [RGiesecke.DllExport.DllExport]
        public static uint readPciConfig(uint pciAddress, uint regAddress) {
            uint value;
            Ring0.ReadPciConfig(pciAddress, regAddress, out value);
            return value;
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="pciAddress"></param>
        /// <param name="regAddress"></param>
        /// <param name="value"></param>
        /// <returns>error if any</returns>
        [RGiesecke.DllExport.DllExport]
        public static string writePciConfig(uint pciAddress, uint regAddress, uint value) {
            string error = string.Empty;
            if (!Ring0.WritePciConfig(pciAddress, regAddress, value)) {
                error = "Error while writing to pci address 0x" + pciAddress.ToString("X8") + " (" + pciAddress + ")";

                try {
                    ulong errorCode = Ring0.GetLastError();
                    error += "\nWin32Exception 0x" + errorCode.ToString("X8") + " (" + errorCode + "): " + (new Win32Exception((int)errorCode)).Message;//error codes http://msdn.microsoft.com/en-us/library/cc231199.aspx
                } catch { }

                error += "\nIf you get all counters there should not be a problem.";
            }
            return error;
        }

        /*
                 protected bool GetACPICStatesAndWindowsFrequencies(out short[] acpiStates, out short[] windowsFrequencies) {
            bool executedCorrectly = false;
            //The lpInBuffer parameter must be NULL; otherwise the function returns ERROR_INVALID_PARAMETER.
            //The lpOutputBuffer buffer receives one PROCESSOR_POWER_INFORMATION structure for each processor that is installed on the system. Use the GetSystemInfo function to retrieve the number of processors.

            acpiStates = new short[_logicalCpus];
            windowsFrequencies = new short[_logicalCpus];

            Type typeOfStruct = typeof(PROCESSOR_POWER_INFORMATION);
            int sizeOfStruct = Marshal.SizeOf(typeOfStruct);

            IntPtr ptr = Marshal.AllocCoTaskMem(sizeOfStruct * _logicalCpus);
            IntPtr thisProcessor = Marshal.AllocCoTaskMem(sizeOfStruct);

            try {
                uint status = CallNtPowerInformation(11, IntPtr.Zero, 0, ptr, (uint)(sizeOfStruct * _logicalCpus)); //SystemBatteryState = 5, ProcessorPowerInformation = 11
                if (status == 0) //= NT_STATUS_SUCCESS
                {
                    byte[] bytes = new byte[sizeOfStruct * _logicalCpus];
                    Marshal.Copy(ptr, bytes, 0, sizeOfStruct * _logicalCpus);

                    //parse each processor
                    for (int logical_cpu = 0; logical_cpu < _logicalCpus; logical_cpu++) {
                        Marshal.Copy(bytes, sizeOfStruct * logical_cpu, thisProcessor, sizeOfStruct);
                        PROCESSOR_POWER_INFORMATION info = ((PROCESSOR_POWER_INFORMATION)Marshal.PtrToStructure(thisProcessor, typeOfStruct));
                        acpiStates[logical_cpu] = (short)info.CurrentIdleState;
                        windowsFrequencies[logical_cpu] = (short)info.CurrentMhz;
                        //Debug.WriteLine(logical_cpu + ": " + acpiStates[logical_cpu]);
                    }
                } else if (status == 3221225485) //C000000D 
                {
                    Console.WriteLine("An invalid parameter was passed to a service or function.");
                } else {
                    Console.WriteLine("CallNtPowerInformation failed. Status: " + status + ". A list of all status codes can be found in hex format at http://nologs.com/ntstatus.html");
                }

                //indicate we went through this
                executedCorrectly = true;
            } catch (Exception ex) {
                Console.WriteLine(ex.Message);
            } finally {
                Marshal.FreeCoTaskMem(ptr);
                Marshal.FreeCoTaskMem(thisProcessor);
            }
            return executedCorrectly;

        }

         */

        /// <summary>
        /// to know how much cores a group contains
        /// </summary>
        /// <param name="groupNumber">the group number if any, or ALL_PROCESSOR_GROUPS (0xffff) for every group</param>
        /// <returns></returns>
        [DllImport("kernel32.dll")]
        private static extern uint GetActiveProcessorCount(ushort groupNumber); //Must be at the end of the file, otherwise RGiesecke.DllExport.DllExport marked functions will nog get linked. 
    }
}
