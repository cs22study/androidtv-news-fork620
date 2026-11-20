# 新聞直播 fork620 - TVBox 區網 Web 後台與選單美編設計規格書

## 1. 模組概述與需求背景

為大幅提升電視盒 (Android TV / OTT Box) 的使用體驗，本模組落實了兩大關鍵改進：
1. **TVBox 風格區網遠端管理後台 (Local Web Admin Server)**：
   - 解決電視遙控器輸入文字極其痛苦的痛點。
   - 在電視裝置上建立零外部依賴、純 Kotlin/Java `ServerSocket` 的輕量 HTTP 伺服器（預設通訊埠 `8080`，支援自動候選備援）。
   - 使用者只需在同一個 Wi-Fi 網域下的手機或電腦瀏覽器輸入 `http://[電視IP]:8080`，即可：
     * 快速貼上頻道名稱、串流網址 (YouTube/M3U8) 並一鍵推播至電視立即播放。
     * 上傳本機檔案或線上編輯頻道清單 (`config.txt`)，並支援即時 JSON 語法檢查與自動備份。
     * 上傳或貼上 Netscape 格式之 Cookies 授權檔 (`cookies.txt`)。
     * 提供手機觸控虛擬遙控器（支援方向鍵、OK、返回、選單、音量調整與觸覺震動回饋）。
2. **左側選單美化與字體比例調校 (Left-side Menu UI Beautification)**：
   - 原作者選單字體過大（`30sp`）、按鈕邊界粗暴（`#333333` 直角矩形），缺乏現代電視應用質感。
   - 改進為現代電視深色壓克力磨砂玻璃抽屜式設計（Acrylic Glassmorphic Drawer）。
   - 字體下調至黃金視覺比例 **`16sp`**，搭配圓角按鈕（Radius `8dp`）、高對比遙控焦點光暈（Vibrant Focus Glow `#1976D2`）與精緻圖標。
   - 增加遮罩防誤觸與點擊遮罩自動收合，底部整合區網後台即時 IP 狀態顯示。

---

## 2. 架構與資安設計 (遵守 5 大開發原則)

### 2.1 零花費與純本機伺服器 (原則 4)
- 不依賴任何雲端付費 PaaS 或外部伺服器。
- 純粹利用 Android 內部標準網路庫 `java.net.ServerSocket` 與協程 (`kotlinx.coroutines`) 處理 HTTP/1.1 協定。
- 前端 Web 介面為完全自給自足的單一 HTML5/CSS/JS 資源，無任何外部 CDN (如 Bootstrap, Tailwind, Vue) 依賴，保證在完全斷網的純區域網路環境下 100% 正常工作。

### 2.2 資安防護與 XSS 預防 (原則 2)
- **私有 IP 白名單過濾**：自動檢查 Client 端來源 IP，只允許 `127.0.0.1`、`192.168.*.*`、`10.*.*.*`、`172.16-31.*.*` 私有網段連線，外部公網 IP (WAN) 一律回傳 `403 Forbidden`。
- **XSS 防護**：前端呈現頻道資訊時一律採用 `escapeHtml` 處理特殊字元 (`&`, `<`, `>`, `"`, `'`)，杜絕 Stored XSS 漏洞。
- **路徑走訪防禦 (Path Traversal)**：檔案存取嚴格受限於 `context.getExternalFilesDir(null)` 目錄下，不接受自訂路徑走訪參數。

### 2.3 隨時可復原 (原則 3)
- **設定檔自動備份**：每當透過 Web 後台覆寫 `config.txt` 時，系統自動產生 `config.txt.bak` 副本，若有異常可立即還原。
- **軟刪除機制**：頻道提供 `isHidden: true` 隱藏機制，而非物理破壞性移除。

### 2.4 自動化測試驗證 (原則 1)
- 建立專屬自動化測試套件 [tests/test_local_web_server.js](file:///D:/志松資料/GAS程式AI運用小松資料區cs22study/新聞直播fork620/tests/test_local_web_server.js)。
- 涵蓋 IP 過濾、XSS 轉義、JSON 驗證、軟刪除邏輯及 Mock HTTP 請求測試，通過率 100%。

---

## 3. Web API 路由規格

| 請求方法 | 路徑 | 說明 |
| :--- | :--- | :--- |
| `GET` | `/` 或 `/index.html` | 回傳 TVBox 遠端管理後台完整 HTML 頁面 |
| `GET` | `/api/status` | 查詢系統狀態 (IP, Port, App 版本, 頻道總數) |
| `GET` | `/api/channels` | 取得目前電視上載入的完整頻道清單與狀態 |
| `POST` | `/api/channel/add` | 快速新增自訂頻道並即時切換至該台 |
| `POST` | `/api/channel/play` | 依 `index` 指令切換電視播放頻道 |
| `POST` | `/api/channel/toggle_hidden` | 切換頻道軟隱藏/顯示狀態 |
| `GET` | `/api/config` | 讀取目前的 `config.txt` 原始 JSON |
| `POST` | `/api/config` | 寫入新 `config.txt`，驗證格式並自動備份，通知電視熱重載 |
| `GET` | `/api/cookies` | 讀取目前的 `cookies.txt` |
| `POST` | `/api/cookies` | 寫入新的 `cookies.txt` 並即時更新 CookieJar |
| `GET` | `/api/remote?key={key}` | 發送遙控器按鍵指令 (`up`, `down`, `left`, `right`, `ok`, `back`, `menu`, `vol_up`, `vol_down`, `ch_up`, `ch_down`) |

---

## 4. UI/UX 改善細節

1. **視覺層次重塑**：
   - [app/src/main/res/values/colors.xml](file:///D:/志松資料/GAS程式AI運用小松資料區cs22study/新聞直播fork620/app/src/main/res/values/colors.xml)：加入科技感深藍暗色調色板。
   - [app/src/main/res/drawable/menu_background.xml](file:///D:/志松資料/GAS程式AI運用小松資料区cs22study/新聞直播fork620/app/src/main/res/drawable/menu_background.xml)：抽屜式右側圓角與邊框。
   - [app/src/main/res/drawable/menu_button.xml](file:///D:/志松資料/GAS程式AI運用小松資料區cs22study/新聞直播fork620/app/src/main/res/drawable/menu_button.xml)：現代感電視焦點漸層發光按鍵。
2. **字體縮放**：
   - 選單字體由原始 `30sp` 調整至 **`16sp`**。
   - 增加圖標表情符號輔助視覺識別：📺 頻道總表、ℹ️ 頻道資訊、➕ 新增頻道、🌐 遠端配置 (Web後台)、⚙️ 系統設定、🔢 前往頻道號碼。
3. **電視端提示對話盒**：
   - 按下「🌐 遠端配置」時，電視跳出大字號 IP 提示框 (`http://192.168.x.x:8080`)，遙控器點擊「確定」後對話盒自動關閉且伺服器持續在背景運作，方便使用者手機操作。
