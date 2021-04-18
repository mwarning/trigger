package app.trigger;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;


public class ImageActivity extends AppCompatActivity {
    private static final int READ_IMAGE_REQUEST = 0x01;
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
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, READ_IMAGE_REQUEST);
        });

        setButton.setOnClickListener((View v) -> {
            // persist your value here
            ImageActivity.this.preference.setImage(ImageActivity.this.image);
            ImageActivity.this.finish();
        });

        deleteButton.setOnClickListener((View v) -> {
            builder.setTitle("Confirm");
            builder.setMessage("Really remove image?");
            builder.setCancelable(false); // not necessary

            builder.setPositiveButton(R.string.yes, (DialogInterface dialog, int id) -> {
                ImageActivity.this.image = null;
                ImageActivity.this.updateImageView();
                dialog.cancel();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == READ_IMAGE_REQUEST && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                updateImage(data.getData());
            }
        }
    }

    void updateImage(Uri uri) {
        final int maxSize = 800;
        try {
            byte[] data = Utils.readFile(this, uri);
            Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);

            if (image == null) {
                showErrorMessage("Error", "Not a supported image format: " + uri.getLastPathSegment());
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
                //Log.d("ImageActivity", "image: " + inWidth + "/" + inHeight + ", compress.length: " + byteStream.toByteArray().length + ", base64: " + Base64.encodeToString(byteStream.toByteArray(), 0).length());
                this.image = image;
            } else {
                throw new Exception("Cannot compress image");
            }
            updateImageView();
        } catch (Exception e) {
            showErrorMessage("Error", e.toString());
        }
    }
}
