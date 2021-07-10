package com.dmytroa.findanime.dataClasses

/**
 * Data class for saving images id in albums collections from public storage
 * **See** [com.dmytroa.findanime.repositories.LocalFilesRepository.getAlbums]
 */
data class Album(var name: String){
    // id can be transformed to uri using
    // Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
    val imageIds: ArrayList<Long> = arrayListOf()
    override fun toString(): String {
        return "Album(name='$name', images number=${imageIds.size})"
    }
}
