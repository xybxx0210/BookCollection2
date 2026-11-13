package com.example.bookcollection.data.network

// 对应返回的根结构
data class BookResponse(
    val totalItems: Int,
    val items: List<BookItem>?
)

data class BookItem(
    val volumeInfo: VolumeInfo
)

data class VolumeInfo(
    val title: String?,
    val authors: List<String>?,
    val publisher: String?,
    val pageCount: Int?,
    val imageLinks: ImageLinks?
)

data class ImageLinks(
    val thumbnail: String?
)