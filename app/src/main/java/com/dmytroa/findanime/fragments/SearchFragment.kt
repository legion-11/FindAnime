package com.dmytroa.findanime.fragments

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.*
import java.io.File


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class SearchFragment : Fragment(), ImageDrawerListDialogFragment.OnImageClickListener,
    MainActivity.OnActionBarCallback {
    private val binding get() = _binding!!
    private var _binding: FragmentSearchBinding? = null
    private lateinit var viewModel: SearchFragmentViewModel
    private var searchAdapter: SearchItemAdapter? = null

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
                    searchAdapter?.share(position)
                    searchAdapter?.notifyItemChanged(position)
                }
                ItemTouchHelper.RIGHT -> {
                    val deletedItem = searchAdapter?.deleteItem(position)
                    deletedItem?.let {
                        if (!deletedItem.searchItem.isFinished) return@let
                        Snackbar.make(binding.searchResultRecyclerView,
                            it.searchResult?.fileName ?: "", Snackbar.LENGTH_LONG)
                            .setAction("UNDO") {
                                //todo
//                                viewModel.launchInsert(deletedItem)
                            }
                            .show()
                    }
                }
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            RecyclerViewSwipeDecorator.Builder(
                c,
                recyclerView,
                viewHolder,
                dX,
                dY,
                actionState,
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
                    (activity as MainActivity).hideFab()
                }
                SCROLL_STATE_IDLE -> {
                    // do not show button at the bottom position
                    if (!recyclerView.canScrollVertically(1) &&
                        recyclerView.canScrollVertically(-1)) {
                        (activity as MainActivity).hideFab()
                    } else {
                        (activity as MainActivity).showFab()
                    }
                }
                else -> {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(
            requireActivity(),
            SearchFragmentViewModel.SearchFragmentViewModelFactory(
                (requireActivity().application as FindAnimeApplication).repository
            )
        ).get(SearchFragmentViewModel::class.java)
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.searchResultRecyclerView.layoutManager = LinearLayoutManager(context)

        binding.searchResultRecyclerView.setHasFixedSize(true)
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(binding.searchResultRecyclerView)

        viewModel.items.observe(viewLifecycleOwner, {
            if (searchAdapter == null) {

                searchAdapter = SearchItemAdapter(it)
                binding.searchResultRecyclerView.adapter = searchAdapter
            }
            searchAdapter?.updateDataset(it)
        })

        binding.searchResultRecyclerView.addOnScrollListener(onScrollListener)

//        binding.button.setOnClickListener {
//            val responseLiveData: LiveData<Response<Quota>> = liveData {
//                val response = searchService.getQuota()
//                emit(response)
//            }
//            responseLiveData.observe(viewLifecycleOwner) {
//                if(it.isSuccessful) {
//                    Log.i("TAG", "addElement: ${it.body()}")
//                }
//            }
//        }
    }

    override fun onImageClick(imageUri: Uri) {
        viewModel.createNewAnimeSearchRequest(imageUri, requireContext())
    }

    private fun showContextualActionBar(showMenu: Boolean) {
        (activity as MainActivity).showContextualActionBar(showMenu)
    }

    override fun delete() {
        searchAdapter?.deleteItem()
    }

    override fun unselectAll() {
        searchAdapter?.unselectAll()
    }

    override fun share() {
        searchAdapter?.shareItem()
    }

    fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else 0
    }

    fun getToolbarBarHeight(): Int {
        val tv = TypedValue()
        if (requireActivity().theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            return TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        }
        return 0
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val outMetrics = DisplayMetrics()
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = activity?.display
            display?.getRealMetrics(outMetrics)
        } else {
            @Suppress("DEPRECATION")
            val display = activity?.windowManager?.defaultDisplay
            @Suppress("DEPRECATION")
            display?.getMetrics(outMetrics)
        }
        return outMetrics
    }


    private inner class SearchItemAdapter(private var items: Array<SearchItemWithSelectedResult>):
        RecyclerView.Adapter<SearchItemAdapter.BaseViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            val itemView = SearchItemsBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
            setMaxHeightToVisibleHeightOfDeviceScreen(itemView)
            return ViewHolder(itemView)
        }

        private fun setMaxHeightToVisibleHeightOfDeviceScreen(itemView: SearchItemsBinding) {
            val params = itemView.videoContainer.layoutParams as ConstraintLayout.LayoutParams
            val height = getDisplayMetrics().heightPixels
            params.matchConstraintMaxHeight = height - getStatusBarHeight() - getToolbarBarHeight()
            itemView.videoContainer.layoutParams = params
        }

        override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
            val item = items[position]
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

        override fun getItemCount(): Int {
            return items.size
        }

        fun deleteItem(item: SearchItemWithSelectedResult? = viewModel.selectedItem) {
            item?.let {
                val newItems = items.toCollection(mutableListOf())
                newItems.remove(it)
                updateDataset(newItems.toTypedArray())
                viewModel.delete(it.searchItem, requireContext())
            }
        }
        fun shareItem(item: SearchItemWithSelectedResult? = viewModel.selectedItem){
            item?.searchItem?.videoURI?.let {

                val shareIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM,
                        getVideoContentUri(
                            File(
                                viewModel.getFullVideoURI(it, requireContext())
                            )
                        )
                    )
                    type = "video/*"
                }

                startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))

            }
        }

        fun getVideoContentUri(videoFile: File): Uri? {
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

        fun shareItem(position: Int) {
            if (position < 0 || position > items.size - 1) return
            val item = items[position]
            shareItem(item)
        }

        fun deleteItem(position: Int): SearchItemWithSelectedResult? {
            if (position < 0 || position > items.size - 1) return null
            val item = items[position]
            deleteItem(item)
            return item
        }

        fun share(position: Int) {
            if (position < 0 || position > items.size - 1) return
            val item = items[position]

        }

        fun updateDataset(newDataset : Array<SearchItemWithSelectedResult>) {
            Log.i("ViewHolder", "updateDataset: $newDataset")
            val oldItems = items
            val diffResult = DiffUtil.calculateDiff(SearchItemDiffCallback(oldItems, newDataset))
            val needScroll = oldItems.size < newDataset.size
            items = newDataset
            diffResult.dispatchUpdatesTo(this)
            if (needScroll) {binding.searchResultRecyclerView.smoothScrollToPosition(0)}
        }

        fun unselectAll() {
            val lastSelected = viewModel.selectedItem
            viewModel.selectedItem = null
            notifyItemChanged(items.indexOfFirst { it.searchItem.id == lastSelected?.searchItem?.id })
        }

        //just in case I will do some other viewHolders
        private abstract inner class BaseViewHolder(binding: ViewBinding):
            RecyclerView.ViewHolder(binding.root)

        private inner class ViewHolder(binding: SearchItemsBinding):
            BaseViewHolder(binding), View.OnClickListener, View.OnLongClickListener {

            val fileNameTV: TextView = binding.nameTextView
            val similarityTV: TextView = binding.similarityTextView
            val videoView: VideoView = binding.videoView
            val thumbnailImageView: ImageView = binding.thumbnailImageView
            val videoContainer = binding.videoContainer
            val buttonsContainer = binding.buttonsContainer
            val textContainer = binding.textContainer
            val toggleBookmarks = binding.toggleBookmarks
            val root = binding.root

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
                val videoURI = item.videoURI?.let { viewModel.getFullVideoURI(it, requireContext()) }

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

            private fun isSelected(): Boolean {
                val item = items[bindingAdapterPosition]
                return item.searchItem.id == viewModel.selectedItem?.searchItem?.id
            }

            private fun selectUnselectItem() {
                val item = items[bindingAdapterPosition]
                val lastSelected = viewModel.selectedItem
                if (lastSelected?.searchItem?.id != item.searchItem.id) {
                    unselectAll()
                    viewModel.selectedItem = item
                } else { viewModel.selectedItem = null }

                changeStrokeColor()
                showContextualActionBar(viewModel.selectedItem != null)
            }

            private fun changeStrokeColor() {
                root.isSelected = isSelected()
            }

            fun bind(item: SearchItemWithSelectedResult) {
                Log.i("ViewHolder", "onBindViewHolder: $item")

                val itemData = item.searchResult
                if (!item.searchItem.isFinished) {
                    viewModel.repeatAnimeSearchRequest(item, requireContext())
                }

                fileNameTV.text = ((itemData?.english ?: itemData?.romaji) ?:
                    (itemData?.nativeTitle ?: itemData?.fileName)) ?: ""
                similarityTV.text = itemData?.similarity
                toggleBookmarks.isChecked = item.searchItem.isBookmarked
                toggleBookmarks.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.setIsBookmarked(isChecked, item)
                }

                if (fileNameTV.text.isNotBlank()) { textContainer.unVeil(); textContainer.visibility = View.GONE }
                else { textContainer.veil(); textContainer.visibility = View.VISIBLE  }

                changeStrokeColor()
                loadVideo(item.searchItem)
                itemView.setOnClickListener(this)
            }

            override fun onClick(v: View?) {
                val item = items[bindingAdapterPosition]
                when(v) {
                    itemView -> selectUnselectItem()
                    thumbnailImageView -> {
                        Log.i("TAG", "bind: loading video ")
                        item.searchItem.let {
                            val fullURI = item.searchItem.videoURI?.let { viewModel.getFullVideoURI(it, requireContext()) }
                            fullURI?.let {
                                videoView.setVideoURI(Uri.fromFile(File(it)))
                            }
                        }
                    }
                    videoView -> {
                        if (videoView.isPlaying) { videoView.pause() }
                        else { videoView.start() }
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
}