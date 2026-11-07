package com.example.bookcollection.data.local.dao

import androidx.room.*
import com.example.bookcollection.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun getByIsbn(isbn: String): BookEntity?

    @Query("UPDATE books SET readPages = :readPages WHERE id = :bookId")
    suspend fun updateProgress(bookId: Long, readPages: Int)
}