package com.consorciotransparente.app.data.model

/**
 * Documento en la colección "users" (doc id = Firebase Auth uid).
 * El constructor sin argumentos es requerido por Firestore para deserializar con toObject().
 */
data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = Role.INQUILINO.name,
    val buildingId: String = "",
    val unitId: String = "",
    val phone: String = ""
) {
    fun roleEnum(): Role = Role.fromString(role)
}
