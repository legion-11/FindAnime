package com.legion_11.findanime.dataClasses.retrofit

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * response for search request to https://api.trace.moe/search
 *
 * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=response-format)
 */
data class Result(
    val anilist: Anilist?,
    @Expose
    @SerializedName("episode")
    private val _episode: Any?, // can come as null, int, or string ("122|123")
    val filename: String,       // name of video file on API server
    val from: Double,           // Starting time of the matching scene (seconds)
    val image: String,          // URL to the preview image of the matching scene
    @Expose
    @SerializedName("similarity")
    private val _similarity: Double, // Similarity compared to the search image
    val to: Double,             // Ending time of the matching scene (seconds)
    val video: String           // URL to the preview video of the matching scene
) {
    val episode: String
        get() = (_episode ?: "").toString()
    val similarity: String
        get() = "%.2f".format(_similarity)

    override fun toString(): String {
        return "Result(anilist=$anilist, filename=$filename, from=$from, image=$image, to=$to," +
                " video=$video, episode='$episode', similarity='$similarity')"
    }
}