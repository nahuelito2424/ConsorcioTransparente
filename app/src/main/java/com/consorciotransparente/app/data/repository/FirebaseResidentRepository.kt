package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseResidentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ResidentRepository {
    override suspend fun listResidents(buildingId: String): List<User> {
        val snapshot = db.collection("users")
            .whereEqualTo("buildingId", buildingId)
            .whereEqualTo("role", Role.INQUILINO.name)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(User::class.java) }
    }

    override suspend fun updateAmounts(buildingId: String, uid: String, rentAmount: Double, expensesAmount: Double) {
        db.collection("users").document(uid).update(
            mapOf("rentAmount" to rentAmount, "expensesAmount" to expensesAmount)
        ).await()
    }
}
