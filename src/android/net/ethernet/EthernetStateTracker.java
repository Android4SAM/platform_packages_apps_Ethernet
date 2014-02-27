
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
import android.os.UserHandle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.net.EthernetDataTracker;
import android.net.EthernetDataTracker.scanResult;
import android.net.EthernetDataTracker.connectResult;
import android.net.server.EthernetService;
import com.android.ethernet.R;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import java.net.UnknownHostException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import java.util.*;

public class EthernetStateTracker extends Handler {

    private static final String TAG = "EthernetStateTracker";

    public static final int ETHER_IFACE_STATE_DOWN = 0;
    public static final int ETHER_IFACE_STATE_UP = 1;
    public static final int ETHER_CONNECTED_FAILED = 5;
    public static final int ETHER_CONNECTED_SUCCESS = 3;
    public static final int ETHER_CABLE_LINK_UP = 4;
    public static final int ETHER_CABLE_LINK_DOWN = 6;

    public static final String ETH_MODE = "eth_mode";
    public static final String ETH_IP = "eth_ip";
    public static final String ETH_MASK = "eth_mask";
    public static final String ETH_DNS = "eth_dns";
    public static final String ETH_ROUTE = "eth_route";
    public static final String ETH_CONF = "eth_conf";
    public static final String ETH_IFNAME = "eth_ifname";

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

    private boolean mStackConnected;
    private boolean mHWConnected;
    private boolean mInterfaceStopped;
    private boolean mGetingIp;
    private String mInterfaceName;
    private DhcpInfoInternal mDhcpInfoInternal;
    private DhcpInfo mDhcpInfo;
    private EthernetMonitor mMonitor;
    private String[] sDnsPropNames;
    private boolean mStartingDhcp;
    private NotificationManager mNotificationManager;
    private Notification mNotification;
    protected NetworkInfo mNetworkInfo;
    private EthernetDataTracker mDataTracker;
	private static List<EthernetStateMachine> mStateMachines;
    private String ip;
    private Handler mHandler;

    private Context mContext;

    public class EthernetStateMachine extends EthernetDevInfo {

		private boolean mLinkUp;		
		private boolean mIfaceUp;		
		private boolean mConnected;	
		
        public EthernetStateMachine(Context context, String dev) {
			super.setIfName(dev);
			mLinkUp = false;
			mIfaceUp = false;
			mConnected = false;

			if (isSaved())
				open();
			else {
				setConnectMode(EthernetDevInfo.ETH_CONN_MODE_DHCP);		
			}
        }
		
		public void setLinkUp(boolean up) {
			if (up)
				mLinkUp = up;
			else {
				mLinkUp = up;
				mConnected = false;
			}
		}

		public void setInterfaceUp(boolean up) {
			mIfaceUp = up;
		}

		public void setConnected(boolean connected) {
			mConnected = connected;
		}

		public boolean getLinkUp() {
			return mLinkUp;
		}

		public boolean getInterfaceUp() {
			return mIfaceUp;
		}

		public boolean getConnected() {
			return mConnected;
		}
		
        public void save() {
            SharedPreferences sp = mContext.getSharedPreferences(this.getIfName(), mContext.MODE_PRIVATE);
            Editor editor = sp.edit();
            editor.putString(ETH_IP, this.getIpAddress());
            editor.putString(ETH_MODE, this.getConnectMode());
            editor.putString(ETH_DNS, this.getDnsAddr());
            editor.putString(ETH_ROUTE, this.getRouteAddr());
            editor.putString(ETH_MASK, this.getNetMask());
            editor.commit();
        }

        public void open() {
            SharedPreferences sp = mContext.getSharedPreferences(getIfName(), mContext.MODE_PRIVATE);
            setConnectMode(sp.getString(ETH_MODE, "none"));
            setIpAddress(sp.getString(ETH_IP, "none"));
            setDnsAddr(sp.getString(ETH_DNS, "none"));
            setNetMask(sp.getString(ETH_MASK, "none"));
            setRouteAddr(sp.getString(ETH_ROUTE, "none"));
        }
        public boolean isSaved() {
            SharedPreferences sp = mContext.getSharedPreferences(getIfName(), mContext.MODE_PRIVATE);
            if(sp.getString(ETH_MODE, "none").matches("none"))
                return false;
            else
                return true;
        }

    }

    public EthernetStateTracker(Context context, NotificationManager notify, EthernetDataTracker mDataTracker) {
        mContext = context;
        this.mDataTracker = mDataTracker;
        mMonitor = new EthernetMonitor(mContext, this, mDataTracker);
        mNotificationManager = notify;
		mStateMachines = new ArrayList<EthernetStateMachine>();
    }

	public static EthernetStateMachine getStateMachineByIface(String iface) {
		EthernetStateMachine current = null;
		for (EthernetStateMachine machine : mStateMachines) {
			if (machine.getIfName().matches(iface)) {
				current = machine;
				break;
			}
		}
		return current;
		
	}
    public void Init() {

    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    public boolean stopInterface(boolean suspend) {
        mDataTracker.disconnect();
        return true;
    }

    public int getInterfaceLinkStatus(String iface) {
        return mDataTracker.getEthernetCarrierState(iface);
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

	private void sendMessage(int what, Object obj) {
		if (mHandler != null) {
			Message message =  mHandler.obtainMessage(what, obj);
			mHandler.sendMessage(message);
		}
	}

    public boolean isEthConfigured(String iface) {
		return getStateMachineByIface(iface).isSaved();
    }


    public synchronized EthernetDevInfo getSavedEthConfig(String iface) {
		return getStateMachineByIface(iface);
    }


    public synchronized void UpdateEthDevInfo(EthernetDevInfo info) {
		EthernetStateMachine m = getStateMachineByIface(info.getIfName());
		m.setConnectMode(info.getConnectMode());
		m.setIpAddress(info.getIpAddress());
		m.setDnsAddr(info.getDnsAddr());
		m.setNetMask(info.getNetMask());
		m.setRouteAddr(info.getRouteAddr());
		m.save();
  		
    }

    public void StartPolling() {
 		mMonitor.startMonitoring();
		
    }

	public void configureIfc(EthernetStateMachine m) {
		mDataTracker.configureIfc(m.getIfName(),
									  m.getIpAddress(),
									  m.getRouteAddr(),
									  m.getNetMask(),
									  m.getDnsAddr(),
									  m.getConnectMode(),
									  m.getHwAddr(),
									  m.getLinkUp()
									  );
	}
	
	public boolean reconnect(EthernetStateMachine m, boolean real) {
		boolean isOK = false;
		mGetingIp = true;
		if (!m.getLinkUp())
			return isOK;
		if (m.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_MANUAL) || real)
			isOK = mDataTracker.configureIfc(m.getIfName(),
									  m.getIpAddress(),
									  m.getRouteAddr(),
									  m.getNetMask(),
									  m.getDnsAddr(),
									  m.getConnectMode(),
									  m.getHwAddr(),
									  m.getLinkUp()
									  );

		return isOK;
	}

	public void setUserFlag(boolean flag) {
		mInterfaceStopped = (flag == false ? true : false);
		mDataTracker.setUserFlag(flag);
	}
	
	public String[] getEthernetDevNameList() {	
		final ArrayList<String> result = new ArrayList<String>();		
		for (EthernetStateMachine m : mStateMachines)
			result.add(m.getIfName());
		return result.toArray(new String[result.size()]);
	}

	public void updateEthernetStatus() {
		mStackConnected = false;
		mHWConnected = false;
		
		for (EthernetStateMachine m : mStateMachines) {
			if (m.getConnected()) {
				mStackConnected = true;
				break;
			}
		}
			
		for (EthernetStateMachine m : mStateMachines) {
			if (m.getLinkUp()) {
				mHWConnected = true;
				break;
			}
		}
		postNotification();
	}
	
    private void postNotification() {
        synchronized(this) {
            if(mNotificationManager != null) {
                int icon;
                CharSequence title = "Ethernet Status";
                CharSequence detail;
                if(mNotification == null) {
                    mNotification = new Notification();
                    mNotification.contentIntent = PendingIntent.getActivity(mContext, 0,
                                                  new Intent(NETWORK_STATE_CHANGED_ACTION), 0);
                }
                mNotification.when = System.currentTimeMillis();
                icon = R.drawable.connect_established;
                if(!mHWConnected) {
                    mNotification.icon = R.drawable.connect_no;
                    detail = "Connect is unestablished,Net work cable is not plugined or disable by user.";
                } else if(mInterfaceStopped) {
                    mNotification.icon = R.drawable.connect_established;
                    detail = "Connect is established,But network disabled by user.";
                } else if(mGetingIp) {
                    mNotification.icon = R.drawable.connect_established;
                    detail = "Connect is established,Waiting for IP address.";
                } else if(!mStackConnected) {
                    mNotification.icon = R.drawable.connect_established;
                    detail = "Connect is established,But get IP address failed,please check your ip setting.";
                } else if(mStackConnected) {
                    mNotification.icon = R.drawable.connect_creating;
                    detail = "Ethernet is connected. IP address: " + ip;
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
		scanResult sR;
		connectResult cR;
		EthernetStateMachine m;
        synchronized(this) {
			Log.d("ethapp", "recieved " + msg.what + " message");
            switch(msg.what) {
            case EthernetDataTracker.ETHER_MSG_ADD_INTERFACE:
				sR = (scanResult)msg.obj;
				m = new EthernetStateMachine(mContext, sR.iFace);
				m.setLinkUp(sR.linkUp);
				m.setHwAddr(sR.HwAddr);
				mStateMachines.add(m);
				m.setLinkUp(reconnect(m, true));
				updateEthernetStatus();
				Log.d("ethapp", "recieved " + sR.iFace + " ETHER_MSG_ADD_INTERFACE " + m.getLinkUp());
                break;

			case EthernetDataTracker.ETHER_MSG_INTERFACE_STATUS_CHANGE:
				sR = (scanResult)msg.obj;
				m = getStateMachineByIface(sR.iFace);
				
				if (m != null) {
					m.setLinkUp(sR.linkUp);
					reconnect(m, false);
				}

				if (mHandler != null)
					sendMessage(EthernetDataTracker.ETHER_MSG_INTERFACE_STATUS_CHANGE, sR.iFace);
					
				updateEthernetStatus();
				break;

			case EthernetDataTracker.ETHER_MSG_CONNECTED_SUCCESS:
				cR = (connectResult)msg.obj;
				Log.d("ethapp", "recieved  ETHER_MSG_CONNECTED_SUCCESS");
				setIp(cR.ipAddr);
				m = getStateMachineByIface(cR.iFace);

				if (m != null)
					m.setConnected(true);
				
				mGetingIp = false;

				if (mHandler != null)
					sendMessage(EthernetDataTracker.ETHER_MSG_CONNECTED_SUCCESS, cR.iFace);
					
				updateEthernetStatus();
				break;

			case EthernetDataTracker.ETHER_MSG_CONNECTED_FAILED:
				cR = (connectResult)msg.obj;
				Log.d("ethapp", "recieved  ETHER_MSG_CONNECTED_SUCCESS");
				m = getStateMachineByIface(cR.iFace);

				if (m != null)
					m.setConnected(false);
				
				mGetingIp = false;

				if (mHandler != null)
					sendMessage(EthernetDataTracker.ETHER_MSG_CONNECTED_FAILED, cR.iFace);
				
				updateEthernetStatus();
				break;
				
            }
        }
    }


}
