package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Reservation

/**
 * Implementa HU-06 (Reserva de Espacios Comunes). En la implementación real, el id del
 * documento es "{espacio}_{fecha}_{franja}": esto convierte al propio id en el mecanismo
 * de exclusión mutua (Escenario 1: no permitir reservar una franja ya ocupada), sin
 * necesidad de una transacción ni de tocar firestore.rules.
 *
 * Interfaz para permitir la implementación mock (ver RepositoryProvider).
 */
interface ReservationRepository {
    suspend fun listReservationsForDate(buildingId: String, spaceName: String, date: String): List<Reservation>

    /** Reservas propias con fecha de hoy en adelante (comparación lexicográfica válida en yyyy-MM-dd). */
    suspend fun listMyUpcomingReservations(buildingId: String, uid: String, todayIso: String): List<Reservation>

    /** Escenario 2: si el turno ya estaba tomado, esta llamada tira una excepción. */
    suspend fun reserve(buildingId: String, spaceName: String, date: String, timeSlot: String, uid: String, name: String)

    /** Escenario 3: cancelar libera la franja. */
    suspend fun cancel(buildingId: String, reservationId: String)

    /** Escenario 3: "editar" = liberar la franja vieja y tomar la nueva (mismo dueño). */
    suspend fun changeSlot(
        buildingId: String,
        oldReservationId: String,
        spaceName: String,
        newDate: String,
        newTimeSlot: String,
        uid: String,
        name: String
    )
}
