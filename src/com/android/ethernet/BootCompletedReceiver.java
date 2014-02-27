
package com.android.ethernet;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ethernet.EthernetStateTracker;
import android.net.EthernetDataTracker;
import android.net.server.EthernetService;

public class BootCompletedReceiver extends BroadcastReceiver {
    EthernetService mEthernetService;
    EthernetStateTracker mTrack;
    NotificationManager mNotificationManager;
    private EthernetDataTracker mDataTracker;

    @Override

    public void onReceive(final Context context, Intent intent) {
        mNotificationManager = (NotificationManager) context.getSystemService("notification");
        mDataTracker = EthernetDataTracker.getInstance();
        mTrack = new EthernetStateTracker(context, mNotificationManager, mDataTracker);
        mEthernetService = new EthernetService(context, mTrack, mDataTracker);
		EthernetService.setInstance(mEthernetService);
        new Thread() {
            public void run() {
                mTrack.Init();
                mEthernetService.Init();
            }
        } .start();
    }
}
