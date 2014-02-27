
package com.android.ethernet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ethernet.EthernetDevInfo;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.net.server.EthernetService;
import android.net.ethernet.EthernetStateTracker;
import android.net.ethernet.EthernetStateTracker.EthernetStateMachine;

public class EthernetConfigDialog extends AlertDialog implements DialogInterface.OnClickListener,
    AdapterView.OnItemSelectedListener, View.OnClickListener {
    private final String TAG = "EtherenetSettings";
    private String mDevName;
    private View mView;
    private RadioButton mConTypeDhcp;
    private RadioButton mConTypeManual;
    private EditText mIpaddr;
    private EditText mDns;
    private EditText mGw;
    private EditText mMask;
	private static EthernetConfigDialog sInstance;

    private EthernetDevInfo mEthInfo;
    private boolean mEnablePending;
	private EthernetStateMachine mState;

    public EthernetConfigDialog(Context context, String devName) {
        super(context);
		mDevName = devName;
		mState = EthernetService.getInstance().getStateTracker().getStateMachineByIface(devName);
    }

	public void showConfigDialog() {
		buildDialogContent(this.getContext());
		show();
	}

	public static EthernetConfigDialog getInstance(Context context, String devName) {
		return sInstance = new EthernetConfigDialog(context, devName);
	}
	
    private int buildDialogContent(Context context) {
        this.setTitle(R.string.eth_config_title);
        this.setView(mView = getLayoutInflater().inflate(R.layout.eth_configure, null));
        mConTypeDhcp = (RadioButton) mView.findViewById(R.id.dhcp_radio);
        mConTypeManual = (RadioButton) mView.findViewById(R.id.manual_radio);
        mIpaddr = (EditText) mView.findViewById(R.id.ipaddr_edit);
        mMask = (EditText) mView.findViewById(R.id.netmask_edit);
        mDns = (EditText) mView.findViewById(R.id.eth_dns_edit);
        mGw = (EditText) mView.findViewById(R.id.eth_gw_edit);
        mConTypeDhcp.setChecked(true);
        mConTypeManual.setChecked(false);
        mIpaddr.setEnabled(false);
        mMask.setEnabled(false);
        mDns.setEnabled(false);
        mGw.setEnabled(false);
        mConTypeManual.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                mIpaddr.setEnabled(true);
                mDns.setEnabled(true);
                mGw.setEnabled(true);
                mMask.setEnabled(true);

			if (mState.isSaved())
				mState.open();

				mIpaddr.setText(mState.getIpAddress());
				mGw.setText(mState.getRouteAddr());
				mDns.setText(mState.getDnsAddr());
				mMask.setText(mState.getNetMask());		
            }
        });
        mConTypeDhcp.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                mIpaddr.setEnabled(false);
                mDns.setEnabled(false);
                mGw.setEnabled(false);
                mMask.setEnabled(false);
            }
        });
        this.setInverseBackgroundForced(true);
        this.setButton(BUTTON_POSITIVE, context.getText(R.string.eth_conf_save), this);
        this.setButton(BUTTON_NEGATIVE, context.getText(R.string.eth_conf_cancel), this);

		if(mState.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_DHCP)) {
			mIpaddr.setEnabled(false);
			mDns.setEnabled(false);
			mGw.setEnabled(false);
			mMask.setEnabled(false);
		} else {		
			mConTypeDhcp.setChecked(false);
			mConTypeManual.setChecked(true);
			mIpaddr.setEnabled(true);
			mDns.setEnabled(true);
			mGw.setEnabled(true);
			mMask.setEnabled(true);
			mIpaddr.setText(mState.getIpAddress());
			mGw.setText(mState.getRouteAddr());
			mDns.setText(mState.getDnsAddr());
			mMask.setText(mState.getNetMask());
		}
        return 0;
    }

    private void handle_saveconf() {
        EthernetDevInfo info = new EthernetDevInfo();
        info.setIfName(mDevName);
        info.setIpAddress(mIpaddr.getText().toString());

        info.setRouteAddr(mGw.getText().toString());
        info.setDnsAddr(mDns.getText().toString());
        info.setNetMask(mMask.getText().toString());
		
        if(mConTypeDhcp.isChecked()) {
            Log.i(TAG, "mode dhcp");
            info.setConnectMode(EthernetDevInfo.ETH_CONN_MODE_DHCP);
        } else {
            Log.i(TAG, "mode manual");
            info.setConnectMode(EthernetDevInfo.ETH_CONN_MODE_MANUAL);
        }
		
        EthernetService.getInstance().getStateTracker().UpdateEthDevInfo(info);
		EthernetService.getInstance().getStateTracker().reconnect(mState, true);
    }

    public void onClick(DialogInterface dialog, int which) {
        switch(which) {
        case BUTTON_POSITIVE:
            handle_saveconf();
            break;
        case BUTTON_NEGATIVE:
            // Don't need to do anything
            break;
        default:
            Log.e(TAG, "Unknow button");
        }
    }

    public void onItemSelected(AdapterView<?> parent, View view, int position,
                               long id) {
        // TODO Auto-generated method stub
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // TODO Auto-generated method stub
    }

    public void onClick(View v) {
        // TODO Auto-generated method stub
    }

    public void SetDevName(String devName) {
        mDevName = devName;
    }

	public String getDevName() {
		return mDevName;
	}
    public void enableAfterConfig() {
        // TODO Auto-generated method stub
        mEnablePending = true;
    }
   
    public boolean checkIp(String ipaddr) {
        if (ipaddr == null)
                return false;
        String[] parts = ipaddr.split("\\.");
        if (parts.length != 4)
                return false;
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);
        int c = Integer.parseInt(parts[2]);
        int d = Integer.parseInt(parts[3]);
        if (a > 255 || a < 0)
                return false;
        if (b > 255 || b < 0)
                return false;
        if (c > 255 || c < 0)
                return false;
        if (d > 255 || d < 0)
                return false;
        return true;
    }
}
