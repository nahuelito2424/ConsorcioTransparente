package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Reservation
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseReservationRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ReservationRepository {
    private fun reservationsRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("reservations")

    private fun slotId(spaceName: String, date: String, timeSlot: String): String {
        val safeSpace = spaceName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
        val safeSlot = timeSlot.replace(":", "").replace("-", "_")
        return "${safeSpace}_${date}_$safeSlot"
    }

    override suspend fun listReservationsForDate(buildingId: String, spaceName: String, date: String): List<Reservation> {
        val snapshot = reservationsRef(buildingId)
            .whereEqualTo("spaceName", spaceName)
            .whereEqualTo("date", date)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(Reservation::class.java)?.copy(id = it.id) }
    }

    override suspend fun listMyUpcomingReservations(buildingId: String, uid: String, todayIso: String): List<Reservation> {
        val snapshot = reservationsRef(buildingId).whereEqualTo("reservedByUid", uid).get().await()
        return snapshot.documents
            .mapNotNull { it.toObject(Reservation::class.java)?.copy(id = it.id) }
            .filter { it.date >= todayIso }
            .sortedBy { it.date }
    }

    override suspend fun reserve(buildingId: String, spaceName: String, date: String, timeSlot: String, uid: String, name: String) {
        val id = slotId(spaceName, date, timeSlot)
        val reservation = Reservation(spaceName = spaceName, date = date, timeSlot = timeSlot, reservedByUid = uid, reservedByName = name)
        reservationsRef(buildingId).document(id).set(reservation).await()
    }

    override suspend fun cancel(buildingId: String, reservationId: String) {
        reservationsRef(buildingId).document(reservationId).delete().await()
    }

    override suspend fun changeSlot(
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
