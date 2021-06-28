package com.dmytroa.findanime.dataClasses.roomDBEntity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SearchItem(
    @PrimaryKey(autoGenerate = true) var id: Long,
    var imageFileName: String?, //without filesDir + separator
    var videoFileName: String?, //without externalFilesDir + separator
    var selectedResultId: Long?,
    var isBookmarked: Boolean = false,
    ){
    constructor(imageURI: String): this(0, imageURI,null, null, false)
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