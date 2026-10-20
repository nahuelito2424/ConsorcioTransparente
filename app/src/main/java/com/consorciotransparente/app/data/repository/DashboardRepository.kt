package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Building

data class DashboardSummary(
    val building: Building?,
    val verifiedExpensesCount: Int,
    val activeVotesCount: Int
)

/** Interfaz para permitir la implementación mock (ver RepositoryProvider). */
interface DashboardRepository {
    suspend fun loadSummary(buildingId: String): DashboardSummary
}
