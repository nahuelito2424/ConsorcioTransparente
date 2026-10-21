package com.consorciotransparente.app.data.repository.mock

import com.consorciotransparente.app.data.repository.NotificationRepository

/** El flavor mock no habla con Firebase Cloud Messaging: no hay tópico que suscribir. */
class MockNotificationRepository : NotificationRepository {
    override suspend fun subscribeToBuilding(buildingId: String) {}
    override suspend fun unsubscribeFromBuilding(buildingId: String) {}
    override suspend fun subscribeToBuildingStaff(buildingId: String) {}
    override suspend fun unsubscribeFromBuildingStaff(buildingId: String) {}
    override suspend fun subscribeToUser(uid: String) {}
    override suspend fun unsubscribeFromUser(uid: String) {}
}
