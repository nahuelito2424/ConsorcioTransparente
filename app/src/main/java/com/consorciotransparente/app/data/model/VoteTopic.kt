package com.consorciotransparente.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Documento en "buildings/{buildingId}/votes". Implementa HU-02: el Administrador crea
 * el tema con 2 o más opciones; cada usuario vota una sola vez (subcolección "responses",
 * doc id = uid del votante, para que Firestore rechace un segundo voto por reglas de seguridad).
 */
data class VoteTopic(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val options: List<String> = emptyList(), // mínimo 2, validado antes de publicar
    val closesAt: Date? = null,
    val isClosed: Boolean = false,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

/** Documento en "buildings/{buildingId}/votes/{voteId}/responses", doc id = uid del votante. */
data class VoteResponse(
    val uid: String = "",
    val optionIndex: Int = 0,
    @ServerTimestamp val votedAt: Date? = null
)
