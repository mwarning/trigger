package com.example.trigger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.net.ConnectivityManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.view.accessibility.AccessibilityEvent.INVALID_POSITION;


enum Action {
    open_door,
    close_door,
    update_state
}

public class MainActivity extends AppCompatActivity implements OnTaskCompleted {
    private boolean enableRefreshButton = false;
    private boolean enableEditButton = false;
    private OnTaskCompleted listener;
    private ImageView stateIcon;
    private Spinner spinner;
    private Wifi wifi;

    // helper class for spinner
    private static class SpinnerItem {
        public final int id;
        public final String name;

        public SpinnerItem(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static List<String> splitCommaSeparated(String str) {
        ArrayList<String> ret = new ArrayList();
        for (String element : str.split(",")) {
            String e = element.trim();
            if (e.length() > 0) {
                ret.add(e);
            }
        }
        return ret;
    }

    private int getPreferredSpinnerIndex(ArrayList<Setup> setups, Setup current) {
        String ssid = wifi.getCurrentSSID();

        // select by ssid
        if (ssid.length() > 0) {
            for (int i = 0; i < setups.size(); i += 1) {
                Setup setup = setups.get(i);
                String ssids = setup.getSSIDs();
                if (splitCommaSeparated(ssids).contains(ssid)) {
                    return i;
                }
            }
        }

        // keep previous selection
        if (current != null) {
            for (int i = 0; i < setups.size(); i += 1) {
                if (current.getId() == setups.get(i).getId()) {
                    return i;
                }
            }
        }

        // select first item
        if (setups.size() > 0) {
            return 0;
        } else {
            return -1;
        }
    }

    Setup getSelectedSetup() {
        SpinnerItem item = (SpinnerItem) spinner.getSelectedItem();
        if (item != null) {
            return Settings.getSetup(item.id);
        } else {
            return null;
        }
    }

    private void updateSpinner() {
        ArrayList<Setup> setups = Settings.getAllSetups();
        ArrayList<SpinnerItem> items = new ArrayList();

        for (Setup setup : setups) {
            items.add(new SpinnerItem(setup.getId(), setup.getName()));
        }

        // sort setups by name
        Collections.sort(items, new Comparator<SpinnerItem>() {
            @Override public int compare(SpinnerItem s1, SpinnerItem s2) {
                return s1.name.compareTo(s2.name);
            }
        });

        ArrayAdapter<SpinnerItem> adapter = new ArrayAdapter<SpinnerItem>(this, R.layout.main_spinner, items);

        Setup current = getSelectedSetup();
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

        // something is selected
        if (spinner.getSelectedItemPosition() != INVALID_POSITION) {
            enableEditButton = true;
        }
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
        this.wifi = new Wifi(context);
        Settings.init(context);

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE") && wifi.isConnected()) {
                    updateSpinner(); // auto select possible entry
                    callRequestHandler(Action.update_state);
                } else {
                    changeUI(StateCode.DISABLED);
                }
            }
        };

        spinner = (Spinner) findViewById(R.id.selection_spinner);
        stateIcon = (ImageView) findViewById(R.id.stateIcon);

        updateSpinner();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(broadcastReceiver, intentFilter);

        listener = this;
    }

    public void onUnlock(View view) {
        callRequestHandler(Action.open_door);
    }

    public void onUpdateState(View view) {
        callRequestHandler(Action.update_state);
    }

    public void onLock(View view) {
        callRequestHandler(Action.close_door);
    }

    private void changeUI(StateCode state) {
        ImageButton bc = (ImageButton) findViewById(R.id.Lock);
        ImageButton bo = (ImageButton) findViewById(R.id.Unlock);

        switch (state) {
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

        if(state != StateCode.DISABLED) {
            enableRefreshButton = true;
        }

        // update action bar menu
        invalidateOptionsMenu();
    }

    @Override
    public void onTaskCompleted(DoorReply r) {
        //Log.d("MainActivity.onTaskCompleted", "message: " + r.message);

        Setup setup = getSelectedSetup();
        if (setup == null) {
            // should not happen
            return;
        }

        DoorState state = setup.parseReply(r);

        // change state image
        changeUI(state.code);

        // display message
        if (state.message.length() > 0) {
            Context context = getApplicationContext();
            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show();
        }
    }

    private void callRequestHandler(Action action) {
        Setup setup = getSelectedSetup();
        if (setup instanceof HttpsDoorSetup) {
            HttpsDoorSetup httpsSetup = (HttpsDoorSetup) setup;
            new HttpsRequestHandler(listener).execute(action, httpsSetup);
        } else if (setup instanceof SshDoorSetup) {
            SshDoorSetup sshSetup = (SshDoorSetup) setup;
            new SshRequestHandler(listener).execute(action, sshSetup);
        } else {
            changeUI(StateCode.DISABLED);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (wifi.isConnected()) {
            callRequestHandler(Action.update_state);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem refreshMenuItem = menu.findItem(R.id.action_reload);
        MenuItem editMenuItem = menu.findItem(R.id.action_edit);

        if (enableEditButton) {
            editMenuItem.setEnabled(true);
            editMenuItem.getIcon().setAlpha(255);
        } else {
            editMenuItem.setEnabled(false);
            editMenuItem.getIcon().setAlpha(130);
        }

        if (enableRefreshButton) {
            refreshMenuItem.setEnabled(true);
            refreshMenuItem.getIcon().setAlpha(255);
        } else {
            refreshMenuItem.setEnabled(false);
            refreshMenuItem.getIcon().setAlpha(130);
        }

        return true;
    }

/*
    private void connectNextWifi() {
        // collect app configured SSIDs
        ArrayList<Setup> setups = Settings.getAllSetups();
        ArrayList<String> ssids = new ArrayList();

        for (Setup setup : setups) {
            ssids.addAll(splitCommaSeparated(setup.getSSIDs()));
        }

        wifi.connectBestOf(ssids);
        updateSpinner();
    }
*/

    @Override
    public boolean onOptionsItemSelected(MenuItem menu_item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = menu_item.getItemId();

        if (id == R.id.action_edit) {
            Setup setup = getSelectedSetup();
            int setup_id = (setup != null) ? setup.getId() : -1;
            Intent i = new Intent(this, SetupActivity.class);
            i.putExtra("setup_id", setup_id);
            startActivity(i);
            return true;
        }

        if (id == R.id.action_new) {
            Intent i = new Intent(this, SetupActivity.class);
            i.putExtra("setup_id", -1);
            startActivity(i);
            return true;
        }

        if (id == R.id.action_about) {
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
