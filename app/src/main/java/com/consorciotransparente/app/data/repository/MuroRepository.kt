package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Post

/**
 * Implementa HU-05 (Muro de Publicaciones): toda publicación nace PENDIENTE y sólo
 * se vuelve visible en el muro cuando el Administrador la aprueba (Escenario 3,
 * moderación previa). La regla de seguridad en firestore.rules ya obliga a que un
 * "create" llegue como PENDIENTE y a que sólo el Administrador pueda cambiar el estado.
 *
 * Interfaz para permitir la implementación mock (ver RepositoryProvider).
 */
interface MuroRepository {
    suspend fun listApprovedPosts(buildingId: String): List<Post>

    suspend fun listPendingPosts(buildingId: String): List<Post>

    /** Escenario 1: título, texto e imagen opcional. Nace PENDIENTE (Escenario 3). */
    suspend fun createPost(
        buildingId: String,
        title: String,
        body: String,
        authorUid: String,
        authorName: String,
        image: Uri?
    )

    /** Sólo el Administrador puede llamar a esto (reforzado también en firestore.rules). */
    suspend fun approvePost(buildingId: String, postId: String)

    suspend fun rejectPost(buildingId: String, postId: String)
}
