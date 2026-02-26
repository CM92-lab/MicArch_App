package com.example.cryobank

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

class BarcodeMergingActivity : AppCompatActivity() {

    private var sampleExcelUri: Uri? = null
    private val pickedUris = mutableListOf<Uri>()
    private var consensusMap: Map<String, Map<String, String?>> = emptyMap()
    private var selectedPlateColumn: String? = null

    private val pickMultipleLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            pickedUris.clear()
            if (!uris.isNullOrEmpty()) {
                pickedUris.addAll(uris)
                Toast.makeText(this, "Selected ${uris.size} images", Toast.LENGTH_SHORT).show()
            }
        }

    private val excelPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                sampleExcelUri = uri
                showPlateSelectionDialog(uri)
            }
        }

    private val excelSaveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )) { uri ->
            if (uri != null) saveConsensusToExcel(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_merging)

        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV init failed!")
            Toast.makeText(this, "OpenCV failed to load", Toast.LENGTH_LONG).show()
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully")
        }

        findViewById<MaterialToolbar>(R.id.toolbar_barcode_merging)?.setNavigationOnClickListener { finish() }

        findViewById<Button>(R.id.btn_select_images).setOnClickListener {
            pickMultipleLauncher.launch(arrayOf("image/*"))
        }

        findViewById<Button>(R.id.btn_select_excel).setOnClickListener {
            excelPickerLauncher.launch(arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/*"
            ))
        }

        findViewById<Button>(R.id.btn_run_merge).setOnClickListener {
            if (pickedUris.isEmpty()) {
                Toast.makeText(this, "Select images first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedPlateColumn == null) {
                Toast.makeText(this, "Select Plate column first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launchWhenStarted {
                val dlg = showProgressDialog("Decoding images...")
                try {
                    val result = withContext(Dispatchers.Default) {
                        runConsensusWorkflow()
                    }
                    consensusMap = result
                    // show annotated consensus and save to gallery
                    showAnnotatedAndSave()
                    Toast.makeText(this@BarcodeMergingActivity, "Consensus completed", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@BarcodeMergingActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    dlg.dismiss()
                }
            }
        }

        findViewById<Button>(R.id.btn_export_excel).setOnClickListener {
            if (consensusMap.isEmpty()) {
                Toast.makeText(this, "Run merge first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (sampleExcelUri == null) {
                Toast.makeText(this, "Select original Excel first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            excelSaveLauncher.launch("rack_output.xlsx")
        }
    }

    private fun showPlateSelectionDialog(uri: Uri) {
        val plates = ExcelUtils.readPlateColumns(this, uri)
        if (plates.isEmpty()) {
            Toast.makeText(this, "No plate columns found in Excel", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Select Plate Column")
            .setItems(plates.toTypedArray()) { _, which ->
                selectedPlateColumn = plates[which]
                Toast.makeText(this, "Selected $selectedPlateColumn", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // returns Map<Well, Map<PlateName, Barcode?>>
    private fun runConsensusWorkflow(): Map<String, Map<String, String?>> {
        val perScans = mutableListOf<Map<String, String?>>()
        for ((i, uri) in pickedUris.withIndex()) {
            val bmp = ImageUtils.uriToBitmap(this, uri) ?: continue
            val rotated = ImageUtils.rotate90Clockwise(bmp)

            // Save deskewed debug crop (optional)
            val deskewed = ImageUtils.deskewAndCropRackOpenCV(rotated, 0.02)

            if (deskewed != null) {
                // decode per-well (decodeRackWellByWell will deskew again internally but that's fine)
                val scanMap = ImageUtils.decodeRackWellByWell(deskewed, this)
                perScans.add(scanMap)
                // save per-scan annotated image to gallery
                val annotated = ImageUtils.annotateRackVisualOnly(deskewed, scanMap, null)
                ImageUtils.saveBitmapToGallery(
                    this,
                    annotated,
                    "scan_${i + 1}_${System.currentTimeMillis()}.jpg"
                )
            } else {
                Log.e("ConsensusWorkflow", "Deskewing failed for image $uri")
            }
        }

        if (perScans.isEmpty()) return emptyMap()
        val consensus = ImageUtils.majorityConsensus(perScans)
        // build final structure: each well -> map {selectedPlateColumn -> barcode}
        val out = mutableMapOf<String, MutableMap<String, String?>>()
        for ((w, code) in consensus) out[w] = mutableMapOf(selectedPlateColumn!! to code)
        return out
    }

    private fun showAnnotatedAndSave() {
        val firstUri = pickedUris.firstOrNull() ?: return
        val bmp = ImageUtils.uriToBitmap(this, firstUri) ?: return
        val rotated = ImageUtils.rotate90Clockwise(bmp)

        val flat = consensusMap.mapValues { it.value[selectedPlateColumn!!] }
        val annotated = ImageUtils.annotateRackVisualOnly(rotated, flat)
        val iv = ImageView(this)
        iv.setImageBitmap(annotated)
        AlertDialog.Builder(this)
            .setTitle("Consensus")
            .setView(iv)
            .setPositiveButton("OK", null)
            .show()

        ImageUtils.saveBitmapToGallery(this, annotated, "rack_consensus_${System.currentTimeMillis()}.jpg")
    }

    private fun saveConsensusToExcel(outputUri: Uri) {
        if (consensusMap.isEmpty() || sampleExcelUri == null || selectedPlateColumn == null) return
        ExcelUtils.writeBarcodesToExcel(this, sampleExcelUri!!, outputUri, consensusMap, selectedPlateColumn!!)
        Toast.makeText(this, "Excel saved", Toast.LENGTH_LONG).show()
    }

    private fun showProgressDialog(message: String) : AlertDialog {
        val builder = AlertDialog.Builder(this)
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_progress_indeterminate, null)
        builder.setView(v)
        val dlg = builder.create()
        dlg.setCancelable(false)
        dlg.show()
        return dlg
    }
}
