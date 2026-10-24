package com.consorciotransparente.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Documento en "buildings/{buildingId}/liquidaciones/{period}" — lo escribe la web
 * para Administradores (ver docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md),
 * nunca Android. Se modela acá sólo para que VecinosActivity pueda avisar de dónde
 * salió un monto de expensas calculado automáticamente — no para generar ni mostrar
 * liquidaciones completas desde el celular, eso sigue siendo exclusivo de la web.
 */
data class Liquidacion(
    val period: String = "",
    val breakdown: List<LiquidacionBreakdownRow> = emptyList(),
    // Sin @ServerTimestamp: Android sólo lee este documento, nunca lo escribe. Se usa
    // para encontrar la liquidación más reciente por orden de emisión real, no por
    // orden alfabético de "period" — dos liquidaciones pueden emitirse fuera de orden
    // (ej. corregir un mes anterior después de haber emitido uno posterior).
    val emittedAt: Date? = null
)

data class LiquidacionBreakdownRow(
    val tenantUid: String = "",
    val amount: Double = 0.0
)
