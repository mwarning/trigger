package com.example.trigger;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        } else {
            startScan();
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

    @Override
    public void barcodeResult(BarcodeResult result) {
        String json = result.getText();

        try {
            JSONObject obj = new JSONObject(json);
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
