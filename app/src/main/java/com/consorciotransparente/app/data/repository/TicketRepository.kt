package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Ticket

/**
 * Implementa HU-10 (Gestión de Incidentes de Mantenimiento con Presupuesto y Pago).
 * El ciclo de vida del ticket es REPORTADO -> EN_PRESUPUESTO -> A_COBRAR -> RESUELTO.
 *
 * El Escenario 3 de la historia pide "generar un cobro a través de Mercado Pago";
 * esa integración es HU-04 y todavía no está hecha (ver README). Para no bloquear
 * el resto del flujo, A_COBRAR hoy se salda subiendo un comprobante de pago manual
 * (transferencia, efectivo, etc.); el día que se integre Mercado Pago, alcanza con
 * reemplazar [markPaid] por la confirmación del webhook de pago, sin tocar el resto
 * del ciclo de vida ni la UI de las otras pantallas.
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

    /** Escenario 3/4: comprobante de pago (manual por ahora, ver comentario de clase) y cierre del ticket. */
    suspend fun markPaid(buildingId: String, ticketId: String, receipt: Uri)
}
