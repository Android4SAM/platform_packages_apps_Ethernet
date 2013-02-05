
package com.android.ethernet;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ethernet.EthernetStateTracker;
import android.net.server.EthernetService;
import android.os.ServiceManager;

public class BootCompletedReceiver extends BroadcastReceiver {
    EthernetService mEthernetService;
    EthernetStateTracker mTrack;
    NotificationManager mNotificationManager;

    @Override
    public void onReceive(final Context context, Intent intent) {
        mNotificationManager = (NotificationManager) context.getSystemService("notification");

        mTrack = new EthernetStateTracker(context, mNotificationManager);
        mEthernetService = new EthernetService(context, mTrack);

        new Thread() {
            public void run() {
                ServiceManager.addService("ETH_SERVICE", mEthernetService);
                mTrack.Init();
                mEthernetService.Init();

            }
        }.start();

    }

}
