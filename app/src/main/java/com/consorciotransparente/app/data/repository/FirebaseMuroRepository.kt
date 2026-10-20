package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Post
import com.consorciotransparente.app.data.model.PostStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseMuroRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : MuroRepository {
    private fun postsRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("posts")

    override suspend fun listApprovedPosts(buildingId: String): List<Post> {
        val snapshot = postsRef(buildingId)
            .whereEqualTo("status", PostStatus.APROBADO.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(Post::class.java)?.copy(id = it.id) }
    }

    override suspend fun listPendingPosts(buildingId: String): List<Post> {
        val snapshot = postsRef(buildingId)
            .whereEqualTo("status", PostStatus.PENDIENTE.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(Post::class.java)?.copy(id = it.id) }
    }

    override suspend fun createPost(
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

    override suspend fun approvePost(buildingId: String, postId: String) {
        postsRef(buildingId).document(postId).update("status", PostStatus.APROBADO.name).await()
    }

    override suspend fun rejectPost(buildingId: String, postId: String) {
        postsRef(buildingId).document(postId).update("status", PostStatus.RECHAZADO.name).await()
    }

    private suspend fun uploadImage(buildingId: String, image: Uri): String {
        val fileName = "posts/$buildingId/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        ref.putFile(image).await()
        return ref.downloadUrl.await().toString()
    }
}
