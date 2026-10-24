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

    /**
     * De la liquidación de expensas más reciente **por orden de emisión real**
     * (`emittedAt`, no el nombre del período — se puede emitir un mes anterior después
     * de uno posterior, por ejemplo para corregirlo), qué inquilino (uid) quedó con
     * qué monto en qué período — para que VecinosActivity pueda avisar que un monto no
     * lo tipeó el Administrador a mano, sino que vino de ahí. El `amount` es necesario
     * para no mostrar el aviso si el Administrador ya pisó ese monto a mano después:
     * en ese caso `user.expensesAmount` ya no coincide con el de la liquidación. Mapa
     * vacío si todavía no se emitió ninguna, o si la lectura falla (es un aviso
     * best-effort, no debe romper la pantalla de Vecinos).
     */
    suspend fun getLatestLiquidacionTenants(buildingId: String): Map<String, LiquidacionTenantInfo>
}

data class LiquidacionTenantInfo(val period: String, val amount: Double)
