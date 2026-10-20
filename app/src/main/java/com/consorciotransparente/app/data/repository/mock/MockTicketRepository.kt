package com.consorciotransparente.app.data.repository.mock

import android.net.Uri
import com.consorciotransparente.app.data.model.Ticket
import com.consorciotransparente.app.data.model.TicketStatus
import com.consorciotransparente.app.data.repository.TicketRepository
import java.util.Date

class MockTicketRepository : TicketRepository {
    override suspend fun listTickets(buildingId: String): List<Ticket> =
        MockDataStore.tickets.sortedByDescending { it.createdAt }

    override suspend fun listMyTickets(buildingId: String, uid: String): List<Ticket> =
        MockDataStore.tickets.filter { it.reportedByUid == uid }.sortedByDescending { it.createdAt }

    override suspend fun getTicket(buildingId: String, ticketId: String): Ticket? =
        MockDataStore.tickets.firstOrNull { it.id == ticketId }

    override suspend fun createTicket(buildingId: String, category: String, description: String, uid: String, photo: Uri?): String {
        val ticket = Ticket(
            id = MockDataStore.newId("ticket"),
            category = category,
            description = description,
            photoUrl = photo?.toString() ?: "",
            status = TicketStatus.REPORTADO.name,
            reportedByUid = uid,
            createdAt = Date()
        )
        MockDataStore.tickets.add(0, ticket)
        return ticket.id
    }

    override suspend fun setBudget(buildingId: String, ticketId: String, amount: Double) {
        update(ticketId) { it.copy(budgetAmount = amount, status = TicketStatus.EN_PRESUPUESTO.name) }
    }

    override suspend fun uploadPurchaseReceipt(buildingId: String, ticketId: String, amount: Double, receipt: Uri) {
        update(ticketId) {
            it.copy(purchaseReceiptUrl = receipt.toString(), budgetAmount = amount, status = TicketStatus.A_COBRAR.name)
        }
    }

    override suspend fun markPaid(buildingId: String, ticketId: String, receipt: Uri) {
        update(ticketId) { it.copy(paymentReceiptUrl = receipt.toString(), status = TicketStatus.RESUELTO.name) }
    }

    private fun update(ticketId: String, transform: (Ticket) -> Ticket) {
        val index = MockDataStore.tickets.indexOfFirst { it.id == ticketId }
        if (index >= 0) MockDataStore.tickets[index] = transform(MockDataStore.tickets[index])
    }
}
