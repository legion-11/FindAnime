package com.dmytroa.findanime.dataClasses.retrofit

data class Anilist(
    val id: Int?,
    val idMal: Int?,
    val isAdult: Boolean?,
    val synonyms: List<String>?,
    val title: Title?
)