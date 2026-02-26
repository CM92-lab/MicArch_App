package com.example.cryobank

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object ExcelUtilsSample {

    // ---------------------------------------------------------------
    // READ PLATE COLUMNS
    // ---------------------------------------------------------------
    fun readPlateColumns(ctx: Context, uri: Uri): List<String> {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val wb = XSSFWorkbook(input)
                val sheet = wb.getSheetAt(0)
                val header = sheet.getRow(0) ?: run {
                    wb.close()
                    return emptyList()
                }

                val out = mutableListOf<String>()
                for (i in 0 until header.lastCellNum) {
                    val cell = header.getCell(i) ?: continue
                    val name = cell.toString().trim()
                    if (name.contains("plate", ignoreCase = true)) {
                        out.add(name)
                    }
                }
                wb.close()
                out
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ---------------------------------------------------------------
    // LOAD SAMPLE NAMES FROM PLATE COLUMN
    // ---------------------------------------------------------------
    fun loadSampleNamesFromPlate(ctx: Context, uri: Uri, plate: String): List<String> {
        val list = mutableListOf<String>()

        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val wb = XSSFWorkbook(input)
                val sheet = wb.getSheetAt(0)
                val header = sheet.getRow(0) ?: run {
                    wb.close()
                    return list
                }

                var plateIdx = -1
                for (i in 0 until header.lastCellNum) {
                    val c = header.getCell(i) ?: continue
                    if (c.toString().trim() == plate.trim()) {
                        plateIdx = i
                        break
                    }
                }

                if (plateIdx == -1) {
                    wb.close()
                    return list
                }

                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val cell = row.getCell(plateIdx) ?: continue
                    val sample = cell.toString().trim()
                    if (sample.isNotEmpty()) list.add(sample)
                }

                wb.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }


    // ---------------------------------------------------------------
    // BARCODE -> SAMPLE MAP
    // ---------------------------------------------------------------
    fun loadBarcodeToSampleMap(
        ctx: Context,
        uri: Uri,
        plate: String
    ): Map<String, String> {

        val out = mutableMapOf<String, String>()

        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val wb = XSSFWorkbook(input)
                val sheet = wb.getSheetAt(0)
                val header = sheet.getRow(0) ?: run {
                    wb.close()
                    return emptyMap()
                }

                var plateIdx = -1
                var barcodeIdx = -1
                val barcodeHeader = "${plate.trim()} Barcodes"

                for (i in 0 until header.lastCellNum) {
                    val c = header.getCell(i) ?: continue
                    val s = c.toString().trim()
                    if (s == plate.trim()) plateIdx = i
                    if (s == barcodeHeader) barcodeIdx = i
                }

                if (plateIdx == -1 || barcodeIdx == -1) {
                    wb.close()
                    return emptyMap()
                }

                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val barcode = row.getCell(barcodeIdx)?.toString()?.trim()
                    val sample = row.getCell(plateIdx)?.toString()?.trim()
                    if (!barcode.isNullOrEmpty() && !sample.isNullOrEmpty()) {
                        out[barcode] = sample
                    }
                }

                wb.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return out
    }

    // ---------------------------------------------------------------
    // LOOKUPS
    // ---------------------------------------------------------------
    fun sampleForBarcode(
        ctx: Context,
        uri: Uri,
        plate: String,
        barcode: String
    ): String? {
        return loadBarcodeToSampleMap(ctx, uri, plate)[barcode]
    }

    fun sampleExistsInPlate(
        ctx: Context,
        excelUri: Uri,
        plateColumn: String,
        sampleName: String
    ): Boolean {
        val samples = loadSampleNamesFromPlate(ctx, excelUri, plateColumn)
        return samples.any { it.equals(sampleName, ignoreCase = true) }
    }
}
