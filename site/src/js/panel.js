/* Звонок · панель: свои объявления и скрытие постов в «Новостях».
   Защита держится не на секретности страницы (код открыт), а на ключе GitHub: без него ничего не изменить.
   Ключ вводится один раз и хранится только на этом устройстве, зашифрованный кодом (PBKDF2 600 000 раз + AES-GCM),
   в памяти только пока панель открыта. Код короткий, поэтому главное ограничение — попытки: после 3 ошибок вход
   замедляется, после 10 подряд ключ стирается. Код нигде в репозитории не записан. Через 10 минут без действий
   и при уходе со страницы панель закрывается. Всё, что записано, сборка сайта ещё раз проверяет (tools/update_news.py). */
(function panel() {
  const REPO = "Danick-Dack/Zvonok", FILE = "data/news_admin.json", API = "https://api.github.com/repos/" + REPO;
  const OWN = 1e9, MAX = 2000, IDLE = 10 * 60e3, ITER = 600000;
  let token = "", idleT = 0;
  const enc = new TextEncoder(), dec = new TextDecoder();
  const b64 = u8 => btoa(String.fromCharCode(...u8));
  const unb64 = s => Uint8Array.from(atob(s), c => c.charCodeAt(0));
  const show = id => ["lock", "setup", "desk"].forEach(x => { $(x).hidden = x !== id; });
  const err = (id, t) => { $(id).textContent = t || ""; $(id).hidden = !t; };

  async function key(pw, salt) {
    const base = await crypto.subtle.importKey("raw", enc.encode(pw), "PBKDF2", false, ["deriveKey"]);
    return crypto.subtle.deriveKey({name: "PBKDF2", salt, iterations: ITER, hash: "SHA-256"}, base, {name: "AES-GCM", length: 256}, false, ["encrypt", "decrypt"]);
  }
  async function seal(tok, pw) {
    const salt = crypto.getRandomValues(new Uint8Array(16)), iv = crypto.getRandomValues(new Uint8Array(12));
    const ct = new Uint8Array(await crypto.subtle.encrypt({name: "AES-GCM", iv}, await key(pw, salt), enc.encode(tok)));
    store.set("panel", {v: 1, salt: b64(salt), iv: b64(iv), ct: b64(ct)});
  }
  async function open(pw) {
    const s = store.get("panel", null);
    const plain = await crypto.subtle.decrypt({name: "AES-GCM", iv: unb64(s.iv)}, await key(pw, unb64(s.salt)), unb64(s.ct));
    return dec.decode(plain);
  }

  async function gh(path, opts) {
    const r = await fetch(API + path, Object.assign({cache: "no-store", credentials: "omit", referrerPolicy: "no-referrer"}, opts, {
      headers: Object.assign({Accept: "application/vnd.github+json", Authorization: "Bearer " + token, "X-GitHub-Api-Version": "2022-11-28"}, (opts || {}).headers)}));
    if (r.status === 401) { lock(); throw new Error("Ключ GitHub не подходит или истёк. Войди заново с новым ключом"); }
    if (!r.ok) throw new Error(r.status === 409 ? "Файл только что изменили в другом месте. Обнови страницу" : "GitHub ответил " + r.status);
    return r.json();
  }
  /** Ключ должен давать запись именно в репозиторий «Звонка». */
  async function check(tok) {
    token = tok;
    const repo = await gh("");
    if (!repo.permissions || !repo.permissions.push) throw new Error("У ключа нет права записи в " + REPO);
  }

  function lock() {
    token = ""; clearTimeout(idleT);
    $("list").replaceChildren(); $("pw").value = "";
    show(store.get("panel", null) ? "lock" : "setup");
  }
  function touch() { clearTimeout(idleT); if (token) idleT = setTimeout(lock, IDLE); }

  /* перебор пароля: после 3 ошибок пауза растёт вдвое (до 15 минут), после 10 ключ стирается */
  const fails = () => store.get("panelFail", {n: 0, until: 0});
  $("lock").onsubmit = async e => {
    e.preventDefault(); err("lockErr");
    const f = fails();
    if (Date.now() < f.until) return err("lockErr", "Слишком много попыток. Подожди " + Math.ceil((f.until - Date.now()) / 1000) + " с");
    try {
      token = await open($("pw").value);
      store.del("panelFail");
      $("pw").value = "";
      show("desk"); touch(); await load();
    } catch (x) {
      token = "";
      if (x && x.name !== "OperationError") return err("lockErr", x.message || "Не получилось войти");
      const n = f.n + 1;
      if (n >= 10) { store.del("panel"); store.del("panelFail"); lock(); return err("setupErr", "10 неверных кодов подряд: ключ стёрт. Введи ключ заново"); }
      store.set("panelFail", {n, until: n >= 3 ? Date.now() + Math.min(900, 2 ** (n - 2) * 15) * 1000 : 0});
      err("lockErr", "Неверный код. Осталось попыток: " + (10 - n));
    }
  };
  $("forget").onclick = () => { store.del("panel"); store.del("panelFail"); lock(); toast("Ключ стёрт с этого устройства"); };

  $("setup").onsubmit = async e => {
    e.preventDefault(); err("setupErr");
    const tok = $("tok").value.trim(), pw = $("pw1").value;
    if (!/^(github_pat_|ghp_)[A-Za-z0-9_]{20,}$/.test(tok)) return err("setupErr", "Это не похоже на ключ GitHub");
    if (pw.length < 4) return err("setupErr", "Код короче 4 символов");
    if (pw !== $("pw2").value) return err("setupErr", "Коды не совпали");
    try {
      await check(tok);
      await seal(tok, pw);
      $("tok").value = $("pw1").value = $("pw2").value = "";
      show("desk"); touch(); await load();
    } catch (x) { token = ""; err("setupErr", x.message || "Ключ не подошёл"); }
  };
  $("lockBtn").onclick = lock;
  addEventListener("pagehide", lock);
  ["pointerdown", "keydown"].forEach(ev => addEventListener(ev, touch, {passive: true}));

  /* ---------- данные ---------- */
  let admin = {posts: [], hidden: []}, sha = "", channel = [];
  const clean = j => ({posts: (Array.isArray(j.posts) ? j.posts : []).filter(p => p && Number.isInteger(p.id) && p.id > OWN && typeof p.text === "string"),
    hidden: (Array.isArray(j.hidden) ? j.hidden : []).filter(Number.isInteger)});
  async function load() {
    err("deskErr");
    try {
      const f = await gh("/contents/" + FILE + "?ref=main");
      sha = f.sha;
      admin = clean(JSON.parse(dec.decode(unb64(f.content.replace(/\s/g, "")))));
      const site = await fetch("news.json", {cache: "no-store"}).then(r => r.ok ? r.json() : {items: []}).catch(() => ({items: []}));
      channel = (site.items || []).filter(p => p && Number.isInteger(p.id) && p.id > 0 && p.id < OWN);
      render();
    } catch (x) { err("deskErr", x.message || "Не получилось загрузить ленту"); }
  }
  async function save(message) {
    const body = JSON.stringify(admin, null, 2) + "\n";
    const r = await gh("/contents/" + FILE, {method: "PUT", body: JSON.stringify({message, content: b64(enc.encode(body)), sha, branch: "main"})});
    sha = r.content.sha;
    toast("Сохранено. Сайт и приложения обновятся через 1–3 минуты");
  }
  async function act(message, change) {
    const before = JSON.stringify(admin);
    err("deskErr"); change(); render();
    try { await save(message); } catch (x) { admin = JSON.parse(before); render(); err("deskErr", x.message); }
  }

  function item(p, kind) {
    const d = new Date(p.date), text = String(p.text || (p.pics ? "Пост с фото" : "Пост")).slice(0, 160);
    const btn = kind === "hidden" ? {text: "Вернуть", click: () => act("Новости: пост " + p.id + " снова на сайте", () => { admin.hidden = admin.hidden.filter(x => x !== p.id); })}
      : {text: "Удалить", click: () => act(kind === "own" ? "Новости: удалено объявление" : "Новости: пост " + p.id + " убран с сайта", () => {
        if (kind === "own") admin.posts = admin.posts.filter(x => x.id !== p.id); else admin.hidden.push(p.id); })};
    return h("div", {class: "card", style: "padding:14px"},
      h("p", {class: "note", text: (kind === "own" ? "Объявление · " : kind === "hidden" ? "Скрыт · " : "Канал · ") + (isNaN(d) ? "" : d.toLocaleString("ru-RU", {dateStyle: "medium", timeStyle: "short"}))}),
      h("p", {text, style: "margin:6px 0 10px;white-space:pre-line"}),
      h("button", {class: "btn btn-quiet btn-sm", type: "button", text: btn.text, onclick: btn.click}));
  }
  function render() {
    const hidden = new Set(admin.hidden), out = [];
    admin.posts.slice().sort((a, b) => b.id - a.id).forEach(p => out.push(item(p, "own")));
    channel.forEach(p => out.push(item(p, hidden.has(p.id) ? "hidden" : "channel")));
    // скрытых постов уже нет в news.json сайта: показываем их по номеру, чтобы можно было вернуть
    admin.hidden.filter(id => !channel.some(p => p.id === id)).forEach(id => out.push(item({id, date: "", text: "Пост канала №" + id}, "hidden")));
    $("list").replaceChildren(...(out.length ? out : [h("p", {class: "note", text: "В ленте пока пусто"})]));
  }

  $("text").oninput = () => { $("cnt").textContent = $("text").value.length + " / " + MAX; };
  $("add").onsubmit = e => {
    e.preventDefault();
    const text = $("text").value.trim().slice(0, MAX);
    if (!text) return;
    const now = new Date(), id = OWN + Math.floor((now - Date.UTC(2026, 0, 1)) / 1000);
    act("Новости: новое объявление", () => { admin.posts = [{id, date: now.toISOString().replace(/\.\d+Z$/, "Z"), text}].concat(admin.posts).slice(0, 30); })
      .then(() => { if (!$("deskErr").textContent) { $("text").value = ""; $("text").oninput(); } });
  };

  if (!window.crypto || !crypto.subtle) { show("lock"); err("lockErr", "Этот браузер не умеет шифровать. Открой панель в свежем Chrome или Safari"); return; }
  lock();
})();
