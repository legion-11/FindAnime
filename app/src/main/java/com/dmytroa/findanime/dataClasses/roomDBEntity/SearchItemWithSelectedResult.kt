package com.dmytroa.findanime.dataClasses.roomDBEntity

import androidx.room.Embedded
import androidx.room.Relation
import java.util.*
import kotlin.math.min

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

    fun getName(): String {
        return searchResult?.getName() ?: ""
    }

    fun getTextComparisonScore(str: String): Int {
        val name = getName().lowercase(Locale.ROOT)
        val comparableString = str.lowercase(Locale.ROOT)
        var score = 0
        for (i in 0 until min(comparableString.length, name.length)) {
            if (str[i] == name[i]) score += 1
            else break
        }
        return score
    }

}
