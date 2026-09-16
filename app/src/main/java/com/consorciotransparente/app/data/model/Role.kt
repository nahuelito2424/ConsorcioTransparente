package com.consorciotransparente.app.data.model

/**
 * Los tres roles definidos en 1.4 del TP: permisos totales (Administrador),
 * gestión de mantenimiento (Encargado, opcional) y usuario final (Inquilino/Propietario).
 */
enum class Role {
    ADMINISTRADOR,
    ENCARGADO,
    INQUILINO;

    companion object {
        fun fromString(value: String?): Role =
            entries.firstOrNull { it.name == value } ?: INQUILINO
    }
}
