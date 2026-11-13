package com.example.bookcollection.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["isbn"], unique = true)]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val isbn: String? = null,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val publisher: String? = null,
    val totalPages: Int = 0,
    val readPages: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)