package com.consorciotransparente.app.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.User
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.DashboardRepository
import com.consorciotransparente.app.data.repository.DashboardSummary
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class DashboardUiState(val user: User?, val summary: DashboardSummary)

class DashboardViewModel(
    private val authRepo: AuthRepository = RepositoryProvider.auth,
    private val dashboardRepo: DashboardRepository = RepositoryProvider.dashboard
) : ViewModel() {

    private val _state = MutableLiveData<Resource<DashboardUiState>>()
    val state: LiveData<Resource<DashboardUiState>> = _state

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile()
                val summary = dashboardRepo.loadSummary(user?.buildingId.orEmpty())
                _state.value = Resource.Success(DashboardUiState(user, summary))
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar el dashboard.")
            }
        }
    }
}
