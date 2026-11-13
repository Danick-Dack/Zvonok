/* Звонок · эталон для Android: что отвечают правила дня (site/src/js/rules.js) в выбранные моменты.
   Запускает tools/golden.py в безголовом Chrome с нужным часовым поясом; Android сверяется с ответом в юнит-тестах. */
function goldenRun(cfg) {
  const at0 = t => { const m = /^(\d+)-(\d+)-(\d+)T(\d+):(\d+):(\d+)$/.exec(t); return new Date(+m[1], m[2] - 1, +m[3], +m[4], +m[5], +m[6]); };
  const les = l => l ? [l.num, l.subject, l.room] : null;
  const cw = customWeek(cfg.custom);
  const source = c => c === "custom" ? {at: d => cw[dow(d)], real: false} : {at: d => dayLessons(c, d), real: true};
  function state(now, src) {
    const s = compute(now, src), v = vacation(now, src), o = {kind: s.kind};
    if (s.cur) o.cur = les(s.cur);
    if (s.next) o.next = les(s.next);
    if (s.target) o.target = s.target;
    if (s.start) o.start = s.start;
    if (s.kind === "BEFORE") o.soon = s.soon;
    if (s.count) { o.count = s.count; o.end = s.end; }
    if (s.idx != null) o.idx = s.idx;
    if (s.holiday) o.holiday = s.holiday[2];
    if (s.nDate) Object.assign(o, {nDays: s.nDays, nLabel: s.nLabel, nWhen: s.nWhen, nCount: s.nCount, nEnd: s.nEnd, nDate: iso(s.nDate)});
    if (v) o.vac = [v.n, v.today, v.name];
    const t = vacationText(v);
    if (t) o.vacText = t;
    return o;
  }
  const cases = [];
  for (const [classes, times] of cfg.plan) {
    for (const c of classes === "*" ? CLASSES : classes) {
      for (const t of times) {
        const now = at0(t);
        cases.push({tz: cfg.tz, cls: c, local: t, now: now.getTime(), expect: state(now, source(c))});
      }
    }
  }
  if (!cfg.tables) return {cases};

  const days = {}, cancel = {};
  for (const c of CLASSES) {
    days[c] = {};
    for (const t of cfg.dates) {
      const d = at0(t + "T12:00:00");
      days[c][t] = dayLessons(c, d).map(l => [l.num, l.start, l.end, l.subject, l.room, l.changed ? 1 : 0, l.short ? 1 : 0]);
      const gone = cancelled(c, d);
      if (gone.length) (cancel[c] = cancel[c] || {})[t] = gone;
    }
  }
  const rooms = {all: ROOMS, days: [], now: []};
  for (const t of cfg.roomDates) {
    const d = at0(t + "T12:00:00"), use = roomDay(d), bells = dayBells(d);
    const slots = bells.map((b, i) => b && Object.values(use).some(us => us.some(u => u.num === i + 1)) ? i + 1 : 0).filter(Boolean);
    rooms.days.push({date: t, slots: slots.map(n => ({n, free: ROOMS.filter(r => r !== "спортзал" && !(use[r] || []).some(u => u.num === n))}))});
    for (const [room, min] of cfg.roomNow) {
      const r = roomNow(room, use, min);
      rooms.now.push({date: t, room, min, busy: r.busy, text: r.text});
    }
  }
  const hues = {};
  for (const s of Object.values(DATA.subjects).concat(cfg.extraSubjects)) hues[s] = hueOf(s);
  // СОР и СОЧ, кружки и ШОР, праздники: то же считает Plus.java
  const plus = {end: examsEnd(), exams: {}, extras: [], soon: [], fest: {}};
  for (const c of cfg.plusClasses) {
    plus.exams[c] = {all: examList(c).length};
    for (const t of cfg.examDates) plus.exams[c][t] = examText(examsOn(c, at0(t + "T12:00:00")));
  }
  for (const t of cfg.extraTimes) {
    const now = at0(t), x = extraNow(now, cfg.extras);
    plus.extras.push({local: t, now: now.getTime(), day: extrasOn(now, cfg.extras).map(e => e.name + " " + e.start + "-" + e.end + " " + e.place).join("; "), text: x ? extraText(x, now) : ""});
  }
  for (const t of cfg.soonTimes) plus.soon.push({local: t, now: at0(t).getTime(), text: offSoon(at0(t), {real: true})});
  for (const t of cfg.festDates) { const f = festive(at0(t + "T12:00:00")); plus.fest[t] = f ? f[4] : ""; }
  return {cases, days, cancelled: cancel, rooms, hues, plus, extras: cfg.extras};
}
