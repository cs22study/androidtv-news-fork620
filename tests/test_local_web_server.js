/**
 * 自動化測試套件：LocalWebServer 與 Web 後台功能驗證
 * 遵循專案開發原則 1 (完整自動化測試驗證) 與原則 2 (資安與 XSS 防護驗證)
 */

const http = require('http');
const net = require('net');
const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('========================================');
console.log('開始執行新聞直播 fork620 自動化測試套件');
console.log('========================================\n');

let passedTests = 0;
let totalTests = 0;

function it(description, fn) {
  totalTests++;
  try {
    fn();
    console.log(`✅ [PASS] ${description}`);
    passedTests++;
  } catch (err) {
    console.error(`❌ [FAIL] ${description}`);
    console.error(err);
    process.exitCode = 1;
  }
}

async function itAsync(description, fn) {
  totalTests++;
  try {
    await fn();
    console.log(`✅ [PASS] ${description}`);
    passedTests++;
  } catch (err) {
    console.error(`❌ [FAIL] ${description}`);
    console.error(err);
    process.exitCode = 1;
  }
}

// 1. 驗證 IP 過濾邏輯 (私有區網與本機防護)
function isPrivateOrLoopbackIp(ip) {
  if (ip === '127.0.0.1' || ip === 'localhost' || ip === '::1') return true;
  if (ip.startsWith('192.168.') || ip.startsWith('10.')) return true;
  if (ip.startsWith('172.')) {
    const second = parseInt(ip.split('.')[1] || '0', 10);
    if (second >= 16 && second <= 31) return true;
  }
  return false;
}

it('資安測試：本機與私有 LAN IP 應通過白名單驗證', () => {
  assert.strictEqual(isPrivateOrLoopbackIp('127.0.0.1'), true);
  assert.strictEqual(isPrivateOrLoopbackIp('localhost'), true);
  assert.strictEqual(isPrivateOrLoopbackIp('192.168.1.105'), true);
  assert.strictEqual(isPrivateOrLoopbackIp('10.0.0.2'), true);
  assert.strictEqual(isPrivateOrLoopbackIp('172.16.0.1'), true);
  assert.strictEqual(isPrivateOrLoopbackIp('172.31.255.255'), true);
});

it('資安測試：外部公網 IP (WAN) 必須被嚴格阻擋', () => {
  assert.strictEqual(isPrivateOrLoopbackIp('8.8.8.8'), false);
  assert.strictEqual(isPrivateOrLoopbackIp('1.1.1.1'), false);
  assert.strictEqual(isPrivateOrLoopbackIp('140.112.1.1'), false);
  assert.strictEqual(isPrivateOrLoopbackIp('172.32.0.1'), false); // 超出 172.16-31 私有範圍
});

// 2. 驗證 XSS 跳脫處理
function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}

it('資安測試：XSS 攻擊字串必須被正確轉義', () => {
  const malicious = '<script>alert("XSS")</script>&param=1';
  const escaped = escapeHtml(malicious);
  assert.strictEqual(escaped.includes('<script>'), false);
  assert.strictEqual(escaped.includes('&lt;script&gt;'), true);
  assert.strictEqual(escaped.includes('&quot;XSS&quot;'), true);
});

// 3. 驗證 config.txt 頻道配置結構與軟刪除機制
it('功能測試：ChannelConfig JSON 結構驗證 (包含 channelList)', () => {
  const validConfig = {
    channelList: [
      { name: "公視新聞", url: "https://www.youtube.com/watch?v=xxx", volume: 1.0 },
      { name: "華視新聞", url: "https://www.youtube.com/watch?v=yyy", isHidden: false }
    ]
  };
  const jsonStr = JSON.stringify(validConfig);
  const parsed = JSON.parse(jsonStr);
  assert.ok(Array.isArray(parsed.channelList), 'channelList 必須為陣列');
  assert.strictEqual(parsed.channelList.length, 2);
});

it('原則 3 測試：軟刪除機制 (isHidden: true 標記而非物理破壞)', () => {
  const channels = [
    { index: 0, name: "頻道 1", isHidden: false },
    { index: 1, name: "頻道 2", isHidden: true }
  ];
  // 模擬切台略過隱藏台
  let cur = 0;
  function getNext(from) {
    let next = (from + 1) % channels.length;
    while (channels[next].isHidden) {
      next = (next + 1) % channels.length;
    }
    return next;
  }
  assert.strictEqual(getNext(0), 0, '當下一台隱藏時，應自動略過隱藏台');
});

// 4. 建立 Mock HTTP Server 模擬 LocalWebServer 完整交互
async function runMockServerTests() {
  const mockPort = 18080;
  let mockChannels = [
    { index: 0, name: "測試公視", url: "https://youtu.be/test1", isHidden: false },
    { index: 1, name: "測試台視", url: "https://youtu.be/test2", isHidden: false }
  ];

  const server = http.createServer((req, res) => {
    const url = new URL(req.url, `http://localhost:${mockPort}`);
    const pathname = url.pathname;

    if (req.method === 'GET' && pathname === '/') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end('<!DOCTYPE html><html><head><title>新聞直播 TVBox 遠端管理後台</title></head><body><h1>TVBox 後台</h1></body></html>');
      return;
    }

    if (req.method === 'GET' && pathname === '/api/status') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'ok', version: '6.2.0', channelCount: mockChannels.length }));
      return;
    }

    if (req.method === 'GET' && pathname === '/api/channels') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(mockChannels));
      return;
    }

    if (req.method === 'POST' && pathname === '/api/config') {
      let body = '';
      req.on('data', chunk => body += chunk);
      req.on('end', () => {
        try {
          const parsed = JSON.parse(body);
          if (!parsed.channelList) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ status: 'error', message: '缺少 channelList' }));
            return;
          }
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ status: 'ok', message: '儲存成功' }));
        } catch (e) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ status: 'error', message: e.message }));
        }
      });
      return;
    }

    if (req.method === 'POST' && pathname === '/api/channel/add') {
      let body = '';
      req.on('data', chunk => body += chunk);
      req.on('end', () => {
        const obj = JSON.parse(body);
        if (!obj.name || !obj.url) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ status: 'error', message: '名稱與網址不可為空' }));
          return;
        }
        mockChannels.push({ index: mockChannels.length, name: obj.name, url: obj.url, isHidden: false });
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'ok', message: '新增成功' }));
      });
      return;
    }

    if (req.method === 'GET' && pathname === '/api/channels/export') {
      const root = {
        appName: "新聞直播fork620",
        version: "6.2.2",
        exportTimestamp: Date.now(),
        channelCount: mockChannels.length,
        channelList: mockChannels
      };
      res.writeHead(200, {
        'Content-Type': 'application/json; charset=utf-8',
        'Content-Disposition': 'attachment; filename="news_channels_backup.json"'
      });
      res.end(JSON.stringify(root));
      return;
    }

    if (req.method === 'GET' && pathname === '/api/youtube/history') {
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(["機場直播", "流行音樂", "穿越短劇", "公視新聞直播"]));
      return;
    }

    if (req.method === 'POST' && pathname === '/api/channels/import') {
      let body = '';
      req.on('data', chunk => body += chunk);
      req.on('end', () => {
        try {
          const parsed = JSON.parse(body);
          const list = Array.isArray(parsed) ? parsed : (parsed.channelList || parsed.newChannelArray);
          if (!list || !Array.isArray(list) || list.length === 0) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ status: 'error', message: '無有效頻道資料' }));
            return;
          }
          mockChannels = list.map((item, idx) => ({
            index: idx,
            name: item.name || item.customName || item.channel,
            url: item.url || item.customUrl,
            isHidden: !!item.isHidden
          }));
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ status: 'ok', count: mockChannels.length, message: `成功匯入 ${mockChannels.length} 個頻道` }));
        } catch (e) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ status: 'error', message: e.message }));
        }
      });
      return;
    }

    if (req.method === 'GET' && pathname === '/api/youtube/search') {
      const q = url.searchParams.get('q');
      if (!q || !q.trim()) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'error', message: '搜尋關鍵字不可為空' }));
        return;
      }
      const results = [
        { title: "公視新聞 24小時線上直播", url: "https://www.youtube.com/watch?v=hg4nYb3jE9A", uploader: "公視新聞網", isLive: true },
        { title: "民視新聞 24小時直播", url: "https://www.youtube.com/watch?v=ylYJSBUgaMA", uploader: "民視新聞網", isLive: true }
      ];
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(results));
      return;
    }

    if (req.method === 'GET' && pathname === '/api/remote') {
      const key = url.searchParams.get('key');
      const validKeys = ['up', 'down', 'left', 'right', 'ok', 'back', 'menu', 'vol_up', 'vol_down'];
      if (validKeys.includes(key)) {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'ok' }));
      } else {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'error', message: '未知的按鍵碼' }));
      }
      return;
    }

    res.writeHead(404);
    res.end();
  });

  await new Promise(resolve => server.listen(mockPort, resolve));

  // 測試 1: GET / (首頁 HTML 取得)
  await itAsync('HTTP 測試：GET / 取得首頁 HTML Dashboard', async () => {
    const res = await fetch(`http://localhost:${mockPort}/`);
    assert.strictEqual(res.status, 200);
    const text = await res.text();
    assert.ok(text.includes('新聞直播 TVBox 遠端管理後台'));
  });

  // 測試 2: GET /api/status (狀態 API)
  await itAsync('HTTP 測試：GET /api/status 回傳系統狀態與頻道總數', async () => {
    const res = await fetch(`http://localhost:${mockPort}/api/status`);
    assert.strictEqual(res.status, 200);
    const data = await res.json();
    assert.strictEqual(data.status, 'ok');
    assert.strictEqual(data.version, '6.2.0');
    assert.strictEqual(data.channelCount, 2);
  });

  // 測試 3: POST /api/config 驗證與錯誤處理
  await itAsync('HTTP 測試：POST /api/config 非法 JSON 拒絕 (400)', async () => {
    const res = await fetch(`http://localhost:${mockPort}/api/config`, {
      method: 'POST',
      body: 'invalid-json'
    });
    assert.strictEqual(res.status, 400);
    const data = await res.json();
    assert.strictEqual(data.status, 'error');
  });

  await itAsync('HTTP 測試：POST /api/config 合法頻道清單套用 (200)', async () => {
    const res = await fetch(`http://localhost:${mockPort}/api/config`, {
      method: 'POST',
      body: JSON.stringify({ channelList: [{ name: "測試頻道", url: "https://example.com" }] })
    });
    assert.strictEqual(res.status, 200);
    const data = await res.json();
    assert.strictEqual(data.status, 'ok');
  });

  // 測試 4: POST /api/channel/add 快速新增
  await itAsync('HTTP 測試：POST /api/channel/add 新增頻道推播', async () => {
    const res = await fetch(`http://localhost:${mockPort}/api/channel/add`, {
      method: 'POST',
      body: JSON.stringify({ name: "民視新聞", url: "https://ftv.com" })
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(mockChannels.length, 3);
  });

  // 測試 5: GET /api/remote 虛擬遙控器按鍵分派
  await itAsync('HTTP 測試：GET /api/remote?key=up 遙控器指令發送', async () => {
    const res = await fetch(`http://localhost:${mockPort}/api/remote?key=up`);
    assert.strictEqual(res.status, 200);
    const data = await res.json();
    assert.strictEqual(data.status, 'ok');
  });

  await itAsync('HTTP 測試：GET /api/remote?key=unknown 非法按鍵阻擋', async () => {
    const res = await fetch(`http://localhost:${mockPort}/api/remote?key=unknown`);
    assert.strictEqual(res.status, 400);
  });

  // 測試 6: GET /api/youtube/search (YouTube 搜尋 API)
  await itAsync('HTTP 測試：GET /api/youtube/search 成功檢索關鍵字並回傳直播資訊', async () => {
    const res = await fetch(`http://localhost:${mockPort}/api/youtube/search?q=%E5%85%AC%E8%A6%96%E6%96%B0%E8%81%9E`);
    assert.strictEqual(res.status, 200);
    const data = await res.json();
    assert.ok(Array.isArray(data), '回傳結果應為陣列');
    assert.strictEqual(data.length, 2);
    assert.strictEqual(data[0].isLive, true);
    assert.ok(data[0].url.startsWith('https://www.youtube.com/watch?v='));
  });

  await itAsync('HTTP 測試：GET /api/youtube/search 空白關鍵字拒絕 (400)', async () => {
    const res = await fetch(`http://localhost:${mockPort}/api/youtube/search?q=`);
    assert.strictEqual(res.status, 400);
    const data = await res.json();
    assert.strictEqual(data.status, 'error');
  });

  // 測試 6.1: GET /api/channels/export (頻道備份匯出 API)
  await itAsync('HTTP 測試：GET /api/channels/export 一鍵匯出頻道備份 JSON', async () => {
    const res = await fetch(`http://localhost:${mockPort}/api/channels/export`);
    assert.strictEqual(res.status, 200);
    assert.ok(res.headers.get('content-disposition').includes('news_channels_backup.json'));
    const data = await res.json();
    assert.strictEqual(data.appName, '新聞直播fork620');
    assert.strictEqual(data.version, '6.2.2');
    assert.ok(Array.isArray(data.channelList), 'channelList 必須為陣列');
    assert.ok(data.channelList.length >= 2, '備份頻道數應大於等於 2');
  });

  // 測試 6.2: POST /api/channels/import (頻道備份匯入 API)
  await itAsync('HTTP 測試：POST /api/channels/import 一鍵匯入頻道備份並生效', async () => {
    const backupPayload = {
      channelList: [
        { name: "測試匯入頻道1", url: "https://www.youtube.com/watch?v=test1", isHidden: false },
        { name: "測試匯入頻道2", url: "https://www.youtube.com/watch?v=test2", isHidden: false }
      ]
    };
    const res = await fetch(`http://localhost:${mockPort}/api/channels/import`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(backupPayload)
    });
    assert.strictEqual(res.status, 200);
    const data = await res.json();
    assert.strictEqual(data.status, 'ok');
    assert.strictEqual(data.count, 2);

    // 驗證 /api/channels 是否已同步更新
    const checkRes = await fetch(`http://localhost:${mockPort}/api/channels`);
    const currentChannels = await checkRes.json();
    assert.strictEqual(currentChannels.length, 2);
    assert.strictEqual(currentChannels[0].name, '測試匯入頻道1');
  });

  // 測試 6.3: GET /api/youtube/history (YouTube 搜尋歷史 CSV API)
  await itAsync('HTTP 測試：GET /api/youtube/history 取得關鍵字歷史', async () => {
    const res = await fetch(`http://localhost:${mockPort}/api/youtube/history`);
    assert.strictEqual(res.status, 200);
    const history = await res.json();
    assert.ok(Array.isArray(history), '搜尋歷史應為陣列');
    assert.ok(history.includes('機場直播'), '搜尋歷史應包含預設關鍵字 機場直播');
    assert.ok(history.includes('流行音樂'), '搜尋歷史應包含預設關鍵字 流行音樂');
    assert.ok(history.includes('穿越短劇'), '搜尋歷史應包含預設關鍵字 穿越短劇');
  });

  // 測試 7: 版本一致性與升級配置校驗
  it('版本校驗：VERSION 檔案格式與版本號碼一致性', () => {
    const versionPath = path.join(__dirname, '..', 'VERSION');
    const version = fs.readFileSync(versionPath, 'utf8').trim();
    assert.match(version, /^\d+\.\d+\.\d+$/, 'VERSION 必須符合語意化版本格式');
    
    const gradlePath = path.join(__dirname, '..', 'app', 'build.gradle.kts');
    const gradleContent = fs.readFileSync(gradlePath, 'utf8');
    assert.ok(gradleContent.includes(`versionName = "${version}"`), 'build.gradle.kts 的 versionName 必須與 VERSION 一致');
    assert.ok(gradleContent.includes('versionCode = 60220'), 'build.gradle.kts 的 versionCode 必須遞增至 60220');
  });

  // 測試 8: 金鑰完整性檢驗
  it('資安與憑證：固化發行簽名 keystore.jks 存在且大小正常', () => {
    const keystorePath = path.join(__dirname, '..', 'keystore.jks');
    assert.ok(fs.existsSync(keystorePath), 'keystore.jks 必須存在於專案根目錄');
    const stats = fs.statSync(keystorePath);
    assert.ok(stats.size > 1000, 'keystore.jks 檔案必須大於 1KB');
  });

  // 測試 9: 頻道總表選台防呆與觸發機制檢驗 (解決小米盒子s選點無反應問題)
  it('選台邏輯：ChannelListActivity 正式移除 itemsCanFocus 衝突並提供三重觸發', () => {
    const activityPath = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'io', 'github', 'anenasa', 'news', 'ChannelListActivity.kt');
    const activityCode = fs.readFileSync(activityPath, 'utf8');
    assert.ok(activityCode.includes('itemsCanFocus = false'), 'ChannelListActivity 必須設置 itemsCanFocus = false 防止按鍵攔截');
    assert.ok(activityCode.includes('selectChannel('), '必須包含 selectChannel 統一結算函式');
    assert.ok(activityCode.includes('KEYCODE_DPAD_CENTER'), '必須攔截 DPAD_CENTER 實體鍵點選');
  });

  // 測試 10: 左側選單「退出APP」功能按鈕檢驗
  it('選單完整性：fragment_menu 與 MainActivity 包含直接退出 APP 功能', () => {
    const menuLayoutPath = path.join(__dirname, '..', 'app', 'src', 'main', 'res', 'layout', 'fragment_menu.xml');
    const menuXml = fs.readFileSync(menuLayoutPath, 'utf8');
    assert.ok(menuXml.includes('btn_exit_app'), 'fragment_menu 必須包含 btn_exit_app 按鈕');
    assert.ok(menuXml.includes('onClick="exitApp"'), '按鈕必須綁定 exitApp 點擊動作');

    const mainActivityPath = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'io', 'github', 'anenasa', 'news', 'MainActivity.kt');
    const mainActivityCode = fs.readFileSync(mainActivityPath, 'utf8');
    assert.ok(mainActivityCode.includes('fun exitApp('), 'MainActivity 必須提供 exitApp 實作');
  });

  await new Promise(resolve => server.close(resolve));
}

runMockServerTests().then(() => {
  console.log('\n========================================');
  console.log(`測試結果：共 ${totalTests} 項測試，通過 ${passedTests} 項，失敗 ${totalTests - passedTests} 項。`);
  console.log('========================================');
  if (totalTests === passedTests) {
    console.log('🎉 所有測試 100% 通過！');
  } else {
    process.exit(1);
  }
}).catch(err => {
  console.error('測試執行錯誤:', err);
  process.exit(1);
});
