package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Post
import com.consorciotransparente.app.data.model.PostStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Implementa HU-05 (Muro de Publicaciones): toda publicación nace PENDIENTE y sólo
 * se vuelve visible en el muro cuando el Administrador la aprueba (Escenario 3,
 * moderación previa). La regla de seguridad en firestore.rules ya obliga a que un
 * "create" llegue como PENDIENTE y a que sólo el Administrador pueda cambiar el estado.
 */
class MuroRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    private fun postsRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("posts")

    suspend fun listApprovedPosts(buildingId: String): List<Post> {
        val snapshot = postsRef(buildingId)
            .whereEqualTo("status", PostStatus.APROBADO.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(Post::class.java)?.copy(id = it.id) }
    }

    suspend fun listPendingPosts(buildingId: String): List<Post> {
        val snapshot = postsRef(buildingId)
            .whereEqualTo("status", PostStatus.PENDIENTE.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(Post::class.java)?.copy(id = it.id) }
    }

    /** Escenario 1: título, texto e imagen opcional. Nace PENDIENTE (Escenario 3). */
    suspend fun createPost(
        buildingId: String,
        title: String,
        body: String,
        authorUid: String,
        authorName: String,
        image: Uri?
    ) {
        val imageUrl = image?.let { uploadImage(buildingId, it) } ?: ""
        val post = Post(
            title = title,
            body = body,
            imageUrl = imageUrl,
            status = PostStatus.PENDIENTE.name,
            authorUid = authorUid,
            authorName = authorName
        )
        postsRef(buildingId).document().set(post).await()
    }

    /** Sólo el Administrador puede llamar a esto (reforzado también en firestore.rules). */
    suspend fun approvePost(buildingId: String, postId: String) {
        postsRef(buildingId).document(postId).update("status", PostStatus.APROBADO.name).await()
    }

    suspend fun rejectPost(buildingId: String, postId: String) {
        postsRef(buildingId).document(postId).update("status", PostStatus.RECHAZADO.name).await()
    }

    private suspend fun uploadImage(buildingId: String, image: Uri): String {
        val fileName = "posts/$buildingId/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        ref.putFile(image).await()
        return ref.downloadUrl.await().toString()
    }
}
