
package android.net.server;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ethernet.EthernetDevInfo;

import android.net.ethernet.EthernetStateTracker;
import android.provider.Settings;
import android.util.Log;
import android.net.EthernetDataTracker;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import java.net.UnknownHostException;
import android.os.Process;
import android.os.Handler;

import android.os.INetworkManagementService;

import android.os.RemoteException;

public class EthernetService {

    private static final String TAG = "EthernetService";

    private Context mContext;
    private EthernetStateTracker mStateTracker;
    private INetworkManagementService mNMService;
    private int isEthEnabled;
    private int mEthState = ETH_STATE_DISABLED;
    private EthernetDataTracker mDataTracker;
    private static EthernetService sInstance;


    public static final String ETH_STATE_CHANGED_ACTION =
        "android.net.ethernet.ETH_STATE_CHANGED";

    public static final String NETWORK_STATE_CHANGED_ACTION =
        "android.net.ethernet.STATE_CHANGE";

    public static final String EXTRA_NETWORK_INFO = "networkInfo";
    public static final String EXTRA_ETH_STATE = "eth_state";
    public static final String EXTRA_PREVIOUS_ETH_STATE = "previous_eth_state";

    public static final int ETH_STATE_DISABLED = 0;
    public static final int ETH_STATE_ENABLED = 1;
    public static final int ETH_STATE_UNKNOWN = 2;

    public static final String ETH_ON = "eth_on";

    public EthernetService(Context context, EthernetStateTracker mTrack, EthernetDataTracker  eTrack) {
        mContext = context;
        mStateTracker = mTrack;
        mDataTracker = eTrack;
        isEthEnabled = getPersistedState();
		mStateTracker.setUserFlag(isEthEnabled == 0 ? false : true);
        Log.i(TAG, "Ethernet dev enabled " + isEthEnabled);
    }

    public void Init() {
        Log.e("getpid", "Ethernet service pid: " + Process.myPid());
        mStateTracker.StartPolling();
		mDataTracker.clearConnections();
		mDataTracker.scanInterface();
		Log.d("ethapp", "clear connections and scan interface");
    }

	public static void setInstance(EthernetService service) {
		sInstance = service;
	}
    public static EthernetService getInstance() {
        return sInstance;
    }

	public void setStateHandler(Handler handler) {
		mStateTracker.setHandler(handler);
	}
	
    public EthernetStateTracker getStateTracker() {
        return mStateTracker;
    }

    private int getPersistedState() {
        final ContentResolver cr = mContext.getContentResolver();
        int i = Settings.Secure.getInt(cr, ETH_ON, 1);
        mEthState = i;
        return i;
    }

    private synchronized void persistEthEnabled(boolean enabled) {
        final ContentResolver cr = mContext.getContentResolver();
        Settings.Secure.putInt(cr, ETH_ON,
                               enabled ? ETH_STATE_ENABLED : ETH_STATE_DISABLED);
    }

    public synchronized void setEthState(int state) {
        Log.i(TAG, "setEthState from " + mEthState + " to " + state);
		mStateTracker.setUserFlag(state == 0 ? false : true);
        if(mEthState != state) {
            mEthState = state;
            if(state == 0) {
                persistEthEnabled(false);
                mStateTracker.stopInterface(false);
            } else {
                persistEthEnabled(true);
				mDataTracker.reconnect();
            }
        }
    }

    public int getEthState() {
        return mEthState;
    }
}
