package com.consorciotransparente.app.ui.vecinos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.User
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.BuildingRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.data.repository.ResidentRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class VecinoRow(val user: User, val showRent: Boolean, val showExpenses: Boolean)

/** HU-03: el Administrador le asigna a cada inquilino un monto fijo de alquiler y/o expensas. */
class VecinosViewModel(
    private val residentRepo: ResidentRepository = RepositoryProvider.resident,
    private val authRepo: AuthRepository = RepositoryProvider.auth,
    private val buildingRepo: BuildingRepository = RepositoryProvider.building
) : ViewModel() {
    private val _state = MutableLiveData<Resource<List<VecinoRow>>>()
    val state: LiveData<Resource<List<VecinoRow>>> = _state

    private val _saveState = MutableLiveData<Resource<Unit>>()
    val saveState: LiveData<Resource<Unit>> = _saveState

    private var buildingId = ""

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                buildingId = user.buildingId
                val building = buildingRepo.getBuilding(buildingId)
                val residents = residentRepo.listResidents(buildingId)
                val rows = residents.map { VecinoRow(it, building?.chargesRent ?: false, building?.chargesExpenses ?: false) }
                _state.value = Resource.Success(rows)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudieron cargar los vecinos.")
            }
        }
    }

    fun saveAmounts(uid: String, rentAmount: Double, expensesAmount: Double) {
        _saveState.value = Resource.Loading
        viewModelScope.launch {
            try {
                residentRepo.updateAmounts(buildingId, uid, rentAmount, expensesAmount)
                _saveState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveState.value = Resource.Error(e.message ?: "No se pudo guardar el monto.")
            }
        }
    }
}
