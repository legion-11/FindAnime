package com.dmytroa.findanime.fragments

import androidx.lifecycle.*
import com.dmytroa.findanime.dataClasses.Album
import java.util.*
import kotlin.collections.ArrayList

class ImageDrawerViewModel(private val albums: ArrayList<Album>,
                           allImagesStringLocalized: String) : ViewModel() {

    val albumNames = listOf(allImagesStringLocalized) + albums.map { it.name }.sorted()

    private val _selectedGallery = MutableLiveData(allImagesStringLocalized)
    val images: LiveData<ArrayList<Long>> = Transformations.map(_selectedGallery) { str ->
        var selectedImages: ArrayList<Long> = arrayListOf()
        if (str == allImagesStringLocalized) {
            //sorted - so images will be in  order it was taken (not album after album)
            selectedImages = ArrayList(albums.flatMap { it.imageIds }.sorted())
        } else {
            for (album in albums) {
                if (album.name == str) {
                    selectedImages = album.imageIds
                    break
                }
            }
        }
        selectedImages.reversed() as ArrayList<Long>
    }

    fun selectGallery(position: Int) {
        _selectedGallery.value = albumNames[position]
    }

    class ImageDrawerViewModelFactory(private val albums: ArrayList<Album>,
                                      private val allImagesStringLocalized: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ImageDrawerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ImageDrawerViewModel(albums, allImagesStringLocalized) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}