package com.consorciotransparente.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Período de pago actual en formato "yyyy-MM", usado como parte del id de Payment. */
fun currentPeriod(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
