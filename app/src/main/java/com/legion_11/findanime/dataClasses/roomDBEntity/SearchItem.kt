package com.legion_11.findanime.dataClasses.roomDBEntity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room db entity for saved search
 * can represent unfinished search request if videoFileName == null
 * in that case request will be repeated
 *
 * @param url url to image for search request
 * @param imageFileName name of the temporary saved in filesDir image for search request via image
 *            fileName and corresponding file will be saved until request succeed with
 *            listOf([com.legion_11.findanime.dataClasses.retrofit.Result])
 * @param id primary key in database, used as a foreign key for
 *             com.legion_11.findanime.dataClasses.roomDBEntity.SearchResult
 * @param size can be "s", "m", "l", represents quality of downloaded preview;
 *            used as a @Querry param size for
 *             [com.legion_11.findanime.retrofit.SearchService#getVideoPreview]
 * @param mute if true adds @Querry param mute for
 *             [com.legion_11.findanime.retrofit.SearchService#getVideoPreview]
 * @param cutBlackBorders if true adds @Querry param cutBorders for
 *             [com.legion_11.findanime.retrofit.SearchService#getVideoPreview]
 * @param showHContent if false after response from https://api.trace.moe/search
 *             all [com.legion_11.findanime.dataClasses.retrofit.Result]'s will be filtered
 *             by it's isAdult param
 * @param videoFileName name of the video file in external storage
 * @param selectedResultId foreign key for selected
 *             com.legion_11.findanime.dataClasses.roomDBEntity.SearchResult
 * @param isBookmarked needed for filtering
 */
@Entity
data class SearchItem(
    @PrimaryKey(autoGenerate = true) var id: Long,
    var url: String?,
    var imageFileName: String?,   // if add without filesDir + separator give path to file
    val size: String,             // needed for repeating request if it fails
    val mute: Boolean,            // needed for repeating request if it fails
    val cutBlackBorders: Boolean, // needed for repeating request if it fails
    val showHContent: Boolean,    // needed for repeating request if it fails
    var videoFileName: String?,   // if add externalFilesDir + separator give path to file
    var selectedResultId: Long?,
    var isBookmarked: Boolean = false,
    ){
    constructor(url: String?, localImageFileName: String?, size: String, mute: Boolean,
                cutBlackBorders: Boolean, showHContent: Boolean):
            this(0,url, localImageFileName, size, mute, cutBlackBorders, showHContent,
                null, null, false)

    val isFinished
        get() = videoFileName != null

    override fun toString(): String {
        return "SearchItem(id=$id, imageFileName=$imageFileName, videoFileName=$videoFileName, selectedResult=$selectedResultId, isBookmarked=$isBookmarked)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchItem

        if (id != other.id) return false
        if (imageFileName != other.imageFileName) return false
        if (videoFileName != other.videoFileName) return false
        if (selectedResultId != other.selectedResultId) return false
        // I have skipped isBookmarked because I do not
        // want video to stop when user presses bookmarks button

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (imageFileName?.hashCode() ?: 0)
        result = 31 * result + (videoFileName?.hashCode() ?: 0)
        result = 31 * result + (selectedResultId?.hashCode() ?: 0)
        result = 31 * result + isBookmarked.hashCode()
        return result
    }

}