package com.consorciotransparente.app.data.repository.mock

import com.consorciotransparente.app.data.model.Provider
import com.consorciotransparente.app.data.model.ProviderReview
import com.consorciotransparente.app.data.repository.ProviderRepository

class MockProviderRepository : ProviderRepository {
    override suspend fun listProviders(): List<Provider> = MockDataStore.providers

    override suspend fun getProvider(providerId: String): Provider? =
        MockDataStore.providers.firstOrNull { it.id == providerId }

    override suspend fun createProvider(provider: Provider): String {
        val saved = provider.copy(id = MockDataStore.newId("provider"))
        MockDataStore.providers.add(saved)
        return saved.id
    }

    override suspend fun updateProvider(provider: Provider) {
        val index = MockDataStore.providers.indexOfFirst { it.id == provider.id }
        if (index >= 0) MockDataStore.providers[index] = provider
    }

    override suspend fun deleteProvider(providerId: String) {
        MockDataStore.providers.removeAll { it.id == providerId }
        MockDataStore.providerReviews.remove(providerId)
    }

    override suspend fun listReviews(providerId: String): List<ProviderReview> =
        MockDataStore.providerReviews[providerId].orEmpty()

    override suspend fun addReview(providerId: String, review: ProviderReview) {
        val saved = review.copy(id = MockDataStore.newId("review"))
        MockDataStore.providerReviews.getOrPut(providerId) { mutableListOf() }.add(saved)
    }
}
