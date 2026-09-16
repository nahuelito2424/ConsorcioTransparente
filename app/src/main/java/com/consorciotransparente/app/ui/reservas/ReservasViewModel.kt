package com.consorciotransparente.app.ui.reservas

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Building
import com.consorciotransparente.app.data.model.Reservation
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.ReservationRepository
import com.consorciotransparente.app.util.Resource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Franjas horarias fijas del edificio, suficientes para un SUM/parrilla/pileta compartidos. */
val TIME_SLOTS = listOf("09:00-13:00", "13:00-17:00", "17:00-21:00", "21:00-01:00")

data class ReservasScreenState(
    val buildingId: String,
    val spaces: List<String>,
    val uid: String,
    val userName: String,
    val selectedSpace: String,
    val selectedDate: String,
    val slotOwners: Map<String, Reservation?>, // franja -> reserva que la ocupa (null = libre)
    val myReservations: List<Reservation>,
    val editingReservationId: String?
)

class ReservasViewModel(
    private val reservationRepo: ReservationRepository = ReservationRepository(),
    private val authRepo: AuthRepository = AuthRepository(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val _state = MutableLiveData<Resource<ReservasScreenState>>()
    val state: LiveData<Resource<ReservasScreenState>> = _state

    private var buildingId = ""
    private var uid = ""
    private var userName = ""
    private var spaces = listOf("SUM")
    private var selectedSpace = "SUM"
    private var selectedDate = todayIso()
    var editingReservationId: String? = null
        private set

    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun start() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                buildingId = user.buildingId
                uid = user.uid
                userName = user.fullName
                if (buildingId.isBlank()) {
                    _state.value = Resource.Error("Tu usuario todavía no está asignado a un edificio.")
                    return@launch
                }
                val buildingSnap = db.collection("buildings").document(buildingId).get().await()
                val building = buildingSnap.toObject(Building::class.java)
                spaces = building?.amenities?.takeIf { it.isNotEmpty() } ?: listOf("SUM")
                selectedSpace = spaces.first()
                refresh()
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar la pantalla de reservas.")
            }
        }
    }

    fun selectSpace(space: String) {
        selectedSpace = space
        refresh()
    }

    fun selectDate(dateIso: String) {
        selectedDate = dateIso
        refresh()
    }

    fun startEditing(reservation: Reservation) {
        editingReservationId = reservation.id
        selectedSpace = reservation.spaceName
        selectedDate = reservation.date
        refresh()
    }

    fun cancelEditing() {
        editingReservationId = null
        refresh()
    }

    fun refresh() {
        if (buildingId.isBlank()) return
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val dayReservations = reservationRepo.listReservationsForDate(buildingId, selectedSpace, selectedDate)
                val slotOwners = TIME_SLOTS.associateWith { slot -> dayReservations.find { it.timeSlot == slot } }
                val myReservations = reservationRepo.listMyUpcomingReservations(buildingId, uid, todayIso())
                _state.value = Resource.Success(
                    ReservasScreenState(
                        buildingId, spaces, uid, userName, selectedSpace, selectedDate,
                        slotOwners, myReservations, editingReservationId
                    )
                )
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudieron cargar las reservas.")
            }
        }
    }

    /** Escenario 2, o Escenario 3 si `editingReservationId` != null (cambia de franja una reserva propia). */
    fun confirmSlot(timeSlot: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val editingId = editingReservationId
                if (editingId != null) {
                    reservationRepo.changeSlot(buildingId, editingId, selectedSpace, selectedDate, timeSlot, uid, userName)
                    editingReservationId = null
                } else {
                    reservationRepo.reserve(buildingId, selectedSpace, selectedDate, timeSlot, uid, userName)
                }
                refresh()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo confirmar la reserva. Puede que alguien la haya tomado antes.")
            }
        }
    }

    fun cancelReservation(reservation: Reservation, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                reservationRepo.cancel(buildingId, reservation.id)
                refresh()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo cancelar la reserva.")
            }
        }
    }
}
