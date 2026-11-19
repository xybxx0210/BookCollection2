package com.example.bookcollection.ui.bookshelf

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private lateinit var etSearch: EditText
    private lateinit var tvEmptyState: TextView

    private var collectJob: Job? = null
    private var currentTagId: Long = -1L
    private var currentKeyword: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getInstance(requireContext())

        val rvBooks = view.findViewById<RecyclerView>(R.id.rvBooks)
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter)
        etSearch = view.findViewById(R.id.etSearch)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        adapter = BookAdapter()
        rvBooks.layoutManager = LinearLayoutManager(requireContext())
        rvBooks.adapter = adapter

        adapter.onItemClick = { book ->
            val intent = Intent(requireContext(), BookDetailActivity::class.java)
            intent.putExtra("bookId", book.id)
            startActivity(intent)
        }

        adapter.onItemLongClick = { book ->
            AlertDialog.Builder(requireContext())
                .setTitle("删除图书")
                .setMessage("确定要把《${book.title}》从书架中删除吗？")
                .setPositiveButton("删除") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        db.bookDao().delete(book)
                        Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentKeyword = s.toString().trim()
                loadData()
            }
        })

        loadFilterTags()
        loadData()
    }

    private fun loadFilterTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            val tags = db.tagDao().getAllOnce()
            chipGroupFilter.removeAllViews()

            val chipAll = Chip(requireContext()).apply {
                text = "全部"
                isCheckable = true
                isChecked = true
            }
            chipAll.setOnClickListener {
                currentTagId = -1L
                loadData()
            }
            chipGroupFilter.addView(chipAll)

            for (tag in tags) {
                val chip = Chip(requireContext()).apply {
                    text = tag.name
                    isCheckable = true
                }
                chip.setOnClickListener {
                    currentTagId = tag.id
                    loadData()
                }
                chipGroupFilter.addView(chip)
            }
        }
    }

    private fun loadData() {
        collectJob?.cancel()
        collectJob = viewLifecycleOwner.lifecycleScope.launch {
            val baseFlow = if (currentTagId == -1L) {
                db.bookDao().observeAll()
            } else {
                db.bookDao().observeBooksByTag(currentTagId)
            }

            baseFlow.collect { allBooks ->
                val filteredList = if (currentKeyword.isEmpty()) {
                    allBooks
                } else {
                    allBooks.filter {
                        it.title.contains(currentKeyword, ignoreCase = true) ||
                                (it.author?.contains(currentKeyword, ignoreCase = true) == true)
                    }
                }

                adapter.submitList(filteredList)

                if (filteredList.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                } else {
                    tvEmptyState.visibility = View.GONE
                }
            }
        }
    }
}