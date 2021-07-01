package com.dmytroa.findanime.fragments.search

import android.content.ContentValues
import android.content.Intent
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import com.dmytroa.findanime.FindAnimeApplication
import com.dmytroa.findanime.MainActivity
import com.dmytroa.findanime.R
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.dmytroa.findanime.databinding.FragmentSearchBinding
import com.dmytroa.findanime.fragments.imageDrawer.ImageDrawerListDialogFragment
import com.dmytroa.findanime.repositories.LocalFilesRepository
import com.dmytroa.findanime.shared.OnFragmentListener
import com.dmytroa.findanime.shared.SharedViewModel
import com.dmytroa.findanime.shared.Utils
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.*
import java.io.File
import java.util.*


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class SearchFragment : Fragment(), ImageDrawerListDialogFragment.OnImageClickListener,
    SearchItemAdapter.OnSearchAdapterItemClickListener {
    private val binding get() = _binding!!
    private var _binding: FragmentSearchBinding? = null
    private lateinit var viewModel: SearchFragmentViewModel
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private var searchAdapter: SearchItemAdapter? = null
    private var bookmarksMenuItem: MenuItem? = null

    private val onScrollListener = object : RecyclerView.OnScrollListener() {

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            Log.i("TAG", "onScrollStateChanged: $newState ")
            when(newState) {
                SCROLL_STATE_DRAGGING -> {
                    (requireActivity() as OnFragmentListener).hideFab()
                }
                SCROLL_STATE_IDLE -> {
                    // do not show button at the bottom position
                    if (!recyclerView.canScrollVertically(1) &&
                        recyclerView.canScrollVertically(-1)) {
                        (requireActivity() as OnFragmentListener).showFab()
                    } else {
                        (requireActivity() as OnFragmentListener).showFab()
                    }
                }
                else -> {}
            }
        }
    }

    private var mActionMode: ActionMode? = null
    private val mActionModeCallback = object: ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu): Boolean {
            mode?.menuInflater?.inflate(R.menu.menu_for_selection, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when(item?.itemId) {
                R.id.action_delete -> {
                    sharedViewModel.selectedItemId.value?.let { deleteItem(it) }
                    mode?.finish()
                    true
                }
                R.id.action_see -> {
                    mode?.finish()
                    try {
                        Log.i("SearchFragment", "onActionItemClicked: ${sharedViewModel.selectedItemId.value}")
                        findNavController()
                            .navigate(R.id.action_SearchFragment_to_SeeOtherOptionsFragment)
                    } catch (e: IllegalArgumentException) {
                        Log.i("SearchFragment", "onActionItemClicked: double click")
                    }
                    true
                }
                R.id.action_share -> {
                    sharedViewModel.selectedItemId.value?.let { shareItem(it) }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            mActionMode = null
        }
    }

    private val simpleCallback = object : ItemTouchHelper.SimpleCallback(
        0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val item = searchAdapter?.getItemByPosition(viewHolder.bindingAdapterPosition)

            when(direction) {
                ItemTouchHelper.LEFT -> {
                    searchAdapter?.notifyItemChanged(viewHolder.bindingAdapterPosition)
                    item?.searchItem?.id?.let { shareItem(it) }
                }
                ItemTouchHelper.RIGHT -> {
                    if (item != null) { deleteItem(item.searchItem) }
                }
            }
        }

        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView,
                                 viewHolder: RecyclerView.ViewHolder, dX: Float,
                                 dY: Float, actionState: Int, isCurrentlyActive: Boolean
        ) {
            RecyclerViewSwipeDecorator.Builder(c, recyclerView, viewHolder, dX, dY, actionState,
                isCurrentlyActive
            )
                .addSwipeLeftBackgroundColor(ContextCompat.getColor(recyclerView.context, R.color.swipe_to_share_bg))
                .addSwipeRightBackgroundColor(ContextCompat.getColor(recyclerView.context, R.color.swipe_to_delete_bg))

                .setSwipeLeftLabelTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    recyclerView.resources.getDimension(R.dimen.item_touch_text_size))
                .setSwipeRightLabelTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    recyclerView.resources.getDimension(R.dimen.item_touch_text_size))

                .addSwipeLeftLabel(recyclerView.resources.getString(R.string.share))
                .addSwipeRightLabel(recyclerView.resources.getString(R.string.delete))

                .setSwipeLeftLabelColor((ContextCompat.getColor(recyclerView.context, R.color.material_card_default_color)))
                .setSwipeRightLabelColor((ContextCompat.getColor(recyclerView.context, R.color.material_card_default_color)))

                .addSwipeLeftActionIcon(R.drawable.ic_baseline_share_48)
                .addSwipeRightActionIcon(R.drawable.ic_baseline_delete_48)

                .setActionIconTint(ContextCompat.getColor(recyclerView.context, R.color.material_card_default_color))
                .create()
                .decorate()
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        Log.i("SearchFragment", "onCreateView: ")
        setHasOptionsMenu(true)
        val application = requireActivity().application as FindAnimeApplication
        viewModel = ViewModelProvider(
            requireActivity(),
            SearchFragmentViewModel.SearchFragmentViewModelFactory(application)
        ).get(SearchFragmentViewModel::class.java)

        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i("SearchFragment", "onViewCreated: ")

        if (sharedViewModel.makeReplacement) {
            sharedViewModel.makeReplacement = false
            val searchItemId = sharedViewModel.selectedItemId.value
            val newResult = sharedViewModel.newSelectedResult
            if (searchItemId != null && newResult != null) {
                viewModel.replaceWithNewVideo(searchItemId, newResult)
            }
        }

        (requireActivity() as OnFragmentListener).setSearchFragmentFun()
        binding.searchResultRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.searchResultRecyclerView.setHasFixedSize(true)

        binding.searchResultRecyclerView.adapter = searchAdapter
        viewModel.items.observe(viewLifecycleOwner, {
            if (searchAdapter == null) {
                searchAdapter = SearchItemAdapter(it, Utils.getVisibleHeight(requireActivity()), this)
                binding.searchResultRecyclerView.adapter = searchAdapter
                val itemTouchHelper = ItemTouchHelper(simpleCallback)
                itemTouchHelper.attachToRecyclerView(binding.searchResultRecyclerView)
                Log.i("SearchFragment", "onViewCreated: set dataset ${it.toList()}")
                return@observe
            }
            Log.i("SearchFragment", "onViewCreated: update dataset ${it.toList()}")

            searchAdapter?.setFullDataset(it)
        })

        sharedViewModel.filterBookmarks.observe(viewLifecycleOwner, {
            searchAdapter?.isBookmarksFiltered = it
        })

        sharedViewModel.filterText.observe(viewLifecycleOwner, {
            searchAdapter?.textFilter = it
        })

        sharedViewModel.selectedItemId.observe(viewLifecycleOwner, { id ->
            showContextualActionBar(id != null)
            searchAdapter?.selectedItemId = id
        })

        binding.searchResultRecyclerView.addOnScrollListener(onScrollListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun showContextualActionBar(showMenuForSelection: Boolean) {
        if (showMenuForSelection) {
            if (mActionMode != null) return
            mActionMode = (requireActivity() as MainActivity).startSupportActionMode(mActionModeCallback)
        } else {
            mActionMode?.finish()
        }
    }

    override fun onImageClick(imageUri: Uri) {
        viewModel.createNewAnimeSearchRequest(imageUri)
    }

    // FileProvider's uri sends video as a file to telegram
    // https://stackoverflow.com/a/63600425/15225582
    private fun getVideoContentUri(videoFile: File): Uri? {
        var uri: Uri? = null
        val cursor = context?.contentResolver?.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID),
            MediaStore.Video.Media.DISPLAY_NAME + "=? ",
            arrayOf(videoFile.name), null)

        if (cursor != null && cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))
            val baseUri = Uri.parse("content://media/external/video/media")
            uri = Uri.withAppendedPath(baseUri, "" + id)
        } else if (videoFile.exists()) {
            val values = ContentValues()
            values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
            uri = context?.contentResolver?.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }

        cursor?.close()
        return uri
    }

    override fun openMal(idMal: Int) {
        val url = "https://myanimelist.net/anime/$idMal"
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        startActivity(i)
    }

    override fun setIsBookmarked(isChecked: Boolean, searchItem: SearchItem) {
        viewModel.setIsBookmarked(isChecked, searchItem)
    }

    override fun repeatAnimeSearchRequest(item: SearchItemWithSelectedResult) {
        viewModel.repeatAnimeSearchRequest(item)
    }

    override fun setSelectedItemId(item: SearchItemWithSelectedResult) {
        item.searchResult?.let {
            sharedViewModel.selectedItemId.value =
                if (sharedViewModel.selectedItemId.value == item.searchItem.id ) {
                    null
                } else {
                    item.searchItem.id
                }
        }
    }

    private fun shareItem(id: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.getSearchItemById(id).videoFileName?.let { shareItem(it) }
        }
    }

    private fun shareItem(fileName: String) {
        val videoContentUri = getVideoContentUri(File(LocalFilesRepository.getFullVideoURI(fileName, requireContext())))
        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_STREAM,
                videoContentUri
            )
            type = "video/*"
        }
        startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))
    }
    private fun deleteItem(id: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            deleteItem(viewModel.getSearchItemById(id))
        }
    }
    private fun deleteItem(searchItem: SearchItem) {
        searchAdapter?.deleteItem(searchItem.id)
        if (sharedViewModel.selectedItemId.value == searchItem.id) {
            sharedViewModel.selectedItemId.value = null
        }
        //TODO make snackbar
        viewModel.delete(searchItem)
    }

    companion object {
        const val REQUEST_PERMISSION = 100
    }

    interface OnCreateToolbar {
        fun prepareToolbar(resId: Int, searchViewIsVisible: Boolean)
        fun openDrawer()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        bookmarksMenuItem = menu.findItem(R.id.action_filter_bookmarks)
        bookmarksMenuItem!!.isChecked = sharedViewModel.bookmarksIsChecked
        if (sharedViewModel.bookmarksIsChecked) { bookmarksMenuItem!!.setIcon(R.drawable.ic_baseline_bookmark_24) }
        (requireActivity() as OnCreateToolbar).prepareToolbar(R.drawable.ic_baseline_menu_24, true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter_bookmarks -> {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    item.setIcon(R.drawable.ic_baseline_bookmark_24)
                } else {
                    item.setIcon(R.drawable.ic_baseline_bookmark_border_24)
                }
                sharedViewModel.setFilterBookmarks(item.isChecked)
                true
            }
            android.R.id.home -> {
                (requireActivity() as OnCreateToolbar).openDrawer()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}