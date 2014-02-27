
package com.android.ethernet;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.content.IntentFilter;
import android.util.Log;
import android.os.Process;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.EthernetDataTracker;
import android.net.server.EthernetService;
import android.net.ethernet.EthernetStateTracker;
import android.net.ethernet.EthernetStateTracker.EthernetStateMachine;
import java.util.*;

public class EthernetSettings extends PreferenceActivity {
    private static final String KEY_TOGGLE_ETH = "toggle_eth";
    private static final String KEY_CONF_ETH = "eth_config";
    private EthernetEnabler mEthEnabler;
    private EthernetConfigDialog mEthConfigDialog;
    private CheckBoxPreference mEthtogglePref;
	private static List<Preference> mDevPreference;

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        if(preference == preferenceScreen.findPreference(KEY_TOGGLE_ETH)) {
            updateDeviceList();
        } else {
        	Preference cp = (Preference)preferenceScreen.findPreference(preference.getTitle().toString());
			mEthConfigDialog = EthernetConfigDialog.getInstance(this, preference.getTitle().toString());
        	mEthConfigDialog.showConfigDialog();
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("getpid", "Ethernet Settings pid: " + Process.myPid());
        addPreferencesFromResource(R.xml.ethernet_settings);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        mEthtogglePref = (CheckBoxPreference) preferenceScreen.findPreference(KEY_TOGGLE_ETH);
		mDevPreference = new ArrayList<Preference>();
		buildDeviceList();
        updateDeviceList();
        /*
         * TO DO: Add new perference screen for Etherenet Configuration
         */
        initToggles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mEthEnabler.resume();
        updateDeviceList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mEthEnabler.pause();
    }

    private Handler handler = new Handler() {  
          public void handleMessage(Message msg) {   
               switch (msg.what) {   
                    default:   
                         updateDeviceList(); 
                         break;   
               }   
               super.handleMessage(msg);   
          }   
     };
	
    private void initToggles() {
        mEthEnabler = new EthernetEnabler(this,
			(CheckBoxPreference) findPreference(KEY_TOGGLE_ETH));				
		EthernetService.getInstance().setStateHandler(handler);
    }

	private void buildDeviceList() {
		String []devs = EthernetService.getInstance().getStateTracker().getEthernetDevNameList();
		for (String s : devs) {
			Preference newPreference = new Preference(this);
			newPreference.setTitle(s);
			newPreference.setKey(s);
			mDevPreference.add(newPreference);
		}
	}
    private void updateDeviceList() {
        Preference group = getPreferenceScreen().findPreference("device_eth");
        getPreferenceScreen().removeAll();
        getPreferenceScreen().addPreference(mEthtogglePref);
        getPreferenceScreen().addPreference(group);
		
		for (Preference p : mDevPreference) {
			EthernetStateMachine m = EthernetService.getInstance()
				.getStateTracker().getStateMachineByIface(p.getTitle().toString());
				p.setEnabled(m.getLinkUp());
				getPreferenceScreen().addPreference(p);
		}


    }
}

