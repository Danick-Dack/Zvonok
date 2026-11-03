// Звонок: работа без интернета. Сначала сеть (чтобы приходили обновления расписания),
// без сети сохранённая копия. Чужие сайты не трогаем.
const CACHE = "zvonok-v5";
const FILES = ["./", "./index.html", "./manifest.webmanifest", "./icon-192.png", "./icon-512.png", "./apple-touch-icon.png", "./poster.html", "./changes.json", "./changelog.html", "./feedback.html"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener("fetch", e => {
  const r = e.request;
  if (r.method !== "GET" || new URL(r.url).origin !== location.origin) return;
  e.respondWith(
    fetch(r).then(res => {
      if (res.ok) {
        const copy = res.clone();
        caches.open(CACHE).then(c => c.put(r, copy));
      }
      return res;
    }).catch(() => caches.match(r, {ignoreSearch: true}).then(m => m || caches.match("./index.html")))
  );
});
