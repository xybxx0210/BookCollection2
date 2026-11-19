package com.example.bookcollection.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bookcollection.R
import com.example.bookcollection.data.model.BookInfo
import com.example.bookcollection.ui.common.CoverLoader

/**
 * 联网搜索结果列表：点条目看详情，点「加入」直接丢进书架。
 */
class SearchResultAdapter : RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder>() {

    private var items: List<BookInfo> = emptyList()

    var onItemClick: ((BookInfo) -> Unit)? = null
    var onAddClick: ((BookInfo) -> Unit)? = null

    fun submitList(newItems: List<BookInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val book = items[position]
        holder.bind(book)

        holder.itemView.setOnClickListener { onItemClick?.invoke(book) }
        holder.btnAdd.setOnClickListener { onAddClick?.invoke(book) }
    }

    override fun getItemCount(): Int = items.size

    class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.ivResultCover)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvResultTitle)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tvResultAuthor)
        private val tvMeta: TextView = itemView.findViewById(R.id.tvResultMeta)
        val btnAdd: Button = itemView.findViewById(R.id.btnAddToShelf)

        fun bind(book: BookInfo) {
            tvTitle.text = book.title
            tvAuthor.text = book.author?.takeIf { it.isNotBlank() } ?: "未知作者"

            val metaParts = listOfNotNull(
                book.publisher?.takeIf { it.isNotBlank() },
                book.totalPages.takeIf { it > 0 }?.let { "$it 页" },
                book.year?.takeIf { it.isNotBlank() }
            )
            if (metaParts.isEmpty()) {
                tvMeta.visibility = View.GONE
            } else {
                tvMeta.visibility = View.VISIBLE
                tvMeta.text = metaParts.joinToString(" · ")
            }

            CoverLoader.load(ivCover, book.coverUrl)
        }
    }
}
