package com.consorciotransparente.app.ui.votaciones

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.consorciotransparente.app.databinding.FragmentVotacionesBinding
import com.consorciotransparente.app.util.Resource

/** HU-02 (Votaciones Vinculantes): listado de convocatorias del edificio del usuario. */
class VotacionesFragment : Fragment() {

    private var _binding: FragmentVotacionesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VotacionesViewModel by lazy {
        ViewModelProvider(this)[VotacionesViewModel::class.java]
    }
    private lateinit var adapter: VoteAdapter
    private var currentBuildingId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVotacionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = VoteAdapter { item ->
            startActivity(
                Intent(requireContext(), VoteDetailActivity::class.java)
                    .putExtra(VoteDetailActivity.EXTRA_BUILDING_ID, currentBuildingId)
                    .putExtra(VoteDetailActivity.EXTRA_VOTE_ID, item.vote.id)
            )
        }
        binding.recyclerVotes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerVotes.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }
        binding.fabCreateVote.setOnClickListener {
            startActivity(Intent(requireContext(), CreateVoteActivity::class.java).putExtra(
                CreateVoteActivity.EXTRA_BUILDING_ID, currentBuildingId
            ))
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            if (state is Resource.Success) {
                currentBuildingId = state.data.buildingId
                adapter.submitList(state.data.items)
                binding.textEmpty.visibility = if (state.data.items.isEmpty()) View.VISIBLE else View.GONE
                binding.fabCreateVote.visibility = if (state.data.isAdmin) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
