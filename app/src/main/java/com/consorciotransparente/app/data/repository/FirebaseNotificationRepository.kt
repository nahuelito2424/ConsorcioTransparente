package com.consorciotransparente.app.data.repository

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class FirebaseNotificationRepository(
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) : NotificationRepository {
    // El mismo prefijo "building_" que usa functions/index.js para publicar.
    private fun topicFor(buildingId: String) = "building_$buildingId"

    override suspend fun subscribeToBuilding(buildingId: String) {
        if (buildingId.isBlank()) return
        messaging.subscribeToTopic(topicFor(buildingId)).await()
    }

    override suspend fun unsubscribeFromBuilding(buildingId: String) {
        if (buildingId.isBlank()) return
        messaging.unsubscribeFromTopic(topicFor(buildingId)).await()
    }
}
