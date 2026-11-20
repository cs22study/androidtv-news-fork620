# 新聞直播 fork620

在 Android 系統電視與手機上透過網路觀看新聞直播（Hub-and-Spoke 強化版）。

[![Build & Release Android APK](https://github.com/cs22study/androidtv-news-fork620/actions/workflows/build-apk.yml/badge.svg)](https://github.com/cs22study/androidtv-news-fork620/actions/workflows/build-apk.yml)

## 📱 多設備專屬 APK 下載

👉 **[點此前往 GitHub Releases 下載各設備專用 APK](https://github.com/cs22study/androidtv-news-fork620/releases)**

| 適用設備 | 推薦下載檔案 | 下載連結 |
| :--- | :--- | :--- |
| 🌟 **全設備通用 (首選推薦)** | 通用版 (Universal) | [androidtv-news-fork620-universal-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.0-preview/androidtv-news-fork620-universal-release.apk) |
| 📱 **OPPO R11s Plus (Android 7.1.1) / 32位元小米盒子** | 32位元相容版 (armeabi-v7a) | [androidtv-news-fork620-armeabi-v7a-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.0-preview/androidtv-news-fork620-armeabi-v7a-release.apk) |
| 🚀 **OPPO 新版手機 (Android 12~15) / 64位元電視** | 64位元高速版 (arm64-v8a) | [androidtv-news-fork620-arm64-v8a-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.0-preview/androidtv-news-fork620-arm64-v8a-release.apk) |
| 💻 **電腦 Android 模擬器 (雷電/夜神/BlueStacks)** | 通用版 (Universal) | [androidtv-news-fork620-universal-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.0-preview/androidtv-news-fork620-universal-release.apk) |

## ✨ fork620 新增亮點特色

1. **🌐 TVBox 風格區網遠端管理後台 (Local Web Admin)**：
   - 電視端免痛苦輸入文字：手機或電腦瀏覽器輸入 `http://[電視IP]:8080` 即可設定。
   - 手機直接貼上頻道網址與名稱，一鍵推播至電視立即播放。
   - 上傳與線上編輯頻道清單 (`config.txt`)，具備語法防呆與自動備份。
   - 內建手機觸控虛擬遙控器（支援方向鍵、OK、返回、選單與音量調校）。
2. **🎨 現代化左側選單美化 (UI Beautification)**：
   - 修正原作者字體過大問題（由 `30sp` 縮放至舒適黃金比例 `16sp`）。
   - 抽屜式深色磨砂壓克力玻璃風格邊欄，搭配高對比遙控焦點光暈。
   - 支援側邊欄外半透明遮罩觸控收合。
3. **🔍 YouTube 關鍵字搜尋一鍵導入 (新功能！)**：
   - App 原生「新增頻道/頻道資訊」頁面新增 YouTube 關鍵字搜尋，一鍵自動填寫頻道名稱與串流網址。
   - 遠端 Web 管理後台同步支援即時 YouTube 影片與直播檢索，一鍵填入表單並推播至電視。
4. **📺 強大串流解密核心**：
   - 使用 [yt-dlp](https://github.com/yt-dlp/yt-dlp) + QuickJS 破解 YouTube 簽名，無須安裝 YouTube 應用程式。
   - 內建台灣四大正版串流專屬爬蟲（四季線上 4gtv、民視新聞 FTV、Hami Video、Ofiii 歐飛）。

## 下載（原作者版本）

https://github.com/anenasa/androidtv-news/releases

新聞直播 6.2.0 版不再支援 Android 5.1 以下系統

## 特色

使用 [yt-dlp](https://github.com/yt-dlp/yt-dlp) 取得影片網址，無須安裝 Youtube 應用程式

使用遙控器轉台

可自訂頻道

## 用法

### 主畫面
1. 轉台：使用上/下鍵或頻道上/下鍵轉台
2. 選擇頻道：輸入數字後按 OK
3. 顯示選單：按 OK，選單中可選擇顯示頻道表、頻道資訊、新增頻道和設定

### 頻道資訊

可顯示頻道的一些資訊，也能修改頻道的名稱、網址、yt-dlp 格式、音量和 header，這裡的設定會覆蓋頻道清單檔案裡的設定。如果要回復到頻道清單檔案的設定，只要把要回復的屬性清空並儲存即可。

## 預設頻道

預設頻道條件：
1. 來源必須合法
2. 台灣能看得到
3. 無須登入

如果有頻道建議歡迎提出。

## 自訂頻道

以 json 格式自訂頻道，可在設定畫面匯入頻道清單或選擇網址，可參考[預設頻道檔案](https://anenasa.github.io/channel/config.txt)。

### 自訂頻道檔案格式

頻道檔案如下（最後一個頻道後面不能加逗點，其他頻道都要）：

    {
      "channelList": [
        頻道1,
        頻道2,
        ...
      ]
    }

頻道格式（最後一個選項後面不能加逗點，其他選項都要）：

    {
      "url": "頻道網址",
      "name": "頻道名稱",
      "ytdl-format": "yt-dlp 格式（可省略）",
      "volume": 音量（可省略）,
      "header": "name: value（可省略）",
      "ytdl-options": JSON 物件（可省略）
    }

yt-dlp 格式請參考[這裡](https://github.com/yt-dlp/yt-dlp/blob/master/README.md#format-selection)。預設格式為bv*+ba/b

如果要設定多個 header 可使用 \r\n 分隔（因為反斜線在 json 格式中是特殊字元，所以要用 \\\\r\\\\n 分隔）

ytdl-options 請參考[這裡](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/YoutubeDL.py#L230)。目前只支援字串的選項，如果你想使用的選項不支援歡迎回報。

如果要使用 [WebView 模式](https://github.com/anenasa/androidtv-news/discussions/11)：

    {
      "url": "頻道網址",
      "name": "頻道名稱",
      "isWebView": true,
      "scriptOnFinish": "網頁載入完成時要執行的 javascript（可省略）"
      "header": "name: value（可省略）"
    }

javascript 中可使用
1. `prompt('tap:x,y')` 模擬點按螢幕，x 是距離左邊的百分比，y 是距離頂部的百分比
2. `prompt('fullscreen:element')` 將特定元素全螢幕顯示

頻道也可以是網路上的頻道清單檔案：

    {
      "list": "頻道清單檔案網址"
    }

## Cookies
可在設定畫面匯入，請參考 https://github.com/yt-dlp/yt-dlp/wiki/FAQ#how-do-i-pass-cookies-to-yt-dlp

## 許可證
[GNU General Public License v3.0](https://github.com/anenasa/androidtv-news/blob/main/LICENSE)
