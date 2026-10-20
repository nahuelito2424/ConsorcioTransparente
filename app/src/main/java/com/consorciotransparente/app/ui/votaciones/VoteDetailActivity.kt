package com.consorciotransparente.app.ui.votaciones

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.databinding.ActivityVoteDetailBinding
import com.consorciotransparente.app.util.Resource

/** HU-02: emitir un voto (Escenario 1) o ver los resultados (Escenario 3) si ya cerró o ya votaste. */
class VoteDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_BUILDING_ID = "buildingId"
        const val EXTRA_VOTE_ID = "voteId"
    }

    private lateinit var binding: ActivityVoteDetailBinding
    private val viewModel: VoteDetailViewModel by lazy {
        ViewModelProvider(this)[VoteDetailViewModel::class.java]
    }
    private lateinit var buildingId: String
    private lateinit var voteId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildingId = intent.getStringExtra(EXTRA_BUILDING_ID).orEmpty()
        voteId = intent.getStringExtra(EXTRA_VOTE_ID).orEmpty()

        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonSubmit.setOnClickListener {
            val selectedId = binding.radioGroupOptions.checkedRadioButtonId
            if (selectedId == -1) {
                binding.textError.text = "Elegí una opción antes de votar."
                binding.textError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            val optionIndex = binding.radioGroupOptions.findViewById<RadioButton>(selectedId).tag as Int
            viewModel.submitVote(buildingId, voteId, optionIndex)
        }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            when (state) {
                is Resource.Success -> render(state.data)
                is Resource.Error -> {
                    binding.textError.text = state.message
                    binding.textError.visibility = View.VISIBLE
                }
                else -> Unit
            }
        }

        viewModel.submitState.observe(this) { state ->
            if (state is Resource.Error) {
                binding.textError.text = state.message
                binding.textError.visibility = View.VISIBLE
            }
        }

        viewModel.load(buildingId, voteId)
    }

    private fun render(data: VoteDetailUiState) {
        binding.textTitle.text = data.vote.title
        binding.textDescription.text = data.vote.description
        binding.textMeta.text = if (data.isClosed) "Esta votación está cerrada." else "Votación activa."

        val showResults = data.counts != null
        binding.resultsContainer.visibility = if (showResults) View.VISIBLE else View.GONE
        binding.radioGroupOptions.visibility = if (showResults) View.GONE else View.VISIBLE
        binding.buttonSubmit.visibility = if (showResults) View.GONE else View.VISIBLE

        if (showResults) {
            renderResults(data)
        } else {
            renderOptions(data)
        }
    }

    private fun renderOptions(data: VoteDetailUiState) {
        binding.radioGroupOptions.removeAllViews()
        data.vote.options.forEachIndexed { index, option ->
            val radioButton = RadioButton(this).apply {
                text = option
                tag = index
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            binding.radioGroupOptions.addView(radioButton)
        }
    }

    private fun renderResults(data: VoteDetailUiState) {
        binding.resultsContainer.removeAllViews()
        val counts = data.counts ?: return
        val total = counts.sum().coerceAtLeast(1)

        data.vote.options.forEachIndexed { index, option ->
            val count = counts.getOrElse(index) { 0 }
            val percent = (count * 100) / total

            val label = TextView(this).apply {
                text = "$option — $percent% ($count)"
                textSize = 13f
                setPadding(0, 12, 0, 4)
            }
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = percent
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24)
            }
            binding.resultsContainer.addView(label)
            binding.resultsContainer.addView(bar)
        }

        val totalLabel = TextView(this).apply {
            text = getString(com.consorciotransparente.app.R.string.vote_total_votes, counts.sum())
            textSize = 11f
            setPadding(0, 16, 0, 0)
        }
        binding.resultsContainer.addView(totalLabel)
    }
}
