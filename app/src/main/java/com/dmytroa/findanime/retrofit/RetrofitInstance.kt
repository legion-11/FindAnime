package com.dmytroa.findanime.retrofit

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


class RetrofitInstance {
    companion object {
        private var  retrofit: Retrofit? = null

        fun getInstance(): Retrofit {

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60,TimeUnit.SECONDS)
                .build()

            return retrofit ?: Retrofit.Builder().baseUrl("https://api.trace.moe/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
                .build()
                .also { retrofit = it }
        }
    }
}