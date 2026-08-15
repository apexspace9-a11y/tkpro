import http from 'node:http';
import { createHash, createHmac, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';

const PORT = Number(process.env.PORT || 8080);
const DB_PATH = process.env.TKPRO_DB_PATH || './tkpro-online.db';
const TOKEN_SECRET = process.env.TKPRO_TOKEN_SECRET || '';
const ADMIN_KEY = process.env.TKPRO_ADMIN_KEY || '';
const TOKEN_TTL_MS = 1000 * 60 * 60 * 24 * 30;

if (!TOKEN_SECRET) throw new Error('TKPRO_TOKEN_SECRET is required');
if (!ADMIN_KEY) throw new Error('TKPRO_ADMIN_KEY is required');

const db = new DatabaseSync(DB_PATH);
db.exec(`
PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT NOT NULL UNIQUE COLLATE NOCASE,
  password_salt TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  premium_tier TEXT NOT NULL DEFAULT 'FREE',
  premium_expiry INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS snapshots (
  user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  revision INTEGER NOT NULL DEFAULT 0,
  payload TEXT,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS app_config (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS payments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan TEXT NOT NULL,
  amount INTEGER NOT NULL,
  transfer_code TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL DEFAULT 'PENDING',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_payments_user ON payments(user_id, created_at DESC);
`);

const defaults = {
  ai_endpoint: '',
  ai_model: '',
  ai_api_key: '',
  ai_system_prompt: 'Bạn là trợ lý tài chính cá nhân. Trả lời chính xác, ngắn gọn, không bịa dữ liệu và ưu tiên an toàn dòng tiền.',
  bank_name: '',
  bank_account: '',
  bank_owner: '',
  plus_price: '49000',
  pro_price: '99000'
};
for (const [key, value] of Object.entries(defaults)) {
  db.prepare('INSERT OR IGNORE INTO app_config(key,value) VALUES(?,?)').run(key, value);
}

function json(res, status, body) {
  const data = Buffer.from(JSON.stringify(body));
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': data.length,
    'Cache-Control': 'no-store'
  });
  res.end(data);
}

async function readJson(req, max = 12 * 1024 * 1024) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > max) throw new Error('Payload quá lớn');
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function cleanEmail(value) {
  const email = String(value || '').trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new Error('Email không hợp lệ');
  return email;
}

function passwordRecord(password) {
  const p = String(password || '');
  if (p.length < 8 || p.length > 160) throw new Error('Mật khẩu cần 8-160 ký tự');
  const salt = randomBytes(16).toString('hex');
  const hash = scryptSync(p, salt, 64).toString('hex');
  return { salt, hash };
}

function verifyPassword(password, salt, expectedHex) {
  const actual = scryptSync(String(password || ''), salt, 64);
  const expected = Buffer.from(expectedHex, 'hex');
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

function b64url(obj) {
  return Buffer.from(JSON.stringify(obj)).toString('base64url');
}

function signToken(user) {
  const payload = b64url({ sub: user.id, email: user.email, exp: Date.now() + TOKEN_TTL_MS });
  const sig = createHmac('sha256', TOKEN_SECRET).update(payload).digest('base64url');
  return `${payload}.${sig}`;
}

function verifyToken(req) {
  const header = String(req.headers.authorization || '');
  if (!header.startsWith('Bearer ')) throw new HttpError(401, 'Chưa đăng nhập');
  const token = header.slice(7).trim();
  const [payload, sig] = token.split('.');
  if (!payload || !sig) throw new HttpError(401, 'Token không hợp lệ');
  const expected = createHmac('sha256', TOKEN_SECRET).update(payload).digest('base64url');
  if (!safeEqual(sig, expected)) throw new HttpError(401, 'Token không hợp lệ');
  const data = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
  if (!data.exp || data.exp < Date.now()) throw new HttpError(401, 'Phiên đăng nhập đã hết hạn');
  const user = db.prepare('SELECT id,email,premium_tier,premium_expiry FROM users WHERE id=?').get(data.sub);
  if (!user) throw new HttpError(401, 'Tài khoản không tồn tại');
  return user;
}

function safeEqual(a, b) {
  const ah = createHash('sha256').update(String(a)).digest();
  const bh = createHash('sha256').update(String(b)).digest();
  return timingSafeEqual(ah, bh);
}

function requireAdmin(req) {
  const key = String(req.headers['x-admin-key'] || '');
  if (!key || !safeEqual(key, ADMIN_KEY)) throw new HttpError(403, 'Admin key không hợp lệ');
}

function configMap(includeSecret = false) {
  const rows = db.prepare('SELECT key,value FROM app_config').all();
  const map = Object.fromEntries(rows.map(row => [row.key, row.value]));
  if (!includeSecret) {
    map.ai_api_key_set = Boolean(map.ai_api_key || process.env.TKPRO_AI_API_KEY);
    delete map.ai_api_key;
  }
  return map;
}

function setConfig(values) {
  const stmt = db.prepare('INSERT INTO app_config(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value');
  const allowed = new Set(Object.keys(defaults));
  for (const [key, value] of Object.entries(values || {})) {
    if (!allowed.has(key)) continue;
    if (key === 'ai_api_key' && String(value || '').trim() === '') continue;
    stmt.run(key, String(value ?? '').trim());
  }
}

function premiumActive(user) {
  return user.premium_tier !== 'FREE' && (user.premium_expiry === 0 || user.premium_expiry > Date.now());
}

class HttpError extends Error {
  constructor(status, message, extra = {}) {
    super(message);
    this.status = status;
    this.extra = extra;
  }
}

async function route(req, res) {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const path = url.pathname;

  if (req.method === 'GET' && path === '/v1/health') {
    return json(res, 200, { ok: true, service: 'tkpro-online', version: 4, time: Date.now() });
  }

  if (req.method === 'POST' && path === '/v1/auth/register') {
    const body = await readJson(req);
    const email = cleanEmail(body.email);
    const { salt, hash } = passwordRecord(body.password);
    try {
      const info = db.prepare('INSERT INTO users(email,password_salt,password_hash,created_at) VALUES(?,?,?,?)').run(email, salt, hash, Date.now());
      const user = db.prepare('SELECT id,email,premium_tier,premium_expiry FROM users WHERE id=?').get(info.lastInsertRowid);
      db.prepare('INSERT INTO snapshots(user_id,revision,payload,updated_at) VALUES(?,0,NULL,?)').run(user.id, Date.now());
      return json(res, 201, { token: signToken(user), user: { ...user, premium_active: premiumActive(user) } });
    } catch (error) {
      if (String(error.message).includes('UNIQUE')) throw new HttpError(409, 'Email đã tồn tại');
      throw error;
    }
  }

  if (req.method === 'POST' && path === '/v1/auth/login') {
    const body = await readJson(req);
    const email = cleanEmail(body.email);
    const row = db.prepare('SELECT id,email,password_salt,password_hash,premium_tier,premium_expiry FROM users WHERE email=?').get(email);
    if (!row || !verifyPassword(body.password, row.password_salt, row.password_hash)) throw new HttpError(401, 'Email hoặc mật khẩu không đúng');
    const user = { id: row.id, email: row.email, premium_tier: row.premium_tier, premium_expiry: row.premium_expiry };
    return json(res, 200, { token: signToken(user), user: { ...user, premium_active: premiumActive(user) } });
  }

  if (req.method === 'GET' && path === '/v1/me') {
    const user = verifyToken(req);
    return json(res, 200, { user: { ...user, premium_active: premiumActive(user) } });
  }

  if (req.method === 'GET' && path === '/v1/config/public') {
    const cfg = configMap(false);
    return json(res, 200, {
      bank_name: cfg.bank_name,
      bank_account: cfg.bank_account,
      bank_owner: cfg.bank_owner,
      plus_price: Number(cfg.plus_price || 0),
      pro_price: Number(cfg.pro_price || 0)
    });
  }

  if (req.method === 'GET' && path === '/v1/snapshot') {
    const user = verifyToken(req);
    const row = db.prepare('SELECT revision,payload,updated_at FROM snapshots WHERE user_id=?').get(user.id) || { revision: 0, payload: null, updated_at: 0 };
    return json(res, 200, { revision: row.revision, payload: row.payload ? JSON.parse(row.payload) : null, updated_at: row.updated_at });
  }

  if (req.method === 'PUT' && path === '/v1/snapshot') {
    const user = verifyToken(req);
    const body = await readJson(req);
    const expected = Number(body.revision ?? -1);
    if (!Number.isInteger(expected) || expected < 0) throw new HttpError(400, 'Revision không hợp lệ');
    if (typeof body.payload !== 'object' || body.payload == null) throw new HttpError(400, 'Snapshot không hợp lệ');
    const row = db.prepare('SELECT revision FROM snapshots WHERE user_id=?').get(user.id) || { revision: 0 };
    if (row.revision !== expected) throw new HttpError(409, 'Dữ liệu cloud đã thay đổi', { revision: row.revision });
    const next = row.revision + 1;
    db.prepare(`INSERT INTO snapshots(user_id,revision,payload,updated_at) VALUES(?,?,?,?)
      ON CONFLICT(user_id) DO UPDATE SET revision=excluded.revision,payload=excluded.payload,updated_at=excluded.updated_at`)
      .run(user.id, next, JSON.stringify(body.payload), Date.now());
    return json(res, 200, { revision: next, updated_at: Date.now() });
  }

  if (req.method === 'POST' && path === '/v1/payments') {
    const user = verifyToken(req);
    const body = await readJson(req);
    const plan = String(body.plan || '').toUpperCase();
    if (!['PLUS', 'PRO'].includes(plan)) throw new HttpError(400, 'Gói không hợp lệ');
    const cfg = configMap(false);
    const amount = plan === 'PLUS' ? Number(cfg.plus_price || 0) : Number(cfg.pro_price || 0);
    if (!Number.isFinite(amount) || amount <= 0) throw new HttpError(400, 'Gói chưa được cấu hình giá');
    const transferCode = `TKP-${randomBytes(5).toString('hex').toUpperCase()}`;
    const now = Date.now();
    const info = db.prepare('INSERT INTO payments(user_id,plan,amount,transfer_code,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)')
      .run(user.id, plan, amount, transferCode, 'PENDING', now, now);
    const payment = db.prepare('SELECT id,plan,amount,transfer_code,status,created_at,updated_at FROM payments WHERE id=?').get(info.lastInsertRowid);
    return json(res, 201, { payment });
  }

  if (req.method === 'GET' && path === '/v1/payments') {
    const user = verifyToken(req);
    const rows = db.prepare('SELECT id,plan,amount,transfer_code,status,created_at,updated_at FROM payments WHERE user_id=? ORDER BY created_at DESC LIMIT 50').all(user.id);
    return json(res, 200, { payments: rows });
  }

  if (req.method === 'POST' && path === '/v1/ai/chat') {
    const user = verifyToken(req);
    if (!(premiumActive(user) && user.premium_tier === 'PRO')) throw new HttpError(402, 'AI online yêu cầu gói PRO');
    const body = await readJson(req, 1024 * 1024);
    const message = String(body.message || '').trim();
    if (!message) throw new HttpError(400, 'Tin nhắn trống');
    const cfg = configMap(true);
    const endpoint = cfg.ai_endpoint;
    const model = cfg.ai_model;
    const apiKey = process.env.TKPRO_AI_API_KEY || cfg.ai_api_key;
    if (!endpoint || !model || !apiKey) throw new HttpError(503, 'AI chưa được Admin cấu hình');
    const context = String(body.context || '').slice(0, 100000);
    const system = `${cfg.ai_system_prompt || defaults.ai_system_prompt}\n\nNgữ cảnh tài chính do ứng dụng cung cấp:\n${context}`;
    const upstream = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${apiKey}` },
      body: JSON.stringify({ model, messages: [{ role: 'system', content: system }, { role: 'user', content: message }] })
    });
    const text = await upstream.text();
    if (!upstream.ok) throw new HttpError(502, `AI upstream lỗi ${upstream.status}`);
    const data = JSON.parse(text);
    const reply = data?.choices?.[0]?.message?.content ?? data?.output_text ?? '';
    if (!reply) throw new HttpError(502, 'AI không trả nội dung');
    return json(res, 200, { reply: String(reply) });
  }

  if (req.method === 'POST' && path === '/v1/admin/verify') {
    requireAdmin(req);
    return json(res, 200, { ok: true });
  }

  if (req.method === 'GET' && path === '/v1/admin/config') {
    requireAdmin(req);
    return json(res, 200, { config: configMap(false) });
  }

  if (req.method === 'PUT' && path === '/v1/admin/config') {
    requireAdmin(req);
    const body = await readJson(req, 1024 * 1024);
    setConfig(body.config || {});
    return json(res, 200, { config: configMap(false) });
  }

  if (req.method === 'GET' && path === '/v1/admin/payments') {
    requireAdmin(req);
    const rows = db.prepare(`SELECT p.id,p.plan,p.amount,p.transfer_code,p.status,p.created_at,p.updated_at,u.email
      FROM payments p JOIN users u ON u.id=p.user_id ORDER BY p.created_at DESC LIMIT 100`).all();
    return json(res, 200, { payments: rows });
  }

  if (req.method === 'POST' && path.match(/^\/v1\/admin\/payments\/\d+\/(approve|reject)$/)) {
    requireAdmin(req);
    const [, , , , idText, action] = path.split('/');
    const id = Number(idText);
    const body = await readJson(req);
    const payment = db.prepare('SELECT * FROM payments WHERE id=?').get(id);
    if (!payment) throw new HttpError(404, 'Không tìm thấy yêu cầu');
    if (action === 'reject') {
      db.prepare('UPDATE payments SET status=?,updated_at=? WHERE id=?').run('REJECTED', Date.now(), id);
    } else {
      const months = Math.max(1, Math.min(120, Number(body.months || 1)));
      const user = db.prepare('SELECT id,premium_expiry FROM users WHERE id=?').get(payment.user_id);
      const base = Math.max(Date.now(), Number(user.premium_expiry || 0));
      const expiry = new Date(base);
      expiry.setMonth(expiry.getMonth() + months);
      db.prepare('UPDATE users SET premium_tier=?,premium_expiry=? WHERE id=?').run(payment.plan, expiry.getTime(), payment.user_id);
      db.prepare('UPDATE payments SET status=?,updated_at=? WHERE id=?').run('APPROVED', Date.now(), id);
    }
    return json(res, 200, { ok: true });
  }

  if (req.method === 'POST' && path === '/v1/admin/premium') {
    requireAdmin(req);
    const body = await readJson(req);
    const email = cleanEmail(body.email);
    const tier = String(body.tier || 'FREE').toUpperCase();
    if (!['FREE', 'PLUS', 'PRO'].includes(tier)) throw new HttpError(400, 'Gói không hợp lệ');
    const user = db.prepare('SELECT id,premium_expiry FROM users WHERE email=?').get(email);
    if (!user) throw new HttpError(404, 'Không tìm thấy tài khoản');
    if (tier === 'FREE') {
      db.prepare('UPDATE users SET premium_tier=?,premium_expiry=0 WHERE id=?').run('FREE', user.id);
    } else {
      const months = Math.max(1, Math.min(120, Number(body.months || 1)));
      const base = Math.max(Date.now(), Number(user.premium_expiry || 0));
      const expiry = new Date(base);
      expiry.setMonth(expiry.getMonth() + months);
      db.prepare('UPDATE users SET premium_tier=?,premium_expiry=? WHERE id=?').run(tier, expiry.getTime(), user.id);
    }
    return json(res, 200, { ok: true });
  }

  throw new HttpError(404, 'Endpoint không tồn tại');
}

const server = http.createServer(async (req, res) => {
  try {
    await route(req, res);
  } catch (error) {
    console.error(error);
    if (error instanceof HttpError) return json(res, error.status, { error: error.message, ...error.extra });
    if (error instanceof SyntaxError) return json(res, 400, { error: 'JSON không hợp lệ' });
    return json(res, 500, { error: error?.message || 'Lỗi máy chủ' });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`TKPro Online V4 listening on :${PORT}`);
});
