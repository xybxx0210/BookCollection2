package com.example.bookcollection.data.network

import com.example.bookcollection.data.model.BookInfo

/**
 * 豆瓣没有开放的 JSON 详情接口，详情页只是普通 HTML，
 * 这里用正则把「作者 / 出版社 / ISBN / 页数 / 封面」抠出来。
 *
 * 豆瓣详情页结构十几年没怎么变过，长这样：
 *   <div id="info">
 *     <span class="pl"> 作者</span>: <a href="...">刘慈欣</a><br/>
 *     <span class="pl">出版社:</span> <a href="...">重庆出版社</a><br/>
 *     <span class="pl">ISBN:</span> 9787536692930<br/>
 *     <span class="pl">页数:</span> 300<br/>
 *   </div>
 * 解析失败也不会崩，上层会退化成「只有书名/作者/封面」也能加入书架。
 */
object DoubanParser {

    private val TITLE_REGEX =
        Regex("""<span property="v:itemreviewed">([\s\S]*?)</span>""")

    private val COVER_REGEX =
        Regex("""<div id="mainpic">[\s\S]*?<img src="([^"]+)"""")

    private val INFO_BLOCK_REGEX =
        Regex("""<div id="info">([\s\S]*?)</div>""")

    private val LABEL_REGEX =
        Regex("""<span class="pl">\s*([^<]*?)\s*</span>\s*[:：]?""")

    private val TAG_REGEX = Regex("<[^>]+>")
    private val SPACE_REGEX = Regex("[\\s\\u00A0]+")
    private val DIGITS_REGEX = Regex("\\d+")

    /** 解析一本书的详情页；连标题都拿不到就返回 null */
    fun parseSubject(html: String): BookInfo? {
        val title = TITLE_REGEX.find(html)?.groupValues?.get(1)
            ?.let { unescape(it).trim() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val info = parseInfoBlock(html)

        val cover = COVER_REGEX.find(html)?.groupValues?.get(1)
            ?.let { upgradeCover(unescape(it)) }

        val author = info["作者"] ?: info["译者"]
        val pages = info["页数"]?.let { DIGITS_REGEX.find(it)?.value?.toIntOrNull() } ?: 0

        return BookInfo(
            title = title,
            author = author?.substringBefore(" /")?.trim()?.takeIf { it.isNotEmpty() },
            publisher = info["出版社"]?.takeIf { it.isNotEmpty() },
            isbn = info["ISBN"]?.replace("-", "")?.trim()?.takeIf { it.isNotEmpty() },
            coverUrl = cover,
            totalPages = pages,
            year = info["出版年"] ?: info["出版时间"],
            source = "豆瓣读书"
        )
    }

    /**
     * 把 <div id="info"> 里的一堆标签压成 "键⇒值" 的纯文本，再逐行取值。
     *
     * 注意：豆瓣的「键」和「值」经常不在同一行，比如
     *   <span class="pl">出版社:</span>
     *     <a href="...">重庆出版社</a>
     * 所以这里要记住上一个键，值在下一行出现时也能接上。
     */
    private fun parseInfoBlock(html: String): Map<String, String> {
        val block = INFO_BLOCK_REGEX.find(html)?.groupValues?.get(1) ?: return emptyMap()

        val withLabels = LABEL_REGEX.replace(block) { match ->
            "\n${match.groupValues[1]}⇒"
        }
        val plain = unescape(TAG_REGEX.replace(withLabels, ""))

        val result = mutableMapOf<String, String>()
        var currentKey: String? = null

        plain.split("\n").forEach { rawLine ->
            val line = SPACE_REGEX.replace(rawLine, " ").trim()
            if (line.isEmpty()) return@forEach

            val marker = line.indexOf('⇒')
            if (marker >= 0) {
                // 豆瓣有的标签把冒号写在 <span> 里面（"出版社:"），有的是写在后面（"作者"），统一去掉
                val key = line.substring(0, marker).trim()
                    .removeSuffix(":").removeSuffix("：").trim()
                if (key.isEmpty()) return@forEach
                currentKey = key
                val value = line.substring(marker + 1).trim().removePrefix(":").removePrefix("：").trim()
                if (value.isNotEmpty()) result.putIfAbsent(key, value)
            } else {
                val key = currentKey ?: return@forEach
                result.putIfAbsent(key, line.removePrefix(":").removePrefix("：").trim())
            }
        }
        return result
    }

    /** 豆瓣小图 /view/subject/s/public/xxx.jpg，换成大图更清晰 */
    private fun upgradeCover(url: String): String {
        var result = url.replace("http://", "https://")
        result = result
            .replace("/view/subject/s/public/", "/view/subject/l/public/")
            .replace("/view/subject/m/public/", "/view/subject/l/public/")
        return result
    }

    private fun unescape(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}
