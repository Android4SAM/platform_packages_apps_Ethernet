
package com.android.ethernet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ethernet.EthernetDevInfo;
import android.net.ethernet.EthernetManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

public class EthernetConfigDialog extends AlertDialog implements DialogInterface.OnClickListener,
        AdapterView.OnItemSelectedListener, View.OnClickListener {
    private final String TAG = "EtherenetSettings";
    private EthernetEnabler mEthEnabler;
    private View mView;
    private Spinner mDevList;
    private TextView mDevs;
    private RadioButton mConTypeDhcp;
    private RadioButton mConTypeManual;
    private EditText mIpaddr;
    private EditText mDns;
    private EditText mGw;
    private EditText mMask;

    private EthernetLayer mEthLayer;
    private EthernetManager mEthManager;
    private EthernetDevInfo mEthInfo;
    private boolean mEnablePending;

    public EthernetConfigDialog(Context context, EthernetEnabler Enabler) {
        super(context);
        mEthLayer = new EthernetLayer(this);
        mEthEnabler = Enabler;
        mEthManager = Enabler.getManager();
        buildDialogContent(context);
    }

    public int buildDialogContent(Context context) {
        this.setTitle(R.string.eth_config_title);
        this.setView(mView = getLayoutInflater().inflate(R.layout.eth_configure, null));
        mDevs = (TextView) mView.findViewById(R.id.eth_dev_list_text);
        mDevList = (Spinner) mView.findViewById(R.id.eth_dev_spinner);
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
        String[] Devs = mEthEnabler.getManager().getDeviceNameList();

        if (Devs != null) {
            Log.i(TAG, "found device: " + Devs[0]);
            updateDevNameList(Devs);
            if (mEthManager.isEthConfigured()) {
                mEthInfo = mEthManager.getSavedEthConfig();
                for (int i = 0; i < Devs.length; i++) {
                    if (Devs[i].equals(mEthInfo.getIfName())) {
                        mDevList.setSelection(i);
                        break;
                    }
                }

                mIpaddr.setText(mEthInfo.getIpAddress());
                mGw.setText(mEthInfo.getRouteAddr());
                mDns.setText(mEthInfo.getDnsAddr());
                mMask.setText(mEthInfo.getNetMask());
                if (mEthInfo.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_DHCP)) {
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
                }
            }
        }
        return 0;
    }

    private void handle_saveconf() {
        EthernetDevInfo info = new EthernetDevInfo();
        info.setIfName(mDevList.getSelectedItem().toString());
        Log.i(TAG, "Config device for " + mDevList.getSelectedItem().toString());
        info.setIpAddress(mIpaddr.getText().toString());
        info.setRouteAddr(mGw.getText().toString());
        info.setDnsAddr(mDns.getText().toString());
        info.setNetMask(mMask.getText().toString());
        if (mConTypeDhcp.isChecked()) {
            Log.i(TAG, "mode dhcp");
            info.setConnectMode(EthernetDevInfo.ETH_CONN_MODE_DHCP);
        } else {
            Log.i(TAG, "mode manual");
            info.setConnectMode(EthernetDevInfo.ETH_CONN_MODE_MANUAL);
        }
        mEthManager.UpdateEthDevInfo(info);
        if (mEnablePending) {
            mEthManager.setEthEnabled(true);
            mEnablePending = false;
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
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

    public void updateDevNameList(String[] DevList) {
        if (DevList != null) {
            ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                    getContext(), android.R.layout.simple_spinner_item, DevList);
            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);
            mDevList.setAdapter(adapter);
        }

    }

    public void enableAfterConfig() {
        // TODO Auto-generated method stub
        mEnablePending = true;
    }
}
