package com.dmytroa.findanime.retrofit

import com.dmytroa.findanime.dataClasses.retrofit.Quota
import com.dmytroa.findanime.dataClasses.retrofit.SearchByImageResult
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*


interface SearchService {

    @Multipart
    @POST("/search")
    fun searchByImage(
        @Part image: MultipartBody.Part): Call<SearchByImageResult>

    @GET("/me")
    suspend fun getQuota() : Response<Quota>

    @GET
    @Streaming
    fun getVideoPreview(@Url url: String): Call<ResponseBody>
}