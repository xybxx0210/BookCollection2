package com.example.bookcollection.ui.bookshelf

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bookcollection.R
import com.example.bookcollection.data.local.AppDatabase
import com.example.bookcollection.ui.BookDetailActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BookShelfFragment : Fragment(R.layout.fragment_bookshelf) {

    private lateinit var adapter: BookAdapter
    private lateinit var db: AppDatabase
    private lateinit var chipGroupFilter: ChipGroup
    private var collectJob: Job? = null // 用于取消上一次的数据库监听

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getInstance(requireContext())

        val rvBooks = view.findViewById<RecyclerView>(R.id.rvBooks)
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter)

        adapter = BookAdapter()
        rvBooks.layoutManager = LinearLayoutManager(requireContext())
        rvBooks.adapter = adapter

        // 点击图书跳转详情页
        adapter.onItemClick = { book ->
            val intent = Intent(requireContext(), BookDetailActivity::class.java)
            intent.putExtra("bookId", book.id)
            startActivity(intent)
        }

        // 1. 加载所有标签到顶部筛选栏
        loadFilterTags()

        // 2. 默认显示全部图书
        showAllBooks()
    }

    // 从数据库读取标签，并生成 Chip 按钮
    private fun loadFilterTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            val tags = db.tagDao().getAllOnce()
            chipGroupFilter.removeAllViews()

            // 添加一个默认的“全部”按钮
            val chipAll = Chip(requireContext()).apply {
                text = "全部"
                isCheckable = true
                isChecked = true // 默认选中
            }
            chipAll.setOnClickListener { showAllBooks() }
            chipGroupFilter.addView(chipAll)

            // 遍历所有的标签
            for (tag in tags) {
                val chip = Chip(requireContext()).apply {
                    text = tag.name
                    isCheckable = true
                }
                chip.setOnClickListener {
                    showBooksByTag(tag.id)
                }
                chipGroupFilter.addView(chip)
            }
        }
    }

    // 显示全部图书
    private fun showAllBooks() {
        collectJob?.cancel() // 停止上一次的数据流
        collectJob = viewLifecycleOwner.lifecycleScope.launch {
            db.bookDao().observeAll().collect { books ->
                adapter.submitList(books)
            }
        }
    }

    // 显示特定标签的图书
    private fun showBooksByTag(tagId: Long) {
        collectJob?.cancel() // 停止上一次的数据流
        collectJob = viewLifecycleOwner.lifecycleScope.launch {
            db.bookDao().observeBooksByTag(tagId).collect { books ->
                adapter.submitList(books)
            }
        }
    }
}