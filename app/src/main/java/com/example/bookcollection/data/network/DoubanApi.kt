package com.example.bookcollection.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 豆瓣读书的联想搜索接口（网页版搜索框在用的那个），
 * 免费、无需 AppKey，国内可直连。
 */
interface DoubanApi {

    @GET("j/subject_suggest")
    suspend fun suggest(@Query("q") keyword: String): List<DoubanSuggestDto>
}
