package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {
    override val currentUid: String? get() = auth.currentUser?.uid

    override suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun register(fullName: String, email: String, password: String, buildingCode: String) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("No se pudo crear el usuario")
        val profile = User(
            uid = uid,
            fullName = fullName,
            email = email,
            role = Role.INQUILINO.name,
            buildingId = buildingCode
        )
        db.collection("users").document(uid).set(profile).await()

        // No se puede validar el código ANTES de crear el perfil: firestore.rules sólo
        // deja leer buildings/{id} a quien ya pertenece a ese edificio (belongsToBuilding),
        // así que recién acá —con buildingId ya puesto— se puede confirmar que existe. Si
        // no existe, se vuelve a dejar el perfil sin edificio (misma regla ya permite que
        // el propio usuario edite su buildingId mientras no toque role/montos).
        if (buildingCode.isNotBlank()) {
            val exists = db.collection("buildings").document(buildingCode).get().await().exists()
            if (!exists) {
                db.collection("users").document(uid).update("buildingId", "").await()
                error("No encontramos un edificio con ese código. Verificalo con tu Administrador.")
            }
        }
    }

    override suspend fun fetchCurrentUserProfile(): User? {
        val uid = currentUid ?: return null
        val snapshot = db.collection("users").document(uid).get().await()
        return snapshot.toObject(User::class.java)
    }

    override fun logout() = auth.signOut()
}
