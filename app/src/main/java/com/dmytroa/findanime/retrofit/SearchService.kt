package com.dmytroa.findanime.retrofit

import com.dmytroa.findanime.dataClasses.retrofit.Quota
import com.dmytroa.findanime.dataClasses.retrofit.SearchByImageRequestResult
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*


interface SearchService {

    @Multipart
    @POST("/search?anilistInfo")
    fun searchByImage(@Part image: MultipartBody.Part) : Call<SearchByImageRequestResult>

    @GET("/search?anilistInfo")
    fun searchByUrl(@Query("url") url: String) : Call<SearchByImageRequestResult>

    @GET("/me")
    fun getQuota() : Call<Quota>

    @GET
    @Streaming
    fun getVideoPreview(@Url url: String) : Call<ResponseBody>
}