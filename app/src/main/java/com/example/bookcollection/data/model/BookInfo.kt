package com.example.bookcollection.data.model

/**
 * 统一的书本模型：不管是豆瓣还是 Google Books 搜到的，
 * 都先转换成这个对象，UI 层只认它。
 */
data class BookInfo(
    val title: String,
    val author: String? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val coverUrl: String? = null,
    val totalPages: Int = 0,
    val year: String? = null,
    /** 豆瓣 subject id，用来抓详情页（出版社 / 页数 / ISBN） */
    val doubanId: String? = null,
    /** 数据来源，界面上会显示 */
    val source: String = ""
)
