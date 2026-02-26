package com.example.cryobank

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.text.equals


object ImageUtilsSample {

    // -----------------------------
    // Load & Preprocess
    // -----------------------------

    fun uriToBitmap(ctx: Context, uri: Uri): Bitmap? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "uriToBitmap failed: ${e.message}")
            null
        }
    }

    fun rotate90Clockwise(bmp: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    fun flipHorizontal(bmp: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            preScale(-1f, 1f)
        }
        return Bitmap.createBitmap(
            bmp, 0, 0, bmp.width, bmp.height, matrix, true
        )
    }

    fun invertBitmap(bmp: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        return out
    }

    fun toContrast(bmp: Bitmap, contrast: Float): Bitmap {
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, 0f,
                0f, contrast, 0f, 0f, 0f,
                0f, 0f, contrast, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        return out
    }

    fun deskewAndCropRackOpenCV(bitmap: Bitmap, marginRatio: Double = 0.02): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        val thresh = Mat()
        Imgproc.threshold(gray, thresh, 200.0, 255.0, Imgproc.THRESH_BINARY_INV)

        val contours = mutableListOf<org.opencv.core.MatOfPoint>()
        Imgproc.findContours(thresh, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        if (contours.isEmpty()) return bitmap

        val rackContour = contours.maxByOrNull { Imgproc.contourArea(it) }!!
        val rect = Imgproc.boundingRect(rackContour)

        val margin = (min(rect.width, rect.height) * marginRatio).toInt()
        val x = max(0, rect.x - margin)
        val y = max(0, rect.y - margin)
        val w = min(rect.width + 2 * margin, bitmap.width - x)
        val h = min(rect.height + 2 * margin, bitmap.height - y)

        val cropped = Mat(mat, rect)
        val outBmp = Bitmap.createBitmap(cropped.width(), cropped.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(cropped, outBmp)
        return outBmp
    }

    fun majorityConsensuss(perScanMaps: List<Map<String, String?>>): Map<String, String?> {
        val wells = mutableSetOf<String>()
        perScanMaps.forEach { wells.addAll(it.keys) }

        val out = mutableMapOf<String, String?>()
        for (well in wells) {
            val counts = mutableMapOf<String, Int>()
            for (m in perScanMaps) {
                val v = m[well]
                if (!v.isNullOrEmpty()) counts[v] = counts.getOrDefault(v, 0) + 1
            }
            out[well] = counts.maxByOrNull { it.value }?.key
        }
        return out
    }

    /** Annotate rack image */
    fun annotatesRack(
        fullImage: Bitmap,
        decodeMap: Map<String, String?>,
        highlightWell: String? = null, showAll: Boolean = true
    ): Bitmap {

        val rackRaw = deskewAndCropRackOpenCV(fullImage)
        val rack = flipHorizontal(rackRaw)
        val annotated = rack.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)

        val paintRect = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = (annotated.width / 200f).coerceAtLeast(2f)
        }

        val paintText = Paint().apply {
            color = Color.WHITE
            textSize = (annotated.width / 36f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val rows = listOf('A','B','C','D','E','F','G','H')
        val colsN = 12
        val rowsN = 8

        val cellW = annotated.width / colsN.toFloat()
        val cellH = annotated.height / rowsN.toFloat()
        val inset = 0.05f

        for (ri in 0 until rowsN) {
            val rowChar = rows[ri]
            for (ci in 0 until colsN) {

                val colNumber = ci + 1

                val left   = ci * cellW + cellW * inset
                val top    = ri * cellH + cellH * inset
                val right  = (ci + 1) * cellW - cellW * inset
                val bottom = (ri + 1) * cellH - cellH * inset

                val well = "$rowChar$colNumber"
                val sampleName = decodeMap[well]

                if (!showAll) {
                    // Search-Sample-Modus: nur Highlight zeichnen
                    if (highlightWell != null && highlightWell.equals(well, ignoreCase = true)) {
                        paintRect.color = Color.YELLOW
                        paintText.color = Color.YELLOW
                        canvas.drawRect(left, top, right, bottom, paintRect)
                        canvas.drawText(sampleName ?: well, left + 6f, top + paintText.textSize + 6f, paintText)
                    }
                } else {
                    // Whole-Rack-Modus: alle Wells anzeigen
                    paintRect.color = when {
                        highlightWell != null && highlightWell.equals(well, ignoreCase = true) -> Color.YELLOW
                        sampleName != null -> Color.GREEN
                        else -> Color.RED
                    }
                    canvas.drawRect(left, top, right, bottom, paintRect)
                    canvas.drawText(sampleName ?: well, left + 6f, top + paintText.textSize + 6f, paintText)
                }
            }
        }

        return annotated
    }




    fun decodeRack(rackImage: Bitmap, ctx: Context? = null): Map<String, String?> {
        val rack = deskewAndCropRackOpenCV(rackImage)

        val rows = listOf('A','B','C','D','E','F','G','H')
        val rowsN = 8
        val colsN = 12
        val cellWf = rack.width.toFloat() / colsN
        val cellHf = rack.height.toFloat() / rowsN
        val insetPct = 0f
        val map = mutableMapOf<String, String?>()

        for (ri in 0 until rowsN) {
            val r = rows[ri]
            for (colIdx in 0 until colsN) {
                val colNumber = 12 - colIdx
                val x1 = max(0, ((colIdx * cellWf) + cellWf * insetPct - 2).toInt())
                val y1 = max(0, ((ri * cellHf) + cellHf * insetPct - 2).toInt())
                val x2 = min(rack.width, ((colIdx + 1) * cellWf - cellWf * insetPct + 2).toInt())
                val y2 = min(rack.height, ((ri + 1) * cellHf - cellHf * insetPct + 2).toInt())

                val w = max(1, x2 - x1)
                val h = max(1, y2 - y1)

                val sx = x1.coerceIn(0, rack.width - 1)
                val sy = y1.coerceIn(0, rack.height - 1)
                val sw = if (sx + w > rack.width) rack.width - sx else w
                val sh = if (sy + h > rack.height) rack.height - sy else h

                val cellBmp = Bitmap.createBitmap(rack, sx, sy, sw, sh)
                val code = kotlinx.coroutines.runBlocking {
                    BarcodeDecoderSample.decodeCell(cellBmp)
                }
                val well = "$r$colNumber"
                map[well] = code
            }
        }
        return map
    }


    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, filename: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Cryobank")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e("ImageUtils", "saveBitmapToGallery failed: ${e.message}")
            false
        }
    }
}