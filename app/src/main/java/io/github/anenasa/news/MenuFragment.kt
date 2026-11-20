package io.github.anenasa.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class MenuFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val button = view.findViewById<Button>(R.id.channel_list)
        button?.requestFocus()

        // 點擊側邊欄外部半透明遮罩時關閉選單
        view.findViewById<View>(R.id.menu_root)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 側邊欄內部點擊不觸發遮罩關閉
        view.findViewById<View>(R.id.sidebar_container)?.setOnClickListener {
            // 吸收事件，不向下穿透
        }

        // 顯示目前 LocalWebServer 運作位址
        val ipTextView = view.findViewById<TextView>(R.id.menu_web_ip)
        if (LocalWebServer.isRunning) {
            ipTextView?.text = "🌐 區網後台: ${LocalWebServer.serverUrl}"
        } else {
            ipTextView?.text = "🌐 遠端配置 (點擊上方按鈕啟動)"
        }
    }
}
