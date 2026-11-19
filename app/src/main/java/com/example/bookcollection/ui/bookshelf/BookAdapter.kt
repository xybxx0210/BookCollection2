package com.example.bookcollection.ui.bookshelf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bookcollection.R
import com.example.bookcollection.data.local.entity.BookEntity

class BookAdapter : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    private var bookList = listOf<BookEntity>()

    var onItemClick: ((BookEntity) -> Unit)? = null
    // 👇 就是这一行漏了，必须要有它！
    var onItemLongClick: ((BookEntity) -> Unit)? = null

    fun submitList(newList: List<BookEntity>) {
        bookList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = bookList[position]
        holder.bind(book)

        // 点击事件
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(book)
        }

        // 长按事件
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(book)
            true
        }
    }

    override fun getItemCount(): Int = bookList.size

    class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.ivItemCover)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tvItemAuthor)

        fun bind(book: BookEntity) {
            tvTitle.text = book.title
            tvAuthor.text = book.author ?: "未知作者"

            if (!book.coverUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(book.coverUrl)
                    .placeholder(R.mipmap.ic_launcher)
                    .into(ivCover)
            } else {
                ivCover.setImageResource(R.mipmap.ic_launcher)
            }
        }
    }
}