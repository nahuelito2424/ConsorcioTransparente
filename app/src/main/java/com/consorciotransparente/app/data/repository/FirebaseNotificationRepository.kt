package com.consorciotransparente.app.data.repository

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class FirebaseNotificationRepository(
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) : NotificationRepository {
    // Mismos nombres de tópico que publica functions/index.js.
    private fun buildingTopic(buildingId: String) = "building_$buildingId"
    private fun staffTopic(buildingId: String) = "building_${buildingId}_staff"
    private fun userTopic(uid: String) = "user_$uid"

    override suspend fun subscribeToBuilding(buildingId: String) {
        if (buildingId.isBlank()) return
        subscribe(buildingTopic(buildingId))
    }

    override suspend fun unsubscribeFromBuilding(buildingId: String) {
        if (buildingId.isBlank()) return
        unsubscribe(buildingTopic(buildingId))
    }

    override suspend fun subscribeToBuildingStaff(buildingId: String) {
        if (buildingId.isBlank()) return
        subscribe(staffTopic(buildingId))
    }

    override suspend fun unsubscribeFromBuildingStaff(buildingId: String) {
        if (buildingId.isBlank()) return
        unsubscribe(staffTopic(buildingId))
    }

    override suspend fun subscribeToUser(uid: String) {
        if (uid.isBlank()) return
        subscribe(userTopic(uid))
    }

    override suspend fun unsubscribeFromUser(uid: String) {
        if (uid.isBlank()) return
        unsubscribe(userTopic(uid))
    }

    private suspend fun subscribe(topic: String) {
        messaging.subscribeToTopic(topic).await()
    }

    private suspend fun unsubscribe(topic: String) {
        messaging.unsubscribeFromTopic(topic).await()
    }
}
