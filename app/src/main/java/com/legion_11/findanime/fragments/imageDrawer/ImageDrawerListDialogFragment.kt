package com.legion_11.findanime.fragments.imageDrawer

import android.animation.LayoutTransition
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.legion_11.findanime.R
import com.legion_11.findanime.databinding.FragmentImageDrawerListDialogBinding
import com.legion_11.findanime.databinding.FragmentImageDrawerListDialogItemBinding
import com.legion_11.findanime.repositories.LocalFilesRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.*


/**
 *
 * A fragment that shows a list of images from public storage as a modal bottom sheet.
 *
 */
class ImageDrawerListDialogFragment : BottomSheetDialogFragment(),
    AdapterView.OnItemSelectedListener {

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var _binding: FragmentImageDrawerListDialogBinding? = null
    private lateinit var viewModel: ImageDrawerViewModel
    private lateinit var listener: OnImageClickListener

    //current ids from selected album
    private var imagesIds: ArrayList<Long> = arrayListOf()
    private val uriExternal = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private lateinit var behavior: BottomSheetBehavior<View>

    // gradually show toolbar when sliding up
    private val slidingListener = object : BottomSheetBehavior.BottomSheetCallback() {
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
    }

    // minimum height of binding.resizableCurtainView
    private var resizableViewMinHeight: Int? = null

    // converts dp to px
    private val Int.px: Int
        get() = (this * Resources.getSystem().displayMetrics.density).toInt()

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

        binding.toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        val itemsInRow =  if (resources.getBoolean(R.bool.isTablet)) 5 else 3
        binding.list.layoutManager = GridLayoutManager(context, itemsInRow)
        binding.list.adapter = ImageDrawerItemAdapter()
        viewModel.images.observe(this) {
            binding.list.scrollToPosition(0)
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
                    album.name = album.name.lowercase(Locale.getDefault())
                    break
                }
            }
        }
        val factory = ImageDrawerViewModel.ImageDrawerViewModelFactory(albums, allImagesLocalized)
        viewModel = ViewModelProvider(this, factory).get(ImageDrawerViewModel::class.java)
    }

    // custom spinner styling
    private fun setupSpinner(){
        val spinnerAdapter = ArrayAdapter(requireContext(),
            R.layout.drawer_spinner_item, viewModel.albumNames)

        binding.toolbarSpinner.adapter = spinnerAdapter
        binding.toolbarSpinner.onItemSelectedListener = this
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog!!.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        // BottomSheetDialogFragment won't shrink after recyclerView shrinks
        // when you change to album that has small number of images
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.peekHeight = (Resources.getSystem().displayMetrics.heightPixels * 0.8).toInt()

        // change starting height when you change orientation on tablets
        if (resources.getBoolean(R.bool.isTablet)) {
            // you can go more fancy and vary the bottom sheet width depending on the screen width
            // see recommendations on https://material.io/components/sheets-bottom#specs
            bottomSheet.layoutParams.width = 600.px
        }

        //show toolbar in expanded state
        if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            resizeCurtainView(1f)
            binding.toolbar.visibility = View.VISIBLE
        }

        behavior.addBottomSheetCallback(slidingListener)
    }

    private fun resizeCurtainView(slideOffset: Float){
        val newHeight = getNewResizableCurtainHeight(slideOffset)
        if (newHeight != resizableViewMinHeight!!) {
            binding.resizableCurtainView.layoutParams.height = newHeight
            binding.resizableCurtainView.requestLayout()
        }
    }

    /**
     * depending on how much you opened drawer, it will
     * starts increasing height on last 10%
     */
    private fun getNewResizableCurtainHeight(slideOffset: Float): Int {
        if (slideOffset <= 0.9) return resizableViewMinHeight!!
        val newHeight = ((slideOffset - 0.9f) * 10f * binding.toolbar.height).toInt()
        if (newHeight < resizableViewMinHeight!!) return resizableViewMinHeight!!
        return newHeight
    }

    override fun onDestroyView() {
        super.onDestroyView()
        behavior.removeBottomSheetCallback(slidingListener)
        _binding = null
    }

    companion object {
        fun newInstance() = ImageDrawerListDialogFragment()
        const val GALLERY_TYPE = -1
        const val IMAGE_TYPE = 0
        const val RESULT_LOAD_IMG = 200
        const val TAG = "ImageDrawerFragment"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // take image with photo picker intent
        if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK) {
            val imageUri = data?.data
            if (imageUri != null) {
                listener.onDrawerImageClick(imageUri)
                dismiss()
            } else {
                Toast.makeText(activity, "Something went wrong", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** adapter for local gallery images */
    private inner class ImageDrawerItemAdapter: RecyclerView.Adapter<BaseViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            return when(viewType) {
                IMAGE_TYPE -> ImageViewHolder(
                    FragmentImageDrawerListDialogItemBinding.inflate(
                            LayoutInflater.from(parent.context), parent, false)
                    )

                else -> GalleryViewHolder(
                    FragmentImageDrawerListDialogItemBinding.inflate(
                            LayoutInflater.from(parent.context), parent, false)
                    )
            }
        }

        override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
            if (holder.itemViewType == IMAGE_TYPE) {
                setupImageViewHolder(holder as ImageViewHolder, position)
            } else {
                setupGalleryViewHolder(holder as GalleryViewHolder, position)
            }
        }

        override fun getItemCount() = imagesIds.size

        override fun getItemViewType(position: Int): Int {
            return if (imagesIds[position] == GALLERY_TYPE.toLong()) GALLERY_TYPE else IMAGE_TYPE
        }

        fun setImages(newImagesIds: ArrayList<Long>) {
            val oldIds = imagesIds
            val diffResult = DiffUtil.calculateDiff(ImageDrawerDiffCallback(oldIds, newImagesIds))
            imagesIds.clear()
            imagesIds.addAll(newImagesIds)
            diffResult.dispatchUpdatesTo(this)
        }

        fun setupImageViewHolder(holder: ImageViewHolder, position: Int) {
            val imageUri = Uri.withAppendedPath(uriExternal, imagesIds[position].toString()) // Uri of the picture
            Glide.with(holder.itemView.context)
                .load(imageUri)
                .centerCrop()
                .into(holder.image)
        }

        fun setupGalleryViewHolder(holder: GalleryViewHolder, position: Int) {
            holder.image.setImageResource(R.drawable.ic_gallery_in_stroke)
        }
    }

    /**
     * baseViewHolder for ImageDrawerItemAdapter
     */
    private abstract inner class BaseViewHolder(binding: ViewBinding): RecyclerView.ViewHolder(binding.root)

    /**
     * viewHolder for ImageDrawerItemAdapter
     *  represent the clickable image that allows select images from gallery app
     */
    private inner class GalleryViewHolder(binding: FragmentImageDrawerListDialogItemBinding):
        BaseViewHolder(binding), View.OnClickListener {

        val image: ImageView = binding.galleryImage
        init { itemView.setOnClickListener(this) }


        override fun onClick(v: View?) {
            val photoPickerIntent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            startActivityForResult(photoPickerIntent, RESULT_LOAD_IMG)
        }
    }

    /**
     * viewHolder for Images from gallery
     * represent single image from public storage
     */
    private inner class ImageViewHolder(binding: FragmentImageDrawerListDialogItemBinding):
        BaseViewHolder(binding), View.OnClickListener {

        val image: ImageView = binding.galleryImage
        init { itemView.setOnClickListener(this) }

        override fun onClick(v: View?) {
            val imageUri = Uri.withAppendedPath(uriExternal, imagesIds[bindingAdapterPosition].toString())
            listener.onDrawerImageClick(imageUri)
            dismiss()
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
        fun onDrawerImageClick(imageUri: Uri)
    }

    // change album
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        viewModel.selectGallery(position)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

}