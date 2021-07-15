package com.legion_11.findanime.dataClasses.retrofit

/**
 * body of the response for search requests to https://api.trace.moe/search
 *
 * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=response-format)
 */
data class SearchByImageRequestResult(
    val error: String?,
    val frameCount: Int?,
    val result: List<Result>
){
    override fun toString(): String {
        return "SearchByImageResult(error=$error, frameCount=$frameCount, result=$result)"
    }
}