package com.consorciotransparente.app.data.repository

/**
 * Suscripción del dispositivo a los tópicos de notificaciones push (ver
 * functions/index.js, que es quien publica en cada uno):
 * - "building_{buildingId}": todos los miembros del edificio (cierre de votación,
 *   vencimiento próximo — HU-02/HU-09).
 * - "building_{buildingId}_staff": sólo Encargado/Administrador (ticket nuevo —
 *   HU-10).
 * - "user_{uid}": un único usuario (cambio de estado de SU ticket — HU-10).
 */
interface NotificationRepository {
    suspend fun subscribeToBuilding(buildingId: String)
    suspend fun unsubscribeFromBuilding(buildingId: String)
    suspend fun subscribeToBuildingStaff(buildingId: String)
    suspend fun unsubscribeFromBuildingStaff(buildingId: String)
    suspend fun subscribeToUser(uid: String)
    suspend fun unsubscribeFromUser(uid: String)
}
