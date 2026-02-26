package com.example.cryobank


import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.cryobank.ExcelUtilsSample.loadBarcodeToSampleMap
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

class SampleFinderActivity : AppCompatActivity() {

    private var excelUri: Uri? = null
    private val rackUris = mutableListOf<Uri>()
    private var consensusMap: Map<String, Map<String, String?>> = emptyMap()
    private var selectedPlateColumn: String? = null
    private var selectedSample: String? = null



    private val pickMultipleLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            rackUris.clear()
            if (!uris.isNullOrEmpty()) {
                rackUris.addAll(uris)
                Toast.makeText(this, "Selected ${uris.size} images", Toast.LENGTH_SHORT).show()
            }
        }

    private val pickExcelLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                excelUri = uri
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
        setContentView(R.layout.activity_sample_finder)

        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV init failed!")
            Toast.makeText(this, "OpenCV failed to load", Toast.LENGTH_LONG).show()
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully")
        }

        findViewById<MaterialToolbar>(R.id.toolbar_sample_finder)?.setNavigationOnClickListener { finish() }

        findViewById<Button>(R.id.btn_pick_rack_image).setOnClickListener {
            pickMultipleLauncher.launch(arrayOf("image/*"))
        }

        findViewById<Button>(R.id.btn_pick_excel_file).setOnClickListener {
            pickExcelLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "application/*"
                )
            )
        }

        findViewById<Button>(R.id.btn_run_search).setOnClickListener {
            if (rackUris.isEmpty()) {
                Toast.makeText(this, "Select Images first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (excelUri == null || selectedPlateColumn == null) {
                Toast.makeText(this, "Select Excel/Plate first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            pickSampleDialog { validSample ->
                selectedSample = validSample

                lifecycleScope.launchWhenStarted {

                    val dlg = showProgressDialog("Searching sample...")
                    try {
                        val wellToBarcode = withContext(Dispatchers.Default) {
                            runWorkflowSearchSample()
                        }
                        showSampleAndSave(wellToBarcode)
                        Toast.makeText(
                            this@SampleFinderActivity,
                            "Search completed",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@SampleFinderActivity,
                            "Error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        dlg.dismiss()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btn_run_whole_plate_annotation).setOnClickListener {
            if (rackUris.isEmpty()) {
                Toast.makeText(this, "Select Images first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedPlateColumn == null || excelUri == null) {
                Toast.makeText(this, "Select Excel/Plate first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            lifecycleScope.launchWhenStarted {
                val dlg = showProgressDialog("Annotating Rack...")
                try {
                    val result = withContext(Dispatchers.Default) {
                        runAnnotationWorkflow()
                    }
                    consensusMap = result
                    // show annotated consensus and save to gallery
                    showAnnotatedAndSave()
                    Toast.makeText(
                        this@SampleFinderActivity,
                        "Rack annotated",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@SampleFinderActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    dlg.dismiss()
                }
            }
        }

        findViewById<Button>(R.id.btn_export_excel).setOnClickListener {
            if (consensusMap.isEmpty()) {
                Toast.makeText(this, "Run any Activity first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (excelUri == null) {
                Toast.makeText(this, "Select Excel file first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            excelSaveLauncher.launch("rack_output.xlsx")
        }

    }

    private fun showPlateSelectionDialog(uri: Uri) {
        val plates = ExcelUtilsSample.readPlateColumns(this, uri)
        if (plates.isEmpty()) {
            Toast.makeText(this, "No plate column found", Toast.LENGTH_SHORT).show()
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

    private fun pickSampleDialog(onValidSample: (String) -> Unit ) {
        val input = EditText(this).apply {
            hint = "Enter sample name"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle("Search sample")
            .setView(input)
            .setPositiveButton("OK", null) // wir überschreiben gleich
            .setNegativeButton("Cancel", null)
            .show()
            .also { dialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

                    val sample = input.text.toString().trim()
                    if (sample.isEmpty()) {
                        input.error = "Required"
                        return@setOnClickListener
                    }

                    val exists = ExcelUtilsSample.sampleExistsInPlate(
                        ctx = this,
                        excelUri = excelUri!!,
                        plateColumn = selectedPlateColumn!!,
                        sampleName = sample
                    )

                    if (!exists) {
                        input.error = "Sample not found in $selectedPlateColumn"
                        return@setOnClickListener
                    }

                    dialog.dismiss()
                    onValidSample(sample)
                }
            }
    }

    private fun runWorkflowSearchSample(): Map<String, String?> {
        val perScans = mutableListOf<Map<String, String?>>()
        for ((i, uri) in rackUris.withIndex()) {
            val bmp = ImageUtilsSample.uriToBitmap(this, uri) ?: continue
            val rotated = ImageUtilsSample.rotate90Clockwise(bmp)
            val rackOnly = ImageUtilsSample.deskewAndCropRackOpenCV(rotated)

            // well by well auf dem ausgeschnittenen Rack scannen
            val scanMap = ImageUtilsSample.decodeRack(rackOnly)
            perScans.add(scanMap)
        }
            if (perScans.isEmpty()) return emptyMap()
            return ImageUtilsSample.majorityConsensuss(perScans)
    }

    private fun showSampleAndSave(wellToBarcode: Map<String, String?>) {

        val firstUri = rackUris.firstOrNull() ?: return
        val bmp = ImageUtilsSample.uriToBitmap(this, firstUri) ?: return
        val rotated = ImageUtilsSample.rotate90Clockwise(bmp)
        val rackOnly = ImageUtilsSample.deskewAndCropRackOpenCV(rotated)

        // Excel: Barcode → Sample
        val barcodeToSample = loadBarcodeToSampleMap(
            this,
            excelUri!!,
            selectedPlateColumn!!
        )

        // Well → Sample + Highlight finden
        val (wellToSample, highlightWell) = mapWellsToSamples(
            wellToBarcode,
            barcodeToSample,
            selectedSample
        )

        if (highlightWell == null) {
            Toast.makeText(this, "Sample not found", Toast.LENGTH_LONG).show()
            return
        }

        // Annotieren (nur das gewünschte Sample)
        val annotated = ImageUtilsSample.annotatesRack(
            rackOnly,
            wellToSample,
            highlightWell = highlightWell,
            showAll = false
        )

        // Anzeigen
        val iv = ImageView(this)
        iv.setImageBitmap(annotated)

        AlertDialog.Builder(this)
            .setTitle("Sample found: $selectedSample")
            .setView(iv)
            .setPositiveButton("OK", null)
            .show()

        // Speichern
        ImageUtilsSample.saveBitmapToGallery(
            this,
            annotated,
            "found_sample_${selectedSample}_${System.currentTimeMillis()}.jpg"
        )
    }



    private fun mapWellsToSamples(
        wellToBarcode: Map<String, String?>,
        barcodeToSample: Map<String, String>,
        selectedSample: String?
    ): Pair<Map<String, String>, String?> {

        val wellToSample = mutableMapOf<String, String>()
        var highlightWell: String? = null

        for ((well, barcode) in wellToBarcode) {
            val sample = barcode?.let { barcodeToSample[it] }
            if (sample != null) {
                wellToSample[well] = sample
                if (sample.equals(selectedSample, ignoreCase = true)) {
                    highlightWell = well
                }
            }
        }
        return wellToSample to highlightWell
    }







    private fun runAnnotationWorkflow(): Map<String, Map<String, String?>> {

        // 1) Excel: Barcode -> Sample
        val barcodeToSample = loadBarcodeToSampleMap(
            this,
            excelUri!!,
            selectedPlateColumn!!
        )

        // 2) Alle Scans decodieren
        val perScans = mutableListOf<Map<String, String?>>()

        for (uri in rackUris) {
            val bmp = ImageUtilsSample.uriToBitmap(this, uri) ?: continue
            val rotated = ImageUtilsSample.rotate90Clockwise(bmp)
            val rack = ImageUtilsSample.deskewAndCropRackOpenCV(rotated)

            val wellToBarcode = ImageUtilsSample.decodeRack(rack)

            // 3) Barcode -> Sample
            val wellToSample = wellToBarcode.mapValues { (_, barcode) ->
                barcode?.let { barcodeToSample[it] }
            }

            perScans.add(wellToSample)
        }

        if (perScans.isEmpty()) return emptyMap()

        // 4) Consensus über alle Scans
        val consensus = ImageUtilsSample.majorityConsensuss(perScans)

        // 5) Struktur fürs Excel / UI
        val out = mutableMapOf<String, MutableMap<String, String?>>()
        for ((well, sample) in consensus) {
            out[well] = mutableMapOf(selectedPlateColumn!! to sample)
        }

        return out
    }



    private fun showAnnotatedAndSave() {
        val firstUri = rackUris.firstOrNull() ?: return
        val bmp = ImageUtilsSample.uriToBitmap(this, firstUri) ?: return
        val rotated = ImageUtilsSample.rotate90Clockwise(bmp)
        val rack = ImageUtilsSample.deskewAndCropRackOpenCV(rotated)

        val flatMap = consensusMap.mapValues { it.value[selectedPlateColumn!!] }
        val annotated = ImageUtilsSample.annotatesRack(rack, flatMap, null, true)
        val iv = ImageView(this)

        iv.setImageBitmap(annotated)
        AlertDialog.Builder(this)
            .setTitle("Annotated Rack")
            .setView(iv)
            .setPositiveButton("OK", null)
            .show()

        ImageUtilsSample.saveBitmapToGallery(this, annotated, "rack_annotated_${System.currentTimeMillis()}.jpg")
    }

    private fun saveConsensusToExcel(outputUri: Uri) {
        if (consensusMap.isEmpty() || excelUri == null || selectedPlateColumn == null) return
        ExcelUtils.writeBarcodesToExcel(this, excelUri!!, outputUri, consensusMap, selectedPlateColumn!!)
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
