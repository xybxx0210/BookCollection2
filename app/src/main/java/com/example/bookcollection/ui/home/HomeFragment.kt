package com.example.bookcollection.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bookcollection.R
import com.example.bookcollection.data.local.AppDatabase
import com.example.bookcollection.data.local.entity.BookEntity
import com.example.bookcollection.data.model.BookInfo
import com.example.bookcollection.data.repository.BookRepository
import com.example.bookcollection.data.repository.SearchOutcome
import com.example.bookcollection.ui.BookDetailActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 联网搜书页：输入关键词 → 真正请求网络 → 列表展示 → 点开详情 / 一键加入书架。
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val repository by lazy { BookRepository() }
    private lateinit var db: AppDatabase
    private lateinit var adapter: SearchResultAdapter
    private lateinit var progressSearch: ProgressBar
    private lateinit var tvSearchResult: TextView

    private var searchJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getInstance(requireContext())

        val etSearchKeyword = view.findViewById<EditText>(R.id.etSearchKeyword)
        val btnSearchApi = view.findViewById<Button>(R.id.btnSearchApi)
        val rvSearchResults = view.findViewById<RecyclerView>(R.id.rvSearchResults)
        progressSearch = view.findViewById(R.id.progressSearch)
        tvSearchResult = view.findViewById(R.id.tvSearchResult)

        adapter = SearchResultAdapter()
        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        rvSearchResults.adapter = adapter

        adapter.onItemClick = { book -> openDetail(book) }
        adapter.onAddClick = { book -> addToShelf(book) }

        btnSearchApi.setOnClickListener { startSearch(etSearchKeyword.text.toString()) }

        etSearchKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startSearch(etSearchKeyword.text.toString())
                true
            } else {
                false
            }
        }
    }

    // ==================== 搜索 ====================

    private fun startSearch(rawKeyword: String) {
        val keyword = rawKeyword.trim()
        if (keyword.isEmpty()) {
            toast("请输入书名或作者")
            return
        }
        hideKeyboard()

        searchJob?.cancel()
        adapter.submitList(emptyList())
        showLoading("正在全网搜索《$keyword》…")

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            when (val outcome = repository.search(keyword)) {
                is SearchOutcome.Success -> {
                    hideLoading()
                    adapter.submitList(outcome.books)
                    tvSearchResult.text = if (outcome.books.isEmpty()) {
                        "没有找到与《$keyword》相关的图书，换个关键词试试"
                    } else {
                        "找到 ${outcome.books.size} 本（来源：${outcome.source}）\n点条目看详情，点「加入」直接收藏"
                    }
                }

                is SearchOutcome.Failure -> {
                    hideLoading()
                    tvSearchResult.text = outcome.message
                    toast(outcome.message)
                }
            }
        }
    }

    // ==================== 打开详情 ====================

    private fun openDetail(book: BookInfo) {
        // 搜索结果里只有书名/作者/封面，这里补一次出版社和页数
        showLoading("正在获取《${book.title}》的详细信息…")

        viewLifecycleOwner.lifecycleScope.launch {
            val detail = repository.loadDetail(book)
            hideLoading()

            if (!isAdded) return@launch

            val intent = Intent(requireContext(), BookDetailActivity::class.java).apply {
                putExtra(BookDetailActivity.EXTRA_ISBN, detail.isbn)
                putExtra(BookDetailActivity.EXTRA_TITLE, detail.title)
                putExtra(BookDetailActivity.EXTRA_AUTHOR, detail.author)
                putExtra(BookDetailActivity.EXTRA_PUBLISHER, detail.publisher)
                putExtra(BookDetailActivity.EXTRA_COVER_URL, detail.coverUrl)
                putExtra(BookDetailActivity.EXTRA_TOTAL_PAGES, detail.totalPages)
            }
            startActivity(intent)
        }
    }

    // ==================== 加入书架 ====================

    private fun addToShelf(book: BookInfo) {
        showLoading("正在把《${book.title}》加入书架…")

        viewLifecycleOwner.lifecycleScope.launch {
            val detail = repository.loadDetail(book)
            hideLoading()

            val isbn = detail.isbn?.trim()?.takeIf { it.isNotEmpty() }
            if (isbn != null && db.bookDao().getByIsbn(isbn) != null) {
                toast("《${detail.title}》已经在书架里啦")
                return@launch
            }

            val id = db.bookDao().insert(
                BookEntity(
                    isbn = isbn,
                    title = detail.title,
                    author = detail.author,
                    publisher = detail.publisher,
                    coverUrl = detail.coverUrl,
                    totalPages = detail.totalPages,
                    readPages = 0
                )
            )

            toast(
                if (id == -1L) "这本书已经在书架里啦"
                else "已把《${detail.title}》加入书架"
            )
        }
    }

    // ==================== 小工具 ====================

    private fun showLoading(message: String) {
        progressSearch.visibility = View.VISIBLE
        tvSearchResult.text = message
    }

    private fun hideLoading() {
        progressSearch.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    private fun toast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
