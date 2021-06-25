package com.dmytroa.findanime.dataClasses.roomDBEntity

import androidx.room.Embedded
import androidx.room.Relation

data class SearchItemWithSelectedResult(
    @Embedded val searchItem: SearchItem,
    @Relation(
        parentColumn = "selectedResultId",
        entityColumn = "id"
    )
    val searchResult: SearchResult?
){
    override fun toString(): String {
        return "SearchItemWithSelectedResult(searchItem=$searchItem, searchResult=$searchResult)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchItemWithSelectedResult

        if (searchItem != other.searchItem) return false
        if (searchResult != other.searchResult) return false

        return true
    }

    override fun hashCode(): Int {
        var result = searchItem.hashCode()
        result = 31 * result + searchResult.hashCode()
        return result
    }

}
