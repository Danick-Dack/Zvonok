// Звонок: работа без интернета и тихие обновления.
// Страницы и данные: сначала сеть (чтобы приходили новое расписание и замены), но не дольше 3 секунд,
// дальше сохранённая копия. Иконки и прочее: сразу из кэша. Чужие сайты и запросы не трогаем.
// Версия меняется при каждой сборке с изменениями: новый воркер ставится сам, старый кэш удаляется.
// localStorage (класс, своё расписание, настройки) воркер не трогает.
const VERSION = "133c68c943";
const CACHE = "zvonok-" + VERSION;
const SHELL = ["./", "index.html", "about.html", "make.html", "iphone.html", "android.html", "changelog.html", "feedback.html", "poster.html", "manifest.webmanifest", "icon-192.png", "icon-512.png", "icon-maskable.png", "apple-touch-icon.png", "changes.json", "olympiads.json"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE)
    .then(c => c.addAll(SHELL.map(u => new Request(u, {cache: "reload"}))))
    .then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k.startsWith("zvonok-") && k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

// Кэшируем без ?параметров: feedback.html?about=… и подобные не плодят копий. И держим не больше 40 адресов
// сверх оболочки (самые старые уходят первыми), чтобы кэш не рос и браузер не упирался в квоту.
// Оболочку (страницы и иконки для работы без сети) не трогаем никогда.
const EXTRA_LIMIT = 40;
function trim(c) {
  const keep = new Set(SHELL.map(u => new URL(u, self.registration.scope).href));
  return c.keys().then(keys => {
    const extra = keys.filter(r => !keep.has(r.url));
    return Promise.all(extra.slice(0, Math.max(0, extra.length - EXTRA_LIMIT)).map(r => c.delete(r)));
  });
}
function save(req, res) {
  if (res && res.ok && res.type === "basic") {
    const copy = res.clone(), url = new URL(req.url);
    caches.open(CACHE).then(c => c.put(url.origin + url.pathname, copy).then(() => trim(c))).catch(() => {});
  }
  return res;
}

// сеть с таймаутом: в подвале и в метро не ждём вечно, отдаём копию; ответ сети всё равно сохранится
function networkFirst(req, fallback) {
  return new Promise(resolve => {
    let done = false;
    const fromCache = () => caches.match(req, {ignoreSearch: true})
      .then(m => m || (fallback ? caches.match(fallback) : null))
      .then(m => m || new Response("Нет сети", {status: 503, headers: {"Content-Type": "text/plain; charset=utf-8"}}));
    const timer = setTimeout(() => fromCache().then(m => { if (!done && m.status !== 503) { done = true; resolve(m); } }), 3000);
    fetch(req).then(res => {
      save(req, res);
      if (!done) { done = true; clearTimeout(timer); resolve(res); }
    }).catch(() => {
      clearTimeout(timer);
      if (!done) { done = true; fromCache().then(resolve); }
    });
  });
}

self.addEventListener("fetch", e => {
  const req = e.request, url = new URL(req.url);
  if (req.method !== "GET" || url.origin !== location.origin) return;
  if (req.mode === "navigate" || /\.html$/.test(url.pathname)) {
    e.respondWith(networkFirst(req, /\/about$/.test(url.pathname) ? "about.html" : "index.html")); // «О проекте» живёт по адресу /about
  } else if (/\.json$/.test(url.pathname)) {
    e.respondWith(networkFirst(req));
  } else if (/\.ics$/.test(url.pathname)) {
    return; // календари берут сами приложения календаря, всегда свежие
  } else {
    e.respondWith(caches.match(req).then(m => m || fetch(req).then(res => save(req, res))));
  }
});
