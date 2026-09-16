package com.consorciotransparente.app.ui.tickets

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.consorciotransparente.app.databinding.ActivityTicketsBinding
import com.consorciotransparente.app.util.Resource

/** HU-10: cola de tickets (Encargado/Administrador) o mis reportes (Inquilino). */
class TicketsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTicketsBinding
    private val viewModel: TicketsViewModel by lazy {
        ViewModelProvider(this)[TicketsViewModel::class.java]
    }
    private var currentBuildingId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTicketsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = TicketAdapter { ticket ->
            startActivity(
                Intent(this, TicketDetailActivity::class.java)
                    .putExtra(TicketDetailActivity.EXTRA_BUILDING_ID, currentBuildingId)
                    .putExtra(TicketDetailActivity.EXTRA_TICKET_ID, ticket.id)
            )
        }
        binding.recyclerTickets.layoutManager = LinearLayoutManager(this)
        binding.recyclerTickets.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }
        binding.fabNewTicket.setOnClickListener {
            startActivity(Intent(this, CreateTicketActivity::class.java))
        }

        viewModel.state.observe(this) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            if (state is Resource.Success) {
                currentBuildingId = state.data.buildingId
                adapter.submitList(state.data.tickets)
                binding.textEmpty.visibility = if (state.data.tickets.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}
