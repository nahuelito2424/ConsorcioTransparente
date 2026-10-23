package com.consorciotransparente.app.ui.protocolos

import android.os.Bundle
import android.widget.TextView
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.ActivityProtocolDetailBinding
import com.consorciotransparente.app.ui.BaseActivity

/** HU-07: pasos del protocolo elegido en ProtocolosActivity. */
class ProtocolDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_PROTOCOL_ID = "protocolId"
    }

    private lateinit var binding: ActivityProtocolDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProtocolDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val protocolId = intent.getStringExtra(EXTRA_PROTOCOL_ID)
        val protocol = emergencyProtocols.firstOrNull { it.id == protocolId } ?: run { finish(); return }

        binding.buttonBack.setOnClickListener { finish() }
        binding.textTitle.text = "${protocol.icon}  ${protocol.title}"

        binding.stepsContainer.removeAllViews()
        protocol.steps.forEachIndexed { index, step ->
            val row = TextView(this).apply {
                text = "${index + 1}. $step"
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, 0, 0, 20)
            }
            binding.stepsContainer.addView(row)
        }
    }
}
