package com.dmytroa.findanime.dataClasses.roomDBEntity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [ForeignKey(
        entity = SearchItem::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("parentId"),
        onDelete = ForeignKey.CASCADE
)])
data class SearchResult(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(index = true) val parentId: Long,
    var fileName: String,
    val english: String?,
    val nativeTitle: String?,
    val romaji: String?,
    val idMal: Int?,
    val isAdult: Boolean?,
    var episode: String?,
    var from: Double,
    var similarity: String,
    var video: String,
){
    override fun toString(): String {
        return "SearchResult(id=$id, parentId=$parentId, fileName=$fileName, english=$english, native=$nativeTitle, romaji=$romaji, idMal=$idMal, isAdult=$isAdult, episode=$episode, from=$from, similarity=$similarity)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchResult

        if (id != other.id) return false
        if (parentId != other.parentId) return false
        if (fileName != other.fileName) return false
        if (english != other.english) return false
        if (nativeTitle != other.nativeTitle) return false
        if (romaji != other.romaji) return false
        if (idMal != other.idMal) return false
        if (isAdult != other.isAdult) return false
        if (episode != other.episode) return false
        if (from != other.from) return false
        if (similarity != other.similarity) return false
        if (video != other.video) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + parentId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + (english?.hashCode() ?: 0)
        result = 31 * result + (nativeTitle?.hashCode() ?: 0)
        result = 31 * result + (romaji?.hashCode() ?: 0)
        result = 31 * result + (idMal ?: 0)
        result = 31 * result + (isAdult?.hashCode() ?: 0)
        result = 31 * result + (episode?.hashCode() ?: 0)
        result = 31 * result + from.hashCode()
        result = 31 * result + similarity.hashCode()
        result = 31 * result + video.hashCode()
        return result
    }
}
