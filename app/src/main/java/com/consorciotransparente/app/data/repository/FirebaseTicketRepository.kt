package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Ticket
import com.consorciotransparente.app.data.model.TicketStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseTicketRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : TicketRepository {
    private fun ticketsRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("tickets")

    override suspend fun listTickets(buildingId: String): List<Ticket> {
        val snapshot = ticketsRef(buildingId).orderBy("createdAt", Query.Direction.DESCENDING).get().await()
        return snapshot.documents.mapNotNull { it.toObject(Ticket::class.java)?.copy(id = it.id) }
    }

    override suspend fun listMyTickets(buildingId: String, uid: String): List<Ticket> {
        val snapshot = ticketsRef(buildingId).whereEqualTo("reportedByUid", uid).get().await()
        return snapshot.documents
            .mapNotNull { it.toObject(Ticket::class.java)?.copy(id = it.id) }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun getTicket(buildingId: String, ticketId: String): Ticket? {
        val doc = ticketsRef(buildingId).document(ticketId).get().await()
        return doc.toObject(Ticket::class.java)?.copy(id = doc.id)
    }

    override suspend fun createTicket(buildingId: String, category: String, description: String, uid: String, photo: Uri?): String {
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

    override suspend fun setBudget(buildingId: String, ticketId: String, amount: Double) {
        ticketsRef(buildingId).document(ticketId).update(
            mapOf("budgetAmount" to amount, "status" to TicketStatus.EN_PRESUPUESTO.name)
        ).await()
    }

    override suspend fun uploadPurchaseReceipt(buildingId: String, ticketId: String, amount: Double, receipt: Uri) {
        val url = uploadImage(buildingId, "purchase", receipt)
        ticketsRef(buildingId).document(ticketId).update(
            mapOf(
                "purchaseReceiptUrl" to url,
                "budgetAmount" to amount,
                "status" to TicketStatus.A_COBRAR.name
            )
        ).await()
    }

    override suspend fun markPaid(buildingId: String, ticketId: String, receipt: Uri) {
        val url = uploadImage(buildingId, "payment", receipt)
        ticketsRef(buildingId).document(ticketId).update(
            mapOf("paymentReceiptUrl" to url, "status" to TicketStatus.RESUELTO.name)
        ).await()
    }

    override suspend fun createTicketMercadoPagoPreference(buildingId: String, ticketId: String): String {
        val result = functions.getHttpsCallable("createTicketMercadoPagoPreference")
            .call(mapOf("buildingId" to buildingId, "ticketId" to ticketId))
            .await()
        val data = result.getData() as? Map<*, *>
        return data?.get("initPoint") as? String ?: error("Mercado Pago no devolvió una URL de pago.")
    }

    private suspend fun uploadImage(buildingId: String, kind: String, image: Uri): String {
        val fileName = "tickets/$buildingId/${kind}_${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        ref.putFile(image).await()
        return ref.downloadUrl.await().toString()
    }
}
