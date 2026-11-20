# 新聞直播 fork620 - 多架構相容安裝檔與下載指引

## 1. 設備相容性與專屬版本對照表

本專案支援全系列 Android 設備，涵蓋舊版 Android 7.1.1 手機、現代 Android 12~15 旗艦機、Google TV / 小米盒子電視盒，以及 PC 電腦模擬器。

請依據您的設備型號選擇下載最適版本：

| 設備類型 | 代表設備 | 系統版本 / 硬體架構 | 推薦下載檔案 | 下載連結 | 說明與優勢 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 🌟 **全設備通用 (首選推薦)** | 小米盒子 S、各廠牌手機、平板、電腦模擬器 | Android 6.0 ~ 15+ (雙架構) | **通用正式版 (Universal)** | [androidtv-news-fork620-universal-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.0-preview/androidtv-news-fork620-universal-release.apk) | 內含 32/64 位元完整庫，相容度 100%，不確定硬體選此版本 |
| 📱 **舊款手機 / 32位元電視盒** | **OPPO R11s Plus**、小米盒子 S (32位元OS) | Android 7.1.1 (ColorOS 3.2, 32-bit ARM) | **32位元相容版 (armeabi-v7a)** | [androidtv-news-fork620-armeabi-v7a-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.0-preview/androidtv-news-fork620-armeabi-v7a-release.apk) | 專為舊版系統與 ColorOS 安全機制校驗設計，體積更精簡 |
| 🚀 **新版旗艦手機** | **OPPO 新版手機**、各品牌 64 位元電視盒 | Android 12 ~ 15 (ARM64) | **64位元高速版 (arm64-v8a)** | [androidtv-news-fork620-arm64-v8a-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.0-preview/androidtv-news-fork620-arm64-v8a-release.apk) | 純 64 位元編譯，充分發揮現代處理器與 GPU 效能 |
| 💻 **電腦 Android 模擬器** | 雷電 / 夜神 / BlueStacks / MuMu | Windows PC (x86/x64) | **通用正式版 (Universal)** | [androidtv-news-fork620-universal-release.apk](https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.0-preview/androidtv-news-fork620-universal-release.apk) | 支援模擬器內部 ARM 二進制翻譯器，順暢運作 |

---

## 2. OPPO R11s Plus (Android 7.1.1) 安裝失敗原因解析與修復方案

### 2.1 失敗主因：
1. **ColorOS 安全中心攔截除錯版 (Debuggable)**：原先產出的 APK 帶有 `debuggable=true` 標記與 `CN=Android Debug` 預設簽名。OPPO ColorOS 會強制阻擋非開發者除錯應用程式安裝，顯示「解析套件錯誤」或「未安裝應用程式」。
2. **簽名規範缺失**：Android 7.0/7.1 引入 V2 簽名，部分舊款 ROM 的安裝器對純 V2 簽名解析相容不良，必須同時存在 **V1 (JAR Signature)**。

### 2.2 修復對策：
1. **全面切換為 Release 正式簽名**：產生正式 2048 位元 RSA 憑證，並強制開啟 `enableV1Signing = true` 與 `enableV2Signing = true`。
2. **架構分包 (APK Splits)**：分離 `armeabi-v7a` 與 `arm64-v8a`，並產出全相容之 Universal FAT APK。
3. **關閉 R8 破壞性混淆**：確保舊版 Android 7.1.1 ART 虛擬機載入穩定無礙。

---

## 3. GitHub 發布頁面

* **GitHub Releases 總覽**：  
  https://github.com/cs22study/androidtv-news-fork620/releases
