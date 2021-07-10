package com.dmytroa.findanime.dataClasses.retrofit

/**
 * additional information about anime title
 *
 * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=include-anilist-info)
 */
data class Anilist(
    val id: Int?,
    val idMal: Int?, // https://myanimelist.net/anime/{id}/
    val isAdult: Boolean?,
    val synonyms: List<String>?,
    val title: Title?
){
    override fun toString(): String {
        return "Anilist(id=$id, idMal=$idMal, isAdult=$isAdult, synonyms=$synonyms, title=$title)"
    }
}