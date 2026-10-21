package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.model.PaymentMethod
import com.consorciotransparente.app.data.model.PaymentStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class FirebasePaymentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : PaymentRepository {
    private fun paymentsRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("payments")

    private fun docId(uid: String, period: String) = "${uid}_$period"

    override suspend fun getCurrentPayment(buildingId: String, uid: String, period: String): Payment? {
        val doc = paymentsRef(buildingId).document(docId(uid, period)).get().await()
        return doc.toObject(Payment::class.java)?.copy(id = doc.id)
    }

    override suspend fun listPaymentsForBuilding(buildingId: String, period: String): List<Payment> {
        val snapshot = paymentsRef(buildingId).whereEqualTo("period", period).get().await()
        return snapshot.documents.mapNotNull { it.toObject(Payment::class.java)?.copy(id = it.id) }
    }

    override suspend fun listMyPayments(buildingId: String, uid: String): List<Payment> {
        val snapshot = paymentsRef(buildingId)
            .whereEqualTo("uid", uid)
            .orderBy("period", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(Payment::class.java)?.copy(id = it.id) }
    }

    override suspend fun uploadReceipt(
        buildingId: String,
        uid: String,
        period: String,
        breakdown: Map<String, Double>,
        receipt: Uri
    ): Payment {
        val fileName = "payments/$buildingId/${uid}_${period}_${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        ref.putFile(receipt).await()
        val url = ref.downloadUrl.await().toString()

        val payment = Payment(
            uid = uid,
            period = period,
            breakdown = breakdown,
            amount = breakdown.values.sum(),
            method = PaymentMethod.TRANSFERENCIA.name,
            status = PaymentStatus.A_VERIFICAR.name,
            receiptUrl = url,
            createdAt = Date()
        )
        paymentsRef(buildingId).document(docId(uid, period)).set(payment).await()
        return payment.copy(id = docId(uid, period))
    }

    override suspend fun approvePayment(buildingId: String, paymentId: String, verifiedByUid: String) {
        paymentsRef(buildingId).document(paymentId).update(
            mapOf(
                "status" to PaymentStatus.PAGADO.name,
                "verifiedAt" to Date(),
                "verifiedByUid" to verifiedByUid
            )
        ).await()
    }

    override suspend fun rejectPayment(buildingId: String, paymentId: String, reason: String) {
        paymentsRef(buildingId).document(paymentId).update(
            mapOf(
                "status" to PaymentStatus.RECHAZADO.name,
                "rejectionReason" to reason
            )
        ).await()
    }
}
