package com.dmytroa.findanime.fragments

import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.constraintlayout.widget.ConstraintLayout
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
import com.dmytroa.findanime.databinding.FragmentSearchBinding
import com.dmytroa.findanime.databinding.SearchItemsBinding
import com.google.android.material.snackbar.Snackbar
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
                    searchAdapter?.notifyItemChanged(position)
                }
                ItemTouchHelper.RIGHT -> {
                    val deletedItem = searchAdapter?.deleteItem(position)
                    deletedItem?.let {
                        if (!deletedItem.finished) return@let
                        Snackbar.make(binding.searchResultRecyclerView, it.fileName ?: "", Snackbar.LENGTH_LONG)
                            .setAction("UNDO") {
                                viewModel.launchInsert(deletedItem)
                            }
                            .show()
                    }

                }
            }
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

    private fun showContextualActionBar(showMenu: Boolean, isBookmarked: Boolean) {
        (activity as MainActivity).showContextualActionBar(showMenu, isBookmarked)
    }

    override fun delete() {
        searchAdapter?.deleteItem()
    }

    override fun unselectAll() {
        searchAdapter?.unselectAll()
    }

    override fun setIsBookmarked(b: Boolean) {
        viewModel.setIsBookmarked(b)
    }

    fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else 0
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


    private inner class SearchItemAdapter(private var items: Array<SearchItem>):
        RecyclerView.Adapter<SearchItemAdapter.BaseViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            val itemView = SearchItemsBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
            val params = itemView.videoContainer.layoutParams as ConstraintLayout.LayoutParams
            val height = getDisplayMetrics().heightPixels
            params.matchConstraintMaxHeight = height - getStatusBarHeight() - 10
            itemView.videoContainer.layoutParams = params
            return ViewHolder(itemView)
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

        fun deleteItem(searchItem: SearchItem? = viewModel.selectedItem) {
            if (searchItem == null) return
            val newItems = items.toCollection(mutableListOf())
            newItems.remove(searchItem)
            updateDataset(newItems.toTypedArray())
            viewModel.delete(searchItem)
        }

        fun deleteItem(position: Int): SearchItem? {
            if (position < 0 || position > items.size - 1) return null
            val item = items[position]
            deleteItem(item)
            return item
        }

        fun updateDataset(newDataset : Array<SearchItem>) {
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
            notifyItemChanged(items.indexOfFirst { it.id == lastSelected?.id })
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
                if (item.video == null) {
                    veilVideo()
                    thumbnailImageView.setImageDrawable(null)
                    return
                }
                videoView.isFocusable = false
                thumbnailImageView.bringToFront()

                Glide.with(requireActivity())
                    .load(Uri.fromFile(File(item.video!!)))
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
                            Log.i("SearchItemAdapter", "onCreateViewHolder: ${videoContainer.height}")
                            return false
                        }

                    })
                    .into(thumbnailImageView)

                thumbnailImageView.setOnLongClickListener(this@ViewHolder)
                videoView.setOnLongClickListener(this@ViewHolder)
                thumbnailImageView.setOnClickListener(this@ViewHolder)
                videoView.setOnClickListener(this@ViewHolder)
            }

            private fun selectUnselectItem(item: SearchItem) {
                val lastSelected = viewModel.selectedItem
                if (lastSelected?.id != item.id) {
                    unselectAll()
                    viewModel.selectedItem = item
                } else { viewModel.selectedItem = null }

                changeStrokeColor(item)
                showContextualActionBar(viewModel.selectedItem != null, item.isBookmarked)
            }

            private fun changeStrokeColor(item: SearchItem) {
                root.isSelected = item.id == viewModel.selectedItem?.id
            }

            fun bind(item: SearchItem) {
                if (!item.finished) { viewModel.repeatAnimeSearchRequest(item, requireContext()) }

                fileNameTV.text = item.fileName ?: ""
                similarityTV.text = if(item.similarity != null) item.similarity.toString() else ""

                Log.i("ViewHolder", "onBindViewHolder: $item")
                if (item.fileName != null) { textContainer.unVeil(); textContainer.visibility = View.GONE }
                else { textContainer.veil(); textContainer.visibility = View.VISIBLE  }

                changeStrokeColor(item)
                loadVideo(item)
                itemView.setOnClickListener(this)
            }

            override fun onClick(v: View?) {
                val item = items[bindingAdapterPosition]
                when(v) {
                    itemView -> selectUnselectItem(item)
                    thumbnailImageView -> {
                        Log.i("TAG", "bind: loading video ")
                        item.video?.let { videoView.setVideoURI(Uri.fromFile(File(it))) }
                    }
                    videoView -> {
                        if (videoView.isPlaying) { videoView.pause() }
                        else { videoView.start() }
                    }
                    else -> {}
                }
            }

            override fun onLongClick(v: View?): Boolean {
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
                    else -> {false}
                }
            }

            //todo add delete with swipe
            //todo add bookmark with swipe
        }
    }




    private class SearchItemDiffCallback(
        var oldImages: Array<SearchItem>,
        var newImages: Array<SearchItem> ): DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldImages.size
        }

        override fun getNewListSize(): Int {
            return newImages.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldImages[oldItemPosition].id == newImages[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldImages[oldItemPosition] == newImages[newItemPosition]
        }
    }
}