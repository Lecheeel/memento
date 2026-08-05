import { createHmac, createHash, createDecipheriv, timingSafeEqual } from 'node:crypto';
import { promises as fs } from 'node:fs';
import { createServer } from 'node:http';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const configPath = join(__dirname, 'config.json');

const defaultConfig = {
  bindHost: '0.0.0.0',
  port: 8080,
  storageDir: './storage',
  deviceToken: 'replace-with-device-token',
  encryptionSecret: 'replace-with-encryption-secret',
  maxBodyBytes: 65536,
  allowedClockSkewMs: 300000,
};

const config = await loadConfig();
const storageDir = join(__dirname, config.storageDir);
const cleanDir = join(storageDir, 'clean');

await fs.mkdir(cleanDir, { recursive: true });

const server = createServer(async (req, res) => {
  try {
    if (req.method === 'GET' && req.url === '/health') {
      writeJson(res, 200, { ok: true });
      return;
    }

    if (req.method === 'GET' && req.url.startsWith('/events')) {
      const url = new URL(req.url, `http://${req.headers.host}`);
      const limit = clampInt(url.searchParams.get('limit'), 1, 200, 20);
      const items = await readRecentEvents(limit);
      writeJson(res, 200, { items });
      return;
    }

    if (req.method === 'POST' && req.url === '/ingest') {
      const body = await readBody(req, config.maxBodyBytes);
      const envelope = safeJsonParse(body);
      if (!envelope) {
        console.warn('ingest rejected: invalid_json');
        writeJson(res, 400, { ok: false, error: 'invalid_json' });
        return;
      }

      const validation = validateEnvelope(envelope, config);
      if (!validation.ok) {
        console.warn(`ingest rejected: ${validation.error}`);
        writeJson(res, 400, validation);
        return;
      }

      const decrypted = decryptEnvelope(envelope, config);
      const stored = await writePayloadRecords(decrypted, envelope);
      console.log(`ingest accepted: device=${envelope.deviceId} stored=${stored}`);
      writeJson(res, 200, { ok: true, stored });
      return;
    }

    writeJson(res, 404, { ok: false, error: 'not_found' });
  } catch (error) {
    writeJson(res, 500, { ok: false, error: error.message || 'internal_error' });
  }
});

server.listen(config.port, config.bindHost, () => {
  console.log(`Memento server listening on http://${config.bindHost}:${config.port}`);
});

async function loadConfig() {
  try {
    const raw = await fs.readFile(configPath, 'utf8');
    return { ...defaultConfig, ...JSON.parse(raw) };
  } catch {
    return defaultConfig;
  }
}

function validateEnvelope(envelope, config) {
  const required = ['deviceId', 'timestamp', 'iv', 'ciphertext', 'signature'];
  for (const key of required) {
    if (typeof envelope[key] !== 'string' && key !== 'timestamp') {
      return { ok: false, error: `missing_${key}` };
    }
    if (key === 'timestamp' && typeof envelope[key] !== 'number') {
      return { ok: false, error: 'missing_timestamp' };
    }
  }

  if (envelope.deviceId !== config.deviceToken) {
    return { ok: false, error: 'invalid_device' };
  }

  const now = Date.now();
  if (Math.abs(now - envelope.timestamp) > config.allowedClockSkewMs) {
    return { ok: false, error: 'timestamp_out_of_range' };
  }

  const expected = signEnvelope(envelope, config.encryptionSecret);
  if (!constantTimeEquals(expected, envelope.signature)) {
    return { ok: false, error: 'invalid_signature' };
  }

  return { ok: true };
}

function signEnvelope(envelope, secret) {
  const key = deriveKey(secret);
  return createHmac('sha256', key)
    .update(`${envelope.iv}.${envelope.ciphertext}.${envelope.deviceId}`)
    .digest('base64');
}

function deriveKey(secret) {
  return createHash('sha256').update(secret, 'utf8').digest();
}

function constantTimeEquals(a, b) {
  const left = Buffer.from(a, 'utf8');
  const right = Buffer.from(b, 'utf8');
  if (left.length !== right.length) return false;
  return timingSafeEqual(left, right);
}

function decryptEnvelope(envelope, config) {
  const key = deriveKey(config.encryptionSecret);
  const decipher = createDecipheriv('aes-256-gcm', key, decodeBase64(envelope.iv));
  decipher.setAAD(Buffer.from(envelope.deviceId, 'utf8'));
  const ciphertext = decodeBase64(envelope.ciphertext);
  const authTag = ciphertext.slice(ciphertext.length - 16);
  const encrypted = ciphertext.slice(0, ciphertext.length - 16);
  decipher.setAuthTag(authTag);
  const plain = Buffer.concat([decipher.update(encrypted), decipher.final()]);
  return JSON.parse(plain.toString('utf8'));
}

async function writePayloadRecords(payload, envelope) {
  const events = extractEvents(payload.events);
  if (events.length === 0) {
    console.warn('ingest accepted but contained no events');
    return 0;
  }

  let stored = 0;
  for (const event of events) {
    const record = recordForEvent(event, envelope, event.packageName || '_unknown', classifyEvent(event));
    if (!record) continue;
    await appendPartitionedRecord(record);
    stored += 1;
  }
  return stored;
}

function extractEvents(value) {
  if (Array.isArray(value)) return value;
  if (typeof value === 'string') {
    const parsed = safeJsonParse(value);
    return Array.isArray(parsed) ? parsed : [];
  }
  return [];
}

function recordForEvent(event, envelope, packageName, category) {
  const ts = event.postTime || envelope.timestamp || Date.now();
  const message = buildMessage(event);
  if (!message) return null;
  return {
    ts,
    time: formatLocalDateTime(ts),
    packageName,
    category,
    message,
  };
}

async function appendPartitionedRecord(record) {
  const packageDir = join(cleanDir, sanitizePathSegment(record.packageName));
  await fs.mkdir(packageDir, { recursive: true });
  const filePath = join(packageDir, `${dateKey(record.ts)}.jsonl`);
  await fs.appendFile(filePath, JSON.stringify(record) + '\n', 'utf8');
  console.log(`stored package=${record.packageName} category=${record.category} file=${filePath}`);
}

function buildMessage(event) {
  const parts = [event.title, event.text, event.subText]
    .map((part) => String(part ?? '').trim())
    .filter(Boolean);
  return parts.join(' | ');
}

function classifyEvent(event) {
  const packageName = String(event.packageName ?? '').toLowerCase();
  const appLabel = String(event.appLabel ?? '').toLowerCase();
  const combined = `${packageName} ${appLabel} ${event.title ?? ''} ${event.text ?? ''} ${event.subText ?? ''}`.toLowerCase();

  if (isAnyMatch(packageName, ['com.android', 'android', 'com.miui', 'com.xiaomi', 'com.google.android'])) {
    return 'system';
  }
  if (isAnyMatch(combined, [
    'com.tencent.mm',
    'com.tencent.mobileqq',
    'org.telegram.messenger',
    'com.whatsapp',
    'com.facebook.orca',
    'com.ss.android.lark',
    'com.alibaba.android.rimet',
    'wechat',
    '微信',
    'qq',
    'telegram',
    'whatsapp',
    'messenger',
    'lark',
    '飞书',
    'dingtalk',
    '钉钉',
    'discord',
    'slack',
  ])) {
    return 'chat';
  }
  if (isAnyMatch(combined, [
    'com.eg.android.alipaygphone',
    'alipay',
    '支付宝',
    'payment',
    'wallet',
    'bank',
    'finance',
    'billing',
    'credit',
  ])) {
    return 'finance';
  }
  if (isAnyMatch(combined, ['mail', 'gmail', 'outlook', 'email', '邮箱'])) {
    return 'mail';
  }
  if (isAnyMatch(combined, [
    'com.taobao.taobao',
    'com.jingdong.app.mall',
    'com.pinduoduo.ui',
    'taobao',
    '淘宝',
    'tmall',
    'jd',
    '京东',
    'amazon',
    'pinduoduo',
    '拼多多',
    'shopping',
  ])) {
    return 'shopping';
  }
  if (isAnyMatch(combined, [
    'com.netease.cloudmusic',
    'music',
    '音乐',
    'video',
    'youtube',
    'bilibili',
    '哔哩哔哩',
    'tiktok',
    'douyin',
    '抖音',
  ])) {
    return 'media';
  }
  if (isAnyMatch(combined, ['calendar', '日历', 'todo', 'notion', 'keep', 'office', 'meeting', 'docs'])) {
    return 'work';
  }
  if (isAnyMatch(combined, [
    'com.xingin.xhs',
    'com.sina.weibo',
    'weibo',
    '微博',
    'instagram',
    'twitter',
    'xhs',
    '小红书',
    'red',
    '小红',
  ])) {
    return 'social';
  }
  return 'other';
}

function isAnyMatch(source, keywords) {
  return keywords.some((keyword) => source.includes(keyword));
}

async function readRecentEvents(limit) {
  const files = await listJsonlFiles(cleanDir);
  const records = [];
  for (const file of files) {
    try {
      const data = await fs.readFile(file, 'utf8');
      const lines = data.trim().split('\n').filter(Boolean);
      records.push(...lines.map((line) => safeJsonParse(line)).filter(Boolean));
    } catch {
      // Ignore partially-written or deleted files during ad-hoc inspection.
    }
  }
  return records
    .sort((a, b) => Number(b.ts || 0) - Number(a.ts || 0))
    .slice(0, limit);
}

async function listJsonlFiles(dir) {
  try {
    const entries = await fs.readdir(dir, { withFileTypes: true });
    const files = await Promise.all(entries.map(async (entry) => {
      const fullPath = join(dir, entry.name);
      if (entry.isDirectory()) return listJsonlFiles(fullPath);
      if (entry.isFile() && entry.name.endsWith('.jsonl')) return [fullPath];
      return [];
    }));
    return files.flat();
  } catch {
    return [];
  }
}

function safeJsonParse(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function writeJson(res, statusCode, payload) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(payload));
}

async function readBody(req, maxBytes) {
  const chunks = [];
  let total = 0;
  for await (const chunk of req) {
    total += chunk.length;
    if (total > maxBytes) {
      throw new Error('body_too_large');
    }
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

function clampInt(value, min, max, fallback) {
  const parsed = Number.parseInt(value ?? '', 10);
  if (Number.isNaN(parsed)) return fallback;
  return Math.min(max, Math.max(min, parsed));
}

function decodeBase64(value) {
  return Buffer.from(value, 'base64');
}

function sanitizePathSegment(value) {
  return String(value).replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 120) || '_unknown';
}

function dateKey(timestamp) {
  return new Date(timestamp).toISOString().slice(0, 10);
}

function formatLocalDateTime(timestamp) {
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(new Date(timestamp));
  const lookup = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${lookup.year}-${lookup.month}-${lookup.day} ${lookup.hour}:${lookup.minute}:${lookup.second}`;
}
