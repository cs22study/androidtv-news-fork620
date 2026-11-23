package io.github.anenasa.news

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object YouTubeSearchHistory {
    private const val TAG = "YouTubeSearchHistory"
    private const val FILE_NAME = "youtube_search_history.csv"
    private const val MAX_HISTORY = 40

    val DEFAULT_PRESETS = listOf(
        "機場直播",
        "流行音樂",
        "穿越短劇",
        "公視新聞直播",
        "東森新聞",
        "TVBS新聞"
    )

    /**
     * 取得搜尋歷史清單 (最近使用的排在最前)
     */
    @Synchronized
    fun getHistory(context: Context): List<String> {
        val extFile = getPrimaryFile(context)
        val intFile = getBackupFile(context)

        val targetFile = when {
            extFile != null && extFile.exists() && extFile.length() > 0 -> extFile
            intFile.exists() && intFile.length() > 0 -> intFile
            else -> null
        }

        if (targetFile == null) {
            // 初始化預設值
            saveList(context, DEFAULT_PRESETS)
            return DEFAULT_PRESETS
        }

        val list = ArrayList<String>()
        try {
            targetFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                var isHeader = true
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    if (isHeader) {
                        isHeader = false
                        if (trimmed.startsWith("keyword", ignoreCase = true)) continue
                    }
                    val kw = parseCsvKeyword(trimmed)
                    if (kw.isNotEmpty() && !list.contains(kw)) {
                        list.add(kw)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Read search history csv error", e)
        }

        if (list.isEmpty()) {
            return DEFAULT_PRESETS
        }
        return list
    }

    /**
     * 新增搜尋關鍵字至歷史紀錄 (排在第一位，存入 CSV)
     */
    @Synchronized
    fun addKeyword(context: Context, keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return

        val currentList = ArrayList(getHistory(context))
        currentList.removeAll { it.equals(trimmed, ignoreCase = true) }
        currentList.add(0, trimmed)

        val trimmedList = if (currentList.size > MAX_HISTORY) currentList.subList(0, MAX_HISTORY) else currentList
        saveList(context, trimmedList)
    }

    /**
     * 清除歷史紀錄
     */
    @Synchronized
    fun clearHistory(context: Context) {
        saveList(context, emptyList())
    }

    private fun saveList(context: Context, list: List<String>) {
        val sb = StringBuilder()
        sb.append("keyword,timestamp\n")
        val now = System.currentTimeMillis()
        for (kw in list) {
            val escaped = kw.replace("\"", "\"\"")
            sb.append("\"").append(escaped).append("\",").append(now).append("\n")
        }
        val bytes = sb.toString().toByteArray(Charsets.UTF_8)

        // 1. 寫入外部儲存
        val extFile = getPrimaryFile(context)
        if (extFile != null) {
            try {
                FileOutputStream(extFile).use { it.write(bytes) }
            } catch (e: Exception) {
                Log.e(TAG, "Write primary csv error", e)
            }
        }

        // 2. 寫入內部沙盒備份
        val intFile = getBackupFile(context)
        try {
            FileOutputStream(intFile).use { it.write(bytes) }
        } catch (e: Exception) {
            Log.e(TAG, "Write backup csv error", e)
        }
    }

    private fun parseCsvKeyword(line: String): String {
        return if (line.startsWith("\"")) {
            val endIdx = line.indexOf("\",", 1)
            if (endIdx != -1) {
                line.substring(1, endIdx).replace("\"\"", "\"")
            } else {
                line.removeSurrounding("\"").replace("\"\"", "\"")
            }
        } else {
            line.substringBefore(",").trim()
        }
    }

    private fun getPrimaryFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: return null
        return File(dir, FILE_NAME)
    }

    private fun getBackupFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }
}
