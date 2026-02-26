// LiveScanActivity.kt
package com.example.cryobank

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiveScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayImage: ImageView
    private lateinit var captureButton: Button

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture

    private val liveWellResults = mutableMapOf<String, MutableList<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_scan)

        previewView = findViewById(R.id.previewView)
        overlayImage = findViewById(R.id.overlayImage)
        captureButton = findViewById(R.id.btnCapture)

        findViewById<MaterialToolbar>(R.id.toolbar_live)?.setNavigationOnClickListener { finish() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                1001
            )
        } else {
            startCamera()
        }

        captureButton.setOnClickListener {
            takePhoto()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val filename = "live_capture_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Cryobank")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri ?: return

                    lifecycleScope.launch(Dispatchers.IO) {
                        val bitmap = loadBitmapFromUri(uri)
                        processCapturedBitmap(bitmap)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@LiveScanActivity,
                        "Capture failed: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        val stream = contentResolver.openInputStream(uri) ?: throw Exception("Cannot open stream")
        return android.graphics.BitmapFactory.decodeStream(stream)
    }

    private fun processCapturedBitmap(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // Original speichern (ungespiegelt) für andere Tools
                ImageUtils.saveBitmapToGallery(
                    this@LiveScanActivity,
                    bitmap,
                    "live_capture_${System.currentTimeMillis()}.jpg"
                )

                // Deskew & Decode
                val deskewed = ImageUtils.deskewAndCropRackOpenCV(bitmap)
                val decoded = ImageUtils.decodeRackWellByWell(deskewed, this@LiveScanActivity)

                // Overlay annotieren (blau/lila K Stil)
                val annotated = ImageUtils.annotateRackVisualOnly(deskewed, decoded, highlightWell = null)

                runOnUiThread {
                    overlayImage.setImageBitmap(annotated)
                }

                // Optional: live consensus speichern
                for ((well, code) in decoded) {
                    if (code != null) {
                        liveWellResults.getOrPut(well) { mutableListOf() }.add(code)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
