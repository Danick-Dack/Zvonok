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

/* ---------- СОР и СОЧ: DATA.sor {класс: {предмет: [даты]}}, DATA.soch {класс: {предмет: дата}} ---------- */
/** Все СОР и СОЧ класса по порядку: [{date, type: "СОР" | "СОЧ", subject}]. В один день сначала СОЧ, внутри по названию предмета. */
function examList(c) {
  const out = [], name = k => DATA.subjects[k] || clean(k), r = (DATA.sor || {})[c] || {}, q = (DATA.soch || {})[c] || {};
  for (const k in r) (Array.isArray(r[k]) ? r[k] : []).forEach(date => out.push({date, type: "СОР", subject: name(k)}));
  for (const k in q) if (typeof q[k] === "string") out.push({date: q[k], type: "СОЧ", subject: name(k)});
  const key = x => x.date + (x.type === "СОЧ" ? "0" : "1") + x.subject;
  return out.sort((a, b) => key(a) < key(b) ? -1 : key(a) > key(b) ? 1 : 0);
}
/** СОР и СОЧ класса на дату. */
function examsOn(c, d) { const t = iso(d); return examList(c).filter(x => x.date === t); }
/** «СОЧ: алгебра, физика. СОР: химия» */
function examText(list) {
  return ["СОЧ", "СОР"].map(t => { const s = list.filter(x => x.type === t).map(x => x.subject.toLowerCase()); return s.length ? t + ": " + s.join(", ") : ""; }).filter(Boolean).join(". ");
}
/** Последний день, когда в лицее у кого-то есть СОР или СОЧ; "" если графика нет. После него вкладка «СОР/СОЧ» прячется. */
function examsEnd() {
  let last = "";
  for (const c in DATA.classes) examList(c).forEach(x => { if (x.date > last) last = x.date; });
  return last;
}

/* ---------- кружки и ШОР: set = {shor: [id групп из DATA.shor], clubs: [{name, place, times: [[день 0–6, "с", "по"]]}]} ---------- */
/** Занятия после уроков на дату, по времени. В каникулы и праздники их нет. */
function extrasOn(d, set) {
  if (!set || holiday(d)) return [];
  const w = dow(d), out = [];
  const add = (name, place, times) => (Array.isArray(times) ? times : []).forEach(t => {
    const s = toMin(t && t[1]), e = toMin(t && t[2]);
    if (t && t[0] === w && s >= 0 && e > s && name) out.push({name, place: place || "", start: s, end: e});
  });
  (DATA.shor || []).forEach(g => { if ((set.shor || []).includes(g.id)) add("ШОР · " + g.subject, g.place, g.times); });
  (set.clubs || []).forEach(c => { if (c) add(clean(c.name), clean(c.place), c.times); });
  return out.sort((a, b) => a.start - b.start);
}
/** Ближайшее занятие сегодня: on = уже идёт (target это конец), иначе впереди (target это начало). null: на сегодня всё. */
function extraNow(now, set) {
  const m = now.getHours() * 60 + now.getMinutes(), x = extrasOn(now, set).find(e => m < e.end);
  return x ? Object.assign({on: m >= x.start, target: at(now, m >= x.start ? x.end : x.start)}, x) : null;
}
/** «ШОР · Физика: до конца 1 ч 5 мин» или «…: до начала 20 мин». */
function extraText(x, now) {
  const left = Math.max(1, Math.ceil((x.target - now) / 60000)), H = Math.floor(left / 60), M = left % 60;
  return x.name + ": до " + (x.on ? "конца " : "начала ") + ((H ? H + " ч " : "") + (M || !H ? M + " мин" : "")).trim();
}

/* ---------- праздники ---------- */
/** Ближайший учебный день (завтра, а в пятницу понедельник) выпал на праздник: «Завтра День Независимости, в школу не идём».
 *  Про каникулы отдельно говорит vacationText. */
function offSoon(now, src) {
  if (!src.real || holiday(now)) return "";
  const d = new Date(now), w = dow(now);
  if (w > 4) return "";
  d.setDate(d.getDate() + (w === 4 ? 3 : 1));
  const x = holiday(d);
  return x && !/каникул/i.test(x[2]) ? (w === 4 ? "В понедельник " : "Завтра ") + x[2] + ", в школу не идём" : "";
}
/** Праздничная тема на дату из DATA.themes: [с "ММ-ДД", по "ММ-ДД", значок, цвет RRGGBB, название, фраза] или null.
 *  Каждый год в те же дни; отрезок может переходить через Новый год (12-20 … 01-08). */
function festive(d) {
  const t = iso(d).slice(5);
  return (DATA.themes || []).find(x => Array.isArray(x) && (x[0] <= x[1] ? t >= x[0] && t <= x[1] : t >= x[0] || t <= x[1])) || null;
}
