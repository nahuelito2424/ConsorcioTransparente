package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.VoteResponse
import com.consorciotransparente.app.data.model.VoteTopic
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Implementa HU-02 (Votaciones Vinculantes). La unicidad del voto (Escenario 1) se
 * garantiza usando el uid del votante como id del documento en "responses": un
 * segundo intento de voto es un create sobre un documento que ya existe y Firestore
 * (+ la regla de seguridad) lo rechaza.
 *
 * El cierre automático (Escenario 2) se resuelve en dos capas: acá, `isEffectivelyClosed`
 * lo calcula el cliente comparando con la fecha; para el cierre "de verdad" del lado del
 * servidor y el envío de la notificación push, ver functions/index.js (Cloud Function
 * programada, a desplegar más adelante).
 */
class VoteRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun votesRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("votes")

    suspend fun listVotes(buildingId: String): List<VoteTopic> {
        val snapshot = votesRef(buildingId).orderBy("createdAt", Query.Direction.DESCENDING).get().await()
        return snapshot.documents.mapNotNull { it.toObject(VoteTopic::class.java)?.copy(id = it.id) }
    }

    suspend fun getVote(buildingId: String, voteId: String): VoteTopic? {
        val doc = votesRef(buildingId).document(voteId).get().await()
        return doc.toObject(VoteTopic::class.java)?.copy(id = doc.id)
    }

    suspend fun getMyResponse(buildingId: String, voteId: String, uid: String): VoteResponse? {
        val doc = votesRef(buildingId).document(voteId).collection("responses").document(uid).get().await()
        return doc.toObject(VoteResponse::class.java)
    }

    /** Total de votos emitidos (conteo agregado del lado del servidor, sin bajar cada documento). */
    suspend fun countVotes(buildingId: String, voteId: String): Int {
        val snapshot = votesRef(buildingId).document(voteId).collection("responses")
            .count().get(AggregateSource.SERVER).await()
        return snapshot.count.toInt()
    }

    /** Lanza una excepción si el usuario ya votó (el doc "uid" ya existe) o si la votación está cerrada. */
    suspend fun submitVote(buildingId: String, voteId: String, uid: String, optionIndex: Int) {
        val vote = getVote(buildingId, voteId) ?: error("La votación ya no existe.")
        if (isClosed(vote)) error("Esta votación ya cerró.")
        val response = VoteResponse(uid = uid, optionIndex = optionIndex)
        val ref = votesRef(buildingId).document(voteId).collection("responses").document(uid)
        if (ref.get().await().exists()) error("Ya emitiste tu voto en esta convocatoria.")
        ref.set(response).await()
    }

    /** Cuenta de votos por índice de opción (Escenario 3: transparencia de resultados). */
    suspend fun getResultCounts(buildingId: String, voteId: String, optionCount: Int): IntArray {
        val counts = IntArray(optionCount)
        val responses = votesRef(buildingId).document(voteId).collection("responses").get().await()
        for (doc in responses.documents) {
            val idx = doc.toObject(VoteResponse::class.java)?.optionIndex ?: continue
            if (idx in counts.indices) counts[idx]++
        }
        return counts
    }

    /** HU-02 Escenario 4: el Administrador crea el tema con 2 o más opciones. */
    suspend fun createVote(
        buildingId: String,
        title: String,
        description: String,
        options: List<String>,
        closesAt: Date,
        createdByUid: String
    ) {
        require(options.size >= 2) { "Una votación necesita al menos 2 opciones." }
        val vote = VoteTopic(
            title = title,
            description = description,
            options = options,
            closesAt = closesAt,
            isClosed = false,
            createdByUid = createdByUid
        )
        votesRef(buildingId).document().set(vote).await()
    }

    fun isClosed(vote: VoteTopic): Boolean {
        val deadline = vote.closesAt
        return vote.isClosed || (deadline != null && deadline.before(Date()))
    }
}
