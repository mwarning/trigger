package app.trigger;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.codekidlabs.storagechooser.StorageChooser;

import java.io.ByteArrayOutputStream;


public class ImageActivity extends AppCompatActivity implements
        StorageChooser.OnSelectListener, StorageChooser.OnCancelListener {
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

        selectButton.setOnClickListener((View v) -> {
            if (Utils.hasReadPermission(ImageActivity.this)) {
                StorageChooser chooser = new StorageChooser.Builder()
                        .withActivity(this)
                        .withFragmentManager(getFragmentManager())
                        .allowCustomPath(true)
                        .setType(StorageChooser.FILE_PICKER)
                        .build();
                chooser.show();

                // get path that the user has chosen
                chooser.setOnSelectListener(ImageActivity.this);
                chooser.setOnCancelListener(ImageActivity.this);
            } else {
                Utils.requestReadPermission(ImageActivity.this, REQUEST_PERMISSION);
                Utils.requestWritePermission(ImageActivity.this, REQUEST_PERMISSION);
            }
        });

        setButton.setOnClickListener((View v) -> {
            Log.d("ImageActivity", "ok pressed, is image null? " + (ImageActivity.this.image == null));
            // persist your value here
            ImageActivity.this.preference.setImage(ImageActivity.this.image);
            ImageActivity.this.finish();
        });

        deleteButton.setOnClickListener((View v) -> {
            builder.setTitle("Confirm");
            builder.setMessage("Really remove image?");
            builder.setCancelable(false); // not necessary

            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    ImageActivity.this.image = null;
                    ImageActivity.this.updateImageView();
                    dialog.cancel();
                }
            });

            builder.setNegativeButton(R.string.no, (DialogInterface dialog, int id) -> {
                dialog.cancel();
            });

            // create dialog box
            AlertDialog alert = builder.create();
            alert.show();
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

    // for StorageChooser
    @Override
    public void onSelect(String path) {
        final int maxSize = 800;

        try {
            byte[] data = Utils.readExternalFile(path);
            Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (image == null) {
                showErrorMessage("Error", "Not a supported image format: " + path);
                return;
            }

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

    // for StorageChooser
    @Override
    public void onCancel() {
        // nothing to do
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
