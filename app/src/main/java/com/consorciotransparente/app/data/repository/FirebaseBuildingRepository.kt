package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Building
import com.consorciotransparente.app.data.model.Liquidacion
import com.consorciotransparente.app.data.model.Role
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirebaseBuildingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BuildingRepository {
    override suspend fun getBuilding(buildingId: String): Building? {
        if (buildingId.isBlank()) return null
        val doc = db.collection("buildings").document(buildingId).get().await()
        return doc.toObject(Building::class.java)?.copy(id = doc.id)
    }

    /**
     * El orden importa: primero se crea el edificio (la regla exige que el usuario
     * todavía no tenga buildingId), y recién después se actualiza el perfil.
     */
    override suspend fun createBuilding(building: Building, adminUid: String): String {
        val ref = db.collection("buildings").document()
        val toSave = building.copy(adminUid = adminUid)
        ref.set(toSave).await()

        db.collection("users").document(adminUid).update(
            mapOf("buildingId" to ref.id, "role" to Role.ADMINISTRADOR.name)
        ).await()

        return ref.id
    }

    override suspend fun updateBuilding(buildingId: String, building: Building) {
        db.collection("buildings").document(buildingId).set(building.copy(id = buildingId)).await()
    }

    override suspend fun getLatestLiquidacionTenants(buildingId: String): Map<String, String> {
        if (buildingId.isBlank()) return emptyMap()
        val snapshot = db.collection("buildings").document(buildingId)
            .collection("liquidaciones")
            .orderBy("period", Query.Direction.DESCENDING)
            .limit(1)
            .get().await()
        val liquidacion = snapshot.documents.firstOrNull()?.toObject(Liquidacion::class.java) ?: return emptyMap()
        return liquidacion.breakdown
            .filter { it.tenantUid.isNotBlank() }
            .associate { it.tenantUid to liquidacion.period }
    }
}
