package com.example.trigger;

import android.bluetooth.BluetoothAdapter;
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
    private boolean hasSetupSelected = false;
    private ImageView stateImage;
    private ImageButton lockButton;
    private ImageButton ringButton;
    private ImageButton unlockButton;
    private Spinner spinner;
    private WifiTools wifi;
    private BluetoothTools bluetooth;

    private Animation pressed;

    private Bitmap state_open_default_image;
    private Bitmap state_closed_default_image;
    private Bitmap state_disabled_default_image;
    private Bitmap state_unknown_default_image;

    RequestHandler handler;

    public enum Action {
        open_door,
        close_door,
        ring_door, // ring the door bell
        fetch_state // fetch the door state
    }

    // helper class for spinner
    private static class SpinnerItem {
        public final int id;
        public final String name;
        public final String ssids;

        public SpinnerItem(int id, String name, String ssids) {
            this.id = id;
            this.name = name;
            this.ssids = ssids;
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

    private int getPreferredSpinnerIndex(ArrayList<SpinnerItem> items, boolean match_ssid) {
        int i;

        // select by ssid
        String ssid = wifi.getCurrentSSID();
        if (ssid.length() > 0 && match_ssid) {
            i = 0;
            for (SpinnerItem item : items) {
                if (splitCommaSeparated(item.ssids).contains(ssid)) {
                    return i;
                }
                i += 1;
            }
        }

        // keep previous selection
        Setup current = getSelectedSetup();
        if (current != null) {
            i = 0;
            for (SpinnerItem item : items) {
                if (current.getId() == item.id) {
                    return i;
                }
                i += 1;
            }
        }

        // select first item
        if (items.size() > 0) {
            return 0;
        } else {
            return INVALID_POSITION;
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

    private void updateSpinner(boolean match_ssid) {
        ArrayList<Setup> setups = Settings.getSetups();
        ArrayList<SpinnerItem> items = new ArrayList();

        for (Setup setup : setups) {
            items.add(new SpinnerItem(setup.getId(), setup.getName(), setup.getSSIDs()));
        }

        // sort items by name
        Collections.sort(items, new Comparator<SpinnerItem>() {
            @Override
            public int compare(SpinnerItem s1, SpinnerItem s2) {
                return s1.name.compareTo(s2.name);
            }
        });

        int selection = getPreferredSpinnerIndex(items, match_ssid);

        ArrayAdapter<SpinnerItem> adapter = new ArrayAdapter<SpinnerItem>(this, R.layout.main_spinner, items);
        spinner.setAdapter(adapter);
        spinner.setSelection(selection);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                updateButtons();
                callRequestHandler(Action.fetch_state);
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

        updateButtons();
    }

    // hide lock/unlock/ring buttons if there is no action behind them
    private void updateButtons() {
        Setup setup = getSelectedSetup();

        if (setup == null || setup.canClose()) {
            lockButton.setVisibility(View.VISIBLE);
        } else {
            lockButton.setVisibility(View.GONE);
        }

        if (setup == null || setup.canOpen()) {
            unlockButton.setVisibility(View.VISIBLE);
        } else {
            unlockButton.setVisibility(View.GONE);
        }

        if (setup == null || setup.canRing()) {
            ringButton.setVisibility(View.VISIBLE);
        } else {
            ringButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = this.getApplicationContext();
        this.wifi = new WifiTools(context);
        this.bluetooth = new BluetoothTools(context);
        Settings.init(context);

        Resources res = getResources();
        state_open_default_image = BitmapFactory.decodeResource(res, R.drawable.state_open);
        state_closed_default_image = BitmapFactory.decodeResource(res, R.drawable.state_closed);
        state_disabled_default_image = BitmapFactory.decodeResource(res, R.drawable.state_disabled);
        state_unknown_default_image = BitmapFactory.decodeResource(res, R.drawable.state_unknown);

        spinner = findViewById(R.id.selection_spinner);
        stateImage = findViewById(R.id.stateImage);
        lockButton = findViewById(R.id.Lock);
        ringButton = findViewById(R.id.Ring);
        unlockButton = findViewById(R.id.Unlock);
        pressed = AnimationUtils.loadAnimation(this, R.anim.pressed);

        updateSpinner(true);
    }

    @Override
    protected void onResume() {
        updateSpinner(false);
        invalidateOptionsMenu();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        registerReceiver(broadcastReceiver, intentFilter);

        super.onResume();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    // listen for connectivity changes
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Setup current = getSelectedSetup();

            if (current == null) {
                changeUI(StateCode.DISABLED);
            } else if (current instanceof BluetoothDoorSetup) {
                // assume the current setup uses Bluetooth based!
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED) && state == BluetoothAdapter.STATE_ON) {
                    Log.d("MainActivity", "bluetooth turned on");
                    updateSpinner(true); // auto select possible entry
                    callRequestHandler(Action.fetch_state);
                } else {
                    Log.d("MainActivity", "bluetooth state unknown => disabled");
                    changeUI(StateCode.DISABLED);
                }
            } else {
                // assume the current setup uses WiFi based!
                Log.d("MainActivity", "wifi turned on");
                if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) && wifi.isConnected()) {
                    updateSpinner(true); // auto select possible entry
                    callRequestHandler(Action.fetch_state);
                } else {
                    Log.d("MainActivity", "wifi state unknown => disabled");
                    changeUI(StateCode.DISABLED);
                }
            }
        }
    };

    public void onUpdateState(View view) {
        callRequestHandler(Action.fetch_state);
    }

    public void doUnlock(View view) {
        unlockButton.startAnimation(pressed);
        callRequestHandler(Action.open_door);
    }

    public void doLock(View view) {
        lockButton.startAnimation(pressed);
        callRequestHandler(Action.close_door);
    }

    public void doRing(View view) {
        ringButton.startAnimation(pressed);
        callRequestHandler(Action.ring_door);
    }

    private void changeUI(StateCode state) {
        Bitmap image = null;

        switch (state) {
            case OPEN:
                image = state_open_default_image;
                lockButton.setEnabled(true);
                unlockButton.setEnabled(true);
                break;

            case CLOSED:
                image = state_closed_default_image;
                lockButton.setEnabled(true);
                unlockButton.setEnabled(true);
                break;

            case DISABLED:
                image = state_disabled_default_image;
                lockButton.setEnabled(false);
                unlockButton.setEnabled(false);
                break;

            case UNKNOWN:
                image = state_unknown_default_image;
                // Enabled, in case the API does not support state queries
                lockButton.setEnabled(true);
                unlockButton.setEnabled(true);
                break;
        }

        // use custom image
        Setup setup = getSelectedSetup();
        if (setup != null) {
            Bitmap custom = setup.getStateImage(state);
            if (custom != null) {
                image = custom;
            }
        }

        // set background image
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
            handler = new HttpsRequestHandler(this);
            handler.execute(action, setup);
        } else if (setup instanceof SshDoorSetup) {
            handler = new SshRequestHandler(this);
            handler.execute(action, setup);
        } else if (setup instanceof BluetoothDoorSetup) {
            handler = new BluetoothRequestHandler(this);
            handler.execute(action, setup);
        } else if (setup instanceof MqttDoorSetup) {
            handler = new MqttRequestHandler(this);
            handler.execute(action, setup);
        } else {
            // hm, invalid setup
            changeUI(StateCode.DISABLED);
        }

        // make sure the handler exists after two seconds (if supported)
        new Thread(() -> {
            RequestHandler h = handler;
            try {
                Thread.sleep(2000);
                if (h != null) {
                    h.stop();
                }
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }).start();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (wifi.isConnected()) {
            callRequestHandler(Action.fetch_state);
        }
    }

    // Show/Hide menu items
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

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
                updateSpinner(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }

        return super.onOptionsItemSelected(menu_item);
    }
}
