package com.dmytroa.findanime.dataClasses.retrofit

data class Result(
    val anilist: Int,
    val episode: Any,
    val filename: String,
    val from: Double,
    val image: String,
    val similarity: Double,
    val to: Double,
    val video: String
){
    override fun toString(): String {
        return "Result(anilist=$anilist, episode=$episode, filename='$filename', from=$from, image='$image', similarity=$similarity, to=$to, video='$video')"
    }
}