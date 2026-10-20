package com.consorciotransparente.app.data.repository.mock

import com.consorciotransparente.app.data.model.Building
import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.repository.BuildingRepository

class MockBuildingRepository : BuildingRepository {
    override suspend fun getBuilding(buildingId: String): Building? =
        MockDataStore.building.takeIf { it.id == buildingId }

    override suspend fun createBuilding(building: Building, adminUid: String): String {
        val toSave = building.copy(id = MockDataStore.BUILDING_ID, adminUid = adminUid)
        MockDataStore.building = toSave
        MockDataStore.currentUser = MockDataStore.currentUser?.copy(
            buildingId = MockDataStore.BUILDING_ID,
            role = Role.ADMINISTRADOR.name
        )
        return MockDataStore.BUILDING_ID
    }

    override suspend fun updateBuilding(buildingId: String, building: Building) {
        MockDataStore.building = building.copy(id = buildingId)
    }
}
