package com.example.trigger;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.List;


public class WifiTools {
    private WifiManager wifiManager;

	WifiTools(Context context) {
		 this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
	}

   	String getCurrentSSID() {
        // From android 8.0 only available if GPS on?!
        if (wifiManager != null) {
            WifiInfo info = wifiManager.getConnectionInfo();
            String ssid = info.getSSID();
            if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                // quoted string
                return ssid.substring(1, ssid.length() - 1);
            } else {
                // hexadecimal string...
                return ssid;
            }
        } else {
            return "";
        }
    }

    ArrayList<String> getScannedSSIDs() {
        ArrayList<String> ssids;
        List<ScanResult> results;

        ssids = new ArrayList();
        if (wifiManager != null) {
            results = wifiManager.getScanResults();
            if (results != null) {
                for (ScanResult result : results) {
                    ssids.add(result.SSID);
                }
            }
        }

        return ssids;
    }

    ArrayList<String> getConfiguredSSIDs() {
        List<WifiConfiguration> configs;
        ArrayList<String> ssids;

        ssids = new ArrayList();
        if (wifiManager != null) {
            configs = wifiManager.getConfiguredNetworks();
            if (configs != null) {
                for (WifiConfiguration config : configs) {
                    ssids.add(config.SSID);
                }
            }
        }

        return ssids;
    }

    WifiConfiguration findConfig(List<WifiConfiguration> configs, String ssid) {
        for (WifiConfiguration config : configs) {
            if (config.SSID.equals(ssid)) {
                return config;
            }
        }
        return null;
    }
/*
    // connect to the best wifi that is configured by this app and system
    void connectBestOf(ArrayList<String> ssids) {
        String current_ssid = this.getCurrentSSID();
        List<WifiConfiguration> configs;
        WifiConfiguration config;
        List<ScanResult> scanned;

        if (wifiManager == null) {
            return;
        }

        configs = wifiManager.getConfiguredNetworks();
        scanned = wifiManager.getScanResults();

        if (scanned == null && configs == null) {
            Log.e("Wifi", "Insufficient data for connect.");
            return;
        }

        // TODO: sort by signal
        for (ScanResult scan : scanned) {
            config = findConfig(configs, scan.SSID);
            if (config != null) {
                if (!current_ssid.equals(scan.SSID)) {
                    wifiManager.disconnect();
                    wifiManager.enableNetwork(config.networkId, true);
                    wifiManager.reconnect();
                }
                break;
            }
        }
    }
*/
    boolean isConnected() {
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            // Wi-Fi adapter is ON
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo.getNetworkId() == -1) {
                // not connected to an access point
                return false;
            }
            // connected to an access point
            return true;
        } else {
            // Wi-Fi adapter is off
            return false;
        }
    }
}
