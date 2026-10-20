package com.consorciotransparente.app.data.repository.mock

import com.consorciotransparente.app.data.model.VoteResponse
import com.consorciotransparente.app.data.model.VoteTopic
import com.consorciotransparente.app.data.repository.VoteRepository
import java.util.Date

class MockVoteRepository : VoteRepository {
    override suspend fun listVotes(buildingId: String): List<VoteTopic> =
        MockDataStore.votes.sortedByDescending { it.createdAt }

    override suspend fun getVote(buildingId: String, voteId: String): VoteTopic? =
        MockDataStore.votes.firstOrNull { it.id == voteId }

    override suspend fun getMyResponse(buildingId: String, voteId: String, uid: String): VoteResponse? =
        MockDataStore.voteResponses[voteId]?.get(uid)

    override suspend fun countVotes(buildingId: String, voteId: String): Int =
        MockDataStore.voteResponses[voteId]?.size ?: 0

    override suspend fun submitVote(buildingId: String, voteId: String, uid: String, optionIndex: Int) {
        val vote = getVote(buildingId, voteId) ?: error("La votación ya no existe.")
        if (isClosed(vote)) error("Esta votación ya cerró.")
        val responses = MockDataStore.voteResponses.getOrPut(voteId) { mutableMapOf() }
        if (responses.containsKey(uid)) error("Ya emitiste tu voto en esta convocatoria.")
        responses[uid] = VoteResponse(uid = uid, optionIndex = optionIndex, votedAt = Date())
    }

    override suspend fun getResultCounts(buildingId: String, voteId: String, optionCount: Int): IntArray {
        val counts = IntArray(optionCount)
        MockDataStore.voteResponses[voteId]?.values?.forEach { response ->
            if (response.optionIndex in counts.indices) counts[response.optionIndex]++
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
        MockDataStore.votes.add(
            0,
            VoteTopic(
                id = MockDataStore.newId("vote"),
                title = title,
                description = description,
                options = options,
                closesAt = closesAt,
                isClosed = false,
                createdByUid = createdByUid,
                createdAt = Date()
            )
        )
    }

    override fun isClosed(vote: VoteTopic): Boolean {
        val deadline = vote.closesAt
        return vote.isClosed || (deadline != null && deadline.before(Date()))
    }
}
