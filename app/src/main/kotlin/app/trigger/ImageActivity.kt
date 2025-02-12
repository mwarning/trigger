package app.trigger

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import app.trigger.DoorStatus.StateCode
import java.io.ByteArrayOutputStream

class ImageActivity : AppCompatActivity() {
    private lateinit var door: Door
    private lateinit var stateCode: StateCode

    private lateinit var builder: AlertDialog.Builder
    private lateinit var setButton: Button
    private lateinit var selectButton: Button
    private lateinit var deleteButton: Button
    private lateinit var imageView: ImageView
    private var currentImage: Bitmap? = null

    private fun showErrorMessage(title: String, message: String) {
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.show()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // currentDoor might still not be stored if it is a new one
        val currentDoor = SetupActivity.currentDoor
        val codeString = intent.getStringExtra("state_code") ?: ""
        if (currentDoor == null || codeString.isEmpty()) {
            // not expected to happen
            finish()
            return
        }

        door = currentDoor
        stateCode = StateCode.valueOf(codeString)
        currentImage = door.getStateImage(stateCode)

        imageView = findViewById(R.id.selectedImage)
        builder = AlertDialog.Builder(this)
        setButton = findViewById(R.id.SetButton)
        selectButton = findViewById(R.id.SelectButton)
        deleteButton = findViewById(R.id.DeleteButton)

        selectButton.setOnClickListener { _: View? ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, READ_IMAGE_REQUEST)
        }

        setButton.setOnClickListener { _: View? ->
            // persist your value here
            door.setStateImage(stateCode, currentImage)
            finish()
        }

        deleteButton.setOnClickListener { _: View? ->
            builder.setTitle("Confirm")
            builder.setMessage("Really remove image?")
            builder.setCancelable(false) // not necessary
            builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, _: Int ->
                currentImage = null
                door.setStateImage(stateCode, null)
                updateImageView()
                dialog.cancel()
            }
            builder.setNegativeButton(R.string.no) { dialog: DialogInterface, _: Int -> dialog.cancel() }

            // create dialog box
            val alert = builder.create()
            alert.show()
        }

        updateImageView()
    }

    private fun updateImageView() {
        if (currentImage == null) {
            // show default image
            val defaultImageResource = when (stateCode) {
                StateCode.OPEN -> R.drawable.state_open
                StateCode.CLOSED -> R.drawable.state_closed
                StateCode.DISABLED -> R.drawable.state_disabled
                StateCode.UNKNOWN -> R.drawable.state_unknown
            }
            imageView.setImageBitmap(BitmapFactory.decodeResource(resources, defaultImageResource))
            deleteButton.isEnabled = false
        } else {
            imageView.setImageBitmap(currentImage)
            deleteButton.isEnabled = true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == READ_IMAGE_REQUEST && resultCode == RESULT_OK) {
            if (data != null) {
                val d = data.data
                if (d != null) {
                    updateImage(d)
                }
            }
        }
    }

    private fun updateImage(uri: Uri) {
        val maxSize = 800
        try {
            val data = Utils.readFile(this, uri)
            var image = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (image == null) {
                showErrorMessage("Error", "Not a supported image format: " + uri.lastPathSegment)
                return
            }
            val inWidth = image.width
            val inHeight = image.height
            var outWidth = 0
            var outHeight = 0
            if (inWidth > inHeight) {
                outWidth = maxSize
                outHeight = (inHeight * maxSize / inWidth.toFloat()).toInt()
            } else {
                outHeight = maxSize
                outWidth = (inWidth * maxSize / inHeight.toFloat()).toInt()
            }
            image = Bitmap.createScaledBitmap(image, outWidth, outHeight, false)
            val byteStream = ByteArrayOutputStream()
            val success = image.compress(Bitmap.CompressFormat.PNG, 0, byteStream)
            if (success) {
                //Log.d("ImageActivity", "image: " + inWidth + "/" + inHeight + ", compress.length: " + byteStream.toByteArray().length + ", base64: " + Base64.encodeToString(byteStream.toByteArray(), 0).length());
                currentImage = image
            } else {
                throw Exception("Cannot compress image")
            }
            updateImageView()
        } catch (e: Exception) {
            showErrorMessage("Error", e.toString())
        }
    }

    companion object {
        private const val READ_IMAGE_REQUEST = 0x01
        private const val TAG = "ImageActivity"
    }
}
