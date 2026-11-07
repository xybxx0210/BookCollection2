package com.example.bookcollection.data.local.dao

import androidx.room.*
import com.example.bookcollection.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<TagEntity>
}