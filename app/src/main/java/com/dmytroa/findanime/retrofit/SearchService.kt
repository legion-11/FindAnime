package com.dmytroa.findanime.retrofit

import com.dmytroa.findanime.dataClasses.retrofit.Quota
import com.dmytroa.findanime.dataClasses.retrofit.SearchByImageRequestResult
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*


interface SearchService {

    /**
     * request to trace moe API
     * @return  [SearchByImageRequestResult]
     * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=search-by-image-upload)
     */
    @Multipart
    @POST("/search?anilistInfo")
    fun searchByImage(@Part image: MultipartBody.Part,
                      @Query("cutBorders") cutBorders: String?): Call<SearchByImageRequestResult>

    /**
     * request to trace moe API
     * @return  [SearchByImageRequestResult]
     * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=search-by-image-url)
     */
    @GET("/search?anilistInfo")
    fun searchByUrl(@Query("url") url: String,
                    @Query("cutBorders") cutBorders: String?): Call<SearchByImageRequestResult>

    /**
     * request to trace moe API
     * @return  [Quota]
     * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=me)
     */
    @GET("/me")
    fun getQuota() : Call<Quota>

    /**
     * request to trace moe API
     * @return  ResponseBody with video file
     * [See API](https://soruly.github.io/trace.moe-api/#/docs?id=media-preview)
     */
    @Streaming
    @GET
    fun getVideoPreview(@Url url: String,
                        @Query("size") size: String,
                        @Query("mute") mute: String?
    ) : Call<ResponseBody>
}