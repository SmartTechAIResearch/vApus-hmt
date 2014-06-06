/*
  
  Version: MPL 1.1/GPL 2.0/LGPL 2.1

  The contents of this file are subject to the Mozilla Public License Version
  1.1 (the "License"); you may not use this file except in compliance with
  the License. You may obtain a copy of the License at
 
  http://www.mozilla.org/MPL/

  Software distributed under the License is distributed on an "AS IS" basis,
  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
  for the specific language governing rights and limitations under the License.

  The Original Code is the Open Hardware Monitor code.

  The Initial Developer of the Original Code is 
  Michael Möller <m.moeller@gmx.ch>.
  Portions created by the Initial Developer are Copyright (C) 2010-2011
  the Initial Developer. All Rights Reserved.

  Contributor(s):

  Alternatively, the contents of this file may be used under the terms of
  either the GNU General Public License Version 2 or later (the "GPL"), or
  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
  in which case the provisions of the GPL or the LGPL are applicable instead
  of those above. If you wish to allow use of your version of this file only
  under the terms of either the GPL or the LGPL, and not to allow others to
  use your version of this file under the terms of the MPL, indicate your
  decision by deleting the provisions above and replace them with the notice
  and other provisions required by the GPL or the LGPL. If you do not delete
  the provisions above, a recipient may use your version of this file under
  the terms of any one of the MPL, the GPL or the LGPL.
 
*/

using System;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

namespace HMTProxy {
    internal static class Ring0 {

        private static KernelDriver _driver;
        private static Mutex _isaBusMutex;
        private static readonly StringBuilder _report = new StringBuilder();

        private const uint OLS_TYPE = 40000;
        private static IOControlCode
          IOCTL_OLS_GET_REFCOUNT = new IOControlCode(OLS_TYPE, 0x801,
            IOControlCode.Access.Any),
          IOCTL_OLS_GET_DRIVER_VERSION = new IOControlCode(OLS_TYPE, 0x800,
            IOControlCode.Access.Any),
          IOCTL_OLS_READ_MSR = new IOControlCode(OLS_TYPE, 0x821,
            IOControlCode.Access.Any),
          IOCTL_OLS_WRITE_MSR = new IOControlCode(OLS_TYPE, 0x822,
            IOControlCode.Access.Any),
          IOCTL_OLS_READ_IO_PORT_BYTE = new IOControlCode(OLS_TYPE, 0x833,
            IOControlCode.Access.Read),
          IOCTL_OLS_WRITE_IO_PORT_BYTE = new IOControlCode(OLS_TYPE, 0x836,
            IOControlCode.Access.Write),
          IOCTL_OLS_READ_PCI_CONFIG = new IOControlCode(OLS_TYPE, 0x851,
            IOControlCode.Access.Read),
          IOCTL_OLS_WRITE_PCI_CONFIG = new IOControlCode(OLS_TYPE, 0x852,
            IOControlCode.Access.Write);

        public static void Open() {
            // no implementation for unix systems
            int p = (int)Environment.OSVersion.Platform;
            if ((p == 4) || (p == 128))
                return;

            if (_driver != null)
                return;

            // clear the current report
            _report.Length = 0;

            _driver = new KernelDriver("WinRing0_1_2_0");
            _driver.Open();

            if (!_driver.IsOpen)
                _driver = null;

            _isaBusMutex = new Mutex(false, "Global\\Access_ISABUS.HTP.Method");
        }

        public static bool IsOpen {
            get { return _driver != null; }
        }

        public static void Close() {
            if (_driver == null)
                return;

            uint refCount = 0;
            _driver.DeviceIOControl(IOCTL_OLS_GET_REFCOUNT, null, ref refCount);

            _driver.Close();

            if (refCount <= 1)
                _driver.Delete();

            _driver = null;

            _isaBusMutex.Close();
        }

        public static string GetReport() {
            if (_report.Length > 0) {
                StringBuilder r = new StringBuilder();
                r.AppendLine("Ring0");
                r.AppendLine();
                r.Append(_report);
                r.AppendLine();
                return r.ToString();
            } else
                return null;
        }

        public static bool WaitIsaBusMutex(int millisecondsTimeout) {
            try {
                return _isaBusMutex.WaitOne(millisecondsTimeout, false);
            } catch (AbandonedMutexException) { return false; } catch (InvalidOperationException) { return false; }
        }

        public static void ReleaseIsaBusMutex() {
            _isaBusMutex.ReleaseMutex();
        }

        /// <summary>
        /// Set the thread affinity for the current process if need be.
        /// </summary>
        /// <param name="index"></param>
        /// <param name="eax"></param>
        /// <param name="edx"></param>
        /// <returns></returns>
        public static bool Rdmsr(uint index, out uint eax, out uint edx) {
            if (_driver == null) {
                eax = 0;
                edx = 0;
                return false;
            }

            ulong buffer = 0;
            bool result = _driver.DeviceIOControl(IOCTL_OLS_READ_MSR, index,
              ref buffer);

            edx = (uint)((buffer >> 32) & 0xFFFFFFFF);
            eax = (uint)(buffer & 0xFFFFFFFF);

            if (!result) {
                ulong error = _driver.GetLastError();
                //31 == A device attached to the sytem is not functioning.
            }
            return result;
        }

        [StructLayout(LayoutKind.Sequential, Pack = 1)]
        private struct WrmsrInput {
            public uint Register;
            public ulong Value;
        }

        /// <summary>
        /// Set the thread affinity for the current process if need be.
        /// </summary>
        /// <param name="index"></param>
        /// <param name="eax"></param>
        /// <param name="edx"></param>
        /// <returns></returns>
        public static bool Wrmsr(uint index, uint eax, uint edx) {
            if (_driver == null)
                return false;

            WrmsrInput input = new WrmsrInput();
            input.Register = index;
            input.Value = ((ulong)edx << 32) | eax;

            return _driver.DeviceIOControl(IOCTL_OLS_WRITE_MSR, input);
        }

        public static byte ReadIoPort(uint port) {
            if (_driver == null)
                return 0;

            uint value = 0;
            _driver.DeviceIOControl(IOCTL_OLS_READ_IO_PORT_BYTE, port, ref value);

            return (byte)(value & 0xFF);
        }

        [StructLayout(LayoutKind.Sequential, Pack = 1)]
        private struct WriteIoPortInput {
            public uint PortNumber;
            public byte Value;
        }

        public static void WriteIoPort(uint port, byte value) {
            if (_driver == null)
                return;

            WriteIoPortInput input = new WriteIoPortInput();
            input.PortNumber = port;
            input.Value = value;

            _driver.DeviceIOControl(IOCTL_OLS_WRITE_IO_PORT_BYTE, input);
        }

        public const uint InvalidPciAddress = 0xFFFFFFFF;

        public static uint GetPciAddress(byte bus, byte device, byte function) {
            return
              (uint)(((bus & 0xFF) << 8) | ((device & 0x1F) << 3) | (function & 7));
        }

        [StructLayout(LayoutKind.Sequential, Pack = 1)]
        private struct ReadPciConfigInput {
            public uint PciAddress;
            public uint RegAddress;
        }

        public static bool ReadPciConfig(uint pciAddress, uint regAddress, out uint value) {
            if (_driver == null || (regAddress & 3) != 0) {
                value = 0;
                return false;
            }

            ReadPciConfigInput input = new ReadPciConfigInput();
            input.PciAddress = pciAddress;
            input.RegAddress = regAddress;

            value = 0;
            return _driver.DeviceIOControl(IOCTL_OLS_READ_PCI_CONFIG, input,
              ref value);
        }

        [StructLayout(LayoutKind.Sequential, Pack = 1)]
        private struct WritePciConfigInput {
            public uint PciAddress;
            public uint RegAddress;
            public uint Value;
        }

        public static bool WritePciConfig(uint pciAddress, uint regAddress, uint value) {
            if (_driver == null || (regAddress & 3) != 0)
                return false;

            WritePciConfigInput input = new WritePciConfigInput();
            input.PciAddress = pciAddress;
            input.RegAddress = regAddress;
            input.Value = value;

            return _driver.DeviceIOControl(IOCTL_OLS_WRITE_PCI_CONFIG, input);
        }

        public static ulong GetLastError() {
            return _driver.GetLastError();
        }
    }
}
