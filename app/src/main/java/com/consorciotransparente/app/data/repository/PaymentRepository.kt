package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Payment

/**
 * Implementa HU-03 (pago por transferencia) y HU-04 (Mercado Pago). El pago por
 * Mercado Pago no se aprueba/rechaza a mano (ver approvePayment/rejectPayment, que
 * sólo tiene sentido para transferencia): lo confirma solo el webhook de Mercado Pago,
 * del lado de la Cloud Function.
 */
interface PaymentRepository {
    suspend fun getCurrentPayment(buildingId: String, uid: String, period: String): Payment?
    suspend fun listPaymentsForBuilding(buildingId: String, period: String): List<Payment>
    suspend fun listMyPayments(buildingId: String, uid: String): List<Payment>

    /** Escenario transferencia: crea o re-sube (tras un RECHAZADO) el comprobante. */
    suspend fun uploadReceipt(
        buildingId: String,
        uid: String,
        period: String,
        breakdown: Map<String, Double>,
        receipt: Uri
    ): Payment

    suspend fun approvePayment(buildingId: String, paymentId: String, verifiedByUid: String)
    suspend fun rejectPayment(buildingId: String, paymentId: String, reason: String)

    /**
     * HU-04: genera una preferencia de Checkout Pro para el período actual y devuelve
     * la URL a abrir (Custom Tab). El monto se calcula server-side (ver Cloud Function
     * createMercadoPagoPreference) — acá no se manda ningún monto.
     */
    suspend fun createMercadoPagoPreference(buildingId: String, period: String): String
}
