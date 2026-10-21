package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Payment

/**
 * Implementa HU-03 (pago por transferencia). El método Mercado Pago (HU-04) se agrega
 * en un plan aparte; esta interfaz ya deja el modelo de datos listo para eso
 * (Payment.method/mercadoPagoPaymentId).
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
}
