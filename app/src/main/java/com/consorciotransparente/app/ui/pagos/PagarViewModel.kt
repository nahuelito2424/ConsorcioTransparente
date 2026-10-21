package com.consorciotransparente.app.ui.pagos

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.BuildingRepository
import com.consorciotransparente.app.data.repository.PaymentRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import com.consorciotransparente.app.util.currentPeriod
import kotlinx.coroutines.launch

data class PagoUiState(
    val period: String,
    val breakdown: Map<String, Double>,
    val total: Double,
    val cbu: String,
    val alias: String,
    val payment: Payment?
)

/** HU-03: lo que un inquilino debe pagar este mes y cómo pagarlo (transferencia + comprobante). */
class PagarViewModel(
    private val authRepo: AuthRepository = RepositoryProvider.auth,
    private val buildingRepo: BuildingRepository = RepositoryProvider.building,
    private val paymentRepo: PaymentRepository = RepositoryProvider.payment
) : ViewModel() {
    private val _state = MutableLiveData<Resource<PagoUiState>>()
    val state: LiveData<Resource<PagoUiState>> = _state

    private val _uploadState = MutableLiveData<Resource<Unit>>()
    val uploadState: LiveData<Resource<Unit>> = _uploadState

    private val _history = MutableLiveData<Resource<List<Payment>>>()
    val history: LiveData<Resource<List<Payment>>> = _history

    private var buildingId = ""
    private var uid = ""
    private val period = currentPeriod()

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                buildingId = user.buildingId
                uid = user.uid
                if (buildingId.isBlank()) error("Tu usuario todavía no está asignado a un edificio.")

                val building = buildingRepo.getBuilding(buildingId)
                val breakdown = buildMap {
                    if (building?.chargesRent == true && user.rentAmount > 0.0) put("alquiler", user.rentAmount)
                    if (building?.chargesExpenses == true && user.expensesAmount > 0.0) put("expensas", user.expensesAmount)
                }
                val payment = paymentRepo.getCurrentPayment(buildingId, uid, period)

                _state.value = Resource.Success(
                    PagoUiState(
                        period = period,
                        breakdown = breakdown,
                        total = breakdown.values.sum(),
                        cbu = building?.cbu.orEmpty(),
                        alias = building?.alias.orEmpty(),
                        payment = payment
                    )
                )
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar tu pago.")
            }
        }
    }

    fun uploadReceipt(receipt: Uri) {
        val current = (state.value as? Resource.Success)?.data ?: return
        _uploadState.value = Resource.Loading
        viewModelScope.launch {
            try {
                paymentRepo.uploadReceipt(buildingId, uid, period, current.breakdown, receipt)
                _uploadState.value = Resource.Success(Unit)
                load()
            } catch (e: Exception) {
                _uploadState.value = Resource.Error(e.message ?: "No se pudo subir el comprobante.")
            }
        }
    }

    fun loadHistory() {
        _history.value = Resource.Loading
        viewModelScope.launch {
            try {
                _history.value = Resource.Success(paymentRepo.listMyPayments(buildingId, uid).filter { it.period != period })
            } catch (e: Exception) {
                _history.value = Resource.Error(e.message ?: "No se pudo cargar el historial.")
            }
        }
    }
}
