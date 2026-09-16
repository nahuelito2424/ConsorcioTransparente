package com.consorciotransparente.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class TicketStatus {
    REPORTADO, EN_PRESUPUESTO, A_COBRAR, RESUELTO
}

/**
 * Documento en "buildings/{buildingId}/tickets". Implementa HU-10: el flujo completo de
 * incidente de mantenimiento, desde el reporte del inquilino hasta el pago del arreglo.
 */
data class Ticket(
    val id: String = "",
    val category: String = "", // Plomería, Electricidad, Gas, Albañilería, Otro
    val description: String = "",
    val photoUrl: String = "",           // foto del problema, opcional (Escenario 1)
    val status: String = TicketStatus.REPORTADO.name,
    val budgetAmount: Double = 0.0,      // 0 = "a cotizar" (Escenario 2)
    val purchaseReceiptUrl: String = "", // comprobante de compra de materiales (Escenario 3)
    val paymentReceiptUrl: String = "",  // comprobante de pago vía Mercado Pago (Escenario 3)
    val reportedByUid: String = "",
    val assignedToUid: String = "",      // Encargado o Administrador
    @ServerTimestamp val createdAt: Date? = null
)
