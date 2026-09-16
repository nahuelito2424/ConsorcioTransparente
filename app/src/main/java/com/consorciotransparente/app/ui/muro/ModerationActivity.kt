package com.consorciotransparente.app.ui.muro

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.consorciotransparente.app.databinding.ActivityModerationBinding
import com.consorciotransparente.app.util.Resource

/**
 * Cola de moderación del Administrador (HU-05 Escenario 3). El acceso a esta pantalla
 * ya está limitado desde MuroFragment (el banner sólo se muestra si isAdmin == true),
 * y las reglas de Firestore además rechazan approvePost/rejectPost si quien llama no
 * tiene rol ADMINISTRADOR — así que aunque alguien fuerce el Intent, no puede aprobar nada.
 */
class ModerationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BUILDING_ID = "buildingId"
    }

    private lateinit var binding: ActivityModerationBinding
    private val viewModel: ModerationViewModel by lazy {
        ViewModelProvider(this)[ModerationViewModel::class.java]
    }
    private lateinit var buildingId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModerationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildingId = intent.getStringExtra(EXTRA_BUILDING_ID).orEmpty()

        val adapter = ModerationAdapter(
            onApprove = { post -> viewModel.approve(buildingId, post.id) },
            onReject = { post -> viewModel.reject(buildingId, post.id) }
        )
        binding.recyclerPending.layoutManager = LinearLayoutManager(this)
        binding.recyclerPending.adapter = adapter

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            if (state is Resource.Success) {
                adapter.submitList(state.data)
                binding.textEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewModel.load(buildingId)
    }
}
