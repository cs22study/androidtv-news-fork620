package io.github.anenasa.news

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import io.github.anenasa.news.YtDlp.Companion.create
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.system.exitProcess
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class MainActivity : AppCompatActivity() {

    @Volatile
    var channelNum: Int = 0
    var channel: ArrayList<Channel> = ArrayList()
    var channelLengthOfConfig: Int = 0
    var input: String = ""
    var defaultFormat: String = ""
    var defaultVolume: String = ""
    var isShowErrorMessage: Boolean = false
    var enableBackgroundExtract: Boolean = false
    var saveVideoUrl: Boolean = false
    var invertChannelButtons: Boolean = false
    var hideNavigationBar: Boolean = false
    var hideStatusBar: Boolean = false
    var useExternalJS: Boolean = false
    var channelListLoaded: Boolean = false

    var ytDlp: YtDlp? = null
    val player: ExoPlayer by lazy { ExoPlayer.Builder(this).build() }
    var webView: WebView? = null
    var webViewHelper: WebViewHelper? = null
    val playerView: SurfaceView by lazy { findViewById(R.id.playerView) }
    val textView: TextView by lazy { findViewById(R.id.textView) }
    val textInfo: TextView by lazy { findViewById(R.id.textInfo) }
    val errorMessageView: TextView by lazy { findViewById(R.id.errorMessage) }
    var backgroundExtractJob: Job? = null
    var readChannelListJob: Job? = null
    val okHttpClient: OkHttpClient = MyApplication.okHttpClient
    val json = Json {
        ignoreUnknownKeys = true
        allowComments = true
    }
    val preferences: SharedPreferences by lazy { getSharedPreferences("io.github.anenasa.news", MODE_PRIVATE) }

    @Volatile
    var isStarted: Boolean = true
    val audioManager: AudioManager by lazy { applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager }
    var errorCount: Int = 0
    var doNotPlayOnStart: Boolean = false

    private val showChannelListLauncher = registerForActivityResult(StartActivityForResult()) { result: ActivityResult? ->
        val data = result?.data
        if (result?.resultCode == RESULT_OK && data != null) {
            switchChannel(data.getIntExtra("channelNum", 0))
        } else {
            play(channelNum)
        }
    }

    private val showChannelInfoLauncher = registerForActivityResult(StartActivityForResult()) { result: ActivityResult? ->
        val data = result?.data
        if (result?.resultCode == RESULT_OK && data != null) {
            if (data.getBooleanExtra("delete", false)) {
                channel.removeAt(channelNum)
                resetChannelNum()
                play(channelNum)
                saveSettings()
                return@registerForActivityResult
            }

            if (data.getBooleanExtra("remove_cache", false)) {
                channel[channelNum].clearVideo()
            }

            // If url or format changes, clear video to extract again
            val urlOld = channel[channelNum].url
            var urlNew = data.getStringExtra("customUrl")?.takeIf { it.isNotEmpty() }
                ?: channel[channelNum].defaultUrl
            val formatOld = channel[channelNum].format
            var formatNew = data.getStringExtra("customFormat")?.takeIf { it.isNotEmpty() }
                ?: channel[channelNum].defaultFormat
            if (urlOld != urlNew || formatOld != formatNew) {
                channel[channelNum].clearVideo()
            }

            channel[channelNum].name = data.getStringExtra("customName").orEmpty()
            channel[channelNum].isHidden = data.getBooleanExtra("isHidden", false)
            channel[channelNum].url = data.getStringExtra("customUrl").orEmpty()
            channel[channelNum].format = data.getStringExtra("customFormat").orEmpty()
            channel[channelNum].setVolume(data.getStringExtra("customVolume").orEmpty())
            channel[channelNum].header = data.getStringExtra("customHeader").orEmpty()
            play(channelNum)
            saveSettings()
        } else {
            play(channelNum)
        }
    }

    private val addNewChannelLauncher = registerForActivityResult(StartActivityForResult()) { result: ActivityResult? ->
        val data = result?.data
        if (result?.resultCode == RESULT_OK && data != null && !data.getBooleanExtra("delete", false)) {
            val customName = data.getStringExtra("customName").orEmpty()
            val customUrl = data.getStringExtra("customUrl").orEmpty()
            if (customUrl.isNotEmpty() || customName.isNotEmpty()) {
                val ch = Channel(
                    "",
                    "",
                    defaultFormat,
                    defaultVolume.toFloat(),
                    "",
                    HashMap(),
                    false
                ).apply {
                    name = customName
                    isHidden = data.getBooleanExtra("isHidden", false)
                    url = customUrl
                    format = data.getStringExtra("customFormat").orEmpty()
                    setVolume(data.getStringExtra("customVolume").orEmpty())
                    header = data.getStringExtra("customHeader").orEmpty()
                }
                channel.add(ch)
                saveSettings()
                switchChannel(channel.size - 1)
            } else {
                play(channelNum)
            }
        } else {
            play(channelNum)
        }
    }

    private val showSettingsLauncher = registerForActivityResult(StartActivityForResult()) { result: ActivityResult? ->
        val data = result?.data
        if (result?.resultCode == RESULT_OK && data != null) {
            defaultFormat = data.getStringExtra("defaultFormat").orEmpty()
            defaultVolume = data.getStringExtra("defaultVolume").orEmpty()
            isShowErrorMessage = data.getBooleanExtra("isShowErrorMessage", false)
            enableBackgroundExtract = data.getBooleanExtra("enableBackgroundExtract", false)
            saveVideoUrl = data.getBooleanExtra("saveVideoUrl", false)
            invertChannelButtons = data.getBooleanExtra("invertChannelButtons", false)
            hideNavigationBar = data.getBooleanExtra("hideNavigationBar", false)
            hideStatusBar = data.getBooleanExtra("hideStatusBar", false)
            useExternalJS = data.getBooleanExtra("useExternalJS", false)
            val noVideoCache = data.getBooleanExtra("remove_cache", false)
            saveSettings(noVideoCache)
            if (data.getBooleanExtra("ytDlpUpdated", false)) {
                // Kill process, so new version of yt-dlp can be loaded
                exitProcess(0)
            }
            MyApplication.cookieJar.loadFile(File(getExternalFilesDir(null), "cookies.txt"))
            ytDlp!!.useExternalJS = useExternalJS

            backgroundExtractJob?.cancel()
            readChannelListJob = lifecycleScope.launch(Dispatchers.IO) {
                while (true) {
                    readChannelList(noVideoCache)
                    delay(5000)
                }
            }
        } else {
            play(channelNum)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        channelNum = preferences.getInt("channelNum", 0)
        defaultFormat = preferences.getString("defaultFormat", "bv*+ba/b")!!
        defaultVolume = preferences.getString("defaultVolume", "1.0")!!
        isShowErrorMessage = preferences.getBoolean("isShowErrorMessage", false)
        enableBackgroundExtract = preferences.getBoolean("enableBackgroundExtract", false)
        saveVideoUrl = preferences.getBoolean("saveVideoUrl", false)
        invertChannelButtons = preferences.getBoolean("invertChannelButtons", false)
        hideNavigationBar = preferences.getBoolean("hideNavigationBar", false)
        hideStatusBar = preferences.getBoolean("hideStatusBar", false)
        useExternalJS = preferences.getBoolean("useExternalJS", false)

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error", error)
                if (!channel.getOrNull(channelNum)?.video.isNullOrEmpty()) {
                    showErrorMessage(error.message)
                }
                if (errorCount > 0) {
                    // Force extract by removing video url
                    channel.getOrNull(channelNum)?.clearVideo()
                    errorCount = 0
                } else errorCount++
                play(channelNum)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    if (!isStarted) {
                        player.stop()
                    }
                    errorMessageView.text = ""
                    textInfo.text = ""
                    errorCount = 0
                } else if (state == Player.STATE_ENDED) {
                    channel[channelNum].clearVideo()
                    play(channelNum)
                }
            }
        })
        player.setVideoSurfaceView(playerView)
        MyApplication.cookieJar.loadFile(File(getExternalFilesDir(null), "cookies.txt"))
    }

    override fun onStart() {
        super.onStart()
        initWebServer()
        lifecycleScope.launch(Dispatchers.IO) { initializeYtDlp() }
        checkForAppUpdatesSilently()
        isStarted = true
        // Do not call play() more than once
        // play() will be called elsewhere
        if (doNotPlayOnStart) {
            doNotPlayOnStart = false
            return
        }
        if (channelListLoaded) {
            play(channelNum)
        }
    }

    private var hasCheckedUpdateOnStartup = false

    private fun checkForAppUpdatesSilently() {
        if (hasCheckedUpdateOnStartup) return
        hasCheckedUpdateOnStartup = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://raw.githubusercontent.com/cs22study/androidtv-news-fork620/main/VERSION")
                    .build()
                val versionNew: String?
                MyApplication.okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@launch
                    versionNew = response.body.string().trim()
                }
                val currentVersion = BuildConfig.VERSION_NAME.substringBefore("-")
                if (!versionNew.isNullOrEmpty() && versionNew != currentVersion) {
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(
                                this@MainActivity,
                                "📢 發現新版本 v$versionNew！可至「系統設定」->「檢查更新」進行升級。",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Startup update check failed: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        var systemUiFlag = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        if (hideStatusBar) {
            systemUiFlag += View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        if (hideNavigationBar) {
            systemUiFlag += View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
        if (systemUiFlag != View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) {
            window.decorView.systemUiVisibility = systemUiFlag
        }
    }

    suspend fun initializeYtDlp() {
        if (ytDlp != null) {
            // Already initialized
            return
        }
        try {
            ytDlp = create(this, useExternalJS)
            readChannelListJob = lifecycleScope.launch(Dispatchers.IO) {
                while (true) {
                    readChannelList()
                    delay(5000)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString(), e)
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("yt-dlp 載入失敗")
                    .setMessage(e.message)
                    .setCancelable(false)
                    .setNegativeButton("確定") { _, _ -> finish() }
                    .show()
            }
        }
    }

    suspend fun readChannelList(noVideoCache: Boolean = false) {
        if (!isStarted) {
            return
        }
        channelListLoaded = false
        channel = ArrayList()
        try {
            val extDir = getExternalFilesDir(null)
            val configFile = if (extDir != null) File(extDir, "config.txt") else null
            val internalConfigFile = File(filesDir, "config.txt")

            var configString: String? = null
            if (configFile?.exists() == true && configFile.length() > 0) {
                configString = configFile.readText()
                try { configFile.copyTo(internalConfigFile, overwrite = true) } catch (_: Exception) {}
            } else if (internalConfigFile.exists() && internalConfigFile.length() > 0) {
                configString = internalConfigFile.readText()
                if (configFile != null) try { internalConfigFile.copyTo(configFile, overwrite = true) } catch (_: Exception) {}
            } else {
                val prefConfig = preferences.getString("config_txt_backup", null)
                if (!prefConfig.isNullOrEmpty()) {
                    configString = prefConfig
                    if (configFile != null) try { configFile.writeText(prefConfig) } catch (_: Exception) {}
                }
            }

            if (!configString.isNullOrEmpty()) {
                readChannelListFromString(configString)
            } else {
                val request = Request.Builder()
                    .url("https://anenasa.github.io/channel/config.txt")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("下載失敗：$response")
                    }
                    val downloaded = response.body.string()
                    readChannelListFromString(downloaded)
                    if (configFile != null) try { configFile.writeText(downloaded) } catch (_: Exception) {}
                    try { internalConfigFile.writeText(downloaded) } catch (_: Exception) {}
                }
            }
            channelLengthOfConfig = channel.size

            var customChannelList: JsonObject? = null
            var newChannelArray: List<CustomChannel>? = null
            val customFile = if (extDir != null) File(extDir, "custom.txt") else null
            val internalCustomFile = File(filesDir, "custom.txt")

            var customString: String? = null
            if (customFile?.exists() == true && customFile.length() > 0) {
                customString = customFile.readText()
                try { customFile.copyTo(internalCustomFile, overwrite = true) } catch (_: Exception) {}
            } else if (internalCustomFile.exists() && internalCustomFile.length() > 0) {
                customString = internalCustomFile.readText()
                if (customFile != null) try { internalCustomFile.copyTo(customFile, overwrite = true) } catch (_: Exception) {}
            } else {
                val extBak = if (extDir != null) File(extDir, "custom.txt.bak") else null
                val intBak = File(filesDir, "custom.txt.bak")
                if (extBak?.exists() == true && extBak.length() > 0) {
                    customString = extBak.readText()
                } else if (intBak.exists() && intBak.length() > 0) {
                    customString = intBak.readText()
                } else {
                    customString = preferences.getString("custom_channels_json_backup", null)
                }
                if (!customString.isNullOrEmpty() && customFile != null) {
                    try { customFile.writeText(customString) } catch (_: Exception) {}
                }
            }

            if (!customString.isNullOrEmpty()) {
                try {
                    val customJsonObject = json.parseToJsonElement(customString).jsonObject
                    customChannelList = customJsonObject["customChannelList"]?.jsonObject
                    customJsonObject["newChannelArray"]?.jsonArray?.let { arr ->
                        newChannelArray = json.decodeFromJsonElement<List<CustomChannel>>(arr)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse custom.txt error", e)
                }
            }

            // Read custom settings for this channel
            for (ch in channel) {
                if (customChannelList?.containsKey(ch.name) == true) {
                    val customChannelObject = json.decodeFromJsonElement<CustomChannel>(customChannelList[ch.name]!!.jsonObject)
                    ch.url = customChannelObject.customUrl
                    ch.name = customChannelObject.customName
                    ch.format = customChannelObject.customFormat
                    ch.setVolume(customChannelObject.customVolume)
                    ch.header = customChannelObject.customHeader
                    ch.isHidden = customChannelObject.isHidden
                }
            }

            //Read custom channel set in app
            newChannelArray?.forEach {
                val ch = Channel(
                    "",
                    "",
                    defaultFormat,
                    defaultVolume.toFloat(),
                    "",
                    HashMap(),
                    false
                ).apply {
                    url = it.customUrl
                    name = it.customName
                    format = it.customFormat
                    setVolume(it.customVolume)
                    header = it.customHeader
                    isHidden = it.isHidden
                }
                channel.add(ch)
            }

            if (saveVideoUrl && !noVideoCache) {
                for (ch in channel) {
                    ch.video = preferences.getString("${ch.url} ${ch.format}", "")!!
                    ch.time = preferences.getLong("${ch.url} ${ch.format} time", 0)
                }
            }

            channelListLoaded = true
            withContext(Dispatchers.Main) { errorMessageView.text = "" }

            if (channelNum >= channel.size) {
                resetChannelNum()
            }
            withContext(Dispatchers.Main) { play(channelNum) }

            if (enableBackgroundExtract) {
                backgroundExtractJob = lifecycleScope.launch(Dispatchers.IO) {
                    while (true) {
                        // Prevent NPE
                        val channelInThread = channel
                        for (i in channelInThread.indices) {
                            try {
                                if (!channelInThread[i].isHidden) {
                                    channelInThread[i].extract(ytDlp!!, okHttpClient, true)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Background extract error", e)
                            }
                            yield()
                        }
                        delay(3600000)
                    }
                }
            }
            readChannelListJob?.cancel()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                errorMessageView.text = String.format(
                    "頻道清單讀取失敗，按 OK 或螢幕進入設定\n%s",
                    e.message
                )
            }
            Log.e(TAG, e.toString(), e)
            channel = ArrayList()
        }
    }

    fun readChannelListFromString(content: String) {
        for (ch in json.decodeFromString<ChannelConfig>(content).channelList) {
            if (ch.list.isNotEmpty()) {
                val request = Request.Builder()
                    .url(ch.list)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("下載失敗：$response")
                    }
                    readChannelListFromString(response.body.string())
                }
                continue
            }
            val url = ch.url
            val name = ch.name
            val format = ch.format ?: defaultFormat
            val volume = ch.volume ?: defaultVolume.toFloat()
            val header = ch.header
            val ytdlOptions = ch.ytdlOptions
            val isWebView = ch.isWebView
            val scriptOnFinish = ch.scriptOnFinish
            val channelObject = Channel(url, name, format, volume, header, ytdlOptions, isWebView, scriptOnFinish)
            channel.add(channelObject)
        }
    }

    fun toPrevChannel() {
        do {
            if (channelNum == 0) {
                channelNum = channel.size - 1
            } else {
                channelNum -= 1
            }
        } while (channel[channelNum].isHidden)
        switchChannel(channelNum)
    }

    fun toNextChannel() {
        do {
            if (channelNum == channel.size - 1) {
                channelNum = 0
            } else {
                channelNum += 1
            }
        } while (channel[channelNum].isHidden)
        switchChannel(channelNum)
    }

    fun switchChannel(num: Int) {
        player.stop()
        errorMessageView.text = ""
        channelNum = num
        play(num)
    }

    @OptIn(markerClass = [UnstableApi::class])
    fun play(num: Int) {
        textInfo.text = String.format(
            Locale.ROOT,
            "正在載入 %d %s",
            num,
            channel[num].name
        )

        if (channel[num].isWebView) {
            player.stop()
            errorMessageView.text = ""
            if (webViewHelper == null) {
                webViewHelper = WebViewHelper()
                webView = webViewHelper?.createWebView(this)
                findViewById<FrameLayout>(R.id.webViewContainer).addView(webView)
            }
            webViewHelper?.loadUrl(webView, channel[num].url, channel[num].scriptOnFinish, channel[num].okHttpHeaders)
            return
        }

        webViewHelper?.stop(webView)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                channel[num].extract(ytDlp!!, okHttpClient)
            } catch (e: Exception) {
                if (channelNum != num) return@launch
                Log.e(TAG, "Channel.parse error", e)
                withContext(Dispatchers.Main) { showErrorMessage(e.message) }
            }
            if (channelNum != num) return@launch

            val httpFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(channel[num].okHttpHeaders.toMap())
            val factory = DefaultDataSource.Factory(this@MainActivity, httpFactory)
            val url = channel[num].video
            val split = url.indexOf('\n')
            val mediaSource: MediaSource = if (split == -1) {
                val mediaItem = MediaItem.fromUri(url)
                DefaultMediaSourceFactory(factory)
                    .createMediaSource(mediaItem)
            } else {
                val firstUrl = url.substring(0, split)
                val firstMediaItem = MediaItem.fromUri(firstUrl)
                val firstMediaSource = DefaultMediaSourceFactory(factory)
                    .createMediaSource(firstMediaItem)
                val secondUrl = url.substring(split + 1)
                val secondMediaItem = MediaItem.fromUri(secondUrl)
                val secondMediaSource = DefaultMediaSourceFactory(factory)
                    .createMediaSource(secondMediaItem)
                MergingMediaSource(firstMediaSource, secondMediaSource)
            }

            // player needs to run on main thread
            withContext(Dispatchers.Main) {
                if (num != channelNum) {
                    // Already switched to another channel, do not play this
                    return@withContext
                }
                player.setMediaSource(mediaSource)
                player.prepare()
                player.volume = channel[num].volume
                if (isStarted) {
                    player.play()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && supportFragmentManager.fragments
                .isEmpty()
        ) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (!channelListLoaded) {
                        readChannelListJob?.let {
                            it.cancel()
                            showSettings(findViewById(R.id.container))
                        }
                    } else if (input.isEmpty()) {
                        showMenu()
                    } else if (input.toInt() < channel.size) {
                        switchChannel(input.toInt())
                        clearInput()
                    } else {
                        clearInput()
                    }
                    return true
                }

                KeyEvent.KEYCODE_BACK -> {
                    if (!input.isEmpty()) {
                        clearInput()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("退出")
                            .setMessage("是否要退出新聞直播？")
                            .setPositiveButton("確定") { _, _ -> finish() }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (!channelListLoaded) return true
                    if (invertChannelButtons) {
                        toNextChannel()
                    } else {
                        toPrevChannel()
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (!channelListLoaded) return true
                    if (invertChannelButtons) {
                        toPrevChannel()
                    } else {
                        toNextChannel()
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    audioManager.adjustVolume(
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_PLAY_SOUND
                    )
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    audioManager.adjustVolume(
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_PLAY_SOUND
                    )
                    return true
                }

                KeyEvent.KEYCODE_INFO -> {
                    if (!channelListLoaded) return true
                    showChannelInfo(findViewById(R.id.container))
                    return true
                }

                KeyEvent.KEYCODE_MENU -> {
                    if (!channelListLoaded) return true
                    showSettings(findViewById(R.id.container))
                    return true
                }

                KeyEvent.KEYCODE_0 -> {
                    appendInput(0)
                    return true
                }

                KeyEvent.KEYCODE_1 -> {
                    appendInput(1)
                    return true
                }

                KeyEvent.KEYCODE_2 -> {
                    appendInput(2)
                    return true
                }

                KeyEvent.KEYCODE_3 -> {
                    appendInput(3)
                    return true
                }

                KeyEvent.KEYCODE_4 -> {
                    appendInput(4)
                    return true
                }

                KeyEvent.KEYCODE_5 -> {
                    appendInput(5)
                    return true
                }

                KeyEvent.KEYCODE_6 -> {
                    appendInput(6)
                    return true
                }

                KeyEvent.KEYCODE_7 -> {
                    appendInput(7)
                    return true
                }

                KeyEvent.KEYCODE_8 -> {
                    appendInput(8)
                    return true
                }

                KeyEvent.KEYCODE_9 -> {
                    appendInput(9)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN ||
            supportFragmentManager.fragments.isNotEmpty()) {
            return super.dispatchTouchEvent(event)
        }
        supportFragmentManager.popBackStack()
        if (!channelListLoaded) {
            readChannelListJob?.let {
                it.cancel()
                showSettings(findViewById(R.id.container))
            }
        } else if (event.x.toInt() < playerView.width / 3) {
            if (invertChannelButtons) {
                toNextChannel()
            } else {
                toPrevChannel()
            }
        } else if (event.x.toInt() < playerView.width / 3 * 2) {
            showMenu()
        } else {
            if (invertChannelButtons) {
                toPrevChannel()
            } else {
                toNextChannel()
            }
        }
        return true
    }

    fun appendInput(num: Int) {
        input += num
        textView.text = input
    }

    fun clearInput() {
        input = ""
        textView.text = ""
    }

    fun showMenu() {
        val fragment = MenuFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(fragment.javaClass.name)
            .commit()
    }

    fun showChannelList(view: View) {
        val nameArray = arrayOfNulls<String>(channel.size)
        val isHiddenArray = BooleanArray(channel.size)
        for (i in channel.indices) {
            nameArray[i] = channel[i].name
            isHiddenArray[i] = channel[i].isHidden
        }
        val intent = Intent(this, ChannelListActivity::class.java).apply {
            putExtra("nameArray", nameArray)
            putExtra("isHiddenArray", isHiddenArray)
            putExtra("currentNum", channelNum)
        }
        showChannelListLauncher.launch(intent)
        supportFragmentManager.popBackStack()
        doNotPlayOnStart = true
    }

    fun showChannelInfo(view: View) {
        val intent = Intent(this, ChannelInfoActivity::class.java).apply {
            putExtra("isNewChannel", channelNum >= channelLengthOfConfig)
            putExtra("index", channelNum)
            putExtra("defaultUrl", channel[channelNum].defaultUrl)
            putExtra("defaultName", channel[channelNum].defaultName)
            putExtra("defaultFormat", channel[channelNum].defaultFormat)
            putExtra("defaultVolume", channel[channelNum].defaultVolume)
            putExtra("defaultHeader", channel[channelNum].defaultHeader)
            putExtra("customUrl", channel[channelNum].customUrl)
            putExtra("customName", channel[channelNum].customName)
            putExtra("customFormat", channel[channelNum].customFormat)
            putExtra("customVolume", channel[channelNum].customVolume)
            putExtra("customHeader", channel[channelNum].customHeader)
            putExtra("isHidden", channel[channelNum].isHidden)
            putExtra("width", player.videoSize.width)
            putExtra("height", player.videoSize.height)
        }
        showChannelInfoLauncher.launch(intent)
        supportFragmentManager.popBackStack()
        doNotPlayOnStart = true
    }

    fun addNewChannel(view: View) {
        val intent = Intent(this, ChannelInfoActivity::class.java).apply {
            putExtra("isNewChannel", true)
            putExtra("index", channel.size)
            putExtra("defaultUrl", "")
            putExtra("defaultName", "")
            putExtra("defaultFormat", defaultFormat)
            putExtra("defaultVolume", defaultVolume.toFloat())
            putExtra("defaultHeader", "")
            putExtra("customUrl", "")
            putExtra("customName", "")
            putExtra("customFormat", "")
            putExtra("customVolume", "")
            putExtra("customHeader", "")
            putExtra("isHidden", false)
            putExtra("width", 0)
            putExtra("height", 0)
        }
        addNewChannelLauncher.launch(intent)
        supportFragmentManager.popBackStack()
        doNotPlayOnStart = true
    }

    fun showSettings(view: View) {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            putExtra("defaultFormat", defaultFormat)
            putExtra("defaultVolume", defaultVolume)
            putExtra("isShowErrorMessage", isShowErrorMessage)
            putExtra("enableBackgroundExtract", enableBackgroundExtract)
            putExtra("saveVideoUrl", saveVideoUrl)
            putExtra("invertChannelButtons", invertChannelButtons)
            putExtra("hideNavigationBar", hideNavigationBar)
            putExtra("hideStatusBar", hideStatusBar)
            putExtra("useExternalJS", useExternalJS)
        }
        showSettingsLauncher.launch(intent)
        supportFragmentManager.popBackStack()
        doNotPlayOnStart = true
    }

    fun showWebAdmin(view: View) {
        initWebServer()
        val url = LocalWebServer.serverUrl
        val message = """
            請在同一個 Wi-Fi 區域網路下的手機或電腦瀏覽器輸入：

            $url

            後台功能特色：
            • 免遙控器輸入：手機瀏覽器直接貼上頻道網址與設定
            • 頻道清單維護：上傳或線上編輯 config.txt
            • 串流授權：貼上或上傳 cookies.txt
            • 虛擬觸控遙控器：手機隨時轉台、調音量與換台
            
            (服務已在背景運作，請直接以手機或電腦連線)
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("🌐 區網遠端配置後台")
            .setMessage(message)
            .setPositiveButton("確定 (保持後台運作)") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("重啟服務") { dialog, _ ->
                LocalWebServer.stop()
                initWebServer()
                showWebAdmin(view)
            }
            .show()
        supportFragmentManager.popBackStack()
    }

    fun initWebServer() {
        LocalWebServer.start(this, 8080, object : LocalWebServer.WebListener {
            override fun onConfigUpdated() {
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "已從 Web 後台更新頻道清單！", Toast.LENGTH_SHORT).show()
                    readChannelListJob?.cancel()
                    readChannelListJob = lifecycleScope.launch(Dispatchers.IO) {
                        readChannelList()
                    }
                }
            }

            override fun onCookiesUpdated() {
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "已從 Web 後台更新 Cookies！", Toast.LENGTH_SHORT).show()
                    MyApplication.cookieJar.loadFile(File(getExternalFilesDir(null), "cookies.txt"))
                }
            }

            override fun onPlayChannel(index: Int) {
                lifecycleScope.launch(Dispatchers.Main) {
                    if (index in channel.indices) {
                        switchChannel(index)
                    }
                }
            }

            override fun onRemoteKey(keyCode: Int) {
                lifecycleScope.launch(Dispatchers.Main) {
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                }
            }

            override fun onChannelAdd(
                name: String,
                url: String,
                format: String,
                volume: String,
                header: String,
                isWebView: Boolean
            ) {
                lifecycleScope.launch(Dispatchers.Main) {
                    val ch = Channel(
                        "",
                        "",
                        format.ifEmpty { defaultFormat },
                        volume.toFloatOrNull() ?: defaultVolume.toFloat(),
                        header,
                        HashMap(),
                        isWebView
                    ).apply {
                        this.name = name
                        this.url = url
                        this.format = format
                        if (volume.isNotEmpty()) this.setVolume(volume)
                        this.header = header
                    }
                    channel.add(ch)
                    saveSettings()
                    switchChannel(channel.size - 1)
                    Toast.makeText(this@MainActivity, "已新增頻道：$name", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onToggleHidden(index: Int) {
                lifecycleScope.launch(Dispatchers.Main) {
                    if (index in channel.indices) {
                        channel[index].isHidden = !channel[index].isHidden
                        saveSettings()
                        Toast.makeText(this@MainActivity, "頻道已${if (channel[index].isHidden) "隱藏" else "顯示"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onChannelsImported(channels: List<Channel>): Int {
                lifecycleScope.launch(Dispatchers.Main) {
                    channel.clear()
                    channel.addAll(channels)
                    channelLengthOfConfig = 0
                    saveSettings()
                    if (channelNum >= channel.size) {
                        channelNum = 0
                    }
                    if (channel.isNotEmpty()) {
                        play(channelNum)
                    }
                    Toast.makeText(this@MainActivity, "成功匯入 ${channels.size} 個頻道並即時生效！", Toast.LENGTH_SHORT).show()
                }
                return channels.size
            }

            override fun getChannelList(): List<Channel> = channel

            override fun getCurrentChannelIndex(): Int = channelNum
        })
    }

    fun showChannelNumEdit(view: View) {
        val editText = EditText(this)
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle("輸入頻道號碼")
            .setView(editText)
            .setPositiveButton("確定") { _, _ ->
                if (editText.text.toString().toInt() < channel.size) {
                    switchChannel(editText.text.toString().toInt())
                }
            }
            .setNegativeButton("取消", null)
            .show()
        supportFragmentManager.popBackStack()
    }

    fun saveSettings(noVideoCache: Boolean = false) {
        preferences.edit(commit = true) {
            putInt("channelNum", channelNum)
            putString("defaultFormat", defaultFormat)
            putString("defaultVolume", defaultVolume)
            putBoolean("isShowErrorMessage", isShowErrorMessage)
            putBoolean("enableBackgroundExtract", enableBackgroundExtract)
            putBoolean("saveVideoUrl", saveVideoUrl)
            putBoolean("invertChannelButtons", invertChannelButtons)
            putBoolean("hideNavigationBar", hideNavigationBar)
            putBoolean("hideStatusBar", hideStatusBar)
            putBoolean("useExternalJS", useExternalJS)

            if (saveVideoUrl && !noVideoCache) {
                for (ch in channel) {
                    putString("${ch.url} ${ch.format}", ch.video)
                    putLong("${ch.url} ${ch.format} time", ch.time)
                }
            }
        }

        // Channel list not initialized, do not write empty list
        if (!channelListLoaded) return

        val customChannels = channel.map { ch ->
            CustomChannel(
                customUrl = ch.customUrl,
                customName = ch.customName,
                customFormat = ch.customFormat,
                customVolume = ch.customVolume,
                customHeader = ch.customHeader,
                isHidden = ch.isHidden
            )
        }
        val jsonString = buildJsonObject {
            putJsonObject("customChannelList") {
                customChannels.take(channelLengthOfConfig).forEachIndexed { index, ch ->
                    put(channel[index].defaultName, json.encodeToJsonElement(ch))
                }
            }
            putJsonArray("newChannelArray") {
                customChannels.drop(channelLengthOfConfig).forEach { ch ->
                    add(json.encodeToJsonElement(ch))
                }
            }
        }.toString()

        // 1. 外部儲存區持久化與滾動備份 (.bak)
        try {
            val extDir = getExternalFilesDir(null)
            if (extDir != null) {
                val file = File(extDir, "custom.txt")
                if (file.exists() && file.length() > 0) {
                    file.copyTo(File(extDir, "custom.txt.bak"), overwrite = true)
                }
                file.writeText(jsonString)

                val extConfigFile = File(extDir, "config.txt")
                if (extConfigFile.exists() && extConfigFile.length() > 0) {
                    extConfigFile.copyTo(File(filesDir, "config.txt"), overwrite = true)
                    preferences.edit(commit = true) {
                        putString("config_txt_backup", extConfigFile.readText())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save external custom.txt error", e)
        }

        // 2. 內部私有儲存區持久化 (永不被外部快取清理，APK 更新絕對保留)
        try {
            val internalFile = File(filesDir, "custom.txt")
            if (internalFile.exists() && internalFile.length() > 0) {
                internalFile.copyTo(File(filesDir, "custom.txt.bak"), overwrite = true)
            }
            internalFile.writeText(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Save internal custom.txt error", e)
        }

        // 3. SharedPreferences 系統層備份 (雙保險)
        try {
            preferences.edit(commit = true) {
                putString("custom_channels_json_backup", jsonString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save prefs backup error", e)
        }
    }

    fun resetChannelNum() {
        channelNum = 0
        while (channel[channelNum].isHidden) {
            channelNum++
        }
    }

    fun showErrorMessage(message: String?) {
        if (!isShowErrorMessage) return
        if (!player.isPlaying) {
            errorMessageView.text = message
        }
    }

    override fun onDestroy() {
        player.release()
        webView?.also {
            findViewById<FrameLayout>(R.id.webViewContainer).removeView(it)
            it.destroy()
        }
        LocalWebServer.stop()
        super.onDestroy()
    }

    override fun onStop() {
        isStarted = false
        player.stop()
        webViewHelper?.stop(webView)
        saveSettings()
        super.onStop()
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
