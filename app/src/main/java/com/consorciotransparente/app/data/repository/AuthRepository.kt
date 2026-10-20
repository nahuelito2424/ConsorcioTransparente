package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.User

/**
 * Wrapper de Firebase Auth + el documento de perfil en Firestore ("users/{uid}").
 * Auth guarda únicamente credenciales; el rol y los datos del consorcio viven en Firestore.
 *
 * Interfaz para poder tener una implementación real (FirebaseAuthRepository) y una de
 * prueba (mock.MockAuthRepository) intercambiables vía RepositoryProvider, según el
 * flavor "prod"/"mock" del build.
 */
interface AuthRepository {
    val currentUid: String?

    suspend fun login(email: String, password: String)

    /**
     * Crea la cuenta y su perfil. El rol por defecto es INQUILINO; subir a
     * ADMINISTRADOR o ENCARGADO es una acción que hace otro Administrador
     * (no algo que uno se auto-asigne en el alta).
     */
    suspend fun register(fullName: String, email: String, password: String)

    suspend fun fetchCurrentUserProfile(): User?

    fun logout()
}
