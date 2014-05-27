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

            string error = HMTProxy.HMTProxy.readMSR64Tx(16, 0);

            Console.Read();
        }
    }
}
