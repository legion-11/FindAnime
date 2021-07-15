package com.dmytroa.findanime.fragments.search

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
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
import com.dmytroa.findanime.fragments.SharedInterfaces
import com.dmytroa.findanime.shared.SharedViewModel
import com.dmytroa.findanime.shared.Utils
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.*
import java.util.*


/**
 * Fragment for showing [com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult]
 * in RecyclerView
 */
class SearchFragment : Fragment(), Interfaces.SubmitSearchRequest,
    SearchItemAdapter.OnSearchAdapterItemClickListener {
    private val binding get() = _binding!!
    private var _binding: FragmentSearchBinding? = null
    private lateinit var viewModel: SearchFragmentViewModel
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private var searchAdapter: SearchItemAdapter? = null
    private var bookmarksMenuItem: MenuItem? = null
    private lateinit var fragmentListener: SharedInterfaces.FragmentListener
    private lateinit var defaultSharedPreferences: SharedPreferences

    //hide MainActivity floating action buttons while scrolling RecyclerView
    private val onScrollListener = object : RecyclerView.OnScrollListener() {

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            Log.i("TAG", "onScrollStateChanged: $newState ")
            when(newState) {
                SCROLL_STATE_DRAGGING -> {
                    fragmentListener.hideMainFab()
                }
                SCROLL_STATE_IDLE -> {
                    // do not show button at the bottom position
                    if (recyclerView.canScrollVertically(-1) &&
                        !recyclerView.canScrollVertically(1)) {
                        fragmentListener.hideMainFab()
                    } else {
                        fragmentListener.showMainFab()
                    }
                }
                else -> {}
            }
        }
    }

    private var mActionMode: ActionMode? = null

    // ActionMode.Callback that shows when selecting item from RecyclerView
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
                        Log.i(TAG, "onActionItemClicked: ${sharedViewModel.selectedItemId.value}")
                        findNavController()
                            .navigate(R.id.action_SearchFragment_to_SeeOtherOptionsFragment)
                    } catch (e: IllegalArgumentException) {
                        Log.i(TAG, "onActionItemClicked: double click")
                    }
                    true
                }
                R.id.action_share -> {
                    sharedViewModel.selectedItemId.value?.let { shareItemById(it) }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            mActionMode = null
        }
    }

    // swipe geastures for RecyclerView
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
                    item?.searchItem?.id?.let { shareItemById(it) }
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

                .addSwipeLeftLabel(getString(R.string.share))
                .addSwipeRightLabel(getString(R.string.delete))

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


    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            fragmentListener = context as SharedInterfaces.FragmentListener
        }catch(e: RuntimeException){
            throw RuntimeException(activity.toString()+" must implement method")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        Log.i(TAG, "onCreateView: ")
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
        Log.i(TAG, "onViewCreated: ")

        defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        // replace selectedItem with new SearchResult
        if (sharedViewModel.makeReplacement) {
            sharedViewModel.makeReplacement = false
            val searchItemId = sharedViewModel.selectedItemId.value
            val newResult = sharedViewModel.newSelectedResult
            if (searchItemId != null && newResult != null) {
                viewModel.replaceWithNewVideo(searchItemId, newResult)
            }
        }

        // setup floating action button function and icon
        fragmentListener.setupFab(android.R.drawable.ic_input_add) {
            fragmentListener.hideShowExtraFabsFunction()
        }
        fragmentListener.restoreExpandableState()

        binding.searchResultRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.searchResultRecyclerView.setHasFixedSize(true)

        binding.searchResultRecyclerView.adapter = searchAdapter
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(binding.searchResultRecyclerView)

        // by notifying data adapter repeat unfinished search requests
        binding.swipeContainer.setOnRefreshListener {
            searchAdapter?.notifyDataSetChanged()
            binding.swipeContainer.isRefreshing = false
        }

        viewModel.items.observe(viewLifecycleOwner, {
            if (searchAdapter == null) {
                searchAdapter = SearchItemAdapter(it, Utils.getVisibleHeight(requireActivity()), this)
                binding.searchResultRecyclerView.adapter = searchAdapter
                Log.i(TAG, "onViewCreated: set dataset ${it.toList()}")
                return@observe
            }
            Log.i(TAG, "onViewCreated: update dataset ${it.toList()}")

            searchAdapter?.setFullDataset(it)
        })

        // show snackbar messages (error messages from view model)
        viewModel.errorMessages.observe(viewLifecycleOwner, {
            if (it == null) return@observe
            fragmentListener.showSnackBar(it)
            viewModel.errorMessages.value = null
        })

        sharedViewModel.filterBookmarks.observe(viewLifecycleOwner, {
            searchAdapter?.isBookmarksFiltered = it
        })

        sharedViewModel.filterText.observe(viewLifecycleOwner, {
            searchAdapter?.textFilter = it
        })

        sharedViewModel.selectedItemId.observe(viewLifecycleOwner, { id ->
            showOrCloseContextualActionBar(id != null)
            searchAdapter?.selectedItemId = id
        })

        // when user reinstall app, app looses permissions to modify files it has created in public storage
        // so we need to request permission
        viewModel.permissionNeededForUpdate.observe(viewLifecycleOwner, { intentSender ->
            intentSender?.let {
                // On Android 10+, if the app doesn't have permission to modify
                // or delete an item, it returns an `IntentSender` that we can
                // use here to prompt the user to grant permission to delete (or modify)
                // the image.
                startIntentSenderForResult(
                    intentSender,
                    UPDATE_PERMISSION_REQUEST,
                    null,
                    0,
                    0,
                    0,
                    null
                )
            }
        })

        viewModel.uriToShare.observe(viewLifecycleOwner, { event ->
            val uri = event.contentIfNotHandled
            Log.i(TAG, "onViewCreated: uriToShare $uri")
            uri?.let {
                share(it)
            } ?: run {
                fragmentListener.showSnackBar(getString(R.string.error_save_to_public_storage))
            }
        })

        binding.searchResultRecyclerView.addOnScrollListener(onScrollListener)


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showOrCloseContextualActionBar(showMenuForSelection: Boolean) {
        if (showMenuForSelection) {
            if (mActionMode != null) return
            mActionMode = (requireActivity() as MainActivity).startSupportActionMode(mActionModeCallback)
        } else {
            mActionMode?.finish()
        }
    }

    override fun onPause() {
        // by doing so we can stop all mediaPlayers that were running at the moment
        searchAdapter?.notifyDataSetChanged()
        super.onPause()
    }

    /**
     * if user unselect do_not_ask_before_search in root_preferences
     * he will be provided with dialog where he can specify search options
     */
    @SuppressLint("InflateParams")
    fun showDialogWithSearchSettings(uriOrUrl: Interfaces.SearchOption) {
        val editor = defaultSharedPreferences.edit()

        var videoSize = defaultSharedPreferences.getString(KEY_SAVED_OPTION_VIDEO_SIZE, "l")!!
        var showHContent = defaultSharedPreferences.getBoolean(KEY_SAVED_OPTION_H_CONTENT, false)
        var muteVideo = defaultSharedPreferences.getBoolean(KEY_SAVED_OPTION_MUTE_VIDEO, false)
        var cutBlackBorders = defaultSharedPreferences.getBoolean(KEY_SAVED_OPTION_CUT_BLACK_BORDERS, true)

        val textToRadioButtonId = hashMapOf(
            R.id.dialogSearchOptionsRadioButtonSmall to "s",
            R.id.dialogSearchOptionsRadioButtonMedium to "m",
            R.id.dialogSearchOptionsRadioButtonLarge to "l"
        )
        fun textToRadioButtonId(str: String): Int {
            return textToRadioButtonId.firstNotNullOf {
                if (it.value == str) return@firstNotNullOf it.key
                else null
            }
        }

        val layout = layoutInflater.inflate(R.layout.dialog_search_prefs, null)

        val radioGroup = layout.findViewById<RadioGroup>(R.id.dialogSearchOptionsRadioGroup)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val checkedText = textToRadioButtonId[checkedId]!!
            videoSize = checkedText
            editor.putString(KEY_SAVED_OPTION_VIDEO_SIZE, checkedText)
            Log.i(TAG, "showDialogWithSearchSettings: radioGroup set $checkedText")
        }
        radioGroup.check(textToRadioButtonId(videoSize))


        val checkBoxMuteVideo = layout.findViewById<CheckBox>(R.id.dialogMutedCheckBox)
        checkBoxMuteVideo.setOnCheckedChangeListener { _, isChecked ->
            muteVideo = isChecked
            editor.putBoolean(KEY_SAVED_OPTION_MUTE_VIDEO, isChecked)
            Log.i(TAG, "showDialogWithSearchSettings: checkBoxMute set $isChecked")
        }
        checkBoxMuteVideo.isChecked = muteVideo

        val checkBoxCutBlackBorders = layout.findViewById<CheckBox>(R.id.dialogSearchOptionsCutBlackBorders)
        checkBoxCutBlackBorders.setOnCheckedChangeListener { _, isChecked ->
            cutBlackBorders = isChecked
            editor.putBoolean(KEY_SAVED_OPTION_CUT_BLACK_BORDERS, isChecked)
            Log.i(TAG, "showDialogWithSearchSettings: checkBoxH set $isChecked")
        }
        checkBoxCutBlackBorders.isChecked = cutBlackBorders

        val checkBoxHContent = layout.findViewById<CheckBox>(R.id.dialogSearchOptionsHContentCheckBox)
        checkBoxHContent.setOnCheckedChangeListener { _, isChecked ->
            showHContent = isChecked
            editor.putBoolean(KEY_SAVED_OPTION_H_CONTENT, isChecked)
            Log.i(TAG, "showDialogWithSearchSettings: checkBoxH set $isChecked")
        }
        checkBoxHContent.isChecked = showHContent

        AlertDialog.Builder(requireContext())
            .setTitle("")
            .setView(layout)
            .setPositiveButton("Ok") { dialog: DialogInterface, _: Int ->
                editor.apply()
                when(uriOrUrl) {
                    is Interfaces.SearchOption.MyUrl ->
                        viewModel.createNewAnimeSearchRequest(
                            uriOrUrl.url, videoSize, muteVideo, cutBlackBorders, showHContent)
                    is Interfaces.SearchOption.MyUri ->
                        viewModel.createNewAnimeSearchRequest(
                            uriOrUrl.uri, videoSize, muteVideo, cutBlackBorders, showHContent)
                }
                dialog.dismiss()
            }
            .show()
    }

    /**
     * submit image/url to viewModel so it can enqueue call request to server
     */
    override fun createRequest(uriOrUrl: Interfaces.SearchOption) {
        Log.i(TAG, "createRequest: ")
        val dontShowDialog = defaultSharedPreferences.getBoolean("do_not_ask_before_search", true)
        if (dontShowDialog) {
            val size = defaultSharedPreferences.getString("video_size", "l")!!
            val showHContent = defaultSharedPreferences.getBoolean("show_h_content", false)
            val muteVideo = defaultSharedPreferences.getBoolean("mute_video", false)
            val cutBlackBorders = defaultSharedPreferences.getBoolean("cut_black_borders", true)

            when(uriOrUrl) {
                is Interfaces.SearchOption.MyUrl ->
                    viewModel.createNewAnimeSearchRequest(
                        uriOrUrl.url, size, muteVideo, cutBlackBorders, showHContent)
                is Interfaces.SearchOption.MyUri ->
                    viewModel.createNewAnimeSearchRequest(
                        uriOrUrl.uri, size, muteVideo, cutBlackBorders, showHContent)
            }
        } else {
            showDialogWithSearchSettings(uriOrUrl)
        }
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

    /**
     * select new item or unselect item if it was selected before
     */
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

    private fun shareItemById(id: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.getSearchItemById(id).videoFileName?.let {
                viewModel.share(it, requireContext())
            }
        }
    }

    /**
     * share video to other application
     * @param uri uri from [android.provider.MediaStore]
     * of video file that was temporary created in publick storage
     */
    private fun share(uri: Uri) {
        Log.i(TAG, "shareItem: $uri")
        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            flags =  Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "video/mp4"
        }
        startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))
    }

    private fun deleteItem(id: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            deleteItem(viewModel.getSearchItemById(id))
        }
    }

    /**
     * deletes item from recyclerview adapter firstly and than room db (ui reacts faster that way)
     */
    private fun deleteItem(searchItem: SearchItem) {
        searchAdapter?.deleteItem(searchItem.id)
        if (sharedViewModel.selectedItemId.value == searchItem.id) {
            sharedViewModel.selectedItemId.value = null
        }
        //TODO make snackbar
        viewModel.delete(searchItem)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == UPDATE_PERMISSION_REQUEST) {
            viewModel.sharePendingImage(requireContext())
        }
    }

    /**
     * setup toolbar menu for that fragment
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        bookmarksMenuItem = menu.findItem(R.id.action_filter_bookmarks)
        bookmarksMenuItem!!.isChecked = sharedViewModel.bookmarksIsChecked
        if (sharedViewModel.bookmarksIsChecked) { bookmarksMenuItem!!.setIcon(R.drawable.ic_baseline_bookmark_24) }
        fragmentListener.prepareToolbar(R.drawable.ic_baseline_menu_24, true)
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
                fragmentListener.openDrawer()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val READ_MEDIA_PERMISSION_REQUEST = 100
        const val UPDATE_PERMISSION_REQUEST = 200
        const val KEY_SAVED_OPTION_VIDEO_SIZE = "key_dialog_video_length"
        const val KEY_SAVED_OPTION_H_CONTENT = "key_dialog_show_h_content"
        const val KEY_SAVED_OPTION_MUTE_VIDEO = "key_dialog_mute_video"
        const val KEY_SAVED_OPTION_CUT_BLACK_BORDERS = "key_dialog_cut_black_borders"
        const val TAG = "SearchFragment"
    }
}