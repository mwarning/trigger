package com.example.trigger;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;


public class QRShowActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrshow);

        int setup_id = getIntent().getIntExtra("setup_id", -1);
        Setup setup = Settings.getSetup(setup_id);

        if (setup != null) {
           setTitle(getTitle() + ": " + setup.getName());

            try {
                generateQR(setup);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Setup not found.", Toast.LENGTH_LONG).show();
        }
    }

    private Collection<String> collect(Iterator<String> iter) {
        ArrayList<String> set = new ArrayList<String>();
        while (iter.hasNext()) {
            set.add(iter.next());
        }
        return set;
    }

    private String encodeSetup(JSONObject obj) {
        return obj.toString();
    }

    private void generateQR(Setup setup) throws Exception {
        /*
        ArrayList<String> removed = new ArrayList<String>();

        // remove large (string) elements
        for (String key : collect(obj.keys())) {
            int len = obj.optString(key).length();
            if (len > 500) {
                obj.remove(key);
                removed.add(key);
            }
        }*/

        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        int data_length = 0;
        try {
            JSONObject obj = Settings.toJsonObject(setup);
            // do not export internal id
            obj.remove("id");
            String data = encodeSetup(obj);

            data_length = data.length();
            // data has to be a string
            BitMatrix bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            ((ImageView) findViewById(R.id.QRView)).setImageBitmap(bitmap);
/*
            if (removed.size() > 0) {
                Toast.makeText(this, "Fields too big for export: " + String.join(", ", removed), Toast.LENGTH_LONG).show();
            }
*/
        } catch (WriterException e) {
            Toast.makeText(this, e.getMessage() + " (" + data_length + " Bytes)", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
