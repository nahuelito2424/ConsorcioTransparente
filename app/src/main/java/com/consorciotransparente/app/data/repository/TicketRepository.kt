package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Ticket

/**
 * Implementa HU-10 (Gestión de Incidentes de Mantenimiento con Presupuesto y Pago).
 * El ciclo de vida del ticket es REPORTADO -> EN_PRESUPUESTO -> A_COBRAR -> RESUELTO.
 *
 * El Escenario 3 ("generar un cobro a través de Mercado Pago") tiene dos caminos a
 * A_COBRAR -> RESUELTO, no uno: [createTicketMercadoPagoPreference] para quien
 * reportó el ticket, y [markPaid] (pago manual) para el Admin/Encargado — que sigue
 * existiendo para reparaciones de áreas comunes que no le corresponden a un vecino
 * puntual, y como respaldo para el propio inquilino. No se reemplazó [markPaid] como
 * se había anticipado originalmente acá: se agregó una alternativa.
 *
 * Interfaz para permitir la implementación mock (ver RepositoryProvider).
 */
interface TicketRepository {
    /** Cola completa del edificio (para Encargado/Administrador). */
    suspend fun listTickets(buildingId: String): List<Ticket>

    /** Sólo mis propios reportes (para el Inquilino). */
    suspend fun listMyTickets(buildingId: String, uid: String): List<Ticket>

    suspend fun getTicket(buildingId: String, ticketId: String): Ticket?

    /** Escenario 1: categoría, descripción y foto opcional del problema. */
    suspend fun createTicket(buildingId: String, category: String, description: String, uid: String, photo: Uri?): String

    /** Escenario 2: presupuesto estimado, o 0 si queda "a cotizar". */
    suspend fun setBudget(buildingId: String, ticketId: String, amount: Double)

    /** Escenario 3: comprobante de compra de materiales y monto real a cobrar. */
    suspend fun uploadPurchaseReceipt(buildingId: String, ticketId: String, amount: Double, receipt: Uri)

    /** Escenario 3/4: comprobante de pago manual (Admin/Encargado, o respaldo del inquilino) y cierre del ticket. */
    suspend fun markPaid(buildingId: String, ticketId: String, receipt: Uri)

    /**
     * Escenario 3: Mercado Pago, sólo para quien reportó el ticket. El Admin/Encargado
     * sigue usando [markPaid] para reparaciones de áreas comunes que no le corresponden
     * a un vecino puntual — ver el comentario de clase.
     */
    suspend fun createTicketMercadoPagoPreference(buildingId: String, ticketId: String): String
}
