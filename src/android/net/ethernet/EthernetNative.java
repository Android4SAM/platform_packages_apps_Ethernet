
package android.net.ethernet;

import android.net.DhcpInfo;

public class EthernetNative {

    public static int getInterfaceCnt() {
        return Native.eth_getInterfaceCnt();
    }

    public static String getInterfaceName(int i) {
        return Native.eth_getInterfaceName(i);
    }

    public static int initEthernetNative() {
        return Native.eth_initEthernetNative();
    }

    public static String waitForEvent() {
        return Native.eth_waitForEvent();
    }

    public static int enableterface(String i) {
        return Native.eth_enableInterface(i);
    }

    public static boolean configureInterface(String interfaceName, DhcpInfo ipInfo) {
        return Native.eth_configureNative(interfaceName,
                ipInfo.ipAddress,
                ipInfo.netmask,
                ipInfo.gateway,
                ipInfo.dns1,
                ipInfo.dns2);
    }

    public static int removeDefaultRoute(String interfaceName) {
        return Native.removeDefaultRoute(interfaceName);
    }
}

class Native {
    static {
        // The runtime will add "lib" on the front and ".o" on the end of
        // the name supplied to loadLibrary.
        System.loadLibrary("ethernet_jni");
    }

    static native String eth_getInterfaceName(int i);

    static native int eth_initEthernetNative();

    static native int eth_getInterfaceCnt();

    static native String eth_waitForEvent();

    static native int eth_enableInterface(String i);

    static native boolean eth_configureNative(
            String interfaceName, int ipAddress, int netmask, int gateway, int dns1, int dns2);

    static native int removeDefaultRoute(String interfaceName);
}
