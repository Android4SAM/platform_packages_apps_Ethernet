
package android.net.ethernet;

//import com.android.ethernettest.EthernetDevInfo;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.EnumMap;

public class EthernetDevInfo implements Parcelable {
    private String dev_name;
    private String ipaddr;
    private String netmask;
    private String route;
    private String dns;
    private String mode;
    public static final String ETH_CONN_MODE_DHCP = "dhcp";
    public static final String ETH_CONN_MODE_MANUAL = "manual";
    protected NetworkInfo mNetworkInfo;
    private static final String TAG = "EthernetDevInfo";
    Parcel mOut = Parcel.obtain();

    public EthernetDevInfo() {
        dev_name = null;
        ipaddr = null;
        dns = null;
        route = null;
        netmask = null;
        mode = ETH_CONN_MODE_DHCP;
    }

    public void setIfName(String ifname) {
        this.dev_name = ifname;
    }

    public String getIfName() {
        return this.dev_name;
    }

    public void setIpAddress(String ip) {
        this.ipaddr = ip;
    }

    public String getIpAddress() {
        return this.ipaddr;
    }

    public void setNetMask(String ip) {
        this.netmask = ip;
    }

    public String getNetMask() {
        return this.netmask;
    }

    public void setRouteAddr(String route) {
        this.route = route;
    }

    public String getRouteAddr() {
        return this.route;
    }

    public void setDnsAddr(String dns) {
        this.dns = dns;
    }

    public String getDnsAddr() {

	String default_dns = "8.8.8.8";

	if (this.dns == null)
		return default_dns;
	else
		return this.dns;
    }

    public boolean setConnectMode(String mode) {
        if (mode.equals(ETH_CONN_MODE_DHCP) || mode.equals(ETH_CONN_MODE_MANUAL)) {
            this.mode = mode;
            return true;
        }
        return false;
    }

    public String getConnectMode() {
        return this.mode;
    }

    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.dev_name);
        dest.writeString(this.ipaddr);
        dest.writeString(this.netmask);
        dest.writeString(this.route);
        dest.writeString(this.dns);
        dest.writeString(this.mode);
    }

    /** Implement the Parcelable interface {@hide} */
    public static final Creator<EthernetDevInfo> CREATOR =
            new Creator<EthernetDevInfo>() {
                public EthernetDevInfo createFromParcel(Parcel in) {
                    EthernetDevInfo info = new EthernetDevInfo();
                    info.setIfName(in.readString());
                    info.setIpAddress(in.readString());
                    info.setNetMask(in.readString());
                    info.setRouteAddr(in.readString());
                    info.setDnsAddr(in.readString());
                    info.setConnectMode(in.readString());
                    return info;
                }

                public EthernetDevInfo[] newArray(int size) {
                    return new EthernetDevInfo[size];
                }
            };

    /* Following source code is use to visit networkinfo.java */

    /**
     * This is the map described in the Javadoc comment above. The positions of
     * the elements of the array must correspond to the ordinal values of
     * <code>DetailedState</code>.
     */
    private static final EnumMap<DetailedState, State> stateMap =
            new EnumMap<DetailedState, State>(DetailedState.class);

    static {
        stateMap.put(DetailedState.IDLE, State.DISCONNECTED);
        stateMap.put(DetailedState.SCANNING, State.DISCONNECTED);
        stateMap.put(DetailedState.CONNECTING, State.CONNECTING);
        stateMap.put(DetailedState.AUTHENTICATING, State.CONNECTING);
        stateMap.put(DetailedState.OBTAINING_IPADDR, State.CONNECTING);
        stateMap.put(DetailedState.CONNECTED, State.CONNECTED);
        stateMap.put(DetailedState.SUSPENDED, State.SUSPENDED);
        stateMap.put(DetailedState.DISCONNECTING, State.DISCONNECTING);
        stateMap.put(DetailedState.DISCONNECTED, State.DISCONNECTED);
        stateMap.put(DetailedState.FAILED, State.DISCONNECTED);
    }

    private String DetailedStateToString(DetailedState Dstate) {
        switch (Dstate) {
            case IDLE:
                return "IDLE";
            case SCANNING:
                return "SCANNING";
            case CONNECTING:
                return "CONNECTING";
            case AUTHENTICATING:
                return "AUTHENTICATING";
            case OBTAINING_IPADDR:
                return "OBTAINING_IPADDR";
            case CONNECTED:
                return "CONNECTED";
            case SUSPENDED:
                return "SUSPENDED";
            case DISCONNECTING:
                return "DISCONNECTING";
            case DISCONNECTED:
                return "DISCONNECTED";
            case FAILED:
                return "FAILED";
            default:
                return "NULL";
        }
    }

    private int DetailedStateToInt(DetailedState Dstate) {
        switch (Dstate) {
            case CONNECTED:
                return 1;
            default:
                return 0;
        }
    }

    private String StateToString(State Dstate) {
        switch (Dstate) {
            case CONNECTING:
                return "CONNECTING";
            case CONNECTED:
                return "CONNECTED";
            case SUSPENDED:
                return "SUSPENDED";
            case DISCONNECTING:
                return "DISCONNECTING";
            case DISCONNECTED:
                return "DISCONNECTED";
            case UNKNOWN:
                return "UNKNOWN";
            default:
                return "NULL";
        }
    }

    public void Eth_NetworkInfo(int networkType,
            int subType,
            String typeName,
            String subtypeName) {
        mOut.writeInt(networkType);
        mOut.writeInt(subType);
        mOut.writeString(typeName);
        mOut.writeString(subtypeName);
        Log.d(TAG, "State =" + StateToString(State.UNKNOWN));
        mOut.writeString(StateToString(State.UNKNOWN));
        Log.d(TAG, "State =" + DetailedStateToString(DetailedState.IDLE));
        mOut.writeString(DetailedStateToString(DetailedState.IDLE));
        mOut.writeInt(0);
        mOut.writeInt(0);
        mOut.writeInt(0);
        mOut.writeString("Reason:We create a networkinfo, wait for network to connect");
        mOut.writeString("ExtraInfo:NULL");
        mOut.setDataPosition(0);
        mNetworkInfo = NetworkInfo.CREATOR.createFromParcel(mOut);
    }

    /**
     * Record the detailed state of a network, and if it is a change from the
     * previous state, send a notification to any listeners.
     * 
     * @param state the new @{code DetailedState}
     * @param reason a {@code String} indicating a reason for the state change,
     *            if one was supplied. May be {@code null}.
     * @param extraInfo optional {@code String} providing extra information
     *            about the state change
     */
    public NetworkInfo setDetailedState(NetworkInfo.DetailedState state, String reason,
            String extraInfo) {
        Log.d(TAG, "setDetailed state, old =" + mNetworkInfo.getDetailedState() + " and new state="
                + state);
        if (state != mNetworkInfo.getDetailedState()) {
            String lastReason = mNetworkInfo.getReason();
            String lastExtraInfo = mNetworkInfo.getExtraInfo();

            reason = (reason == null) ? lastReason : reason;
            extraInfo = (extraInfo == null) ? lastExtraInfo : extraInfo;
            setDetailedState_l(state, reason, extraInfo);
        }
        return mNetworkInfo;
    }

    private void setDetailedState_l(NetworkInfo.DetailedState state, String reason, String extraInfo) {
        mOut.setDataPosition(0);
        mOut.writeInt(mNetworkInfo.getType());
        mOut.writeInt(mNetworkInfo.getSubtype());
        mOut.writeString(mNetworkInfo.getTypeName());
        mOut.writeString(mNetworkInfo.getSubtypeName());
        mOut.writeString(StateToString(stateMap.get(state)));
        mOut.writeString(DetailedStateToString(state));
        mOut.writeInt(0);
        mOut.writeInt(DetailedStateToInt(state));
        mOut.writeInt(0);
        mOut.writeString(reason);
        mOut.writeString(extraInfo);
        mOut.setDataPosition(0);
        mNetworkInfo = NetworkInfo.CREATOR.createFromParcel(mOut);
    }

}
