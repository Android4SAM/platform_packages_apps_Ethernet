
package android.net.server;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ethernet.EthernetDevInfo;
import android.net.ethernet.EthernetManager;
import android.net.ethernet.EthernetNative;
import android.net.ethernet.EthernetStateTracker;
import android.net.ethernet.IEthernetManager;
import android.provider.Settings;
import android.util.Log;

import java.net.UnknownHostException;

public class EthernetService<syncronized> extends IEthernetManager.Stub {

    private static final String TAG = "EthernetService";

    private Context mContext;
    private EthernetStateTracker mStateTracker;
    private String[] DevName;
    private int isEthEnabled;
    private int mEthState = EthernetManager.ETH_STATE_DISABLED;

    public static final String ETH_ON = "eth_on";
    public static final String ETH_MODE = "eth_mode";
    public static final String ETH_IP = "eth_ip";
    public static final String ETH_MASK = "eth_mask";
    public static final String ETH_DNS = "eth_dns";
    public static final String ETH_ROUTE = "eth_route";
    public static final String ETH_CONF = "eth_conf";
    public static final String ETH_IFNAME = "eth_ifname";

    public EthernetService(Context context, EthernetStateTracker mTrack) {

        mContext = context;
        mStateTracker = mTrack;

        isEthEnabled = getPersistedState();

        Log.i(TAG, "Ethernet dev enabled " + isEthEnabled);

    }

    public void Init() {

        if (getDeviceNameList() == null) {
            Log.e(TAG, "No ethernet device detected.We will return!!!");
            return;
        }

        // mStateTracker.SetInterfaceName(DevName[0]);

        if (!isEthConfigured()) {
            // If user did not configure any interfaces yet, pick the first one
            // and enable it.
            setEthMode(EthernetDevInfo.ETH_CONN_MODE_DHCP);
        }

        Log.i(TAG, "Trigger the ethernet monitor");
        mStateTracker.StartPolling();
    }

    public boolean isEthConfigured() {

        final ContentResolver cr = mContext.getContentResolver();
        int x = Settings.Secure.getInt(cr, ETH_CONF, 0);

        if (x == 1)
            return true;
        return false;
    }

    public synchronized EthernetDevInfo getSavedEthConfig() {

        if (isEthConfigured()) {

            final ContentResolver cr = mContext.getContentResolver();
            EthernetDevInfo info = new EthernetDevInfo();
            info.setConnectMode(Settings.Secure.getString(cr, ETH_MODE));
            info.setIfName(Settings.Secure.getString(cr, ETH_IFNAME));
            info.setIpAddress(Settings.Secure.getString(cr, ETH_IP));
            info.setDnsAddr(Settings.Secure.getString(cr, ETH_DNS));
            info.setNetMask(Settings.Secure.getString(cr, ETH_MASK));
            info.setRouteAddr(Settings.Secure.getString(cr, ETH_ROUTE));

            return info;
        }
        return null;
    }

    public synchronized void setEthMode(String mode) {

        final ContentResolver cr = mContext.getContentResolver();

        if (DevName != null) {
            Settings.Secure.putString(cr, ETH_IFNAME, DevName[0]);
            Settings.Secure.putInt(cr, ETH_CONF, 1);
            Settings.Secure.putString(cr, ETH_MODE, mode);
        }
    }

    public synchronized void UpdateEthDevInfo(EthernetDevInfo info) {

        final ContentResolver cr = mContext.getContentResolver();
        Settings.Secure.putInt(cr, ETH_CONF, 1);
        Settings.Secure.putString(cr, ETH_IFNAME, info.getIfName());
        Settings.Secure.putString(cr, ETH_IP, info.getIpAddress());
        Settings.Secure.putString(cr, ETH_MODE, info.getConnectMode());
        Settings.Secure.putString(cr, ETH_DNS, info.getDnsAddr());
        Settings.Secure.putString(cr, ETH_ROUTE, info.getRouteAddr());
        Settings.Secure.putString(cr, ETH_MASK, info.getNetMask());
        if (mEthState == EthernetManager.ETH_STATE_ENABLED) {
            try {
                mStateTracker.resetInterface();
            } catch (UnknownHostException e) {
                Log.e(TAG, "Wrong ethernet configuration");
            }

        }

    }

    public int getTotalInterface() {

        return EthernetNative.getInterfaceCnt();
    }

    private int scanEthDevice() {
        int i = 0, j;
        if ((i = EthernetNative.getInterfaceCnt()) != 0) {
            Log.i(TAG, "total found " + i + " net devices");
            DevName = new String[i];
        }
        else
            return i;

        for (j = 0; j < i; j++) {
            DevName[j] = EthernetNative.getInterfaceName(j);
            if (DevName[j] == null)
                break;
            Log.i(TAG, "device " + j + " name " + DevName[j]);
        }

        return i;
    }

    public String[] getDeviceNameList() {
        if (scanEthDevice() > 0)
            return DevName;
        else
            return null;
    }

    private int getPersistedState() {

        final ContentResolver cr = mContext.getContentResolver();

        int i = Settings.Secure.getInt(cr, ETH_ON, 1);
        mEthState = i;
        mStateTracker.SetInterfaceState(i);
        return i;
    }

    private synchronized void persistEthEnabled(boolean enabled) {

        final ContentResolver cr = mContext.getContentResolver();
        Settings.Secure.putInt(cr, ETH_ON,
                enabled ? EthernetManager.ETH_STATE_ENABLED : EthernetManager.ETH_STATE_DISABLED);
        mStateTracker.SetInterfaceState(enabled ? EthernetManager.ETH_STATE_ENABLED
                : EthernetManager.ETH_STATE_DISABLED);
    }

    public synchronized void setEthState(int state) {
        Log.i(TAG, "setEthState from " + mEthState + " to " + state);

        if (mEthState != state) {
            mEthState = state;
            if (state == EthernetManager.ETH_STATE_DISABLED) {
                persistEthEnabled(false);
                mStateTracker.stopInterface(false);
            } else {
                persistEthEnabled(true);
                if (!isEthConfigured()) {
                    // If user did not configure any interfaces yet, pick the
                    // first one
                    // and enable it.
                    setEthMode(EthernetDevInfo.ETH_CONN_MODE_DHCP);
                }
                try {
                    mStateTracker.resetInterface();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "Wrong ethernet configuration");
                }

            }
        }
    }

    public int getEthState() {
        return mEthState;
    }

}
