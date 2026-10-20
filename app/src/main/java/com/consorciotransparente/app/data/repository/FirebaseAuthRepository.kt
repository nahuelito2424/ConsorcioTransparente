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

    override suspend fun register(fullName: String, email: String, password: String) {
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

    override suspend fun fetchCurrentUserProfile(): User? {
        val uid = currentUid ?: return null
        val snapshot = db.collection("users").document(uid).get().await()
        return snapshot.toObject(User::class.java)
    }

    override fun logout() = auth.signOut()
}
