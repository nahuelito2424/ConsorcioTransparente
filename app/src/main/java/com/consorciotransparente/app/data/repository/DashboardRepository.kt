package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Building
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class DashboardSummary(
    val building: Building?,
    val verifiedExpensesCount: Int,
    val activeVotesCount: Int
)

class DashboardRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun loadSummary(buildingId: String): DashboardSummary {
        if (buildingId.isBlank()) return DashboardSummary(null, 0, 0)

        val buildingSnap = db.collection("buildings").document(buildingId).get().await()
        val building = buildingSnap.toObject(Building::class.java)?.copy(id = buildingSnap.id)

        val expensesSnap = db.collection("buildings").document(buildingId)
            .collection("expenses")
            .whereEqualTo("status", "VERIFICADO")
            .get().await()

        val votesSnap = db.collection("buildings").document(buildingId)
            .collection("votes")
            .whereEqualTo("isClosed", false)
            .get().await()

        return DashboardSummary(
            building = building,
            verifiedExpensesCount = expensesSnap.size(),
            activeVotesCount = votesSnap.size()
        )
    }
}
