
package com.android.ethernet;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

public class EthernetSettings extends PreferenceActivity {
    private static final String KEY_TOGGLE_ETH = "toggle_eth";
    private static final String KEY_CONF_ETH = "eth_config";
    private EthernetEnabler mEthEnabler;
    private EthernetConfigDialog mEthConfigDialog;
    private Preference mEthConfigPref;

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        if (preference == mEthConfigPref) {
            mEthConfigDialog.show();
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.ethernet_settings);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        mEthConfigPref = preferenceScreen.findPreference(KEY_CONF_ETH);
        /*
         * TO DO: Add new perference screen for Etherenet Configuration
         */
        initToggles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mEthEnabler.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mEthEnabler.pause();
    }

    private void initToggles() {
        mEthEnabler = new EthernetEnabler(this,
                (CheckBoxPreference) findPreference(KEY_TOGGLE_ETH));

        mEthConfigDialog = new EthernetConfigDialog(this, mEthEnabler);
        mEthEnabler.setConfigDialog(mEthConfigDialog);
    }
}
