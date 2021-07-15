package com.legion_11.findanime.fragments.search

import android.net.Uri

object Interfaces {
    /**
     * interface submitting image/url data  from [com.legion_11.findanime.MainActivity]
     * to [SearchFragment]
     */
    interface SubmitSearchRequest {
        fun createRequest(uriOrUrl: SearchOption)
    }

    /**
     * interface for holding url/uri while user specifies search params (mute, cut borders, ...)
     */
    sealed class SearchOption {
        data class MyUrl(val url: String): SearchOption()
        data class MyUri(val uri: Uri): SearchOption()
    }
}