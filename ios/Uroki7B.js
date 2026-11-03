// Звонок 7Б: виджеты для iPhone (приложение Scriptable).
// Экран блокировки: прямоугольный, круглый, строка над часами. Рабочий стол и StandBy: маленький, средний, большой.
// Если запустить скрипт в Scriptable, откроется экран как в приложении: таймер, кольцо и весь день.
//
// Заряд почти не тратится: таймер на виджете тикает силами iOS, а сам скрипт iOS запускает изредка.
// В интернет скрипт ходит только за файлом замен со своего сайта (не чаще раза в 3 часа), без сети работает как обычно.

// ---------- данные (сайт подставляет сюда выбранный класс) ----------
const CLASS = "7Б";
const THEME = ""; // код темы с сайта, например "ZV1;bg=0B1220;ac=60A5FA". Параметр виджета важнее
const BELLS = [[480,525],[530,575],[585,630],[650,695],[715,760],[770,815],[825,870],[880,925],[935,985]];
const WEEK = {"1":[["Классный час","40"],["Казахский яз. и лит.","42/21"],["История Казахстана","46"],["Алгебра","24/35"],["Математика","24/35"],["Физкультура","спортзал"],["Физкультура","спортзал"],["Практ. физика","48/73"]],"2":[["Геометрия","24/35"],["Английский язык","44/47"],["Физика","48/49"],["Физика","48/49"],["Программирование","56/57"],["Биология","25"],["Худ. труд","11/1"]],"3":[["Алгебра","24/35"],["Алгебра","24/35"],["Английский язык","45/47"],["География","54"],["Физкультура","спортзал"],["Русский язык","46"]],"4":[["Казахский яз. и лит.","42/21"],["Алгебра","24/35"],["Русский язык","46"],["Русская литература","46"],["Физика","48/49"],["Физика","48/49"],["Всемирная история","54"]],"5":[["Геометрия","24/35"],["Геометрия","24/35"],["Казахский яз. и лит.","42/21"],["Английский язык","25/47"],["Химия","40"],["Информатика","56/57"],["Русская литература","46"]]};
const HOLIDAYS = [["2026-12-16","2026-12-16","День Независимости"],["2026-12-28","2027-01-10","Зимние каникулы"],["2027-03-08","2027-03-08","Международный женский день"],["2027-03-22","2027-03-28","Весенние каникулы"],["2027-05-01","2027-05-01","Праздник единства народа Казахстана"],["2027-05-03","2027-05-03","Выходной за 1 мая"],["2027-05-07","2027-05-07","День защитника Отечества"],["2027-05-09","2027-05-10","День Победы"],["2027-05-26","2027-08-31","Летние каникулы"]];
const TERM_END = "2026-12-27";
const SUBJECTS = {"alg":"Алгебра","geom":"Геометрия","mat":"Математика","fiz":"Физика","prf":"Практ. физика","kaz":"Казахский яз. и лит.","rus":"Русский язык","rlit":"Русская литература","eng":"Английский язык","istk":"История Казахстана","vist":"Всемирная история","geo":"География","bio":"Биология","him":"Химия","inf":"Информатика","prog":"Программирование","fk":"Физкультура","hud":"Худ. труд","kl":"Классный час","prav":"Основы права","nvp":"НВП","avto":"Автодело"};
const SITE = "https://danick-dack.github.io/Zvonok/";
// ---------- конец данных ----------

// ---------- расписание (та же логика, что на сайте и в Android) ----------

const DAYN = ["Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"];
const WHEN = ["в воскресенье", "в понедельник", "во вторник", "в среду", "в четверг", "в пятницу", "в субботу"];
const DSHORT = ["вс", "пн", "вт", "ср", "чт", "пт", "сб"];
const MONTHS = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"];
const SHORT = {"Казахский яз. и лит.": "Казахский", "Английский язык": "Английский", "Русский язык": "Русский", "Русская литература": "Литература",
  "История Казахстана": "Ист. Казахстана", "Всемирная история": "Всем. история", "Программирование": "Программир.", "Практ. физика": "Пр. физика"};
let CH = {days: {}}; // замены с сайта

function hm(min) { const m = min % 60; return Math.floor(min / 60) + ":" + (m < 10 ? "0" : "") + m; }
function two(n) { return (n < 10 ? "0" : "") + n; }
function iso(d) { return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate()); }
function dm(d) { return d.getDate() + " " + MONTHS[d.getMonth()]; }
function at(day, min) { return new Date(day.getFullYear(), day.getMonth(), day.getDate(), 0, min, 0, 0); }
function short(s) { return SHORT[s] || s; }
function word(n, one, few, many) { const a = n % 10, b = n % 100; return n + " " + (a === 1 && b !== 11 ? one : a >= 2 && a <= 4 && (b < 12 || b > 14) ? few : many); }
function plural(n) { return word(n, "урок", "урока", "уроков"); }
function clean(s) { return String(s == null ? "" : s).replace(/[\u0000-\u001f]/g, " ").trim().slice(0, 40); }
function roomName(r) { return r === "с/з" ? "спортзал" : r || ""; }
function holiday(d) { const t = iso(d); return HOLIDAYS.find(h => t >= h[0] && t <= h[1]) || null; }
function chDay(d) { return (CH.days || {})[iso(d)] || null; }
function noLesson(v) { return v === null || (typeof v === "string" && /^нет/i.test(v.trim())); }
// день без уроков у всех: каникулы, праздник или «off» в заменах
function dayOff(d) { const h = holiday(d), c = chDay(d); return h || (c && c.off ? [iso(d), iso(d), clean(c.off)] : null); }
function dayBells(d) {
  const c = chDay(d);
  if (!c || !Array.isArray(c.bells)) return BELLS;
  const t = x => { const m = /^(\d{1,2})[:.](\d\d)$/.exec(String(x || "").trim()); return m ? +m[1] * 60 + +m[2] : -1; };
  return c.bells.map((b, i) => { const s = b ? t(b[0]) : -1, e = b ? t(b[1]) : -1; return s >= 0 && e > s ? [s, e] : BELLS[i] || null; });
}
// уроки на дату: с каникулами, заменами и сокращёнными звонками
function lessonsOn(d) {
  if (dayOff(d)) return [];
  const raw = WEEK[d.getDay()] || [], ch = chDay(d), bells = dayBells(d), fix = ch && ch.classes && ch.classes[CLASS] || {};
  const n = Math.max(raw.length, ...Object.keys(fix).map(Number).filter(x => x > 0), 0), out = [];
  for (let i = 1; i <= n; i++) {
    const p = raw[i - 1], f = fix[i];
    let subject = p && p[0] || "", room = p && p[1] || "", changed = false;
    if (f !== undefined && noLesson(f)) continue;
    if (f && typeof f === "object") {
      changed = true;
      if (f.subject) subject = SUBJECTS[f.subject] || clean(f.subject);
      if (f.room != null) room = roomName(clean(f.room));
    }
    if (!subject || !bells[i - 1]) continue;
    out.push({num: i, start: bells[i - 1][0], end: bells[i - 1][1], subject, room, changed, short: bells !== BELLS});
  }
  return out;
}

function compute(now) {
  const s = {}, list = lessonsOn(now), sec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
  if (list.length) {
    const first = list[0], last = list[list.length - 1];
    s.count = list.length; s.end = last.end; s.list = list;
    if (sec < first.start * 60) {
      Object.assign(s, {kind: "BEFORE", next: first, start: at(now, first.start - 60), target: at(now, first.start), soon: sec >= (first.start - 60) * 60});
      return s;
    }
    for (let i = 0; i < list.length; i++) {
      const l = list[i];
      if (sec < l.end * 60) {
        if (sec >= l.start * 60) Object.assign(s, {kind: "LESSON", cur: l, idx: i, next: list[i + 1] || null, start: at(now, l.start), target: at(now, l.end)});
        else Object.assign(s, {kind: "BREAK", cur: list[i - 1], idx: i - 1, next: l, start: at(now, list[i - 1].end), target: at(now, l.start)});
        return s;
      }
    }
    s.kind = "AFTER";
  } else { s.kind = "OFF"; s.holiday = dayOff(now); }
  for (let d = 1; d <= 120; d++) {
    const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() + d), l = lessonsOn(date);
    if (!l.length) continue;
    const far = d > 6, wd = date.getDay();
    Object.assign(s, {next: l[0], nCount: l.length, nEnd: l[l.length - 1].end, nDate: date, nDays: d,
      nLabel: d === 1 ? "Завтра" : far ? dm(date) : DAYN[wd], nWhen: d === 1 ? "завтра" : far ? dm(date) : WHEN[wd],
      nShort: d === 1 ? "завтра" : far ? date.getDate() + "." + two(date.getMonth() + 1) : DSHORT[wd], target: at(date, l[0].start)});
    break;
  }
  return s;
}

// учебные дни до ближайших каникул (сегодня считается, пока уроки не кончились)
function vacation(now) {
  const t = iso(now);
  if (holiday(now) || (TERM_END && t > TERM_END)) return null;
  const v = HOLIDAYS.filter(x => /каникул/i.test(x[2]) && x[0] > t).sort((a, b) => a[0] < b[0] ? -1 : 1)[0];
  if (!v) return null;
  const nowMin = now.getHours() * 60 + now.getMinutes();
  let n = 0, today = false;
  for (let i = 0; i < 400; i++) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + i);
    if (iso(d) >= v[0]) break;
    const l = lessonsOn(d);
    if (!l.length || (i === 0 && nowMin >= l[l.length - 1].end)) continue;
    n++; if (i === 0) today = true;
  }
  return n > 60 ? null : {n, today, name: v[2]};
}
function vacationText(v) {
  if (!v) return "";
  if (!v.n) return "Дальше " + v.name.toLowerCase();
  if (v.n === 1 && v.today) return "Сегодня последний учебный день, потом " + v.name.toLowerCase();
  return "До каникул " + word(v.n, "учебный день", "учебных дня", "учебных дней");
}

// всё, что показывают виджеты и экран, в одном месте
function model(now) {
  const s = compute(now), n = s.next, c = s.cur;
  const m = {s, kind: s.kind, timer: false, target: s.target, progress: -1, big: "", bigSub: "", capW: "", capV: "", label: "", tag: "",
    title: "", room: "", changed: false, nextHead: "", nextSubj: "", nextRoom: "", nextChanged: false, home: false, go: "", hol: false, days: 0};
  const pct = () => Math.max(0, Math.min(1, (now - s.start) / (s.target - s.start)));
  if (s.kind === "LESSON") {
    const last = !n;
    Object.assign(m, {timer: true, progress: pct(), label: last ? "ПОСЛЕДНИЙ УРОК" : "УРОК " + (s.idx + 1) + " ИЗ " + s.count, title: c.subject,
      room: c.room, changed: c.changed, capW: "звонок", capV: hm(c.end), when: "до " + hm(c.end), home: last});
    m.tag = m.label;
    if (n) Object.assign(m, {nextHead: "ДАЛЬШЕ · " + hm(n.start), nextSubj: n.subject, nextRoom: n.room, nextChanged: n.changed, go: n.room});
    else Object.assign(m, {nextHead: "ПОТОМ", nextSubj: "Домой в " + hm(c.end)});
  } else if (s.kind === "BREAK") {
    const len = n.start - c.end, win = len > 25;
    Object.assign(m, {timer: true, progress: pct(), label: (win ? "ОКНО · " : "ПЕРЕМЕНА · ") + len + " МИН", tag: win ? "ОКНО" : "ПЕРЕМЕНА",
      title: win ? "Окно" : "Перемена", room: n.room, changed: n.changed, capW: "урок в", capV: hm(n.start), when: "до " + hm(n.start),
      nextHead: "ИДИ В", nextSubj: n.subject, nextRoom: n.room, nextChanged: n.changed, go: n.room});
  } else if (s.kind === "BEFORE") {
    Object.assign(m, {nextHead: "ПЕРВЫЙ · " + hm(n.start), nextSubj: n.subject, nextRoom: n.room, nextChanged: n.changed, go: n.room, room: n.room});
    if (s.soon) Object.assign(m, {timer: true, progress: pct(), label: "СКОРО УРОКИ", tag: "СКОРО УРОКИ", title: "До уроков", capW: "уроки в", capV: hm(n.start), when: "до " + hm(n.start)});
    else Object.assign(m, {label: "СЕГОДНЯ " + plural(s.count).toUpperCase(), tag: "СЕГОДНЯ", title: "Уроки с " + hm(n.start),
      big: hm(n.start), bigSub: "сегодня", capW: "до", capV: hm(s.end)});
  } else {
    const h = s.holiday, vac = !!h && /каникул/i.test(h[2]);
    m.hol = !!h && (!n || s.nDays > 1); // однодневный праздник выглядит как обычный вечер перед уроками
    m.label = s.kind === "AFTER" ? "УРОКИ ЗАКОНЧИЛИСЬ" : h ? h[2].toUpperCase() : "ВЫХОДНОЙ";
    m.tag = s.kind === "AFTER" ? (n ? s.nLabel.toUpperCase() : "ВСЁ") : vac ? "КАНИКУЛЫ" : "ВЫХОДНОЙ";
    m.title = s.kind === "AFTER" ? "Свободен" : h ? (vac ? "Каникулы" : "Выходной") : "Отдыхай";
    if (n) {
      Object.assign(m, {nextSubj: n.subject, nextRoom: n.room, nextChanged: n.changed, go: n.room});
      if (m.hol) {
        m.days = s.nDays;
        Object.assign(m, {big: String(s.nDays), bigSub: word(s.nDays, "день", "дня", "дней").replace(/^\d+ /, ""),
          capW: word(s.nDays, "день", "дня", "дней").replace(/^\d+ /, "") + " до уроков", capV: dm(s.nDate), nextHead: dm(s.nDate).toUpperCase() + " · " + hm(n.start)});
      } else Object.assign(m, {big: hm(n.start), bigSub: s.nShort, capW: "до", capV: hm(s.nEnd), nextHead: s.nLabel.toUpperCase() + " · " + plural(s.nCount).toUpperCase()});
    } else m.big = "нет";
  }
  // подпись под таймером на экране в приложении
  m.ringSub = m.kind === "LESSON" ? "звонок в " + m.capV : m.timer ? m.capW + " " + m.capV : m.hol ? m.capW : m.kind === "BEFORE" ? "первый звонок" : s.next ? s.nWhen : "";
  return m;
}

// Когда iOS стоит перерисовать виджет (это только подсказка, iOS может опоздать)
function nextRefresh(m, now) {
  const s = m.s, soon = new Date(now.getTime() + 10 * 60000);
  if (s.kind === "LESSON" || s.kind === "BREAK") return new Date(Math.min(s.target.getTime() + 1000, soon.getTime()));
  if (s.kind === "BEFORE") return s.soon ? new Date(s.target.getTime() + 1000) : at(now, s.next.start - 60);
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 5);
}

// замены: файл с сайта, не чаще раза в 3 часа; без сети берём сохранённый
async function loadChanges() {
  let fm, path, data = null;
  try {
    fm = FileManager.local(); path = fm.joinPath(fm.documentsDirectory(), "zvonok-changes.json");
    if (fm.fileExists(path)) data = JSON.parse(fm.readString(path));
    const age = fm.fileExists(path) ? Date.now() - fm.modificationDate(path).getTime() : Infinity;
    if (age > 3 * 3600000) {
      const r = new Request(SITE + "changes.json");
      r.timeoutInterval = 4;
      const j = await r.loadJSON();
      if (j && typeof j.days === "object") { data = j; fm.writeString(path, JSON.stringify(j)); }
    }
  } catch (e) { /* нет сети: работаем с тем, что есть */ }
  return data && typeof data.days === "object" ? data : {days: {}};
}

// ---------- оформление ----------

function parseTheme(code) {
  const t = {bg: "000000", ac: "FFFFFF", p: 1, n: 1, d: 1};
  code = String(code || "").trim();
  if (!code.startsWith("ZV1") || code.length > 200) return t;
  for (const part of code.split(";")) {
    const k = part.split("=")[0].trim(), v = (part.split("=")[1] || "").trim();
    if ((k === "bg" || k === "ac") && /^[0-9a-fA-F]{6}$/.test(v)) t[k] = v.toUpperCase();
    else if (k === "p" || k === "n" || k === "d") t[k] = v === "0" ? 0 : 1;
  }
  return t;
}
function lum(hex) { const n = parseInt(hex, 16); return (0.2126 * (n >> 16 & 255) + 0.7152 * (n >> 8 & 255) + 0.0722 * (n & 255)) / 255; }
function mix(a, b, t) {
  const x = parseInt(a, 16), y = parseInt(b, 16), ch = s => Math.round((x >> s & 255) * (1 - t) + (y >> s & 255) * t);
  return [16, 8, 0].map(s => ch(s).toString(16).padStart(2, "0")).join("").toUpperCase();
}
const param = typeof args !== "undefined" && args.widgetParameter ? String(args.widgetParameter) : "";
const TH = parseTheme(param.trim().startsWith("ZV1") ? param : THEME);
const LIGHT = lum(TH.bg) > 0.5;
const FG = LIGHT ? "111111" : "FFFFFF";
const ACC = Math.abs(lum(TH.ac) - lum(TH.bg)) < 0.25 ? FG : TH.ac;
const c = a => new Color("#" + FG, a);
const acc = a => new Color("#" + ACC, a);
const WHITE = a => new Color("#FFFFFF", a);

function background(w) {
  const g = new LinearGradient();
  const bg = TH.bg;
  if (bg === "000000") { g.colors = ["1C1C21", "0A0A0C", "000000"].map(x => new Color("#" + x)); g.locations = [0, 0.55, 1]; }
  else if (LIGHT) { g.colors = [mix(bg, "FFFFFF", 0.55), bg].map(x => new Color("#" + x)); g.locations = [0, 1]; }
  else { g.colors = [mix(bg, "FFFFFF", 0.10), bg, mix(bg, "000000", 0.35)].map(x => new Color("#" + x)); g.locations = [0, 0.6, 1]; }
  g.startPoint = new Point(0, 0); g.endPoint = new Point(0, 1);
  w.backgroundGradient = g;
}

// текст: на экране блокировки без цвета (iOS красит сам), иерархия только прозрачностью
function T(p, s, font, a, color, scale) {
  const t = p.addText(String(s));
  t.font = font;
  if (color) t.textColor = color;
  if (a != null && a < 1) t.textOpacity = a;
  t.lineLimit = 1;
  t.minimumScaleFactor = scale || 0.75;
  return t;
}
function timer(p, date, font, a, color, align) {
  const t = p.addDate(date);
  t.applyTimerStyle();
  t.font = font;
  if (color) t.textColor = color;
  if (a != null && a < 1) t.textOpacity = a;
  t.lineLimit = 1;
  t.minimumScaleFactor = 0.6;
  if (align === "c") t.centerAlignText(); else if (align === "r") t.rightAlignText();
  return t;
}
function row(p) { const r = p.addStack(); r.layoutHorizontally(); r.centerAlignContent(); return r; }
function fixed(p, w, h) { const s = p.addStack(); s.size = new Size(w, h || 0); return s; }
// строка по центру (внутри кольца)
function centered(p, fn) { const r = row(p); r.addSpacer(); fn(r); r.addSpacer(); return r; }
function rightCol(p, fn) { const r = row(p); r.addSpacer(); fn(r); return r; }

// кольцо прогресса: дорожка и дуга с круглыми концами
function ring(size, line, value, track, arc) {
  const ctx = new DrawContext();
  ctx.size = new Size(size, size); ctx.opaque = false; ctx.respectScreenScale = true;
  const cc = size / 2, r = cc - line / 2 - 0.5;
  ctx.setLineWidth(line);
  ctx.setStrokeColor(track);
  ctx.strokeEllipse(new Rect(cc - r, cc - r, 2 * r, 2 * r));
  if (value >= 0) {
    const v = Math.max(0.03, Math.min(1, value)), n = Math.max(2, Math.ceil(v * 120)), p = new Path();
    for (let i = 0; i <= n; i++) {
      const a = -Math.PI / 2 + 2 * Math.PI * v * i / n, pt = new Point(cc + r * Math.cos(a), cc + r * Math.sin(a));
      if (i) p.addLine(pt); else p.move(pt);
    }
    ctx.addPath(p); ctx.setStrokeColor(arc); ctx.strokePath();
    ctx.setFillColor(arc);
    for (const a of [-Math.PI / 2, -Math.PI / 2 + 2 * Math.PI * v])
      ctx.fillEllipse(new Rect(cc + r * Math.cos(a) - line / 2, cc + r * Math.sin(a) - line / 2, line, line));
  }
  return ctx.getImage();
}
function ringBox(p, D, L, v, track, arc) {
  const b = p.addStack();
  b.size = new Size(D, D); b.layoutVertically(); b.centerAlignContent();
  b.backgroundImage = ring(D, L, v, track, arc);
  return b;
}
function bar(p, W, H, v, track, fill) {
  const ctx = new DrawContext();
  ctx.size = new Size(W, H); ctx.opaque = false; ctx.respectScreenScale = true;
  const path = (w) => { const q = new Path(); q.addRoundedRect(new Rect(0, 0, w, H), H / 2, H / 2); return q; };
  ctx.addPath(path(W)); ctx.setFillColor(track); ctx.fillPath();
  ctx.addPath(path(Math.max(H, W * Math.max(0, Math.min(1, v))))); ctx.setFillColor(fill); ctx.fillPath();
  const img = p.addImage(ctx.getImage());
  img.imageSize = new Size(W, H);
  return img;
}
function line(p, W, color) {
  const ctx = new DrawContext();
  ctx.size = new Size(W, 1); ctx.opaque = false; ctx.respectScreenScale = true;
  ctx.setFillColor(color); ctx.fillRect(new Rect(0, 0, W, 1));
  const img = p.addImage(ctx.getImage());
  img.imageSize = new Size(W, 1);
}
// точки дня: прошедшие, текущий урок шире, будущие
function dots(p, m) {
  const s = m.s;
  if (!TH.d || !s.list || !(s.kind === "LESSON" || s.kind === "BREAK" || s.kind === "BEFORE")) return;
  // при 7 и больше уроках капсулы короче, чтобы влезали рядом с ярлыком
  const many = s.list.length > 6, st = p.addStack(); st.spacing = many ? 2 : 3; st.centerAlignContent();
  s.list.forEach((l, i) => {
    const cur = s.kind === "LESSON" && i === s.idx, done = s.idx != null && (i < s.idx || (s.kind === "BREAK" && i === s.idx));
    const d = st.addStack(); d.size = new Size(cur ? (many ? 10 : 14) : (many ? 6 : 8), 4); d.cornerRadius = 2;
    d.backgroundColor = cur ? acc(1) : done ? c(0.45) : c(0.16);
  });
}
function chip(p, text, size) {
  const ch = p.addStack();
  ch.cornerRadius = 7; ch.setPadding(2, 7, 2, 7); ch.backgroundColor = acc(0.22);
  T(ch, text, Font.boldRoundedSystemFont(size), 1, acc(1), 0.7);
  return ch;
}
function symbol(p, name, size, a, color) {
  const s = SFSymbol.named(name);
  if (!s) return null;
  const img = p.addImage(s.image);
  img.imageSize = new Size(size, size);
  if (color) img.tintColor = color;
  if (a != null && a < 1) img.imageOpacity = a;
  return img;
}
const room0 = r => r === "спортзал" ? "с/з" : r;
function hasLessonToday(m) { return m.kind === "LESSON" || m.kind === "BREAK" || m.kind === "BEFORE"; }

// ---------- экран блокировки ----------

function lockRect(m) {
  const w = new ListWidget();
  w.setPadding(0, 0, 0, 0);
  const s = m.s, n = s.next;
  // ряд 1
  const r1 = row(w);
  if (m.changed && (m.kind === "LESSON" || m.kind === "BREAK")) { symbol(r1, "arrow.triangle.2.circlepath", 11); r1.addSpacer(3); }
  if (m.kind === "LESSON") {
    const right = m.home ? "" : "→ " + m.go;
    T(r1, short(m.title), Font.semiboldSystemFont(13), 1);
    if (m.room && short(m.title).length + m.room.length + (right.length || 2) <= 19) { r1.addSpacer(5); T(r1, m.room, Font.mediumSystemFont(13), 0.6); }
    r1.addSpacer();
    if (m.home) symbol(r1, "house.fill", 12, 0.7); else T(r1, right, Font.semiboldSystemFont(12), 0.7);
  } else if (m.kind === "BREAK" || (m.kind === "BEFORE" && s.soon)) {
    T(r1, "→ " + short(n.subject), Font.semiboldSystemFont(13), 1);
    r1.addSpacer();
    if (n.room) {
      const pl = r1.addStack(); pl.backgroundColor = WHITE(0.22); pl.cornerRadius = 5; pl.setPadding(1, 5, 1, 5);
      T(pl, n.room, Font.boldRoundedSystemFont(12), 1);
    }
  } else if (m.kind === "BEFORE") {
    T(r1, m.title, Font.semiboldSystemFont(13), 1);
  } else {
    T(r1, m.hol ? m.s.holiday[2] : n ? s.nLabel + " · " + plural(s.nCount) : m.title, Font.semiboldSystemFont(13), 1);
  }
  w.addSpacer(1);
  // ряд 2: таймер и время звонка (опоздавшая перерисовка не обманет)
  const r2 = row(w);
  // ширины колонок заданы, иначе таймер iOS растягивается и сжимает подпись справа
  const lw = m.hol ? 52 : 100, left = fixed(r2, lw);
  if (m.timer) timer(left, m.target, Font.boldRoundedSystemFont(32), 1);
  else T(left, m.big, Font.boldRoundedSystemFont(32), 1, null, 0.6);
  r2.addSpacer();
  const col = fixed(r2, 154 - lw); col.layoutVertically();
  rightCol(col, x => T(x, m.capW, Font.mediumSystemFont(11), 0.6));
  rightCol(col, x => T(x, m.capV, Font.boldRoundedSystemFont(m.hol ? 13 : 17), 1));
  w.addSpacer(4);
  // ряд 3
  if (m.kind === "LESSON" || m.kind === "BREAK") {
    if (TH.p) bar(w, 156, 5, m.progress, WHITE(0.28), WHITE(1));
    else if (m.kind === "LESSON" && n) T(w, "Дальше: " + short(n.subject) + (n.room ? " · " + n.room : ""), Font.mediumSystemFont(12), 0.7);
  } else if (m.kind === "BEFORE" && s.soon) {
    T(w, "Сегодня " + plural(s.count) + " · до " + hm(s.end), Font.mediumSystemFont(12), 0.7);
  } else if (n) {
    T(w, "→ " + short(n.subject) + (n.room ? " · " + n.room : ""), Font.mediumSystemFont(12), 0.7);
  }
  return w;
}

function lockCircle(m) {
  const w = new ListWidget();
  w.addAccessoryWidgetBackground = true;
  w.setPadding(0, 0, 0, 0);
  w.backgroundImage = ring(72, 5, m.timer ? m.progress : -1, WHITE(0.28), WHITE(1));
  w.addSpacer();
  if (m.hol && m.days) {
    const mon = new Date().getMonth();
    centered(w, r => symbol(r, mon === 11 || mon <= 1 ? "snowflake" : "sun.max.fill", 12));
    centered(w, r => T(r, m.big, Font.boldRoundedSystemFont(20), 1));
    centered(w, r => T(r, m.bigSub, Font.mediumSystemFont(10), 0.65));
  } else {
    centered(w, r => T(r, m.timer ? m.capV : m.bigSub || "", m.timer ? Font.semiboldRoundedSystemFont(10) : Font.semiboldSystemFont(10), 0.65));
    if (m.timer) { const t = timer(w, m.target, Font.boldRoundedSystemFont(16), 1, null, "c"); t.minimumScaleFactor = 0.55; }
    else centered(w, r => T(r, m.big, Font.boldRoundedSystemFont(17), 1, null, 0.55));
    centered(w, r => {
      if (m.home) symbol(r, "house.fill", 10, 0.85);
      else if (m.go) T(r, (m.kind === "LESSON" ? "→" : "") + room0(m.go), Font.semiboldRoundedSystemFont(11), 0.85, null, 0.6);
    });
  }
  w.addSpacer();
  return w;
}

function inlineText(m) {
  const s = m.s, n = s.next, sub = l => "Замена: " + short(l.subject) + (l.room ? " · " + l.room : "");
  if (m.kind === "LESSON") return m.changed ? sub(s.cur) : m.home ? "До " + m.capV + ", потом домой" : "До " + m.capV + " → " + m.go;
  if (m.kind === "BREAK") return n.changed ? sub(n) : "Урок в " + hm(n.start) + (n.room ? " · " + n.room : "");
  if (m.kind === "BEFORE") return n.changed ? sub(n) : "Уроки в " + hm(n.start) + (n.room ? " · " + n.room : "");
  if (!n) return m.title;
  if (m.hol) { const e = new Date(s.holiday[1] + "T12:00:00"); return (m.title === "Каникулы" ? "Каникулы" : "Выходные") + " до " + e.getDate() + " " + MONTHS[e.getMonth()].slice(0, 3); }
  return (s.nDays === 1 ? "Завтра" : s.nDays > 6 ? dm(s.nDate) : "В " + DSHORT[s.nDate.getDay()]) + " в " + hm(n.start);
}
function lockInline(m) {
  const w = new ListWidget();
  w.addText(inlineText(m));
  return w;
}

// ---------- рабочий стол ----------

// центр кольца: таймер и время звонка, или крупное время / число дней
function ringCenter(b, m, big, small) {
  b.addSpacer();
  if (m.timer) {
    timer(b, m.target, Font.semiboldRoundedSystemFont(big), 1, c(1), "c");
    b.addSpacer(1);
    centered(b, r => T(r, m.when, Font.mediumRoundedSystemFont(small), 0.55, c(1)));
  } else {
    centered(b, r => T(r, m.big, Font.semiboldRoundedSystemFont(m.hol ? big * 1.25 : big + 1), 1, c(1), 0.5));
    if (m.bigSub) centered(b, r => T(r, m.bigSub, Font.mediumRoundedSystemFont(small), 0.55, c(1)));
  }
  b.addSpacer();
}
function headTitle(m) {
  if (m.kind === "BREAK" || (m.kind === "BEFORE" && m.s.soon)) return m.nextSubj;
  return m.title;
}

function small(m) {
  const w = new ListWidget();
  background(w);
  w.setPadding(14, 14, 14, 14);
  // кольцо слева, справа ярлык и кабинет; предмет отдельной строкой на всю ширину (рядом с кольцом ему мало места)
  const r2 = row(w);
  const b = ringBox(r2, 80, 8, m.timer ? m.progress : -1, c(0.16), acc(1));
  ringCenter(b, m, 20, 10);
  r2.addSpacer(8);
  const col = r2.addStack(); col.layoutVertically(); col.size = new Size(0, 80);
  T(col, m.tag.replace(" ИЗ ", "/").replace("ПОСЛЕДНИЙ УРОК", "ПОСЛЕДНИЙ"), Font.semiboldSystemFont(10), 0.42, c(1), 0.6);
  col.addSpacer();
  if (m.room && hasLessonToday(m)) chip(col, m.room, 15);
  if (m.changed && hasLessonToday(m)) { col.addSpacer(3); T(col, "замена", Font.semiboldSystemFont(10), 0.62, c(1)); }
  w.addSpacer(8);
  T(w, short(headTitle(m)), Font.semiboldSystemFont(16), 1, c(1), 0.7);
  w.addSpacer();
  if (TH.n) {
    const r3 = row(w), n = m.s.next, [, subj, rm] = bottomLine(m);
    let text = "", room = "";
    if (m.kind === "LESSON" && m.home) text = "Домой в " + m.capV;
    else if (hasLessonToday(m)) { text = short(subj); room = rm; }
    else if (n) { text = (m.hol ? m.s.nDate.getDate() + " " + MONTHS[m.s.nDate.getMonth()].slice(0, 3) + " · " : hm(n.start) + " ") + short(n.subject); room = m.hol ? "" : n.room; }
    if (text) {
      if (hasLessonToday(m) && !(m.kind === "BEFORE" && m.s.soon)) { T(r3, "→", Font.mediumSystemFont(12), 0.42, c(1)); r3.addSpacer(4); }
      T(r3, text, Font.mediumSystemFont(12), 0.62, c(1), 0.7);
      r3.addSpacer();
      if (room) T(r3, room, Font.semiboldRoundedSystemFont(12), 0.62, c(1));
    }
  }
  return w;
}

// правая часть medium и large: ярлык, заголовок с кабинетом, «дальше»
function sideInfo(col, m, opt) {
  const r1 = row(col);
  T(r1, m.label, Font.semiboldSystemFont(11), 0.42, c(1), 0.6);
  r1.addSpacer(6);
  dots(r1, m);
  col.addSpacer(4);
  T(col, headTitle(m), Font.semiboldSystemFont(opt.title), 1, c(1), 0.7);
  if ((m.room || m.changed) && hasLessonToday(m)) {
    col.addSpacer(4);
    const r2 = row(col);
    if (m.room) chip(r2, m.room, 14);
    if (m.changed) { r2.addSpacer(6); T(r2, "ЗАМЕНА", Font.semiboldSystemFont(10), 0.62, c(1)); }
  }
  col.addSpacer();
  if (!TH.n) return;
  if (opt.rule) { line(col, 190, c(0.10)); col.addSpacer(8); }
  const [head, subj, rm] = bottomLine(m);
  T(col, head, Font.semiboldSystemFont(11), 0.42, c(1), 0.7);
  col.addSpacer(2);
  const r3 = row(col);
  T(r3, subj, Font.mediumSystemFont(15), 0.8, c(1), 0.75);
  r3.addSpacer();
  if (rm) T(r3, rm, Font.semiboldRoundedSystemFont(14), 0.62, c(1));
}
// нижняя строка: на перемене и утром заголовок уже про следующий урок, поэтому тут то, что будет после него
function bottomLine(m) {
  const s = m.s;
  if (m.kind === "BREAK") { const a = s.list[s.list.indexOf(s.next) + 1]; return a ? ["ПОТОМ · " + hm(a.start), a.subject, a.room] : ["ПОТОМ", "Домой в " + hm(s.next.end), ""]; }
  if (m.kind === "BEFORE" && s.soon) return ["СЕГОДНЯ", plural(s.count) + " · до " + hm(s.end), ""];
  return [m.nextHead, m.nextSubj, m.nextRoom];
}

function medium(m) {
  const w = new ListWidget();
  background(w);
  w.setPadding(14, 16, 14, 18);
  const r = row(w);
  const b = ringBox(r, 128, 10, m.timer ? m.progress : -1, c(0.16), acc(1));
  ringCenter(b, m, 30, 13);
  r.addSpacer(16);
  const col = r.addStack(); col.layoutVertically(); col.size = new Size(0, 128);
  sideInfo(col, m, {title: 20, rule: true});
  return w;
}

function large(m, now) {
  const w = new ListWidget();
  background(w);
  w.setPadding(16, 16, 14, 16);
  const top = row(w);
  const b = ringBox(top, 104, 9, m.timer ? m.progress : -1, c(0.16), acc(1));
  ringCenter(b, m, 26, 12);
  top.addSpacer(14);
  const col = top.addStack(); col.layoutVertically(); col.size = new Size(0, 104);
  sideInfo(col, m, {title: 20, rule: false});
  w.addSpacer(12);

  // весь день таймлайном: сегодня, а после уроков и в выходные ближайший учебный день
  const s = m.s, today = hasLessonToday(m), day = today ? now : s.nDate, list = day ? lessonsOn(day) : [];
  const nowMin = now.getHours() * 60 + now.getMinutes(), many = list.length >= 9, fs = many ? 13 : 14;
  for (const l of list) {
    const cur = today && s.kind === "LESSON" && s.cur.num === l.num;
    const next = today && s.kind !== "LESSON" && s.next && s.next.num === l.num;
    const past = today && !cur && l.end <= nowMin && s.kind !== "BEFORE";
    const a = past ? 0.32 : 1;
    const r = row(w);
    r.setPadding(many ? 3 : 4, 8, many ? 3 : 4, 8);
    if (cur) { r.backgroundColor = c(0.10); r.cornerRadius = 10; }
    else if (next) { r.backgroundColor = c(0.05); r.cornerRadius = 10; }
    const t = fixed(r, 38); t.addSpacer(); T(t, hm(l.start), Font.semiboldRoundedSystemFont(12), past ? 0.32 : 0.62, c(1));
    r.addSpacer(8);
    const mark = fixed(r, 3, 16); mark.cornerRadius = 1.5;
    mark.backgroundColor = cur ? acc(1) : next ? c(0.62) : past ? c(0.16) : c(0.30);
    r.addSpacer(8);
    T(r, l.subject, cur ? Font.semiboldSystemFont(fs) : Font.mediumSystemFont(fs), a, c(1), 0.8);
    r.addSpacer();
    if (l.changed) { T(r, "замена", Font.semiboldSystemFont(10), past ? 0.32 : 0.62, c(1)); r.addSpacer(6); }
    if ((cur || (next && s.kind === "BREAK")) && m.timer) { const tb = fixed(r, 44); timer(tb, m.target, Font.mediumRoundedSystemFont(11), 0.62, c(1), "r"); r.addSpacer(6); }
    if (l.room) T(r, l.room, Font.semiboldRoundedSystemFont(12), cur ? 1 : past ? 0.32 : 0.62, cur ? acc(1) : c(1));
    w.addSpacer(2);
  }
  w.addSpacer();
  const d = now, h = s.holiday, v = vacation(now);
  let foot = DSHORT[d.getDay()].toUpperCase() + ", " + dm(d).toUpperCase();
  if (h && /каникул/i.test(h[2])) foot = h[2].toUpperCase() + " · ДО " + dm(new Date(h[1] + "T12:00:00")).toUpperCase();
  else if (v) foot += " · " + vacationText(v).toUpperCase();
  T(w, foot, Font.semiboldSystemFont(10), 0.42, c(1), 0.7);
  return w;
}

// ---------- экран внутри Scriptable ----------

function appHtml(m, now) {
  const core = [hm, two, iso, dm, at, short, word, plural, clean, roomName, holiday, chDay, noLesson, dayOff, dayBells, lessonsOn, compute, vacation, vacationText, model]
    .map(f => f.toString()).join("\n");
  const data = {CLASS, BELLS, WEEK, HOLIDAYS, TERM_END, SUBJECTS, CH, DAYN, WHEN, DSHORT, MONTHS, SHORT, FG, ACC, BG: TH.bg};
  const glow = "#" + mix(TH.bg, "FFFFFF", 0.10), fg = "#" + FG, ac = "#" + ACC, a = (hex, x) => hex + Math.round(x * 255).toString(16).padStart(2, "0");
  return `<!doctype html><html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<style>
:root{color-scheme:${LIGHT ? "light" : "dark"}}
*{box-sizing:border-box;margin:0;padding:0;-webkit-tap-highlight-color:transparent}
body{background:radial-gradient(120% 60% at 50% 0%,${glow},#${TH.bg} 60%) #${TH.bg};color:${fg};font:16px/1.4 -apple-system,system-ui,sans-serif;
  padding:max(18px,env(safe-area-inset-top)) 20px 40px;min-height:100vh}
.caps{font-size:11px;letter-spacing:.14em;text-transform:uppercase;font-weight:600;opacity:.5}
.num{font-family:ui-rounded,-apple-system,system-ui;font-variant-numeric:tabular-nums}
.head{display:flex;align-items:center;justify-content:space-between}.head b{font-size:17px;font-weight:650}
.ring{position:relative;width:248px;height:248px;margin:22px auto 0}
.ring svg{position:absolute;inset:0}
.ring .in{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center}
#tm{font-size:64px;font-weight:300;line-height:1.05;letter-spacing:-.02em}
#tm.d{font-size:76px}
#sub{font-size:15px;opacity:.6;margin-top:2px}
.title{display:flex;align-items:center;justify-content:center;gap:10px;margin-top:18px;flex-wrap:wrap;text-align:center}
#title{font-size:26px;font-weight:600;letter-spacing:-.01em}
.pl{background:${a(ac, .22)};color:${ac};font-weight:700;font-size:15px;border-radius:10px;padding:3px 10px;white-space:nowrap}
.pl.s{background:${a(fg, .10)};color:${fg};font-size:14px}
.sub{font-size:10px;letter-spacing:.1em;text-transform:uppercase;border:1px solid ${a(fg, .3)};border-radius:6px;padding:1px 6px;font-weight:600;opacity:.8}
.dots{display:flex;gap:4px;justify-content:center;margin-top:12px}.dots i{width:14px;height:5px;border-radius:3px;background:${a(fg, .16)}}
.dots i.d{background:${a(fg, .45)}}.dots i.c{background:${ac};width:22px}
.card{background:${a(fg, .06)};border:1px solid ${a(fg, .08)};border-radius:22px;padding:16px;margin-top:22px}
.nx{display:flex;align-items:center;gap:10px;margin-top:6px}.nx span:first-child{flex:1;font-size:18px}
.seg{display:flex;background:${a(fg, .05)};border:1px solid ${a(fg, .08)};border-radius:16px;padding:4px;gap:2px;margin-top:26px}
.seg button{flex:1;border:0;background:none;color:${fg};opacity:.55;font:600 15px -apple-system,system-ui;padding:9px 0;border-radius:12px}
.seg button.t{opacity:1}.seg button.on{background:${fg};color:#${TH.bg};opacity:1}
.list{margin-top:12px}
.les{display:flex;align-items:center;gap:12px;padding:12px;border-radius:16px}
.les.cur{background:${a(fg, .10)}}.les.nx2{background:${a(fg, .05)}}.les.past{opacity:.38}
.les .t{width:44px;font-size:15px;font-weight:600}.les .t small{display:block;font-size:12px;opacity:.55;font-weight:500}
.les .b{width:3px;align-self:stretch;border-radius:2px;background:${a(fg, .3)}}.les.cur .b{background:${ac}}.les.nx2 .b{background:${a(fg, .62)}}
.les .m{flex:1;min-width:0}.les .m small{display:block;font-size:13px;opacity:.55}
.brk{font-size:12px;opacity:.45;padding:2px 0 2px 70px}.brk.on{opacity:.9;font-weight:600}
#note,#vac{font-size:14px;opacity:.6;margin-top:14px;text-align:center}
.how{font-size:14px;line-height:1.5;opacity:.85}.how b{display:block;font-size:15px;margin-bottom:6px}
</style></head><body>
<div class="head"><b>Звонок ${CLASS}</b><span class="caps" id="date"></span></div>
<div class="ring"><svg viewBox="0 0 248 248"><circle cx="124" cy="124" r="110" fill="none" stroke="${a(fg, .10)}" stroke-width="14"/>
<circle id="arc" cx="124" cy="124" r="110" fill="none" stroke="${ac}" stroke-width="14" stroke-linecap="round" transform="rotate(-90 124 124)" stroke-dasharray="0 999"/>
<circle id="knob" r="9" fill="${ac}" style="filter:drop-shadow(0 0 6px ${a(ac, .6)})"/></svg>
<div class="in"><div class="caps" id="label"></div><div class="num" id="tm"></div><div id="sub"></div></div></div>
<div class="title"><span id="title"></span><span id="chg" class="sub">замена</span><span id="room" class="pl"></span></div>
<div class="dots" id="dots"></div>
<div class="card" id="next"><div class="caps" id="nh"></div><div class="nx"><span id="ns"></span><span class="pl s" id="nr"></span></div></div>
<div class="seg" id="days"></div>
<div id="note"></div>
<div class="list" id="list"></div>
<div id="vac"></div>
<div class="card how"><b>Как добавить виджет</b>
Экран блокировки: зажми его, нажми «Настроить», выбери экран блокировки и нажми на поле под часами. Найди Scriptable, добавь прямоугольный или круглый виджет, нажми на него и в поле Script выбери этот скрипт.<br><br>
Рабочий стол: зажми пустое место, нажми «+» или «Изменить», найди Scriptable и выбери размер. Потом нажми на виджет и выбери этот скрипт.</div>
<script>
const D=${JSON.stringify(data).replace(/</g, "\\u003c")};
const {CLASS,BELLS,WEEK,HOLIDAYS,TERM_END,SUBJECTS,CH,DAYN,WHEN,DSHORT,MONTHS,SHORT}=D;
${core}
const $=id=>document.getElementById(id);
let sel=null;
function fmt(ms){const t=Math.max(0,Math.ceil(ms/1000)),h=Math.floor(t/3600),m=Math.floor(t%3600/60),s=t%60;return (h?h+":"+two(m):m)+":"+two(s)}
function dateOf(wd,base){const d=new Date(base.getFullYear(),base.getMonth(),base.getDate());d.setDate(d.getDate()+(wd-d.getDay()+7)%7);return d}
function render(){
 const now=new Date(),m=model(now),s=m.s,n=s.next;
 $("date").textContent=DSHORT[now.getDay()]+", "+dm(now);
 $("label").textContent=m.label;
 const tm=$("tm");tm.className="num"+(m.timer?"":" d");tm.textContent=m.timer?fmt(m.target-now):m.big;
 $("sub").textContent=m.ringSub;
 const v=m.timer?Math.max(0.03,(now-s.start)/(s.target-s.start)):-1,C=2*Math.PI*110,arc=$("arc"),k=$("knob");
 arc.setAttribute("stroke-dasharray",v<0?"0 999":(v*C)+" "+C);k.style.display=v<0?"none":"";
 if(v>=0){const a=-Math.PI/2+2*Math.PI*Math.min(1,v);k.setAttribute("cx",124+110*Math.cos(a));k.setAttribute("cy",124+110*Math.sin(a))}
 $("title").textContent=m.title;$("room").textContent=m.room;$("room").style.display=m.room&&(s.kind==="LESSON"||s.kind==="BREAK"||s.kind==="BEFORE")?"":"none";
 $("chg").style.display=m.changed&&(s.kind==="LESSON"||s.kind==="BREAK"||s.kind==="BEFORE")?"":"none";
 const dots=$("dots");dots.innerHTML="";
 if(s.list&&(s.kind==="LESSON"||s.kind==="BREAK"||s.kind==="BEFORE"))s.list.forEach((l,i)=>{const e=document.createElement("i");e.className=s.kind==="LESSON"&&i===s.idx?"c":s.idx!=null&&(i<s.idx||(s.kind==="BREAK"&&i===s.idx))?"d":"";dots.appendChild(e)});
 $("next").style.display=m.nextSubj?"":"none";$("nh").textContent=m.nextHead+(m.nextChanged?" · ЗАМЕНА":"");$("ns").textContent=m.nextSubj;$("nr").textContent=m.nextRoom;$("nr").style.display=m.nextRoom?"":"none";
 if(sel===null){sel=s.kind==="LESSON"||s.kind==="BREAK"||s.kind==="BEFORE"||!s.nDate?now.getDay():s.nDate.getDay();if(!WEEK[sel])sel=1}
 const days=$("days");days.innerHTML="";
 for(let wd=1;wd<=(WEEK[6]?6:5);wd++){const b=document.createElement("button");b.textContent=["","Пн","Вт","Ср","Чт","Пт","Сб"][wd];b.className=(wd===sel?"on ":"")+(wd===now.getDay()?"t":"");b.onclick=()=>{sel=wd;render()};days.appendChild(b)}
 const date=dateOf(sel,s.nDays>6&&!(s.kind==="LESSON"||s.kind==="BREAK"||s.kind==="BEFORE")?s.nDate:now),off=dayOff(date),isT=sel===now.getDay()&&!off,nowMin=now.getHours()*60+now.getMinutes();
 const ls=off?(WEEK[sel]||[]).map((p,i)=>p&&p[0]&&BELLS[i]?{num:i+1,start:BELLS[i][0],end:BELLS[i][1],subject:p[0],room:p[1]}:null).filter(Boolean):lessonsOn(date);
 const ch=chDay(date);
 $("note").textContent=off?dm(date)+": "+off[2]+". Ниже обычное расписание этого дня.":ch&&ch.note?clean(ch.note):"";
 const list=$("list");list.innerHTML="";
 ls.forEach((l,i)=>{
  if(i){const len=l.start-ls[i-1].end,on=isT&&s.kind==="BREAK"&&n&&n.num===l.num,b=document.createElement("div");b.className="brk"+(on?" on":"");b.textContent=on?"● перемена сейчас · до "+hm(l.start):(len>25?"окно ":"перемена ")+len+" мин";list.appendChild(b)}
  const cur=isT&&s.kind==="LESSON"&&s.cur.num===l.num,nx=isT&&(s.kind==="BREAK"||s.kind==="BEFORE")&&n&&n.num===l.num,past=isT&&!cur&&l.end<=nowMin;
  const r=document.createElement("div");r.className="les"+(cur?" cur":nx?" nx2":past?" past":"");
  const t=document.createElement("div");t.className="t num";t.innerHTML=hm(l.start)+"<small>"+hm(l.end)+"</small>";
  const bb=document.createElement("div");bb.className="b";
  const mm=document.createElement("div");mm.className="m";mm.textContent=l.subject;
  const sm=document.createElement("small");sm.textContent=(cur?"сейчас · ":nx?"следующий · ":"")+"урок "+l.num;mm.appendChild(sm);
  r.append(t,bb,mm);
  if(l.changed){const z=document.createElement("span");z.className="sub";z.textContent="замена";r.appendChild(z)}
  if(l.room){const p=document.createElement("span");p.className="pl"+(cur?"":" s");p.textContent=l.room;r.appendChild(p)}
  list.appendChild(r)});
 if(!ls.length)list.innerHTML='<div class="brk" style="padding:20px 0;text-align:center">Уроков нет. Отдыхай.</div>';
 $("vac").textContent=vacationText(vacation(now));
}
render();setInterval(render,1000);
</script></body></html>`;
}

// ---------- запуск ----------

CH = await loadChanges();
const now = new Date();
const m = model(now);

if (config.runsInWidget || config.runsInAccessoryWidget) {
  const f = config.widgetFamily;
  const w = f === "accessoryRectangular" ? lockRect(m) : f === "accessoryCircular" ? lockCircle(m) : f === "accessoryInline" ? lockInline(m)
    : f === "medium" ? medium(m) : f === "large" || f === "extraLarge" ? large(m, now) : small(m);
  w.refreshAfterDate = nextRefresh(m, now);
  Script.setWidget(w);
} else {
  const wv = new WebView();
  await wv.loadHTML(appHtml(m, now));
  await wv.present(true);
}
Script.complete();
