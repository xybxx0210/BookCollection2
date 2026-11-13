package com.example.bookcollection.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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

    private var currentBookId: Long = 0L
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)
        db = AppDatabase.getInstance(this)

        // UI 绑定
        val ivCover = findViewById<ImageView>(R.id.ivCover)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvAuthor = findViewById<TextView>(R.id.tvAuthor)
        val tvPublisher = findViewById<TextView>(R.id.tvPublisher)
        val etTotalPages = findViewById<EditText>(R.id.etTotalPages)
        val etReadPages = findViewById<EditText>(R.id.etReadPages)
        val btnSaveProgress = findViewById<Button>(R.id.btnSaveProgress)
        val btnCollect = findViewById<Button>(R.id.btnCollect)

        // 接收传过来的 bookId
        currentBookId = intent.getLongExtra("bookId", 0L)

        if (currentBookId > 0L) {
            // ===== 从书架进来的：读数据库 =====
            btnCollect.visibility = android.view.View.GONE // 已经收藏了，隐藏按钮
            lifecycleScope.launch {
                val book = db.bookDao().getById(currentBookId)
                if (book != null) {
                    tvTitle.text = book.title
                    tvAuthor.text = book.author ?: "未知作者"
                    tvPublisher.text = book.publisher ?: "未知出版社"
                    etTotalPages.setText(book.totalPages.toString())
                    etReadPages.setText(book.readPages.toString())

                    if (!book.coverUrl.isNullOrEmpty()) {
                        Glide.with(this@BookDetailActivity).load(book.coverUrl).placeholder(R.mipmap.ic_launcher).into(ivCover)
                    }
                }
            }
        } else {
            // ===== 从扫码进来的：使用 Intent 传入的数据 =====
            val isbn = intent.getStringExtra("isbn") ?: ""
            val title = intent.getStringExtra("title") ?: "未知书名"
            val author = intent.getStringExtra("author") ?: "未知作者"
            val publisher = intent.getStringExtra("publisher") ?: "未知出版社"
            val coverUrl = intent.getStringExtra("coverUrl") ?: ""
            val totalPages = intent.getIntExtra("totalPages", 0)

            tvTitle.text = title
            tvAuthor.text = author
            tvPublisher.text = publisher
            etTotalPages.setText(totalPages.toString())
            etReadPages.setText("0")

            if (coverUrl.isNotEmpty()) {
                Glide.with(this).load(coverUrl).placeholder(R.mipmap.ic_launcher).into(ivCover)
            }

            // 扫码进来的，点击加入书架
            btnCollect.setOnClickListener {
                lifecycleScope.launch {
                    val newBook = BookEntity(
                        isbn = isbn.ifEmpty { null },
                        title = title,
                        author = author,
                        publisher = publisher,
                        coverUrl = coverUrl,
                        totalPages = totalPages,
                        readPages = 0
                    )
                    val result = db.bookDao().insert(newBook)
                    if (result != -1L) {
                        Toast.makeText(this@BookDetailActivity, "已加入书架！", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@BookDetailActivity, "这本书已经在书架里啦", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // ===== 保存进度逻辑 =====
        btnSaveProgress.setOnClickListener {
            val total = etTotalPages.text.toString().toIntOrNull() ?: 0
            val read = etReadPages.text.toString().toIntOrNull() ?: 0

            if (read > total && total > 0) {
                Toast.makeText(this, "已读页数不能大于总页数哦", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 如果是扫码刚进来的，还没入库（currentBookId == 0），先存一遍再更新
            lifecycleScope.launch {
                if (currentBookId > 0L) {
                    // 已经存在的书，直接更新
                    val book = db.bookDao().getById(currentBookId)
                    if (book != null) {
                        db.bookDao().update(book.copy(totalPages = total, readPages = read))
                        Toast.makeText(this@BookDetailActivity, "进度已保存", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // 如果是扫描进来的新书，先插入，拿到 id
                    val newBook = BookEntity(
                        isbn = intent.getStringExtra("isbn"),
                        title = tvTitle.text.toString(),
                        author = tvAuthor.text.toString(),
                        publisher = tvPublisher.text.toString(),
                        coverUrl = intent.getStringExtra("coverUrl") ?: "",
                        totalPages = total,
                        readPages = read
                    )
                    val newId = db.bookDao().insert(newBook)
                    if (newId != -1L) {
                        currentBookId = newId // 更新当前 ID
                        btnCollect.visibility = android.view.View.GONE // 已经加入了，隐藏按钮
                        Toast.makeText(this@BookDetailActivity, "已加入书架并保存进度", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@BookDetailActivity, "保存失败，书可能已存在", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}