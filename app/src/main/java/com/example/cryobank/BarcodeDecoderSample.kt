package com.example.cryobank

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object BarcodeDecoderSample {

    private val mlkitOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX)
        .build()

    suspend fun decodeCell(cell: Bitmap): String? {
        // 1. ML Kit, original
        decodeMLKit(cell)?.let { return it }

        // 2. ML Kit on rotated versions
        for (angle in listOf(90f, 180f, 270f)) {
            val rot = rotate(cell, angle)
            decodeMLKit(rot)?.let { return it }
        }

        // 3. ML Kit on enhanced versions
        val scaled = Bitmap.createScaledBitmap(cell, cell.width * 2, cell.height * 2, true)
        decodeMLKit(scaled)?.let { return it }

        // 4. Invert using ImageUtilsSample
        val inverted = ImageUtilsSample.invertBitmap(cell)
        decodeMLKit(inverted)?.let { return it }

        // 5. Contrast using ImageUtilsSample
        val contrast = ImageUtilsSample.toContrast(cell, 1.8f)
        decodeMLKit(contrast)?.let { return it }

        // ZXing fallback
        val fallbacks = listOf(cell, scaled, inverted, contrast)
        for ((i, bmp) in fallbacks.withIndex()) {
            val res = decodeZXing(bmp)
            if (res != null) {
                Log.d("BarcodeDecoder", "ZXing succeeded on fallback $i")
                return res
            }
        }

        Log.d("BarcodeDecoder", "Failed to decode cell")
        return null
    }

    private suspend fun decodeMLKit(bmp: Bitmap): String? = suspendCoroutine { cont ->
        try {
            val image = InputImage.fromBitmap(bmp, 0)
            val scanner = BarcodeScanning.getClient(mlkitOptions)

            scanner.process(image)
                .addOnSuccessListener { list ->
                    val first = list.firstOrNull()?.rawValue
                    cont.resume(first)
                }
                .addOnFailureListener {
                    cont.resume(null)
                }

        } catch (e: Exception) {
            cont.resume(null)
        }
    }

    private fun decodeZXing(bmp: Bitmap): String? {
        return try {
            val intArray = IntArray(bmp.width * bmp.height)
            bmp.getPixels(intArray, 0, bmp.width, 0, 0, bmp.width, bmp.height)

            val source = RGBLuminanceSource(bmp.width, bmp.height, intArray)
            val bitmap = BinaryBitmap(HybridBinarizer(source))

            val hints = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.DATA_MATRIX),
                DecodeHintType.TRY_HARDER to true
            )

            val reader = MultiFormatReader().apply {
                setHints(hints)
            }

            val result = reader.decode(bitmap)
            result.text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            Log.e("BarcodeDecoder", "ZXing decode error: ${e.message}")
            null
        }
    }

    private fun rotate(bmp: Bitmap, angle: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }
}
