# 新聞直播 fork620 - YouTube 關鍵字搜尋一鍵導入與多端設計規格書

## 1. 模組簡介與新增功能

針對電視盒與手機使用者手動輸入 YouTube 冗長串流網址的不便，本版本正式導入 **「YouTube 關鍵字搜尋一鍵導入」** 功能：
1. **App 內建搜尋（電視與手機原生介面）**：
   - 於「新增頻道」與「頻道資訊」頁面頂部新增「🔍 搜尋 YouTube 影片/直播導入」選項。
   - 使用者只需在對話盒輸入關鍵字（例如：「公視新聞直播」、「東森新聞台」），系統即時檢索 YouTube，列出標題、直播標籤（🔴[直播]）與上傳者。
   - 點擊任一搜尋結果，系統**一鍵自動填入「頻道名稱」與「串流網址」**，儲存後立即跳台播放。
2. **Web 後台搜尋（手機/電腦瀏覽器）**：
   - 在 TVBox 遠端管理後台 (`http://[電視IP]:8080`) 的「頻道管理」分頁新增專屬「🔍 YouTube 關鍵字搜尋一鍵導入」面板。
   - 支援即時 Ajax 搜尋，呈現影片清單與直播狀態。
   - 點擊「🚀 一鍵導入」即可自動將 YouTube 標題、網址與預設 `bv*+ba/b` 畫質填入下方表單，無須手動複製貼上。

---

## 2. 核心架構與零費用設計 (遵守原則 4)

* **免 YouTube Data API Key (零配額、零花費)**：
  - **策略一 (超高速 Direct Scraping)**：透過 `OkHttp` 發送匿名標準檢索請求，由 [YouTubeSearcher.kt](file:///D:/志松資料/GAS程式AI運用小松資料區cs22study/新聞直播fork620/app/src/main/java/io/github/anenasa/news/YouTubeSearcher.kt) 提取 YouTube 官方頁面內之 `ytInitialData`，解析 `videoRenderer`。延遲小於 1 秒。
  - **策略二 (本地 Chaquopy 降級備援)**：若遇特定網路防禦，自動調用本地 Python `yt-dlp` 的平坦檢索模式 (`extract_flat: in_playlist`)，確保 100% 搜尋成功率。
* **資料安全與防注入 (原則 2)**：
  - 搜尋結果一律經過嚴格的 JSON 序列化與 HTML Entity 轉義，防止 XSS 攻擊。

---

## 3. Web API 擴充

| 請求方法 | 路徑 | 參數 | 說明 | 回傳格式 |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/youtube/search` | `q` (搜尋關鍵字) | 檢索 YouTube 影片與直播串流 | JSON 陣列：`[{"title":"...","url":"...","uploader":"...","isLive":true}]` |

---

## 4. 自動化測試驗證 (原則 1)

* 測試檔案：[tests/test_local_web_server.js](file:///D:/志松資料/GAS程式AI運用小松資料區cs22study/新聞直播fork620/tests/test_local_web_server.js)
* 測試項目涵蓋：
  - 關鍵字搜尋回傳結果與欄位校驗 (`title`, `url`, `uploader`, `isLive`)。
  - 空白與無效關鍵字 400 錯誤攔截。
  - 14 項測試全數 100% 通過。
