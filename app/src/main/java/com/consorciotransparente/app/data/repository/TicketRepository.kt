package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Ticket
import com.consorciotransparente.app.data.model.TicketStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

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
 */
class TicketRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    private fun ticketsRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("tickets")

    /** Cola completa del edificio (para Encargado/Administrador). */
    suspend fun listTickets(buildingId: String): List<Ticket> {
        val snapshot = ticketsRef(buildingId).orderBy("createdAt", Query.Direction.DESCENDING).get().await()
        return snapshot.documents.mapNotNull { it.toObject(Ticket::class.java)?.copy(id = it.id) }
    }

    /** Sólo mis propios reportes (para el Inquilino). */
    suspend fun listMyTickets(buildingId: String, uid: String): List<Ticket> {
        val snapshot = ticketsRef(buildingId).whereEqualTo("reportedByUid", uid).get().await()
        return snapshot.documents
            .mapNotNull { it.toObject(Ticket::class.java)?.copy(id = it.id) }
            .sortedByDescending { it.createdAt }
    }

    suspend fun getTicket(buildingId: String, ticketId: String): Ticket? {
        val doc = ticketsRef(buildingId).document(ticketId).get().await()
        return doc.toObject(Ticket::class.java)?.copy(id = doc.id)
    }

    /** Escenario 1: categoría, descripción y foto opcional del problema. */
    suspend fun createTicket(buildingId: String, category: String, description: String, uid: String, photo: Uri?): String {
        val photoUrl = photo?.let { uploadImage(buildingId, "problem", it) } ?: ""
        val ticket = Ticket(
            category = category,
            description = description,
            photoUrl = photoUrl,
            status = TicketStatus.REPORTADO.name,
            reportedByUid = uid
        )
        val ref = ticketsRef(buildingId).document()
        ref.set(ticket).await()
        return ref.id
    }

    /** Escenario 2: presupuesto estimado, o 0 si queda "a cotizar". */
    suspend fun setBudget(buildingId: String, ticketId: String, amount: Double) {
        ticketsRef(buildingId).document(ticketId).update(
            mapOf("budgetAmount" to amount, "status" to TicketStatus.EN_PRESUPUESTO.name)
        ).await()
    }

    /** Escenario 3: comprobante de compra de materiales y monto real a cobrar. */
    suspend fun uploadPurchaseReceipt(buildingId: String, ticketId: String, amount: Double, receipt: Uri) {
        val url = uploadImage(buildingId, "purchase", receipt)
        ticketsRef(buildingId).document(ticketId).update(
            mapOf(
                "purchaseReceiptUrl" to url,
                "budgetAmount" to amount,
                "status" to TicketStatus.A_COBRAR.name
            )
        ).await()
    }

    /** Escenario 3/4: comprobante de pago (manual por ahora, ver comentario de clase) y cierre del ticket. */
    suspend fun markPaid(buildingId: String, ticketId: String, receipt: Uri) {
        val url = uploadImage(buildingId, "payment", receipt)
        ticketsRef(buildingId).document(ticketId).update(
            mapOf("paymentReceiptUrl" to url, "status" to TicketStatus.RESUELTO.name)
        ).await()
    }

    private suspend fun uploadImage(buildingId: String, kind: String, image: Uri): String {
        val fileName = "tickets/$buildingId/${kind}_${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        ref.putFile(image).await()
        return ref.downloadUrl.await().toString()
    }
}
