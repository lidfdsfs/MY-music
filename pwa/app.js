'use strict';

const $ = (id) => document.getElementById(id);
const audio = $('audio');

// ---------------- 本地存储 ----------------
const LS = {
  cfg: 'my_music_cfg',
  favs: 'my_music_favs',
  history: 'my_music_history',
  nc: 'my_music_nc_cookie',
  qq: 'my_music_qq_cookie',
};
function loadJSON(k, def) {
  try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : def; } catch (_) { return def; }
}
function saveJSON(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (_) {} }

const cfg = Object.assign({ api: '', autoNext: true }, loadJSON(LS.cfg, {}));
let favs = loadJSON(LS.favs, []);
let history = loadJSON(LS.history, []);

const state = {
  tab: 'search',
  results: [],
  queue: [],
  index: -1,
  current: null,
  lrcLines: [],
  lrcIndex: -1,
};

// ---------------- 通用 ----------------
let toastTimer = null;
function toast(msg) {
  const el = $('toast');
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 2200);
}

function fmtTime(sec) {
  if (!isFinite(sec) || sec < 0) sec = 0;
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return m + ':' + String(s).padStart(2, '0');
}

function songKey(s) {
  return (s.title || '').trim().toLowerCase() + '|' + (s.artist || '').trim().toLowerCase();
}

function isFav(s) {
  const k = songKey(s);
  return favs.some((f) => songKey(f) === k);
}

function toggleFav(s) {
  const k = songKey(s);
  const i = favs.findIndex((f) => songKey(f) === k);
  if (i >= 0) { favs.splice(i, 1); toast('已取消收藏'); }
  else { favs.unshift({ title: s.title, artist: s.artist, cover: s.cover || '', source: s.source, id: s.id, auId: s.auId }); toast('已收藏'); }
  saveJSON(LS.favs, favs);
  renderResults();
  renderFavs();
}

// ---------------- 音频解锁（iOS 要求用户手势） ----------------
const SILENT = 'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=';
let audioUnlocked = false;
function unlockAudio() {
  if (audioUnlocked) return;
  audioUnlocked = true;
  try {
    audio.src = SILENT;
    audio.play().then(() => audio.pause()).catch(() => {});
  } catch (_) {}
}
document.addEventListener('touchstart', unlockAudio, { once: true, passive: true });
document.addEventListener('click', unlockAudio, { once: true });

// ---------------- 搜索 ----------------
async function searchApi(q) {
  if (!cfg.api) throw new Error('未填写接口地址');
  const r = await fetch(cfg.api + '/api/search?s=' + encodeURIComponent(q));
  if (!r.ok) throw new Error('搜索失败：接口返回错误');
  const d = await r.json();
  return (d.songs || []).map((s) => Object.assign({}, s, { source: 'gh', keyword: q }));
}

let searchSeq = 0;
async function doSearch(q) {
  q = (q || '').trim();
  if (!q) return;
  addHistory(q);
  const seq = ++searchSeq;
  $('searchLoading').hidden = false;
  $('searchHint').hidden = true;
  $('historyWrap').hidden = true;
  $('searchList').innerHTML = '';
  let list = [];
  try { list = await searchApi(q); } catch (e) { toast('搜索失败'); }
  if (seq !== searchSeq) return;
  $('searchLoading').hidden = true;
  state.results = list;
  renderResults();
  if (!list.length) {
    $('searchHint').textContent = cfg.api ? '没有找到结果，换个关键词试试' : '请先在「设置」里填写接口地址';
    $('searchHint').hidden = false;
  }
}

function addHistory(q) {
  history = [q].concat(history.filter((x) => x !== q)).slice(0, 12);
  saveJSON(LS.history, history);
}

function renderHistory() {
  const wrap = $('historyWrap');
  if (!history.length) { wrap.hidden = true; return; }
  wrap.hidden = false;
  wrap.innerHTML = '';
  history.forEach((h) => {
    const b = document.createElement('button');
    b.className = 'chip';
    b.textContent = h;
    b.onclick = () => { $('searchInput').value = h; doSearch(h); };
    wrap.appendChild(b);
  });
  const c = document.createElement('button');
  c.className = 'chip clear';
  c.textContent = '清空';
  c.onclick = () => { history = []; saveJSON(LS.history, history); renderHistory(); };
  wrap.appendChild(c);
}

function renderResults() {
  const list = $('searchList');
  list.innerHTML = '';
  state.results.forEach((s) => list.appendChild(songRow(s, state.results)));
}

function songRow(s, queue) {
  const row = document.createElement('div');
  row.className = 'row';

  const cover = document.createElement('img');
  cover.className = 'row-cover';
  cover.alt = '';
  if (s.cover) cover.src = s.cover;

  const main = document.createElement('div');
  main.className = 'row-main';

  const title = document.createElement('div');
  title.className = 'row-title';
  title.textContent = s.title;

  const sub = document.createElement('div');
  sub.className = 'row-sub';
  sub.textContent = [s.artist || '未知歌手', s.album, s.duration].filter(Boolean).join(' · ');

  main.appendChild(title);
  main.appendChild(sub);

  const fav = document.createElement('button');
  fav.className = 'row-act' + (isFav(s) ? ' fav' : '');
  fav.textContent = isFav(s) ? '♥' : '♡';
  fav.onclick = (e) => { e.stopPropagation(); toggleFav(s); };

  row.appendChild(cover);
  row.appendChild(main);
  row.appendChild(fav);
  row.onclick = () => {
    const list = queue || state.results;
    playAt(list, list.indexOf(s));
  };
  return row;
}

// ---------------- 播放 ----------------
async function resolveStreamUrl(song) {
  if (!cfg.api) throw new Error('未填写接口地址');
  if (song.source === 'import') {
    const hits = await searchApi(song.title + ' ' + (song.artist || ''));
    const hit = hits[0];
    if (!hit) throw new Error('没有找到音源');
    return resolveStreamUrl(hit);
  }
  // gequhai
  const r = await fetch(cfg.api + '/api/resolve?id=' + encodeURIComponent(song.id));
  const d = await r.json();
  if (!d || !d.url) {
    if (d && d.code === 430) throw new Error('该曲暂无版权音源');
    if (d && d.code === 429) throw new Error('请求太频繁，稍后再试');
    throw new Error('解析失败');
  }
  if (d.cover && !song.cover) song.cover = d.cover;
  if (d.title) song.title = d.title;
  if (d.artist) song.artist = d.artist;
  state.lrcLines = parseLrc(d.lrc || '');
  if (!state.lrcLines.length) loadLyrics(song);
  return cfg.api + '/api/stream?url=' + encodeURIComponent(d.url);
}

async function playAt(list, index) {
  if (index < 0 || index >= list.length) return;
  state.queue = list;
  state.index = index;
  const song = list[index];
  state.current = song;
  state.lrcLines = [];
  updatePlayerUi(song);
  $('playerLyric').textContent = '正在获取音源…';
  try {
    const url = await resolveStreamUrl(song);
    playUrl(url, song);
  } catch (e) {
    toast(e.message || '播放失败');
    $('playerLyric').textContent = e.message || '播放失败';
  }
}

function playUrl(url, song) {
  unlockAudio();
  audio.src = url;
  audio.load();
  const p = audio.play();
  if (p && p.catch) p.catch((e) => { toast('无法播放：' + (e && e.message || '')); });
  setupMediaSession(song);
  updatePlayerUi(song);
  renderLyric('');
}

let audioErrorCount = 0;
audio.addEventListener('error', () => {
  if (!state.current) return;
  toast('音频加载失败，尝试下一首');
  audioErrorCount++;
  if (audioErrorCount <= 2) next();
  else audioErrorCount = 0;
});
audio.addEventListener('play', () => { updatePlayIcons(true); });
audio.addEventListener('pause', () => { updatePlayIcons(false); });
audio.addEventListener('ended', () => {
  if (cfg.autoNext) next();
  else updatePlayIcons(false);
});
audio.addEventListener('timeupdate', () => {
  if (!audio.duration) return;
  const pct = (audio.currentTime / audio.duration) * 1000;
  $('seek').value = String(pct);
  $('tCur').textContent = fmtTime(audio.currentTime);
  $('tDur').textContent = fmtTime(audio.duration);
  syncLyric(audio.currentTime);
});

function next() {
  if (!state.queue.length) return;
  const i = (state.index + 1) % state.queue.length;
  playAt(state.queue, i);
}
function prev() {
  if (!state.queue.length) return;
  const i = (state.index - 1 + state.queue.length) % state.queue.length;
  playAt(state.queue, i);
}

function setupMediaSession(song) {
  if (!('mediaSession' in navigator)) return;
  try {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: song.title || '',
      artist: song.artist || '',
      album: song.album || 'MY音乐',
      artwork: song.cover ? [{ src: song.cover, sizes: '512x512' }] : [],
    });
    navigator.mediaSession.setActionHandler('play', () => audio.play().catch(() => {}));
    navigator.mediaSession.setActionHandler('pause', () => audio.pause());
    navigator.mediaSession.setActionHandler('previoustrack', prev);
    navigator.mediaSession.setActionHandler('nexttrack', next);
    navigator.mediaSession.setActionHandler('seekto', (d) => {
      if (d.seekTime != null) audio.currentTime = d.seekTime;
    });
  } catch (_) {}
}

function updatePlayIcons(playing) {
  const t = playing ? '⏸' : '▶';
  $('playBtn').textContent = t;
  $('miniPlay').textContent = t;
  if ('mediaSession' in navigator) {
    try { navigator.mediaSession.playbackState = playing ? 'playing' : 'paused'; } catch (_) {}
  }
}

function updatePlayerUi(song) {
  $('miniBar').hidden = false;
  $('miniTitle').textContent = song.title || '—';
  $('miniArtist').textContent = song.artist || '—';
  $('playerTitle').textContent = song.title || '—';
  $('playerArtist').textContent = song.artist || '—';
  if (song.cover) {
    $('miniCover').src = song.cover;
    $('playerCover').src = song.cover;
    $('playerBg').style.backgroundImage = 'url("' + song.cover + '")';
  } else {
    $('miniCover').removeAttribute('src');
    $('playerCover').removeAttribute('src');
    $('playerBg').style.backgroundImage = 'none';
  }
}

// ---------------- 歌词 ----------------
function parseLrc(text) {
  if (!text) return [];
  const out = [];
  text.split('\n').forEach((line) => {
    const m = /\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]\s*(.*)/.exec(line);
    if (m) {
      const t = parseInt(m[1], 10) * 60 + parseInt(m[2], 10) + (m[3] ? parseInt(m[3], 10) / (m[3].length === 3 ? 1000 : 100) : 0);
      const txt = m[4].trim();
      if (txt) out.push({ t, text: txt });
    }
  });
  return out.sort((a, b) => a.t - b.t);
}

async function loadLyrics(song) {
  if (!cfg.api) return;
  try {
    const r = await fetch(cfg.api + '/api/lyrics?title=' + encodeURIComponent(song.title) +
      '&artist=' + encodeURIComponent(song.artist || ''));
    const d = await r.json();
    state.lrcLines = parseLrc(d.lrc || '');
    if (state.lrcLines.length) syncLyric(audio.currentTime || 0);
    else renderLyric(d.lrc || '暂无歌词');
  } catch (_) {}
}

function renderLyric(text) {
  $('playerLyric').textContent = text || '暂无歌词';
}

function syncLyric(t) {
  if (!state.lrcLines.length) return;
  let idx = -1;
  for (let i = 0; i < state.lrcLines.length; i++) {
    if (state.lrcLines[i].t <= t + 0.25) idx = i; else break;
  }
  if (idx === state.lrcIndex) return;
  state.lrcIndex = idx;
  const el = $('playerLyric');
  el.textContent = state.lrcLines.map((l, i) => (i === idx ? '▶ ' + l.text : l.text)).join('\n');
  const active = el.querySelector ? null : null;
  // 让当前行尽量可见
  if (idx >= 0) {
    const lineHeight = 26;
    el.scrollTop = Math.max(0, idx * lineHeight - el.clientHeight / 2);
  }
}

// ---------------- 收藏 ----------------
function renderFavs() {
  $('favCount').textContent = favs.length + ' 首';
  const list = $('favList');
  list.innerHTML = '';
  $('favEmpty').hidden = favs.length > 0;
  favs.forEach((s) => {
    const row = songRow(Object.assign({ source: s.source || 'gh', keyword: (s.title + ' ' + (s.artist || '')).trim() }, s), favs.map(toSong));
    list.appendChild(row);
  });
}
function toSong(f) {
  return Object.assign({ source: f.source || 'gh', keyword: (f.title + ' ' + (f.artist || '')).trim() }, f);
}

// ---------------- 导入 ----------------
function normalizeKey(t, a) {
  const n = (x) => String(x || '').toLowerCase().replace(/[^\u4e00-\u9fa5a-z0-9]/g, '');
  return n(t) + '|' + n(a);
}

function addSongs(songs, sourceLabel) {
  let added = 0;
  const seen = new Set(favs.map((f) => normalizeKey(f.title, f.artist)));
  songs.forEach((s) => {
    if (!s.title) return;
    const k = normalizeKey(s.title, s.artist);
    if (seen.has(k)) return;
    seen.add(k);
    favs.push({ title: s.title, artist: s.artist || '', cover: s.cover || '', source: 'import', id: '', keyword: (s.title + ' ' + (s.artist || '')).trim() });
    added++;
  });
  saveJSON(LS.favs, favs);
  renderFavs();
  toast(added > 0 ? ('已导入 ' + added + ' 首到收藏') : ('这些歌都已在收藏'));
  return added;
}

function parsePaste(text) {
  const out = [];
  const seen = new Set();
  text.split('\n').forEach((raw) => {
    let s = raw.trim();
    if (!s || /^https?:/.test(s)) return;
    s = s.replace(/^\d{1,3}\s*[.、,，)）:：\-]\s*/, '').trim();
    if (s.length < 2 || s.length > 80) return;
    const parts = s.split(/\s+[-–—－]\s+|\s*[-–—－]\s*/).map((x) => x.trim()).filter(Boolean);
    const title = parts[0] || s;
    const artist = parts.slice(1).join(' / ');
    if (!title) return;
    const k = normalizeKey(title, artist);
    if (seen.has(k)) return;
    seen.add(k);
    out.push({ title: title.slice(0, 80), artist: artist.slice(0, 60) });
  });
  return out;
}

async function ncLoad() {
  const cookie = $('ncCookie').value.trim();
  if (!cookie) { toast('先粘贴网易云 Cookie'); return; }
  if (!cfg.api) { toast('请先在设置里填写接口地址'); return; }
  localStorage.setItem(LS.nc, cookie);
  $('ncList').innerHTML = '<div class="loading"><span class="spinner"></span>读取中…</div>';
  try {
    const r = await fetch(cfg.api + '/api/nc/playlists?cookie=' + encodeURIComponent(cookie));
    const d = await r.json();
    if (!d.playlists) { $('ncList').innerHTML = '<div class="muted small">读取失败：' + (d.error || '未知错误') + '</div>'; return; }
    renderPlaylistList($('ncList'), d.playlists, 'nc', cookie);
  } catch (e) {
    $('ncList').innerHTML = '<div class="muted small">读取失败</div>';
  }
}

async function qqLoad() {
  const cookie = $('qqCookie').value.trim();
  if (!cookie) { toast('先粘贴 QQ 音乐 Cookie'); return; }
  if (!cfg.api) { toast('请先在设置里填写接口地址'); return; }
  localStorage.setItem(LS.qq, cookie);
  $('qqList').innerHTML = '<div class="loading"><span class="spinner"></span>读取中…</div>';
  try {
    const r = await fetch(cfg.api + '/api/qq/playlists?cookie=' + encodeURIComponent(cookie));
    const d = await r.json();
    if (!d.playlists || !d.playlists.length) {
      $('qqList').innerHTML = '<div class="muted small">未列出歌单' + (d.error ? ('：' + d.error) : '') + '，可用上方粘贴导入</div>';
      return;
    }
    renderPlaylistList($('qqList'), d.playlists, 'qq', cookie);
  } catch (e) {
    $('qqList').innerHTML = '<div class="muted small">读取失败</div>';
  }
}

function renderPlaylistList(container, playlists, kind, cookie) {
  container.innerHTML = '';
  playlists.forEach((p) => {
    const row = document.createElement('div');
    row.className = 'row';
    const cover = document.createElement('img');
    cover.className = 'row-cover';
    cover.alt = '';
    if (p.cover) cover.src = p.cover;
    const main = document.createElement('div');
    main.className = 'row-main';
    const title = document.createElement('div');
    title.className = 'row-title';
    title.textContent = p.name;
    const sub = document.createElement('div');
    sub.className = 'row-sub';
    sub.textContent = (p.count || 0) + ' 首';
    main.appendChild(title);
    main.appendChild(sub);
    const btn = document.createElement('button');
    btn.className = 'pillbtn primary';
    btn.textContent = '导入';
    btn.onclick = () => importPlaylist(kind, p, cookie, btn);
    row.appendChild(cover);
    row.appendChild(main);
    row.appendChild(btn);
    container.appendChild(row);
  });
}

async function importPlaylist(kind, p, cookie, btn) {
  btn.disabled = true;
  btn.textContent = '导入中…';
  try {
    const url = kind === 'nc'
      ? cfg.api + '/api/nc/tracks?id=' + encodeURIComponent(p.id) + '&cookie=' + encodeURIComponent(cookie)
      : cfg.api + '/api/qq/tracks?dissid=' + encodeURIComponent(p.id) + '&cookie=' + encodeURIComponent(cookie);
    const r = await fetch(url);
    const d = await r.json();
    if (!d.songs || !d.songs.length) { toast('没读到曲目'); btn.textContent = '导入'; btn.disabled = false; return; }
    addSongs(d.songs, p.name);
    btn.textContent = '已导入';
  } catch (e) {
    toast('导入失败');
    btn.textContent = '导入';
    btn.disabled = false;
  }
}

// ---------------- 页面切换 ----------------
function switchTab(tab) {
  state.tab = tab;
  document.querySelectorAll('.tab').forEach((b) => b.classList.toggle('active', b.dataset.tab === tab));
  document.querySelectorAll('.page').forEach((p) => p.classList.toggle('active', p.id === 'page-' + tab));
  const titles = { search: '搜索', fav: '收藏', import: '导入', settings: '设置' };
  $('pageTitle').textContent = titles[tab] || '';
  $('searchBox').style.display = tab === 'search' ? 'flex' : 'none';
  if (tab === 'search') renderHistory();
  if (tab === 'fav') renderFavs();
}

// ---------------- 事件绑定 ----------------
document.querySelectorAll('.tab').forEach((b) => {
  b.onclick = () => switchTab(b.dataset.tab);
});

$('searchInput').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { e.preventDefault(); doSearch(e.target.value); e.target.blur(); }
});
$('searchInput').addEventListener('input', (e) => {
  $('searchClear').hidden = !e.target.value;
});
$('searchClear').onclick = () => {
  $('searchInput').value = '';
  $('searchClear').hidden = true;
  state.results = [];
  $('searchList').innerHTML = '';
  $('searchHint').hidden = false;
  renderHistory();
};

$('miniPlay').onclick = () => { audio.paused ? audio.play().catch(() => {}) : audio.pause(); };
$('miniNext').onclick = next;
$('miniBar').onclick = (e) => {
  if (e.target.closest('button')) return;
  $('playerPage').hidden = false;
  requestAnimationFrame(() => $('playerPage').classList.add('show'));
};
$('playerBack').onclick = () => {
  $('playerPage').classList.remove('show');
  setTimeout(() => { $('playerPage').hidden = true; }, 320);
};
$('playBtn').onclick = () => { audio.paused ? audio.play().catch(() => {}) : audio.pause(); };
$('prevBtn').onclick = prev;
$('nextBtn').onclick = next;
$('seek').oninput = (e) => {
  if (!audio.duration) return;
  audio.currentTime = (parseFloat(e.target.value) / 1000) * audio.duration;
};
$('favPlayAll').onclick = () => {
  if (!favs.length) return;
  playAt(favs.map(toSong), 0);
};

$('pasteImport').onclick = () => {
  const songs = parsePaste($('pasteText').value);
  if (!songs.length) { toast('没识别到歌曲，每行「歌名 - 歌手」'); return; }
  addSongs(songs, '粘贴');
  $('pasteText').value = '';
};

$('ncLoad').onclick = ncLoad;
$('ncClear').onclick = () => { localStorage.removeItem(LS.nc); $('ncCookie').value = ''; $('ncList').innerHTML = ''; toast('已清除'); };
$('qqLoad').onclick = qqLoad;
$('qqClear').onclick = () => { localStorage.removeItem(LS.qq); $('qqCookie').value = ''; $('qqList').innerHTML = ''; toast('已清除'); };

$('saveApi').onclick = () => {
  let v = $('apiUrl').value.trim();
  if (v && !/^https:\/\//.test(v)) { toast('请填写 https:// 开头的地址'); return; }
  v = v.replace(/\/+$/, '');
  cfg.api = v;
  saveJSON(LS.cfg, cfg);
  toast(v ? '接口地址已保存' : '已清空接口地址');
};
$('autoNext').onchange = (e) => { cfg.autoNext = e.target.checked; saveJSON(LS.cfg, cfg); };
$('clearData').onclick = () => {
  if (!confirm('清空收藏、历史和账号 Cookie？')) return;
  favs = []; history = [];
  saveJSON(LS.favs, favs); saveJSON(LS.history, history);
  localStorage.removeItem(LS.nc); localStorage.removeItem(LS.qq);
  renderFavs(); renderHistory();
  toast('已清空');
};

// ---------------- 启动 ----------------
$('apiUrl').value = cfg.api || '';
$('autoNext').checked = cfg.autoNext !== false;
$('ncCookie').value = localStorage.getItem(LS.nc) || '';
$('qqCookie').value = localStorage.getItem(LS.qq) || '';
renderHistory();
renderFavs();
switchTab('search');

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  });
}
