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
        private static int _logicalCores, _physicalCores, _packages;

        private static uint _msrEAX, _msrEDX;

        private static int[] _acpiStates, _windowsFrequencies;

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
            return sharedGetLogicalCores();
        }
        private static int sharedGetLogicalCores() {
            if (_logicalCores == 0) _logicalCores = Convert.ToInt32(GetActiveProcessorCount(0xFFFF)); //to include all processor groups
            return _logicalCores;
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
            foreach (var item in new System.Management.ManagementObjectSearcher("Select NumberOfCores from Win32_Processor").Get()) {
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
        public static string readMSRTx(uint msr, int core) {
            setCoreAffinity(core);
            return sharedReadMSR(msr);
        }

        /// <summary>
        /// </summary>
        /// <param name="msr"></param>
        /// <param name="highBit"></param>
        /// <param name="lowBit"></param>
        /// <returns></returns>
        [RGiesecke.DllExport.DllExport]
        public static string readMSR(uint msr) {
            return sharedReadMSR(msr);
        }
        private static string sharedReadMSR(uint msr) {
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
            setCoreAffinity(core);
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
        public static void setCoreAffinity(int core) {
            var process = Process.GetCurrentProcess();
            var affinity = new IntPtr((long)Math.Pow(2, core));
            if (process.ProcessorAffinity != affinity)
                process.ProcessorAffinity = affinity;
        }

        [RGiesecke.DllExport.DllExport]
        public static uint readPciConfig(int bus, int device, int function, uint regAddress) {
            uint value;
            Ring0.ReadPciConfig(Ring0.GetPciAddress(Convert.ToByte(bus), Convert.ToByte(device), Convert.ToByte(function)), regAddress, out value);
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
        public static string writePciConfig(int bus, int device, int function, uint regAddress, uint value) {
            string error = string.Empty;
            uint pciAddress;

            if (!Ring0.ReadPciConfig(Ring0.GetPciAddress(Convert.ToByte(bus), Convert.ToByte(device), Convert.ToByte(function)), regAddress, out pciAddress) || !Ring0.WritePciConfig(pciAddress, regAddress, value)) {
                error = "Error while writing to pci address 0x" + pciAddress.ToString("X8") + " (" + pciAddress + ")";

                try {
                    ulong errorCode = Ring0.GetLastError();
                    error += "\nWin32Exception 0x" + errorCode.ToString("X8") + " (" + errorCode + "): " + (new Win32Exception((int)errorCode)).Message;//error codes http://msdn.microsoft.com/en-us/library/cc231199.aspx
                } catch { }

                error += "\nIf you get all counters there should not be a problem.";
            }
            return error;
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="core"></param>
        /// <param name="hyperThreading"></param>
        /// <returns></returns>
        [RGiesecke.DllExport.DllExport]
        public static int getACPICState(int core, bool hyperThreading) {
            if (_acpiStates == null)
                CalculateACPICStatesAndWindowsFreqs();
            return hyperThreading ? Math.Min(_acpiStates[core * 2], _acpiStates[(core * 2) + 1]) : _acpiStates[core];
        }
        /// <summary>
        /// 
        /// </summary>
        /// <param name="core"></param>
        /// <param name="hyperThreading"></param>
        /// <returns></returns>
        [RGiesecke.DllExport.DllExport]
        public static int getWindowsFrequency(int core, bool hyperThreading) {
            if (_windowsFrequencies == null)
                CalculateACPICStatesAndWindowsFreqs();
            return hyperThreading ? Math.Min(_windowsFrequencies[core * 2], _windowsFrequencies[(core * 2) + 1]) : _windowsFrequencies[core];
        }

        /// <summary>
        /// 
        /// </summary>
        /// <returns>Error if any</returns>
        private static string CalculateACPICStatesAndWindowsFreqs() { 
            string error = string.Empty;
            //The lpInBuffer parameter must be NULL; otherwise the function returns ERROR_INVALID_PARAMETER.
            //The lpOutputBuffer buffer receives one PROCESSOR_POWER_INFORMATION structure for each processor that is installed on the system. Use the GetSystemInfo function to retrieve the number of processors.

            _acpiStates = new int[sharedGetLogicalCores()];
            _windowsFrequencies = new int[_logicalCores];

            Type typeOfStruct = typeof(PROCESSOR_POWER_INFORMATION);
            int sizeOfStruct = Marshal.SizeOf(typeOfStruct);

            IntPtr ptr = Marshal.AllocCoTaskMem(sizeOfStruct * _logicalCores);
            IntPtr thisProcessor = Marshal.AllocCoTaskMem(sizeOfStruct);

            try {
                uint status = CallNtPowerInformation(11, IntPtr.Zero, 0, ptr, (uint)(sizeOfStruct * _logicalCores)); //SystemBatteryState = 5, ProcessorPowerInformation = 11
                if (status == 0) //= NT_STATUS_SUCCESS
                {
                    byte[] bytes = new byte[sizeOfStruct * _logicalCores];
                    Marshal.Copy(ptr, bytes, 0, sizeOfStruct * _logicalCores);

                    //parse each processor
                    for (int logical_cpu = 0; logical_cpu < _logicalCores; logical_cpu++) {
                        Marshal.Copy(bytes, sizeOfStruct * logical_cpu, thisProcessor, sizeOfStruct);
                        PROCESSOR_POWER_INFORMATION info = ((PROCESSOR_POWER_INFORMATION)Marshal.PtrToStructure(thisProcessor, typeOfStruct));
                        _acpiStates[logical_cpu] = (short)info.CurrentIdleState;
                        _windowsFrequencies[logical_cpu] = (short)info.CurrentMhz;
                        //Debug.WriteLine(logical_cpu + ": " + acpiStates[logical_cpu]);
                    }
                } else if (status == 3221225485) { //C000000D 
                    error = "An invalid parameter was passed to a service or function.";
                } else {
                    error = "CallNtPowerInformation failed. Status: " + status + ". A list of all status codes can be found in hex format at http://nologs.com/ntstatus.html";
                }
            } catch (Exception ex) {
                error = ex.Message;
            } finally {
                Marshal.FreeCoTaskMem(ptr);
                Marshal.FreeCoTaskMem(thisProcessor);
            }
            return error;
        }

        /// <summary>
        /// to know how much cores a group contains
        /// </summary>
        /// <param name="groupNumber">the group number if any, or ALL_PROCESSOR_GROUPS (0xffff) for every group</param>
        /// <returns></returns>
        [DllImport("kernel32.dll")]
        private static extern uint GetActiveProcessorCount(ushort groupNumber); //Must be at the end of the file, otherwise RGiesecke.DllExport.DllExport marked functions will nog get linked. 

        /// <summary>
        /// Use this function to request power information of the system (informationlevel 11 for processor information)
        /// </summary>
        /// <param name="InformationLevel">specify which information you want to know, has to be a value of the POWER_INFORMATION_LEVEL enum</param>
        /// <param name="lpInputBuffer">pointer to the input buffer</param>
        /// <param name="nInputBufferSize">size of the input buffer in bytes</param>
        /// <param name="lpOutputBuffer">pointer to the output buffer</param>
        /// <param name="nOutputBufferSize">size of the output buffer in bytes</param>
        /// <returns>NT_STATUS_SUCCESS (= 0) on succes, otherwise error code</returns>
        [DllImport("powrprof.dll", SetLastError = true)]
        private static extern UInt32 CallNtPowerInformation(
             Int32 InformationLevel,
             IntPtr lpInputBuffer,
             UInt32 nInputBufferSize,
             IntPtr lpOutputBuffer,
             UInt32 nOutputBufferSize
             );

        [StructLayout(LayoutKind.Sequential)]
        struct PROCESSOR_POWER_INFORMATION {
            public uint Number; //ulong in WIN32 API = uint in 64bit c#
            public uint MaxMhz;
            public uint CurrentMhz;
            public uint MhzLimit;
            public uint MaxIdleState;
            public uint CurrentIdleState;
        }
    }
}
