package com.consorciotransparente.app.data.repository.mock

import com.consorciotransparente.app.data.model.Reservation
import com.consorciotransparente.app.data.repository.ReservationRepository

class MockReservationRepository : ReservationRepository {
    private fun slotId(spaceName: String, date: String, timeSlot: String): String {
        val safeSpace = spaceName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
        val safeSlot = timeSlot.replace(":", "").replace("-", "_")
        return "${safeSpace}_${date}_$safeSlot"
    }

    override suspend fun listReservationsForDate(buildingId: String, spaceName: String, date: String): List<Reservation> =
        MockDataStore.reservations.filter { it.spaceName == spaceName && it.date == date }

    override suspend fun listMyUpcomingReservations(buildingId: String, uid: String, todayIso: String): List<Reservation> =
        MockDataStore.reservations
            .filter { it.reservedByUid == uid && it.date >= todayIso }
            .sortedBy { it.date }

    override suspend fun reserve(buildingId: String, spaceName: String, date: String, timeSlot: String, uid: String, name: String) {
        val id = slotId(spaceName, date, timeSlot)
        if (MockDataStore.reservations.any { it.id == id }) {
            error("Ese turno ya está reservado.")
        }
        MockDataStore.reservations.add(
            Reservation(id = id, spaceName = spaceName, date = date, timeSlot = timeSlot, reservedByUid = uid, reservedByName = name)
        )
    }

    override suspend fun cancel(buildingId: String, reservationId: String) {
        MockDataStore.reservations.removeAll { it.id == reservationId }
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
