package com.legion_11.findanime.fragments.search

import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
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
import com.legion_11.findanime.R
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchItem
import com.legion_11.findanime.dataClasses.roomDBEntity.SearchItemWithSelectedResult
import com.legion_11.findanime.databinding.SearchItemsBinding
import com.legion_11.findanime.repositories.LocalFilesRepository
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.io.File

/**
 * adapter for recycler view in [SearchFragment]
 * @param allItems all [SearchItem]s from room db
 * @param maxHeight maximum height of items (calculates from toolbar, appbar, and statusbar heights)
 * @param listener listener to adapter item clicks
 */
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
    // items that will be presented to user
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

    // strings from resource for formating time and episode
    private lateinit var similarityString: String
    private lateinit var timeString: String
    private lateinit var episodeString: String
    private lateinit var episodeAndTimeString: String
    private lateinit var videoThumbnailError: String

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        similarityString = recyclerView.resources.getString(R.string.similarity)
        timeString = recyclerView.resources.getString(R.string.time_part)
        episodeString = recyclerView.resources.getString(R.string.episode_part)
        episodeAndTimeString = recyclerView.resources.getString(R.string.episode_and_time)
        videoThumbnailError = recyclerView.resources.getString(R.string.error_loading_thumbnail)
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

    // stopPlayback of videoView to prevent lag
    override fun onViewDetachedFromWindow(holder: BaseViewHolder) {
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

    // scroll to top if user adds new item
    private var needScroll = false

    /**
     * update fullDataset from room db
     */
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
            // (strange bug) if you do not notify it takes focus from search view once you start scrolling
            if (textFilter.isNotEmpty()) { notifyDataSetChanged()}
        }
    }

    /**
     * set maximum height of video view to be smaller than screen - different bars
     */
    private fun setMaxHeightToVisibleHeightOfDeviceScreen(itemView: SearchItemsBinding) {
        val params = itemView.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        params.matchConstraintMaxHeight = maxHeight
        itemView.videoContainer.layoutParams = params
    }
    /**
     * remove item from dataset
     */
    fun deleteItem(id: Long) {
        val newItems = allItems.toCollection(mutableListOf())
        newItems.removeAt(newItems.indexOfFirst { it.searchItem.id == id })
        setFullDataset(newItems.toTypedArray())
    }

    /**
     * @param charSearch text from searchview once it updated
     * @return filtered by filters (textfilter from searchview) and bookmarks filter fulldataset
     */
    private fun getFilteredArray(charSearch: String): Array<SearchItemWithSelectedResult> {
        val lowercaseCharSearch = charSearch.lowercase()
        val firstFilter = if (charSearch.isEmpty()) {
            allItems
        } else {
            allItems.sortedWith(
                compareBy(
                    {if (it.getName().lowercase().contains(lowercaseCharSearch)) 0 else 1},
                    {0 - it.getTextComparisonScore(lowercaseCharSearch)}
                )
            ).toTypedArray()
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

    // filter dataset by searchview text
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

    /**
     * @return formated time "HH:MM:SS" or ""
     */
    private fun getTimeString(sec: Double?): String {
        val from = sec?.toInt()
        return if (from == null){
            ""
        } else {
            val hours = from / 3600
            val minutes = (from % 3600) / 60
            val seconds = from % 60
            timeString.format(hours, minutes, seconds)
        }
    }
    /**
     * @return formated episode without trailing zeros
     */
    private fun getEpisodeString(str: String?): String {
        if (str == null) return ""
        val asInt = str.toFloatOrNull()?.toInt()
        // without trailingZeros
        if (asInt != null) {
            val withoutTrailingZeros = "%d".format(asInt)
            return episodeString.format(withoutTrailingZeros)
        }
        return episodeString.format(str)
    }

    //just in case I will do some other viewHolders
    abstract class BaseViewHolder(itemBinding: ViewBinding):
        RecyclerView.ViewHolder(itemBinding.root)

    /**
     * viewHolder for [SearchItemAdapter]
     */
    private inner class ViewHolder(itemBinding: SearchItemsBinding):
        BaseViewHolder(itemBinding), View.OnClickListener, View.OnLongClickListener {

        val fileNameTV: TextView = itemBinding.nameTextView
        val similarityTV: TextView = itemBinding.similarityTextView
        val episodeAndTimeTV: TextView = itemBinding.episodeAndTimeTextView
        val videoView: VideoView = itemBinding.videoView
        // since it is very laggy to show video preview by showing first second
        // we can just save it in image view and not load video at all
        val thumbnailImageView: ImageView = itemBinding.thumbnailImageView
        val videoContainer = itemBinding.videoContainer
        val buttonsPlaceholder = itemBinding.buttonsSkeletonContainer
        val textPlaceholder = itemBinding.textSkeletonContainer
        val videoPlaceholder = itemBinding.videoSkeletonContainer
        val toggleBookmarks = itemBinding.toggleBookmarks
        val malButton = itemBinding.MALImageButton
        val root = itemBinding.root

        init {
            //resize to save aspect ratio once user started playing video
            videoView.setOnPreparedListener { mp ->
                resizeVideo(mp)
                mp.start()
                showVideoViewOnceVideoIsFullyPrepared(mp)
            }
        }

        /**
         * resize videoView depending on video aspect ratio
         */
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

        /**
         * videoView.setOnPreparedListener not representing the moment once video is fully ready to play
         * so to prevent flickering of black color while video is starting we just hiding thumbnail
         * once current playback position goes to 0.2 seconds
         */
        private fun showVideoViewOnceVideoIsFullyPrepared(mp: MediaPlayer?) {
            CoroutineScope(Dispatchers.IO).launch {
                var started = false
                var counter = 0
                while (!started || counter < 200) {
                    try {
                        if (mp != null && mp.currentPosition > 200) {
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

        // hide placeholders for buttons and video
        private fun unVeilVideo() {
            videoPlaceholder.apply {
                stopShimmer()
                visibility = View.GONE
            }
            buttonsPlaceholder.apply {
                stopShimmer()
                visibility = View.GONE
            }
            malButton.visibility = View.VISIBLE
            toggleBookmarks.visibility = View.VISIBLE
        }

        // show placeholders for buttons and video
        private fun veilVideo() {
            videoPlaceholder.apply {
                startShimmer()
                visibility = View.VISIBLE
            }
            buttonsPlaceholder.apply {
                startShimmer()
                visibility = View.VISIBLE
            }
            malButton.visibility = View.INVISIBLE
            toggleBookmarks.visibility = View.INVISIBLE
        }

        /**
         * instead of loading video we just loading thumbnail,
         * and loading video only when user clicks on it
         */
        private fun loadVideo(item: SearchItem) {
            val videoURI = item.videoFileName?.let { LocalFilesRepository.getFullVideoPath(it, recyclerView.context) }

            if (videoURI == null) {
                veilVideo()
                thumbnailImageView.setImageDrawable(null)
                return
            }

            videoView.isFocusable = false
            thumbnailImageView.bringToFront()

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
                        Snackbar.make(recyclerView, videoThumbnailError, Snackbar.LENGTH_LONG).show()
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
            videoContainer.setOnClickListener(this@ViewHolder)
            videoContainer.setOnLongClickListener(this@ViewHolder)

        }

        private fun isSelected(): Boolean {
            val item = filteredItems[bindingAdapterPosition]
            return item.searchItem.id == selectedItemId
        }

        fun changeStrokeColor() {
            root.isSelected = isSelected()
        }

        /**
         * bind viewHolder to recyclerview
         */
        fun bind(item: SearchItemWithSelectedResult) {

            val itemData = item.searchResult
            if (!item.searchItem.isFinished) {
                listener.repeatAnimeSearchRequest(item)
            }

            fileNameTV.text = item.getName()
            similarityTV.text = if (itemData?.similarity != null)
                similarityString.format(itemData.similarity)
            else
                ""

            val timeFormatted = getTimeString(itemData?.from)
            val episodeFormated = getEpisodeString(itemData?.episode)

            episodeAndTimeTV.text = episodeAndTimeString.format(episodeFormated, timeFormatted)

            toggleBookmarks.isChecked = item.searchItem.isBookmarked
            toggleBookmarks.setOnCheckedChangeListener { _, isChecked ->
                listener.setIsBookmarked(isChecked, item.searchItem)
            }
            malButton.setOnClickListener(this@ViewHolder)
            if (fileNameTV.text.isNotBlank()) {
                textPlaceholder.stopShimmer()
                textPlaceholder.visibility = View.GONE
            }
            else {
                textPlaceholder.startShimmer()
                textPlaceholder.visibility = View.VISIBLE
            }

            changeStrokeColor()
            loadVideo(item.searchItem)
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            // not sure how it is possible but I once had error with it (only once, but it was strange)
            if (bindingAdapterPosition<0) return
            val item = filteredItems[bindingAdapterPosition]
            when(v) {
                itemView -> {
                    listener.setSelectedItemId(item)
                }
                thumbnailImageView -> {
                    item.searchItem.let {
                        val fullURI = item.searchItem.videoFileName?.let { LocalFilesRepository.getFullVideoPath(it, recyclerView.context) }
                        fullURI?.let {
                            videoView.setVideoURI(Uri.fromFile(File(it)))
                        }
                    }
                }
                videoContainer -> {
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
            return when(v) {
                thumbnailImageView -> {
                    root.performClick()
                    root.isPressed = true
                    root.isPressed = false
                    true
                }
                videoContainer -> {
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

    /**
     * interface for sending data to [SearchFragment] on clicks on viewHolder
     */
    interface OnSearchAdapterItemClickListener {
        fun openMal(idMal: Int)
        fun setIsBookmarked(isChecked: Boolean, searchItem: SearchItem)
        fun repeatAnimeSearchRequest(item: SearchItemWithSelectedResult)
        fun setSelectedItemId(item: SearchItemWithSelectedResult)
    }
}

