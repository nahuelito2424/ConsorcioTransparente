package com.consorciotransparente.app

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

/**
 * Application class: habilita la caché offline de Firestore (RNF-07 y HU-07 "próximamente"
 * dependen de que las lecturas de sólo lectura sobrevivan sin conexión).
 */
class ConsorcioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseFirestore.getInstance().firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
    }
}
