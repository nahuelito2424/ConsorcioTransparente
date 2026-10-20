package com.consorciotransparente.app.data.repository.mock

import android.net.Uri
import com.consorciotransparente.app.data.model.Post
import com.consorciotransparente.app.data.model.PostStatus
import com.consorciotransparente.app.data.repository.MuroRepository
import java.util.Date

class MockMuroRepository : MuroRepository {
    override suspend fun listApprovedPosts(buildingId: String): List<Post> =
        MockDataStore.posts.filter { it.status == PostStatus.APROBADO.name }.sortedByDescending { it.createdAt }

    override suspend fun listPendingPosts(buildingId: String): List<Post> =
        MockDataStore.posts.filter { it.status == PostStatus.PENDIENTE.name }.sortedByDescending { it.createdAt }

    override suspend fun createPost(
        buildingId: String,
        title: String,
        body: String,
        authorUid: String,
        authorName: String,
        image: Uri?
    ) {
        MockDataStore.posts.add(
            0,
            Post(
                id = MockDataStore.newId("post"),
                title = title,
                body = body,
                imageUrl = image?.toString() ?: "",
                status = PostStatus.PENDIENTE.name,
                authorUid = authorUid,
                authorName = authorName,
                createdAt = Date()
            )
        )
    }

    override suspend fun approvePost(buildingId: String, postId: String) {
        updateStatus(postId, PostStatus.APROBADO.name)
    }

    override suspend fun rejectPost(buildingId: String, postId: String) {
        updateStatus(postId, PostStatus.RECHAZADO.name)
    }

    private fun updateStatus(postId: String, status: String) {
        val index = MockDataStore.posts.indexOfFirst { it.id == postId }
        if (index >= 0) MockDataStore.posts[index] = MockDataStore.posts[index].copy(status = status)
    }
}
