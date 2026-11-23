# 新聞直播 fork620 - 多架構相容安裝檔與下載指引 (v6.2.2)

## 1. 設備相容性與專屬版本對照表

本專案支援全系列 Android 設備，涵蓋舊版 Android 7.1.1 手機、現代 Android 12~15 旗艦機、Google TV / 小米盒子電視盒，以及 PC 電腦模擬器。

請依據您的設備型號選擇下載最適版本：

| 設備類型 | 代表設備 | 系統版本 / 硬體架構 | 推薦下載檔案 | 下載連結 | 說明與優勢 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 🌟 **全設備通用 (首選推薦)** | 小米盒子 S、各廠牌手機、平板、電腦模擬器 | Android 6.0 ~ 15+ (雙架構) | **通用正式版 (Universal)** | [androidtv-news-fork620-universal-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.2/androidtv-news-fork620-universal-release.apk) | 內含 32/64 位元完整庫，相容度 100%，不確定硬體選此版本 |
| 📱 **舊款手機 / 32位元電視盒** | **OPPO R11s Plus**、小米盒子 S (32位元OS) | Android 7.1.1 (ColorOS 3.2, 32-bit ARM) | **32位元相容版 (armeabi-v7a)** | [androidtv-news-fork620-armeabi-v7a-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.2/androidtv-news-fork620-armeabi-v7a-release.apk) | 專為舊版系統與 ColorOS 安全機制校驗設計，體積更精簡 |
| 🚀 **新版旗艦手機** | **OPPO 新版手機**、各品牌 64 位元電視盒 | Android 12 ~ 15 (ARM64) | **64位元高速版 (arm64-v8a)** | [androidtv-news-fork620-arm64-v8a-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.2/androidtv-news-fork620-arm64-v8a-release.apk) | 純 64 位元編譯，充分發揮現代處理器與 GPU 效能 |
| 💻 **電腦 Android 模擬器** | 雷電 / 夜神 / BlueStacks / MuMu | Windows PC (x86/x64) | **通用正式版 (Universal)** | [androidtv-news-fork620-universal-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.2/androidtv-news-fork620-universal-release.apk) | 支援模擬器內部 ARM 二進制翻譯器，順暢運作 |

---

## 2. 舊版覆蓋安裝失敗原因與永久修復機制

### 2.1 失敗根本原因：
1. **簽名金鑰不一致 (INSTALL_FAILED_UPDATE_INCOMPATIBLE)**：
   - 先前 CI 自動編譯腳本中，每次建置皆在 runner 內部執行 `keytool -genkeypair` 動態生成金鑰。
   - 儘管別名與密碼相同，但底層隨機生成的 RSA 密鑰對與 SHA256 憑證指紋完全不同。
   - Android Package Manager 基於安全機制，嚴格禁止相同 Package Name (`io.github.anenasa.news`) 的 APK 由不同憑證覆蓋安裝，因而引發「未完成安裝」錯誤。
2. **版本號碼需遞增**：
   - 原先版本維持在 `versionCode = 60200`，部分系統對同版號 APK 不予覆蓋。

### 2.2 永久解決方案：
1. **建立並固化專屬金鑰 (`keystore.jks`)**：
   - 產生永久持久金鑰並納入版本控管，確保未來每一次本機編譯或 GitHub Actions 產出皆共用同一憑證指紋。
2. **升級版本號**：
   - 將 `versionCode` 升至 `60220`，`versionName` 升至 `6.2.2`。
3. **過渡期操作提醒**：
   - 若設備上曾安裝過前一版暫存 APK，需先手動解除安裝一次舊版，接著安裝本次正式產出的 `v6.2.2`。
   - **從 v6.2.2 開始，未來所有後續版本均可直接點擊覆蓋升級，無須再解除安裝！**

---

## 3. GitHub 發布頁面

* **GitHub Releases 總覽**：  
  https://github.com/cs22study/androidtv-news-fork620/releases/tag/v6.2.2
