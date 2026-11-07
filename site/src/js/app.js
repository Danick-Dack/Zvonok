/* Звонок · главная, общая часть: выбранный класс, вкладки и адреса, шапка, лента дня и плашки кабинетов, свежие замены.
   Сами вкладки в now.js, week.js, rooms.js и more.js, запуск в start.js (сборка склеивает их в этом порядке). */

const SITE = "@SITE_URL@", APK = "@APK_URL@";
const QRS = /*@QRS@*/null; // QR-коды: "" это сайт, остальное ссылки на классы

/* ---------- выбранный класс или своё расписание ---------- */
let cls = store.get("class", null);
let pick = !DATA.classes[cls]; // новичок: сначала выбирает класс
if (pick) cls = CLASSES[0];
let custom = store.get("custom", null);
let useCustom = !!store.get("useCustom", false) && !!custom;
if (useCustom) pick = false;
let WEEK = [];
const src = {at: d => useCustom ? WEEK[dow(d)] : dayLessons(cls, d), real: true};
function rebuild() { WEEK = useCustom ? customWeek(custom) : classWeek(cls); src.real = !useCustom; }
const title = () => useCustom ? "своё" : cls;

/* ссылка на класс: …/Zvonok/#10a (принимаем и #10а). Так устроены QR-коды на плакатах */
const CYR = {a: "А", b: "Б", v: "В", g: "Г", "а": "А", "б": "Б", "в": "В", "г": "Г"};
function hashText() { try { return decodeURIComponent(location.hash.slice(1)).trim(); } catch (e) { return ""; } }
function classFromHash() {
  const m = /^(7|8|9|10|11)([abvgабвг])$/i.exec(hashText());
  const c = m ? m[1] + CYR[m[2].toLowerCase()] : null;
  return c && DATA.classes[c] ? c : null;
}
/** Открыть класс по ссылке. Свой выбор не перезаписываем, только если класса ещё нет. */
function openLinkClass(c) {
  cls = c; pick = false; useCustom = false;
  if (!DATA.classes[store.get("class", null)]) store.set("class", c);
  rebuild();
}
function setClass(c) {
  cls = c; pick = false; useCustom = false;
  store.set("class", c); store.set("useCustom", false);
  rebuild(); refresh(); homeIcon();
}
function setCustom(on) {
  custom = store.get("custom", null);
  useCustom = on && !!custom; store.set("useCustom", useCustom);
  if (useCustom) pick = false;
  rebuild(); refresh();
}

/* ---------- вкладки ---------- */
const VIEWS = ["now", "week", "rooms", "more", "olymp"];
// старые якоря и разделы внутри «Ещё»
const SECTIONS = {schedule: "week", top: "now", install: "more", calendar: "more", make: "more", share: "more", faq: "more", about: "more", safe: "more", olympiads: "olymp"};
let view = "now";
const dirty = {now: true, week: true, rooms: true, more: true, olymp: true};
const RENDER = {now: () => renderNow(new Date()), week: () => renderWeek(), rooms: () => renderRooms(), more: () => renderMore(), olymp: () => renderOlymp()};
function show(v) {
  view = v;
  VIEWS.forEach(x => { $("v-" + x).hidden = x !== v; });
  $$("a[data-tab]").forEach(a => a.dataset.tab === v ? a.setAttribute("aria-current", "page") : a.removeAttribute("aria-current"));
  if (dirty[v]) { dirty[v] = false; RENDER[v](); }
  if (v === "now") tick(); // на «Сейчас» таймер идёт по секундам сразу, а не со следующего планового тика
}
function route(first) {
  const c = classFromHash();
  if (c) {
    openLinkClass(c);
    try { history.replaceState(null, "", location.pathname + location.search); } catch (e) {}
    if (!first) refresh();
  }
  const raw = c ? "" : hashText(), v = VIEWS.includes(raw) ? raw : SECTIONS[raw] || "now";
  show(v);
  const target = SECTIONS[raw] === "more" && $(raw);
  if (target) { if (target.tagName === "DETAILS") target.open = true; requestAnimationFrame(() => target.scrollIntoView({block: "start"})); }
  else if (!first) window.scrollTo(0, 0);
}
addEventListener("hashchange", () => route(false));
/** Что-то поменялось (класс, замены, день): перерисовать видимое, остальное при открытии. */
function refresh() {
  VIEWS.forEach(v => { dirty[v] = true; });
  key = ""; renderHeader(); tick();
  if (dirty[view]) { dirty[view] = false; RENDER[view](); }
}

/* ---------- шапка: класс и тема ---------- */
function renderHeader() {
  const b = $("clsBtn");
  b.replaceChildren(pick ? "Класс" : useCustom ? "Своё" : cls, icon("down"));
  b.classList.toggle("empty", pick);
  b.setAttribute("aria-label", pick ? "Выбрать класс" : (useCustom ? "Своё расписание" : "Класс " + cls) + ". Сменить");
}
// значки темы для сборки: icon("auto") icon("sun") icon("moon")
const SCHEME_UI = {auto: ["auto", "как в системе"], light: ["sun", "светлая"], dark: ["moon", "тёмная"]};
function renderScheme() {
  const s = scheme(), b = $("schemeBtn");
  b.replaceChildren(icon(SCHEME_UI[s][0]));
  b.setAttribute("aria-label", "Тема: " + SCHEME_UI[s][1] + ". Сменить");
  b.title = "Тема: " + SCHEME_UI[s][1];
}
$("schemeBtn").onclick = () => { const s = SCHEMES[(SCHEMES.indexOf(scheme()) + 1) % SCHEMES.length]; applyScheme(s); renderScheme(); toast("Тема: " + SCHEME_UI[s][1]); };

/* выбор класса: сетка по параллелям; в шапке открывается листом */
function renderGrades(box, after) {
  box.textContent = "";
  const grades = {};
  CLASSES.forEach(c => (grades[parseInt(c)] = grades[parseInt(c)] || []).push(c));
  Object.keys(grades).sort((a, b) => a - b).forEach(g => box.append(h("div", {class: "grade", role: "group", "aria-label": g + "-е классы"},
    h("b", {text: g, "aria-hidden": "true"}),
    grades[g].map(c => h("button", {type: "button", "aria-pressed": String(!pick && !useCustom && c === cls), text: c,
      onclick: () => { setClass(c); if (after) after(); }})))));
  box.append(h("div", {class: "grade own"}, h("b", {"aria-hidden": "true"}, icon("edit", "sm")), store.get("custom", null)
    ? h("button", {type: "button", "aria-pressed": String(useCustom), text: "Своё расписание", onclick: () => { setCustom(true); if (after) after(); }})
    : h("a", {class: "own-link", href: "make.html"}, "Собрать своё расписание", h("small", {text: "для другой школы, колледжа или кружков"}))));
}
const sheet = $("clsSheet");
function openSheet() {
  renderGrades($("sheetGrades"), closeSheet);
  if (typeof sheet.showModal === "function") sheet.showModal(); else sheet.setAttribute("open", "");
  const on = sheet.querySelector('[aria-pressed="true"]'); if (on) on.focus();
}
function closeSheet() { if (typeof sheet.close === "function") sheet.close(); else sheet.removeAttribute("open"); sheet.style.transform = ""; }
$("clsBtn").onclick = openSheet;
sheet.addEventListener("click", e => { if (e.target === sheet || e.target.closest("[data-close]")) closeSheet(); });
// лист можно смахнуть вниз, как на iPhone
(() => {
  let y0 = null, dy = 0;
  sheet.addEventListener("touchstart", e => { y0 = sheet.scrollTop > 0 ? null : e.touches[0].clientY; dy = 0; }, {passive: true});
  sheet.addEventListener("touchmove", e => {
    if (y0 == null) return;
    dy = Math.max(0, e.touches[0].clientY - y0);
    sheet.style.transform = dy ? "translateY(" + dy + "px)" : "";
  }, {passive: true});
  sheet.addEventListener("touchend", () => { if (y0 != null && dy > 90) closeSheet(); else sheet.style.transform = ""; y0 = null; });
})();

/* ---------- кабинет: 48/49 это две группы ---------- */
function roomChip(r, now, lg) {
  const e = h("span", {class: "room" + (now ? " now" : "") + (lg ? " lg" : "") + (/^\d/.test(r) ? "" : " gym"), title: /^\d/.test(r) ? "Кабинет " + r : r});
  r.split("/").forEach((p, i) => { if (i) e.append(h("i", {text: "/"})); e.append(p || "–"); });
  return e;
}
const mark = l => l && l.changed ? "замена" : l && l.short ? "сокращённый" : "";

/* ---------- лента дня ---------- */
/** Уроки дня с переменами. live = это сегодня: отмечаем прошедшие, текущий и следующий. */
function timeline(list, live, s, empty) {
  const ol = h("ol", {class: "tl"});
  if (!list.length) { ol.append(h("li", {class: "empty", text: empty || "Уроков нет. Можно выдохнуть"})); return ol; }
  const now = new Date(), nowMin = now.getHours() * 60 + now.getMinutes();
  list.forEach((l, i) => {
    if (i) {
      const len = l.start - list[i - 1].end, on = live && s.kind === "BREAK" && s.next && s.next.num === l.num;
      if (len > 0) ol.append(h("li", {class: "gap" + (on ? " now" : "")}, h("span"), h("span", {class: "rail"}),
        h("span", {class: "m", text: (on ? "Сейчас " : "") + (len > 25 ? "окно " : "перемена ") + len + " мин"}), h("span")));
    }
    const cur = live && s.kind === "LESSON" && s.cur.num === l.num;
    const nx = live && (s.kind === "BREAK" || s.kind === "BEFORE") && s.next && s.next.num === l.num;
    const past = live && !cur && l.end <= nowMin, m = mark(l);
    ol.append(h("li", {class: "les hue-" + hueOf(l.subject) + (cur ? " cur" : nx ? " next-l" : past ? " past" : ""), "aria-current": cur ? "time" : null},
      h("span", {class: "t"}, h("b", {text: hm(l.start)}), h("small", {text: hm(l.end)})),
      h("span", {class: "rail"}, h("i", {class: "dot"})),
      h("span", {class: "m"}, SHORTS[l.subject] ? h("b", null, h("span", {class: "full", text: l.subject}), h("span", {class: "short", text: SHORTS[l.subject], title: l.subject})) : h("b", {text: l.subject}),
        cur || nx || m ? h("small", null, cur ? "Идёт, урок " + l.num : nx ? "Следующий, урок " + l.num : "Урок " + l.num,
          m ? [" · ", h("span", {class: "chg", text: m})] : null) : null),
      l.room ? roomChip(l.room, cur) : h("span")));
  });
  return ol;
}
/** Пометка дня: каникулы, отмены, заметка из замен. */
function dayNote(date) {
  if (useCustom) return null;
  const off = dayOff(date);
  if (off) return {kind: "ok", text: off[2] + ". Уроков нет, ниже обычное расписание этого дня."};
  const ch = chDay(date);
  if (!ch) return null;
  const gone = cancelled(cls, date), list = dayLessons(cls, date);
  const text = [clean(ch.note) || (list.some(l => l.changed || l.short) || gone.length ? "В этот день расписание изменено" : ""),
    gone.length ? (gone.length > 1 ? "Отменены уроки " : "Отменён урок ") + gone.join(", ") : ""].filter(Boolean).join(". ");
  return text ? {kind: gone.length ? "bad" : "warn", text} : null;
}
function noteEl(n, id) { return n ? h("p", {class: "day-note " + n.kind, id: id || null, text: n.text}) : null; }
// «8 уроков · 8:00–15:25»: число не отрывается от слова, а время переносится только целиком (не «8:00–» и «15:25»)
const dayRange = list => list.length ? plural(list.length).replace(" ", " ") + " · " + hm(list[0].start) + "⁠–⁠" + hm(list[list.length - 1].end) : "уроков нет";
/* короткие названия там, где мало места (неделя на компьютере, как в виджетах) */
const SHORTS = {"Казахский яз. и лит.": "Казахский", "Программирование": "Программир.", "Русская литература": "Литература",
  "История Казахстана": "История РК", "Всемирная история": "Всем. история", "Английский язык": "Английский"};
/** Лента дня: цветной отрезок на каждый урок, длина по времени, промежутки это перемены. */
function strip(list, live, s) {
  const el = h("div", {class: "strip", "aria-hidden": "true"});
  const now = new Date(), nowMin = now.getHours() * 60 + now.getMinutes();
  list.forEach((l, i) => {
    if (i && l.start > list[i - 1].end) el.append(h("i", {style: "flex-grow:" + (l.start - list[i - 1].end)}));
    const cur = live && s && s.kind === "LESSON" && s.cur && s.cur.num === l.num, past = live && !cur && l.end <= nowMin;
    el.append(h("span", {class: "hue-" + hueOf(l.subject) + (cur ? " cur" : past ? " past" : ""), style: "flex-grow:" + (l.end - l.start), title: l.subject + ", " + hm(l.start) + "–" + hm(l.end)}));
  });
  return el;
}

/* ---------- свежие замены с сайта (в файле уже есть копия на момент сборки) ---------- */
let chAt = 0, chVer = 0;
function loadChanges() {
  if (!ONLINE_SITE || Date.now() - chAt < 600000) return;
  chAt = Date.now();
  fetch("changes.json", {cache: "no-cache"}).then(r => r.ok ? r.json() : null).then(j => {
    // сравниваем уже очищенное: лишнее поле в файле не должно вызывать перерисовку каждые 10 минут
    const next = j && typeof j.days === "object" ? cleanChanges(j) : null;
    if (!next || JSON.stringify(next) === JSON.stringify(CHANGES)) return;
    CHANGES = next; chVer++; refresh(); // только поля, которые правила умеют читать
  }).catch(() => {});
}
// своё расписание меняют в конструкторе в соседней вкладке
addEventListener("storage", e => {
  if (!e.key || !/^zv\.(class|custom|useCustom|scheme|accent)$/.test(e.key)) return;
  if (e.key === "zv.scheme") { applyScheme(); renderScheme(); return; }
  if (e.key === "zv.accent") { applyAccent(); return; }
  cls = DATA.classes[store.get("class", null)] ? store.get("class", null) : cls;
  custom = store.get("custom", null); useCustom = !!store.get("useCustom", false) && !!custom;
  if (useCustom || DATA.classes[store.get("class", null)]) pick = false;
  rebuild(); refresh();
});
