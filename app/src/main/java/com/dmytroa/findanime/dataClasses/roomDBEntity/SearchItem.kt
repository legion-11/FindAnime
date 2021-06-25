package com.dmytroa.findanime.dataClasses.roomDBEntity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SearchItem(
    @PrimaryKey(autoGenerate = true) var id: Long,
    var imageURI: String?, //without filesDir + separator
    var videoURI: String?, //without externalFilesDir + separator
    var selectedResultId: Long?,
    var isBookmarked: Boolean = false,
    ){
    constructor(imageURI: String): this(0, imageURI,null, null, false)
    val isFinished
        get() = videoURI != null

    override fun toString(): String {
        return "SearchItem(id=$id, imageURI=$imageURI, videoURI=$videoURI, selectedResult=$selectedResultId, isBookmarked=$isBookmarked)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchItem

        if (id != other.id) return false
        if (imageURI != other.imageURI) return false
        if (videoURI != other.videoURI) return false
        if (selectedResultId != other.selectedResultId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (imageURI?.hashCode() ?: 0)
        result = 31 * result + (videoURI?.hashCode() ?: 0)
        result = 31 * result + (selectedResultId?.hashCode() ?: 0)
        result = 31 * result + isBookmarked.hashCode()
        return result
    }

}