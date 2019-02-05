package com.example.trigger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;


enum UIState {
    OPEN,
    CLOSED,
    UNKNOWN,
    DISABLED,
}

public class MainActivity extends AppCompatActivity implements OnTaskCompleted {
    private boolean enableRefreshButton;
    private OnTaskCompleted listener;
    private SharedPreferences prefs;
    private ImageView stateIcon;

    private Setup getSelectedSetup() {
        Spinner spinner = (Spinner) findViewById(R.id.selection_spinner);
        return (Setup) spinner.getSelectedItem();
    }

    private int getPreferredSpinnerIndex(ArrayList<Setup> items, Setup current) {
        String ssid = getWifiSSID();

        // Select by ssid
        if (ssid.length() > 0) {
            for (int i = 0; i < items.size(); i += 1) {
                Setup setup = items.get(i);
                String ssids = setup.getSSIDs();
                if (Arrays.asList(ssids.trim().split("\\s*,\\s*")).contains(ssid)) {
                    return i;
                }
            }
        }

        // Keep previous selection except for the the DummySetup
        if (current != null && !(current instanceof DummySetup)) {
            for (int i = 0; i < items.size(); i += 1) {
                if (current.getId() == items.get(i).getId()) {
                    return i;
                }
            }
        }

        // Select first item
        if (items.size() > 0) {
            return 0;
        } else {
            Log.w("MainActivity.getPreferredSpinnerIndex", "spinner list empty - should not happen");
            return -1;
        }
    }

    private void updateSpinner() {
        ArrayList<Setup> setups = Settings.all_setups();
        Collections.sort(setups, new Comparator<Setup>() {
            @Override public int compare(Setup s1, Setup s2) {
                return s1.getName().compareTo(s2.getName());
            }
        });

        setups.add(new DummySetup(getResources().getString(R.string.new_entry)));

        ArrayAdapter<Setup> adapter = new ArrayAdapter<Setup>(this,
            android.R.layout.simple_spinner_item, setups);

        Setup current = getSelectedSetup();
        Spinner spinner = (Spinner) findViewById(R.id.selection_spinner);
        spinner.setAdapter(adapter);
        spinner.setSelection(getPreferredSpinnerIndex(setups, current));
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                callRequestHandler(Action.update_state);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // nothing to do
            }

        });
    }

    @Override
    protected void onResume() {
        updateSpinner();
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = this.getApplicationContext();
        Settings.load(this.getApplicationContext());

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE") && isWifiConnected()) {
                    updateSpinner(); // auto select possible entry
                    callRequestHandler(Action.update_state);
                } else {
                    changeUI(UIState.DISABLED);
                }
            }
        };

        updateSpinner();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(broadcastReceiver, intentFilter);

        stateIcon = (ImageView) findViewById(R.id.stateIcon);

        listener = this;
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        Button button_open = (Button) findViewById(R.id.button_open);
        button_open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callRequestHandler(Action.open_door);
            }
        });

        Button button_close = (Button) findViewById(R.id.button_close);
        button_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callRequestHandler(Action.close_door);
            }
        });
    }

    private String getWifiSSID() {
        // From android 8.0 only available if GPS on?!
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo();
        String ssid = info.getSSID();
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            // quoted string
            return ssid.substring(1, ssid.length() - 1);
        } else {
            // hexadecimal string...
            return ssid;
        }
    }

    private boolean isWifiConnected() {
        WifiManager wifiMgr = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if (wifiMgr.isWifiEnabled()) { // Wi-Fi adapter is ON
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();

            if (wifiInfo.getNetworkId() == -1) {
                return false; // Not connected to an access point
            }
            return true; // Connected to an access point
        } else {
            return false; // Wi-Fi adapter is OFF
        }
    }

    private void changeUI(UIState state) {
        Button bc = (Button) findViewById(R.id.button_close);
        Button bo = (Button) findViewById(R.id.button_open);

        switch(state) {
            case OPEN:
                stateIcon.setImageResource(R.drawable.state_open);
                bc.setEnabled(true);
                bo.setEnabled(true);
                break;

            case CLOSED:
                stateIcon.setImageResource(R.drawable.state_closed);
                bc.setEnabled(true);
                bo.setEnabled(true);
                break;

            case DISABLED:
                stateIcon.setImageResource(R.drawable.state_wifi);
                bc.setEnabled(false);
                bo.setEnabled(false);
                enableRefreshButton = false;
                break;

            case UNKNOWN:
                stateIcon.setImageResource(R.drawable.state_unknown);
                // Enabled, in case the API does not support state queries
                bc.setEnabled(true);
                bo.setEnabled(true);
                break;
        }

        if(state != UIState.DISABLED) {
            enableRefreshButton = true;
        }

        // update action bar menu
        invalidateOptionsMenu();
    }

    @Override
    public void onTaskCompleted(String result) {
        Log.i("[GET RESULT]", result);

        if (result.equals("UNLOCKED")) {
            // door unlocked
            changeUI(UIState.OPEN);
        } else if (result.equals("LOCKED")) {
            // door locked
            changeUI(UIState.CLOSED);
        } else {
            changeUI(UIState.UNKNOWN);
        }
    }

    private void callRequestHandler(Action action) {
        Setup setup = getSelectedSetup();
        if (setup instanceof DummySetup) {
            changeUI(UIState.DISABLED);
        } else {
            new HttpsRequestHandler(listener, prefs).execute(Action.update_state, setup);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (this.isWifiConnected()) {
            callRequestHandler(Action.update_state);
        }
    }

    @Override
    protected boolean onPrepareOptionsPanel(View view, Menu menu) {
        if (menu != null) {
            if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
                try {
                    Method m = menu.getClass().getDeclaredMethod(
                            "setOptionalIconsVisible", Boolean.TYPE);
                    m.setAccessible(true);
                    m.invoke(menu, true);
                } catch (Exception e) {
                    Log.e(getClass().getSimpleName(), "onMenuOpened...unable to set icons for overflow menu", e);
                }
            }
        }
        return super.onPrepareOptionsPanel(view, menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem refreshMenuItem = menu.findItem(R.id.action_reload);

        if (!enableRefreshButton) {
            refreshMenuItem.setEnabled(false);
            refreshMenuItem.getIcon().setAlpha(130);
        } else {
            refreshMenuItem.setEnabled(true);
            refreshMenuItem.getIcon().setAlpha(255);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menu_item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = menu_item.getItemId();
        if (id == R.id.action_edit) {
            // launch settings activity (also for "New Entry" dummy entry)
            Setup setup = getSelectedSetup();
            if (setup != null) {
                Intent i = new Intent(this, SetupActivity.class);
                i.putExtra("setup_id", setup.getId());
                startActivity(i);
                return true;
            }
        }

        if (id == R.id.action_about) {
            // launch about activity
            Intent i = new Intent(this, AboutActivity.class);
            startActivity(i);
            return true;
        }

        if (id == R.id.action_reload) {
            callRequestHandler(Action.update_state);
        }

        return super.onOptionsItemSelected(menu_item);
    }
}
