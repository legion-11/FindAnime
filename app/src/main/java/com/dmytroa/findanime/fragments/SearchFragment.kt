package com.dmytroa.findanime.fragments

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.dmytroa.findanime.FindAnimeApplication
import com.dmytroa.findanime.MainActivity
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

    private inner class SearchItemAdapter(private var items: Array<SearchItem>): RecyclerView.Adapter<BaseViewHolder>() {
        val TAG = "SearchItemAdapter"

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            return ViewHolder(SearchItemsBinding.inflate(
                LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
            val item = items[position]
            holder as ViewHolder
            Glide.with(requireActivity()).load(item.imageURI).into(holder.originalImageView)
            if (!item.finished) { viewModel.repeatAnimeSearchRequest(item, requireContext()) }

            holder.fileNameTV.text = item.fileName ?: ""
            holder.similarityTV.text = if(item.similarity != null) item.similarity.toString() else ""
            holder.progressBar.visibility = if (!item.finished) View.VISIBLE else View.GONE
            Log.i(TAG, "onBindViewHolder: ${item.video}")

            if(item.video != null) {

                Glide.with(requireActivity())
                    .load(Uri.fromFile(File(item.video!!)))
                    .thumbnail(0.1f)
                    .into(holder.thumbnailImageView)

                holder.videoView.isFocusable = false
                holder.thumbnailImageView.setOnClickListener {
                    Log.i(TAG, "onBindViewHolder: click ${item.video}")
                    holder.videoView.setVideoURI(Uri.fromFile(File(item.video!!)))
                    holder.videoView.setOnClickListener { videoView ->

                        videoView as VideoView
                        if (videoView.isPlaying) {
                            videoView.pause()
                        } else {
                            videoView.start()
                        }
                    }
                }
            } else {
                holder.thumbnailImageView.setImageDrawable(null)
            }
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
            items = newDataset
            diffResult.dispatchUpdatesTo(this)
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
        val progressBar: ProgressBar = binding.progressBar
        val videoView: VideoView = binding.videoView
        val thumbnailImageView: ImageView = binding.thumbnailImageView
        val container = binding.videoContainer
        var runablePlay: Runnable? = null
        var handler: Handler? = null

        init {
            //resize to save aspect ratio
            videoView.setOnPreparedListener { mp -> //Get your video's width and height

                val videoWidth = mp.videoWidth
                val videoHeight = mp.videoHeight

                //Get VideoView's current width and height
                val containerViewWidth: Int = container.width
                val containerViewHeight: Int = container.height
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
    }

}