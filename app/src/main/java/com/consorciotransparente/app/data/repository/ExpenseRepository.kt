package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Expense

/**
 * Implementa HU-01 (Auditoría Documental Obligatoria):
 *  - Escenario 1: si no hay foto, el gasto se guarda igual pero como BLOQUEADO
 *    (en un backend HTTP esto sería un 400; en Firestore el equivalente correcto
 *    es una regla de seguridad que sólo deja marcar VERIFICADO si receiptUrl != "").
 *  - Escenario 2/3: con foto adjunta, se sube a Storage, se guarda VERIFICADO
 *    y queda visible para todos los inquilinos del edificio.
 *
 * Interfaz para permitir la implementación mock (ver RepositoryProvider).
 */
interface ExpenseRepository {
    suspend fun listExpenses(buildingId: String): List<Expense>

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
    ): Expense
}
