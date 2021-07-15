package com.legion_11.findanime.fragments.seeOtherOptions

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.legion_11.findanime.R
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchResult
import com.legion_11.findanime.databinding.FragmentSeeOtherOptionsBinding
import com.legion_11.findanime.fragments.SharedInterfaces
import com.legion_11.findanime.shared.SharedViewModel
import com.legion_11.findanime.shared.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fragment for replacing video preview
 */
class SeeOtherOptionsFragment : Fragment(), OtherOptionsAdapter.OnItemClickListener {

    private val binding get() = _binding!!
    private var _binding: FragmentSeeOtherOptionsBinding? = null
    private lateinit var fragmentListener: SharedInterfaces.FragmentListener
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val viewModel: SeeOtherOptionsViewModel by viewModels {
        SeeOtherOptionsViewModel.SeeOtherOptionsViewModelFactory(requireActivity().application,
            sharedViewModel.selectedItemId.value ?: -1)
    }
    private lateinit var adapter: OtherOptionsAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            fragmentListener = context as SharedInterfaces.FragmentListener
        }catch(e: RuntimeException){
            throw RuntimeException("$activity must implement method")
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeeOtherOptionsBinding.inflate(inflater, container, false)
        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        CoroutineScope(Dispatchers.IO).launch {
            sharedViewModel.newSelectedResult = viewModel.get(sharedViewModel.selectedItemId.value!!)?.searchResult
        }

        fragmentListener.restoreDefaultState()
        fragmentListener.setupFab(R.drawable.ic_baseline_check_24) {
            sharedViewModel.makeReplacement = true
            navigateBack()
        }

        requireActivity().onBackPressedDispatcher.addCallback(this) { navigateBack() }
    }

    private fun navigateBack() {
        try {
            findNavController().navigate(R.id.action_SeeOtherOptionsFragment_to_SearchFragment)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.allResults.observe(viewLifecycleOwner, {
            adapter = OtherOptionsAdapter(it, this,
                sharedViewModel.newSelectedResult,
                Utils.getVisibleHeight(requireActivity())
            )
            binding.recyclerView.layoutManager = LinearLayoutManager(context)
            binding.recyclerView.adapter = adapter
            binding.swipeContainer.setOnRefreshListener {
                adapter.notifyDataSetChanged()
                binding.swipeContainer.isRefreshing = false
            }
        })

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onItemClick(searchResult: SearchResult) {
        sharedViewModel.newSelectedResult = searchResult
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        fragmentListener.prepareToolbar(
            androidx.appcompat.R.drawable.abc_ic_ab_back_material,
            false
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                navigateBack()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val TAG = "SeeOtherOptionsFragment"
    }
}