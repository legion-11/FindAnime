package com.dmytroa.findanime.dataClasses.retrofit

data class Title(
    val english: String?,
    val native: String?,
    val romaji: String?
){
    override fun toString(): String {
        return "Title(english=$english, native=$native, romaji=$romaji)"
    }
}