package com.dmytroa.findanime.fragments.search

import android.content.ContentValues
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.view.ActionMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.dmytroa.findanime.FindAnimeApplication
import com.dmytroa.findanime.MainActivity
import com.dmytroa.findanime.R
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.dmytroa.findanime.databinding.FragmentSearchBinding
import com.dmytroa.findanime.databinding.SearchItemsBinding
import com.dmytroa.findanime.fragments.imageDrawer.ImageDrawerListDialogFragment
import com.dmytroa.findanime.shared.SharedViewModel
import com.dmytroa.findanime.shared.Utils
import com.google.android.material.floatingactionbutton.FloatingActionButton
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.*
import java.io.File
import java.util.*


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class SearchFragment : Fragment(), ImageDrawerListDialogFragment.OnImageClickListener {
    private val binding get() = _binding!!
    private var _binding: FragmentSearchBinding? = null
    private lateinit var viewModel: SearchFragmentViewModel
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private var searchAdapter: SearchItemAdapter? = null
    private lateinit var fab: FloatingActionButton

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
            val position = viewHolder.bindingAdapterPosition

            when(direction) {
                ItemTouchHelper.LEFT -> {
                    searchAdapter?.shareItem(position)
                    searchAdapter?.notifyItemChanged(position)
                }
                ItemTouchHelper.RIGHT -> {
                    val deletedItem = searchAdapter?.deleteItem(position)
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
                .addSwipeLeftBackgroundColor(ContextCompat.getColor(requireContext(), R.color.swipe_to_share_bg))
                .addSwipeRightBackgroundColor(ContextCompat.getColor(requireContext(), R.color.swipe_to_delete_bg))

                .setSwipeLeftLabelTextSize(TypedValue.COMPLEX_UNIT_SP,
                    resources.getDimension(R.dimen.item_touch_text_size))
                .setSwipeRightLabelTextSize(TypedValue.COMPLEX_UNIT_SP,
                    resources.getDimension(R.dimen.item_touch_text_size))

                .addSwipeLeftLabel(resources.getString(R.string.share))
                .addSwipeRightLabel(resources.getString(R.string.delete))

                .setSwipeLeftLabelColor((ContextCompat.getColor(requireContext(), R.color.material_card_default_color)))
                .setSwipeRightLabelColor((ContextCompat.getColor(requireContext(), R.color.material_card_default_color)))

                .addSwipeLeftActionIcon(R.drawable.ic_baseline_share_48)
                .addSwipeRightActionIcon(R.drawable.ic_baseline_delete_48)

                .setActionIconTint(ContextCompat.getColor(requireContext(), R.color.material_card_default_color))
                .create()
                .decorate()
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    private val onScrollListener = object : RecyclerView.OnScrollListener() {

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            Log.i("TAG", "onScrollStateChanged: $newState ")
            when(newState) {
                SCROLL_STATE_DRAGGING -> {
                    (requireActivity() as OnSearchFragmentListener).hideFab()
                }
                SCROLL_STATE_IDLE -> {
                    // do not show button at the bottom position
                    if (!recyclerView.canScrollVertically(1) &&
                        recyclerView.canScrollVertically(-1)) {
                        (requireActivity() as OnSearchFragmentListener).showFab()
                    } else {
                        (requireActivity() as OnSearchFragmentListener).showFab()
                    }
                }
                else -> {}
            }
        }
    }

    private var mActionMode: ActionMode? = null
    private val mActionModeCallback = object: ActionMode.Callback {
        private var needUnselection = true

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
                    searchAdapter?.deleteItem()
                    needUnselection = true
                    mode?.finish()
                    true
                }
                R.id.action_see -> {
                    needUnselection = false
                    mode?.finish()
                    sharedViewModel.selectedItem?.searchResult?.let {
                        findNavController()
                            .navigate(R.id.action_SearchFragment_to_SeeOtherOptionsFragment)
                    }
                    true
                }
                R.id.action_share -> {
                    searchAdapter?.shareItem()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            if (needUnselection){
                searchAdapter?.unselectAll()
            }
            mActionMode = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = requireActivity().application as FindAnimeApplication
        viewModel = ViewModelProvider(
            requireActivity(),
            SearchFragmentViewModel.SearchFragmentViewModelFactory(application)
        ).get(SearchFragmentViewModel::class.java)
        Log.i("TAG", "onCreate2: $sharedViewModel")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showContextualActionBar(sharedViewModel.selectedItem != null)

        arguments?.getBoolean(MainActivity.KEY_INVOKE_VIDEO_REPLACEMENT)?.let {
            if (sharedViewModel.selectedItem?.searchItem?.selectedResultId
                != sharedViewModel.newSelectedResult.id) {
                viewModel.replaceWithNewVideo(
                    sharedViewModel.selectedItem!!.searchItem,
                    sharedViewModel.newSelectedResult
                )
            }
        }

        (requireActivity() as OnSearchFragmentListener).setSearchFragmentFun()
        binding.searchResultRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.searchResultRecyclerView.setHasFixedSize(true)
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(binding.searchResultRecyclerView)

        viewModel.items.observe(viewLifecycleOwner, {
            if (searchAdapter == null) {
                searchAdapter = SearchItemAdapter(it, Utils.getVisibleHeight(requireActivity()))
                binding.searchResultRecyclerView.adapter = searchAdapter
                return@observe
            }
            searchAdapter?.setFullDataset(it)
        })

        sharedViewModel.filterBookmarks.observe(viewLifecycleOwner, {
            searchAdapter?.filter?.filter(sharedViewModel.filterText.value)
        })

        sharedViewModel.filterText.observe(viewLifecycleOwner, {
            searchAdapter?.filter?.filter(it)
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

    fun openMal(idMal: Int) {
        val url = "https://myanimelist.net/anime/$idMal"
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        startActivity(i)
    }

    fun shareViaIntent(fileName: String) {
        val videoContentUri = getVideoContentUri(File(viewModel.getFullVideoURI(fileName)))
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

    companion object {
        const val REQUEST_PERMISSION = 100
    }

    private inner class SearchItemAdapter(
        private var allItems: Array<SearchItemWithSelectedResult>,
        private val maxHeight: Int
    ): RecyclerView.Adapter<SearchItemAdapter.BaseViewHolder>(), Filterable {

        private var filteredItems: Array<SearchItemWithSelectedResult> = getFilteredArray(textFilter)

        private var selectedItem get() = sharedViewModel.selectedItem
            set(value) {
                sharedViewModel.selectedItem = value
            }

        private val textFilter get() = sharedViewModel.filterText.value ?: ""
        private val isBookmarksFiltered get() = sharedViewModel.filterBookmarks.value ?: false


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            val itemView = SearchItemsBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
            setMaxHeightToVisibleHeightOfDeviceScreen(itemView)
            return ViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
            val item = filteredItems[position]
            (holder as ViewHolder).bind(item)
        }

        override fun onViewDetachedFromWindow(holder: BaseViewHolder) {
            Log.i("SearchItemAdapter", "onViewDetachedFromWindow: ")
            holder as ViewHolder
            holder.thumbnailImageView.visibility = View.VISIBLE
            holder.videoView.apply {
                clearAnimation()
                setVideoURI(null)
                stopPlayback()
                alpha = 0f
            }
            super.onViewDetachedFromWindow(holder)
        }

        override fun getItemCount(): Int = filteredItems.size
        private var needScroll = false
        fun setFullDataset(newDataset : Array<SearchItemWithSelectedResult>) {
            val oldItems = allItems
            needScroll = allItems.size == filteredItems.size && oldItems.size < newDataset.size
            allItems = newDataset
            filter.filter(textFilter)
        }

        fun updateDataset(newDataset : Array<SearchItemWithSelectedResult>) {
            val oldItems = filteredItems
            filteredItems = newDataset
            val diffResult = DiffUtil.calculateDiff(SearchItemDiffCallback(oldItems, newDataset))
            diffResult.dispatchUpdatesTo(this)

            if (textFilter.isNotEmpty() || needScroll) {
                needScroll = false
                binding.searchResultRecyclerView.scrollToPosition(0)
                // if you do not notify it takes focus from search view
                if (textFilter.isNotEmpty()) { notifyDataSetChanged()}
            }
        }

        private fun setMaxHeightToVisibleHeightOfDeviceScreen(itemView: SearchItemsBinding) {
            val params = itemView.videoContainer.layoutParams as ConstraintLayout.LayoutParams
            params.matchConstraintMaxHeight = maxHeight
            itemView.videoContainer.layoutParams = params
        }

        fun deleteItem(position: Int): SearchItemWithSelectedResult? {
            if (position < 0 || position > filteredItems.size - 1) return null
            val item = filteredItems[position]
            deleteItem(item)
            return item
        }

        fun deleteItem(item: SearchItemWithSelectedResult? = selectedItem) {
            item?.let {
                val newItems = filteredItems.toCollection(mutableListOf())
                newItems.remove(it)
                setFullDataset(newItems.toTypedArray())
                if (selectedItem?.searchItem?.id == item.searchItem.id) {selectedItem = null}
                viewModel.delete(it.searchItem)
            }
        }

        fun shareItem(position: Int) {
            if (position < 0 || position > filteredItems.size - 1) return
            val item = filteredItems[position]
            shareItem(item)
        }

        fun shareItem(item: SearchItemWithSelectedResult? = selectedItem){
            item?.searchItem?.videoFileName?.let { shareViaIntent(it) }
        }

        fun unselectAll() {
            val lastSelected = selectedItem
            selectedItem = null
            notifyItemChanged(filteredItems.indexOfFirst { it.searchItem.id == lastSelected?.searchItem?.id })
        }

        private fun getFilteredArray(charSearch: String): Array<SearchItemWithSelectedResult> {
            val firstFilter = if (charSearch.isEmpty()) {
                allItems
            } else {
                allItems.sortedWith(compareReversed(charSearch)).toTypedArray()
            }
            return if (!isBookmarksFiltered) {
                firstFilter
            } else {
                val filteredListParent = arrayListOf<SearchItemWithSelectedResult>()
                for (item in firstFilter) {
                    if (item.searchItem.isBookmarked) {
                        filteredListParent.add(item)
                    }
                }
                filteredListParent.toTypedArray()
            }
        }

        private fun compareReversed(str: String) = Comparator<SearchItemWithSelectedResult> { o1, o2 ->
            o2.getTextComparisonScore(str) - o1.getTextComparisonScore(str)
        }

        override fun getFilter(): Filter {
            return  object : Filter() {

                override fun performFiltering(constraint: CharSequence): FilterResults {
                    val charSearch = constraint.toString()
                    val results = FilterResults()
                    results.values = getFilteredArray(charSearch)
                    return results
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence, results: FilterResults) {
                    val newData = results.values as Array<SearchItemWithSelectedResult>
                    updateDataset(newData)
                }
            }
        }

        //just in case I will do some other viewHolders
        private abstract inner class BaseViewHolder(itemBinding: ViewBinding):
            RecyclerView.ViewHolder(itemBinding.root)

        private inner class ViewHolder(itemBinding: SearchItemsBinding):
            BaseViewHolder(itemBinding), View.OnClickListener, View.OnLongClickListener {

            val fileNameTV: TextView = itemBinding.nameTextView
            val similarityTV: TextView = itemBinding.similarityTextView
            val videoView: VideoView = itemBinding.videoView
            val thumbnailImageView: ImageView = itemBinding.thumbnailImageView
            val videoContainer = itemBinding.videoContainer
            val buttonsContainer = itemBinding.buttonsContainer
            val textContainer = itemBinding.textContainer
            val toggleBookmarks = itemBinding.toggleBookmarks
            val malButton = itemBinding.MALImageButton
            val root = itemBinding.root

            init {
                textContainer.layout = R.layout.default_text_layout
                buttonsContainer.layout = R.layout.default_buttons_layout

                //resize to save aspect ratio
                videoView.setOnPreparedListener { mp -> //Get your video's width and height
                    resizeVideo(mp)
                    showVideoViewOnceVideoIsFullyPrepared(mp)
                }
            }

            private fun resizeVideo(mp: MediaPlayer){
                val videoWidth = mp.videoWidth
                val videoHeight = mp.videoHeight

                //Get VideoView's current width and height
                val containerViewWidth: Int = videoContainer.width
                val containerViewHeight: Int = videoContainer.height
                val xScale = containerViewWidth.toFloat() / videoWidth
                val yScale = containerViewHeight.toFloat() / videoHeight

                //For Center Crop use the Math.max to calculate the scale
                //float scale = Math.max(xScale, yScale);
                //For Center Inside use the Math.min scale.
                //I prefer Center Inside so I am using Math.min
                val scale = xScale.coerceAtMost(yScale)
                val scaledWidth = scale * videoWidth
                val scaledHeight = scale * videoHeight

                //Set the new size for the VideoView based on the dimensions of the video
                val layoutParams: ViewGroup.LayoutParams = videoView.layoutParams

                layoutParams.width = scaledWidth.toInt()
                layoutParams.height = scaledHeight.toInt()
                videoView.layoutParams = layoutParams
                videoView.start()
            }

            private fun showVideoViewOnceVideoIsFullyPrepared(mp: MediaPlayer?) {
                CoroutineScope(Dispatchers.IO).launch {
                    var started = false
                    while (!started) {
                        try {
                            if (mp != null && mp.currentPosition > 0) {
                                withContext(Dispatchers.Main) {
                                    videoView.alpha = 1f
                                    thumbnailImageView.visibility = View.GONE
                                    started = true
                                    return@withContext
                                }
                            }
                            delay(10)
                        } catch (e : IllegalStateException) {
                            return@launch
                        }
                    }
                }
            }

            private fun unVeilVideo() {
                videoContainer.unVeil()
                buttonsContainer.apply { unVeil(); visibility = View.GONE }
            }

            private fun veilVideo() {
                videoContainer.veil()
                buttonsContainer.apply { veil(); visibility = View.VISIBLE }
            }

            private fun loadVideo(item: SearchItem) {
                val videoURI = item.videoFileName?.let { viewModel.getFullVideoURI(it) }

                if (videoURI == null) {
                    veilVideo()
                    thumbnailImageView.setImageDrawable(null)
                    return
                }
                videoView.isFocusable = false
                thumbnailImageView.bringToFront()

                Glide.with(requireActivity())
                    .load(Uri.fromFile(File(videoURI)))
                    .thumbnail(0.1f)
                    //unveil video once image is loaded
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            unVeilVideo()
                            Log.i("SearchItemAdapter", "onCreateViewHolder: delete")
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable?,
                            model: Any?,
                            target: Target<Drawable>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            unVeilVideo()
                            return false
                        }

                    })
                    .into(thumbnailImageView)
                thumbnailImageView.setOnLongClickListener(this@ViewHolder)
                thumbnailImageView.setOnClickListener(this@ViewHolder)
                videoView.setOnClickListener(this@ViewHolder)
                videoView.setOnLongClickListener(this@ViewHolder)

            }

            private fun selectUnselectItem() {
                val item = filteredItems[bindingAdapterPosition]
                val lastSelected = selectedItem
                selectedItem = if (lastSelected?.searchItem?.id != item.searchItem.id) {
                    unselectAll()
                    item
                } else {
                    null
                }
                changeStrokeColor()

                showContextualActionBar(selectedItem != null)

            }

            private fun isSelected(): Boolean {
                val item = filteredItems[bindingAdapterPosition]
                return item.searchItem.id == selectedItem?.searchItem?.id
            }

            private fun changeStrokeColor() {
                Log.i("TAG", "changeStrokeColor: ${selectedItem?.searchItem?.id}")
                Log.i("TAG", "changeStrokeColor: ${isSelected()}")
                root.isSelected = isSelected()
            }

            fun bind(item: SearchItemWithSelectedResult) {
                Log.i("ViewHolder", "onBindViewHolder: $item")

                val itemData = item.searchResult
                if (!item.searchItem.isFinished) {
                    viewModel.repeatAnimeSearchRequest(item)
                }

                fileNameTV.text = item.getName()
                similarityTV.text = itemData?.similarity
                toggleBookmarks.isChecked = item.searchItem.isBookmarked
                toggleBookmarks.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.setIsBookmarked(isChecked, item)
                }
                malButton.setOnClickListener(this@ViewHolder)
                if (fileNameTV.text.isNotBlank()) { textContainer.unVeil(); textContainer.visibility = View.GONE }
                else { textContainer.veil(); textContainer.visibility = View.VISIBLE  }

                changeStrokeColor()
                loadVideo(item.searchItem)
                itemView.setOnClickListener(this)
            }

            override fun onClick(v: View?) {
                if (bindingAdapterPosition<0) return
                val item = filteredItems[bindingAdapterPosition]
                when(v) {
                    itemView -> selectUnselectItem()
                    thumbnailImageView -> {
                        Log.i("TAG", "bind: loading video ")
                        item.searchItem.let {
                            val fullURI = item.searchItem.videoFileName?.let { viewModel.getFullVideoURI(it) }
                            fullURI?.let {
                                videoView.setVideoURI(Uri.fromFile(File(it)))
                            }
                        }
                    }
                    videoView -> {
                        if (videoView.isPlaying) { videoView.pause() }
                        else { videoView.start() }
                    }
                    malButton -> {
                        item.searchResult?.idMal?.let { idMal ->
                            openMal(idMal)
                        }
                    }
                    else -> {}
                }
            }

            override fun onLongClick(v: View?): Boolean {
                Log.i("ViewHolder", "onLongClick: ")
                return when(v) {
                    thumbnailImageView -> {
                        root.performClick()
                        root.isPressed = true
                        root.isPressed = false
                        true
                    }
                    videoView -> {
                        root.performClick()
                        root.isPressed = true
                        root.isPressed = false
                        true
                    }
                    else -> { false }
                }
            }
        }

    }

    private class SearchItemDiffCallback(
        var oldImages: Array<SearchItemWithSelectedResult>,
        var newImages: Array<SearchItemWithSelectedResult> ): DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldImages.size
        }

        override fun getNewListSize(): Int {
            return newImages.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldImages[oldItemPosition].searchItem.id == newImages[newItemPosition].searchItem.id
                    && oldImages[oldItemPosition].searchResult?.id == newImages[newItemPosition].searchResult?.id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldImages[oldItemPosition] == newImages[newItemPosition]
        }
    }

    interface OnSearchFragmentListener {
        fun hideFab()
        fun showFab()
        fun setSearchFragmentFun()
    }
}