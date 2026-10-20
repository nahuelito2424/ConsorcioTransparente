package com.consorciotransparente.app.data.repository.mock

import com.consorciotransparente.app.data.model.ExpenseStatus
import com.consorciotransparente.app.data.repository.DashboardRepository
import com.consorciotransparente.app.data.repository.DashboardSummary

class MockDashboardRepository : DashboardRepository {
    override suspend fun loadSummary(buildingId: String): DashboardSummary {
        if (buildingId != MockDataStore.BUILDING_ID) return DashboardSummary(null, 0, 0)
        return DashboardSummary(
            building = MockDataStore.building,
            verifiedExpensesCount = MockDataStore.expenses.count { it.status == ExpenseStatus.VERIFICADO.name },
            activeVotesCount = MockDataStore.votes.count { !it.isClosed }
        )
    }
}
