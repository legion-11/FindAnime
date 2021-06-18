package com.dmytroa.findanime.dataClasses.retrofit

data class Quota(
    val concurrency: Int,
    val id: String,
    val priority: Int,
    val quota: Int,
    val quotaUsed: Int
){
    override fun toString(): String {
        return "Quota(concurrency=$concurrency, id='$id', priority=$priority, quota=$quota, quotaUsed=$quotaUsed)"
    }
}