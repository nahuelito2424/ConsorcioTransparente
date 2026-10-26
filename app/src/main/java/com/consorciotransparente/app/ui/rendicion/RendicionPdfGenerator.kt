package com.consorciotransparente.app.ui.rendicion

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.ExpenseCategory
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PAGE_WIDTH = 595 // A4 a 72dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f

/**
 * Mismo contenido que la pantalla, en texto — sin las fotos de comprobante (ver
 * diseño: incrustarlas obligaría a descargarlas todas antes de poder exportar). Un
 * período sin gastos genera igual el PDF, con "$0" y sin filas de categoría.
 */
fun generarPdf(context: Context, buildingName: String, period: String, state: RendicionUiState): File {
    val esAr = Locale.Builder().setLanguage("es").setRegion("AR").build()
    val currencyFormat = NumberFormat.getCurrencyInstance(esAr)
    val monthLabelFormat = SimpleDateFormat("MMMM yyyy", esAr)
    val (year, month) = period.split("-").map { it.toInt() }
    val periodLabel = monthLabelFormat
        .format(Calendar.getInstance().apply { set(year, month - 1, 1) }.time)
        .replaceFirstChar { it.uppercase() }

    val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
    val subtitlePaint = Paint().apply { textSize = 13f }
    val boldBodyPaint = Paint().apply { textSize = 11f; isFakeBoldText = true }
    val bodyPaint = Paint().apply { textSize = 11f }
    val smallPaint = Paint().apply { textSize = 9f; color = android.graphics.Color.DKGRAY }

    val document = PdfDocument()
    var page = document.startPage(newPageInfo(document))
    var canvas = page.canvas
    var y = MARGIN

    fun breakPageIfNeeded(lineHeight: Float) {
        if (y + lineHeight > PAGE_HEIGHT - MARGIN) {
            document.finishPage(page)
            page = document.startPage(newPageInfo(document))
            canvas = page.canvas
            y = MARGIN
        }
    }

    canvas.drawText(buildingName.ifBlank { "Consorcio" }, MARGIN, y, titlePaint)
    y += 24f
    canvas.drawText("Rendición de cuentas — $periodLabel", MARGIN, y, subtitlePaint)
    y += 28f

    canvas.drawText("Total gastado: ${currencyFormat.format(state.totalVerificado)}", MARGIN, y, boldBodyPaint)
    y += 20f

    if (state.bloqueadoCount > 0) {
        val sustantivo = if (state.bloqueadoCount == 1) "gasto pendiente" else "gastos pendientes"
        canvas.drawText(
            "+ ${state.bloqueadoCount} $sustantivo de comprobante " +
                "(${currencyFormat.format(state.bloqueadoTotal)}), no incluido(s) en este total.",
            MARGIN, y, smallPaint
        )
        y += 20f
    }
    y += 10f

    for ((category, total) in state.categoryTotals) {
        breakPageIfNeeded(18f)
        canvas.drawText("${categoryLabel(context, category)}: ${currencyFormat.format(total)}", MARGIN, y, boldBodyPaint)
        y += 18f
        for (expense in state.expensesByCategory[category].orEmpty()) {
            breakPageIfNeeded(16f)
            val provider = expense.providerName.ifBlank { context.getString(R.string.rendicion_sin_proveedor) }
            canvas.drawText(
                "   ${expense.description} — $provider — ${currencyFormat.format(expense.amount)}",
                MARGIN, y, bodyPaint
            )
            y += 16f
        }
        y += 6f
    }

    breakPageIfNeeded(20f)
    y += 10f
    val today = SimpleDateFormat("dd/MM/yyyy", esAr).format(Date())
    canvas.drawText(
        "Generado desde Consorcio Transparente el $today — no reemplaza la rendición de cuentas " +
            "formal del edificio si existe una vía distinta.",
        MARGIN, y, smallPaint
    )

    document.finishPage(page)

    val dir = File(context.cacheDir, "rendiciones").apply { mkdirs() }
    val file = File(dir, "rendicion_$period.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    return file
}

private fun newPageInfo(document: PdfDocument) =
    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create()

private fun categoryLabel(context: Context, category: ExpenseCategory): String {
    val labels = context.resources.getStringArray(R.array.expense_categories)
    val index = ExpenseCategory.entries.indexOf(category)
    return labels.getOrElse(index) { category.name }
}
