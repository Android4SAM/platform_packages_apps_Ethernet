
package android.net.ethernet;

import android.net.NetworkInfo;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.net.EthernetDataTracker;

public class EthernetMonitor {
    private static final String TAG = "EthernetMonitor";
    private static final int ETHER_CONNECTED_FAILED = 5;
    private static final int ETHER_CABLE_NOT_PLUG_IN = 4;
    private static final int ETHER_CONNECTED_SUCCESS = 3;
    private final Context mContext;
    private static EthernetStateTracker mTracker;
    private EthernetDataTracker mDataTracker;
    private NetStateHandler sInstance;
    private static HandlerThread NetStateThread ;

    public EthernetMonitor(Context context, EthernetStateTracker tracker, EthernetDataTracker DataTracker) {
        mContext = context;
        mDataTracker = DataTracker;
        mTracker = tracker;
    }

    public  synchronized NetStateHandler getHandlerTarget() {
        if(sInstance == null) sInstance = new NetStateHandler(NetStateThread.getLooper(), mTracker);
        return sInstance;
    }



    private class NetStateHandler extends Handler {
        public NetStateHandler(Looper looper, EthernetStateTracker target) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            mTracker.handleMessage(msg);
        }

    };


    public void startMonitoring() {
        NetStateThread = new HandlerThread("NetState Handler Thread");
        NetStateThread.start();
        getHandlerTarget();
        mDataTracker.setHandler(sInstance);
    }
}

