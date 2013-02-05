
package com.android.ethernet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ethernet.EthernetManager;

public class EthernetLayer {
    private static final String TAG = "EthernetLayer";

    private EthernetManager mEthManager;
    private String[] mDevList;
    private EthernetConfigDialog mDialog;

    EthernetLayer(EthernetConfigDialog configdialog) {
        mDialog = configdialog;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(EthernetManager.ETH_DEVICE_SCAN_RESULT_READY)) {
                handleDevListChanges();
            }
        }
    };

    private void handleDevListChanges() {
        mDevList = mEthManager.getDeviceNameList();
        mDialog.updateDevNameList(mDevList);
    }
}
