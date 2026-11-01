package com.consorciotransparente.app.ui.protocolos

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.ActivityProtocolosBinding
import com.consorciotransparente.app.ui.BaseActivity

/**
 * HU-07: listado de protocolos de emergencia + teléfonos. Contenido fijo (ver
 * EmergencyProtocols.kt) — no hay ViewModel/Resource porque no hay nada que cargar de
 * red; arma las filas a mano en vez de un RecyclerView porque son 4 protocolos y 4
 * contactos, siempre los mismos.
 */
class ProtocolosActivity : BaseActivity() {

    private lateinit var binding: ActivityProtocolosBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProtocolosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }

        binding.protocolsContainer.removeAllViews()
        for (protocol in emergencyProtocols) {
            val row = TextView(this).apply {
                text = "${protocol.icon}  ${protocol.title}"
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                // bg_card ya es un ripple con máscara (ver su propio comentario) — un
                // foreground de selectableItemBackground encima duplicaba el efecto y,
                // al ser rectangular, se salía de las esquinas redondeadas de la tarjeta.
                setBackgroundResource(R.drawable.bg_card)
                setPadding(32, 32, 32, 32)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(
                        Intent(this@ProtocolosActivity, ProtocolDetailActivity::class.java)
                            .putExtra(ProtocolDetailActivity.EXTRA_PROTOCOL_ID, protocol.id)
                    )
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16
            binding.protocolsContainer.addView(row, params)
        }

        binding.contactsContainer.removeAllViews()
        for (contact in emergencyContacts) {
            val row = TextView(this).apply {
                text = "${contact.name}: ${contact.phone}"
                textSize = 13f
                setTextColor(getColor(R.color.text_primary))
                setPadding(32, 24, 32, 24)
            }
            binding.contactsContainer.addView(row)
        }
    }
}
