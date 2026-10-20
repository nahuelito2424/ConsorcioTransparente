package com.consorciotransparente.app.util

import android.content.Context

/**
 * "Modo simple" (accesibilidad): letra más grande y texto secundario en negro pleno en
 * toda la app, pensado para usuarios a los que les cuesta leer letra chica (ver
 * BaseActivity). Es una preferencia del dispositivo, no de la cuenta — cada usuario la
 * prende en su propio celular, no se guarda en Firestore.
 */
object SimpleModePrefs {
    private const val PREFS_NAME = "consorcio_prefs"
    private const val KEY_SIMPLE_MODE = "simple_mode_enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SIMPLE_MODE, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SIMPLE_MODE, enabled)
            .apply()
    }
}
