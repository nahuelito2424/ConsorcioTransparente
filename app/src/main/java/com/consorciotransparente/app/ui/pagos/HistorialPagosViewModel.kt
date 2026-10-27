package com.consorciotransparente.app.ui.pagos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.PaymentRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import com.consorciotransparente.app.util.currentPeriod
import com.consorciotransparente.app.util.shiftPeriod
import kotlinx.coroutines.launch

data class HistorialRow(val period: String, val payment: Payment?)

/**
 * Backlog "Estado de cuenta por unidad": últimos 12 meses de un inquilino, cruzando
 * los `Payment` que existan con los 12 períodos — un período sin documento se expone
 * igual como una fila con `payment == null`, para que la pantalla lo muestre como
 * "Sin pago registrado" en vez de omitirlo en silencio.
 */
class HistorialPagosViewModel(
    private val authRepo: AuthRepository = RepositoryProvider.auth,
    private val paymentRepo: PaymentRepository = RepositoryProvider.payment
) : ViewModel() {
    private val _state = MutableLiveData<Resource<List<HistorialRow>>>()
    val state: LiveData<Resource<List<HistorialRow>>> = _state

    fun load(uid: String) {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                // El perfil cargado es el de quien mira la pantalla (para saber su
                // buildingId), no necesariamente el dueño del historial — ese es `uid`.
                val user = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                if (user.buildingId.isBlank()) error("Tu usuario todavía no está asignado a un edificio.")

                val paymentsByPeriod = paymentRepo.listMyPayments(user.buildingId, uid)
                    .associateBy { it.period }

                val periods = (0..11).map { shiftPeriod(currentPeriod(), -it) }
                val rows = periods.map { period -> HistorialRow(period, paymentsByPeriod[period]) }

                _state.value = Resource.Success(rows)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar el historial.")
            }
        }
    }
}
