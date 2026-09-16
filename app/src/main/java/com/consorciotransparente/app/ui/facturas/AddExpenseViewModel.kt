package com.consorciotransparente.app.ui.facturas

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Expense
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.ExpenseRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

class AddExpenseViewModel(
    private val expenseRepo: ExpenseRepository = ExpenseRepository(),
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData<Resource<Expense>>()
    val state: LiveData<Resource<Expense>> = _state

    /**
     * HU-01 Escenario 1: sin foto de comprobante, el gasto NO se guarda (equivalente al
     * HTTP 400 del backend descripto en la historia de usuario).
     */
    fun save(
        description: String,
        category: String,
        providerName: String,
        amount: Double,
        receiptImage: Uri?
    ) {
        if (description.isBlank() || amount <= 0.0) {
            _state.value = Resource.Error("Completá la descripción y un monto válido.")
            return
        }
        if (receiptImage == null) {
            _state.value = Resource.Error("No se puede guardar un gasto sin la foto de la factura.")
            return
        }
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile()
                    ?: error("No se encontró tu perfil de usuario.")
                if (user.buildingId.isBlank()) error("Tu usuario todavía no está asignado a un edificio.")

                val expense = expenseRepo.createExpense(
                    buildingId = user.buildingId,
                    description = description,
                    category = category,
                    providerName = providerName,
                    amount = amount,
                    createdByUid = user.uid,
                    receiptImage = receiptImage
                )
                _state.value = Resource.Success(expense)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo guardar el gasto.")
            }
        }
    }
}
