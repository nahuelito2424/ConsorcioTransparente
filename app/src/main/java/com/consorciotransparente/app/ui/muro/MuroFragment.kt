package com.consorciotransparente.app.ui.muro

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.FragmentMuroBinding
import com.consorciotransparente.app.util.Resource

/** HU-05 (Muro de Publicaciones): feed con las publicaciones ya aprobadas por el Administrador. */
class MuroFragment : Fragment() {

    private var _binding: FragmentMuroBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MuroViewModel by lazy {
        ViewModelProvider(this)[MuroViewModel::class.java]
    }
    private val adapter = PostAdapter()
    private var currentBuildingId = ""
    private var currentAuthorName = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMuroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPosts.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }
        binding.buttonNewPost.setOnClickListener {
            startActivity(
                Intent(requireContext(), CreatePostActivity::class.java)
                    .putExtra(CreatePostActivity.EXTRA_BUILDING_ID, currentBuildingId)
                    .putExtra(CreatePostActivity.EXTRA_AUTHOR_NAME, currentAuthorName)
            )
        }
        binding.textModerationBanner.setOnClickListener {
            startActivity(
                Intent(requireContext(), ModerationActivity::class.java)
                    .putExtra(ModerationActivity.EXTRA_BUILDING_ID, currentBuildingId)
            )
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            if (state is Resource.Success) {
                currentBuildingId = state.data.buildingId
                currentAuthorName = state.data.authorName
                adapter.submitList(state.data.posts)
                binding.textEmpty.visibility = if (state.data.posts.isEmpty()) View.VISIBLE else View.GONE

                if (state.data.isAdmin && state.data.pendingCount > 0) {
                    binding.textModerationBanner.visibility = View.VISIBLE
                    binding.textModerationBanner.text =
                        getString(R.string.muro_moderation_banner, state.data.pendingCount)
                } else {
                    binding.textModerationBanner.visibility = View.GONE
                }
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
