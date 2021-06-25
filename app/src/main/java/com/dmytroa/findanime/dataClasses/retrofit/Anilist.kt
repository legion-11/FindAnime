package com.dmytroa.findanime.dataClasses.retrofit

data class Anilist(
    val id: Int?,
    val idMal: Int?,
    val isAdult: Boolean?,
    val synonyms: List<String>?,
    val title: Title?
){
    override fun toString(): String {
        return "Anilist(id=$id, idMal=$idMal, isAdult=$isAdult, synonyms=$synonyms, title=$title)"
    }
}