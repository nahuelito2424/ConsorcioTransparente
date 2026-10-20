package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.VoteResponse
import com.consorciotransparente.app.data.model.VoteTopic
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
 *
 * Interfaz para permitir la implementación mock (ver RepositoryProvider).
 */
interface VoteRepository {
    suspend fun listVotes(buildingId: String): List<VoteTopic>

    suspend fun getVote(buildingId: String, voteId: String): VoteTopic?

    suspend fun getMyResponse(buildingId: String, voteId: String, uid: String): VoteResponse?

    /** Total de votos emitidos (conteo agregado del lado del servidor, sin bajar cada documento). */
    suspend fun countVotes(buildingId: String, voteId: String): Int

    /** Lanza una excepción si el usuario ya votó (el doc "uid" ya existe) o si la votación está cerrada. */
    suspend fun submitVote(buildingId: String, voteId: String, uid: String, optionIndex: Int)

    /** Cuenta de votos por índice de opción (Escenario 3: transparencia de resultados). */
    suspend fun getResultCounts(buildingId: String, voteId: String, optionCount: Int): IntArray

    /** HU-02 Escenario 4: el Administrador crea el tema con 2 o más opciones. */
    suspend fun createVote(
        buildingId: String,
        title: String,
        description: String,
        options: List<String>,
        closesAt: Date,
        createdByUid: String
    )

    fun isClosed(vote: VoteTopic): Boolean
}
