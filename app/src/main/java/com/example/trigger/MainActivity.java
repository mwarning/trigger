package com.example.trigger;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.Preference;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.net.wifi.WifiInfo;


enum UIState {
    OPEN,
    CLOSED,
    UNKNOWN,
    DISABLED,
}

public class MainActivity extends Activity implements OnTaskCompleted {

    private OnTaskCompleted listener;
    private SharedPreferences prefs;
    private ImageView stateIcon;
    private boolean enableRefreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE") && isWifiConnected()) {
                    new TriggerRequestHandler(listener, prefs).execute(Action.update_state);
                } else {
                    changeUI(UIState.DISABLED);
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(broadcastReceiver, intentFilter);
/*
        CheckBox checkBoxLab = (CheckBox) findViewById(R.id.checkbox_ignore);
        checkBoxLab.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                System.out.println(String.format("checkbox changed %b", isChecked));
                if (isChecked) {
                    HttpsTrustManager.allowAllSSL();
                } else {
                    // TODO: disable
                }
            }
        });
*/
        stateIcon = (ImageView) findViewById(R.id.stateIcon);

        listener = this;
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        Button button_open = (Button) findViewById(R.id.button_open);
        button_open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new TriggerRequestHandler(listener, prefs).execute(Action.open_door);
            }
        });

        Button button_close = (Button) findViewById(R.id.button_close);
        button_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new TriggerRequestHandler(listener, prefs).execute(Action.close_door);
            }
        });
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
/*
    private boolean isWifiConnected2() {
        Context context = this.getApplicationContext();
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService( Context.CONNECTIVITY_SERVICE );
        //NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        NetworkInfo wifiNetInfo = connectivityManager.getNetworkInfo( ConnectivityManager.TYPE_WIFI );

        Log.i("[WIFI-STATE]", wifiNetInfo.getDetailedState().toString());

        return wifiNetInfo.isConnected();
    }
*/
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

        // Update action bar menu
        invalidateOptionsMenu();
    }

    @Override
    public void onTaskCompleted(String result) {

        Log.i("[GET RESULT]", result);

        if (result.equals("UNLOCKED")) {
            // Door unlocked
            changeUI(UIState.OPEN);
        } else if (result.equals("LOCKED")) {
            // Door locked
            changeUI(UIState.CLOSED);
        } else {
            changeUI(UIState.UNKNOWN);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (this.isWifiConnected()) {
            new TriggerRequestHandler(listener, prefs).execute(Action.update_state);
        }
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
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            // Launch settings activity
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
            return true;
        }

        if (id == R.id.action_reload) {
            new TriggerRequestHandler(listener, prefs).execute(Action.update_state);
        }

        return super.onOptionsItemSelected(item);
    }
}
