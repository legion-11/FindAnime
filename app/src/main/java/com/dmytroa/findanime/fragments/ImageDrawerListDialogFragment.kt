package com.dmytroa.findanime.fragments

import android.animation.LayoutTransition
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dmytroa.findanime.R
import com.dmytroa.findanime.databinding.FragmentImageDrawerListDialogBinding
import com.dmytroa.findanime.databinding.FragmentImageDrawerListDialogItemBinding
import com.dmytroa.findanime.repositories.LocalFilesRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.*


/**
 *
 * A fragment that shows a list of items as a modal bottom sheet.
 *
 * You can show this modal bottom sheet from your activity like this:
 * <pre>
 *    ImageDrawerListDialogFragment.newInstance(*args).show(supportFragmentManager, "dialog")
 * </pre>
 */
class ImageDrawerListDialogFragment : BottomSheetDialogFragment(),
    AdapterView.OnItemSelectedListener {

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var _binding: FragmentImageDrawerListDialogBinding? = null
    private lateinit var viewModel: ImageDrawerViewModel
    private lateinit var listener: OnImageClickListener

    private var imagesIds: ArrayList<Long> = arrayListOf()
    private val uriExternal = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    // minimum height of binding.resizableCurtainView
    private var resizableViewMinHeight: Int? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as OnImageClickListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // make background transparent so binding.resizableCurtainView animation can be visible
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
        createViewModel()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageDrawerListDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        resizableViewMinHeight = resources.getDimension(R.dimen.resizable_view_min_height).toInt()
//        setHasOptionsMenu(true)
//        (activity as MainActivity).setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationIcon(android.R.drawable.arrow_up_float)
        val itemsInRow =  if (isLandscape()) 5 else 3
        binding.list.layoutManager = GridLayoutManager(context, itemsInRow)
        binding.list.adapter = ImageDrawerItemAdapter()
        viewModel.images.observe(this) {
            (binding.list.adapter as ImageDrawerItemAdapter).setImages(it)
        }
        setupSpinner()
    }
    private fun createViewModel() {
        val albums = LocalFilesRepository.getAlbums(requireContext())
        val allImagesLocalized = resources.getString(R.string.all_images)
        if (albums.map { it.name }.contains(allImagesLocalized)) {
            for (album in albums) {
                if (album.name == allImagesLocalized) {
                    album.name = album.name.toLowerCase(Locale.getDefault())
                    break
                }
            }
        }
        val factory = ImageDrawerViewModel.ImageDrawerViewModelFactory(albums, allImagesLocalized)
        viewModel = ViewModelProvider(this, factory).get(ImageDrawerViewModel::class.java)
    }

    private fun setupSpinner(){
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
            viewModel.albumNames)
        binding.toolbarSpinner.adapter = spinnerAdapter
        binding.toolbarSpinner.onItemSelectedListener = this
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog!!.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        // BottomSheetDialogFragment won't shrink after recyclerView shrinks
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        //open full screen in landscape orientation
        if (isLandscape()) { behavior.state = BottomSheetBehavior.STATE_EXPANDED }

        if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            resizeCurtainView(1f)
            binding.toolbar.visibility = View.VISIBLE
        }
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                when(slideOffset) {
                    1f -> {
                        binding.root.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
                        binding.toolbar.visibility = View.VISIBLE
                    }
                    else -> {
                        if (binding.toolbar.visibility == View.VISIBLE) {
                            binding.root.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
                            binding.toolbar.visibility = View.INVISIBLE
                        }
                        resizeCurtainView(slideOffset)
                    }
                }
            }
        })
    }

    private fun resizeCurtainView(slideOffset: Float){
        binding.resizableCurtainView.layoutParams.height = getNewResizableCurtainHeight(slideOffset)
        binding.resizableCurtainView.requestLayout()
    }

    private fun getNewResizableCurtainHeight(slideOffset: Float): Int {
        if (slideOffset <= 0.9) return resizableViewMinHeight!!
        return ((slideOffset - 0.9f) * 10f * binding.toolbar.height).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    companion object {
        const val ARG_ITEMS_IN_ROW = "items_in_row"
        fun newInstance() = ImageDrawerListDialogFragment()
    }

    /** adapter for local gallery images */
    private inner class ImageDrawerItemAdapter: RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(FragmentImageDrawerListDialogItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false)
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val imageUri = Uri.withAppendedPath(uriExternal, imagesIds[position].toString()) // Uri of the picture
            Glide.with(holder.itemView.context)
                .load(imageUri)
                .centerCrop()
                .into(holder.image)
                .also {
                    Log.i( "DeviceImageManager", "file loaded => $imageUri")
                }

        }

        override fun getItemCount() = imagesIds.size

        fun setImages(newImagesIds: ArrayList<Long>) {
            val oldIds = imagesIds
            val diffResult = DiffUtil.calculateDiff(
                ImageDrawerDiffCallback(oldIds, newImagesIds)
            )
            imagesIds = newImagesIds
            diffResult.dispatchUpdatesTo(this)
        }
    }

    /** viewHolder for ImageDrawerItemAdapter */
    private inner class ViewHolder(
        binding: FragmentImageDrawerListDialogItemBinding): RecyclerView.ViewHolder(binding.root),
        View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
        }
        val image: ImageView = binding.galleryImage

        override fun onClick(v: View?) {
            val imageUri = Uri.withAppendedPath(uriExternal, imagesIds[adapterPosition].toString())
            listener.onImageClick(imageUri)
        }
    }

    private inner class ImageDrawerDiffCallback(var oldImages: ArrayList<Long>,
                                                var newImages: ArrayList<Long>): DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldImages.size
        }

        override fun getNewListSize(): Int {
            return newImages.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldImages[oldItemPosition] == newImages[newItemPosition]
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldImages[oldItemPosition] == newImages[newItemPosition]
        }

    }

    /** callback for click on ViewHolder */
    interface OnImageClickListener {
        fun onImageClick(imageUri: Uri)
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        viewModel.selectGallery(position)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}
}