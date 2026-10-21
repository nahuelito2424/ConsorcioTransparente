package com.consorciotransparente.app.data.repository

/**
 * Suscripción del dispositivo al tópico de notificaciones push de un edificio. Las
 * Cloud Functions (closeExpiredVotes de HU-02, sendDueDateReminders de HU-09) ya le
 * mandan notificaciones al tópico "building_{buildingId}" (ver functions/index.js);
 * esto es lo que faltaba del lado de la app para que alguien las reciba sin tener que
 * tener la app abierta en ese momento.
 */
interface NotificationRepository {
    suspend fun subscribeToBuilding(buildingId: String)
    suspend fun unsubscribeFromBuilding(buildingId: String)
}
