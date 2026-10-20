package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.VoteResponse
import com.consorciotransparente.app.data.model.VoteTopic
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

class FirebaseVoteRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : VoteRepository {
    private fun votesRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("votes")

    override suspend fun listVotes(buildingId: String): List<VoteTopic> {
        val snapshot = votesRef(buildingId).orderBy("createdAt", Query.Direction.DESCENDING).get().await()
        return snapshot.documents.mapNotNull { it.toObject(VoteTopic::class.java)?.copy(id = it.id) }
    }

    override suspend fun getVote(buildingId: String, voteId: String): VoteTopic? {
        val doc = votesRef(buildingId).document(voteId).get().await()
        return doc.toObject(VoteTopic::class.java)?.copy(id = doc.id)
    }

    override suspend fun getMyResponse(buildingId: String, voteId: String, uid: String): VoteResponse? {
        val doc = votesRef(buildingId).document(voteId).collection("responses").document(uid).get().await()
        return doc.toObject(VoteResponse::class.java)
    }

    override suspend fun countVotes(buildingId: String, voteId: String): Int {
        val snapshot = votesRef(buildingId).document(voteId).collection("responses")
            .count().get(AggregateSource.SERVER).await()
        return snapshot.count.toInt()
    }

    override suspend fun submitVote(buildingId: String, voteId: String, uid: String, optionIndex: Int) {
        val vote = getVote(buildingId, voteId) ?: error("La votación ya no existe.")
        if (isClosed(vote)) error("Esta votación ya cerró.")
        val response = VoteResponse(uid = uid, optionIndex = optionIndex)
        val ref = votesRef(buildingId).document(voteId).collection("responses").document(uid)
        if (ref.get().await().exists()) error("Ya emitiste tu voto en esta convocatoria.")
        ref.set(response).await()
    }

    override suspend fun getResultCounts(buildingId: String, voteId: String, optionCount: Int): IntArray {
        val counts = IntArray(optionCount)
        val responses = votesRef(buildingId).document(voteId).collection("responses").get().await()
        for (doc in responses.documents) {
            val idx = doc.toObject(VoteResponse::class.java)?.optionIndex ?: continue
            if (idx in counts.indices) counts[idx]++
        }
        return counts
    }

    override suspend fun createVote(
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

    override fun isClosed(vote: VoteTopic): Boolean {
        val deadline = vote.closesAt
        return vote.isClosed || (deadline != null && deadline.before(Date()))
    }
}
