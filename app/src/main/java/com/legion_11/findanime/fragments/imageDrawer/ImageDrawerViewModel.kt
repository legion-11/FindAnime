package com.legion_11.findanime.fragments.imageDrawer

import androidx.lifecycle.*
import com.legion_11.findanime.dataClasses.Album
import com.legion_11.findanime.fragments.imageDrawer.ImageDrawerListDialogFragment.Companion.GALLERY_TYPE

/**
 * viewModel for [com.legion_11.findanime.fragments.imageDrawer.ImageDrawerListDialogFragment]
 * @param albums list of all albums from public storage
 *               **See** [com.legion_11.findanime.repositories.LocalFilesRepository.getAlbums]
 * @param allImagesStringLocalized name for album with all images
 */
class ImageDrawerViewModel(private val albums: ArrayList<Album>,
                           allImagesStringLocalized: String) : ViewModel() {

    // all album names (first name is special name for all images)
    val albumNames = listOf(allImagesStringLocalized) +
            albums.sortedByDescending { it.imageIds.maxOrNull() } // the later photo was taken, the earlier album will be in the list
                .map { it.name }

    private val _selectedGallery = MutableLiveData(allImagesStringLocalized)

    val images: LiveData<ArrayList<Long>> = Transformations.map(_selectedGallery) { str ->
        if (str == allImagesStringLocalized) {
            //sorted - so images will be in  order it was taken (not album after album)
            ArrayList( listOf((GALLERY_TYPE).toLong()) + albums.flatMap { it.imageIds }.sorted().reversed())
        } else {
            albums.first { it.name == str }.imageIds
        }
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