package com.legion_11.findanime.dataClasses.retrofit

/**
 * anime titles of anime in different languages
 *
 * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=include-anilist-info)
 */
data class Title(
    val english: String?,
    val native: String?,
    val romaji: String?
){
    override fun toString(): String {
        return "Title(english=$english, native=$native, romaji=$romaji)"
    }
}