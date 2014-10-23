using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace HMTProxyTester {
    static class Program {
        /// <summary>
        /// The main entry point for the application.
        /// </summary>
        [STAThread]
        static void Main() {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            HMTProxy.HMTProxy.init(Application.StartupPath);

            int logicalCores = HMTProxy.HMTProxy.getLogicalCores();
            int physicalCores = HMTProxy.HMTProxy.getPhysicalCores();
            int packages = HMTProxy.HMTProxy.getPackages();

            string basefreq = HMTProxy.HMTProxy.readMSR(206);

            Console.Read();
        }
    }
}
