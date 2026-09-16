package com.consorciotransparente.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class ExpenseCategory {
    REPUESTOS, MANO_DE_OBRA, PLOMERIA, ELECTRICIDAD, GAS, ALBANILERIA, LIMPIEZA, OTRO
}

/**
 * Documento en "buildings/{buildingId}/expenses". Implementa HU-01 (Auditoría Documental
 * Obligatoria): un gasto sin receiptUrl se guarda como BLOQUEADO y no entra en la
 * liquidación de expensas hasta que se adjunte el comprobante.
 */
data class Expense(
    val id: String = "",
    val description: String = "",
    val category: String = ExpenseCategory.OTRO.name,
    val providerName: String = "",
    val amount: Double = 0.0,
    val receiptUrl: String = "",
    val status: String = ExpenseStatus.BLOQUEADO.name,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Date? = null
) {
    val isVerified: Boolean get() = receiptUrl.isNotBlank()
}

enum class ExpenseStatus {
    VERIFICADO,   // tiene comprobante -> auditado, entra en la liquidación
    BLOQUEADO     // sin comprobante -> HU-01 Escenario 1, no se prorratea
}
