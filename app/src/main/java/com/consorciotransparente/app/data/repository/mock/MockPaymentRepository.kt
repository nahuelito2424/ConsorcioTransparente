package com.consorciotransparente.app.data.repository.mock

import android.net.Uri
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.model.PaymentMethod
import com.consorciotransparente.app.data.model.PaymentStatus
import com.consorciotransparente.app.data.repository.PaymentRepository
import java.util.Date

class MockPaymentRepository : PaymentRepository {
    private fun docId(uid: String, period: String) = "${uid}_$period"

    override suspend fun getCurrentPayment(buildingId: String, uid: String, period: String): Payment? =
        MockDataStore.payments.firstOrNull { it.id == docId(uid, period) }

    override suspend fun listPaymentsForBuilding(buildingId: String, period: String): List<Payment> =
        MockDataStore.payments.filter { it.period == period }

    override suspend fun listMyPayments(buildingId: String, uid: String): List<Payment> =
        MockDataStore.payments.filter { it.uid == uid }.sortedByDescending { it.period }

    override suspend fun uploadReceipt(
        buildingId: String,
        uid: String,
        period: String,
        breakdown: Map<String, Double>,
        receipt: Uri
    ): Payment {
        val payment = Payment(
            id = docId(uid, period),
            uid = uid,
            period = period,
            breakdown = breakdown,
            amount = breakdown.values.sum(),
            method = PaymentMethod.TRANSFERENCIA.name,
            status = PaymentStatus.A_VERIFICAR.name,
            receiptUrl = receipt.toString(),
            createdAt = Date()
        )
        val index = MockDataStore.payments.indexOfFirst { it.id == payment.id }
        if (index >= 0) MockDataStore.payments[index] = payment else MockDataStore.payments.add(payment)
        return payment
    }

    override suspend fun approvePayment(buildingId: String, paymentId: String, verifiedByUid: String) {
        update(paymentId) { it.copy(status = PaymentStatus.PAGADO.name, verifiedAt = Date(), verifiedByUid = verifiedByUid) }
    }

    override suspend fun rejectPayment(buildingId: String, paymentId: String, reason: String) {
        update(paymentId) { it.copy(status = PaymentStatus.RECHAZADO.name, rejectionReason = reason) }
    }

    private fun update(paymentId: String, transform: (Payment) -> Payment) {
        val index = MockDataStore.payments.indexOfFirst { it.id == paymentId }
        if (index >= 0) MockDataStore.payments[index] = transform(MockDataStore.payments[index])
    }
}
