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
import kotlinx.coroutines.launch

class BookShelfFragment : Fragment(R.layout.fragment_bookshelf) {

    private lateinit var adapter: BookAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvBooks = view.findViewById<RecyclerView>(R.id.rvBooks)
        adapter = BookAdapter()

        rvBooks.layoutManager = LinearLayoutManager(requireContext())
        rvBooks.adapter = adapter

        // 👇 处理点击跳转
        adapter.onItemClick = { book ->
            val intent = Intent(requireContext(), BookDetailActivity::class.java)
            intent.putExtra("bookId", book.id) // 传数据库的 id 过去
            startActivity(intent)
        }

        val db = AppDatabase.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            db.bookDao().observeAll().collect { books ->
                adapter.submitList(books)
            }
        }
    }

    // 每次回到该页面时刷新（为了从详情页回来时能看到最新进度，虽然 Room 的 Flow 会自动刷新，这步是双保险）
    override fun onResume() {
        super.onResume()
    }
}