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
    // ===== 标签绑定相关（第11步） =====

    // 绑定标签
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun bindTag(crossRef: com.example.bookcollection.data.local.entity.BookTagCrossRef)

    // 解绑标签
    @Query("DELETE FROM book_tag_cross_ref WHERE bookId = :bookId AND tagId = :tagId")
    suspend fun unbindTag(bookId: Long, tagId: Long)

    // 查询某本书绑定了哪些标签ID
    @Query("SELECT tagId FROM book_tag_cross_ref WHERE bookId = :bookId")
    suspend fun getBoundTagIds(bookId: Long): List<Long>
    // 根据标签ID查询图书列表（联表查询）
    @Query("SELECT b.* FROM books b INNER JOIN book_tag_cross_ref r ON b.id = r.bookId WHERE r.tagId = :tagId ORDER BY b.createdAt DESC")
    fun observeBooksByTag(tagId: Long): Flow<List<BookEntity>>
} // 👈 就是这个大括号！它是用来闭合最上面的 interface BookDao { 的
