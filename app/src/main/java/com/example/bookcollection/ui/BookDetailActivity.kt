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
import com.example.bookcollection.R
import com.example.bookcollection.data.local.AppDatabase
import com.example.bookcollection.data.local.entity.BookEntity
import com.example.bookcollection.data.local.entity.BookTagCrossRef
import com.example.bookcollection.ui.common.CoverLoader
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

/**
 * 图书详情页，两种进入方式：
 *   1. 书架里点某本书 → 传 EXTRA_BOOK_ID，直接读数据库
 *   2. 联网搜索 / 扫码查到的新书 → 传书名、作者、ISBN 等信息，点「加入书架」后入库
 */
class BookDetailActivity : AppCompatActivity() {

    private var currentBookId: Long = 0L
    private var pendingIsbn: String? = null

    private lateinit var db: AppDatabase
    private lateinit var ivCover: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var tvPublisher: TextView
    private lateinit var tvIsbn: TextView
    private lateinit var etTotalPages: EditText
    private lateinit var etReadPages: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var chipGroupTags: ChipGroup
    private lateinit var btnCollect: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)
        db = AppDatabase.getInstance(this)

        ivCover = findViewById(R.id.ivCover)
        tvTitle = findViewById(R.id.tvTitle)
        tvAuthor = findViewById(R.id.tvAuthor)
        tvPublisher = findViewById(R.id.tvPublisher)
        tvIsbn = findViewById(R.id.tvIsbn)
        etTotalPages = findViewById(R.id.etTotalPages)
        etReadPages = findViewById(R.id.etReadPages)
        progressBar = findViewById(R.id.progressBar)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        chipGroupTags = findViewById(R.id.chipGroupTags)
        btnCollect = findViewById(R.id.btnCollect)

        val btnSaveProgress = findViewById<Button>(R.id.btnSaveProgress)
        val btnResetProgress = findViewById<Button>(R.id.btnResetProgress)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateProgressBar()
            }
        }
        etTotalPages.addTextChangedListener(textWatcher)
        etReadPages.addTextChangedListener(textWatcher)

        currentBookId = intent.getLongExtra(EXTRA_BOOK_ID, 0L)

        if (currentBookId > 0L) {
            btnCollect.visibility = View.GONE
            loadBookDetail(currentBookId)
            loadTagsForBook(currentBookId)
        } else {
            showNewBook()
        }

        btnCollect.setOnClickListener { collectNewBook() }

        btnSaveProgress.setOnClickListener {
            val total = etTotalPages.text.toString().toIntOrNull() ?: 0
            val read = etReadPages.text.toString().toIntOrNull() ?: 0

            if (read > total && total > 0) {
                Toast.makeText(this, "已读页数不能大于总页数哦", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveProgress(total, read)
        }

        btnResetProgress.setOnClickListener {
            etReadPages.setText("0")
            updateProgressBar()
            Toast.makeText(this, "已重置为 0", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 新书（从搜索结果 / 扫码进来） ====================

    private fun showNewBook() {
        pendingIsbn = intent.getStringExtra(EXTRA_ISBN)?.trim()?.takeIf { it.isNotEmpty() }

        tvTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: "未知书名"
        tvAuthor.text = intent.getStringExtra(EXTRA_AUTHOR) ?: "未知作者"

        val publisher = intent.getStringExtra(EXTRA_PUBLISHER)
        tvPublisher.text = publisher?.takeIf { it.isNotBlank() } ?: "未知出版社"

        tvIsbn.text = pendingIsbn?.let { "ISBN：$it" } ?: ""
        tvIsbn.visibility = if (pendingIsbn == null) View.GONE else View.VISIBLE

        val totalPages = intent.getIntExtra(EXTRA_TOTAL_PAGES, 0)
        etTotalPages.setText(if (totalPages > 0) totalPages.toString() else "")
        etReadPages.setText("0")

        val coverUrl = intent.getStringExtra(EXTRA_COVER_URL)
        CoverLoader.load(ivCover, coverUrl)

        // 还没入库，没有 bookId，先不显示标签区
        chipGroupTags.visibility = View.GONE
        btnCollect.visibility = View.VISIBLE
    }

    private fun collectNewBook() {
        lifecycleScope.launch {
            val total = etTotalPages.text.toString().toIntOrNull() ?: 0
            val read = etReadPages.text.toString().toIntOrNull() ?: 0

            val existingId = pendingIsbn?.let { db.bookDao().getByIsbn(it)?.id }
            if (existingId != null && existingId > 0L) {
                onBookCollected(existingId, "这本书已经在书架里啦")
                return@launch
            }

            val newId = db.bookDao().insert(
                BookEntity(
                    isbn = pendingIsbn,
                    title = tvTitle.text.toString(),
                    author = tvAuthor.text.toString().takeIf { it.isNotBlank() },
                    publisher = tvPublisher.text.toString().takeIf { it.isNotBlank() },
                    coverUrl = intent.getStringExtra(EXTRA_COVER_URL),
                    totalPages = total,
                    readPages = read
                )
            )

            if (newId == -1L) {
                Toast.makeText(this@BookDetailActivity, "这本书已经在书架里啦", Toast.LENGTH_SHORT).show()
            } else {
                onBookCollected(newId, "已加入书架！")
            }
        }
    }

    private fun onBookCollected(bookId: Long, message: String) {
        currentBookId = bookId
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        btnCollect.visibility = View.GONE
        chipGroupTags.visibility = View.VISIBLE
        loadTagsForBook(bookId)
    }

    // ==================== 已有书 ====================

    private fun loadBookDetail(id: Long) {
        lifecycleScope.launch {
            val book = db.bookDao().getById(id) ?: return@launch

            tvTitle.text = book.title
            tvAuthor.text = book.author ?: "未知作者"
            tvPublisher.text = book.publisher ?: "未知出版社"

            tvIsbn.text = book.isbn?.takeIf { it.isNotBlank() }?.let { "ISBN：$it" } ?: ""
            tvIsbn.visibility = if (book.isbn.isNullOrBlank()) View.GONE else View.VISIBLE

            etTotalPages.setText(book.totalPages.toString())
            etReadPages.setText(book.readPages.toString())

            CoverLoader.load(ivCover, book.coverUrl)
        }
    }

    private fun saveProgress(total: Int, read: Int) {
        lifecycleScope.launch {
            if (currentBookId <= 0L) {
                // 新书还没入库：先入库再存进度
                val existingId = pendingIsbn?.let { db.bookDao().getByIsbn(it)?.id }
                if (existingId != null && existingId > 0L) {
                    onBookCollected(existingId, "这本书已经在书架里啦")
                } else {
                    val newId = db.bookDao().insert(
                        BookEntity(
                            isbn = pendingIsbn,
                            title = tvTitle.text.toString(),
                            author = tvAuthor.text.toString().takeIf { it.isNotBlank() },
                            publisher = tvPublisher.text.toString().takeIf { it.isNotBlank() },
                            coverUrl = intent.getStringExtra(EXTRA_COVER_URL),
                            totalPages = total,
                            readPages = read
                        )
                    )
                    if (newId == -1L) {
                        Toast.makeText(this@BookDetailActivity, "这本书已经在书架里啦", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    onBookCollected(newId, "已加入书架")
                }
            }

            val book = db.bookDao().getById(currentBookId)
            if (book != null) {
                db.bookDao().update(book.copy(totalPages = total, readPages = read))
                Toast.makeText(this@BookDetailActivity, "进度已保存", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== 标签 ====================

    private fun loadTagsForBook(bookId: Long) {
        lifecycleScope.launch {
            val allTags = db.tagDao().getAllOnce()
            val boundTagIds = db.bookDao().getBoundTagIds(bookId)

            chipGroupTags.removeAllViews()

            if (allTags.isEmpty()) {
                val hint = Chip(this@BookDetailActivity).apply {
                    text = "还没有标签，去「标签」页新建"
                    isClickable = false
                }
                chipGroupTags.addView(hint)
                return@launch
            }

            for (tag in allTags) {
                val chip = Chip(this@BookDetailActivity).apply {
                    text = tag.name
                    isCheckable = true
                    isChecked = boundTagIds.contains(tag.id)
                }

                chip.setOnCheckedChangeListener { _, isChecked ->
                    lifecycleScope.launch {
                        if (isChecked) {
                            db.bookDao().bindTag(BookTagCrossRef(bookId, tag.id))
                        } else {
                            db.bookDao().unbindTag(bookId, tag.id)
                        }
                    }
                }
                chipGroupTags.addView(chip)
            }
        }
    }

    // ==================== 进度条 ====================

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

    companion object {
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_ISBN = "isbn"
        const val EXTRA_TITLE = "title"
        const val EXTRA_AUTHOR = "author"
        const val EXTRA_PUBLISHER = "publisher"
        const val EXTRA_COVER_URL = "coverUrl"
        const val EXTRA_TOTAL_PAGES = "totalPages"
    }
}
