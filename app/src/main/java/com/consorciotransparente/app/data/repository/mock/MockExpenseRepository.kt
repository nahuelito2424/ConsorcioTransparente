package com.consorciotransparente.app.data.repository.mock

import android.net.Uri
import com.consorciotransparente.app.data.model.Expense
import com.consorciotransparente.app.data.model.ExpenseStatus
import com.consorciotransparente.app.data.repository.ExpenseRepository
import java.util.Date

class MockExpenseRepository : ExpenseRepository {
    override suspend fun listExpenses(buildingId: String): List<Expense> =
        MockDataStore.expenses.sortedByDescending { it.createdAt }

    override suspend fun createExpense(
        buildingId: String,
        description: String,
        category: String,
        providerName: String,
        amount: Double,
        createdByUid: String,
        receiptImage: Uri?
    ): Expense {
        // No hay Storage real en mock: la propia Uri local hace las veces de "url subida".
        val receiptUrl = receiptImage?.toString() ?: ""
        val expense = Expense(
            id = MockDataStore.newId("exp"),
            description = description,
            category = category,
            providerName = providerName,
            amount = amount,
            receiptUrl = receiptUrl,
            status = if (receiptUrl.isNotBlank()) ExpenseStatus.VERIFICADO.name else ExpenseStatus.BLOQUEADO.name,
            createdByUid = createdByUid,
            createdAt = Date()
        )
        MockDataStore.expenses.add(0, expense)
        return expense
    }
}
