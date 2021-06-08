package com.dmytroa.findanime.dataClasses

data class Album(
    var name: String,
){
    // id can be transformed to uri using
    // Uri.withAppendedPath(uriExternal, id.toString())
    val imageIds: ArrayList<Long> = arrayListOf()
    override fun toString(): String {
        return "Album(name='$name', images number=${imageIds.size})"
    }
}
