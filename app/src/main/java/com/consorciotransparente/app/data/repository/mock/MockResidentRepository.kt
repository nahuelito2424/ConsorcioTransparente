package com.consorciotransparente.app.data.repository.mock

import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.model.User
import com.consorciotransparente.app.data.repository.ResidentRepository

class MockResidentRepository : ResidentRepository {
    override suspend fun listResidents(buildingId: String): List<User> =
        MockDataStore.users.filter { it.buildingId == buildingId && it.role == Role.INQUILINO.name }

    override suspend fun updateAmounts(buildingId: String, uid: String, rentAmount: Double, expensesAmount: Double) {
        MockDataStore.updateUserAmounts(uid, rentAmount, expensesAmount)
    }
}
