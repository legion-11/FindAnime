package com.dmytroa.findanime.fragments

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.dmytroa.findanime.MainActivity
import com.dmytroa.findanime.R
import com.dmytroa.findanime.databinding.FragmentImageDrawerListDialogItemBinding
import com.dmytroa.findanime.databinding.FragmentImageDrawerListDialogBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlin.random.Random

/**
 *
 * A fragment that shows a list of items as a modal bottom sheet.
 *
 * You can show this modal bottom sheet from your activity like this:
 * <pre>
 *    ImageDrawerListDialogFragment.newInstance(30).show(supportFragmentManager, "dialog")
 * </pre>
 */
class ImageDrawerListDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentImageDrawerListDialogBinding? = null
    private val random = Random(42)
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageDrawerListDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        (activity as MainActivity).setSupportActionBar(binding.toolbar)

        val list = binding.list
        list.layoutManager = GridLayoutManager(context, 3)
        list.adapter = arguments?.getInt(ARG_ITEM_COUNT)?.let { ImageDrawerItemAdapter(it) }

    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                val stringState = when(newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> "STATE_COLLAPSED"
                    BottomSheetBehavior.STATE_DRAGGING -> "STATE_DRAGGING"
                    BottomSheetBehavior.STATE_EXPANDED -> "STATE_EXPANDED"
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> "STATE_HALF_EXPANDED"
                    BottomSheetBehavior.STATE_HIDDEN -> "STATE_HIDDEN"
                    BottomSheetBehavior.STATE_SETTLING -> "STATE_SETTLING"

                    else -> ""
                }
                Log.i("tagggg", stringState)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                Log.i("tagggg", slideOffset.toString())
            }

        })

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // TODO: Customize parameter argument names
        const val ARG_ITEM_COUNT = "item_count"
        // TODO: Customize parameters
        fun newInstance(itemCount: Int) = ImageDrawerListDialogFragment()
            .apply {
                arguments = Bundle().apply { putInt(ARG_ITEM_COUNT, itemCount) }
            }
    }

    private inner class ViewHolder(binding: FragmentImageDrawerListDialogItemBinding):
        RecyclerView.ViewHolder(binding.root) {
//            val text: TextView = binding.text
        val image: ImageView = binding.galleryImage
    }

    private inner class ImageDrawerItemAdapter(private val mItemCount: Int): RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                FragmentImageDrawerListDialogItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false)
            ).apply {
                image.maxHeight = parent.measuredWidth / 3
            }
        }


        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//            holder.text.text = position.toString()
            val id = if (random.nextBoolean()) R.drawable.tmp_image else R.drawable.tmp_image2
            holder.image.setImageResource(id)
        }

        override fun getItemCount() = mItemCount
    }
}