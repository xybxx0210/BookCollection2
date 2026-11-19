package com.example.bookcollection.data.network

import com.google.gson.annotations.SerializedName

// ==================== Google Books API 的返回结构 ====================

data class BookResponse(
    val totalItems: Int? = null,
    val items: List<BookItem>? = null
)

data class BookItem(
    val volumeInfo: VolumeInfo? = null
)

data class VolumeInfo(
    val title: String? = null,
    val authors: List<String>? = null,
    val publisher: String? = null,
    val pageCount: Int? = null,
    val imageLinks: ImageLinks? = null,
    val industryIdentifiers: List<IndustryIdentifier>? = null
)

data class ImageLinks(
    val thumbnail: String? = null,
    val smallThumbnail: String? = null
)

data class IndustryIdentifier(
    val type: String? = null,
    val identifier: String? = null
)

// ==================== 豆瓣读书「联想搜索」的返回结构 ====================
// GET https://book.douban.com/j/subject_suggest?q=关键词
// 返回 JSON 数组，不需要 AppKey，也不需要登录。

data class DoubanSuggestDto(
    val title: String? = null,
    @SerializedName("author_name")
    val authorName: String? = null,
    val pic: String? = null,
    val url: String? = null,
    val year: String? = null,
    val id: String? = null,
    val type: String? = null
)
