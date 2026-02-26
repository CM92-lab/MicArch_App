package com.example.cryobank

import android.content.Context
import android.net.Uri
import android.widget.Toast
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

object ExcelUtils {

    // ---------------------------------------------------------------
    // READ PLATE COLUMNS  (Fixes your unresolved reference)
    // ---------------------------------------------------------------
    fun readPlateColumns(ctx: Context, uri: Uri): List<String> {
        val result = mutableListOf<String>()

        try {
            val input: InputStream = ctx.contentResolver.openInputStream(uri)
                ?: return emptyList()

            val workbook = XSSFWorkbook(input)
            val sheet = workbook.getSheetAt(0)
            val header = sheet.getRow(0) ?: return emptyList()

            for (cellIndex in 0 until header.lastCellNum) {
                val cell = header.getCell(cellIndex)
                val name = cell?.stringCellValue ?: continue

                // Accept anything starting with “Plate” or matching “Plate XYZ”
                if (name.contains("plate", ignoreCase = true)) {
                    result.add(name)
                }
            }

            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }

    // ---------------------------------------------------------------
    // WRITE BARCODE OUTPUT EXCEL
    // ---------------------------------------------------------------
    fun writeBarcodesToExcel(
        context: Context,
        inputUri: Uri,
        outputUri: Uri,
        barcodes: Map<String, Map<String, String?>>,
        selectedPlate: String
    ) {
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
            if (inputStream == null) {
                Toast.makeText(context, "Cannot open input file", Toast.LENGTH_SHORT).show()
                return
            }

            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            val headerRow = sheet.getRow(0) ?: run {
                Toast.makeText(context, "Sheet empty", Toast.LENGTH_SHORT).show()
                return
            }

            val wellCol = headerRow.indexOfCell("Well")
            if (wellCol == -1) {
                Toast.makeText(context, "'Well' column missing", Toast.LENGTH_SHORT).show()
                return
            }

            val plateIndex = headerRow.indexOfCell(selectedPlate)
            if (plateIndex == -1) {
                Toast.makeText(context, "Selected Plate not found", Toast.LENGTH_SHORT).show()
                return
            }

            // Shift and insert new column
            shiftColumnsRight(sheet, plateIndex + 1)

            headerRow.createCell(plateIndex + 1).setCellValue("$selectedPlate Barcodes")

            for (r in 1..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                val well = row.getCell(wellCol)?.stringCellValue ?: continue
                val value = barcodes[well]?.get(selectedPlate) ?: ""

                val cell = row.getCell(plateIndex + 1) ?: row.createCell(plateIndex + 1)
                cell.setCellValue(value)
            }

            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                workbook.write(out)
            }

            workbook.close()
            Toast.makeText(context, "Excel saved", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------
    // Column shifting
    // ---------------------------------------------------------------
    private fun shiftColumnsRight(sheet: Sheet, startIndex: Int) {
        for (row in sheet) {
            for (c in row.lastCellNum downTo startIndex) {
                val oldCell = row.getCell(c - 1)
                val newCell = row.createCell(c)

                if (oldCell != null) {
                    when (oldCell.cellType) {
                        CellType.STRING -> newCell.setCellValue(oldCell.stringCellValue)
                        CellType.NUMERIC -> newCell.setCellValue(oldCell.numericCellValue)
                        CellType.BOOLEAN -> newCell.setCellValue(oldCell.booleanCellValue)
                        CellType.FORMULA -> newCell.setCellFormula(oldCell.cellFormula)
                        else -> newCell.setCellValue(oldCell.toString())
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Find header column
    // ---------------------------------------------------------------
    private fun Row.indexOfCell(name: String): Int {
        for (i in 0 until lastCellNum) {
            val c = getCell(i)
            if (c != null && c.stringCellValue.equals(name, ignoreCase = true)) {
                return i
            }
        }
        return -1
    }

    // ---------------------------------------------------------------
    // LOAD SAMPLE NAMES — FIXED
    // ---------------------------------------------------------------
    fun loadSampleNames(ctx: Context, uri: Uri): List<String> {
        val list = mutableListOf<String>()

        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return list
            val workbook = XSSFWorkbook(input)
            val sheet = workbook.getSheetAt(0)

            val header = sheet.getRow(0)
            var colIndex = -1

            // fixed (no more illegal .first {})
            for (i in 0 until header.lastCellNum) {
                val c = header.getCell(i)
                if (c != null && c.stringCellValue.contains("Sample", ignoreCase = true)) {
                    colIndex = i
                    break
                }
            }

            if (colIndex == -1) return list

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val s = row.getCell(colIndex)?.stringCellValue
                if (!s.isNullOrBlank()) list.add(s)
            }

            workbook.close()
        } catch (e: Exception) { }

        return list
    }

    // ---------------------------------------------------------------
    // GET WELL FOR SAMPLE — FIXED
    // ---------------------------------------------------------------
    fun getWellForSample(ctx: Context, uri: Uri, sample: String): String? {
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        val wb = XSSFWorkbook(input)
        val sheet = wb.getSheetAt(0)

        val header = sheet.getRow(0)

        var sampleCol = -1
        var wellCol = -1

        for (i in 0 until header.lastCellNum) {
            val cell = header.getCell(i) ?: continue
            val name = cell.stringCellValue

            if (name.contains("Sample", ignoreCase = true)) sampleCol = i
            if (name.contains("Well", ignoreCase = true)) wellCol = i
        }

        if (sampleCol == -1 || wellCol == -1) return null

        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val name = row.getCell(sampleCol)?.stringCellValue ?: ""
            if (name.equals(sample, ignoreCase = true)) {
                val result = row.getCell(wellCol)?.stringCellValue
                wb.close()
                return result
            }
        }

        wb.close()
        return null
    }
}
