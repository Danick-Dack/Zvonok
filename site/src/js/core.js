"use strict";
/* Звонок · общее для всех страниц: DOM, хранилище, время, тема, офлайн */

const $ = id => document.getElementById(id);
const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

// Наш карманный createElement. Текст кладём через textContent, поэтому никакой HTML из расписания или замен
// не выполнится. Работает уже давно и всем нравится, трогать осторожно.
function h(tag, attrs, ...kids) {
  const e = document.createElement(tag);
  if (attrs) for (const k in attrs) {
    const v = attrs[k];
    if (v == null || v === false) continue;
    if (k === "class") e.className = v;
    else if (k === "text") e.textContent = v;
    else if (k === "style") e.style.cssText = v;
    else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else e.setAttribute(k, v === true ? "" : v);
  }
  for (const c of kids.flat(Infinity)) if (c != null && c !== false) e.append(c.nodeType ? c : document.createTextNode(String(c)));
  return e;
}
const SVGNS = "http://www.w3.org/2000/svg";
/** Иконка из спрайта страницы (сборка кладёт в страницу только нужные значки). */
function icon(name, cls) {
  const s = document.createElementNS(SVGNS, "svg"), u = document.createElementNS(SVGNS, "use");
  s.setAttribute("class", "icon" + (cls ? " " + cls : "")); s.setAttribute("aria-hidden", "true");
  u.setAttribute("href", "#i-" + name); s.append(u);
  return s;
}

// localStorage, который не падает: в приватном режиме Safari и в браузере Telegram он любит кидаться исключениями.
const store = {
  get(k, d) { try { const v = localStorage.getItem("zv." + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem("zv." + k, JSON.stringify(v)); } catch (e) {} },
  del(k) { try { localStorage.removeItem("zv." + k); } catch (e) {} }
};

/* Цвета виджетов из конструктора. Код темы уходит внутрь скрипта для iPhone и в приложение на Android,
   поэтому из хранилища берём только известные поля с правильными значениями, остальное по умолчанию. */
const TH_DEFAULT = {bg: "000000", ac: "FFFFFF", op: 100, r: 26, s: 1, p: 1, n: 1, d: 1, sc: 0, tx: ""};
function cleanTheme(raw) {
  const o = raw && typeof raw === "object" ? raw : {}, t = Object.assign({}, TH_DEFAULT);
  const hex = v => typeof v === "string" && /^[0-9a-f]{6}$/i.test(v) ? v.toUpperCase() : null;
  const int = (v, lo, hi) => { const n = typeof v === "string" && /^\d{1,3}$/.test(v) ? +v : v; return Number.isInteger(n) && n >= lo && n <= hi ? n : null; };
  if (hex(o.bg)) t.bg = hex(o.bg);
  if (hex(o.ac)) t.ac = hex(o.ac);
  if (o.tx === "" || hex(o.tx)) t.tx = hex(o.tx) || "";
  if (int(o.op, 30, 100) != null) t.op = int(o.op, 30, 100);
  if (int(o.r, 0, 60) != null) t.r = int(o.r, 0, 60);
  for (const k of ["s", "p", "n", "d", "sc"]) if (int(o[k], 0, 1) != null) t[k] = int(o[k], 0, 1);
  return t;
}
/** Код темы «ZV1;bg=…;ac=…» в одном порядке полей для iPhone и Android. */
const themeString = t => `ZV1;bg=${t.bg};ac=${t.ac};op=${t.op};r=${t.r};s=${t.s};p=${t.p};n=${t.n};d=${t.d};sc=${t.sc}` + (t.tx ? `;tx=${t.tx}` : "");

let toastT;
function toast(t) {
  let e = $("zvToast");
  if (!e) { e = h("div", {class: "zv-toast", id: "zvToast", role: "status", "aria-live": "polite"}); document.body.append(e); }
  e.textContent = t; e.classList.add("on");
  clearTimeout(toastT); toastT = setTimeout(() => e.classList.remove("on"), 2400);
}
// Копирование. Где нет Clipboard API (файл из Telegram, старый Android), выделяем текст по старинке.
async function copyText(text, msg) {
  let ok = false;
  try { await navigator.clipboard.writeText(text); ok = true; } catch (e) {
    const t = h("textarea", {readonly: true, style: "position:fixed;top:0;left:0;opacity:0;font-size:16px"});
    t.value = text; document.body.append(t); t.focus(); t.select(); t.setSelectionRange(0, text.length);
    try { ok = document.execCommand("copy"); } catch (x) {}
    t.remove();
  }
  toast(ok ? (msg || "Скопировано") : "Не получилось скопировать. Выдели текст и скопируй вручную");
  return ok;
}

/* ---------- слова и время ---------- */
/** 1 урок, 3 урока, 7 уроков. */
function word(n, one, few, many) { const a = n % 10, b = n % 100; return n + " " + (a === 1 && b !== 11 ? one : a >= 2 && a <= 4 && (b < 12 || b > 14) ? few : many); }
const plural = n => word(n, "урок", "урока", "уроков");
const MONTHS = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"];
const DAYN = ["Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"];
const DAYS = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
const WHEN = ["в понедельник", "во вторник", "в среду", "в четверг", "в пятницу", "в субботу", "в воскресенье"];
const dow = d => (d.getDay() + 6) % 7; // 0 = понедельник
const two = n => String(n).padStart(2, "0");
const iso = d => d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate());
const dm = d => d.getDate() + " " + MONTHS[d.getMonth()];
const toMin = t => { const m = /^\s*(\d{1,2})[:.](\d{2})\s*$/.exec(t || ""); if (!m) return -1; const H = +m[1], M = +m[2]; return H < 24 && M < 60 ? H * 60 + M : -1; };
const hm = m => Math.floor(m / 60) + ":" + two(m % 60);
/** Время в мс: полночь даты плюс min минут. */
function at(date, min) { const d = new Date(date); d.setHours(0, 0, 0, 0); return d.getTime() + min * 60000; }
/* «Звонку» столько дней: первая версия вышла 1 ноября 2026 (первый коммит) */
const BORN = new Date(2026, 10, 1);
const projectAge = now => Math.max(1, Math.round((new Date(now.getFullYear(), now.getMonth(), now.getDate()) - BORN) / 864e5));
/** Тексты счётчика для «Ещё» и сайта о проекте: {n, days: «дней», born: «1 ноября 2026»}. */
function projectAgeText(now) {
  const n = projectAge(now);
  return {n, days: word(n, "день", "дня", "дней").slice(String(n).length + 1), born: dm(BORN) + " " + BORN.getFullYear()};
}
const clean = s => String(s == null ? "" : s).replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 40);
const esc = t => String(t).replace(/[&<>"']/g, ch => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"})[ch]);

/* ---------- тема: как в системе, светлая или тёмная ---------- */
const SCHEMES = ["auto", "light", "dark"];
function scheme() { const s = store.get("scheme", "auto"); return SCHEMES.includes(s) ? s : "auto"; }
function isDark() {
  const s = scheme();
  return s === "dark" || (s === "auto" && !!window.matchMedia && matchMedia("(prefers-color-scheme: dark)").matches);
}
function applyScheme(s) {
  if (s) store.set("scheme", s);
  s = scheme();
  const root = document.documentElement;
  if (s === "auto") delete root.dataset.theme; else root.dataset.theme = s;
  // цвет полосы браузера и статус-бара iPhone под тему
  $$('meta[name="theme-color"]').forEach(m => {
    if (s === "auto") m.setAttribute("content", m.dataset.c);
    else m.setAttribute("content", s === "dark" ? "#0B0B0C" : "#F4F4F1");
  });
  document.dispatchEvent(new CustomEvent("zv:scheme"));
}
/* цвет акцента: им отмечено то, что идёт сейчас. Первый в списке по умолчанию */
const ACCENTS = /*@ACCENTS@*/null || [{id: "red", name: "Звонок", light: "#C8320F", dark: "#FF5A36"}];
function accent() { const a = store.get("accent", ACCENTS[0].id); return ACCENTS.some(x => x.id === a) ? a : ACCENTS[0].id; }
function applyAccent(a) {
  if (a) store.set("accent", a);
  a = accent();
  if (a === ACCENTS[0].id) delete document.documentElement.dataset.accent; else document.documentElement.dataset.accent = a;
  document.dispatchEvent(new CustomEvent("zv:scheme"));
}
if (window.matchMedia) {
  const mq = matchMedia("(prefers-color-scheme: dark)");
  const on = () => { if (scheme() === "auto") document.dispatchEvent(new CustomEvent("zv:scheme")); };
  if (mq.addEventListener) mq.addEventListener("change", on); else if (mq.addListener) mq.addListener(on);
}

/* ---------- платформа ---------- */
const UA = navigator.userAgent || "";
const IOS = /iPhone|iPad|iPod/.test(UA) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
const ANDROID = /Android/i.test(UA);
const STANDALONE = (!!window.matchMedia && matchMedia("(display-mode: standalone)").matches) || navigator.standalone === true;
if (STANDALONE) document.documentElement.classList.add("standalone");
if (IOS) document.documentElement.classList.add("ios");
const ONLINE_SITE = /^https?:$/.test(location.protocol);

/** Service worker: сайт работает без интернета и обновляется сам, данные в браузере не трогает. */
function registerSW() {
  if (!ONLINE_SITE || !("serviceWorker" in navigator)) return;
  addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => {}));
}
