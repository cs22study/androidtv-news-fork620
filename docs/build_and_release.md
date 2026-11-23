# 新聞直播 Fork620 - 多架構相容安裝檔與下載指引 (v6.2.4)

## 1. 設備相容性與專屬版本對照表

本專案支援全系列 Android 設備，涵蓋舊版 Android 7.1.1 手機、現代 Android 12~15 旗艦機、Google TV / 小米盒子電視盒，以及 PC 電腦模擬器。

**✨ 重要重大升級 (v6.2.4)**：本版本正式啟用專屬 Application ID（`io.github.cs22study.newsfork620`），與原作者版本完全隔離獨立，**兩者可同時安裝在同一台電視盒、手機或模擬器上並存運行，互不覆蓋、各自獨立檢查更新**！

請依據您的設備型號選擇下載最適版本：

| 設備類型 | 代表設備 | 系統版本 / 硬體架構 | 推薦下載檔案 | 下載連結 | 說明與優勢 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 🌟 **全設備通用 (首選推薦)** | 小米盒子 S、各廠牌手機、平板、電腦模擬器 | Android 6.0 ~ 15+ (雙架構) | **通用正式版 (Universal)** | [androidtv-news-fork620-universal-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.4/androidtv-news-fork620-universal-release.apk) | 內含 32/64 位元完整庫，相容度 100%，不確定硬體選此版本 |
| 📱 **舊款手機 / 32位元電視盒** | **OPPO R11s Plus**、小米盒子 S (32位元OS) | Android 7.1.1 (ColorOS 3.2, 32-bit ARM) | **32位元相容版 (armeabi-v7a)** | [androidtv-news-fork620-armeabi-v7a-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.4/androidtv-news-fork620-armeabi-v7a-release.apk) | 專為舊版系統與 ColorOS 安全機制校驗設計，體積更精簡 |
| 🚀 **新版旗艦手機** | **OPPO 新版手機**、各品牌 64 位元電視盒 | Android 12 ~ 15 (ARM64) | **64位元高速版 (arm64-v8a)** | [androidtv-news-fork620-arm64-v8a-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.4/androidtv-news-fork620-arm64-v8a-release.apk) | 純 64 位元編譯，充分發揮現代處理器與 GPU 效能 |
| 💻 **電腦 Android 模擬器** | 雷電 / 夜神 / BlueStacks / MuMu | Windows PC (x86/x64) | **通用正式版 (Universal)** | [androidtv-news-fork620-universal-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.4/androidtv-news-fork620-universal-release.apk) | 支援模擬器內部 ARM 二進制翻譯器，順暢運作 |

---

## 2. 原版與修改版雙軌並存架構與更新機制 (v6.2.4)

### 2.1 衝突原因剖析：
1. **原版與 Fork 版共用相同 Application ID (`io.github.anenasa.news`)**：
   - Android 系統視相同 Package Name 為同一應用。若電視盒上已裝原版（由原作者金鑰簽署），嘗試安裝修改版時，系統因簽名不匹配直接回傳 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，導致無法安裝。
2. **更新通道互串**：
   - 原版新聞直播 App 檢查原作者倉庫更新，若共用包名會造成版號混亂與更新失敗。

### 2.2 v6.2.4 雙軌並存解決方案：
1. **獨立 Application ID (`io.github.cs22study.newsfork620`)**：
   - 建立專屬 Package Name，Android 系統視為全新應用，**原版與修改版 100% 同時安裝並存**。
2. **專屬 APP 名稱「新聞直播 Fork620」**：
   - 桌面圖示名稱、左側選單與 Web 管理後台皆清楚標記為「新聞直播 Fork620」，視覺一目了然。
3. **專屬獨立更新通道**：
   - 啟動靜默更新檢查與設定手動檢查，全數指向 Fork620 官方倉庫 (`cs22study/androidtv-news-fork620`)。
4. **原版頻道無痛自動繼承**：
   - 首次啟動 Fork620 時，系統自動偵測並繼承原版在 `/sdcard/Android/data/io.github.anenasa.news/files/` 下的 `config.txt`、`custom.txt` 與搜尋歷史，辛苦建立的頻道一秒零痛轉移！

---

## 3. GitHub 發布頁面

* **GitHub Releases 總覽**：  
  https://github.com/cs22study/androidtv-news-fork620/releases/tag/v6.2.4

---

## 4. v6.2.3 重大技術調校紀錄 (直屏防變形與 YouTube 緩衝卡頓解決方案)

### 4.1 直屏短劇橫向拉伸變形問題：
- **原因**：原有播放視圖 `playerView` 設置為 `match_parent` 滿版且無監聽影片實際維度，導致 9:16 直屏影片被拉伸為 16:9 扁平狀。
- **解決方案**：
  1. 在 `MainActivity.kt` 實作 ExoPlayer `onVideoSizeChanged`，綜合考量 `unappliedRotationDegrees`（90°/270° 旋轉角）與 `pixelWidthHeightRatio` 精確計算長寬比。
  2. 預設模式（Mode 0: 保持比例 Fit）動態調整 `SurfaceView` LayoutParams 居中縮放，兩側保留電影級深邃黑邊 (`#000000`)。
  3. 提供三段式比例切換（Fit 等比居中 / Stretch 強制全螢幕 / Crop 滿版裁切），支援左側選單、遙控器及 Web 後台一鍵循環切換。

### 4.2 YouTube 流行音樂與點播影片 (VOD) 播放數秒後卡頓問題：
- **原因**：
  1. YouTube 對非直播影片會進行動態頻寬節流 (Throttling)，若 ExoPlayer 的緩衝策略過於保守，在耗盡初段 buffer 後需漫長累積數秒資料才會重啟播放，造成轉圈卡死。
  2. 影音分軌 (DASH) 使用 `MergingMediaSource` 合併時未開啟時間戳校準，導致音訊與視訊軌道時間差拉大造成解碼器管線堵塞。
  3. 預設畫質拉取過高（4K/2K）超出低階電視盒 GPU 解碼能力。
- **解決方案**：
  1. 深度調校 `DefaultLoadControl`：起播緩衝由 2.5 秒降至 1.0 秒以加速即時秒開；二次起播緩衝降至 2.5 秒，最大緩衝設定 60 秒，並啟用時間優先緩衝策略 (`prioritizeTimeOverSizeThresholds = true`)。
  2. `MergingMediaSource` 啟用 `adjustPeriodTimeOffsets = true` 與 `clipDurations = true`，自動修整音視軌時間差，消除同步停滯。
  3. `DefaultHttpDataSource` 設定 15 秒連線與 20 秒讀取寬容逾時，支援跨協定重定向並加入現代 Chrome Desktop User-Agent 標頭。
  4. 預設 YouTube 畫質限制為 `bv*[height<=1080]+ba/b`，確保在 1080p 最佳畫質與極致流暢度之間取得完美平衡。
  2. `androidtv-news-fork620-armeabi-v7a-release.apk` (22.30 MB)
  3. `androidtv-news-fork620-arm64-v8a-release.apk` (22.91 MB)
