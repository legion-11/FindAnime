package com.dmytroa.findanime.fragments.search

import android.net.Uri

object Interfaces {
    interface SubmitSearchRequest {
        fun imageRequest(imageUri: Uri)
        fun urlRequest(url: String)
    }
}