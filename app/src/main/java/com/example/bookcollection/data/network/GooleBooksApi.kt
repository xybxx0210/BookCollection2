package com.example.bookcollection.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleBooksApi {
    // 根据 ISBN 查询图书
    @GET("books/v1/volumes")
    suspend fun searchBookByIsbn(@Query("q") isbn: String): BookResponse
}