package com.dmytroa.findanime.dataClasses.retrofit

data class Result(
    val anilist: Anilist?,
    val episode: Any?,
    val filename: String?,
    val from: Double?,
    val image: String?,
    val similarity: Double?,
    val to: Double?,
    val video: String?
)