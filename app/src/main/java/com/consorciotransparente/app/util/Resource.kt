package com.consorciotransparente.app.util

/** Envoltorio simple para exponer el estado de una operación asincrónica a la UI. */
sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
}
