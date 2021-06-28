package com.dmytroa.findanime.fragments.seeOtherOptions

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.activity.addCallback
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dmytroa.findanime.MainActivity
import com.dmytroa.findanime.R
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchResult
import com.dmytroa.findanime.databinding.FragmentSeeOtherOptionsBinding
import com.dmytroa.findanime.shared.SharedViewModel
import com.dmytroa.findanime.shared.Utils

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SeeOtherOptionsFragment : Fragment(), OtherOptionsAdapter.OnItemClickListener {

    private val binding get() = _binding!!
    private var _binding: FragmentSeeOtherOptionsBinding? = null
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val viewModel: SeeOtherOptionsViewModel by viewModels {
        SeeOtherOptionsViewModel.SeeOtherOptionsViewModelFactory(requireActivity().application,
            sharedViewModel.selectedItem?.searchItem?.id ?: -1)
    }
    private lateinit var adapter: OtherOptionsAdapter
    private val mActionModeCallback = object: ActionMode.Callback {
        val closeButtonPressed = true
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu): Boolean {
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            try {
                findNavController().navigate(R.id.action_SeeOtherOptionsFragment_to_SearchFragment)
            } catch (e: IllegalArgumentException){
                e.printStackTrace()
            }
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
        sharedViewModel.newSelectedResult = sharedViewModel.selectedItem!!.searchResult!!
        (requireActivity() as OnOtherOptionsFragmentListener).setOtherOptionsFun()
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            findNavController().navigate(R.id.action_SeeOtherOptionsFragment_to_SearchFragment)
        }
        (requireActivity() as MainActivity).startSupportActionMode(mActionModeCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.allResults.observe(viewLifecycleOwner, {
            adapter = OtherOptionsAdapter(it, this, sharedViewModel.newSelectedResult, Utils.getVisibleHeight(requireActivity()))
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

    interface OnOtherOptionsFragmentListener {
        fun setOtherOptionsFun()
    }

    companion object {
        const val TAG = "SeeOtherOptionsFragment"
    }
}