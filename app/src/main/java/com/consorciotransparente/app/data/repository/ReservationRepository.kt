package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Reservation
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Implementa HU-06 (Reserva de Espacios Comunes). En vez de dejar que Firestore
 * genere un id al azar, el id del documento es "{espacio}_{fecha}_{franja}"
 * (ver [slotId]). Esto convierte al propio id en el mecanismo de exclusión
 * mutua: si el turno ya está tomado, el intento de otro usuario se evalúa como
 * un "update" contra la regla de seguridad (que exige ser el dueño de la
 * reserva o Administrador) y Firestore lo rechaza — sin necesidad de una
 * transacción ni de tocar firestore.rules (Escenario 1: no permitir reservar
 * una franja ya ocupada).
 */
class ReservationRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun reservationsRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("reservations")

    private fun slotId(spaceName: String, date: String, timeSlot: String): String {
        val safeSpace = spaceName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
        val safeSlot = timeSlot.replace(":", "").replace("-", "_")
        return "${safeSpace}_${date}_$safeSlot"
    }

    suspend fun listReservationsForDate(buildingId: String, spaceName: String, date: String): List<Reservation> {
        val snapshot = reservationsRef(buildingId)
            .whereEqualTo("spaceName", spaceName)
            .whereEqualTo("date", date)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(Reservation::class.java)?.copy(id = it.id) }
    }

    /** Reservas propias con fecha de hoy en adelante (comparación lexicográfica válida en yyyy-MM-dd). */
    suspend fun listMyUpcomingReservations(buildingId: String, uid: String, todayIso: String): List<Reservation> {
        val snapshot = reservationsRef(buildingId).whereEqualTo("reservedByUid", uid).get().await()
        return snapshot.documents
            .mapNotNull { it.toObject(Reservation::class.java)?.copy(id = it.id) }
            .filter { it.date >= todayIso }
            .sortedBy { it.date }
    }

    /** Escenario 2: si el turno ya estaba tomado, esta llamada tira una excepción (permission-denied). */
    suspend fun reserve(buildingId: String, spaceName: String, date: String, timeSlot: String, uid: String, name: String) {
        val id = slotId(spaceName, date, timeSlot)
        val reservation = Reservation(spaceName = spaceName, date = date, timeSlot = timeSlot, reservedByUid = uid, reservedByName = name)
        reservationsRef(buildingId).document(id).set(reservation).await()
    }

    /** Escenario 3: cancelar libera la franja (se borra el documento con ese id). */
    suspend fun cancel(buildingId: String, reservationId: String) {
        reservationsRef(buildingId).document(reservationId).delete().await()
    }

    /** Escenario 3: "editar" = liberar la franja vieja y tomar la nueva (mismo dueño). */
    suspend fun changeSlot(
        buildingId: String,
        oldReservationId: String,
        spaceName: String,
        newDate: String,
        newTimeSlot: String,
        uid: String,
        name: String
    ) {
        reserve(buildingId, spaceName, newDate, newTimeSlot, uid, name)
        cancel(buildingId, oldReservationId)
    }
}
