package com.example.bookcollection.ui.common

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.example.bookcollection.R
import com.example.bookcollection.data.network.NetworkModule

/**
 * 统一加载图书封面。
 *
 * 豆瓣的图片服务器有防盗链：不带 Referer 会直接返回 418，
 * 表现就是列表里全是默认图标。所以这里给 Glide 的请求加上
 * 和网页一致的 Referer / User-Agent。
 */
object CoverLoader {

    private val headers: LazyHeaders by lazy {
        LazyHeaders.Builder()
            .addHeader("User-Agent", NetworkModule.BROWSER_UA)
            .addHeader("Referer", "https://book.douban.com/")
            .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .build()
    }

    fun load(imageView: ImageView, coverUrl: String?) {
        if (coverUrl.isNullOrBlank()) {
            imageView.setImageResource(R.mipmap.ic_launcher)
            return
        }

        Glide.with(imageView)
            .load(GlideUrl(coverUrl, headers))
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .into(imageView)
    }
}
