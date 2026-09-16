package com.consorciotransparente.app.data.model

/**
 * Documento en "providers" (directorio global de proveedores). Implementa HU-08: ficha
 * con los 5 datos pedidos (teléfono, nombre/perfil, categoría, comentarios, calificación),
 * administrada por el Administrador.
 */
data class Provider(
    val id: String = "",
    val name: String = "",
    val category: String = "", // Plomería, Electricidad, Gas, Albañilería
    val phone: String = "",
    val averageRating: Double = 0.0,
    val ratingCount: Int = 0
)

/** Documento en "providers/{providerId}/reviews". Un comentario + estrellas por trabajo realizado. */
data class ProviderReview(
    val id: String = "",
    val authorUid: String = "",
    val comment: String = "",
    val stars: Int = 5,
    val expenseId: String = "" // vincula la reseña a un único gasto/trabajo (HU-08 Escenario 3)
)
