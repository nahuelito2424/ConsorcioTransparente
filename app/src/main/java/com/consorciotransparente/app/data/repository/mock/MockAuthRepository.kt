package com.consorciotransparente.app.data.repository.mock

import com.consorciotransparente.app.data.model.User
import com.consorciotransparente.app.data.repository.AuthRepository

/**
 * Tres credenciales fijas (contraseña libre, no se valida) para poder probar los tres
 * roles sin un proyecto Firebase real. Ver MockDataStore para los emails exactos.
 */
class MockAuthRepository : AuthRepository {
    override val currentUid: String? get() = MockDataStore.currentUser?.uid

    override suspend fun login(email: String, password: String) {
        val user = MockDataStore.findUserByEmail(email) ?: error(
            "Usuario mock no reconocido. Usá ${MockDataStore.adminUser.email}, " +
                "${MockDataStore.encargadoUser.email} o ${MockDataStore.inquilinoUser.email} " +
                "(cualquier contraseña)."
        )
        MockDataStore.currentUser = user
    }

    override suspend fun register(fullName: String, email: String, password: String) {
        error("El alta de usuarios no está disponible en el flavor mock; usá una de las 3 credenciales fijas.")
    }

    override suspend fun fetchCurrentUserProfile(): User? = MockDataStore.currentUser

    override fun logout() {
        MockDataStore.currentUser = null
    }
}
