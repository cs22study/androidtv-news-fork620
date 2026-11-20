package io.github.anenasa.news

import android.util.Log
import com.chaquo.python.Python
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class YouTubeSearchResult(
    val title: String,
    val url: String,
    val uploader: String,
    val isLive: Boolean
)

/**
 * YouTube 關鍵字搜尋引擎 (免 API 金鑰、零費用)
 * 策略一：透過官方 Web 檢索端點即時解析 ytInitialData，速度在 1 秒以內完成。
 * 策略二：若遇網路防禦或結構變更，自動平滑降級調用本地 Chaquopy 內建之 yt-dlp flat-search。
 */
object YouTubeSearcher {
    private const val TAG = "YouTubeSearcher"

    fun search(query: String, maxResults: Int = 8): List<YouTubeSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = ArrayList<YouTubeSearchResult>()

        // 策略一：以 OkHttp 直接抓取 YouTube 搜尋頁 (超高速、零配額消耗)
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val request = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$encodedQuery")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            MyApplication.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body.string()
                    val jsonStr = extractYtInitialData(html)
                    if (!jsonStr.isNullOrEmpty()) {
                        val parsed = parseYtInitialData(jsonStr, maxResults)
                        if (parsed.isNotEmpty()) {
                            return parsed
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct YouTube scraping failed, falling back to yt-dlp", e)
        }

        // 策略二：使用 Chaquopy yt-dlp 快速平坦檢索 (Flat extraction)
        try {
            val py = Python.getInstance()
            val ytdlpModule = py.getModule("yt_dlp")
            val opts = py.builtins.callAttr("dict")
            opts.callAttr("__setitem__", "extract_flat", "in_playlist")
            opts.callAttr("__setitem__", "skip_download", true)
            opts.callAttr("__setitem__", "quiet", true)
            opts.callAttr("__setitem__", "no_warnings", true)

            val ydl = ytdlpModule.callAttr("YoutubeDL", opts)
            val searchQuery = "ytsearch$maxResults:$trimmed"
            val info = ydl.callAttr("extract_info", searchQuery, false)
            val entries = info.callAttr("get", "entries")
            if (entries != null) {
                val listSize = entries.callAttr("__len__").toInt()
                for (i in 0 until listSize) {
                    val item = entries.callAttr("__getitem__", i)
                    val title = item.callAttr("get", "title")?.toString().orEmpty()
                    val videoId = item.callAttr("get", "id")?.toString().orEmpty()
                    val webpageUrl = item.callAttr("get", "url")?.toString()
                        ?: "https://www.youtube.com/watch?v=$videoId"
                    val uploader = item.callAttr("get", "uploader")?.toString().orEmpty()
                    val isLive = item.callAttr("get", "is_live")?.toBoolean() ?: false

                    if (title.isNotEmpty() && videoId.isNotEmpty()) {
                        results.add(
                            YouTubeSearchResult(
                                title = title,
                                url = if (webpageUrl.startsWith("http")) webpageUrl else "https://www.youtube.com/watch?v=$videoId",
                                uploader = uploader,
                                isLive = isLive
                            )
                        )
                        if (results.size >= maxResults) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp flat search failed", e)
        }

        return results
    }

    private fun extractYtInitialData(html: String): String? {
        val marker1 = "var ytInitialData = "
        val idx1 = html.indexOf(marker1)
        if (idx1 != -1) {
            val start = idx1 + marker1.length
            val end = html.indexOf(";</script>", start)
            if (end != -1) {
                return html.substring(start, end)
            }
        }

        val marker2 = "ytInitialData = "
        val idx2 = html.indexOf(marker2)
        if (idx2 != -1) {
            val start = idx2 + marker2.length
            val end = html.indexOf(";</script>", start)
            if (end != -1) {
                return html.substring(start, end)
            }
        }
        return null
    }

    private fun parseYtInitialData(jsonString: String, maxResults: Int): List<YouTubeSearchResult> {
        val list = ArrayList<YouTubeSearchResult>()
        try {
            val root = JSONObject(jsonString)
            val primaryContents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return list

            for (c in 0 until primaryContents.length()) {
                val itemSection = primaryContents.optJSONObject(c)?.optJSONObject("itemSectionRenderer") ?: continue
                val items = itemSection.optJSONArray("contents") ?: continue
                for (i in 0 until items.length()) {
                    val vr = items.optJSONObject(i)?.optJSONObject("videoRenderer") ?: continue
                    val videoId = vr.optString("videoId")
                    if (videoId.isEmpty()) continue

                    // 取得標題
                    val titleObj = vr.optJSONObject("title")
                    val titleRuns = titleObj?.optJSONArray("runs")
                    val title = if (titleRuns != null && titleRuns.length() > 0) {
                        val sb = StringBuilder()
                        for (r in 0 until titleRuns.length()) {
                            sb.append(titleRuns.optJSONObject(r)?.optString("text").orEmpty())
                        }
                        sb.toString()
                    } else {
                        titleObj?.optString("simpleText").orEmpty()
                    }

                    // 取得上傳者 / 頻道名稱
                    val ownerObj = vr.optJSONObject("ownerText")
                    val ownerRuns = ownerObj?.optJSONArray("runs")
                    val uploader = ownerRuns?.optJSONObject(0)?.optString("text").orEmpty()

                    // 判斷是否為進行中之網路直播
                    var isLive = false
                    val badges = vr.optJSONArray("badges")
                    if (badges != null) {
                        for (b in 0 until badges.length()) {
                            val badgeRenderer = badges.optJSONObject(b)?.optJSONObject("metadataBadgeRenderer")
                            val style = badgeRenderer?.optString("style").orEmpty()
                            val label = badgeRenderer?.optString("label").orEmpty()
                            if (style.contains("LIVE") || label.contains("直播")) {
                                isLive = true
                                break
                            }
                        }
                    }

                    if (title.isNotEmpty()) {
                        list.add(
                            YouTubeSearchResult(
                                title = title,
                                url = "https://www.youtube.com/watch?v=$videoId",
                                uploader = uploader,
                                isLive = isLive
                            )
                        )
                        if (list.size >= maxResults) return list
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseYtInitialData error", e)
        }
        return list
    }
}
