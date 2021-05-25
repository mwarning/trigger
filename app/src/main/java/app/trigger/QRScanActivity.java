package app.trigger;

import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import app.trigger.ssh.SshTools;

import app.trigger.ssh.KeyPairBean;


public class QRScanActivity extends AppCompatActivity implements BarcodeCallback {
    private static final String TAG = "QRScanActivity";
    private DecoratedBarcodeView barcodeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_qrscan);
        barcodeView = (DecoratedBarcodeView) findViewById(R.id.barcodeScannerView);

        if (Utils.hasCameraPermission(this)) {
            startScan();
        } else {
            Utils.requestCameraPermission(this, 1);
        }
    }

    private void startScan() {
        Collection<BarcodeFormat> formats = Collections.singletonList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        barcodeView.initializeFromIntent(getIntent());
        barcodeView.decodeContinuous(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        barcodeView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        barcodeView.pause();
    }

    public void pause(View view) {
        barcodeView.pause();
    }

    public void resume(View view) {
        barcodeView.resume();
    }

    public void triggerScan(View view) {
        barcodeView.decodeSingle(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
            startScan();
        } else {
            Toast.makeText(this, "Camera permissions required for QR code scan.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private JSONObject decodeSetup(String data) throws JSONException {
        try {
            KeyPairBean kp = SshTools.parsePrivateKeyPEM(data);
            if (kp != null) {
                if (kp.encrypted) {
                    throw new Exception("Encrypted keys are not supported.");
                }
                Log.d(TAG, "type: " + kp.type + ", pk len: " + kp.publicKey.length + ", sk.len: " + kp.privateKey.length);
                JSONObject obj = new JSONObject();
                obj.put("type", "SshDoorSetup");
                obj.put("name", "SSH Door");
                obj.put("keypair", SshTools.serializeKeyPair(kp));
                return obj;
            } else {
                // assume raw link
                URI uri = new URI(data.trim());
                String scheme = uri.getScheme();
                String domain = uri.getHost();
                String path = uri.getPath();
                String query = uri.getQuery();
                int port = uri.getPort();
                switch (scheme) {
                    case "https":
                    case "http": {
                        String http_server = domain + ((port > 0) ? (":" + port) : "");
                        JSONObject obj = new JSONObject();
                        obj.put("type", "HttpsDoorSetup");
                        obj.put("name", "http_server");
                        obj.put("open_query", data);
                        return obj;
                    }
                    case "ssl":
                    case "tcp:": {
                        String mqtt_server = scheme + "://" + domain + ((port > 0) ? (":" + port) : "");
                        JSONObject obj = new JSONObject();
                        obj.put("type", "MqttDoorSetup");
                        obj.put("name", domain);
                        obj.put("server", mqtt_server);
                        obj.put("command_topic", path);
                        obj.put("open_command", query);
                        return obj;
                    }
                    case "ssh": {
                        JSONObject obj = new JSONObject();
                        obj.put("type", "SshDoorSetup");
                        obj.put("name", domain);
                        obj.put("host", domain);
                        obj.put("port", port);
                        obj.put("open_command", query);
                        return obj;
                    }
                    default:
                        // continue
                }
            }
        } catch (Exception e) {
            // continue
        }

        // assume json data, throws exception otherwise
        return new JSONObject(data);
    }

    @Override
    public void barcodeResult(BarcodeResult result) {
        try {
            JSONObject obj = decodeSetup(result.getText());
            // give entry a new id
            obj.put("id", Settings.getNewID());

            Setup setup = Settings.fromJsonObject(obj);
            Settings.addSetup(setup);
            Toast.makeText(this, "Added " + setup.getName(), Toast.LENGTH_LONG).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show();
        } catch (IllegalAccessException e) {
            Toast.makeText(this, "Incompatible QR Code", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
        finish();
    }

    @Override
    public void possibleResultPoints(List<ResultPoint> resultPoints) {

    }
}
