package com.legion_11.findanime.dataClasses.retrofit

/**
 * response from trace moe API to https://api.trace.moe/me
 *
 * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=me)
 */
data class Quota(
    val concurrency: Int, // how many search requests can be executed at the same time
                          // search will return error: "concurrency limit exceeded" if you try
                          // more than that number
    val id: String,       // IP address (guest) or email address (user) (for now it is always guest)
    val priority: Int,    //https://soruly.github.io/trace.moe-api/#/limits
    val quota: Int,
    val quotaUsed: Int
){
    override fun toString(): String {
        return "Quota(concurrency=$concurrency, id='$id', priority=$priority, quota=$quota, quotaUsed=$quotaUsed)"
    }
}