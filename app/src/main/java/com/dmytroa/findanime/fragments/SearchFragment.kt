package com.dmytroa.findanime.fragments


import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
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
import java.io.File


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class SearchFragment : Fragment(), ImageDrawerListDialogFragment.OnImageClickListener {
    private val binding get() = _binding!!
    private var _binding: FragmentSearchBinding? = null
    private lateinit var viewModel: SearchFragmentViewModel

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
        val searchAdapter = SearchItemAdapter(arrayOf())
        binding.searchResultRecyclerView.layoutManager = LinearLayoutManager(context)

        binding.searchResultRecyclerView.adapter = searchAdapter
        binding.searchResultRecyclerView.setHasFixedSize(true)

        binding.searchResultRecyclerView.addOnScrollListener( object :
            RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    Log.i("TAG", "onScrollStateChanged: $newState ")
                    when(newState) {
                        SCROLL_STATE_DRAGGING -> {
                            (activity as MainActivity).hideFab()
                        }
                        SCROLL_STATE_IDLE -> {
                            // do not show button at the bottom position
                            if (!recyclerView.canScrollVertically(1)) {
                                (activity as MainActivity).hideFab()
                            } else {
                                (activity as MainActivity).showFab()
                            }
                        }
                        else -> {}
                    }
                }
            }
        )

        viewModel.items.observe(viewLifecycleOwner, {
            searchAdapter.updateDataset(it)
        })
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

//        view.findViewById<Button>(R.id.button_first).setOnClickListener {
//            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
//        }
    }
    override fun onImageClick(imageUri: Uri) {
        viewModel.createNewAnimeSearchRequest(imageUri, requireContext())
    }

    private inner class SearchItemAdapter(private var items: Array<SearchItem>):
        RecyclerView.Adapter<BaseViewHolder>() {
        val TAG = "SearchItemAdapter"

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            return ViewHolder(SearchItemsBinding.inflate(
                LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
            val item = items[position]
            (holder as ViewHolder).bind(item)
        }

        override fun onViewDetachedFromWindow(holder: BaseViewHolder) {
            Log.i(TAG, "onViewDetachedFromWindow: ")
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

        fun updateDataset(newDataset : Array<SearchItem>) {
            val oldItems = items
            val diffResult = DiffUtil.calculateDiff(SearchItemDiffCallback(oldItems, newDataset))
            val needScroll = oldItems.size < newDataset.size
            items = newDataset
            diffResult.dispatchUpdatesTo(this)
            if (needScroll) {binding.searchResultRecyclerView.smoothScrollToPosition(0)}
        }

    }

    private inner class SearchItemDiffCallback(var oldImages: Array<SearchItem>,
                                               var newImages: Array<SearchItem>
    ): DiffUtil.Callback() {
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

    //just in case I will do some other viewHolders
    private abstract inner class BaseViewHolder(binding: ViewBinding): RecyclerView.ViewHolder(binding.root)

    private inner class ViewHolder(binding: SearchItemsBinding):
        BaseViewHolder(binding) {

        val originalImageView: ImageView = binding.originalImageView
        val fileNameTV: TextView = binding.nameTextView
        val similarityTV: TextView = binding.similarityTextView
        val videoView: VideoView = binding.videoView
        val thumbnailImageView: ImageView = binding.thumbnailImageView
        val videoContainer = binding.videoContainer
        val buttonsContainer = binding.buttonsContainer
        val textContainer = binding.textContainer

        var runablePlay: Runnable? = null
        var handler: Handler? = null

        init {
            textContainer.layout = R.layout.default_text_layout
            buttonsContainer.layout = R.layout.default_buttons_layout

            //resize to save aspect ratio
            videoView.setOnPreparedListener { mp -> //Get your video's width and height

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

                //avoid black background
                handler = Handler(Looper.getMainLooper())
                runablePlay = Runnable {
                    try {
                        if (mp != null && mp.currentPosition > 0) {
                            videoView.alpha = 1f
                            thumbnailImageView.visibility = View.INVISIBLE
                        }
                        if (videoView.alpha == 1f) {
                            runablePlay = null
                            handler = null
                        } else {
                            handler?.postDelayed(runablePlay!!, 0)
                        }
                    } catch (e : IllegalStateException) {
                        runablePlay = null
                        handler = null
                    }
                }
                handler!!.post(runablePlay!!)
            }
        }

        fun bind(item: SearchItem) {
            Glide.with(requireActivity()).load(item.imageURI).into(originalImageView)
            if (!item.finished) { viewModel.repeatAnimeSearchRequest(item, requireContext()) }

            fileNameTV.text = item.fileName ?: "some text"
            similarityTV.text = if(item.similarity != null) item.similarity.toString() else "some text"

            Log.i("ViewHolder", "onBindViewHolder: ${item.video}")

            if (item.fileName != null) {textContainer.unVeil()} else {textContainer.veil()}

            if(item.video != null) {
                Glide.with(requireActivity())
                    .load(Uri.fromFile(File(item.video!!)))
                    .thumbnail(0.1f)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            videoContainer.unVeil()
                            buttonsContainer.unVeil()
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable?,
                            model: Any?,
                            target: Target<Drawable>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            videoContainer.unVeil()
                            buttonsContainer.unVeil()
                            return false
                        }

                    })
                    .into(thumbnailImageView)

                videoView.isFocusable = false
                thumbnailImageView.setOnClickListener {
                    videoView.setVideoURI(Uri.fromFile(File(item.video!!)))
                    videoView.setOnClickListener { videoView ->
                        videoView as VideoView
                        if (videoView.isPlaying) {
                            videoView.pause()
                        } else {
                            videoView.start()
                        }
                    }
                }
            } else {
                videoContainer.veil()
                buttonsContainer.veil()
                thumbnailImageView.setImageDrawable(null)
            }
        }
    }
}