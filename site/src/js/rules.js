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
    if (str(c.off)) day.off = c.off;
    if (Array.isArray(c.bells)) day.bells = c.bells.map(b => Array.isArray(b) && str(b[0]) && str(b[1]) ? [b[0], b[1]] : null);
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
