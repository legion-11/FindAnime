package com.dmytroa.findanime.dataClasses.roomDBEntity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SearchItem(
    @PrimaryKey(autoGenerate = true) var id: Long,
    var fileName: String?,
    var episode: String?,
    var from: Double?,
    var similarity: Double?,
    var imageURI: String,
    var video: String?,
    var finished: Boolean,
    var isBookmarked: Boolean = false,
    ){
    constructor(imageURI: String): this(0,null, null,null, null, imageURI, null, false)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchItem

        if (id != other.id) return false
        if (fileName != other.fileName) return false
        if (episode != other.episode) return false
        if (from != other.from) return false
        if (similarity != other.similarity) return false
        if (imageURI != other.imageURI) return false
        if (video != other.video) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (episode?.hashCode() ?: 0)
        result = 31 * result + (from?.hashCode() ?: 0)
        result = 31 * result + (similarity?.hashCode() ?: 0)
        result = 31 * result + imageURI.hashCode()
        result = 31 * result + (video?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "SearchItem(id=$id, fileName=$fileName, episode=$episode, from=$from," +
                " similarity=$similarity, imageURI='$imageURI', video=$video)"
    }


}