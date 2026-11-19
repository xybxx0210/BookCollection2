package com.example.bookcollection.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Google Books API，作为豆瓣的备用数据源。
 * 免费、无需 AppKey，但需要能访问 Google（国内要挂代理）。
 */
interface GoogleBooksApi {

    @GET("books/v1/volumes")
    suspend fun searchBooks(
        @Query("q") keyword: String,
        @Query("maxResults") maxResults: Int,
        @Query("printType") printType: String
    ): BookResponse
}
