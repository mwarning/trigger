package com.example.trigger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
import static android.view.accessibility.AccessibilityEvent.INVALID_POSITION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.example.trigger.DoorState.StateCode;
import com.example.trigger.https.HttpsRequestHandler;
import com.example.trigger.ssh.SshRequestHandler;
import com.example.trigger.bluetooth.BluetoothRequestHandler;
import com.example.trigger.mqtt.MqttRequestHandler;

import org.json.JSONObject;


public class MainActivity extends AppCompatActivity implements OnTaskCompleted {
    private boolean enableRefreshItem = false;
    private boolean hasSetupSelected = false;
    private ImageView stateImage;
    private ImageButton lockButton;
    private ImageButton unlockButton;
    private Spinner spinner;
    private WifiTools wifi;
    private Animation pressed;

    private Bitmap state_open_default_image;
    private Bitmap state_closed_default_image;
    private Bitmap state_wifi_default_image;
    private Bitmap state_unknown_default_image;

    public enum Action {
        open_door,
        close_door,
        update_state
    }

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
            if (!e.isEmpty()) {
                ret.add(e);
            }
        }
        return ret;
    }

    private static int getItemIndex(ArrayList<SpinnerItem> items, int id) {
        for (int i = 0; i < items.size(); i += 1) {
            if (items.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    private int getPreferredSpinnerIndex(ArrayList<SpinnerItem> items, ArrayList<Setup> setups, Setup current) {
        String ssid = wifi.getCurrentSSID();

        // select by ssid
        if (ssid.length() > 0) {
            for (Setup setup : setups) {
                String ssids = setup.getSSIDs();
                if (splitCommaSeparated(ssids).contains(ssid)) {
                    return getItemIndex(items, setup.getId());
                }
            }
        }

        // keep previous selection
        if (current != null) {
            for (Setup setup : setups) {
                if (current.getId() == setup.getId()) {
                    return getItemIndex(items, setup.getId());
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

    int getSelectedSetupId() {
        SpinnerItem item = (SpinnerItem) spinner.getSelectedItem();
        if (item != null) {
            return item.id;
        } else {
            return -1;
        }
    }

    private void updateSpinner() {
        ArrayList<Setup> setups = Settings.getSetups();
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

        Setup current = getSelectedSetup();
        ArrayAdapter<SpinnerItem> adapter = new ArrayAdapter<SpinnerItem>(this, R.layout.main_spinner, items);
        spinner.setAdapter(adapter);
        spinner.setSelection(getPreferredSpinnerIndex(items, setups, current));
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
        if (spinner.getSelectedItemPosition() == INVALID_POSITION) {
            hasSetupSelected = false;
        } else {
            hasSetupSelected = true;
        }
    }

    @Override
    protected void onResume() {
        updateSpinner();
        invalidateOptionsMenu();
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = this.getApplicationContext();
        this.wifi = new WifiTools(context);
        Settings.init(context);

        Resources res = getResources();
        state_open_default_image = BitmapFactory.decodeResource(res, R.drawable.state_open);
        state_closed_default_image = BitmapFactory.decodeResource(res, R.drawable.state_closed);
        state_wifi_default_image = BitmapFactory.decodeResource(res, R.drawable.state_wifi);
        state_unknown_default_image = BitmapFactory.decodeResource(res, R.drawable.state_unknown);

        spinner = (Spinner) findViewById(R.id.selection_spinner);
        stateImage = (ImageView) findViewById(R.id.stateImage);
        lockButton = (ImageButton) findViewById(R.id.Lock);
        unlockButton = (ImageButton) findViewById(R.id.Unlock);
        pressed = AnimationUtils.loadAnimation(this, R.anim.pressed);

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

        updateSpinner();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(broadcastReceiver, intentFilter);
    }

    public void onUpdateState(View view) {
        callRequestHandler(Action.update_state);
    }

    public void onUnlock(View view) {
        unlockButton.startAnimation(pressed);
        callRequestHandler(Action.open_door);
    }

    public void onLock(View view) {
        lockButton.startAnimation(pressed);
        callRequestHandler(Action.close_door);
    }

    private void changeUI(StateCode state) {
        Bitmap image = null;

        switch (state) {
            case OPEN:
                image = state_open_default_image;
                lockButton.setEnabled(true);
                unlockButton.setEnabled(true);
                enableRefreshItem = true;
                break;

            case CLOSED:
                image = state_closed_default_image;
                lockButton.setEnabled(true);
                unlockButton.setEnabled(true);
                enableRefreshItem = true;
                break;

            case DISABLED:
                image = state_wifi_default_image;
                lockButton.setEnabled(false);
                unlockButton.setEnabled(false);
                enableRefreshItem = false;
                break;

            case UNKNOWN:
                image = state_unknown_default_image;
                // Enabled, in case the API does not support state queries
                lockButton.setEnabled(true);
                unlockButton.setEnabled(true);
                enableRefreshItem = true;
                break;
        }

        // overwrite with custom image
        Setup setup = getSelectedSetup();
        if (setup != null) {
            Bitmap custom = setup.getStateImage(state);
            if (custom != null) {
                image = custom;
            }
        }

        stateImage.setImageBitmap(image);

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
            new HttpsRequestHandler(this).execute(action, httpsSetup);
        } else if (setup instanceof SshDoorSetup) {
            SshDoorSetup sshSetup = (SshDoorSetup) setup;
            new SshRequestHandler(this).execute(action, sshSetup);
        } else if (setup instanceof BluetoothDoorSetup) {
            BluetoothDoorSetup bluetoothSetup = (BluetoothDoorSetup) setup;
            new BluetoothRequestHandler(this).execute(action, bluetoothSetup);
        } else if (setup instanceof MqttDoorSetup) {
            MqttDoorSetup mqttSetup = (MqttDoorSetup) setup;
            new MqttRequestHandler(this).execute(action, mqttSetup);
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

    // Show/Hide menu items
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem refreshMenuItem = menu.findItem(R.id.action_reload);
        MenuItem editMenuItem = menu.findItem(R.id.action_edit);
        MenuItem showQrMenuItem = menu.findItem(R.id.action_show_qr);
        MenuItem cloneMenuItem = menu.findItem(R.id.action_clone);

        if (hasSetupSelected) {
            editMenuItem.setEnabled(true);
            editMenuItem.getIcon().setAlpha(255);

            showQrMenuItem.setEnabled(true);
            showQrMenuItem.getIcon().setAlpha(255);

            cloneMenuItem.setEnabled(true);
            cloneMenuItem.getIcon().setAlpha(255);
        } else {
            editMenuItem.setEnabled(false);
            editMenuItem.getIcon().setAlpha(130);

            showQrMenuItem.setEnabled(false);
            showQrMenuItem.getIcon().setAlpha(130);

            cloneMenuItem.setEnabled(false);
            cloneMenuItem.getIcon().setAlpha(130);
        }

        if (enableRefreshItem) {
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
            int setup_id = getSelectedSetupId();
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

        if (id == R.id.action_scan_qr) {
            int setup_id = getSelectedSetupId();
            Intent i = new Intent(this, QRScanActivity.class);
            i.putExtra("setup_id", setup_id);
            startActivity(i);
            return true;
        }

        if (id == R.id.action_show_qr) {
            int setup_id = getSelectedSetupId();
            Intent i = new Intent(this, QRShowActivity.class);
            i.putExtra("setup_id", setup_id);
            startActivity(i);
            return true;
        }

        if (id == R.id.action_backup) {
            Intent i = new Intent(this, BackupActivity.class);
            startActivity(i);
            return true;
        }

        if (id == R.id.action_clone) {
            try {
                Setup setup = getSelectedSetup();
                JSONObject obj = Settings.toJsonObject(setup);
                int new_id = Settings.getNewID();
                String new_name = setup.getName().split("~")[0] + "~" + new_id;
                obj.put("id", new_id);
                obj.put("name", new_name);
                setup = Settings.fromJsonObject(obj);
                Settings.addSetup(setup);
                updateSpinner();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }

        if (id == R.id.action_reload) {
            callRequestHandler(Action.update_state);
        }

        return super.onOptionsItemSelected(menu_item);
    }
}
