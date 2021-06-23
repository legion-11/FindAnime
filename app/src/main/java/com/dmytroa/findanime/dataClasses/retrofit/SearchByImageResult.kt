package com.dmytroa.findanime.dataClasses.retrofit

data class SearchByImageResult(
    val error: String?,
    val frameCount: Int?,
    val result: List<Result>
)