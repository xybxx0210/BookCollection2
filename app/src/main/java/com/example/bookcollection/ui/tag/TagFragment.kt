package com.example.bookcollection.ui.tag

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bookcollection.R
import com.example.bookcollection.data.local.AppDatabase
import com.example.bookcollection.data.local.entity.TagEntity
import kotlinx.coroutines.launch

class TagFragment : Fragment(R.layout.fragment_tag) {

    private lateinit var adapter: TagAdapter
    private lateinit var db: AppDatabase

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getInstance(requireContext())

        val etTagName = view.findViewById<EditText>(R.id.etTagName)
        val btnAddTag = view.findViewById<Button>(R.id.btnAddTag)
        val rvTags = view.findViewById<RecyclerView>(R.id.rvTags)

        adapter = TagAdapter()
        rvTags.layoutManager = LinearLayoutManager(requireContext())
        rvTags.adapter = adapter

        // 1. 监听数据库，实时刷新列表
        viewLifecycleOwner.lifecycleScope.launch {
            db.tagDao().observeAll().collect { tags ->
                adapter.submitList(tags)
            }
        }

        // 2. 添加标签逻辑
        btnAddTag.setOnClickListener {
            val tagName = etTagName.text.toString().trim()
            if (tagName.isEmpty()) {
                Toast.makeText(requireContext(), "标签名不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                // 因为我们在 TagEntity 里设置了唯一索引，重复添加会返回 -1
                val result = db.tagDao().insert(TagEntity(name = tagName))
                if (result != -1L) {
                    Toast.makeText(requireContext(), "添加成功", Toast.LENGTH_SHORT).show()
                    etTagName.text.clear()
                } else {
                    Toast.makeText(requireContext(), "该标签已存在", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 3. 删除标签逻辑
        adapter.onDeleteClick = { tag ->
            viewLifecycleOwner.lifecycleScope.launch {
                db.tagDao().delete(tag)
                Toast.makeText(requireContext(), "已删除标签: ${tag.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}