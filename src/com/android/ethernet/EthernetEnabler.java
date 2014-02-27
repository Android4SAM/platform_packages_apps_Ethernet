
package com.android.ethernet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.text.TextUtils;
import android.util.Log;
import android.net.server.EthernetService;

public class EthernetEnabler implements Preference.OnPreferenceChangeListener {
    private static final boolean LOCAL_LOGD = true;
    private static final String TAG = "EthernetEnabler";
    private Context mContext;
    private CheckBoxPreference mEthCheckBoxPref;
    private final CharSequence mOriginalSummary;

    public static final int ETH_STATE_DISABLED = 0;
    public static final int ETH_STATE_ENABLED = 1;
    public static final int ETH_STATE_UNKNOWN = 2;

    public EthernetEnabler(Context context,
                           CheckBoxPreference ethernetCheckBoxPreference) {
        mContext = context;
        mEthCheckBoxPref = ethernetCheckBoxPreference;
        mOriginalSummary = ethernetCheckBoxPreference.getSummary();
        ethernetCheckBoxPreference.setPersistent(false);
        if(EthernetService.getInstance().getEthState() == ETH_STATE_ENABLED) {
            mEthCheckBoxPref.setChecked(true);
        }
    }

    public void resume() {
        mEthCheckBoxPref.setOnPreferenceChangeListener(this);
    }

    public void pause() {
        // mContext.unregisterReceiver(mEthStateReceiver);
        mEthCheckBoxPref.setOnPreferenceChangeListener(null);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        setEthEnabled((Boolean) newValue);
        return false;
    }

    private void setEthEnabled(final boolean enable) {
        Log.i(TAG, "Show configuration dialog " + enable);
        // Disable button
        mEthCheckBoxPref.setEnabled(false);
        mEthCheckBoxPref.setChecked(enable);
		EthernetService.getInstance().setEthState(enable ? ETH_STATE_ENABLED : ETH_STATE_DISABLED);
        // enable button
        mEthCheckBoxPref.setEnabled(true);
    }

    public boolean getEthEnabled() {
        return mEthCheckBoxPref.isChecked();
    }

    private void handleEthStateChanged(int ethState, int previousEthState) {
    }

    private void handleNetworkStateChanged(NetworkInfo networkInfo) {
        if(LOCAL_LOGD) {
            Log.d(TAG, "Received network state changed to " + networkInfo);
        }
        /*
         * if (mEthernetManager.isEthEnabled()) { String summary =
         * ethStatus.getStatus(mContext,
         * mEthManager.getConnectionInfo().getSSID(),
         * networkInfo.getDetailedState());
         * mEthCheckBoxPref.setSummary(summary); }
         */
    }

    private boolean isEnabledByDependency() {
        Preference dep = getDependencyPreference();
        if(dep == null) {
            return true;
        }
        return !dep.shouldDisableDependents();
    }

    private Preference getDependencyPreference() {
        String depKey = mEthCheckBoxPref.getDependency();
        if(TextUtils.isEmpty(depKey)) {
            return null;
        }
        return mEthCheckBoxPref.getPreferenceManager().findPreference(depKey);
    }

    private static String getHumanReadableEthState(int wifiState) {
        switch(wifiState) {
        case ETH_STATE_DISABLED:
            return "Disabled";
        case ETH_STATE_ENABLED:
            return "Enabled";
        default:
            return "Some other state!";
        }
    }
}
