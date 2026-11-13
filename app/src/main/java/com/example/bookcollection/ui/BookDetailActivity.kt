package com.example.bookcollection.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.bookcollection.R
import com.example.bookcollection.data.local.AppDatabase
import com.example.bookcollection.data.local.entity.BookEntity
import com.example.bookcollection.data.local.entity.BookTagCrossRef
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class BookDetailActivity : AppCompatActivity() {

    private var currentBookId: Long = 0L
    private lateinit var db: AppDatabase
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var etTotalPages: EditText
    private lateinit var etReadPages: EditText
    private lateinit var chipGroupTags: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)
        db = AppDatabase.getInstance(this)

        // UI 绑定
        val ivCover = findViewById<ImageView>(R.id.ivCover)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvAuthor = findViewById<TextView>(R.id.tvAuthor)
        val tvPublisher = findViewById<TextView>(R.id.tvPublisher)
        etTotalPages = findViewById(R.id.etTotalPages)
        etReadPages = findViewById(R.id.etReadPages)
        progressBar = findViewById(R.id.progressBar)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        chipGroupTags = findViewById(R.id.chipGroupTags)
        val btnSaveProgress = findViewById<Button>(R.id.btnSaveProgress)
        val btnResetProgress = findViewById<Button>(R.id.btnResetProgress)
        val btnCollect = findViewById<Button>(R.id.btnCollect)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateProgressBar()
            }
        }
        etTotalPages.addTextChangedListener(textWatcher)
        etReadPages.addTextChangedListener(textWatcher)

        currentBookId = intent.getLongExtra("bookId", 0L)

        if (currentBookId > 0L) {
            btnCollect.visibility = View.GONE
            loadBookDetail(currentBookId, ivCover, tvTitle, tvAuthor, tvPublisher)
            loadTagsForBook(currentBookId) // 加载该书的标签
        } else {
            // 扫码进来的新书，先不支持绑标签（因为还没 ID）
            chipGroupTags.visibility = View.GONE

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

            btnCollect.setOnClickListener {
                lifecycleScope.launch {
                    val newBook = BookEntity(
                        isbn = isbn.ifEmpty { null }, title = title, author = author,
                        publisher = publisher, coverUrl = coverUrl,
                        totalPages = totalPages, readPages = 0
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

        btnSaveProgress.setOnClickListener {
            val total = etTotalPages.text.toString().toIntOrNull() ?: 0
            val read = etReadPages.text.toString().toIntOrNull() ?: 0
            if (read > total && total > 0) {
                Toast.makeText(this, "已读页数不能大于总页数哦", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                if (currentBookId > 0L) {
                    val book = db.bookDao().getById(currentBookId)
                    if (book != null) {
                        db.bookDao().update(book.copy(totalPages = total, readPages = read))
                        Toast.makeText(this@BookDetailActivity, "进度已保存", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val newBook = BookEntity(
                        isbn = intent.getStringExtra("isbn"), title = tvTitle.text.toString(),
                        author = tvAuthor.text.toString(), publisher = tvPublisher.text.toString(),
                        coverUrl = intent.getStringExtra("coverUrl") ?: "",
                        totalPages = total, readPages = read
                    )
                    val newId = db.bookDao().insert(newBook)
                    if (newId != -1L) {
                        currentBookId = newId
                        btnCollect.visibility = View.GONE
                        Toast.makeText(this@BookDetailActivity, "已加入书架并保存进度", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnResetProgress.setOnClickListener {
            etReadPages.setText("0")
            progressBar.progress = 0
            tvProgressPercent.text = "阅读进度: 0%"
            Toast.makeText(this, "已重置为 0", Toast.LENGTH_SHORT).show()
        }
    }

    // 独立出来的加载书籍详情
    private fun loadBookDetail(id: Long, ivCover: ImageView, tvTitle: TextView, tvAuthor: TextView, tvPublisher: TextView) {
        lifecycleScope.launch {
            val book = db.bookDao().getById(id)
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
    }

    // 加载所有标签，并判断哪个被选中了
    private fun loadTagsForBook(bookId: Long) {
        lifecycleScope.launch {
            val allTags = db.tagDao().getAllOnce()
            val boundTagIds = db.bookDao().getBoundTagIds(bookId)

            chipGroupTags.removeAllViews() // 先清空

            for (tag in allTags) {
                // 动态创建一个小 Chip 按钮
                val chip = Chip(this@BookDetailActivity)
                chip.text = tag.name
                chip.isCheckable = true
                // 如果这本书已经绑定了这个标签，就默认勾选
                chip.isChecked = boundTagIds.contains(tag.id)

                // 监听点击事件：勾选就存入，取消就删除
                chip.setOnCheckedChangeListener { _, isChecked ->
                    lifecycleScope.launch {
                        if (isChecked) {
                            db.bookDao().bindTag(BookTagCrossRef(bookId, tag.id))
                        } else {
                            db.bookDao().unbindTag(bookId, tag.id)
                        }
                    }
                }
                // 把按钮加到屏幕上
                chipGroupTags.addView(chip)
            }
        }
    }

    private fun updateProgressBar() {
        val totalStr = etTotalPages.text.toString()
        val readStr = etReadPages.text.toString()
        val total = if (totalStr.isEmpty()) 0 else totalStr.toIntOrNull() ?: 0
        val read = if (readStr.isEmpty()) 0 else readStr.toIntOrNull() ?: 0
        if (total > 0) {
            var percent = (read * 100) / total
            if (percent > 100) percent = 100
            progressBar.progress = percent
            tvProgressPercent.text = "阅读进度: $percent%"
        } else {
            progressBar.progress = 0
            tvProgressPercent.text = "阅读进度: 0%"
        }
    }
}