package com.consorciotransparente.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class PaymentMethod { TRANSFERENCIA, MERCADO_PAGO }
enum class PaymentStatus { A_VERIFICAR, PAGADO, RECHAZADO }

/**
 * Documento en "buildings/{buildingId}/payments", doc id = "{uid}_{period}" (period =
 * "yyyy-MM"). Implementa HU-03: breakdown/amount quedan congelados al crear el pago —
 * si el Administrador cambia los montos del vecino después, no afecta a un pago ya
 * generado, recién al período siguiente. Reutiliza el mismo id tras un RECHAZADO (no
 * hay historial de intentos, solo el motivo del último rechazo).
 */
data class Payment(
    val id: String = "",
    val uid: String = "",
    val period: String = "",
    val breakdown: Map<String, Double> = emptyMap(),
    val amount: Double = 0.0,
    val method: String = PaymentMethod.TRANSFERENCIA.name,
    val status: String = PaymentStatus.A_VERIFICAR.name,
    val receiptUrl: String = "",
    val mercadoPagoPaymentId: String = "",
    val rejectionReason: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val verifiedAt: Date? = null,
    val verifiedByUid: String = ""
)
