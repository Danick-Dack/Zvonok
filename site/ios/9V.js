// Звонок 9В: виджеты для iPhone (приложение Scriptable).
// Экран блокировки: прямоугольный, круглый, строка над часами. Рабочий стол и StandBy: маленький, средний, большой.
// Если запустить скрипт в Scriptable, откроется экран как в приложении: таймер, кольцо и весь день.
//
// Можно спросить Siri или нажать кнопку действия: в «Командах» добавь Scriptable → Run Script → «Звонок» и «Показать результат».
//
// Заряд почти не тратится: таймер на виджете тикает силами iOS, а сам скрипт iOS запускает изредка.
// В интернет скрипт ходит только на свой сайт: за файлом замен (не чаще раза в 3 часа) и за новой версией себя
// (не чаще раза в 6 часов). Новая версия ставится сама, только если подписана ключом «Звонка» (Ed25519),
// и приходит уведомление «Звонок обновился».
// Выключить самообновление: AUTO_UPDATE = false ниже. Без сети всё работает как обычно.

const AUTO_UPDATE = true;

// ---------- данные (сайт подставляет сюда выбранный класс) ----------
const CLASS = "9В";
const THEME = ""; // код темы с сайта, например "ZV1;bg=0B1220;ac=60A5FA". Параметр виджета важнее
const VERSION = "cda4aa3bae";
const UPDATE_KEY = ""; // открытый ключ подписи новых версий (пусто: сборка была без ключа)
const BELLS = [[480,525],[530,575],[585,630],[650,695],[715,760],[770,815],[825,870],[880,925],[935,985]];
const WEEK = {"1":[["История Казахстана","41"],["Казахский яз. и лит.","44/43"],["Алгебра","36/59"],["Математика","36/59"],["Физика","73/72"],["Физика","73/72"],["Программирование","56/57"],["Классный час","46"]],"2":[["Физика","73/72"],["Физкультура","спортзал"],["Информатика","56/57"],["Худ. труд","11/1"],["Геометрия","36/59"],["Английский язык","47/46"],["Русская литература","46"]],"3":[["Алгебра","36/59"],["География","54"],["Химия","40"],["Казахский яз. и лит.","44/43"],["Биология","25"],["Основы права","28"],["Русский язык","46"],["Физкультура","спортзал"]],"4":[["Английский язык","47/35"],["Физика","73/72"],["Практ. физика","73/72"],["Казахский яз. и лит.","44/43"],["Алгебра","36/59"],["Алгебра","36/59"],["Русская литература","46"]],"5":[["Геометрия","36/59"],["Геометрия","36/59"],["Русский язык","46"],["Всемирная история","41"],["Физкультура","спортзал"],["Английский язык","47/28"]]};
const HOLIDAYS = [["2026-12-16","2026-12-16","День Независимости"],["2026-12-28","2027-01-10","Зимние каникулы"],["2027-03-08","2027-03-08","Международный женский день"],["2027-03-22","2027-03-28","Весенние каникулы"],["2027-05-01","2027-05-01","Праздник единства народа Казахстана"],["2027-05-03","2027-05-03","Выходной за 1 мая"],["2027-05-07","2027-05-07","День защитника Отечества"],["2027-05-09","2027-05-10","День Победы"],["2027-05-26","2027-08-31","Летние каникулы"]];
const TERM_END = "2026-12-27";
const SUBJECTS = {"alg":"Алгебра","geom":"Геометрия","mat":"Математика","fiz":"Физика","prf":"Практ. физика","kaz":"Казахский яз. и лит.","rus":"Русский язык","rlit":"Русская литература","eng":"Английский язык","istk":"История Казахстана","vist":"Всемирная история","geo":"География","bio":"Биология","him":"Химия","inf":"Информатика","prog":"Программирование","fk":"Физкультура","hud":"Худ. труд","kl":"Классный час","prav":"Основы права","nvp":"НВП","avto":"Автодело"};
const COLORS = {"Алгебра":["007AFF","0A84FF"],"Геометрия":["007AFF","0A84FF"],"Математика":["007AFF","0A84FF"],"Физика":["AF52DE","BF5AF2"],"Практ. физика":["AF52DE","BF5AF2"],"Казахский яз. и лит.":["FFCC00","FFD60A"],"Русский язык":["FF3B30","FF453A"],"Русская литература":["FF3B30","FF453A"],"Английский язык":["32ADE6","64D2FF"],"История Казахстана":["A2845E","AC8E68"],"Всемирная история":["A2845E","AC8E68"],"География":["00C7BE","63E6E2"],"Биология":["34C759","30D158"],"Химия":["30B0C7","40CBE0"],"Информатика":["5856D6","5E5CE6"],"Программирование":["5856D6","5E5CE6"],"Физкультура":["FF9500","FF9F0A"],"Худ. труд":["FF2D55","FF375F"],"Классный час":["8E8E93","8E8E93"],"Основы права":["A2845E","AC8E68"],"НВП":["34C759","30D158"],"Автодело":["FF9500","FF9F0A"]}; // цвета предметов: для светлого и тёмного фона
const SITE = "https://danick-dack.github.io/Zvonok/";
// ---------- конец данных ----------

// ---------- расписание ----------
// Правила дня (каникулы, замены, сокращённые звонки, что сейчас) общие с сайтом: сборка вставляет их ниже
// из site/src/js/rules.js. Здесь только то, что этим правилам нужно от виджета, и неделя класса в их формате.

const DAYN = ["Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"]; // с понедельника, как на сайте
const WHEN = ["в понедельник", "во вторник", "в среду", "в четверг", "в пятницу", "в субботу", "в воскресенье"];
const DSHORT = ["вс", "пн", "вт", "ср", "чт", "пт", "сб"]; // по getDay(): с воскресенья
const MONTHS = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"];
const SHORT = {"Казахский яз. и лит.": "Казахский", "Английский язык": "Английский", "Русский язык": "Русский", "Русская литература": "Литература",
  "История Казахстана": "Ист. Казахстана", "Всемирная история": "Всем. история", "Программирование": "Программир.", "Практ. физика": "Пр. физика"};
const DATA = {holidays: HOLIDAYS, subjects: SUBJECTS, term_end: TERM_END};
let CHANGES = {days: {}}; // замены с сайта

function hm(min) { const m = min % 60; return Math.floor(min / 60) + ":" + (m < 10 ? "0" : "") + m; }
function two(n) { return (n < 10 ? "0" : "") + n; }
function iso(d) { return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate()); }
function dm(d) { return d.getDate() + " " + MONTHS[d.getMonth()]; }
function dow(d) { return (d.getDay() + 6) % 7; } // 0 = понедельник
function at(day, min) { return new Date(day.getFullYear(), day.getMonth(), day.getDate(), 0, min, 0, 0); }
function toMin(t) { const m = /^\s*(\d{1,2})[:.](\d{2})\s*$/.exec(String(t || "")); if (!m) return -1; const H = +m[1], M = +m[2]; return H < 24 && M < 60 ? H * 60 + M : -1; }
function short(s) { return SHORT[s] || s; }
function word(n, one, few, many) { const a = n % 10, b = n % 100; return n + " " + (a === 1 && b !== 11 ? one : a >= 2 && a <= 4 && (b < 12 || b > 14) ? few : many); }
function plural(n) { return word(n, "урок", "урока", "уроков"); }
function clean(s) { return String(s == null ? "" : s).replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 40); }
function roomName(r) { return r === "с/з" ? "спортзал" : r || ""; }
/** Обычная неделя класса в формате правил: 7 дней с понедельника, уроки {num, start, end, subject, room}. */
function classWeek() {
  return classWeek.w || (classWeek.w = [1, 2, 3, 4, 5, 6, 0].map(wd => (WEEK[wd] || []).map((p, i) =>
    p && p[0] && BELLS[i] ? {num: i + 1, start: BELLS[i][0], end: BELLS[i][1], subject: p[0], room: p[1] || ""} : null).filter(Boolean)));
}
/** Уроки своего класса на дату: с каникулами, заменами и сокращёнными звонками. */
function lessonsOn(d) { return dayLessons(CLASS, d); }
/** Коротко, когда следующие уроки: «завтра», «пн», «12.01». */
function nShort(s) { return !s.nDate ? "" : s.nDays === 1 ? "завтра" : s.nDays > 6 ? s.nDate.getDate() + "." + two(s.nDate.getMonth() + 1) : DSHORT[s.nDate.getDay()]; }

// ---------- правила дня (сборка вставляет сюда site/src/js/rules.js, править там) ----------
/* Звонок · правила дня: каникулы, замены, сокращённые звонки и что происходит в момент now.
   Один файл на сайт и на виджет iPhone. Сборка (tools/build_site.py) вставляет его в скрипт Scriptable
   между метками, поэтому правила у сайта и виджета не расходятся. Править только здесь.
   Только объявления функций: виджет передаёт их в свой экран через toString().
   Снаружи нужны: DATA {holidays, subjects, term_end}, CHANGES, BELLS, classWeek(c), iso, dow, toMin, clean,
   roomName, hm, dm, at, word, DAYN и WHEN (с понедельника). */

/** Замены с сайта: оставляем только то, что правила умеют читать. Кривое поле в changes.json выпадает,
 *  а не роняет расписание у всех (на сайте и в виджете одинаково). */
function cleanChanges(j) {
  const out = {days: {}}, str = v => typeof v === "string", obj = v => !!v && typeof v === "object" && !Array.isArray(v);
  if (!obj(j)) return out;
  if (str(j.updated)) out.updated = j.updated;
  const days = obj(j.days) ? j.days : {};
  for (const date of Object.keys(days)) {
    const c = days[date];
    if (!/^\d{4}-\d\d-\d\d$/.test(date) || !obj(c)) continue;
    const day = {};
    if (str(c.note)) day.note = c.note;
    if (str(c.off) && c.off.trim()) day.off = c.off;
    else if (c.off === true) day.off = "Уроков нет"; // off: true тоже значит «уроков нет», пустая строка нет
    // пустой список звонков считаем ошибкой правки: звонки в этот день обычные
    if (Array.isArray(c.bells) && c.bells.length) day.bells = c.bells.map(b => Array.isArray(b) && str(b[0]) && str(b[1]) ? [b[0], b[1]] : null);
    if (obj(c.classes)) {
      day.classes = {};
      for (const cls of Object.keys(c.classes)) {
        const fix = c.classes[cls], f = {};
        if (!obj(fix)) continue;
        for (const n of Object.keys(fix)) {
          const v = fix[n];
          if (!/^\d{1,2}$/.test(n)) continue;
          if (v === null || str(v)) f[n] = v;
          else if (obj(v)) f[n] = {subject: str(v.subject) ? v.subject : undefined, room: str(v.room) || typeof v.room === "number" ? String(v.room) : undefined};
        }
        day.classes[cls] = f;
      }
    }
    out.days[date] = day;
  }
  return out;
}
/** Каникулы и праздники: [с, по, название]. */
function holiday(d) { const t = iso(d); return (DATA.holidays || []).find(x => t >= x[0] && t <= x[1]) || null; }
/** Замены на дату: data/changes.json (копия на момент сборки, свежая подтягивается с сайта). */
function chDay(d) { return (CHANGES.days || {})[iso(d)] || null; }
/** null, «нет» или «нет урока» в заменах: урока нет. */
function noLesson(v) { return v === null || (typeof v === "string" && /^нет/i.test(v.trim())); }
/** День без уроков у всех: каникулы, праздник или «off» в заменах. [с, по, название] */
function dayOff(d) { const x = holiday(d), c = chDay(d); return x || (c && c.off ? [iso(d), iso(d), clean(c.off)] : null); }
/** Звонки на дату: свои на день целиком из замен или обычные. Урока без звонка в этот день нет. */
function dayBells(d) {
  const c = chDay(d);
  if (!c || !Array.isArray(c.bells)) return BELLS;
  return c.bells.map((x, i) => { const s = x ? toMin(x[0]) : -1, e = x ? toMin(x[1]) : -1; return s >= 0 && e > s ? [s, e] : BELLS[i] || null; });
}
/** Уроки класса на конкретную дату: с каникулами, заменами и сокращёнными звонками. */
function dayLessons(c, d) {
  if (dayOff(d)) return [];
  const base = classWeek(c)[dow(d)], ch = chDay(d);
  if (!ch) return base;
  const bells = dayBells(d), fix = (ch.classes || {})[c] || {}, short = bells !== BELLS;
  const nums = [...new Set(base.map(l => l.num).concat(Object.keys(fix).map(Number).filter(n => n > 0)))].sort((a, b) => a - b);
  return nums.map(n => {
    let l = base.find(x => x.num === n);
    const f = fix[n];
    if (f !== undefined && noLesson(f)) return null;
    if (f && typeof f === "object") l = {num: n, changed: true,
      subject: f.subject ? DATA.subjects[f.subject] || clean(f.subject) : l ? l.subject : "",
      room: f.room != null ? roomName(clean(f.room)) : l ? l.room : ""};
    if (!l || !l.subject || !bells[n - 1]) return null;
    return Object.assign({}, l, {start: bells[n - 1][0], end: bells[n - 1][1], short});
  }).filter(Boolean);
}

/**
 * Что происходит в момент now. src = {at(date) → уроки на дату, real: учитывать каникулы}.
 * kind: LESSON (идёт урок), BREAK (перемена), BEFORE (до первого урока), AFTER (уроки кончились), OFF (уроков нет).
 */
function compute(now, src) {
  const s = {}, list = src.at(now), sec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
  if (list.length) {
    const first = list[0], last = list[list.length - 1];
    s.count = list.length; s.end = hm(last.end); s.list = list;
    if (sec < first.start * 60) {
      return Object.assign(s, {kind: "BEFORE", next: first, start: at(now, first.start - 60), target: at(now, first.start), soon: sec >= (first.start - 60) * 60});
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
  } else { s.kind = "OFF"; s.holiday = src.real ? dayOff(now) : null; }
  for (let d = 1; d <= 120; d++) {
    const date = new Date(now); date.setDate(date.getDate() + d);
    const l = src.at(date);
    if (!l.length) continue;
    const far = d > 6;
    Object.assign(s, {next: l[0], nList: l, nDate: date, nDays: d, nCount: l.length, nEnd: hm(l[l.length - 1].end),
      nLabel: d === 1 ? "Завтра" : far ? dm(date) : DAYN[dow(date)], nWhen: d === 1 ? "завтра" : far ? dm(date) : WHEN[dow(date)], target: at(date, l[0].start)});
    break;
  }
  return s;
}
/** Сколько учебных дней до ближайших каникул (сегодня считается, если уроки ещё не кончились). */
function vacation(now, src) {
  if (!src.real || holiday(now) || (DATA.term_end && iso(now) > DATA.term_end)) return null;
  const t = iso(now), v = (DATA.holidays || []).filter(x => /каникул/i.test(x[2]) && x[0] > t).sort((a, b) => a[0] < b[0] ? -1 : 1)[0];
  if (!v) return null;
  const d = new Date(now), nowMin = now.getHours() * 60 + now.getMinutes();
  let n = 0, today = false;
  for (let i = 0; i < 400 && iso(d) < v[0]; i++, d.setDate(d.getDate() + 1)) {
    const l = src.at(d);
    if (!l.length || (i === 0 && nowMin >= l[l.length - 1].end)) continue;
    n++; if (i === 0) today = true;
  }
  return n > 60 ? null : {n, today, name: v[2], from: v[0]}; // больше 60 дней: рано считать
}
function vacationText(v) {
  if (!v) return "";
  if (!v.n) return "Дальше " + v.name.toLowerCase();
  if (v.n === 1 && v.today) return "Сегодня последний учебный день, потом " + v.name.toLowerCase();
  return "До каникул " + word(v.n, "учебный день", "учебных дня", "учебных дней");
}
// ---------- конец правил дня ----------

// всё, что показывают виджеты и экран, в одном месте
function model(now) {
  const s = compute(now, {at: lessonsOn, real: true}), n = s.next, c = s.cur;
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
      big: hm(n.start), bigSub: "сегодня", capW: "до", capV: s.end});
  } else {
    const h = s.holiday, vac = !!h && /каникул/i.test(h[2]);
    m.hol = !!h && (!n || s.nDays > 1); // однодневный праздник выглядит как обычный вечер перед уроками
    m.label = s.kind === "AFTER" ? "УРОКИ ЗАКОНЧИЛИСЬ" : h ? h[2].toUpperCase() : "ВЫХОДНОЙ";
    m.tag = s.kind === "AFTER" ? (n ? s.nLabel.toUpperCase() : "ВСЁ") : vac ? "КАНИКУЛЫ" : "ВЫХОДНОЙ";
    m.title = s.kind === "AFTER" ? "Отдыхай" : h ? (vac ? "Каникулы" : "Выходной") : "Отдыхай";
    if (n) {
      Object.assign(m, {nextSubj: n.subject, nextRoom: n.room, nextChanged: n.changed, go: n.room});
      if (m.hol) {
        m.days = s.nDays;
        Object.assign(m, {big: String(s.nDays), bigSub: word(s.nDays, "день", "дня", "дней").replace(/^\d+ /, ""),
          capW: word(s.nDays, "день", "дня", "дней").replace(/^\d+ /, "") + " до уроков", capV: dm(s.nDate), nextHead: dm(s.nDate).toUpperCase() + " · " + hm(n.start)});
      } else Object.assign(m, {big: hm(n.start), bigSub: nShort(s), capW: "до", capV: s.nEnd, nextHead: s.nLabel.toUpperCase() + " · " + plural(s.nCount).toUpperCase()});
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

// ---------- сеть ----------
// Радио телефона дорого будить, поэтому в сеть ходим редко и одним заходом. Замены с сайта берём не чаще
// раза в 3 часа, и в тот же заход (если с прошлой проверки прошло 6 часов) спрашиваем, нет ли новой версии:
// оба запроса идут одновременно. Если связь плохая и запрос долго висел, следующая попытка через 30 минут,
// а не на каждой перерисовке виджета. Когда сети нет совсем, запрос падает сразу и почти ничего не стоит:
// тогда пробуем при каждой перерисовке, как раньше, чтобы замены пришли, как только появится интернет.
const NET = {changes: false, update: false, st: {}, fm: null, stamp: ""};
function saveNet() { try { NET.fm.writeString(NET.stamp, JSON.stringify(NET.st)); } catch (e) {} }
function netPlan() {
  const now = Date.now();
  try {
    const fm = NET.fm = FileManager.local(), dir = fm.documentsDirectory(), saved = fm.joinPath(dir, "zvonok-changes.json");
    NET.stamp = fm.joinPath(dir, "zvonok-update.json");
    try { if (fm.fileExists(NET.stamp)) NET.st = JSON.parse(fm.readString(NET.stamp)) || {}; } catch (e) {}
    // Math.abs: если часы на телефоне перевели назад, не ждём, пока время «догонит» отметку
    const age = fm.fileExists(saved) ? Math.abs(now - fm.modificationDate(saved).getTime()) : Infinity;
    NET.changes = age > 3 * 3600000 && Math.abs(now - (NET.st.slow || 0)) > 30 * 60000;
    NET.update = NET.changes && AUTO_UPDATE && !!VERSION && typeof module !== "undefined" && Math.abs(now - (NET.st.t || 0)) > 6 * 3600000;
    if (NET.update) { NET.st.t = now; saveNet(); }
  } catch (e) { /* без доступа к файлам в сеть не ходим: иначе ходили бы при каждой перерисовке */ }
}

// замены: сохранённые с прошлого раза; свежие с сайта, только если netPlan сказал, что пора
async function loadChanges(fetch) {
  let fm = null, path = "", data = null;
  try {
    fm = FileManager.local(); path = fm.joinPath(fm.documentsDirectory(), "zvonok-changes.json");
    if (fm.fileExists(path)) data = JSON.parse(fm.readString(path));
  } catch (e) { data = null; }
  if (fetch) {
    const t0 = Date.now();
    try {
      const r = new Request(SITE + "changes.json");
      r.timeoutInterval = 4;
      const j = await r.loadJSON();
      if (j && typeof j.days === "object") { data = j; if (fm) fm.writeString(path, JSON.stringify(j)); }
    } catch (e) {
      // работаем с тем, что есть; запрос висел долго (плохая связь): радио не мучаем 30 минут
      if (Math.abs(Date.now() - t0) > 1500) { NET.st.slow = Date.now(); saveNet(); }
    }
  }
  return data && typeof data.days === "object" ? data : {days: {}};
}

// ---------- оформление ----------

function parseTheme(code) {
  const t = {bg: "000000", ac: "FFFFFF", p: 1, n: 1, d: 1, sc: 0, tx: ""};
  code = String(code || "").trim();
  // своей темы нет: фирменный вид, кольцо и кабинет в цвете урока (в своей теме цвет урока как выбрали на сайте)
  if (!code.startsWith("ZV1") || code.length > 200) { t.sc = 1; return t; }
  for (const part of code.split(";")) {
    const k = part.split("=")[0].trim(), v = (part.split("=")[1] || "").trim();
    if ((k === "bg" || k === "ac" || k === "tx") && /^[0-9a-fA-F]{6}$/.test(v)) t[k] = v.toUpperCase();
    else if (k === "sc") t.sc = v === "1" ? 1 : 0;
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
const FG = TH.tx || (LIGHT ? "111111" : "FFFFFF");
let ACC = Math.abs(lum(TH.ac) - lum(TH.bg)) < 0.25 ? FG : TH.ac; // при «цвете урока» меняется на цвет предмета
let TINT = ""; // цвет урока для мягкого свечения фона сверху (только в учебный день, при «цвете урока»)
const c = a => new Color("#" + FG, a);
/** Цвет предмета под фон виджета или null (без «цвета урока» или неизвестный предмет). */
function subjColor(subject, a) { const x = TH.sc && COLORS[subject]; return x ? new Color("#" + x[LIGHT ? 0 : 1], a) : null; }
const acc = a => new Color("#" + ACC, a);
const WHITE = a => new Color("#FFFFFF", a);

function background(w) {
  const g = new LinearGradient();
  const bg = TH.bg, glow = (x, k) => TINT ? mix(x, TINT, k) : x; // сверху мягкий цвет урока, как на сайте и в Android
  if (bg === "000000") { g.colors = [glow("1C1C21", 0.26), "0A0A0C", "000000"].map(x => new Color("#" + x)); g.locations = [0, 0.55, 1]; }
  else if (LIGHT) { g.colors = [glow(mix(bg, "FFFFFF", 0.55), 0.16), bg].map(x => new Color("#" + x)); g.locations = [0, 1]; }
  else { g.colors = [glow(mix(bg, "FFFFFF", 0.10), 0.24), bg, mix(bg, "000000", 0.35)].map(x => new Color("#" + x)); g.locations = [0, 0.6, 1]; }
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
    T(w, "Сегодня " + plural(s.count) + " · до " + s.end, Font.mediumSystemFont(12), 0.7);
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
  if (m.kind === "BEFORE" && s.soon) return ["СЕГОДНЯ", plural(s.count) + " · до " + s.end, ""];
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
    // полоска урока в цвете предмета (прошедшие бледнее), без «цвета урока» как раньше
    const mark = fixed(r, 4, 16); mark.cornerRadius = 2;
    mark.backgroundColor = subjColor(l.subject, past ? 0.35 : cur || next ? 1 : 0.8) || (cur ? acc(1) : next ? c(0.62) : past ? c(0.16) : c(0.30));
    r.addSpacer(8);
    T(r, l.subject, cur ? Font.semiboldSystemFont(fs) : Font.mediumSystemFont(fs), a, c(1), 0.8);
    r.addSpacer();
    if (l.changed) { T(r, "замена", Font.semiboldSystemFont(10), past ? 0.32 : 0.62, c(1)); r.addSpacer(6); }
    if ((cur || (next && s.kind === "BREAK")) && m.timer) { const tb = fixed(r, 44); timer(tb, m.target, Font.mediumRoundedSystemFont(11), 0.62, c(1), "r"); r.addSpacer(6); }
    if (l.room) T(r, l.room, Font.semiboldRoundedSystemFont(12), cur ? 1 : past ? 0.32 : 0.62, cur ? acc(1) : c(1));
    w.addSpacer(2);
  }
  w.addSpacer();
  const d = now, h = s.holiday, v = vacation(now, {at: lessonsOn, real: true});
  let foot = DSHORT[d.getDay()].toUpperCase() + ", " + dm(d).toUpperCase();
  if (h && /каникул/i.test(h[2])) foot = h[2].toUpperCase() + " · ДО " + dm(new Date(h[1] + "T12:00:00")).toUpperCase();
  else if (v) foot += " · " + vacationText(v).toUpperCase();
  T(w, foot, Font.semiboldSystemFont(10), 0.42, c(1), 0.7);
  return w;
}

// ---------- экран внутри Scriptable ----------

function appHtml(m, now) {
  const core = [hm, two, iso, dm, dow, at, toMin, short, word, plural, clean, roomName, classWeek, lessonsOn, nShort,
    holiday, chDay, noLesson, dayOff, dayBells, dayLessons, compute, vacation, vacationText, model]
    .map(f => f.toString()).join("\n");
  const data = {CLASS, BELLS, WEEK, DATA, CHANGES, DAYN, WHEN, DSHORT, MONTHS, SHORT, FG, ACC, BG: TH.bg, COL: TH.sc ? COLORS : null, LT: LIGHT};
  const glow = "#" + mix(mix(TH.bg, "FFFFFF", 0.10), TINT || TH.bg, TINT ? 0.3 : 0), fg = "#" + FG, ac = "#" + ACC, a = (hex, x) => hex + Math.round(x * 255).toString(16).padStart(2, "0");
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
const {CLASS,BELLS,WEEK,DATA,CHANGES,DAYN,WHEN,DSHORT,MONTHS,SHORT}=D;
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
 arc.setAttribute("stroke-dasharray",v<0?"0 999":(v*C)+" "+C);arc.style.display=k.style.display=v<0?"none":""; // пустая дуга с круглым концом рисовала точку
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
  const bb=document.createElement("div");bb.className="b";const sc=D.COL&&D.COL[l.subject];if(sc)bb.style.background="#"+sc[D.LT?0:1];
  const mm=document.createElement("div");mm.className="m";mm.textContent=l.subject;
  const sm=document.createElement("small");sm.textContent=(cur?"сейчас · ":nx?"следующий · ":"")+"урок "+l.num;mm.appendChild(sm);
  r.append(t,bb,mm);
  if(l.changed){const z=document.createElement("span");z.className="sub";z.textContent="замена";r.appendChild(z)}
  if(l.room){const p=document.createElement("span");p.className="pl"+(cur?"":" s");p.textContent=l.room;r.appendChild(p)}
  list.appendChild(r)});
 if(!ls.length)list.innerHTML='<div class="brk" style="padding:20px 0;text-align:center">Уроков нет. Отдыхай.</div>';
 $("vac").textContent=vacationText(vacation(now,{at:lessonsOn,real:true}));
}
render();setInterval(render,1000);
</script></body></html>`;
}

// ---------- самообновление: новая версия скрипта для своего класса с сайта ----------
// Когда пора (netPlan: не чаще раза в 6 часов), спрашиваем ios/versions.json; если версия новее, скачиваем
// ios/<класс>.js, проверяем, что это целый скрипт «Звонка» для того же класса и что он собирается,
// и записываем поверх себя. Тема с сайта сохраняется.
// ---------- подпись новых версий: Ed25519 (RFC 8032) ----------
// Сборка подписывает скрипт каждого класса закрытым ключом «Звонка» (он есть только в секретах GitHub,
// в репозитории и на сайте его нет), а открытый ключ вписан в скрипт (UPDATE_KEY в данных выше).
// Если ключ вписан, виджет ставит только версию с верной подписью: подменённый на хостинге файл не примет.
// В Scriptable нет WebCrypto, поэтому SHA-512 и Ed25519 здесь свои, на BigInt; проверка занимает доли секунды
// и бывает только при выходе новой версии. Автотесты сверяют их с Python и libsodium.
function b64bytes(s) {
  const A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/", out = [];
  let buf = 0, n = 0;
  for (const ch of String(s || "").replace(/[^A-Za-z0-9+/]/g, "")) {
    buf = ((buf << 6) | A.indexOf(ch)) & 0xffff; n += 6;
    if (n >= 8) { n -= 8; out.push((buf >> n) & 255); }
  }
  return out;
}
function utf8(str) {
  const out = [];
  for (const ch of String(str)) {
    const c = ch.codePointAt(0);
    if (c < 0x80) out.push(c);
    else if (c < 0x800) out.push(0xc0 | c >> 6, 0x80 | c & 63);
    else if (c < 0x10000) out.push(0xe0 | c >> 12, 0x80 | c >> 6 & 63, 0x80 | c & 63);
    else out.push(0xf0 | c >> 18, 0x80 | c >> 12 & 63, 0x80 | c >> 6 & 63, 0x80 | c & 63);
  }
  return out;
}
function sha512(bytes) {
  const M = (1n << 64n) - 1n;
  if (!sha512.K) {
    // константы SHA-512 считаем, а не переписываем: 64 бита дробной части кубических корней первых 80 простых
    // (и квадратных корней первых 8 для начального значения), как в стандарте FIPS 180-4
    const primes = [];
    for (let x = 2; primes.length < 80; x++) if (primes.every(y => x % y)) primes.push(x);
    const root = (x, k) => {
      const K = BigInt(k);
      let r = 1n << BigInt(Math.ceil(x.toString(2).length / k));
      for (;;) { const s = ((K - 1n) * r + x / r ** (K - 1n)) / K; if (s >= r) return r; r = s; }
    };
    sha512.K = primes.map(x => root(BigInt(x) << 192n, 3) & M);
    sha512.H = primes.slice(0, 8).map(x => root(BigInt(x) << 128n, 2) & M);
  }
  const K = sha512.K, rotr = (x, n) => ((x >> n) | (x << (64n - n))) & M;
  const m = bytes.slice(), bits = BigInt(bytes.length) * 8n;
  m.push(0x80);
  while (m.length % 128 !== 112) m.push(0);
  for (let i = 15; i >= 0; i--) m.push(Number((bits >> BigInt(i * 8)) & 255n));
  let h = sha512.H.slice();
  const w = new Array(80);
  for (let o = 0; o < m.length; o += 128) {
    for (let t = 0; t < 16; t++) { let x = 0n; for (let j = 0; j < 8; j++) x = (x << 8n) | BigInt(m[o + t * 8 + j]); w[t] = x; }
    for (let t = 16; t < 80; t++) {
      const a = w[t - 15], b = w[t - 2];
      w[t] = (w[t - 16] + (rotr(a, 1n) ^ rotr(a, 8n) ^ (a >> 7n)) + w[t - 7] + (rotr(b, 19n) ^ rotr(b, 61n) ^ (b >> 6n))) & M;
    }
    let [a, b, c, dd, e, f, g, hh] = h;
    for (let t = 0; t < 80; t++) {
      const t1 = (hh + (rotr(e, 14n) ^ rotr(e, 18n) ^ rotr(e, 41n)) + ((e & f) ^ (~e & M & g)) + K[t] + w[t]) & M;
      const t2 = ((rotr(a, 28n) ^ rotr(a, 34n) ^ rotr(a, 39n)) + ((a & b) ^ (a & c) ^ (b & c))) & M;
      hh = g; g = f; f = e; e = (dd + t1) & M; dd = c; c = b; b = a; a = (t1 + t2) & M;
    }
    h = [a, b, c, dd, e, f, g, hh].map((x, i) => (h[i] + x) & M);
  }
  const out = [];
  for (const x of h) for (let j = 7; j >= 0; j--) out.push(Number((x >> BigInt(j * 8)) & 255n));
  return out;
}
function edVerify(pub, msg, sig) {
  const P = 2n ** 255n - 19n, L = 2n ** 252n + 27742317777372353535851937790883648493n;
  const mod = x => ((x % P) + P) % P;
  const pow = (b, e) => { let r = 1n; b = mod(b); for (; e > 0n; e >>= 1n, b = b * b % P) if (e & 1n) r = r * b % P; return r; };
  const inv = x => pow(x, P - 2n);
  const D = mod(-121665n * inv(121666n)), I = pow(2n, (P - 1n) / 4n);
  const num = b => { let x = 0n; for (let i = b.length - 1; i >= 0; i--) x = (x << 8n) | BigInt(b[i]); return x; };
  const xOf = (y, sign) => {
    if (y >= P) return null;
    const x2 = mod((y * y - 1n) * inv(D * y * y + 1n));
    if (x2 === 0n) return sign ? null : 0n;
    let x = pow(x2, (P + 3n) / 8n);
    if (mod(x * x - x2) !== 0n) x = x * I % P;
    if (mod(x * x - x2) !== 0n) return null;
    if (Number(x & 1n) !== sign) x = P - x;
    return x;
  };
  const point = b => { const y = num(b) & ((1n << 255n) - 1n), x = xOf(y, b[31] >> 7); return x === null ? null : [x, y, 1n, x * y % P]; };
  const add = (p, q) => {
    const A = mod((p[1] - p[0]) * (q[1] - q[0])), B = (p[1] + p[0]) * (q[1] + q[0]) % P;
    const C = 2n * p[3] * q[3] % P * D % P, Z = 2n * p[2] * q[2] % P;
    const E = mod(B - A), F = mod(Z - C), G = (Z + C) % P, H = (B + A) % P;
    return [E * F % P, G * H % P, F * G % P, E * H % P];
  };
  const mul = (s, p) => { let q = [0n, 1n, 1n, 0n]; for (; s > 0n; s >>= 1n, p = add(p, p)) if (s & 1n) q = add(q, p); return q; };
  const same = (p, q) => mod(p[0] * q[2] - q[0] * p[2]) === 0n && mod(p[1] * q[2] - q[1] * p[2]) === 0n;
  if (pub.length !== 32 || sig.length !== 64) return false;
  const A = point(pub), R = point(sig.slice(0, 32)), S = num(sig.slice(32));
  if (!A || !R || S >= L) return false;
  const gy = 4n * inv(5n) % P, gx = xOf(gy, 0), B = [gx, gy, 1n, gx * gy % P];
  const h = num(sha512(sig.slice(0, 32).concat(pub, msg))) % L;
  return same(mul(S, B), add(R, mul(h, A)));
}
/** Новая версия подписана ключом «Звонка». */
function signedByZvonok(code, sig) {
  try { return !!sig && edVerify(b64bytes(UPDATE_KEY), utf8(code), b64bytes(sig)); } catch (e) { return false; }
}
function compiles(code) {
  try { new (Object.getPrototypeOf(async function () {}).constructor)(code); return true; }
  catch (e) { return !(e instanceof SyntaxError); }
}
async function selfUpdate() {
  if (!AUTO_UPDATE || !VERSION || typeof module === "undefined") return;
  try {
    const slug = CLASS.replace(/[АБВГ]/g, x => ({"А": "A", "Б": "B", "В": "V", "Г": "G"})[x]);
    const r = new Request(SITE + "ios/versions.json");
    r.timeoutInterval = 4;
    const v = await r.loadJSON();
    const latest = v && v.classes && v.classes[slug];
    if (!latest || latest === VERSION) return;
    const rr = new Request(SITE + "ios/" + slug + ".js");
    rr.timeoutInterval = 8;
    let code = await rr.loadString();
    const ok = code && code.length > 20000 && code.length < 400000 && code.includes('const VERSION = "' + latest + '"')
      && code.includes('const CLASS = "' + CLASS + '"') && code.includes("Script.complete()");
    // с ключом в скрипте ставим только подписанную версию; без ключа (первая сборка без секрета) как раньше
    if (!ok || (UPDATE_KEY && !signedByZvonok(code, v.sig && v.sig[slug])) || !compiles(code)) return;
    if (THEME) code = code.replace('const THEME = "";', "const THEME = " + JSON.stringify(THEME) + ";");
    const icloud = FileManager.iCloud();
    const target = icloud.isFileStoredIniCloud(module.filename) ? icloud : FileManager.local();
    target.writeString(module.filename, code);
    const n = new Notification();
    n.title = "Звонок обновился";
    n.body = v.note ? "Что нового: " + v.note : "Новое расписание и улучшения уже в виджете.";
    n.openURL = SITE + "changelog.html";
    await n.schedule();
  } catch (e) { /* нет сети или нет доступа к файлу: попробуем в следующий раз */ }
}

// ---------- Siri и «Команды»: ответ одной фразой ----------
function spoken(m, now) {
  const s = m.s, n = s.next;
  const say = t => t.replace(/яз\. и лит\./g, "язык и литература").replace(/^Практ\. /, "практическая ").replace(/^Худ\. /, "художественный ")
    .replace(/^Ист\. /, "история ").replace(/^Всем\. /, "всемирная ").replace(/^НВП$/, "Н В П");
  const low = t => { t = say(t); return /^[А-ЯЁ][а-яё]/.test(t) ? t[0].toLowerCase() + t.slice(1) : t; };
  const where = r => !r ? "" : r === "спортзал" ? ", спортзал" : ", кабинет " + r.split("/").filter(Boolean).join(" или ");
  const left = t => {
    const min = Math.max(1, Math.ceil((t.getTime() - now.getTime()) / 60000));
    return min < 60 ? word(min, "минута", "минуты", "минут") : word(Math.floor(min / 60), "час", "часа", "часов") + (min % 60 ? " " + word(min % 60, "минута", "минуты", "минут") : "");
  };
  if (s.kind === "LESSON") return "Сейчас " + low(s.cur.subject) + where(s.cur.room) + ". До звонка " + left(s.target) + "."
    + (n ? " Дальше " + low(n.subject) + where(n.room) + "." : " Это последний урок.");
  if (s.kind === "BREAK") return (n.start - s.cur.end > 25 ? "Окно" : "Перемена") + ". Через " + left(s.target) + " " + low(n.subject) + where(n.room) + ".";
  if (s.kind === "BEFORE") return (s.soon ? "Первый урок через " + left(s.target) : "Сегодня " + plural(s.count) + ", первый в " + hm(n.start)) + ": " + low(n.subject) + where(n.room) + ".";
  const head = s.kind === "AFTER" ? "Уроки закончились." : s.holiday ? s.holiday[2] + "." : "Сегодня выходной.";
  if (!n) return head;
  return head + (s.nLabel === "Завтра" ? " Завтра первый урок в " : " Следующие уроки " + s.nWhen + ". Первый в ") + hm(n.start) + ": " + low(n.subject) + where(n.room) + ".";
}

// ---------- запуск ----------

netPlan();
CHANGES = cleanChanges((await Promise.all([loadChanges(NET.changes), NET.update ? selfUpdate() : null]))[0]);
const now = new Date();
const m = model(now);
// «цвет урока»: кольцо и кабинет в цвете текущего предмета, на перемене следующего
if (TH.sc) {
  const subj = m.s.kind === "LESSON" ? m.s.cur && m.s.cur.subject : m.s.next && m.s.next.subject;
  const col = subj && COLORS[subj];
  if (col) ACC = col[LIGHT ? 0 : 1];
  if (col && hasLessonToday(m)) TINT = col[1]; // в выходной фон не красим уроком понедельника
}

if (config.runsInWidget || config.runsInAccessoryWidget) {
  const f = config.widgetFamily;
  const w = f === "accessoryRectangular" ? lockRect(m) : f === "accessoryCircular" ? lockCircle(m) : f === "accessoryInline" ? lockInline(m)
    : f === "medium" ? medium(m) : f === "large" || f === "extraLarge" ? large(m, now) : small(m);
  w.refreshAfterDate = nextRefresh(m, now);
  Script.setWidget(w);
} else if (config.runsWithSiri || config.runsInShortcuts) {
  Script.setShortcutOutput(spoken(m, now));
} else {
  Script.setShortcutOutput(spoken(m, now));
  const wv = new WebView();
  await wv.loadHTML(appHtml(m, now));
  await wv.present(true);
}
Script.complete();
