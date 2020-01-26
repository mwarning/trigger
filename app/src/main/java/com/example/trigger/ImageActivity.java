package com.example.trigger;

import com.example.trigger.Utils;
import com.github.isabsent.filepicker.SimpleFilePickerDialog;

import static com.github.isabsent.filepicker.SimpleFilePickerDialog.CompositeMode.FILE_ONLY_SINGLE_CHOICE;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.trigger.R;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import java.io.ByteArrayOutputStream;
import java.io.File;


public class ImageActivity extends AppCompatActivity implements
        SimpleFilePickerDialog.InteractionListenerString {
    private static final String SELECT_FILE_REQUEST = "SELECT_FILE_REQUEST";
    private static final int REQUEST_PERMISSION = 0x01;
    private ImagePreference preference; // hack
    private AlertDialog.Builder builder;
    private Button setButton;
    private Button selectButton;
    private Button deleteButton;
    private ImageView imageView;
    private Bitmap image;

    private void showErrorMessage(String title, String message) {
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        this.preference = ImagePreference.self; // hack, TODO: pass serialized bitmap in bundle
        this.image = this.preference.getImage();

        imageView = findViewById(R.id.selectedImage);

        builder = new AlertDialog.Builder(this);
        setButton = findViewById(R.id.SetButton);
        selectButton = findViewById(R.id.SelectButton);
        deleteButton = findViewById(R.id.DeleteButton);
        final ImageActivity self = this;

        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.hasReadPermission(self)) {
                    final String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                    showListItemDialog("Pick Image", rootPath, FILE_ONLY_SINGLE_CHOICE, SELECT_FILE_REQUEST);
                } else {
                    Utils.requestReadPermission(self, REQUEST_PERMISSION);
                    Utils.requestWritePermission(self, REQUEST_PERMISSION);
                }
            }
        });

        setButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("ImageActivity", "ok pressed, is image null? " + (self.image == null));
                // persist your value here
                self.preference.setImage(self.image);
                self.finish();
            }
        });

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                builder.setTitle("Confirm");
                builder.setMessage("Really remove image?");
                builder.setCancelable(false); // not necessary

                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ImageActivity.this.image = null;
                        self.updateImageView();
                        dialog.cancel();
                    }
                });

                builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

                // create dialog box
                AlertDialog alert = builder.create();
                alert.show();
            }
        });

        updateImageView();
    }

    private void updateImageView() {
        imageView.setImageBitmap(image);
        //imageView.setImageDrawable(image);

        if (image == null) {
            deleteButton.setEnabled(false);
        } else {
            deleteButton.setEnabled(true);
        }
    }

    // path picker
    @Override
    public void showListItemDialog(String title, String folderPath, SimpleFilePickerDialog.CompositeMode mode, String dialogTag){
        SimpleFilePickerDialog.build(folderPath, mode)
                .title(title)
                .show(this, dialogTag);
    }

    @Override
    public boolean onResult(@NonNull String dialogTag, int which, @NonNull Bundle extras) {
        switch (dialogTag) {
            case SELECT_FILE_REQUEST:
                if (extras.containsKey(SimpleFilePickerDialog.SELECTED_SINGLE_PATH)) {
                    String selectedSinglePath = extras.getString(SimpleFilePickerDialog.SELECTED_SINGLE_PATH);
                    final int maxSize = 800;

                    try {
                        byte[] data = Utils.readExternalFile(selectedSinglePath);
                        Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);
                        final int inWidth = image.getWidth();
                        final int inHeight = image.getHeight();
                        int outWidth = 0;
                        int outHeight = 0;

                        if (inWidth > inHeight) {
                            outWidth = maxSize;
                            outHeight = (int) ((inHeight * maxSize) / (float) inWidth);
                        } else {
                            outHeight = maxSize;
                            outWidth = (int) ((inWidth * maxSize) / (float) inHeight);
                        }

                        image = Bitmap.createScaledBitmap(image, outWidth, outHeight, false);
                        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                        boolean success = image.compress(Bitmap.CompressFormat.PNG, 0, byteStream);
                        if (success) {
                            Log.d("ImageActivity", "data.length: " + data.length + " + compress.length: " + byteStream.toByteArray().length + ", base64: "
                             + Base64.encodeToString(byteStream.toByteArray(), 0).length());
                            this.image = image;
                        } else {
                            throw new Exception("Cannot compress image");
                        }
                        updateImageView();
                    } catch( Exception e) {
                        showErrorMessage("Error", e.toString());
                    }
                }
                break;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION:
                if (Utils.allGranted(grantResults)) {
                    // permissions granted
                    Toast.makeText(getApplicationContext(), "Permissions granted - please try again.", Toast.LENGTH_SHORT).show();
                } else {
                    showErrorMessage("Permissions Required", "Action cannot be performed.");
                }
                break;
        }
    }
}
