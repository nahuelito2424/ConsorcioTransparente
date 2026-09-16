package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Expense
import com.consorciotransparente.app.data.model.ExpenseStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Implementa HU-01 (Auditoría Documental Obligatoria):
 *  - Escenario 1: si no hay foto, el gasto se guarda igual pero como BLOQUEADO
 *    (en un backend HTTP esto sería un 400; en Firestore el equivalente correcto
 *    es una regla de seguridad que sólo deja marcar VERIFICADO si receiptUrl != "").
 *  - Escenario 2/3: con foto adjunta, se sube a Storage, se guarda VERIFICADO
 *    y queda visible para todos los inquilinos del edificio.
 */
class ExpenseRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    private fun expensesRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("expenses")

    suspend fun listExpenses(buildingId: String): List<Expense> {
        val snapshot = expensesRef(buildingId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Expense::class.java)?.copy(id = doc.id)
        }
    }

    /**
     * @param receiptImage null => Escenario 1 (bloqueado, sin comprobante).
     */
    suspend fun createExpense(
        buildingId: String,
        description: String,
        category: String,
        providerName: String,
        amount: Double,
        createdByUid: String,
        receiptImage: Uri?
    ): Expense {
        val receiptUrl = receiptImage?.let { uploadReceipt(buildingId, it) } ?: ""
        val expense = Expense(
            description = description,
            category = category,
            providerName = providerName,
            amount = amount,
            receiptUrl = receiptUrl,
            status = if (receiptUrl.isNotBlank()) ExpenseStatus.VERIFICADO.name else ExpenseStatus.BLOQUEADO.name,
            createdByUid = createdByUid
        )
        val ref = expensesRef(buildingId).document()
        ref.set(expense).await()
        return expense.copy(id = ref.id)
    }

    private suspend fun uploadReceipt(buildingId: String, image: Uri): String {
        val fileName = "receipts/$buildingId/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        ref.putFile(image).await()
        return ref.downloadUrl.await().toString()
    }
}
