package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.User

/**
 * Implementa la parte de HU-03 donde el Administrador le asigna un monto fijo de
 * alquiler y/o expensas a cada inquilino de su edificio (sin alícuotas/unidades, ver
 * docs/superpowers/specs/2026-10-20-pago-expensas-design.md).
 */
interface ResidentRepository {
    suspend fun listResidents(buildingId: String): List<User>
    suspend fun updateAmounts(buildingId: String, uid: String, rentAmount: Double, expensesAmount: Double)
}
