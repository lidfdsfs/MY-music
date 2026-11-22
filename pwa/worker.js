/**
 * MY音乐 PWA —— Cloudflare Worker 代理
 *
 * 浏览器直接请求 gequhai / 网易云 / QQ 会被 CORS 拦下，音频直链还要求特定 Referer。
 * 这个 Worker 只做三件事：转发请求、补上 Referer/UA、把结果用 CORS 头吐回前端。
 *
 * 部署：Cloudflare Dashboard → Workers & Pages → 创建 Worker → 粘贴本文件 → 部署。
 * 然后把得到的 https://xxx.workers.dev 填进 PWA 的「设置 → 接口地址」。
 *
 * 路由：
 *   GET  /api/search?s=关键词          曲库搜索
 *   GET  /api/resolve?id=歌曲id        解析出音频直链 / 歌词 / 封面
 *   GET  /api/stream?url=&ref=         音频流（透传 Range，支持拖动进度）
 *   GET  /api/lyrics?title=&artist=    LRCLIB 歌词
 *   GET  /api/nc/playlists?cookie=     网易云：我的歌单
 *   GET  /api/nc/tracks?id=&cookie=    网易云：歌单曲目
 *   GET  /api/qq/playlists?cookie=     QQ：我的歌单
 *   GET  /api/qq/tracks?dissid=&cookie= QQ：歌单曲目
 */

const GH = 'https://www.gequhai.com';
const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36';

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
  'Access-Control-Allow-Headers': 'Range,Content-Type,X-Requested-With,X-Custom-Header',
  'Access-Control-Expose-Headers': 'Content-Range,Content-Length,Accept-Ranges',
};

export default {
  async fetch(req) {
    const u = new URL(req.url);
    if (req.method === 'OPTIONS') return new Response(null, { headers: CORS });
    try {
      switch (u.pathname) {
        case '/api/search': return await search(u);
        case '/api/resolve': return await resolve(u);
        case '/api/stream': return await stream(req, u);
        case '/api/lyrics': return await lyrics(u);
        case '/api/nc/playlists': return await ncPlaylists(u);
        case '/api/nc/tracks': return await ncTracks(u);
        case '/api/qq/playlists': return await qqPlaylists(u);
        case '/api/qq/tracks': return await qqTracks(u);
        case '/': return json({ ok: true, service: 'MY音乐 proxy' });
        default: return json({ error: 'not found' }, 404);
      }
    } catch (e) {
      return json({ error: String((e && e.message) || e) }, 500);
    }
  },
};

// ---------------- 曲库（gequhai）----------------

async function search(u) {
  const q = (u.searchParams.get('s') || '').trim();
  if (!q) return json({ songs: [] });
  const path = '/search?s=' + encodeURIComponent(q);
  const r = await ghFetch(path, GH + '/');
  if (r.status !== 200) return json({ songs: [], error: 'upstream ' + r.status }, 200);
  return json({ songs: parseSearch(r.text, q) });
}

async function resolve(u) {
  const id = (u.searchParams.get('id') || '').trim();
  const pageUrl = u.searchParams.get('page') || (GH + '/play/' + id);
  if (!/^https:\/\/www\.gequhai\.com\/play\/\d+$/.test(pageUrl)) {
    return json({ error: 'bad page' }, 400);
  }
  const jar = {};
  const r1 = await ghFetch(new URL(pageUrl).pathname, GH + '/');
  const cookie = mergeCookie(jar, r1.setCookie);
  const html = r1.text;

  const playId = /window\.play_id\s*=\s*'([^']+)'/.exec(html)?.[1];
  const type = /window\.mp3_type\s*=\s*(\d+)/.exec(html)?.[1] || '0';
  if (!playId) return json({ error: 'no play_id' }, 502);

  const r2 = await fetch(GH + '/api/music', {
    method: 'POST',
    headers: {
      'User-Agent': UA,
      Referer: pageUrl,
      Cookie: cookie,
      'X-Requested-With': 'http',
      'X-Custom-Header': 'Key',
      Origin: GH,
      'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
      Accept: 'application/json, text/javascript, */*; q=0.01',
      'Accept-Language': 'zh-CN,zh;q=0.9',
    },
    body: 'id=' + encodeURIComponent(playId) + '&type=' + encodeURIComponent(type),
  });
  let j = {};
  try { j = await r2.json(); } catch (_) { return json({ error: 'bad json' }, 502); }

  return json({
    code: j.code ?? 200,
    url: j.url || '',
    title: jsStr(html, 'mp3_title'),
    artist: jsStr(html, 'mp3_author'),
    cover: jsStr(html, 'mp3_cover'),
    lrc: parseLrc(html),
  });
}

/** 音频流：转发 Range，保证 iOS 的 audio 能 seek */
async function stream(req, u) {
  const target = u.searchParams.get('url');
  if (!target) return new Response('missing url', { status: 400, headers: CORS });
  const ref = u.searchParams.get('ref') || GH + '/';
  const h = { 'User-Agent': UA, Referer: ref, Accept: '*/*' };
  const range = req.headers.get('Range');
  if (range) h.Range = range;

  const r = await fetch(target, { headers: h });
  const headers = new Headers(CORS);
  for (const k of ['Content-Type', 'Content-Length', 'Content-Range', 'Accept-Ranges']) {
    const v = r.headers.get(k);
    if (v) headers.set(k, v);
  }
  if (!headers.has('Accept-Ranges')) headers.set('Accept-Ranges', 'bytes');
  return new Response(r.body, { status: r.status, headers });
}

// ---------------- 歌词（LRCLIB，本身支持 CORS，这里统一入口）----------------

async function lyrics(u) {
  const title = (u.searchParams.get('title') || '').trim();
  const artist = (u.searchParams.get('artist') || '').trim();
  if (!title) return json({ lrc: '' });
  const qs = `track_name=${encodeURIComponent(title)}&artist_name=${encodeURIComponent(artist)}`;
  const r1 = await fetch('https://lrclib.net/api/get?' + qs, {
    headers: { 'User-Agent': 'MYMusicPWA/1.0' },
  });
  if (r1.status === 200) {
    const d = await r1.json();
    const lrc = d.syncedLyrics || d.plainLyrics || '';
    if (lrc) return json({ lrc });
  }
  const r2 = await fetch('https://lrclib.net/api/search?' + qs, {
    headers: { 'User-Agent': 'MYMusicPWA/1.0' },
  });
  if (r2.status === 200) {
    const arr = await r2.json();
    const hit = Array.isArray(arr) ? arr.find((x) => x.syncedLyrics) || arr[0] : null;
    return json({ lrc: (hit && (hit.syncedLyrics || hit.plainLyrics)) || '' });
  }
  return json({ lrc: '' });
}

// ---------------- 网易云导入 ----------------

async function ncPlaylists(u) {
  const cookie = u.searchParams.get('cookie') || '';
  if (!cookie) return json({ error: 'login required' }, 401);
  const acc = await ncGet('/api/nuser/account/get', cookie);
  const uid = acc?.profile?.userId || acc?.account?.id || 0;
  if (!uid) return json({ error: 'account read failed' }, 401);
  const list = await ncGet(`/api/user/playlist?uid=${uid}&limit=100&offset=0`, cookie);
  const arr = Array.isArray(list?.playlist) ? list.playlist : [];
  return json({
    uid,
    playlists: arr.map((p) => ({
      id: p.id,
      name: p.name,
      count: p.trackCount || 0,
      cover: String(p.coverImgUrl || '').replace(/^http:/, 'https:'),
    })),
  });
}

async function ncTracks(u) {
  const id = (u.searchParams.get('id') || '').trim();
  const cookie = u.searchParams.get('cookie') || '';
  if (!id || !cookie) return json({ error: 'bad request' }, 400);
  const detail = await ncGet(`/api/v6/playlist/detail?id=${id}&n=1000`, cookie);
  const tids = (detail?.playlist?.trackIds || []).map((t) => t.id).filter(Boolean);
  if (!tids.length) return json({ songs: [], error: 'empty playlist' });
  const songs = [];
  for (let i = 0; i < tids.length; i += 500) {
    const chunk = tids.slice(i, i + 500);
    const d = await ncGet('/api/song/detail?ids=' + encodeURIComponent('[' + chunk.join(',') + ']'), cookie);
    for (const s of d?.songs || []) {
      if (!s?.name) continue;
      songs.push({
        id: s.id,
        title: s.name,
        artist: (s.ar || s.artists || []).map((a) => a.name).filter(Boolean).join(' / '),
        album: s.al?.name || s.album?.name || '',
        durationMs: s.dt || s.duration || 0,
      });
    }
  }
  return json({ songs });
}

async function ncGet(path, cookie) {
  const r = await fetch('https://music.163.com' + path, {
    headers: {
      'User-Agent': UA,
      Referer: 'https://music.163.com/',
      Accept: 'application/json, text/plain, */*',
      Cookie: cookie,
    },
  });
  try { return await r.json(); } catch (_) { return null; }
}

// ---------------- QQ 音乐导入 ----------------

async function qqPlaylists(u) {
  const cookie = u.searchParams.get('cookie') || '';
  const m = /(?:^|;\s*)uin=(o?[A-Za-z0-9]+)/i.exec(cookie);
  if (!m) return json({ error: 'login required' }, 401);
  const raw = m[1];
  const uin = /^o/i.test(raw) ? raw : 'o' + raw;
  const body =
    `{"comm":{"ct":24,"cv":0,"uin":"${uin}"},"getSongList":{"module":"music.mylist.GetMySongList",` +
    `"method":"GetMySongList","param":{"uin":"${uin}","begin":0,"num":30}}}`;
  const url = 'https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=' + encodeURIComponent(body);
  const r = await fetch(url, {
    headers: { 'User-Agent': UA, Referer: 'https://y.qq.com/n/ryqq/profile', Cookie: cookie },
  });
  let j = {};
  try { j = await r.json(); } catch (_) { return json({ playlists: [], error: 'bad json' }); }
  const node = j?.getSongList;
  if (!node || node.code !== 0) return json({ playlists: [], error: 'qq returned ' + (node?.code ?? j?.code) });
  const arr = node?.data?.songlist || node?.data?.v_songlist || node?.data?.list || [];
  return json({
    playlists: arr.map((p) => ({
      id: String(p.dissid || p.tid || ''),
      name: p.dissname || p.name || '',
      count: p.songnum || p.song_num || 0,
    })).filter((p) => p.id && p.name),
  });
}

async function qqTracks(u) {
  const dissid = (u.searchParams.get('dissid') || '').trim();
  const cookie = u.searchParams.get('cookie') || '';
  if (!/^\d{1,20}$/.test(dissid)) return json({ error: 'bad id' }, 400);
  const url =
    'https://c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg?format=json&inCharset=utf-8&outCharset=utf-8' +
    `&platform=yqq&id=${dissid}&type=1&onlysong=0&new_format=1`;
  const r = await fetch(url, {
    headers: { 'User-Agent': UA, Referer: `https://y.qq.com/n/ryqq/playlist/${dissid}`, Cookie: cookie },
  });
  let j = {};
  try { j = await r.json(); } catch (_) { return json({ songs: [], error: 'bad json' }); }
  const cd = j?.data?.cdlist?.[0];
  const arr = cd?.songlist || [];
  return json({
    name: cd?.dissname || '',
    songs: arr.map((s) => ({
      title: s.songname || s.title || '',
      artist: (s.singer || []).map((x) => x.name).filter(Boolean).join(' / '),
      album: s.albumname || '',
      durationMs: (s.interval || 0) * 1000,
    })).filter((s) => s.title),
  });
}

// ---------------- 工具 ----------------

async function ghFetch(path, referer, cookie = '') {
  const r = await fetch(GH + path, {
    headers: {
      'User-Agent': UA,
      Referer: referer,
      'Accept-Language': 'zh-CN,zh;q=0.9',
      Accept: 'text/html,application/xhtml+xml',
      ...(cookie ? { Cookie: cookie } : {}),
    },
    redirect: 'follow',
  });
  const text = await r.text();
  let setCookies = [];
  try { setCookies = r.headers.getSetCookie ? r.headers.getSetCookie() : []; } catch (_) {}
  return { status: r.status, text, setCookie: setCookies };
}

function mergeCookie(jar, setCookies) {
  for (const line of setCookies || []) {
    const pair = line.split(';')[0];
    const i = pair.indexOf('=');
    if (i > 0) jar[pair.slice(0, i).trim()] = pair.slice(i + 1).trim();
  }
  return Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; ');
}

function parseSearch(html, query) {
  const rows = html.match(/<tr[^>]*>[\s\S]*?<\/tr>/gi) || [];
  const out = [];
  const seenId = new Set();
  const seenKey = new Set();
  for (const block of rows) {
    const hit = /href=["']\/play\/(\d+)["'][^>]*>([\s\S]*?)<\/a>/i.exec(block);
    if (!hit) continue;
    const id = hit[1];
    const title = clean(hit[2]);
    if (!title) continue;
    const tds = [...block.matchAll(/<td[^>]*>([\s\S]*?)<\/td>/gi)].map((m) => clean(m[1]));
    const artist = (tds[2] || tds[tds.length - 1] || '').trim();
    const duration = tds.find((t) => /^\d{1,2}:\d{2}$/.test(t)) || '';
    const album = (tds[3] || '').trim();
    const key = norm(title) + '|' + norm(artist);
    if (seenId.has(id) || seenKey.has(key)) continue;
    seenId.add(id);
    seenKey.add(key);
    out.push({ id, title: title.slice(0, 80), artist: artist.slice(0, 40), album, duration });
  }
  const want = norm(query);
  const score = (r) => {
    const t = norm(r.title);
    const a = norm(r.artist);
    if (t === want) return 0;
    if (t.startsWith(want)) return 1;
    if (t.includes(want)) return 2;
    if (a.includes(want)) return 3;
    return 4;
  };
  return out.sort((x, y) => score(x) - score(y)).slice(0, 50);
}

function parseLrc(html) {
  const m = /id=["']content-lrc2["'][^>]*>([\s\S]*?)<\/div>/i.exec(html);
  if (!m) return '';
  return decodeEntities(
    m[1].replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '')
  ).split('\n').map((s) => s.trim()).filter(Boolean).join('\n').slice(0, 20000);
}

function jsStr(html, name) {
  const m = new RegExp(`window\\.${name}\\s*=\\s*'([^']*)'`).exec(html);
  return m ? m[1].trim() : '';
}

function clean(s) {
  return decodeEntities(String(s).replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ').trim();
}

function decodeEntities(s) {
  return s
    .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&apos;/g, "'");
}

function norm(s) {
  return String(s || '').toLowerCase().replace(/[^\u4e00-\u9fa5a-z0-9]/g, '');
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { ...CORS, 'Content-Type': 'application/json; charset=utf-8' },
  });
}
