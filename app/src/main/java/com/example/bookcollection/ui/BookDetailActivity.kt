package com.example.bookcollection.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.bookcollection.R
import com.example.bookcollection.data.local.AppDatabase
import com.example.bookcollection.data.local.entity.BookEntity
import kotlinx.coroutines.launch

class BookDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)

        // 1. 接收前面传过来的数据
        val isbn = intent.getStringExtra("isbn") ?: ""
        val title = intent.getStringExtra("title") ?: "未知书名"
        val author = intent.getStringExtra("author") ?: "未知作者"
        val publisher = intent.getStringExtra("publisher") ?: "未知出版社"
        val coverUrl = intent.getStringExtra("coverUrl") ?: ""
        val totalPages = intent.getIntExtra("totalPages", 0)

        // 2. 绑定 UI
        val ivCover = findViewById<ImageView>(R.id.ivCover)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvAuthor = findViewById<TextView>(R.id.tvAuthor)
        val tvPublisher = findViewById<TextView>(R.id.tvPublisher)
        val btnCollect = findViewById<Button>(R.id.btnCollect)

        tvTitle.text = title
        tvAuthor.text = author
        tvPublisher.text = publisher

        // 3. 使用 Glide 加载封面图片
        if (coverUrl.isNotEmpty()) {
            Glide.with(this)
                .load(coverUrl)
                .placeholder(R.mipmap.ic_launcher) // 加载中显示默认图标
                .into(ivCover)
        }

        // 4. 点击加入书架，存入 Room 数据库
        btnCollect.setOnClickListener {
            val db = AppDatabase.getInstance(this)
            val bookDao = db.bookDao()

            lifecycleScope.launch {
                val newBook = BookEntity(
                    isbn = isbn.ifEmpty { null },
                    title = title,
                    author = author,
                    publisher = publisher,
                    coverUrl = coverUrl,
                    totalPages = totalPages
                )
                val result = bookDao.insert(newBook)
                if (result != -1L) {
                    Toast.makeText(this@BookDetailActivity, "已加入书架！", Toast.LENGTH_SHORT).show()
                    finish() // 关闭当前页面
                } else {
                    Toast.makeText(this@BookDetailActivity, "这本书已经在书架里啦", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}