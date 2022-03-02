package app.trigger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;


public class WifiTools {
    private static final String TAG = "WifiTools";
    private static WifiManager wifiManager;
    private static ConnectivityManager connectivityManager;

    static void init(Context context) {
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static boolean matchSSID(String ssids, String ssid) {
        if (ssids == null || ssids.length() == 0) {
            return false;
        }
        for (String element : ssids.split(",")) {
            String e = element.trim();
            if (!e.isEmpty() && e.equals(ssid)) {
                return true;
            }
        }
        return false;
    }

    public static String getCurrentSSID() {
        // Note: needs coarse location permission
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

/*
    public static ArrayList<String> getScannedSSIDs() {
        ArrayList<String> ssids;
        List<ScanResult> results;

        ssids = new ArrayList<>();
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

    public static ArrayList<String> getConfiguredSSIDs() {
        // Note: needs coarse location permission
        List<WifiConfiguration> configs;
        ArrayList<String> ssids;

        ssids = new ArrayList<>();
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

    public static WifiConfiguration findConfig(List<WifiConfiguration> configs, String ssid) {
        for (WifiConfiguration config : configs) {
            if (config.SSID.equals(ssid)) {
                return config;
            }
        }
        return null;
    }

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

    public static boolean isConnected() {
        Network[] networks = connectivityManager.getAllNetworks();
        for (Network network : networks){
           NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);

           if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
               return true;
           }
        }
        return false;
    }

    public static boolean isConnectedWithInternet() {
        if (connectivityManager == null) {
            return false;
        }

        NetworkInfo mWifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi.isConnected();
    }
}
