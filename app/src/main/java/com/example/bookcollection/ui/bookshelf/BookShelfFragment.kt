package com.example.bookcollection.ui.bookshelf

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bookcollection.R
import com.example.bookcollection.data.local.AppDatabase
import kotlinx.coroutines.launch

class BookShelfFragment : Fragment(R.layout.fragment_bookshelf) {

    private lateinit var adapter: BookAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvBooks = view.findViewById<RecyclerView>(R.id.rvBooks)
        adapter = BookAdapter()

        rvBooks.layoutManager = LinearLayoutManager(requireContext())
        rvBooks.adapter = adapter

        // 从数据库读取数据
        val db = AppDatabase.getInstance(requireContext())

        // 使用协程持续监听数据库变化
        viewLifecycleOwner.lifecycleScope.launch {
            db.bookDao().observeAll().collect { books ->
                // 当数据库数据改变时，自动刷新列表
                adapter.submitList(books)
            }
        }
    }
}