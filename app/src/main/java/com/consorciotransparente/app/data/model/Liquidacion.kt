package com.consorciotransparente.app.data.model

/**
 * Documento en "buildings/{buildingId}/liquidaciones/{period}" — lo escribe la web
 * para Administradores (ver docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md),
 * nunca Android. Se modela acá sólo para que VecinosActivity pueda avisar de dónde
 * salió un monto de expensas calculado automáticamente — no para generar ni mostrar
 * liquidaciones completas desde el celular, eso sigue siendo exclusivo de la web.
 */
data class Liquidacion(
    val period: String = "",
    val breakdown: List<LiquidacionBreakdownRow> = emptyList()
)

data class LiquidacionBreakdownRow(
    val tenantUid: String = ""
)
