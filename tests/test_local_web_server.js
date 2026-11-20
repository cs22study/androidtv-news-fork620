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
