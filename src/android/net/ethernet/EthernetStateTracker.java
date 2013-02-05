
package android.net.ethernet;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.DhcpInfoInternal;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import com.android.ethernet.R;

import java.net.UnknownHostException;

public class EthernetStateTracker extends Handler {

    private static final String TAG = "EthernetStateTracker";

    private static final int EVENT_DHCP_START = 0;
    private static final int EVENT_INTERFACE_CONFIGURATION_SUCCEEDED = 1;
    private static final int EVENT_INTERFACE_CONFIGURATION_FAILED = 2;
    private static final int EVENT_HW_CONNECTED = 3;
    private static final int EVENT_HW_DISCONNECTED = 4;
    private static final int EVENT_HW_PHYCONNECTED = 5;
    private static final int EVENT_MANUL_STOP = 6;
    private static final int NOTIFY_ID = 7;

    private EthernetManager mEM;

    private boolean mServiceStarted;

    private boolean mStackConnected;
    private boolean mHWConnected;
    private boolean mInterfaceStopped;
    private boolean mGetingIp;
    private DhcpHandler mDhcpTarget;
    private String mInterfaceName;
    private DhcpInfoInternal mDhcpInfoInternal;
    private DhcpInfo mDhcpInfo;
    private EthernetMonitor mMonitor;
    private String[] sDnsPropNames;
    private boolean mStartingDhcp;
    private NotificationManager mNotificationManager;
    private Notification mNotification;
    private EthernetDevInfo mEthernetDevinfo;
    protected NetworkInfo mNetworkInfo;

    private Context mContext;

    public EthernetStateTracker(Context context, NotificationManager notify) {

        Log.i(TAG, "Starts...");

        mServiceStarted = true;

        mContext = context;

        if (EthernetNative.initEthernetNative() != 0)
            Log.e(TAG, "Can not init ethernet device layers");

        HandlerThread dhcpThread = new HandlerThread("DHCP Handler Thread");

        mMonitor = new EthernetMonitor(this);

        dhcpThread.start();

        mDhcpTarget = new DhcpHandler(dhcpThread.getLooper(), this);

        mDhcpInfoInternal = new DhcpInfoInternal();

        mDhcpInfo = new DhcpInfo();

        mEthernetDevinfo = new EthernetDevInfo();
        mEthernetDevinfo.Eth_NetworkInfo(9, 0, "ETHERNET", "");

        mNotificationManager = notify;

    }

    public void Init() {
        getEthernetManager();
        Log.i(TAG, "Successed");
    }

    public void SetInterfaceState(int i) {
        if (i == EthernetManager.ETH_STATE_ENABLED)
            mInterfaceStopped = false;
        else if (i == EthernetManager.ETH_STATE_DISABLED)
            mInterfaceStopped = true;
        postNotification();
    }

    public void SetInterfaceName(String i) {
        mInterfaceName = i;
    }

    private void setHWState(boolean i) {
        mHWConnected = i;
        postNotification();
    }

    private void setStackState(boolean i) {
        mStackConnected = i;
        postNotification();
    }

    private void setGetingIPState(boolean i) {
        mGetingIp = i;
        postNotification();
    }

    public boolean stopInterface(boolean suspend) {
        if (mEM != null) {
            EthernetDevInfo info = mEM.getSavedEthConfig();
            if (info != null && mEM.ethConfigured())
            {
                synchronized (mDhcpTarget) {

                    Log.i(TAG, "stop dhcp and interface");

                    mDhcpTarget.removeMessages(EVENT_DHCP_START);

                    String ifname = info.getIfName();

                    if (!NetworkUtils.stopDhcp(ifname)) {
                        Log.e(TAG, "Could not stop DHCP");
                    }

                    NetworkUtils.resetConnections(ifname, NetworkUtils.RESET_IPV4_ADDRESSES);
                    EthernetNative.removeDefaultRoute(ifname);
                    NetworkUtils.stopDhcp(ifname);

                    mStartingDhcp = false;
                }
            }
        }

        return true;
    }

    private void getEthernetManager() {

        if (mEM == null) {

            IBinder b = ServiceManager.getService("ETH_SERVICE");
            IEthernetManager service = IEthernetManager.Stub.asInterface(b);
            mEM = new EthernetManager(service);
        }
    }

    private static int stringToIpAddr(String addrString) throws UnknownHostException {
        try {
            if (addrString == null)
                return 0;
            String[] parts = addrString.split("\\.");
            if (parts.length != 4) {
                throw new UnknownHostException(addrString);
            }

            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]) << 8;
            int c = Integer.parseInt(parts[2]) << 16;
            int d = Integer.parseInt(parts[3]) << 24;

            return a | b | c | d;

        } catch (NumberFormatException ex) {

            throw new UnknownHostException(addrString);
        }
    }

    private static String[] getNameServerList(String[] propertyNames) {
        String[] dnsAddresses = new String[propertyNames.length];
        int i, j;

        for (i = 0, j = 0; i < propertyNames.length; i++) {
            String value = SystemProperties.get(propertyNames[i]);
            // The GSM layer sometimes sets a bogus DNS server address of
            // 0.0.0.0
            if (!TextUtils.isEmpty(value) && !TextUtils.equals(value, "0.0.0.0")) {
                dnsAddresses[j++] = value;
            }
        }
        return dnsAddresses;
    }

    private void handleDnsConfigurationChange() {
        // add default net's dns entries
        String[] dnsList = getNameServerList(sDnsPropNames);
        int j = 1;
        for (String dns : dnsList) {
            if (dns != null && !TextUtils.equals(dns, "0.0.0.0")) {
                SystemProperties.set("net.dns" + j++, dns);
            }
        }
    }

    private boolean configureInterface(EthernetDevInfo info) throws UnknownHostException {

        setGetingIPState(true);
        sDnsPropNames = new String[] {
                "dhcp." + mInterfaceName + ".dns1",
                "dhcp." + mInterfaceName + ".dns2"
        };

        if (info.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_DHCP)) {
            if (mStartingDhcp)
                return true;
            mStartingDhcp = true;
            Log.i(TAG, "trigger dhcp for device " + info.getIfName());
            mDhcpTarget.sendEmptyMessage(EVENT_DHCP_START);
        } else {
            int event;

            mStartingDhcp = false;

            mDhcpInfo.ipAddress = stringToIpAddr(info.getIpAddress());
            mDhcpInfo.gateway = stringToIpAddr(info.getRouteAddr());
            mDhcpInfo.netmask = NetworkUtils.netmaskIntToPrefixLength(stringToIpAddr(info
                    .getNetMask()));
            mDhcpInfo.dns1 = stringToIpAddr(info.getDnsAddr());
            mDhcpInfo.dns2 = 0;

            Log.i(TAG, "set ip manually " + mDhcpInfoInternal.toString());

            EthernetNative.removeDefaultRoute(info.getIfName());
            NetworkUtils.stopDhcp(info.getIfName());

            if (EthernetNative.configureInterface(info.getIfName(), mDhcpInfo)) {
                // if (NetworkUtils.runDhcp(info.getIfName(),
                // mDhcpInfoInternal)) {
                event = EVENT_INTERFACE_CONFIGURATION_SUCCEEDED;
                SystemProperties.set("net.dns1", info.getDnsAddr());
                SystemProperties.set("net." + info.getIfName() + ".dns1", info.getDnsAddr());
                Log.v(TAG, "Static IP configuration succeeded");
            } else {
                event = EVENT_INTERFACE_CONFIGURATION_FAILED;
                Log.v(TAG, "Static IP configuration failed");
            }

            this.sendEmptyMessage(event);
        }
        return true;
    }

    public boolean resetInterface() throws UnknownHostException {
        /*
         * This will guide us to enabled the enabled device
         */
        if (mEM == null) {
            Log.e(TAG, "mEM is null,we will return.This should not happen!!!");
            return false;
        }

        if (!mHWConnected) {
            Log.d(TAG, "The net wire is disconnect,we will not resetInterface");
            // Did not to throw a exception
            return true;
        }

        if (mInterfaceStopped) {
            Log.d(TAG, "The network is stoped by user,we will not resetInterface");
            // Did not to throw a exception
            return true;
        }

        EthernetDevInfo info = mEM.getSavedEthConfig();
        if (info != null && mEM.ethConfigured()) {
            synchronized (this) {
                mInterfaceName = info.getIfName();
                Log.i(TAG, "reset device " + mInterfaceName);
                NetworkUtils.resetConnections(mInterfaceName, NetworkUtils.RESET_IPV4_ADDRESSES);
                // Stop DHCP
                if (mDhcpTarget != null) {
                    mDhcpTarget.removeMessages(EVENT_DHCP_START);
                }

                if (!NetworkUtils.stopDhcp(mInterfaceName)) {
                    Log.e(TAG, "Could not stop DHCP");
                }

                configureInterface(info);
            }
        }
        return true;
    }

    public void StartPolling() {
        Log.i(TAG, "start polling");
        String[] Devs = mEM.getDeviceNameList();
        mMonitor.startMonitoring();

        for (int i = 0; i < Devs.length; i++) {
            EthernetNative.enableterface(Devs[i]);
        }
    }

    private void postNotification() {
        synchronized (this) {
            if (mNotificationManager != null) {
                int icon;
                CharSequence title = "Ethernet Status";
                CharSequence detail;
                if (mNotification == null) {
                    mNotification = new Notification();
                    mNotification.contentIntent = PendingIntent.getActivity(mContext, 0,
                            new Intent(EthernetManager.NETWORK_STATE_CHANGED_ACTION), 0);
                }

                mNotification.when = System.currentTimeMillis();
                icon = R.drawable.connect_established;

                if (!mHWConnected) {
                    mNotification.icon = R.drawable.connect_no;
                    detail = "Connect is unestablished,Net work cable is not plugined.";
                } else if (mInterfaceStopped) {
                    mNotification.icon = R.drawable.connect_established;
                    detail = "Connect is established,But network disabled by user.";
                } else if (mGetingIp) {
                    mNotification.icon = R.drawable.connect_established;
                    detail = "Connect is established,Waiting for IP address.";
                } else if (!mStackConnected) {
                    mNotification.icon = R.drawable.connect_established;
                    detail = "Connect is established,But get IP address failed.";
                } else if (mStackConnected) {
                    EthernetDevInfo info = mEM.getSavedEthConfig();
                    mNotification.icon = R.drawable.connect_creating;
                    String ipaddr;
                    if (info.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_DHCP)) {
                        String ipprop = "dhcp." + mInterfaceName + ".ipaddress";
                        ipaddr = SystemProperties.get(ipprop);
                    } else {
                        ipaddr = info.getIpAddress();
                    }
                    detail = "Ethernet is connected. IP address: " + ipaddr;
                } else {
                    mNotification.icon = R.drawable.connect_no;
                    detail = "Unknow State.";
                }

                Log.i(TAG, "post event to notification manager " + detail);
                mNotification.setLatestEventInfo(mContext, title, detail,
                        mNotification.contentIntent);
                mNotificationManager.notify(icon, mNotification);
            } else {
                Log.i(TAG, "notification manager is not up yet");
            }
        }

    }

    public void handleMessage(Message msg) {

        synchronized (this) {
            switch (msg.what) {
                case EVENT_INTERFACE_CONFIGURATION_SUCCEEDED:
                    setGetingIPState(false);
                    setStackState(true);
                    Log.i(TAG, "received configured events, stack: " + mStackConnected + " HW "
                            + mHWConnected);
                    mNetworkInfo = mEthernetDevinfo.setDetailedState(DetailedState.CONNECTED,
                            "We have get ip addr", null);
                    sendConnectedBroadcast(mNetworkInfo);
                    break;

                case EVENT_INTERFACE_CONFIGURATION_FAILED:
                    setGetingIPState(false);
                    setStackState(false);
                    mNetworkInfo = mEthernetDevinfo.setDetailedState(DetailedState.DISCONNECTED,
                            "We get ip failed", null);
                    sendConnectedBroadcast(mNetworkInfo);
                    break;

                case EVENT_HW_CONNECTED:
                    setHWState(true);
                    Log.i(TAG, "received connected events, stack: " + mStackConnected + " HW "
                            + mHWConnected);
                    mHWConnected = true;
                    mNetworkInfo = mEthernetDevinfo.setDetailedState(DetailedState.CONNECTED,
                            "Wire Cable has be    en pluged in", null);
                    sendConnectedBroadcast(mNetworkInfo);

                    break;

                case EVENT_HW_DISCONNECTED:
                    stopInterface(true);
                    setHWState(false);
                    Log.i(TAG, "received disconnected events, stack: " + mStackConnected + " HW "
                            + mHWConnected);
                    mNetworkInfo = mEthernetDevinfo.setDetailedState(DetailedState.DISCONNECTED,
                            "Wire Cable has been pluged out", null);
                    sendConnectedBroadcast(mNetworkInfo);
                    break;

                case EVENT_HW_PHYCONNECTED:
                    Log.i(TAG, "interface up event, kick off connection request");
                    setHWState(true);
                    try {
                        resetInterface();
                    } catch (UnknownHostException e) {
                        Log.e(TAG, "Wrong ethernet configuration");
                    }

                    break;
            }
        }
    }

    private class DhcpHandler extends Handler {
        private Handler mTrackerTarget;

        public DhcpHandler(Looper looper, Handler target) {
            super(looper);
            mTrackerTarget = target;
        }

        public void handleMessage(Message msg) {
            int event;

            switch (msg.what) {
                case EVENT_DHCP_START:
                    synchronized (mDhcpTarget) {
                        if (!mInterfaceStopped) {
                            Log.d(TAG, "DhcpHandler: DHCP request started");
                            if (NetworkUtils.runDhcp(mInterfaceName, mDhcpInfoInternal)) {
                                event = EVENT_INTERFACE_CONFIGURATION_SUCCEEDED;
                                handleDnsConfigurationChange();
                                Log.v(TAG, "DhcpHandler: DHCP request succeeded");
                            } else {
                                event = EVENT_INTERFACE_CONFIGURATION_FAILED;
                                Log.i(TAG, "DhcpHandler: DHCP request failed: " +
                                        NetworkUtils.getDhcpError());
                            }
                            if (mStartingDhcp)
                                mTrackerTarget.sendEmptyMessage(event);
                        }
                        mStartingDhcp = false;
                    }
                    break;
            }

        }
    }

    public void notifyPhyConnected(String ifname) {
        Log.i(TAG, "report interface is up for " + ifname);
        EthernetDevInfo info = mEM.getSavedEthConfig();
        info.setIfName(ifname);
        mEM.UpdateEthDevInfo(info);
        synchronized (this) {
            this.sendEmptyMessage(EVENT_HW_PHYCONNECTED);
        }

    }

    public void notifyStateChange(String ifname, DetailedState state) {
        Log.i(TAG, "report new state " + state.toString() + " on dev " + ifname);

        if (ifname.equals(mInterfaceName)) {

            Log.i(TAG, "update network state tracker");
            synchronized (this) {
                if (state.equals(DetailedState.CONNECTED)) {
                    this.sendEmptyMessage(EVENT_HW_CONNECTED);
                } else {
                    this.sendEmptyMessage(EVENT_HW_DISCONNECTED);
                }

            }
        }
    }

    private void sendStickyBroadcast(Intent intent) {
        synchronized (this) {
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendStickyBroadcast(intent);
        }
    }

    private void sendGeneralBroadcast(NetworkInfo info, String bcastType) {
        Intent intent = new Intent(bcastType);
        intent.putExtra(ConnectivityManager.EXTRA_NETWORK_INFO, info);
        sendStickyBroadcast(intent);
    }

    private void sendConnectedBroadcast(NetworkInfo info) {
        /*
         * For SDIO WIFI If we disable the wired net by user, we didn't report
         * any connect/disconnect state to browser
         */
        if (mInterfaceStopped)
            return;
        sendGeneralBroadcast(info, ConnectivityManager.CONNECTIVITY_ACTION);
    }
}
