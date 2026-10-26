package com.consorciotransparente.app.ui.rendicion

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Expense
import com.consorciotransparente.app.data.model.ExpenseCategory
import com.consorciotransparente.app.data.model.ExpenseStatus
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.BuildingRepository
import com.consorciotransparente.app.data.repository.ExpenseRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

data class RendicionUiState(
    val period: String,
    val buildingName: String,
    val totalVerificado: Double,
    val categoryTotals: List<Pair<ExpenseCategory, Double>>,
    val bloqueadoCount: Int,
    val bloqueadoTotal: Double,
    val expensesByCategory: Map<ExpenseCategory, List<Expense>>
)

/**
 * Backlog "Rendición de cuentas descargable": desglose por categoría de los gastos
 * VERIFICADO de un período, más un aviso de cuántos gastos quedaron BLOQUEADOS (sin
 * comprobante) y por eso no entran en el total — ver "Review Focus" del plan: un mes
 * con sólo bloqueados no debe confundirse con un mes sin ningún gasto.
 */
class RendicionCuentasViewModel(
    private val authRepo: AuthRepository = RepositoryProvider.auth,
    private val expenseRepo: ExpenseRepository = RepositoryProvider.expense,
    private val buildingRepo: BuildingRepository = RepositoryProvider.building
) : ViewModel() {
    private val _state = MutableLiveData<Resource<RendicionUiState>>()
    val state: LiveData<Resource<RendicionUiState>> = _state

    private val periodFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    // Si el usuario navega de mes rápido, dos `load()` pueden quedar en vuelo a la vez
    // y la respuesta más vieja puede llegar después de la más nueva — sin este guard,
    // el estado final terminaría mostrando (y permitiendo descargar en PDF) un período
    // distinto al que dice la etiqueta en pantalla.
    private var requestedPeriod = ""

    fun load(period: String) {
        requestedPeriod = period
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                if (user.buildingId.isBlank()) error("Tu usuario todavía no está asignado a un edificio.")

                val buildingName = buildingRepo.getBuilding(user.buildingId)?.name.orEmpty()
                val expensesThisPeriod = expenseRepo.listExpenses(user.buildingId).filter {
                    it.createdAt != null && periodFormat.format(it.createdAt) == period
                }

                val verificados = expensesThisPeriod.filter { it.status == ExpenseStatus.VERIFICADO.name }
                val bloqueados = expensesThisPeriod.filter { it.status == ExpenseStatus.BLOQUEADO.name }

                val expensesByCategory = verificados.groupBy { categoriaDe(it) }
                // Orden fijo del enum, no el de aparición en el mapa — mismo criterio que
                // la web (GastosPage/paymentFilter): categorías sin gasto no aparecen.
                val categoryTotals = ExpenseCategory.entries.mapNotNull { category ->
                    val total = expensesByCategory[category]?.sumOf { it.amount } ?: 0.0
                    if (total > 0.0) category to total else null
                }

                if (period != requestedPeriod) return@launch // superado por una navegación más nueva

                _state.value = Resource.Success(
                    RendicionUiState(
                        period = period,
                        buildingName = buildingName,
                        totalVerificado = verificados.sumOf { it.amount },
                        categoryTotals = categoryTotals,
                        bloqueadoCount = bloqueados.size,
                        bloqueadoTotal = bloqueados.sumOf { it.amount },
                        expensesByCategory = expensesByCategory
                    )
                )
            } catch (e: Exception) {
                if (period != requestedPeriod) return@launch
                _state.value = Resource.Error(e.message ?: "No se pudo cargar la rendición de cuentas.")
            }
        }
    }

    // Gastos cargados antes de que la categoría fuera un menú fijo (ver Task 1) pueden
    // traer texto libre que no matchea ningún valor del enum — van a "Otro" en vez de
    // romper la pantalla.
    private fun categoriaDe(expense: Expense): ExpenseCategory =
        try {
            ExpenseCategory.valueOf(expense.category)
        } catch (e: IllegalArgumentException) {
            ExpenseCategory.OTRO
        }
}
