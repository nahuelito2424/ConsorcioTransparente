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
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

/**
 * Mismo contenido que la pantalla, en texto — sin las fotos de comprobante (ver
 * diseño: incrustarlas obligaría a descargarlas todas antes de poder exportar). Un
 * período sin gastos genera igual el PDF, con "$0" y sin filas de categoría.
 *
 * Todo lo que necesita (edificio, período, montos) sale de `state`, no de parámetros
 * aparte — si el período de `state` y el de quien llama pudieran desalinearse (p. ej.
 * el usuario navega de mes mientras carga), el PDF podría salir titulado con un mes y
 * los números de otro.
 */
fun generarPdf(context: Context, state: RendicionUiState): File {
    val esAr = Locale.Builder().setLanguage("es").setRegion("AR").build()
    val currencyFormat = NumberFormat.getCurrencyInstance(esAr)
    val monthLabelFormat = SimpleDateFormat("MMMM yyyy", esAr)
    val (year, month) = state.period.split("-").map { it.toInt() }
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

    // Parte `text` en tantas líneas como haga falta para no pasarse de CONTENT_WIDTH,
    // pidiendo página nueva línea por línea si corresponde.
    fun drawWrapped(text: String, paint: Paint, lineHeight: Float) {
        var remaining = text
        while (remaining.isNotEmpty()) {
            breakPageIfNeeded(lineHeight)
            val fit = paint.breakText(remaining, true, CONTENT_WIDTH, null)
            var cut = fit
            if (cut < remaining.length) {
                val lastSpace = remaining.lastIndexOf(' ', cut - 1)
                if (lastSpace > 0) cut = lastSpace
            }
            canvas.drawText(remaining.substring(0, cut).trimEnd(), MARGIN, y, paint)
            y += lineHeight
            remaining = remaining.substring(cut).trimStart()
        }
    }

    // Monto siempre a la derecha, en su propia columna — una descripción/proveedor
    // largo nunca lo empuja fuera de la hoja; en vez de eso, se trunca con "…".
    fun drawRowWithAmount(label: String, amountText: String, paint: Paint, indent: Float, lineHeight: Float) {
        breakPageIfNeeded(lineHeight)
        val amountWidth = paint.measureText(amountText)
        val maxLabelWidth = CONTENT_WIDTH - indent - amountWidth - 10f
        val fit = paint.breakText(label, true, maxLabelWidth, null)
        val labelToDraw = if (fit < label.length) label.substring(0, fit).trimEnd() + "…" else label
        canvas.drawText(labelToDraw, MARGIN + indent, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(amountText, PAGE_WIDTH - MARGIN, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += lineHeight
    }

    canvas.drawText((state.buildingName.ifBlank { "Consorcio" }), MARGIN, y, titlePaint)
    y += 24f
    canvas.drawText("Rendición de cuentas — $periodLabel", MARGIN, y, subtitlePaint)
    y += 28f

    drawRowWithAmount("Total gastado", currencyFormat.format(state.totalVerificado), boldBodyPaint, 0f, 20f)

    if (state.bloqueadoCount > 0) {
        val sustantivo = if (state.bloqueadoCount == 1) "gasto pendiente" else "gastos pendientes"
        drawWrapped(
            "+ ${state.bloqueadoCount} $sustantivo de comprobante " +
                "(${currencyFormat.format(state.bloqueadoTotal)}), no incluido(s) en este total.",
            smallPaint, 12f
        )
    }
    y += 10f

    for ((category, total) in state.categoryTotals) {
        drawRowWithAmount(categoryLabel(context, category), currencyFormat.format(total), boldBodyPaint, 0f, 18f)
        for (expense in state.expensesByCategory[category].orEmpty()) {
            val provider = expense.providerName.ifBlank { context.getString(R.string.rendicion_sin_proveedor) }
            drawRowWithAmount(
                "${expense.description} — $provider",
                currencyFormat.format(expense.amount),
                bodyPaint, 14f, 16f
            )
        }
        y += 6f
    }

    y += 10f
    val today = SimpleDateFormat("dd/MM/yyyy", esAr).format(Date())
    drawWrapped(
        "Generado desde Consorcio Transparente el $today — no reemplaza la rendición de cuentas " +
            "formal del edificio si existe una vía distinta.",
        smallPaint, 12f
    )

    document.finishPage(page)

    val dir = File(context.cacheDir, "rendiciones").apply { mkdirs() }
    val file = File(dir, "rendicion_${state.period}.pdf")
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
