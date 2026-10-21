package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Provider
import com.consorciotransparente.app.data.model.ProviderReview

/**
 * Implementa HU-08 (Directorio de Proveedores): directorio global ("providers", no
 * cuelga de un edificio — un plomero recomendado sirve para cualquier consorcio). El
 * Administrador da de alta/edita/elimina; cualquier usuario firmado puede listar y
 * dejar una reseña (ver firestore.rules).
 *
 * [Provider.averageRating]/[Provider.ratingCount] no se escriben desde acá: se
 * recalculan en el momento a partir de [listReviews] (ver ProviderDetailViewModel) para
 * no requerir una transacción ni ensanchar la regla de escritura de "providers" (hoy
 * sólo el Administrador puede escribir el documento del proveedor).
 *
 * Interfaz para permitir la implementación mock (ver RepositoryProvider).
 */
interface ProviderRepository {
    suspend fun listProviders(): List<Provider>
    suspend fun getProvider(providerId: String): Provider?

    /** Sólo Administrador (reforzado en firestore.rules). */
    suspend fun createProvider(provider: Provider): String
    suspend fun updateProvider(provider: Provider)
    suspend fun deleteProvider(providerId: String)

    suspend fun listReviews(providerId: String): List<ProviderReview>

    /** Cualquier usuario firmado; [ProviderReview.authorUid] debe ser el suyo propio. */
    suspend fun addReview(providerId: String, review: ProviderReview)
}
