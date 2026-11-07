package com.example.bookcollection

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.bookcollection.ui.bookshelf.BookShelfFragment
import com.example.bookcollection.ui.home.HomeFragment
import com.example.bookcollection.ui.scan.ScanFragment
import com.example.bookcollection.ui.tag.TagFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

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
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}