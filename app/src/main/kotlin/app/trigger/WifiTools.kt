package app.trigger

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager

object WifiTools {
    private const val TAG = "WifiTools"
    private var wifiManager: WifiManager? = null
    private var connectivityManager: ConnectivityManager? = null

    fun init(context: Context) {
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    fun matchSSID(ssids: String?, ssid: String): Boolean {
        if (ssids != null) {
            for (element in ssids.split(",").toTypedArray()) {
                val e = element.trim { it <= ' ' }
                if (e.isNotEmpty() && e == ssid) {
                    return true
                }
            }
        }
        return false
    }

    // Note: needs coarse location permission
    fun getCurrentSSID(): String {
        // Note: needs coarse location permission
        return if (wifiManager != null) {
            val info = wifiManager!!.connectionInfo
            val ssid = info.ssid
            if (ssid.length >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                // quoted string
                ssid.substring(1, ssid.length - 1)
            } else {
                // hexadecimal string...
                ssid
            }
        } else {
            ""
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
    fun isConnected(): Boolean {
        val networks = connectivityManager!!.allNetworks
        for (network in networks) {
            val networkInfo = connectivityManager!!.getNetworkInfo(network)
            if (networkInfo!!.type == ConnectivityManager.TYPE_WIFI) {
                return true
            }
        }
        return false
    }

    fun isConnectedWithInternet(): Boolean {
        if (connectivityManager == null) {
            return false
        }
        val mWifi = connectivityManager!!.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        return mWifi!!.isConnected
    }
}
