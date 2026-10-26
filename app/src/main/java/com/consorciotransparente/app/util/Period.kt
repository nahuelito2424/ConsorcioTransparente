package com.consorciotransparente.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Período de pago actual en formato "yyyy-MM", usado como parte del id de Payment. */
fun currentPeriod(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

/** Desplaza un período "yyyy-MM" delta meses (± ), cruzando años si corresponde. */
fun shiftPeriod(period: String, delta: Int): String {
    val (year, month) = period.split("-").map { it.toInt() }
    val calendar = Calendar.getInstance().apply {
        set(year, month - 1 + delta, 15) // día 15: no cae nunca en un día inexistente de otro mes
    }
    return SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.time)
}
