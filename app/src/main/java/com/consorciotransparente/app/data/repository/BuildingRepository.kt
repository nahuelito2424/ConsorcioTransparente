package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Building

/**
 * Implementa HU-09 (Configuración de Cobros y Vencimientos) y, de paso, el alta de
 * edificio que hasta ahora sólo se podía hacer a mano desde la consola de Firebase:
 * el primer usuario de un edificio lo crea y automáticamente pasa a ser su
 * Administrador (ver la regla de "bootstrap" en firestore.rules).
 *
 * Interfaz para permitir la implementación mock (ver RepositoryProvider).
 */
interface BuildingRepository {
    suspend fun getBuilding(buildingId: String): Building?

    /**
     * Crea el edificio y, en el mismo flujo, promueve al creador a ADMINISTRADOR de
     * ese edificio.
     */
    suspend fun createBuilding(building: Building, adminUid: String): String

    /** Sólo el Administrador del edificio puede editar su configuración (reforzado en las reglas). */
    suspend fun updateBuilding(buildingId: String, building: Building)
}
