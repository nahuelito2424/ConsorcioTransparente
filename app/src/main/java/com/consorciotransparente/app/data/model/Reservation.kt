package com.consorciotransparente.app.data.model

import java.util.Date

/**
 * Documento en "buildings/{buildingId}/reservations". Implementa HU-06: reserva de
 * espacios comunes (SUM, parrilla, pileta) con edición/cancelación por el propio usuario.
 */
data class Reservation(
    val id: String = "",
    val spaceName: String = "",   // "SUM", "Parrilla", "Pileta"
    val date: String = "",        // yyyy-MM-dd
    val timeSlot: String = "",    // ej. "18:00-22:00"
    val reservedByUid: String = "",
    val reservedByName: String = ""
)
