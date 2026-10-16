package com.fastlink.accounts.util

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

object ExcelExporter {

    fun exportToExcel(
        context: Context,
        fileNamePrefix: String,
        sheetTitle: String,
        headers: List<String>,
        rows: List<List<String>>
    ): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet(sheetTitle)
        sheet.isRightToLeft = true // دعم RTL كامل لجدول الإكسل

        val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.index
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.RIGHT
            setFont(headerFont)
        }
        val bodyStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.RIGHT
        }

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { colIdx, text ->
            val cell = headerRow.createCell(colIdx)
            cell.setCellValue(text)
            cell.cellStyle = headerStyle
            sheet.setColumnWidth(colIdx, 20 * 256)
        }

        rows.forEachIndexed { rowIdx, rowData ->
            val r = sheet.createRow(rowIdx + 1)
            rowData.forEachIndexed { colIdx, value ->
                val cell = r.createCell(colIdx)
                cell.setCellValue(value)
                cell.cellStyle = bodyStyle
            }
        }

        val outDir = PdfReportGenerator.getReportsOutputDir(context)
        val fileName = "${fileNamePrefix}_${FormatUtils.formatFileTimestamp()}.xlsx"
        val outFile = File(outDir, fileName)
        FileOutputStream(outFile).use { fos ->
            workbook.write(fos)
        }
        workbook.close()

        NotificationHelper.showNotification(
            context,
            NotificationHelper.CHANNEL_EXPORT,
            "تم تصدير ملف Excel بنجاح",
            outFile.name
        )
        return outFile
    }

    fun showExcelActionsDialog(context: Context, excelFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            excelFile
        )
        AlertDialog.Builder(context)
            .setTitle("تم تصدير ملف Excel")
            .setMessage("اسم الملف: ${excelFile.name}
اختر الإجراء المطلوب:")
            .setPositiveButton("مشاركة") { _, _ ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "مشاركة ملف Excel"))
            }
            .setNeutralButton("فتح") { _, _ ->
                try {
                    val openIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(openIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "لا يوجد تطبيق لفتح ملفات Excel على الجهاز", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("حفظ فقط") { _, _ ->
                Toast.makeText(context, "تم الحفظ في: ${excelFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
            .show()
    }
}
