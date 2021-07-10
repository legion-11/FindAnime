package com.dmytroa.findanime.fragments.seeOtherOptions

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dmytroa.findanime.dataClasses.roomDBEntity.SearchResult
import com.dmytroa.findanime.databinding.OtherOptionsItemBinding
import com.dmytroa.findanime.databinding.SearchItemsBinding

/**
 * adapter for recycler view in [SeeOtherOptionsFragment]
 * shows images from [SearchResult] that corresponds to selected SearchItem
 */
class OtherOptionsAdapter(
    private var items: Array<SearchResult>,
    private val onItemClickListener: OnItemClickListener,
    private var newSelectedResult: SearchResult?,
    private val visibleHeight: Int
): RecyclerView.Adapter<OtherOptionsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView =  OtherOptionsItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        setMaxHeightToVisibleHeightOfDeviceScreen(itemView)
        return ViewHolder(itemView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    private fun setMaxHeightToVisibleHeightOfDeviceScreen(itemView: OtherOptionsItemBinding) {
        val params = itemView.imageView.layoutParams as ConstraintLayout.LayoutParams
        Log.i(TAG, "setMaxHeightToVisibleHeightOfDeviceScreen: $visibleHeight")
        params.matchConstraintMaxHeight = visibleHeight
        itemView.imageView.layoutParams = params
    }

    inner class ViewHolder(itemBinding: OtherOptionsItemBinding, private val onItemClickListener: OnItemClickListener) : RecyclerView.ViewHolder(itemBinding.root), View.OnClickListener {
        val imageView = itemBinding.imageView
        val nameTV = itemBinding.nameTV
        val similarityTV = itemBinding.similarityTV
        val root = itemBinding.root

        fun bind(itemData: SearchResult) {
            Log.i("ViewHolder", "bind: ${itemData.imageURL}")
            Glide.with(imageView.context)
                .load(itemData.imageURL)
                .into(imageView)
            nameTV.text = itemData.getName()
            similarityTV.text = itemData.similarity
            root.setOnClickListener(this)
            root.isSelected = itemData.id == newSelectedResult?.id
        }

        /**
         * select new viewholder
         */
        override fun onClick(v: View?) {
            val selectedResult = items[bindingAdapterPosition]
            val oldPosition = items.indexOfFirst { it.id == newSelectedResult?.id }
            newSelectedResult = selectedResult
            notifyItemChanged(oldPosition)
            notifyItemChanged(bindingAdapterPosition)
            onItemClickListener.onItemClick(selectedResult)
        }
    }

    companion object {
        const val TAG = "OtherOptionsAdapter"
    }

    interface OnItemClickListener {
        fun onItemClick(searchResult: SearchResult)
    }
}