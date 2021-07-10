package com.dmytroa.findanime.fragments.search

import android.net.Uri

object Interfaces {
    /**
     * interface submitting image/url data  from [com.dmytroa.findanime.MainActivity]
     * to [SearchFragment]
     */
    interface SubmitSearchRequest {
        fun createRequest(uriOrUrl: SearchOption)
    }

    /**
     * interface for holding url/uri while user specifies search params (mute, cut borders, ...)
     */
    sealed class SearchOption {
        data class MyUrl(val holding: String): SearchOption()
        data class MyUri(val holding: Uri): SearchOption()
    }
}