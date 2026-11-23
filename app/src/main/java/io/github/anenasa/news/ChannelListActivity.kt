package io.github.anenasa.news

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class ChannelListActivity : Activity() {
    private val channelNumArray = ArrayList<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_list)
        val intentExtras = intent.extras ?: run {
            Toast.makeText(this, "Error: intentExtras is null", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error: intentExtras is null")
            finish()
            return
        }
        val channelArray: Array<String> = intentExtras.getStringArray("nameArray") ?: run {
            Toast.makeText(this, "Error: channelArray is null", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error: channelArray is null")
            finish()
            return
        }
        val isHiddenArray: BooleanArray = intentExtras.getBooleanArray("isHiddenArray") ?: booleanArrayOf()
        val currentNum = intentExtras.getInt("currentNum")
        val displayArray = ArrayList<String>()
        channelNumArray.clear()
        for (i in channelArray.indices) {
            if (!isHiddenArray[i]) {
                displayArray.add("$i. ${channelArray[i]}")
                channelNumArray.add(i)
            }
        }

        findViewById<Button>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
        findViewById<TextView>(R.id.tvTitle)?.text = "📺 頻道總表 (共 ${displayArray.size} 台)"

        findViewById<ListView>(R.id.channelListView)?.apply {
            itemsCanFocus = false
            val adapter = ArrayAdapter(this@ChannelListActivity, R.layout.mylistview, displayArray)
            setAdapter(adapter)
            val targetSelection = channelNumArray.indexOf(currentNum).coerceAtLeast(0)
            setSelection(targetSelection)
            requestFocus()

            setOnItemClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
                if (i in channelNumArray.indices) {
                    selectChannel(channelNumArray[i])
                }
            }
        }
    }

    private fun selectChannel(channelNum: Int) {
        val returnIntent = Intent().apply {
            putExtra("channelNum", channelNum)
        }
        setResult(RESULT_OK, returnIntent)
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    val listView = findViewById<ListView>(R.id.channelListView)
                    if (listView != null) {
                        val pos = listView.selectedItemPosition
                        if (pos != AdapterView.INVALID_POSITION && pos in channelNumArray.indices) {
                            selectChannel(channelNumArray[pos])
                            return true
                        }
                    }
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    val listView = findViewById<ListView>(R.id.channelListView) ?: return super.dispatchKeyEvent(event)
                    val firstVisible = listView.firstVisiblePosition
                    val lastVisible = listView.lastVisiblePosition
                    val pageSize = (lastVisible - firstVisible).coerceAtLeast(1)
                    val targetPosition = if (event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                        (firstVisible - pageSize).coerceAtLeast(0)
                    } else { // KEYCODE_CHANNEL_DOWN
                        (firstVisible + pageSize).coerceAtMost(listView.adapter.count - 1)
                    }
                    listView.setSelection(targetPosition)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val TAG = "ChannelListActivity"
    }
}
