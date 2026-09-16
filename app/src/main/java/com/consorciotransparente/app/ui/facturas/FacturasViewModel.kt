package com.consorciotransparente.app.ui.facturas

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Expense
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.ExpenseRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

class FacturasViewModel(
    private val expenseRepo: ExpenseRepository = ExpenseRepository(),
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData<Resource<List<Expense>>>()
    val state: LiveData<Resource<List<Expense>>> = _state

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile()
                if (user == null || user.buildingId.isBlank()) {
                    _state.value = Resource.Success(emptyList())
                    return@launch
                }
                _state.value = Resource.Success(expenseRepo.listExpenses(user.buildingId))
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudieron cargar las facturas.")
            }
        }
    }
}
