package com.dmytroa.findanime.fragments.seeOtherOptions

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dmytroa.findanime.R
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchResult
import com.dmytroa.findanime.databinding.FragmentSeeOtherOptionsBinding
import com.dmytroa.findanime.fragments.search.SearchFragment
import com.dmytroa.findanime.shared.OnFragmentListener
import com.dmytroa.findanime.shared.SharedViewModel
import com.dmytroa.findanime.shared.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SeeOtherOptionsFragment : Fragment(), OtherOptionsAdapter.OnItemClickListener {

    private val binding get() = _binding!!
    private var _binding: FragmentSeeOtherOptionsBinding? = null
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val viewModel: SeeOtherOptionsViewModel by viewModels {
        SeeOtherOptionsViewModel.SeeOtherOptionsViewModelFactory(requireActivity().application,
            sharedViewModel.selectedItemId.value ?: -1)
    }

    private lateinit var adapter: OtherOptionsAdapter

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
        (requireActivity() as OnFragmentListener).setOtherOptionsFun()
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            findNavController().navigate(R.id.action_SeeOtherOptionsFragment_to_SearchFragment)
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
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onItemClick(searchResult: SearchResult) {
        sharedViewModel.newSelectedResult = searchResult
        Log.i(TAG, "onItemClick: ${sharedViewModel.newSelectedResult}")
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        (requireActivity() as SearchFragment.OnCreateToolbar)
            .prepareToolbar(androidx.appcompat.R.drawable.abc_ic_ab_back_material, false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                try{
                    findNavController().navigate(R.id.action_SeeOtherOptionsFragment_to_SearchFragment)
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val TAG = "SeeOtherOptionsFragment"
    }
}