package com.example.bookcollection.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.bookcollection.data.local.dao.BookDao
import com.example.bookcollection.data.local.dao.TagDao
import com.example.bookcollection.data.local.entity.BookEntity
import com.example.bookcollection.data.local.entity.BookTagCrossRef
import com.example.bookcollection.data.local.entity.TagEntity

@Database(
    entities = [BookEntity::class, TagEntity::class, BookTagCrossRef::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "book_collection.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}