package com.dmytroa.findanime.fragments.search

import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.dmytroa.findanime.R
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItem
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.dmytroa.findanime.databinding.SearchItemsBinding
import com.dmytroa.findanime.repositories.LocalFilesRepository
import kotlinx.coroutines.*
import java.io.File

class SearchItemAdapter(
    private var allItems: Array<SearchItemWithSelectedResult>,
    private val maxHeight: Int,
    private val listener: OnSearchAdapterItemClickListener
): RecyclerView.Adapter<SearchItemAdapter.BaseViewHolder>(), Filterable {

    private lateinit var recyclerView: RecyclerView
    var textFilter = ""
        set(value) {
            field = value
            filter.filter(value)
        }
    var isBookmarksFiltered = false
        set(value) {
            field = value
            filter.filter(textFilter)
        }
    private var filteredItems: Array<SearchItemWithSelectedResult> = getFilteredArray(textFilter)
    var selectedItemId: Long? = null
    set(value) {
        if (field == value) return
        val lastSelected = field
        field = value
        val positionOfNewSelected = filteredItems.indexOfFirst { it.searchItem.id == value }
        val newSelectedViewHolder = recyclerView.findViewHolderForAdapterPosition(positionOfNewSelected) as ViewHolder?
        newSelectedViewHolder?.changeStrokeColor()
        if (lastSelected == null) return
        val positionOfLastSelected = filteredItems.indexOfFirst { it.searchItem.id == lastSelected }
        val lastSelectedViewHolder = recyclerView.findViewHolderForAdapterPosition(positionOfLastSelected) as ViewHolder?
        lastSelectedViewHolder?.changeStrokeColor()
    }


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

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
            recyclerView.scrollToPosition(0)
            // if you do not notify it takes focus from search view
            if (textFilter.isNotEmpty()) { notifyDataSetChanged()}
        }
    }

    private fun setMaxHeightToVisibleHeightOfDeviceScreen(itemView: SearchItemsBinding) {
        val params = itemView.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        params.matchConstraintMaxHeight = maxHeight
        itemView.videoContainer.layoutParams = params
    }

    fun deleteItem(id: Long) {
        val newItems = filteredItems.toCollection(mutableListOf())
        newItems.removeAt(newItems.indexOfFirst { it.searchItem.id == id })
        setFullDataset(newItems.toTypedArray())
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

    fun getItemByPosition(pos: Int): SearchItemWithSelectedResult {
        return filteredItems[pos]
    }

    //just in case I will do some other viewHolders
    abstract class BaseViewHolder(itemBinding: ViewBinding):
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
            videoView.setOnPreparedListener { mp ->
                resizeVideo(mp)
                mp.start()
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
        }

        private fun showVideoViewOnceVideoIsFullyPrepared(mp: MediaPlayer?) {
            CoroutineScope(Dispatchers.IO).launch {
                var started = false
                var counter = 0
                while (!started || counter < 100) {
                    try {
                        if (mp != null && mp.currentPosition > 0) {
                            break
                        }
                        delay(10)
                        counter += 1
                    } catch (e : IllegalStateException) {
                        break
                    }
                }
                withContext(Dispatchers.Main) {
                    videoView.alpha = 1f
                    thumbnailImageView.visibility = View.GONE
                    started = true
                    return@withContext
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
            val videoURI = item.videoFileName?.let { LocalFilesRepository.getFullVideoPath(it, recyclerView.context) }

            if (videoURI == null) {
                Log.i("SearchItemAdapter", "loadVideo: fail videoURI == null $item")
                veilVideo()
                thumbnailImageView.setImageDrawable(null)
                return
            }

            videoView.isFocusable = false
            thumbnailImageView.bringToFront()

            Log.i("SearchItemAdapter", "loadVideo: call glide")
            Glide.with(thumbnailImageView.context)
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
                        Log.i("SearchItemAdapter", "loadVideo: onLoadFailed")
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.i("SearchItemAdapter", "loadVideo: onResourceReady")
                        unVeilVideo()
                        return false
                    }

                })
                .into(thumbnailImageView)
                .also { Log.i("SearchItemAdapter", "loadVideo: after call glide") }


            thumbnailImageView.setOnLongClickListener(this@ViewHolder)
            thumbnailImageView.setOnClickListener(this@ViewHolder)
            videoView.setOnClickListener(this@ViewHolder)
            videoView.setOnLongClickListener(this@ViewHolder)

        }

        private fun isSelected(): Boolean {
            val item = filteredItems[bindingAdapterPosition]
            return item.searchItem.id == selectedItemId
        }

        fun changeStrokeColor() {
            root.isSelected = isSelected()
        }

        fun bind(item: SearchItemWithSelectedResult) {
            Log.i("ViewHolder", "onBindViewHolder: $item")

            val itemData = item.searchResult
            if (!item.searchItem.isFinished) {
                listener.repeatAnimeSearchRequest(item)
            }

            fileNameTV.text = item.getName()
            similarityTV.text = itemData?.similarity
            toggleBookmarks.isChecked = item.searchItem.isBookmarked
            toggleBookmarks.setOnCheckedChangeListener { _, isChecked ->
                listener.setIsBookmarked(isChecked, item.searchItem)
            }
            malButton.setOnClickListener(this@ViewHolder)
            if (fileNameTV.text.isNotBlank()) { textContainer.unVeil(); textContainer.visibility = View.GONE }
            else { textContainer.veil(); textContainer.visibility = View.VISIBLE  }

            changeStrokeColor()
            Log.i("SearchItemAdapter", "bind: call load video")
            loadVideo(item.searchItem)
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            if (bindingAdapterPosition<0) return
            val item = filteredItems[bindingAdapterPosition]
            when(v) {
                itemView -> {
                    listener.setSelectedItemId(item)
                }
                thumbnailImageView -> {
                    Log.i("TAG", "bind: loading video ")
                    item.searchItem.let {
                        val fullURI = item.searchItem.videoFileName?.let { LocalFilesRepository.getFullVideoPath(it, recyclerView.context) }
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
                        listener.openMal(idMal)
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

    interface OnSearchAdapterItemClickListener {
        fun openMal(idMal: Int)
        fun setIsBookmarked(isChecked: Boolean, searchItem: SearchItem)
        fun repeatAnimeSearchRequest(item: SearchItemWithSelectedResult)
        fun setSelectedItemId(item: SearchItemWithSelectedResult)
    }
}

