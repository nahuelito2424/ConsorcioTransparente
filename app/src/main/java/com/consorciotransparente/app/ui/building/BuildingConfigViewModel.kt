package com.consorciotransparente.app.ui.building

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Building
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.BuildingRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class BuildingConfigLoadState(val building: Building?, val isNew: Boolean)

class BuildingConfigViewModel(
    private val buildingRepo: BuildingRepository = RepositoryProvider.building,
    private val authRepo: AuthRepository = RepositoryProvider.auth
) : ViewModel() {

    private val _loadState = MutableLiveData<Resource<BuildingConfigLoadState>>()
    val loadState: LiveData<Resource<BuildingConfigLoadState>> = _loadState

    private val _saveState = MutableLiveData<Resource<Unit>>()
    val saveState: LiveData<Resource<Unit>> = _saveState

    fun load() {
        _loadState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                val existing = buildingRepo.getBuilding(user.buildingId)
                _loadState.value = Resource.Success(BuildingConfigLoadState(existing, existing == null))
            } catch (e: Exception) {
                _loadState.value = Resource.Error(e.message ?: "No se pudo cargar la configuración.")
            }
        }
    }

    /** HU-09 Escenario 1: qué conceptos cobra el edificio y sus vencimientos. */
    fun save(building: Building, isNew: Boolean) {
        if (!building.chargesRent && !building.chargesExpenses) {
            _saveState.value = Resource.Error("Elegí al menos un concepto para cobrar (alquiler y/o expensas).")
            return
        }
        _saveState.value = Resource.Loading
        viewModelScope.launch {
            try {
                if (isNew) {
                    val uid = authRepo.currentUid ?: error("Tenés que iniciar sesión de nuevo.")
                    buildingRepo.createBuilding(building, uid)
                } else {
                    buildingRepo.updateBuilding(building.id, building)
                }
                _saveState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveState.value = Resource.Error(e.message ?: "No se pudo guardar la configuración.")
            }
        }
    }
}
