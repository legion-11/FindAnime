package com.dmytroa.findanime.dataClasses.retrofit

data class SearchByImageResult(
    val error: String,
    val frameCount: Int,
    val result: List<Result>
){
    override fun toString(): String {
        return "SearchByImageResult(error='$error', frameCount=$frameCount, result=$result)"
    }
}