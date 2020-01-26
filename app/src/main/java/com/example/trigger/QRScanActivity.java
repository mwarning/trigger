package com.example.trigger;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
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


public class QRScanActivity extends AppCompatActivity implements BarcodeCallback {
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
            // assume raw link
            URI uri = new URI(data);
            String scheme = uri.getScheme();
            String domain = uri.getHost();
            String path = uri.getPath();
            String query = uri.getQuery();
            int port = uri.getPort();
            switch (scheme) {
                case "https":
                case "http":
                    String http_server = domain + ((port > 0) ? (":" + port) : "");
                    return new JSONObject(
                            "{\"type\": \"HttpsDoorSetup\", "
                            + "\"name\": \""+ http_server + "\", "
                            + "\"open_query\": \"" + data + "\"}"
                    );
                case "ssl":
                case "tcp:":
                    String mqtt_server = scheme + "://" + domain + ((port > 0) ? (":" + port) : "");
                    return new JSONObject(
                                "{\"type\": \"MqttDoorSetup\", "
                                + "\"name\": \"" + domain + "\", "
                                + "\"server\": \"" + mqtt_server + "\", "
                                + "\"command_topic\": \"" + path + "\", "
                                + "\"open_command\": \"" + query + "\"}"
                    );
                case "ssh":
                    return new JSONObject(
                            "{\"type\": \"SshDoorSetup\", "
                            + "\"name\": \"" + domain + "\", "
                            + "\"host\": \"" + domain + "\", "
                            + "\"port\": \"" + port + "\", "
                            + "\"open_command\": \"" + query + "\"}"
                    );
                default:
                    // continue
            }
        } catch (Exception e) {
            // continue
        }

        // assume json data
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
            e.printStackTrace();
            Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Toast.makeText(this, "Incompatible QR Code", Toast.LENGTH_LONG).show();
        }

        finish();
    }

    @Override
    public void possibleResultPoints(List<ResultPoint> resultPoints) {

    }
}
