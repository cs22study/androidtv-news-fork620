package io.github.anenasa.news

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.Collections

/**
 * 區網 TVBox 遠端管理後台 HTTP 伺服器 (Local Web Server)
 * 允許使用者在同一個 Wi-Fi 網域的手機或電腦瀏覽器：
 * 1. 上傳、編輯、管理頻道清單 (config.txt)
 * 2. 匯入、儲存 Cookies (cookies.txt)
 * 3. 快速新增頻道與即時跳台
 * 4. 虛擬手機遙控器操作
 */
object LocalWebServer {
    private const val TAG = "LocalWebServer"

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var activePort: Int = 8080
        private set

    @Volatile
    var localIp: String = "127.0.0.1"
        private set

    val serverUrl: String
        get() = "http://$localIp:$activePort"

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var webListener: WebListener? = null
    private var appContext: Context? = null

    interface WebListener {
        fun onConfigUpdated()
        fun onCookiesUpdated()
        fun onPlayChannel(index: Int)
        fun onRemoteKey(keyCode: Int)
        fun onChannelAdd(
            name: String,
            url: String,
            format: String,
            volume: String,
            header: String,
            isWebView: Boolean
        )
        fun onToggleHidden(index: Int)
        fun getChannelList(): List<Channel>
        fun getCurrentChannelIndex(): Int
    }

    /**
     * 啟動 HTTP 伺服器
     */
    @Synchronized
    fun start(context: Context, defaultPort: Int = 8080, listener: WebListener): Boolean {
        if (isRunning) return true
        appContext = context.applicationContext
        webListener = listener
        localIp = getLocalIpAddress()

        val candidatePorts = listOf(defaultPort, 8888, 9978, 5000, 0)
        for (port in candidatePorts) {
            try {
                serverSocket = ServerSocket(port)
                activePort = serverSocket!!.localPort
                break
            } catch (e: Exception) {
                Log.w(TAG, "Port $port occupied, trying next...")
            }
        }

        if (serverSocket == null) {
            Log.e(TAG, "Failed to bind to any port")
            return false
        }

        isRunning = true
        Log.i(TAG, "LocalWebServer started at $serverUrl")

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                try {
                    val clientSocket = serverSocket!!.accept()
                    launch { handleClient(clientSocket) }
                } catch (e: Exception) {
                    if (!isRunning) break
                    Log.e(TAG, "ServerSocket accept exception", e)
                }
            }
        }
        return true
    }

    /**
     * 停止 HTTP 伺服器
     */
    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing serverSocket", e)
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        Log.i(TAG, "LocalWebServer stopped")
    }

    /**
     * 取得本機 Wi-Fi / 乙太網路 IPv4 位址
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // 優先選擇 192.168 / 10. / 172. 網段之私人 IP
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
            // 次要選擇任何非 loopback IPv4
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve local IP address", e)
        }
        return "127.0.0.1"
    }

    /**
     * 檢查是否為私有網域或本機連線 (資安防護：防禦 WAN 外部連線)
     */
    private fun isPrivateOrLoopbackIp(ip: String): Boolean {
        if (ip == "127.0.0.1" || ip == "localhost" || ip == "::1") return true
        if (ip.startsWith("192.168.") || ip.startsWith("10.")) return true
        if (ip.startsWith("172.")) {
            val second = ip.split('.').getOrNull(1)?.toIntOrNull() ?: 0
            if (second in 16..31) return true
        }
        return false
    }

    /**
     * 處理單一 HTTP Client 連線
     */
    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 10000 // 10 秒超時保護
            val remoteAddr = (s.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: ""
            if (!isPrivateOrLoopbackIp(remoteAddr)) {
                sendResponse(s, 403, "text/plain", "Forbidden: Local LAN access only".toByteArray())
                return
            }

            val input = BufferedInputStream(s.getInputStream())
            val output = s.getOutputStream()

            val requestLine = readLine(input)
            if (requestLine.isNullOrEmpty()) return

            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawPath = parts[1]

            val path = rawPath.substringBefore("?")
            val queryString = if (rawPath.contains("?")) rawPath.substringAfter("?") else ""
            val queryParams = parseQueryParams(queryString)

            // 讀取 HTTP Headers
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val split = line.indexOf(':')
                if (split != -1) {
                    headers[line.substring(0, split).trim().lowercase()] = line.substring(split + 1).trim()
                }
            }

            // 讀取 Body (限制最大 5MB 避免記憶體溢位)
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyBytes = if (contentLength in 1..5242880) {
                val buf = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = input.read(buf, read, contentLength - read)
                    if (count == -1) break
                    read += count
                }
                buf
            } else ByteArray(0)
            val bodyString = String(bodyBytes, StandardCharsets.UTF_8)

            dispatchRoute(s, method, path, queryParams, bodyString)
        }
    }

    /**
     * 路由分派
     */
    private fun dispatchRoute(
        socket: Socket,
        method: String,
        path: String,
        params: Map<String, String>,
        body: String
    ) {
        try {
            when {
                // 首頁 Dashboard HTML
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    sendResponse(socket, 200, "text/html; charset=utf-8", getDashboardHtml().toByteArray(StandardCharsets.UTF_8))
                }

                // 系統狀態 API
                method == "GET" && path == "/api/status" -> {
                    val channelCount = webListener?.getChannelList()?.size ?: 0
                    val currentIdx = webListener?.getCurrentChannelIndex() ?: 0
                    val json = JSONObject().apply {
                        put("status", "ok")
                        put("ip", localIp)
                        put("port", activePort)
                        put("version", "6.2.0")
                        put("channelCount", channelCount)
                        put("currentChannelIndex", currentIdx)
                    }
                    sendResponse(socket, 200, "application/json; charset=utf-8", json.toString().toByteArray())
                }

                // 頻道清單列表 API
                method == "GET" && path == "/api/channels" -> {
                    val channels = webListener?.getChannelList() ?: emptyList()
                    val array = JSONArray()
                    channels.forEachIndexed { index, ch ->
                        val obj = JSONObject().apply {
                            put("index", index)
                            put("name", ch.name)
                            put("url", ch.url)
                            put("format", ch.format)
                            put("volume", ch.volume)
                            put("header", ch.header)
                            put("isHidden", ch.isHidden)
                            put("isWebView", ch.isWebView)
                        }
                        array.put(obj)
                    }
                    sendResponse(socket, 200, "application/json; charset=utf-8", array.toString().toByteArray())
                }

                // 取得 config.txt 原始內容
                method == "GET" && path == "/api/config" -> {
                    val configFile = File(appContext?.getExternalFilesDir(null), "config.txt")
                    val content = if (configFile.exists()) configFile.readText() else """{"channelList":[]}"""
                    sendResponse(socket, 200, "application/json; charset=utf-8", content.toByteArray())
                }

                // 上傳 / 更新 config.txt
                method == "POST" && path == "/api/config" -> {
                    if (body.isBlank()) {
                        sendError(socket, 400, "設定內容不可為空")
                        return
                    }
                    try {
                        // 驗證是否符合 JSON 格式
                        val parsed = JSONObject(body)
                        if (!parsed.has("channelList")) {
                            sendError(socket, 400, "無效的頻道清單：缺少 channelList 欄位")
                            return
                        }
                    } catch (e: Exception) {
                        sendError(socket, 400, "JSON 語法錯誤：${e.message}")
                        return
                    }

                    val dir = appContext?.getExternalFilesDir(null)
                    val configFile = File(dir, "config.txt")
                    val backupFile = File(dir, "config.txt.bak")

                    // 備份機制 (符合原則 3：隨時可復原)
                    if (configFile.exists()) {
                        configFile.copyTo(backupFile, overwrite = true)
                    }
                    configFile.writeText(body)

                    webListener?.onConfigUpdated()
                    val resp = JSONObject().apply {
                        put("status", "ok")
                        put("message", "頻道清單儲存成功！電視已同步載入。")
                    }
                    sendResponse(socket, 200, "application/json; charset=utf-8", resp.toString().toByteArray())
                }

                // 快速新增單一頻道
                method == "POST" && path == "/api/channel/add" -> {
                    val obj = JSONObject(body)
                    val name = obj.optString("name", "").trim()
                    val url = obj.optString("url", "").trim()
                    val format = obj.optString("format", "")
                    val volume = obj.optString("volume", "")
                    val header = obj.optString("header", "")
                    val isWebView = obj.optBoolean("isWebView", false)

                    if (name.isEmpty() || url.isEmpty()) {
                        sendError(socket, 400, "頻道名稱與網址不可為空")
                        return
                    }

                    webListener?.onChannelAdd(name, url, format, volume, header, isWebView)
                    val resp = JSONObject().apply {
                        put("status", "ok")
                        put("message", "頻道「$name」新增成功！")
                    }
                    sendResponse(socket, 200, "application/json; charset=utf-8", resp.toString().toByteArray())
                }

                // 切換指定頻道播放
                method == "POST" && path == "/api/channel/play" -> {
                    val obj = JSONObject(body)
                    val index = obj.optInt("index", -1)
                    if (index >= 0) {
                        webListener?.onPlayChannel(index)
                        val resp = JSONObject().apply {
                            put("status", "ok")
                            put("message", "已切換至第 $index 台")
                        }
                        sendResponse(socket, 200, "application/json; charset=utf-8", resp.toString().toByteArray())
                    } else {
                        sendError(socket, 400, "無效的頻道編號")
                    }
                }

                // 軟隱藏 / 取消隱藏頻道 (符合原則 3：軟刪除機制)
                method == "POST" && path == "/api/channel/toggle_hidden" -> {
                    val obj = JSONObject(body)
                    val index = obj.optInt("index", -1)
                    if (index >= 0) {
                        webListener?.onToggleHidden(index)
                        val resp = JSONObject().apply {
                            put("status", "ok")
                            put("message", "頻道顯示狀態已變更")
                        }
                        sendResponse(socket, 200, "application/json; charset=utf-8", resp.toString().toByteArray())
                    } else {
                        sendError(socket, 400, "無效的頻道編號")
                    }
                }

                // 取得 cookies.txt
                method == "GET" && path == "/api/cookies" -> {
                    val cookiesFile = File(appContext?.getExternalFilesDir(null), "cookies.txt")
                    val content = if (cookiesFile.exists()) cookiesFile.readText() else ""
                    sendResponse(socket, 200, "text/plain; charset=utf-8", content.toByteArray())
                }

                // 儲存 cookies.txt
                method == "POST" && path == "/api/cookies" -> {
                    val cookiesFile = File(appContext?.getExternalFilesDir(null), "cookies.txt")
                    cookiesFile.writeText(body)
                    webListener?.onCookiesUpdated()
                    val resp = JSONObject().apply {
                        put("status", "ok")
                        put("message", "Cookies 已更新儲存！")
                    }
                    sendResponse(socket, 200, "application/json; charset=utf-8", resp.toString().toByteArray())
                }

                // 手機虛擬遙控器指令
                method == "GET" && path == "/api/remote" -> {
                    val key = params["key"] ?: ""
                    val keyCode = when (key) {
                        "up" -> KeyEvent.KEYCODE_DPAD_UP
                        "down" -> KeyEvent.KEYCODE_DPAD_DOWN
                        "left" -> KeyEvent.KEYCODE_DPAD_LEFT
                        "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                        "ok", "enter" -> KeyEvent.KEYCODE_DPAD_CENTER
                        "back" -> KeyEvent.KEYCODE_BACK
                        "menu" -> KeyEvent.KEYCODE_MENU
                        "ch_up" -> KeyEvent.KEYCODE_CHANNEL_UP
                        "ch_down" -> KeyEvent.KEYCODE_CHANNEL_DOWN
                        "vol_up" -> KeyEvent.KEYCODE_VOLUME_UP
                        "vol_down" -> KeyEvent.KEYCODE_VOLUME_DOWN
                        "0" -> KeyEvent.KEYCODE_0
                        "1" -> KeyEvent.KEYCODE_1
                        "2" -> KeyEvent.KEYCODE_2
                        "3" -> KeyEvent.KEYCODE_3
                        "4" -> KeyEvent.KEYCODE_4
                        "5" -> KeyEvent.KEYCODE_5
                        "6" -> KeyEvent.KEYCODE_6
                        "7" -> KeyEvent.KEYCODE_7
                        "8" -> KeyEvent.KEYCODE_8
                        "9" -> KeyEvent.KEYCODE_9
                        else -> 0
                    }
                    if (keyCode != 0) {
                        webListener?.onRemoteKey(keyCode)
                        sendResponse(socket, 200, "application/json", """{"status":"ok"}""".toByteArray())
                    } else {
                        sendError(socket, 400, "未知的按鍵碼: $key")
                    }
                }

                else -> {
                    sendError(socket, 404, "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Route handler error: $path", e)
            sendError(socket, 500, "Internal Server Error: ${e.message}")
        }
    }

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c == -1) {
                if (out.size() == 0) return null
                break
            }
            if (c == '\n'.code) break
            if (c != '\r'.code) out.write(c)
        }
        return out.toString("UTF-8")
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val map = HashMap<String, String>()
        if (query.isEmpty()) return map
        query.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx != -1) {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[k] = v
            }
        }
        return map
    }

    private fun sendResponse(socket: Socket, code: Int, contentType: String, content: ByteArray) {
        val statusText = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val out = socket.getOutputStream()
        val headers = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${content.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(StandardCharsets.UTF_8))
        out.write(content)
        out.flush()
    }

    private fun sendError(socket: Socket, code: Int, message: String) {
        val json = """{"status":"error","message":"$message"}"""
        sendResponse(socket, code, "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * 產出自給自足之繁體中文 TVBox 後台網頁介面 (無任何外部 CDN 依賴，保證區網離線運作)
     */
    private fun getDashboardHtml(): String {
        return """
<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>新聞直播 TVBox 遠端配置後台</title>
<style>
  :root {
    --bg-main: #0b0f19;
    --card-bg: #151c2e;
    --card-border: #232f48;
    --primary: #2563eb;
    --primary-hover: #1d4ed8;
    --accent: #10b981;
    --text-main: #f1f5f9;
    --text-sub: #94a3b8;
    --danger: #ef4444;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
  body { background-color: var(--bg-main); color: var(--text-main); line-height: 1.6; padding-bottom: 40px; }
  header { background: #111827; border-bottom: 1px solid var(--card-border); padding: 16px 20px; display: flex; align-items: center; justify-content: space-between; }
  header h1 { font-size: 1.25rem; font-weight: 700; color: #60a5fa; display: flex; align-items: center; gap: 8px; }
  .badge { background: #065f46; color: #a7f3d0; font-size: 0.75rem; padding: 4px 8px; border-radius: 9999px; font-weight: 600; }
  .container { max-width: 900px; margin: 20px auto; padding: 0 16px; }
  .tabs { display: flex; gap: 8px; border-bottom: 1px solid var(--card-border); padding-bottom: 12px; margin-bottom: 20px; overflow-x: auto; }
  .tab-btn { background: var(--card-bg); border: 1px solid var(--card-border); color: var(--text-sub); padding: 8px 16px; border-radius: 8px; cursor: pointer; font-size: 0.95rem; white-space: nowrap; font-weight: 500; }
  .tab-btn.active { background: var(--primary); color: #fff; border-color: var(--primary); }
  .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 12px; padding: 20px; margin-bottom: 20px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.2); }
  .card h2 { font-size: 1.1rem; color: #93c5fd; margin-bottom: 14px; display: flex; align-items: center; gap: 8px; }
  label { display: block; font-size: 0.85rem; color: var(--text-sub); margin-bottom: 6px; font-weight: 500; }
  input[type="text"], input[type="url"], input[type="number"], textarea, select {
    width: 100%; background: #0b1120; border: 1px solid var(--card-border); border-radius: 8px; padding: 10px 12px; color: var(--text-main); font-size: 0.95rem; margin-bottom: 14px;
  }
  input:focus, textarea:focus { outline: none; border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,0.3); }
  textarea { min-height: 240px; font-family: monospace; font-size: 0.85rem; white-space: pre; }
  .btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; background: var(--primary); color: #fff; border: none; padding: 10px 18px; border-radius: 8px; font-weight: 600; cursor: pointer; font-size: 0.95rem; width: 100%; }
  .btn:hover { background: var(--primary-hover); }
  .btn-accent { background: var(--accent); }
  .btn-accent:hover { background: #059669; }
  .btn-danger { background: var(--danger); }
  .btn-sm { width: auto; padding: 6px 12px; font-size: 0.85rem; }
  .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  @media(max-width: 600px) { .row { grid-template-columns: 1fr; } }
  .channel-item { display: flex; align-items: center; justify-content: space-between; padding: 12px; border-bottom: 1px solid #1e293b; gap: 8px; }
  .channel-item:last-child { border-bottom: none; }
  .channel-title { font-weight: 600; font-size: 0.95rem; }
  .channel-url { font-size: 0.75rem; color: var(--text-sub); word-break: break-all; }
  .channel-actions { display: flex; gap: 6px; flex-shrink: 0; }
  .remote-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; max-width: 320px; margin: 0 auto; }
  .remote-btn { background: #1e293b; border: 1px solid #334155; color: #fff; padding: 16px; border-radius: 12px; font-size: 1.1rem; font-weight: bold; cursor: pointer; text-align: center; user-select: none; }
  .remote-btn:active { background: #3b82f6; transform: scale(0.96); }
  .toast { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); background: #10b981; color: #fff; padding: 10px 20px; border-radius: 30px; font-weight: 600; box-shadow: 0 4px 12px rgba(0,0,0,0.5); opacity: 0; pointer-events: none; transition: opacity 0.3s; z-index: 999; }
  .toast.show { opacity: 1; }
</style>
</head>
<body>

<header>
  <h1>📺 新聞直播 TVBox 遠端管理</h1>
  <div class="badge" id="statusBadge">🟢 區網連線中</div>
</header>

<div class="container">
  <div class="tabs">
    <button class="tab-btn active" onclick="switchTab('channels')">📺 頻道管理</button>
    <button class="tab-btn" onclick="switchTab('config')">📄 頻道清單 (config.txt)</button>
    <button class="tab-btn" onclick="switchTab('cookies')">🍪 Cookies 授權</button>
    <button class="tab-btn" onclick="switchTab('remote')">📱 手機虛擬遙控器</button>
  </div>

  <!-- 分頁 1: 頻道管理 -->
  <div id="tab-channels">
    <div class="card">
      <h2>➕ 快速新增頻道至電視</h2>
      <div class="row">
        <div>
          <label>頻道名稱 (必填)</label>
          <input type="text" id="addName" placeholder="例如：公視新聞台直播">
        </div>
        <div>
          <label>串流網址 / YouTube / M3U8 (必填)</label>
          <input type="url" id="addUrl" placeholder="https://...">
        </div>
      </div>
      <div class="row">
        <div>
          <label>yt-dlp 畫質格式 (選填，預設 bv*+ba/b)</label>
          <input type="text" id="addFormat" placeholder="bv*+ba/b">
        </div>
        <div>
          <label>音量係數 (選填，預設 1.0)</label>
          <input type="number" step="0.1" id="addVolume" placeholder="1.0">
        </div>
      </div>
      <div>
        <label>自訂 HTTP Headers (選填，多組以 \\r\\n 分隔)</label>
        <input type="text" id="addHeader" placeholder="Referer: https://...\\r\\nUser-Agent: ...">
      </div>
      <div style="margin-bottom: 14px;">
        <label style="display:inline-flex; align-items:center; gap:8px; cursor:pointer;">
          <input type="checkbox" id="addIsWebView"> 啟用 WebView 網頁播放模式 (針對加密網頁或反爬蟲)
        </label>
      </div>
      <button class="btn btn-accent" onclick="addNewChannel()">🚀 新增頻道並推播至電視</button>
    </div>

    <div class="card">
      <h2>📋 電視目前頻道總表 (<span id="channelCount">0</span> 台)</h2>
      <div id="channelListContainer">載入中...</div>
    </div>
  </div>

  <!-- 分頁 2: config.txt 編輯 -->
  <div id="tab-config" style="display:none;">
    <div class="card">
      <h2>📄 匯入或編輯頻道設定檔 (config.txt)</h2>
      <p style="font-size:0.85rem; color:var(--text-sub); margin-bottom:12px;">
        您可以直接上傳本機的 JSON 頻道檔案，或在此處線上編輯後儲存套用至電視。
      </p>
      <div style="margin-bottom:14px;">
        <label>選擇本機檔案上傳 (.txt / .json)</label>
        <input type="file" id="configFile" accept=".txt,.json" onchange="uploadConfigFile(this)">
      </div>
      <label>JSON 內容編輯器</label>
      <textarea id="configContent" placeholder="載入中..."></textarea>
      <div class="row">
        <button class="btn" onclick="saveConfigContent()">💾 儲存並通知電視即時更新</button>
        <button class="btn" style="background:#475569;" onclick="loadConfigContent()">🔄 重新整理</button>
      </div>
    </div>
  </div>

  <!-- 分頁 3: Cookies 設定 -->
  <div id="tab-cookies" style="display:none;">
    <div class="card">
      <h2>🍪 YouTube / 會員串流 Cookies 設定 (cookies.txt)</h2>
      <p style="font-size:0.85rem; color:var(--text-sub); margin-bottom:12px;">
        如頻道需要會員登入或遭遇 YouTube Bot 偵測，可透過瀏覽器擴充套件導出 Netscape 格式 cookies.txt 貼於此處。
      </p>
      <div style="margin-bottom:14px;">
        <label>上傳 cookies.txt</label>
        <input type="file" accept=".txt" onchange="uploadCookiesFile(this)">
      </div>
      <label>Cookies 內容 (Netscape 格式)</label>
      <textarea id="cookiesContent" placeholder="# Netscape HTTP Cookie File..."></textarea>
      <button class="btn" onclick="saveCookiesContent()">💾 儲存 Cookies 授權檔</button>
    </div>
  </div>

  <!-- 分頁 4: 手機虛擬遙控器 -->
  <div id="tab-remote" style="display:none;">
    <div class="card">
      <h2>📱 手機觸控遙控器</h2>
      <p style="font-size:0.85rem; color:var(--text-sub); margin-bottom:16px; text-align:center;">
        直接使用手機操控電視，支援方向鍵、音量、跳台與快速鍵
      </p>
      
      <div class="remote-grid" style="margin-bottom:16px;">
        <div class="remote-btn" onclick="sendKey('vol_down')">🔉 音量-</div>
        <div class="remote-btn" onclick="sendKey('up')">▲ 上</div>
        <div class="remote-btn" onclick="sendKey('vol_up')">🔊 音量+</div>

        <div class="remote-btn" onclick="sendKey('left')">◀ 左</div>
        <div class="remote-btn" style="background:#2563eb;" onclick="sendKey('ok')">OK</div>
        <div class="remote-btn" onclick="sendKey('right')">▶ 右</div>

        <div class="remote-btn" onclick="sendKey('back')">⮌ 返回</div>
        <div class="remote-btn" onclick="sendKey('down')">▼ 下</div>
        <div class="remote-btn" onclick="sendKey('menu')">≡ 選單</div>
      </div>

      <div class="remote-grid">
        <div class="remote-btn" onclick="sendKey('ch_up')">🔼 上一台</div>
        <div class="remote-btn" style="background:#0f172a; border-color:#1e293b;"></div>
        <div class="remote-btn" onclick="sendKey('ch_down')">🔽 下一台</div>
      </div>
    </div>
  </div>
</div>

<div class="toast" id="toast"></div>

<script>
  function showToast(msg) {
    const t = document.getElementById('toast');
    t.innerText = msg;
    t.classList.add('show');
    if (navigator.vibrate) navigator.vibrate(25);
    setTimeout(() => t.classList.remove('show'), 2500);
  }

  function switchTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    event.target.classList.add('active');
    ['channels', 'config', 'cookies', 'remote'].forEach(t => {
      document.getElementById('tab-' + t).style.display = (t === tab ? 'block' : 'none');
    });
    if (tab === 'channels') loadChannels();
    if (tab === 'config') loadConfigContent();
    if (tab === 'cookies') loadCookiesContent();
  }

  function sendKey(key) {
    if (navigator.vibrate) navigator.vibrate(20);
    fetch('/api/remote?key=' + key).catch(e => console.error(e));
  }

  function loadChannels() {
    fetch('/api/channels')
      .then(r => r.json())
      .then(channels => {
        document.getElementById('channelCount').innerText = channels.length;
        const c = document.getElementById('channelListContainer');
        if (channels.length === 0) {
          c.innerHTML = '<div style="color:var(--text-sub); text-align:center; padding:20px;">尚無頻道資料</div>';
          return;
        }
        let html = '';
        channels.forEach(ch => {
          const safeName = escapeHtml(ch.name);
          const safeUrl = escapeHtml(ch.url);
          const hiddenTag = ch.isHidden ? '<span style="color:#ef4444; font-size:0.75rem;">(隱藏中)</span>' : '';
          html += `
            <div class="channel-item">
              <div>
                <div class="channel-title">${'$'}{ch.index}. ${'$'}{safeName} ${'$'}{hiddenTag}</div>
                <div class="channel-url">${'$'}{safeUrl}</div>
              </div>
              <div class="channel-actions">
                <button class="btn btn-sm btn-accent" onclick="playChannel(${'$'}{ch.index})">▶ 播放</button>
                <button class="btn btn-sm" style="background:#334155;" onclick="toggleHidden(${'$'}{ch.index})">${'$'}{ch.isHidden ? '👁 顯示' : '🚫 隱藏'}</button>
              </div>
            </div>
          `;
        });
        c.innerHTML = html;
      })
      .catch(e => {
        document.getElementById('channelListContainer').innerText = '載入頻道失敗: ' + e;
      });
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
  }

  function playChannel(index) {
    fetch('/api/channel/play', {
      method: 'POST',
      body: JSON.stringify({ index: index })
    })
    .then(r => r.json())
    .then(data => showToast(data.message || '指令已送出'))
    .catch(e => showToast('播放失敗: ' + e));
  }

  function toggleHidden(index) {
    fetch('/api/channel/toggle_hidden', {
      method: 'POST',
      body: JSON.stringify({ index: index })
    })
    .then(r => r.json())
    .then(data => {
      showToast(data.message || '狀態已切換');
      loadChannels();
    })
    .catch(e => showToast('切換失敗: ' + e));
  }

  function addNewChannel() {
    const name = document.getElementById('addName').value.trim();
    const url = document.getElementById('addUrl').value.trim();
    const format = document.getElementById('addFormat').value.trim();
    const volume = document.getElementById('addVolume').value.trim();
    const header = document.getElementById('addHeader').value.trim();
    const isWebView = document.getElementById('addIsWebView').checked;

    if (!name || !url) {
      alert('請填寫頻道名稱與串流網址');
      return;
    }

    fetch('/api/channel/add', {
      method: 'POST',
      body: JSON.stringify({ name, url, format, volume, header, isWebView })
    })
    .then(r => r.json())
    .then(data => {
      showToast(data.message || '頻道新增成功！');
      document.getElementById('addName').value = '';
      document.getElementById('addUrl').value = '';
      loadChannels();
    })
    .catch(e => showToast('新增失敗: ' + e));
  }

  function loadConfigContent() {
    fetch('/api/config')
      .then(r => r.text())
      .then(t => {
        try {
          const parsed = JSON.parse(t);
          document.getElementById('configContent').value = JSON.stringify(parsed, null, 2);
        } catch(e) {
          document.getElementById('configContent').value = t;
        }
      })
      .catch(e => showToast('載入失敗: ' + e));
  }

  function saveConfigContent() {
    const content = document.getElementById('configContent').value.trim();
    try {
      JSON.parse(content);
    } catch(e) {
      alert('JSON 格式錯誤，請檢查語法後再儲存：\n' + e.message);
      return;
    }
    fetch('/api/config', {
      method: 'POST',
      body: content
    })
    .then(r => r.json())
    .then(data => showToast(data.message || '儲存成功！'))
    .catch(e => showToast('儲存失敗: ' + e));
  }

  function uploadConfigFile(input) {
    if (!input.files || input.files.length === 0) return;
    const file = input.files[0];
    const reader = new FileReader();
    reader.onload = function(e) {
      const text = e.target.result;
      try {
        const parsed = JSON.parse(text);
        document.getElementById('configContent').value = JSON.stringify(parsed, null, 2);
        showToast('已載入檔案，請檢視後點擊「儲存」按鈕！');
      } catch(err) {
        alert('上傳檔案非有效 JSON 格式：' + err.message);
      }
    };
    reader.readAsText(file);
  }

  function loadCookiesContent() {
    fetch('/api/cookies')
      .then(r => r.text())
      .then(t => document.getElementById('cookiesContent').value = t)
      .catch(e => showToast('載入 Cookies 失敗: ' + e));
  }

  function saveCookiesContent() {
    const text = document.getElementById('cookiesContent').value;
    fetch('/api/cookies', {
      method: 'POST',
      body: text
    })
    .then(r => r.json())
    .then(data => showToast(data.message || 'Cookies 儲存成功！'))
    .catch(e => showToast('儲存失敗: ' + e));
  }

  function uploadCookiesFile(input) {
    if (!input.files || input.files.length === 0) return;
    const reader = new FileReader();
    reader.onload = function(e) {
      document.getElementById('cookiesContent').value = e.target.result;
      showToast('已讀取 cookies 檔案，請點擊「儲存」套用');
    };
    reader.readAsText(input.files[0]);
  }

  // 初始載入
  loadChannels();
</script>
</body>
</html>
        """.trimIndent()
    }
}
