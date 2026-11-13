package com.example.bookcollection.ui.tag

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bookcollection.R
import com.example.bookcollection.data.local.entity.TagEntity

class TagAdapter : RecyclerView.Adapter<TagAdapter.TagViewHolder>() {

    private var tagList = listOf<TagEntity>()

    // 点击删除的回调
    var onDeleteClick: ((TagEntity) -> Unit)? = null

    fun submitList(newList: List<TagEntity>) {
        tagList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tag, parent, false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        val tag = tagList[position]
        holder.bind(tag)
    }

    override fun getItemCount(): Int = tagList.size

    inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTagName: TextView = itemView.findViewById(R.id.tvTagName)
        private val btnDeleteTag: Button = itemView.findViewById(R.id.btnDeleteTag)

        fun bind(tag: TagEntity) {
            tvTagName.text = tag.name

            btnDeleteTag.setOnClickListener {
                onDeleteClick?.invoke(tag)
            }
        }
    }
}