package com.example.bookcollection.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 统一管理网络请求。
 *
 * 数据源策略：
 *   1. 豆瓣读书（国内直连、免费、无需 AppKey）—— 主力
 *   2. Google Books（需要能访问 Google）—— 兜底
 *
 * 之前代码里把 BASE_URL 写成了万维易源，但接口路径却是 Google 的
 * books/v1/volumes，两边根本不是一家，所以永远搜不到书。
 * 万维易源还需要 appid + sign 签名，光有 AppKey 也用不了，这里直接去掉。
 */
object NetworkModule {

    private const val DOUBAN_BASE_URL = "https://book.douban.com/"
    private const val GOOGLE_BASE_URL = "https://www.googleapis.com/"

    /** 豆瓣会拦截默认的 okhttp UA，伪装成浏览器更稳 */
    const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", BROWSER_UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                    .header("Referer", "https://book.douban.com/")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }

    val doubanApi: DoubanApi by lazy {
        createRetrofit(DOUBAN_BASE_URL).create(DoubanApi::class.java)
    }

    val googleBooksApi: GoogleBooksApi by lazy {
        createRetrofit(GOOGLE_BASE_URL).create(GoogleBooksApi::class.java)
    }

    private fun createRetrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}
