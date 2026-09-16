package com.consorciotransparente.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class PostStatus { PENDIENTE, APROBADO, RECHAZADO }

/**
 * Documento en "buildings/{buildingId}/posts". Implementa HU-05: toda publicación nace
 * en PENDIENTE y sólo es visible en el muro cuando el Administrador la pasa a APROBADO
 * (moderación previa, no posterior).
 */
data class Post(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val status: String = PostStatus.PENDIENTE.name,
    val authorUid: String = "",
    val authorName: String = "",
    @ServerTimestamp val createdAt: Date? = null
)
