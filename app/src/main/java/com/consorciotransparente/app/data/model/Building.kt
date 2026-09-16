package com.consorciotransparente.app.data.model

/**
 * Documento en "buildings". Cubre HU-09: qué conceptos cobra este edificio y sus vencimientos.
 */
data class Building(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val chargesRent: Boolean = false,
    val chargesExpenses: Boolean = true,
    val expensesDueDay: Int = 10,
    val rentDueDay: Int = 10,
    val reminderDaysBefore: Int = 5, // HU-09 Escenario 2: anticipación de la notificación de vencimiento
    val hasEncargado: Boolean = false,
    val amenities: List<String> = emptyList(), // ej. "SUM", "Parrilla", "Pileta"
    val adminUid: String = ""
)

/**
 * Documento en "buildings/{buildingId}/units". Una unidad funcional (depto/local).
 */
data class UnitInfo(
    val id: String = "",
    val label: String = "",       // ej. "7B"
    val aliquot: Double = 0.0,    // porcentaje de alícuota para el prorrateo de expensas
    val ownerUid: String = "",
    val tenantUid: String = ""
)
