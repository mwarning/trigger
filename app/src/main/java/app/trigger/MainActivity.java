package app.trigger;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import static android.view.accessibility.AccessibilityEvent.INVALID_POSITION;

import java.net.NetworkInterface;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;

import app.trigger.DoorState.StateCode;
import app.trigger.https.HttpsRequestHandler;
import app.trigger.nuki.NukiRequestHandler;
import app.trigger.ssh.KeyPairBean;
import app.trigger.ssh.PubkeyUtils;
import app.trigger.ssh.SshRequestHandler;
import app.trigger.bluetooth.BluetoothRequestHandler;
import app.trigger.mqtt.MqttRequestHandler;

import org.conscrypt.OpenSSLProvider;
import org.json.JSONObject;


public class MainActivity extends AppCompatActivity implements OnTaskCompleted {
    private static final String TAG = "MainActivity";
    private boolean hasSetupSelected = false;
    private ImageView stateImage;
    private ImageButton lockButton;
    private ImageButton ringButton;
    private ImageButton unlockButton;
    private Spinner spinner;

    private Animation pressed;

    private Bitmap state_open_default_image;
    private Bitmap state_closed_default_image;
    private Bitmap state_disabled_default_image;
    private Bitmap state_unknown_default_image;

    private int ignore_wifi_check_for_setup_id = -1;
    private String ssh_passphrase = "";

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

    private int getPreferredSpinnerIndex(ArrayList<SpinnerItem> items, boolean match_ssid) {
        int i;

        // select by ssid
        if (match_ssid && WifiTools.isConnected()) {
            String ssid = WifiTools.getCurrentSSID();
            i = 0;
            for (SpinnerItem item : items) {
                if (WifiTools.matchSSID(item.ssids, ssid)) {
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
        ArrayList<SpinnerItem> items = new ArrayList<>();

        for (Setup setup : setups) {
            items.add(new SpinnerItem(setup.getId(), setup.getName(), setup.getWiFiSSIDs()));
        }

        // sort items by name
        Collections.sort(items, (SpinnerItem s1, SpinnerItem s2) -> s1.name.compareTo(s2.name));

        int selection = getPreferredSpinnerIndex(items, match_ssid);

        ArrayAdapter<SpinnerItem> adapter = new ArrayAdapter<SpinnerItem>(this, R.layout.main_spinner, items);
        spinner.setAdapter(adapter);
        spinner.setSelection(selection);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            int check = 0; // prevent triggering on creation

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (check++ > 0) {
                    updateButtons();
                    callRequestHandler(Action.fetch_state);
                }
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
        WifiTools.init(context);
        BluetoothTools.init(context);
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

        Log.d("MainActivity", "Security.insertProviderAt(new OpenSSLProvider()");
        Security.insertProviderAt(new OpenSSLProvider(), 1);
    }

    @Override
    protected void onResume() {
        updateSpinner(false);
        invalidateOptionsMenu();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        registerReceiver(broadcastReceiver, intentFilter);

        super.onResume();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    private void showMessage(String message) {
        Context context = getApplicationContext();
        // show centered text
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
        //toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    // listen for connectivity changes
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isInitialStickyBroadcast()) {
                // do nothing if the receiver was just initialized
                return;
            }

            String action = intent.getAction();

            if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (networkInfo == null) {
                    return;
                }
                NetworkInfo.DetailedState state = networkInfo.getDetailedState();
                if (state == NetworkInfo.DetailedState.CONNECTED || state == NetworkInfo.DetailedState.DISCONNECTED) {
                    // WifiTools.isConnected() will take a while until it returns true
                    final Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> {
                            callRequestHandler(Action.fetch_state);
                    }, 1000);
                }
            }

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_OFF) {
                    callRequestHandler(Action.fetch_state);
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

    // can be called multiple times by the same task
    @Override
    public void onTaskResult(int setup_id, DoorReply.ReplyCode code, String message) {
        this.runOnUiThread(() -> {
            Setup setup = getSelectedSetup();
            if (setup == null || setup.getId() != setup_id) {
                // probably some late result that does not matter anymore
                return;
            }

            DoorState state = setup.parseReply(new DoorReply(code, message));

            // change state image
            changeUI(state.code);

            // display message
            if (state.message.length() > 0) {
                showMessage(state.message);
            }
        });
    }

    private boolean checkConnectedWifi(Setup setup, Action action) {
        if (setup == null || setup.getId() == ignore_wifi_check_for_setup_id) {
            return true;
        } else {
            ignore_wifi_check_for_setup_id = -1;
        }

        boolean wifi_connected = WifiTools.isConnected();

        if (!wifi_connected && setup.getWiFiRequired()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("WiFi Disabled");
            builder.setMessage("WiFi disabled - ignore?");

            builder.setPositiveButton("Yes", (DialogInterface dialog, int id) -> {
                ignore_wifi_check_for_setup_id = setup.getId();
                // trigger aggain
                callRequestHandler(action);
                dialog.cancel();
            });

            builder.setNegativeButton("No", (DialogInterface dialog, int id) -> {
                dialog.cancel();
            });

            builder.show();
            return false;
        }

        if (wifi_connected) {
            String ssids = setup.getWiFiSSIDs();
            String current_ssid = WifiTools.getCurrentSSID();

            if (ssids.length() > 0 && !WifiTools.matchSSID(ssids, current_ssid)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Wrong WiFi");
                builder.setMessage("Connected to wrong network ('" + current_ssid + "') - ignore?");

                builder.setPositiveButton("Yes", (DialogInterface dialog, int id) -> {
                    ignore_wifi_check_for_setup_id = setup.getId();
                    // trigger aggain
                    callRequestHandler(action);
                    dialog.cancel();
                });

                builder.setNegativeButton("No", (DialogInterface dialog, int id) -> {
                    dialog.cancel();
                });

                builder.show();
                return false;
            }
        }

        return true;
    }

    private boolean checkSshPassword(Setup setup, Action action) {
        if (setup instanceof SshDoorSetup) {
            SshDoorSetup s = (SshDoorSetup) setup;
            if (s.keypair == null || !s.keypair.encrypted || ssh_passphrase.length() != 0) {
                return true;
            }

            Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.dialog_ssh_passphrase);

            EditText passphraseEditText = dialog.findViewById(R.id.PassphraseEditText);
            Button abortButton = dialog.findViewById(R.id.AbortButton);
            Button okButton = dialog.findViewById(R.id.OkButton);

            okButton.setOnClickListener((View v) -> {
                ssh_passphrase = passphraseEditText.getText().toString();
                callRequestHandler(action);
                dialog.cancel();
            });

            abortButton.setOnClickListener((View v) -> {
                dialog.cancel();
            });

            dialog.show();
            return false;
        } else {
            ssh_passphrase = "";
        }

        return true;
    }

    private void callRequestHandler(Action action) {
        Setup setup = getSelectedSetup();

        if (!checkConnectedWifi(setup, action)) {
            return;
        }

        if (!checkSshPassword(setup, action)) {
            return;
        }

        if (setup instanceof HttpsDoorSetup) {
            HttpsRequestHandler handler = new HttpsRequestHandler(this, (HttpsDoorSetup) setup, action);
            handler.start();
        } else if (setup instanceof SshDoorSetup) {
            SshRequestHandler handler = new SshRequestHandler(this, (SshDoorSetup) setup, action, this.ssh_passphrase);
            handler.start();
        } else if (setup instanceof BluetoothDoorSetup) {
            BluetoothRequestHandler handler = new BluetoothRequestHandler(this, (BluetoothDoorSetup) setup, action);
            handler.start();
        } else if (setup instanceof NukiDoorSetup) {
            NukiRequestHandler handler = new NukiRequestHandler(this, (NukiDoorSetup) setup, action);
            handler.start();
        } else if (setup instanceof MqttDoorSetup) {
            MqttRequestHandler handler = new MqttRequestHandler(this, (MqttDoorSetup) setup, action);
            handler.start();
        } else {
            // hm, invalid setup
            changeUI(StateCode.DISABLED);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        callRequestHandler(Action.fetch_state);
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
            ssids.addAll(splitCommaSeparated(setup.getWiFiSSIDs()));
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
