package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Building
import com.consorciotransparente.app.data.model.Role
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Implementa HU-09 (Configuración de Cobros y Vencimientos) y, de paso, el alta de
 * edificio que hasta ahora sólo se podía hacer a mano desde la consola de Firebase:
 * el primer usuario de un edificio lo crea y automáticamente pasa a ser su
 * Administrador (ver la regla de "bootstrap" en firestore.rules).
 */
class BuildingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun getBuilding(buildingId: String): Building? {
        if (buildingId.isBlank()) return null
        val doc = db.collection("buildings").document(buildingId).get().await()
        return doc.toObject(Building::class.java)?.copy(id = doc.id)
    }

    /**
     * Crea el edificio y, en el mismo flujo, promueve al creador a ADMINISTRADOR de
     * ese edificio. El orden importa: primero se crea el edificio (la regla exige que
     * el usuario todavía no tenga buildingId), y recién después se actualiza el perfil.
     */
    suspend fun createBuilding(building: Building, adminUid: String): String {
        val ref = db.collection("buildings").document()
        val toSave = building.copy(adminUid = adminUid)
        ref.set(toSave).await()

        db.collection("users").document(adminUid).update(
            mapOf("buildingId" to ref.id, "role" to Role.ADMINISTRADOR.name)
        ).await()

        return ref.id
    }

    /** Sólo el Administrador del edificio puede editar su configuración (reforzado en las reglas). */
    suspend fun updateBuilding(buildingId: String, building: Building) {
        db.collection("buildings").document(buildingId).set(building.copy(id = buildingId)).await()
    }
}
