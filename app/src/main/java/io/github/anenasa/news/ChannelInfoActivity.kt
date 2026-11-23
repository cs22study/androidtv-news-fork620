package io.github.anenasa.news

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelInfoActivity : AppCompatActivity() {
    var fragment: ChannelInfoFragment = ChannelInfoFragment()
    var isNewChannel: Boolean = false
    var channelIndex: Int = 0
    var defaultUrl: String = ""
    var defaultName: String = ""
    var defaultFormat: String = ""
    var defaultVolume: Float = 1f
    var defaultHeader: String = ""
    var customUrl: String = ""
    var customName: String = ""
    var customFormat: String = ""
    var customVolume: String = ""
    var customHeader: String = ""
    var channelIsHidden: Boolean = false
    var channelWidth: Int = 0
    var channelHeight: Int = 0
    var removeCache: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentExtras = intent.extras ?: run {
            Toast.makeText(this, "Error: intentExtras is null", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error: intentExtras is null")
            finish()
            return
        }
        isNewChannel = intentExtras.getBoolean("isNewChannel")
        channelIndex = intentExtras.getInt("index")
        defaultUrl = intentExtras.getString("defaultUrl").orEmpty()
        defaultName = intentExtras.getString("defaultName").orEmpty()
        defaultFormat = intentExtras.getString("defaultFormat").orEmpty()
        defaultVolume = intentExtras.getFloat("defaultVolume")
        defaultHeader = intentExtras.getString("defaultHeader").orEmpty()
        customUrl = intentExtras.getString("customUrl").orEmpty()
        customName = intentExtras.getString("customName").orEmpty()
        customFormat = intentExtras.getString("customFormat").orEmpty()
        customVolume = intentExtras.getString("customVolume").orEmpty()
        customHeader = intentExtras.getString("customHeader").orEmpty()
        channelIsHidden = intentExtras.getBoolean("isHidden")
        channelWidth = intentExtras.getInt("width")
        channelHeight = intentExtras.getInt("height")

        setContentView(R.layout.activity_channel_info_container)
        findViewById<TextView>(R.id.tvTitle)?.text = if (isNewChannel) "➕ 新增頻道" else "ℹ️ 頻道資訊與設定"
        findViewById<Button>(R.id.btnBack)?.setOnClickListener {
            if (isNewChannel && customUrl.isEmpty() && customName.isEmpty()) {
                setResult(RESULT_CANCELED)
                finish()
            } else {
                saveAndFinish()
            }
        }
        findViewById<Button>(R.id.btnSave)?.setOnClickListener {
            if (isNewChannel && customUrl.isEmpty() && customName.isEmpty()) {
                Toast.makeText(this, "請先輸入頻道網址或透過 YouTube 搜尋導入！", Toast.LENGTH_SHORT).show()
            } else {
                saveAndFinish()
            }
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.channel_info_container, fragment)
            .commit()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isNewChannel && customUrl.isEmpty() && customName.isEmpty()) {
                    setResult(RESULT_CANCELED)
                    finish()
                } else {
                    saveAndFinish()
                }
            }
        })
    }

    private fun saveAndFinish() {
        val intent = Intent().apply {
            putExtra("customName", customName)
            putExtra("isHidden", channelIsHidden)
            putExtra("customUrl", customUrl)
            putExtra("customFormat", customFormat)
            putExtra("customVolume", customVolume)
            putExtra("customHeader", customHeader)
            putExtra("remove_cache", removeCache)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    class ChannelInfoFragment : PreferenceFragmentCompat() {
        val myActivity: ChannelInfoActivity by lazy { requireActivity() as ChannelInfoActivity }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.activity_channel_info, rootKey)

            findPreference<Preference?>("search_youtube")?.apply {
                setOnPreferenceClickListener {
                    showYouTubeSearchDialog()
                    true
                }
            }

            findPreference<EditTextPreference?>("name")?.apply {
                if (myActivity.customName.isEmpty()) {
                    setSummary(myActivity.defaultName)
                    setText(myActivity.defaultName)
                } else {
                    setSummary(myActivity.customName)
                    setText(myActivity.customName)
                }
                setOnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                    myActivity.customName = newValue.toString()
                    if (newValue.toString().isEmpty()) {
                        preference?.setSummary(myActivity.defaultName)
                    } else {
                        preference?.setSummary(newValue.toString())
                    }
                    true
                }
            }

            findPreference<SwitchPreferenceCompat?>("isHidden")?.apply {
                setChecked(myActivity.channelIsHidden)
                setOnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                    myActivity.channelIsHidden = newValue as Boolean
                    true
                }
            }

            findPreference<Preference?>("number")?.apply {
                setSummary(myActivity.channelIndex.toString())
            }

            findPreference<EditTextPreference?>("url")?.apply {
                if (myActivity.customUrl.isEmpty()) {
                    setSummary(myActivity.defaultUrl)
                    setText(myActivity.defaultUrl)
                } else {
                    setSummary(myActivity.customUrl)
                    setText(myActivity.customUrl)
                }
                setOnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                    myActivity.customUrl = newValue.toString()
                    if (newValue.toString().isEmpty()) {
                        preference?.setSummary(myActivity.defaultUrl)
                    } else {
                        preference?.setSummary(newValue.toString())
                    }
                    true
                }
            }

            findPreference<EditTextPreference?>("format")?.apply {
                if (myActivity.customFormat.isEmpty()) {
                    setSummary(myActivity.defaultFormat)
                    setText(myActivity.defaultFormat)
                } else {
                    setSummary(myActivity.customFormat)
                    setText(myActivity.customFormat)
                }
                setOnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                    myActivity.customFormat = newValue.toString()
                    if (newValue.toString().isEmpty()) {
                        preference?.setSummary(myActivity.defaultFormat)
                    } else {
                        preference?.setSummary(newValue.toString())
                    }
                    true
                }
            }

            findPreference<EditTextPreference?>("volume")?.apply {
                if (myActivity.customVolume.isEmpty()) {
                    setSummary(myActivity.defaultVolume.toString())
                    setText(myActivity.defaultVolume.toString())
                } else {
                    setSummary(myActivity.customVolume)
                    setText(myActivity.customVolume)
                }
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
                setOnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                    myActivity.customVolume = newValue.toString()
                    if (newValue.toString().isEmpty()) {
                        preference?.setSummary(myActivity.defaultVolume.toString())
                    } else {
                        preference?.setSummary(newValue.toString())
                    }
                    true
                }
            }

            findPreference<Preference?>("size")?.apply {
                setSummary("${myActivity.channelWidth}×${myActivity.channelHeight}")
            }

            findPreference<EditTextPreference?>("header")?.apply {
                if (myActivity.customHeader.isEmpty()) {
                    setSummary(myActivity.defaultHeader)
                    setText(myActivity.defaultHeader)
                } else {
                    setSummary(myActivity.customHeader)
                    setText(myActivity.customHeader)
                }
                setOnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                    myActivity.customHeader = newValue.toString()
                    if (newValue.toString().isEmpty()) {
                        preference?.setSummary(myActivity.defaultHeader)
                    } else {
                        preference?.setSummary(newValue.toString())
                    }
                    true
                }
            }

            findPreference<Preference?>("remove_cache")?.apply {
                setOnPreferenceClickListener { preference: Preference? ->
                    myActivity.removeCache = true
                    preference?.setSummary("將移除快取")
                    true
                }
            }

            findPreference<Preference?>("delete")?.apply {
                isVisible = myActivity.isNewChannel
                setOnPreferenceClickListener { _: Preference? ->
                    val builder = AlertDialog.Builder(activity)
                        .setTitle("刪除")
                        .setMessage("確定要刪除嗎？")
                        .setPositiveButton("確定") { _: DialogInterface?, _: Int ->
                            val returnIntent = Intent()
                            returnIntent.putExtra("delete", true)
                            myActivity.setResult(RESULT_OK, returnIntent)
                            myActivity.finish()
                        }
                        .setNegativeButton("取消", null)
                    val alertDialog = builder.create()
                    alertDialog.show()
                    true
                }
            }
        }

        private fun showYouTubeSearchDialog() {
            val ctx = requireContext()
            val history = YouTubeSearchHistory.getHistory(ctx)

            val rootLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 16)
            }

            val editText = EditText(ctx).apply {
                hint = "例如：機場直播、流行音樂、穿越短劇"
                setSingleLine()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rootLayout.addView(editText)

            var dialogInstance: AlertDialog? = null

            if (history.isNotEmpty()) {
                val historyHeader = TextView(ctx).apply {
                    text = "📜 歷史查詢 (點擊直接填入搜尋)："
                    textSize = 13f
                    setTextColor(Color.parseColor("#A0AEC0"))
                    setPadding(0, 20, 0, 8)
                }
                rootLayout.addView(historyHeader)

                val horizontalScrollView = HorizontalScrollView(ctx).apply {
                    isHorizontalScrollBarEnabled = false
                }
                val chipGroup = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                history.take(15).forEach { kw ->
                    val chipBtn = Button(ctx).apply {
                        text = kw
                        textSize = 13f
                        isAllCaps = false
                        setPadding(20, 6, 20, 6)
                        setBackgroundResource(R.drawable.menu_button)
                        setTextColor(Color.WHITE)
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 10, 0)
                        }
                        layoutParams = lp
                        setOnClickListener {
                            editText.setText(kw)
                            editText.setSelection(kw.length)
                            YouTubeSearchHistory.addKeyword(ctx, kw)
                            dialogInstance?.dismiss()
                            performYouTubeSearch(kw)
                        }
                    }
                    chipGroup.addView(chipBtn)
                }
                horizontalScrollView.addView(chipGroup)
                rootLayout.addView(horizontalScrollView)
            }

            dialogInstance = AlertDialog.Builder(ctx)
                .setTitle("🔍 搜尋 YouTube 影片/直播")
                .setView(rootLayout)
                .setPositiveButton("搜尋") { _, _ ->
                    val query = editText.text.toString().trim()
                    if (query.isNotEmpty()) {
                        YouTubeSearchHistory.addKeyword(ctx, query)
                        performYouTubeSearch(query)
                    }
                }
                .setNegativeButton("取消", null)
                .create()

            dialogInstance.show()
        }

        private fun performYouTubeSearch(query: String) {
            val ctx = requireContext()
            YouTubeSearchHistory.addKeyword(ctx, query)

            val progressDialog = AlertDialog.Builder(ctx)
                .setTitle("正在搜尋 YouTube")
                .setMessage("正在檢索「$query」，請稍候...")
                .setCancelable(false)
                .create()
            progressDialog.show()

            lifecycleScope.launch(Dispatchers.IO) {
                val results = YouTubeSearcher.search(query, 8)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    if (results.isEmpty()) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("未找到結果")
                            .setMessage("未搜尋到與「$query」相關的 YouTube 影片，請嘗試其他關鍵字。")
                            .setPositiveButton("確定", null)
                            .show()
                        return@withContext
                    }

                    val displayItems = results.map { res ->
                        val badge = if (res.isLive) "🔴[直播] " else "🎬 "
                        val uploaderStr = if (res.uploader.isNotEmpty()) " - ${res.uploader}" else ""
                        "$badge${res.title}$uploaderStr"
                    }.toTypedArray()

                    AlertDialog.Builder(requireContext())
                        .setTitle("選擇要導入的頻道 (${results.size} 個結果)")
                        .setItems(displayItems) { _, which ->
                            val selected = results[which]
                            myActivity.customName = selected.title
                            myActivity.customUrl = selected.url
                            myActivity.removeCache = true

                            findPreference<EditTextPreference?>("name")?.apply {
                                text = selected.title
                                summary = selected.title
                            }
                            findPreference<EditTextPreference?>("url")?.apply {
                                text = selected.url
                                summary = selected.url
                            }

                            Toast.makeText(requireContext(), "已成功導入：「${selected.title}」！", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }
    }

    companion object {
        const val TAG = "ChannelInfoActivity"
    }
}
