package com.consorciotransparente.app.data.repository.mock

import com.consorciotransparente.app.data.model.Building
import com.consorciotransparente.app.data.model.Expense
import com.consorciotransparente.app.data.model.ExpenseCategory
import com.consorciotransparente.app.data.model.ExpenseStatus
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.model.PaymentMethod
import com.consorciotransparente.app.data.model.PaymentStatus
import com.consorciotransparente.app.data.model.Post
import com.consorciotransparente.app.data.model.PostStatus
import com.consorciotransparente.app.data.model.Provider
import com.consorciotransparente.app.data.model.ProviderReview
import com.consorciotransparente.app.data.model.Reservation
import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.model.Ticket
import com.consorciotransparente.app.data.model.TicketStatus
import com.consorciotransparente.app.data.model.User
import com.consorciotransparente.app.data.model.VoteResponse
import com.consorciotransparente.app.data.model.VoteTopic
import com.consorciotransparente.app.util.currentPeriod
import com.consorciotransparente.app.util.shiftPeriod
import java.util.Date
import java.util.UUID

/**
 * Estado en memoria compartido por todos los MockXRepository del flavor "mock": permite
 * probar la app entera sin un proyecto Firebase real. Un solo edificio de ejemplo,
 * compartido por los tres usuarios fijos (ver MockAuthRepository), para poder probar
 * interacciones entre roles en la misma sesión (ej. el inquilino reporta un ticket, el
 * administrador le pone presupuesto). No persiste a disco: se reinicia con el proceso.
 */
object MockDataStore {
    const val BUILDING_ID = "mock-building"

    val adminUser = User(
        uid = "mock-admin-uid",
        fullName = "Ana Administradora",
        email = "admin@mock.com",
        role = Role.ADMINISTRADOR.name,
        buildingId = BUILDING_ID
    )
    val encargadoUser = User(
        uid = "mock-encargado-uid",
        fullName = "Carlos Encargado",
        email = "encargado@mock.com",
        role = Role.ENCARGADO.name,
        buildingId = BUILDING_ID
    )
    val inquilinoUser = User(
        uid = "mock-inquilino-uid",
        fullName = "Inés Inquilina",
        email = "inquilino@mock.com",
        role = Role.INQUILINO.name,
        buildingId = BUILDING_ID,
        unitId = "4B"
    )

    val users = mutableListOf(adminUser, encargadoUser, inquilinoUser)

    var currentUser: User? = null

    fun updateUserAmounts(uid: String, rentAmount: Double, expensesAmount: Double) {
        val index = users.indexOfFirst { it.uid == uid }
        if (index < 0) return
        val updated = users[index].copy(rentAmount = rentAmount, expensesAmount = expensesAmount)
        users[index] = updated
        if (currentUser?.uid == uid) currentUser = updated
    }

    var building = Building(
        id = BUILDING_ID,
        name = "Edificio de prueba (mock)",
        address = "Av. Siempre Viva 742, CABA",
        chargesRent = false,
        chargesExpenses = true,
        expensesDueDay = 10,
        rentDueDay = 10,
        reminderDaysBefore = 5,
        hasEncargado = true,
        amenities = listOf("SUM", "Parrilla"),
        adminUid = adminUser.uid
    )

    val expenses = mutableListOf(
        Expense(
            id = "exp-1", description = "Reparación de bomba de agua",
            category = ExpenseCategory.PLOMERIA.name, providerName = "Plomería del Sur",
            amount = 45000.0, receiptUrl = "mock://receipt-1", status = ExpenseStatus.VERIFICADO.name,
            createdByUid = adminUser.uid, createdAt = Date()
        ),
        Expense(
            id = "exp-2", description = "Foco de palier quemado",
            category = ExpenseCategory.ELECTRICIDAD.name, providerName = "",
            amount = 3000.0, receiptUrl = "", status = ExpenseStatus.BLOQUEADO.name,
            createdByUid = encargadoUser.uid, createdAt = Date()
        )
    )

    val posts = mutableListOf(
        Post(
            id = "post-1", title = "Corte de agua programado",
            body = "El jueves de 9 a 13hs por mantenimiento de tanque.",
            status = PostStatus.APROBADO.name, authorUid = adminUser.uid,
            authorName = adminUser.fullName, createdAt = Date()
        ),
        Post(
            id = "post-2", title = "¿Alguien vio mi bici?",
            body = "Estaba en el palier del 2do piso.",
            status = PostStatus.PENDIENTE.name, authorUid = inquilinoUser.uid,
            authorName = inquilinoUser.fullName, createdAt = Date()
        )
    )

    val votes = mutableListOf(
        VoteTopic(
            id = "vote-1", title = "Pintura de fachada",
            description = "Elegir el color de la nueva pintura.",
            options = listOf("Gris", "Beige", "Blanco"),
            closesAt = Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000),
            isClosed = false, createdByUid = adminUser.uid, createdAt = Date()
        )
    )

    /** voteId -> uid del votante -> su respuesta. */
    val voteResponses = mutableMapOf<String, MutableMap<String, VoteResponse>>()

    val reservations = mutableListOf(
        Reservation(
            id = "sum_2026-09-20_1800_2200", spaceName = "SUM", date = "2026-09-20",
            timeSlot = "18:00-22:00", reservedByUid = inquilinoUser.uid,
            reservedByName = inquilinoUser.fullName
        )
    )

    val tickets = mutableListOf(
        Ticket(
            id = "ticket-1", category = "Plomería",
            description = "Pérdida de agua en el baño del SUM",
            status = TicketStatus.REPORTADO.name, reportedByUid = inquilinoUser.uid, createdAt = Date()
        ),
        Ticket(
            id = "ticket-2", category = "Electricidad",
            description = "Portero eléctrico sin sonido",
            status = TicketStatus.EN_PRESUPUESTO.name, budgetAmount = 12000.0,
            reportedByUid = inquilinoUser.uid, createdAt = Date()
        )
    )

    val payments = mutableListOf(
        Payment(
            id = "${inquilinoUser.uid}_${shiftPeriod(currentPeriod(), -1)}",
            uid = inquilinoUser.uid,
            period = shiftPeriod(currentPeriod(), -1),
            amount = 50000.0,
            method = PaymentMethod.TRANSFERENCIA.name,
            status = PaymentStatus.PAGADO.name,
            receiptUrl = "mock://receipt-historial-pagado",
            createdAt = Date(),
            verifiedAt = Date(),
            verifiedByUid = adminUser.uid
        ),
        Payment(
            id = "${inquilinoUser.uid}_${shiftPeriod(currentPeriod(), -3)}",
            uid = inquilinoUser.uid,
            period = shiftPeriod(currentPeriod(), -3),
            amount = 48000.0,
            method = PaymentMethod.TRANSFERENCIA.name,
            status = PaymentStatus.RECHAZADO.name,
            receiptUrl = "mock://receipt-historial-rechazado",
            rejectionReason = "El monto no coincide con lo asignado",
            // rejectPayment() nunca setea verifiedAt (sólo approvePayment lo hace) — sin
            // esto, la fila de este pago no tendría ninguna fecha para mostrar.
            createdAt = Date()
        )
    )

    val providers = mutableListOf(
        Provider(id = "provider-1", name = "Plomería del Sur", category = "Plomería", phone = "11-4444-5555"),
        Provider(id = "provider-2", name = "Electricidad Fernández", category = "Electricidad", phone = "11-2222-3333")
    )

    /** providerId -> sus reseñas. */
    val providerReviews = mutableMapOf<String, MutableList<ProviderReview>>(
        "provider-1" to mutableListOf(
            ProviderReview(id = "review-1", authorUid = adminUser.uid, comment = "Vino rápido y arregló la bomba.", stars = 5)
        )
    )

    fun findUserByEmail(email: String): User? = users.firstOrNull { it.email.equals(email, ignoreCase = true) }

    fun newId(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"
}
