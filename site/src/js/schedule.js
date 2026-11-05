/* Звонок · расписание: уроки, звонки, каникулы, замены, кабинеты.
   Правила дня (каникулы, замены, «что сейчас») в rules.js: тот же файл сборка вставляет в скрипт iPhone.
   Та же логика в приложении Android (Schedule.java). */

const DATA = /*@DATA@*/null;
let CHANGES = cleanChanges(/*@CHANGES@*/null || {days: {}}); // cleanChanges в rules.js
const BELLS = DATA.bells.map(b => [toMin(b[0]), toMin(b[1])]);
const CLASSES = Object.keys(DATA.classes).sort((a, b) => parseInt(a) - parseInt(b) || a.localeCompare(b, "ru"));
const roomName = r => r === "с/з" ? "спортзал" : r;
/* у каждого предмета свой цвет: название → оттенок (сборка берёт из tools/build_site.py) */
const SUBJ_HUE = /*@HUES@*/null || {};
const HUE_IDS = ["blue", "purple", "yellow", "red", "cyan", "brown", "mint", "green", "teal", "indigo", "orange", "pink"];
/** Оттенок предмета. Для своих предметов из конструктора подбираем по похожему названию или по буквам. */
function hueOf(subject) {
  const s = String(subject || "").trim();
  if (!s) return "none";
  if (SUBJ_HUE[s]) return SUBJ_HUE[s];
  const low = s.toLowerCase().slice(0, 5);
  for (const name in SUBJ_HUE) if (name.toLowerCase().startsWith(low)) return SUBJ_HUE[name];
  let x = 0;
  for (const ch of s.toLowerCase()) x = (x * 31 + ch.charCodeAt(0)) >>> 0;
  return HUE_IDS[x % HUE_IDS.length];
}
const LAT = {"А": "a", "Б": "b", "В": "v", "Г": "g"};
const slugOf = c => c.replace(/[АБВГ]/g, x => LAT[x]).toLowerCase();

/** Обычная неделя класса: 7 дней, в каждом уроки {num, start, end, subject, room}. */
const CW = {};
function classWeek(cls) {
  if (CW[cls]) return CW[cls];
  const days = DATA.classes[cls] || [];
  return CW[cls] = Array.from({length: 7}, (_, d) => (days[d] || []).map((raw, i) => {
    if (!raw || !BELLS[i]) return null;
    const sp = raw.indexOf(" "), code = sp < 0 ? raw : raw.slice(0, sp), room = sp < 0 ? "" : raw.slice(sp + 1);
    return {num: i + 1, start: BELLS[i][0], end: BELLS[i][1], subject: DATA.subjects[code] || code, room: roomName(room)};
  }).filter(Boolean));
}
/** Своё расписание из конструктора: {bells: [[с, по]], days: [[[предмет, кабинет]]]}. */
function customWeek(c) {
  const bells = (c.bells || DATA.bells).map(b => [toMin(b && b[0]), toMin(b && b[1])]);
  return Array.from({length: 7}, (_, d) => ((c.days || [])[d] || []).map((l, i) => {
    if (!l || !clean(l[0]) || !bells[i] || bells[i][0] < 0 || bells[i][1] <= bells[i][0]) return null;
    return {num: i + 1, start: bells[i][0], end: bells[i][1], subject: clean(l[0]), room: roomName(clean(l[1]))};
  }).filter(Boolean));
}

/* Каникулы, замены, звонки на дату, уроки на дату и что происходит сейчас: rules.js (общий с виджетом iPhone). */
/** Номера отменённых уроков класса на дату. */
const cancelled = (c, d) => { const ch = chDay(d), fix = ch && (ch.classes || {})[c] || {}; return Object.keys(fix).filter(n => noLesson(fix[n])).map(Number); };

/** 24:16 или 1:02:03. Округляем вверх: 0:00 должен совпасть со звонком, а не наступить на секунду раньше. */
function fmtTimer(ms) {
  const t = Math.max(0, Math.ceil(ms / 1000)), H = Math.floor(t / 3600), M = Math.floor(t % 3600 / 60), S = t % 60;
  return (H ? H + ":" + two(M) : M) + ":" + two(S);
}

/* ---------- кабинеты ---------- */
/** "42/21" это две группы в двух кабинетах. */
const splitRooms = r => (r || "").split("/").map(x => x.trim()).filter(Boolean);
const ROOM_SET = new Set();
CLASSES.forEach(c => classWeek(c).forEach(list => list.forEach(l => splitRooms(l.room).forEach(r => ROOM_SET.add(r)))));
const ROOMS = [...ROOM_SET].sort((a, b) => (parseInt(a) || 999) - (parseInt(b) || 999) || a.localeCompare(b, "ru"));
/** Кто где сидит в эту дату (с заменами). В каникулы показываем обычный день недели. */
function roomDay(date) {
  const off = dayOff(date), d = dow(date), use = {};
  CLASSES.forEach(c => (off ? classWeek(c)[d] : dayLessons(c, date)).forEach(l => splitRooms(l.room)
    .forEach(r => (use[r] = use[r] || []).push({num: l.num, cls: c, subject: l.subject, start: l.start, end: l.end}))));
  return use;
}
const lowFirst = t => /^[А-ЯЁ][а-яё]/.test(t) ? t[0].toLowerCase() + t.slice(1) : t;
/** Короткий ответ: кто сейчас в кабинете и когда он освободится. */
function roomNow(room, use, nowMin) {
  const u = (use[room] || []).slice().sort((a, b) => a.num - b.num), cur = u.filter(x => nowMin >= x.start && nowMin < x.end);
  const who = list => [...new Set(list.map(x => x.cls))].join(", ") + ", " + lowFirst(list[0].subject);
  if (cur.length) {
    let n = cur[0].num, end = cur[0].end;
    for (let x; (x = u.find(y => y.num === n + 1)); n++) end = x.end; // уроки подряд: занят до конца цепочки
    return {busy: true, text: "Сейчас " + who(cur) + ". Освободится в " + hm(end)};
  }
  const nx = u.find(x => x.start >= nowMin);
  return {busy: false, text: nx ? "Сейчас свободен. Дальше " + who(u.filter(x => x.num === nx.num)) + " в " + hm(nx.start) : "Свободен до конца дня"};
}
