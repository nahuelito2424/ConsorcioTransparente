package com.consorciotransparente.app.ui.facturas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.consorciotransparente.app.data.model.Expense
import com.consorciotransparente.app.databinding.FragmentFacturasBinding
import com.consorciotransparente.app.util.Resource
import java.text.NumberFormat
import java.util.Locale

class FacturasFragment : Fragment() {

    private var _binding: FragmentFacturasBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FacturasViewModel by lazy {
        ViewModelProvider(this)[FacturasViewModel::class.java]
    }
    private val adapter = ExpenseAdapter()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("AR").build())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFacturasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerExpenses.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }
        binding.buttonAddExpense.setOnClickListener {
            startActivity(Intent(requireContext(), AddExpenseActivity::class.java))
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            if (state is Resource.Success) renderList(state.data)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load() // recarga al volver de "Cargar gasto"
    }

    private fun renderList(expenses: List<Expense>) {
        adapter.submitList(expenses)
        val verifiedTotal = expenses.filter { it.isVerified }.sumOf { it.amount }
        val blockedTotal = expenses.filterNot { it.isVerified }.sumOf { it.amount }
        binding.textTotalVerified.text = currencyFormat.format(verifiedTotal)
        binding.textTotalBlocked.text = currencyFormat.format(blockedTotal)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
