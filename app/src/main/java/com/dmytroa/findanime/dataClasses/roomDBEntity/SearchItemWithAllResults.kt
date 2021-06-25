package com.dmytroa.findanime.dataClasses.roomDBEntity

import androidx.room.Embedded
import androidx.room.Relation

data class SearchItemWithAllResults(
    @Embedded val searchItem: SearchItem,
    @Relation(
        parentColumn = "id",
        entityColumn = "parentId"
    )
    val searchResult: List<SearchResult>
){
    override fun toString(): String {
        return "SearchItemWithAllResults(searchItem=$searchItem, searchResult=$searchResult)"
    }
}
