package com.michiwend.spincterremote;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

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
    private boolean enabelRefreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if( intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE") && isWifiConnected(context) ) {
                    new SphincterRequestHandler(listener, prefs).execute(Action.update_state);
                }
                else {
                    changeUI(UIState.DISABLED);
                }
            }
        };

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
                new SphincterRequestHandler(listener, prefs).execute(Action.open_door);
            }
        });

        Button button_close = (Button) findViewById(R.id.button_close);

        button_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new SphincterRequestHandler(listener, prefs).execute(Action.close_door);
            }
        });

    }

    private boolean isWifiConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService( Context.CONNECTIVITY_SERVICE );
        //NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        NetworkInfo wifiNetInfo = connectivityManager.getNetworkInfo( ConnectivityManager.TYPE_WIFI );

        Log.i("[WIFI-STATE]", wifiNetInfo.getDetailedState().toString());

        if(wifiNetInfo.isConnected()) {
            return true;
        }
        else {
            return false;
        }
    }

    private void changeUI(UIState state) {

        Button bc = (Button) findViewById(R.id.button_close);
        Button bo = (Button) findViewById(R.id.button_open);

        switch(state) {
            case OPEN:
                stateIcon.setImageResource(R.drawable.labstate_open);
                break;

            case CLOSED:
                stateIcon.setImageResource(R.drawable.labstate_closed);
                break;

            case DISABLED:
                stateIcon.setImageResource(R.drawable.labstate_wifi);
                bc.setEnabled(false);
                bo.setEnabled(false);
                break;

            case UNKNOWN:
                stateIcon.setImageResource(R.drawable.labstate_unknown);
                break;
        }

        if(state != UIState.DISABLED) {
            enabelRefreshButton = true;
        }
        // update action bar menu
        invalidateOptionsMenu();

    }

    @Override
    public void onTaskCompleted(String result) {

        Log.i("[GET RESULT]", result);

        if( result.equals("UNLOCKED") ) {
            // Door unlocked
            changeUI(UIState.OPEN);
        }
        else if ( result.equals("LOCKED") ) {
            // Door locked
            changeUI(UIState.CLOSED);
        }
        else {
            changeUI(UIState.UNKNOWN);
        }
    }


    @Override
    protected void onStart() {
        super.onStart();

        if(isWifiConnected(this.getApplicationContext())) {
            new SphincterRequestHandler(listener, prefs).execute(Action.update_state);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem refreshMenuItem = menu.findItem(R.id.action_reload);

        if(!enabelRefreshButton) {
            refreshMenuItem.setEnabled(false);
            refreshMenuItem.getIcon().setAlpha(130);
        }
        else {
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
        if(id == R.id.action_reload) {
            new SphincterRequestHandler(listener, prefs).execute(Action.update_state);
        }

        return super.onOptionsItemSelected(item);
    }

}
