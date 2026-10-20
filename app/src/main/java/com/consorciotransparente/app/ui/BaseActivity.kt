package com.consorciotransparente.app.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.consorciotransparente.app.R
import com.consorciotransparente.app.util.SimpleModePrefs

/**
 * Todas las Activity heredan de acá en vez de AppCompatActivity directo. Cuando el
 * usuario prende "Modo simple" (SimpleModePrefs, toggle en Más), aplica letra más
 * grande en toda la pantalla vía Configuration.fontScale (el mismo mecanismo que usa
 * Android para la configuración de accesibilidad del sistema, sin tocar cada layout) y
 * cambia el tema para que el texto secundario salga en negro pleno (ver
 * Theme.ConsorcioTransparente.Simple en themes.xml).
 */
abstract class BaseActivity : AppCompatActivity() {

    private val simpleModeFontScale = 1.3f

    override fun attachBaseContext(newBase: Context) {
        val context = if (SimpleModePrefs.isEnabled(newBase)) {
            val config = Configuration(newBase.resources.configuration)
            config.fontScale = simpleModeFontScale
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (SimpleModePrefs.isEnabled(this)) {
            setTheme(R.style.Theme_ConsorcioTransparente_Simple)
        }
        super.onCreate(savedInstanceState)
    }
}
