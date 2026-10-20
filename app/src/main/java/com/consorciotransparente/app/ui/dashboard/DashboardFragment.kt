package com.consorciotransparente.app.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.FragmentDashboardBinding
import com.consorciotransparente.app.ui.tickets.CreateTicketActivity
import com.consorciotransparente.app.util.Resource
import com.consorciotransparente.app.util.SimpleModePrefs
import java.text.NumberFormat
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by lazy {
        ViewModelProvider(this)[DashboardViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        val simpleMode = SimpleModePrefs.isEnabled(requireContext())
        binding.normalModeContent.visibility = if (simpleMode) View.GONE else View.VISIBLE
        binding.simpleModeContent.visibility = if (simpleMode) View.VISIBLE else View.GONE

        binding.buttonSimpleExpenses.setOnClickListener {
            findNavController().navigate(R.id.facturasFragment)
        }
        binding.buttonSimpleTicket.setOnClickListener {
            startActivity(Intent(requireContext(), CreateTicketActivity::class.java))
        }
        binding.buttonSimpleNews.setOnClickListener {
            findNavController().navigate(R.id.muroFragment)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            when (state) {
                is Resource.Success -> render(state.data)
                is Resource.Error -> binding.textNews.text = state.message
                else -> Unit
            }
        }

        viewModel.load()
    }

    private fun render(data: DashboardUiState) {
        val firstName = data.user?.fullName?.substringBefore(" ").orEmpty().ifBlank { "vecino/a" }
        binding.textGreeting.text = "Hola, $firstName 👋"

        val building = data.summary.building
        binding.textBuilding.text = if (building != null) {
            "${building.name} · Dto. ${data.user?.unitId.orEmpty().ifBlank { "-" }}"
        } else {
            "Todavía no estás asignado a un edificio. Pedile el alta al Administrador."
        }

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("AR").build())
        binding.metricExpensesValue.text = currencyFormat.format(0)
        binding.metricDueDate.text = building?.expensesDueDay?.toString()?.plus("") ?: "-"
        binding.metricVotes.text = data.summary.activeVotesCount.toString()
        binding.metricExpensesAudited.text = data.summary.verifiedExpensesCount.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
