package com.dmytroa.findanime.dataClasses.retrofit

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Result(
    val anilist: Anilist?,
    @Expose
    @SerializedName("episode")
    private val _episode: Any?,
    val filename: String,
    val from: Double,
    val image: String,
    @Expose
    @SerializedName("similarity")
    private val _similarity: Double,
    val to: Double,
    val video: String
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