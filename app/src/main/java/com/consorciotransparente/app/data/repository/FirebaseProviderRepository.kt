package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Provider
import com.consorciotransparente.app.data.model.ProviderReview
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseProviderRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ProviderRepository {
    private fun providersRef() = db.collection("providers")
    private fun reviewsRef(providerId: String) = providersRef().document(providerId).collection("reviews")

    override suspend fun listProviders(): List<Provider> {
        val snapshot = providersRef().get().await()
        return snapshot.documents.mapNotNull { it.toObject(Provider::class.java)?.copy(id = it.id) }
    }

    override suspend fun getProvider(providerId: String): Provider? {
        val doc = providersRef().document(providerId).get().await()
        return doc.toObject(Provider::class.java)?.copy(id = doc.id)
    }

    override suspend fun createProvider(provider: Provider): String {
        val ref = providersRef().document()
        ref.set(provider.copy(id = ref.id)).await()
        return ref.id
    }

    override suspend fun updateProvider(provider: Provider) {
        providersRef().document(provider.id).set(provider).await()
    }

    override suspend fun deleteProvider(providerId: String) {
        providersRef().document(providerId).delete().await()
    }

    override suspend fun listReviews(providerId: String): List<ProviderReview> {
        val snapshot = reviewsRef(providerId).get().await()
        return snapshot.documents.mapNotNull { it.toObject(ProviderReview::class.java)?.copy(id = it.id) }
    }

    override suspend fun addReview(providerId: String, review: ProviderReview) {
        val ref = reviewsRef(providerId).document()
        ref.set(review.copy(id = ref.id)).await()
    }
}
