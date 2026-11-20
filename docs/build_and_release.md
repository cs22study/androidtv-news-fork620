# 新聞直播 fork620 - 自動化打包與 APK 下載指引

## 1. 簡介與建置方式

為符合專案「零花費」與「隨時可復原」原則，本專案導入 GitHub Actions CI/CD 自動化建置流程 ([.github/workflows/build-apk.yml](file:///D:/志松資料/GAS程式AI運用小松資料區cs22study/新聞直播fork620/.github/workflows/build-apk.yml))。

每當有新程式碼推送至 `main` 分支或建立發布標籤（如 `v6.2.0-preview`），GitHub Actions 會自動在純淨的雲端 Ubuntu 虛擬環境中完成：
1. 配置 Java JDK 17 (Temurin) 與 Python 3.13 環境。
2. 授予 Gradle Wrapper 執行權限。
3. 編譯 Android 專案產生最新 Debug APK (`assembleDebug`)。
4. 將 APK 打包儲存至 GitHub Actions 構件 (Artifacts)。
5. 自動發布至 GitHub Releases 頁面，供手機與電視端一鍵點擊下載。

---

## 2. 下載連結

* **GitHub Releases 發布頁面**：  
  https://github.com/cs22study/androidtv-news-fork620/releases

* **直接下載 APK 檔案 (Direct Download)**：  
  https://github.com/cs22study/androidtv-news-fork620/releases/download/v6.2.0-preview/androidtv-news-fork620-debug.apk

* **GitHub Actions 構件下載**：  
  https://github.com/cs22study/androidtv-news-fork620/actions

---

## 3. 手機與電視盒安裝步驟

### 3.1 手機安裝測試
1. 在手機瀏覽器中開啟上述下載連結，下載 `androidtv-news-fork620-debug.apk`。
2. 下載完成後點擊安裝。
3. 若 Android 系統跳出安全性提醒（如「禁止安裝未知來源應用程式」），請進入設定允許瀏覽器或檔案管理員「安裝未知應用程式」。
4. 安裝完成後，在手機主畫面即可看到「新聞直播」圖示，開啟即進入橫向播放介面。
5. 點擊螢幕中央 1/3 區域即可呼叫全新的深色壓克力磨砂美化選單；點擊「🌐 遠端配置 (Web後台)」即可測試同網域管理功能。

### 3.2 電視盒 (Android TV) 安裝測試
1. 使用電腦或手機下載 APK 檔案存入 USB 隨身碟，插入電視盒後使用檔案管理器點擊安裝。
2. 或在電視盒瀏覽器中直接輸入短網址下載安裝。
3. 安裝後即可使用電視遙控器上下鍵切換頻道、數字鍵直接選台、OK 鍵開啟左側美化選單。
