package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.BuildConfig
import com.consorciotransparente.app.data.repository.mock.MockAuthRepository
import com.consorciotransparente.app.data.repository.mock.MockBuildingRepository
import com.consorciotransparente.app.data.repository.mock.MockDashboardRepository
import com.consorciotransparente.app.data.repository.mock.MockExpenseRepository
import com.consorciotransparente.app.data.repository.mock.MockMuroRepository
import com.consorciotransparente.app.data.repository.mock.MockReservationRepository
import com.consorciotransparente.app.data.repository.mock.MockTicketRepository
import com.consorciotransparente.app.data.repository.mock.MockVoteRepository

/**
 * Único punto donde se decide, según el flavor de build (BuildConfig.USE_MOCK_DATA,
 * ver app/build.gradle.kts), si cada Repository habla con Firebase real o con los datos
 * en memoria de data/repository/mock/. No es un contenedor de DI: es un default value
 * más para el constructor de cada ViewModel, resuelto acá para no repetir el `if` en
 * cada uno de ellos.
 */
object RepositoryProvider {
    val auth: AuthRepository by lazy {
        if (BuildConfig.USE_MOCK_DATA) MockAuthRepository() else FirebaseAuthRepository()
    }
    val building: BuildingRepository by lazy {
        if (BuildConfig.USE_MOCK_DATA) MockBuildingRepository() else FirebaseBuildingRepository()
    }
    val dashboard: DashboardRepository by lazy {
        if (BuildConfig.USE_MOCK_DATA) MockDashboardRepository() else FirebaseDashboardRepository()
    }
    val expense: ExpenseRepository by lazy {
        if (BuildConfig.USE_MOCK_DATA) MockExpenseRepository() else FirebaseExpenseRepository()
    }
    val muro: MuroRepository by lazy {
        if (BuildConfig.USE_MOCK_DATA) MockMuroRepository() else FirebaseMuroRepository()
    }
    val reservation: ReservationRepository by lazy {
        if (BuildConfig.USE_MOCK_DATA) MockReservationRepository() else FirebaseReservationRepository()
    }
    val ticket: TicketRepository by lazy {
        if (BuildConfig.USE_MOCK_DATA) MockTicketRepository() else FirebaseTicketRepository()
    }
    val vote: VoteRepository by lazy {
        if (BuildConfig.USE_MOCK_DATA) MockVoteRepository() else FirebaseVoteRepository()
    }
}
