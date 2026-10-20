package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Expense
import com.consorciotransparente.app.data.model.ExpenseStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseExpenseRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : ExpenseRepository {
    private fun expensesRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("expenses")

    override suspend fun listExpenses(buildingId: String): List<Expense> {
        val snapshot = expensesRef(buildingId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Expense::class.java)?.copy(id = doc.id)
        }
    }

    override suspend fun createExpense(
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
