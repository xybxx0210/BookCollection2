package com.example.bookcollection

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.bookcollection.data.local.AppDatabase
import com.example.bookcollection.data.local.entity.BookEntity
import com.example.bookcollection.ui.bookshelf.BookShelfFragment
import com.example.bookcollection.ui.home.HomeFragment
import com.example.bookcollection.ui.scan.ScanFragment
import com.example.bookcollection.ui.tag.TagFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        if (savedInstanceState == null) {
            switchFragment(HomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchFragment(HomeFragment())
                R.id.nav_scan -> switchFragment(ScanFragment())
                R.id.nav_bookshelf -> switchFragment(BookShelfFragment())
                R.id.nav_tag -> switchFragment(TagFragment())
            }
            true
        }

        // =========== 第二步验收测试代码 ===========
        // 注意：这段代码现在在 onCreate 里面，是完全合法的
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(this@MainActivity)
            val bookDao = db.bookDao()

            // 1. 插入一条测试数据
            val testBook = BookEntity(
                title = "第一行代码",
                author = "郭霖",
                isbn = "9787115442000",
                totalPages = 500
            )
            val insertId = bookDao.insert(testBook)
            Log.d("DB_TEST", "插入成功！图书ID = $insertId")

            // 2. 查询所有数据
            val allBooks = bookDao.getAllOnce()
            Log.d("DB_TEST", "数据库中有 ${allBooks.size} 本书")
            for (book in allBooks) {
                Log.d("DB_TEST", "查到图书: 书名=${book.title}, 作者=${book.author}")
            }
        }
        // ==========================================
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}