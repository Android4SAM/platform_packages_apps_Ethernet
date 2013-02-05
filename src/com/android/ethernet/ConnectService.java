
package com.android.ethernet;

import android.net.ethernet.EthernetManager;
import android.net.ethernet.IEthernetManager;
import android.os.IBinder;
import android.os.ServiceManager;

public class ConnectService {
    public static final String ETH_SERVICE = "ETH_SERVICE";

    private static EthernetManager sEthManager;

    public static Object getEthernetService() {
        return getEthernetManager();
    }

    private static EthernetManager getEthernetManager() {
        if (sEthManager == null) {
            IBinder b = ServiceManager.getService(ETH_SERVICE);
            IEthernetManager service = IEthernetManager.Stub.asInterface(b);
            sEthManager = new EthernetManager(service);
        }
        return sEthManager;
    }

}
