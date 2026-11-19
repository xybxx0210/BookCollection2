package com.example.bookcollection.data.repository

import android.util.Log
import com.example.bookcollection.data.model.BookInfo
import com.example.bookcollection.data.network.BookItem
import com.example.bookcollection.data.network.DoubanApi
import com.example.bookcollection.data.network.DoubanParser
import com.example.bookcollection.data.network.DoubanSuggestDto
import com.example.bookcollection.data.network.GoogleBooksApi
import com.example.bookcollection.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 搜索结果：成功（可能为空列表）或失败（带人话提示） */
sealed interface SearchOutcome {
    data class Success(val books: List<BookInfo>, val source: String) : SearchOutcome
    data class Failure(val message: String) : SearchOutcome
}

/** 单本书查询结果 */
sealed interface BookLookupResult {
    data class Success(val book: BookInfo) : BookLookupResult
    data class NotFound(val message: String) : BookLookupResult
    data class Failure(val message: String) : BookLookupResult
}

/**
 * 联网搜书的唯一入口：先问豆瓣，再问 Google Books，都失败才报错。
 *
 * - search()       按书名 / 作者搜索，返回列表
 * - lookupByIsbn() 扫码后按 ISBN 查一本
 * - loadDetail()   点开某一本时，再抓详情页补出版社 / 页数 / ISBN
 */
class BookRepository(
    private val doubanApi: DoubanApi = NetworkModule.doubanApi,
    private val googleApi: GoogleBooksApi = NetworkModule.googleBooksApi,
    private val client: OkHttpClient = NetworkModule.okHttpClient
) {

    suspend fun search(keyword: String): SearchOutcome = withContext(Dispatchers.IO) {
        val query = keyword.trim()
        if (query.isEmpty()) {
            return@withContext SearchOutcome.Failure("请输入书名或作者")
        }

        var doubanOk = false
        var googleOk = false
        var lastError: Exception? = null

        // 1) 豆瓣读书：国内直连，中文书数据最全
        try {
            val books = doubanApi.suggest(query)
                // 豆瓣的联想搜索偶尔会混进电影/音乐，这里只要图书（type = b）
                .filter { it.type == null || it.type == "b" }
                .mapNotNull { it.toBookInfo() }
                .distinctBy { it.title to it.author }
                .take(MAX_RESULTS)
            doubanOk = true
            if (books.isNotEmpty()) {
                return@withContext SearchOutcome.Success(books, "豆瓣读书")
            }
        } catch (e: Exception) {
            lastError = e
            Log.w(TAG, "豆瓣搜索失败", e)
        }

        // 2) Google Books：需要能访问 Google
        try {
            val books = googleApi.searchBooks(query, MAX_RESULTS, "books")
                .items.orEmpty()
                .mapNotNull { it.toBookInfo() }
                .distinctBy { it.title to it.author }
                .take(MAX_RESULTS)
            googleOk = true
            if (books.isNotEmpty()) {
                return@withContext SearchOutcome.Success(books, "Google Books")
            }
        } catch (e: Exception) {
            lastError = e
            Log.w(TAG, "Google Books 搜索失败", e)
        }

        if (doubanOk || googleOk) {
            SearchOutcome.Success(emptyList(), if (doubanOk) "豆瓣读书" else "Google Books")
        } else {
            SearchOutcome.Failure(friendlyMessage(lastError))
        }
    }

    /** 扫码得到 ISBN 后调用；依次尝试豆瓣 ISBN 页 → Google Books → 豆瓣关键字 */
    suspend fun lookupByIsbn(rawIsbn: String): BookLookupResult = withContext(Dispatchers.IO) {
        val isbn = rawIsbn.replace("-", "").replace(" ", "").trim()
        if (isbn.isEmpty()) {
            return@withContext BookLookupResult.NotFound("ISBN 为空，无法查询")
        }

        var lastError: Exception? = null

        // 1) 豆瓣的 /isbn/xxx/ 会 302 跳到详情页，信息最全
        try {
            val html = fetchHtml("https://book.douban.com/isbn/$isbn/")
            val parsed = DoubanParser.parseSubject(html)
            if (parsed != null) {
                return@withContext BookLookupResult.Success(parsed.copy(isbn = isbn))
            }
        } catch (e: Exception) {
            lastError = e
            Log.w(TAG, "豆瓣 ISBN 查询失败", e)
        }

        // 2) Google Books
        try {
            val book = googleApi.searchBooks("isbn:$isbn", 5, "books")
                .items.orEmpty()
                .mapNotNull { it.toBookInfo() }
                .firstOrNull()
            if (book != null) {
                return@withContext BookLookupResult.Success(book.copy(isbn = book.isbn ?: isbn))
            }
        } catch (e: Exception) {
            lastError = e
            Log.w(TAG, "Google Books ISBN 查询失败", e)
        }

        // 3) 豆瓣联想搜索兜底（个别书没建 ISBN 页）
        try {
            val book = doubanApi.suggest(isbn).firstOrNull()?.toBookInfo()
            if (book != null) {
                return@withContext BookLookupResult.Success(book.copy(isbn = isbn))
            }
        } catch (e: Exception) {
            lastError = e
            Log.w(TAG, "豆瓣 ISBN 兜底搜索失败", e)
        }

        if (lastError != null && lastError is IOException) {
            BookLookupResult.Failure(friendlyMessage(lastError))
        } else {
            BookLookupResult.NotFound("没有查到 ISBN 为 $isbn 的图书")
        }
    }

    /**
     * 搜索结果里只有书名 / 作者 / 封面，点开时再补出版社、页数、ISBN。
     * 抓不到也不报错，返回原数据即可（详情页里还能手动填）。
     */
    suspend fun loadDetail(book: BookInfo): BookInfo = withContext(Dispatchers.IO) {
        val needMore = book.publisher.isNullOrBlank() || book.totalPages <= 0 || book.isbn.isNullOrBlank()
        if (!needMore) {
            return@withContext book
        }

        var result = book

        val doubanId = book.doubanId
        if (!doubanId.isNullOrBlank()) {
            try {
                val detail = DoubanParser.parseSubject(fetchHtml("https://book.douban.com/subject/$doubanId/"))
                if (detail != null) {
                    result = merge(result, detail)
                }
            } catch (e: Exception) {
                Log.w(TAG, "抓取豆瓣详情失败", e)
            }
        }

        val isbn = result.isbn?.takeIf { it.isNotBlank() }
        if (isbn != null && (result.publisher.isNullOrBlank() || result.totalPages <= 0)) {
            try {
                val detail = DoubanParser.parseSubject(fetchHtml("https://book.douban.com/isbn/$isbn/"))
                if (detail != null) {
                    result = merge(result, detail)
                }
            } catch (e: Exception) {
                Log.w(TAG, "抓取豆瓣 ISBN 详情失败", e)
            }
        }

        result
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://book.douban.com/")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    private fun merge(base: BookInfo, detail: BookInfo): BookInfo = base.copy(
        title = detail.title.ifBlank { base.title },
        author = detail.author ?: base.author,
        publisher = detail.publisher ?: base.publisher,
        isbn = detail.isbn ?: base.isbn,
        coverUrl = detail.coverUrl ?: base.coverUrl,
        totalPages = if (detail.totalPages > 0) detail.totalPages else base.totalPages,
        year = detail.year ?: base.year
    )

    private fun friendlyMessage(error: Exception?): String = when {
        error == null -> "搜索失败，请稍后再试"
        error is UnknownHostException -> "连不上服务器，请检查手机是否联网"
        error is SocketTimeoutException -> "网络超时，请检查网络后重试"
        error is InterruptedIOException -> "网络超时，请检查网络后重试"
        error is SSLException -> "网络证书异常，请关掉 VPN / 代理后再试"
        error is HttpException && error.code() == 403 -> "豆瓣暂时拒绝了请求（403），过一会儿再试"
        error is HttpException -> "服务器返回 ${error.code()}，请稍后再试"
        error is ConnectException -> "连不上服务器，请检查网络"
        error is IOException -> "网络异常：${error.message ?: "连接失败"}"
        else -> "搜索失败：${error.message ?: error.javaClass.simpleName}"
    }

    companion object {
        private const val TAG = "BookRepository"
        private const val MAX_RESULTS = 20
    }
}

// ==================== 各家 JSON → BookInfo ====================

private fun DoubanSuggestDto.toBookInfo(): BookInfo? {
    val name = title?.trim().orEmpty()
    if (name.isEmpty()) return null

    return BookInfo(
        title = name,
        author = authorName?.trim()?.takeIf { it.isNotEmpty() },
        coverUrl = pic?.let { upgradeCover(it) },
        year = year,
        doubanId = id,
        source = "豆瓣读书"
    )
}

private fun BookItem.toBookInfo(): BookInfo? {
    val info = volumeInfo ?: return null
    val name = info.title?.trim().orEmpty()
    if (name.isEmpty()) return null

    val isbn = info.industryIdentifiers
        ?.firstOrNull { it.type == "ISBN_13" }?.identifier
        ?: info.industryIdentifiers?.firstOrNull()?.identifier

    return BookInfo(
        title = name,
        author = info.authors?.joinToString(" / ")?.takeIf { it.isNotBlank() },
        publisher = info.publisher?.takeIf { it.isNotBlank() },
        isbn = isbn,
        coverUrl = info.imageLinks?.thumbnail?.let { upgradeCover(it) }
            ?: info.imageLinks?.smallThumbnail?.let { upgradeCover(it) },
        totalPages = info.pageCount ?: 0,
        source = "Google Books"
    )
}

/** 豆瓣封面小图 /view/subject/s/public/xxx.jpg → 大图；顺便把 http 换成 https */
private fun upgradeCover(url: String): String = url
    .replace("http://", "https://")
    .replace("/view/subject/s/public/", "/view/subject/l/public/")
    .replace("/view/subject/m/public/", "/view/subject/l/public/")
