package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Wrapper de Firebase Auth + el documento de perfil en Firestore ("users/{uid}").
 * Auth guarda únicamente credenciales; el rol y los datos del consorcio viven en Firestore.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    /**
     * Crea la cuenta de Auth y su perfil en Firestore. El rol por defecto es INQUILINO;
     * subir a ADMINISTRADOR o ENCARGADO es una acción que hace otro Administrador
     * (no algo que uno se auto-asigne en el alta).
     */
    suspend fun register(fullName: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("No se pudo crear el usuario")
        val profile = User(
            uid = uid,
            fullName = fullName,
            email = email,
            role = Role.INQUILINO.name
        )
        db.collection("users").document(uid).set(profile).await()
    }

    suspend fun fetchCurrentUserProfile(): User? {
        val uid = currentUid ?: return null
        val snapshot = db.collection("users").document(uid).get().await()
        return snapshot.toObject(User::class.java)
    }

    fun logout() = auth.signOut()
}
