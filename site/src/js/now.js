/* Звонок · вкладка «Сейчас»: таймер, лента дня, приглашение поставить на телефон, «что нового» */
/* ---------- «Сейчас» ---------- */
let S = null, M = {timer: false}, key = "", tickT;
const QUIPS = {
  after: ["Домашка сама себя не сделает.", "Рюкзак можно бросить в угол. До утра.", "Самое время забыть, где лежит дневник."],
  off: ["Даже звонок сегодня отдыхает.", "Будильник можно не ставить.", "Понедельник подождёт, а выходной ждать не будет."],
  vac: ["Звонок в отпуске. ЕНТ подождёт. Наверное.", "Каникулы: можно спать до обеда и не стыдиться.", "Кабинеты пустые, учителя тоже отдыхают."]
};
const C = 2 * Math.PI * 54; // длина кольца
function model(now, s) {
  const n = s.next, m = {timer: false, live: false, prog: -1, tag: "", room: "", roomNow: false, sub: "", nextH: "", nextS: "", nextR: "", foot: "", big: "", cap: ""};
  if (s.kind === "LESSON") {
    const c = s.cur, rest = s.count - s.idx - 1;
    Object.assign(m, {timer: true, live: true, start: s.start, target: s.target, cap: "до звонка", status: n ? "Урок " + (s.idx + 1) + " из " + s.count : "Последний урок",
      tag: mark(c), title: c.subject, room: c.room, roomNow: true,
      sub: "Звонок в " + hm(c.end) + (n ? ", потом " + (n.start - c.end > 25 ? "окно " : "перемена ") + (n.start - c.end) + " мин" : ", потом домой"),
      foot: rest ? "Ещё " + plural(rest) + ", до " + s.end : ""});
    if (n) Object.assign(m, {nextH: "Дальше в " + hm(n.start) + (mark(n) ? " · " + mark(n) : ""), nextS: n.subject, nextR: n.room});
  } else if (s.kind === "BREAK") {
    const len = n.start - s.cur.end, a = s.list[s.idx + 2];
    Object.assign(m, {timer: true, live: true, start: s.start, target: s.target, cap: "до урока", status: (len > 25 ? "Окно" : "Перемена") + " · " + len + " мин",
      tag: mark(n), title: n.subject, room: n.room, roomNow: true, sub: "Урок " + (s.idx + 2) + " в " + hm(n.start),
      foot: "Осталось " + plural(s.count - s.idx - 1) + ", до " + s.end});
    if (a) Object.assign(m, {nextH: "Потом в " + hm(a.start), nextS: a.subject, nextR: a.room});
  } else if (s.kind === "BEFORE") {
    const a = s.list[1];
    Object.assign(m, {title: n.subject, room: n.room, tag: mark(n), foot: "Сегодня " + plural(s.count) + ", " + hm(n.start) + "–" + s.end});
    if (s.soon) Object.assign(m, {timer: true, live: true, start: s.start, target: s.target, cap: "до первого урока", status: "Скоро уроки", roomNow: true, sub: "Начало в " + hm(n.start)});
    else Object.assign(m, {big: hm(n.start), cap: "первый урок", status: "Сегодня " + plural(s.count), sub: "Уроки до " + s.end});
    if (a) Object.assign(m, {nextH: "Потом в " + hm(a.start), nextS: a.subject, nextR: a.room});
  } else {
    const hol = s.holiday, vac = hol && /каникул/i.test(hol[2]);
    m.status = s.kind === "AFTER" ? "Уроки закончились" : hol ? hol[2] : "Выходной";
    m.title = s.kind === "AFTER" ? "На сегодня всё" : vac ? "Каникулы" : "Отдыхай";
    if (n) {
      Object.assign(m, {big: hm(n.start), cap: s.nWhen, sub: s.nLabel + ": " + plural(s.nCount) + ", " + hm(n.start) + "–" + s.nEnd,
        nextH: "Первый урок " + s.nWhen, nextS: n.subject, nextR: n.room});
      if (hol && s.nDays > 1) Object.assign(m, {big: String(s.nDays), cap: word(s.nDays, "день", "дня", "дней").replace(/^\d+ /, "") + " до уроков", sub: "Уроки с " + s.nWhen + ", " + hm(n.start)});
    } else Object.assign(m, {big: "–", cap: "уроков не найдено", sub: useCustom ? "В своём расписании пока пусто." : ""});
  }
  m.kind = s.kind;
  m.vac = vacationText(vacation(now, src));
  // когда уроков нет, пусть карточка не будет совсем сухой; фраза своя на каждый день и не прыгает
  if (!m.timer && (s.kind === "AFTER" || s.kind === "OFF") && !m.foot) {
    const q = s.holiday && /каникул/i.test(s.holiday[2]) ? QUIPS.vac : s.kind === "AFTER" ? QUIPS.after : QUIPS.off;
    m.foot = q[now.getDate() % q.length];
  }
  if (src.real && DATA.term_end && iso(now) > DATA.term_end && !s.holiday) m.foot = "Четверть закончилась. Новое расписание появится здесь, когда его утвердят.";
  if (m.timer) m.prog = Math.max(0, Math.min(1, (now - m.start) / (m.target - m.start)));
  return m;
}
function renderNow(now) {
  $("hello").hidden = !pick; $("nowMain").hidden = pick;
  if (pick) { renderGrades($("helloGrades")); M = {timer: false}; return; }
  const s = S || compute(now, src), m = M = model(now, s);
  const hue = m.timer || m.kind === "BEFORE" ? hueOf(m.title) : "none";
  $("live").className = "live hue-" + hue + (m.timer ? " tinted" : "");
  $("lNext").className = "next hue-" + hueOf(m.nextS);
  $("lStatus").textContent = m.status;
  $("lPulse").hidden = !m.live; $("lPulse").classList.toggle("on", m.live);
  $("lTag").textContent = m.tag; $("lTag").hidden = !m.tag; $("lTag").className = "tag warn";
  $("lDial").classList.toggle("idle", !m.timer);
  // звонки идут по времени Алматы (UTC+5); если на телефоне другой пояс, честно предупреждаем
  $("tzNote").hidden = [-300, -360].includes(now.getTimezoneOffset()); // +6: Алматы на телефонах со старой базой поясов
  swing();
  $("lCap").textContent = m.cap;
  $("lTitle").textContent = m.title;
  const r = $("lRoom"); r.replaceWith(m.room ? Object.assign(roomChip(m.room, m.roomNow, true), {id: "lRoom"}) : h("span", {id: "lRoom", hidden: true}));
  $("lSub").textContent = m.sub; $("lSub").hidden = !m.sub;
  $("lNext").hidden = !m.nextS;
  $("lNextH").textContent = m.nextH; $("lNextS").textContent = m.nextS;
  const nr = $("lNextR"); nr.replaceWith(m.nextR ? Object.assign(roomChip(m.nextR), {id: "lNextR"}) : h("span", {id: "lNextR", hidden: true}));
  $("liveStrip").replaceWith(Object.assign(s.list && s.kind !== "AFTER" ? strip(s.list, true, s) : h("div", {class: "strip", "aria-hidden": "true"}), {id: "liveStrip"}));
  $("lFoot").replaceChildren(...[m.foot].filter(Boolean));
  if (!m.timer) { $("lTimer").textContent = m.big; $("lTimer").classList.toggle("long", m.big.length > 5); $("lArc").style.strokeDashoffset = C.toFixed(1); }
  // лента: сегодня, а после уроков и в выходные ближайший учебный день
  const today = s.list && s.kind !== "AFTER";
  const date = today ? now : s.nDate, list = today ? s.list : s.nList || [];
  $("todayH").textContent = today ? "Сегодня" : s.nDays === 1 ? "Завтра" : date ? DAYN[dow(date)] : "Дальше";
  $("todayMeta").textContent = date ? (today ? DAYN[dow(now)].toLowerCase() + ", " : "") + dm(date) + " · " + dayRange(list) : "";
  $("todayVac").textContent = m.vac || ""; $("todayVac").hidden = !m.vac;
  const note = date ? dayNote(date) : null;
  $("todayNote").textContent = note ? note.text : ""; $("todayNote").hidden = !note; $("todayNote").className = "day-note" + (note ? " " + note.kind : "");
  $("todayList").replaceWith(Object.assign(timeline(list, today, s, "Ближайших уроков нет"), {id: "todayList"}));
  renderPromo();
}
// Таймер в заголовке вкладки виден только в браузере на компьютере. На телефоне заголовка не видно,
// а каждая его смена будит браузер (у сайта на экране «Домой» ещё и список приложений), так что там не трогаем.
const LIVE_TITLE = !STANDALONE && !!window.matchMedia && matchMedia("(hover: hover) and (pointer: fine)").matches;
const CALM = !!window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
const setTitle = t => { if (document.title !== t) document.title = t; };
const baseTitle = () => pick ? "Звонок · расписание лицея №165" : "Звонок · " + title();
let swingT = 0;
/** Кольцо доезжает до нового места плавно, когда сменился урок; дальше шагает раз в секунду без анимации. */
function swing() {
  const a = $("lArc");
  a.classList.add("swing"); clearTimeout(swingT);
  swingT = setTimeout(() => a.classList.remove("swing"), 700);
}
/** Каждую секунду: только таймер, кольцо и полоска текущего урока. */
function paint(now) {
  if (M.timer) {
    const t = fmtTimer(M.target - now), p = Math.max(0, Math.min(1, (now - M.start) / (M.target - M.start)));
    $("lTimer").textContent = t; $("lTimer").classList.toggle("long", t.length > 5);
    $("lArc").style.strokeDashoffset = (C * (1 - Math.max(.004, p))).toFixed(2);
    setTitle(LIVE_TITLE ? t + " · Звонок · " + title() : baseTitle());
  } else setTitle(baseTitle());
  // точка «идёт» мигает в такт секундам: в том же кадре, что и цифры, без отдельной вечной анимации
  if (M.live && !CALM) $("lPulse").classList.toggle("dim", now.getSeconds() % 2 === 1);
  if (S && S.kind === "LESSON") {
    // полоска урока шагом 0,5 %: на глаз так же плавно, а стили пересчитываются раз в ~13 секунд, а не каждую
    const p = (Math.round(Math.max(0, Math.min(1, (now - S.start) / (S.target - S.start))) * 200) / 200).toFixed(3);
    $$(".tl .cur").forEach(li => { if (li.style.getPropertyValue("--p") !== p) li.style.setProperty("--p", p); });
  }
}
const stateKey = (s, now) => [pick, useCustom, cls, s.kind, s.soon, s.cur && s.cur.num, s.next && s.next.num, now.toDateString(), chVer].join("|");
function tick() {
  clearTimeout(tickT);
  if (document.hidden) { background(); return; }
  const now = new Date();
  S = compute(now, src);
  const k = stateKey(S, now);
  if (k !== key) {
    const first = !key; key = k;
    VIEWS.forEach(v => { dirty[v] = true; });
    dirty[view] = false; RENDER[view]();
    if (view !== "now") { dirty.now = false; renderNow(now); }
    if (!first) announce();
  }
  paint(now);
  tickT = setTimeout(tick, nextTick());
}
/** Через сколько будить: к ближайшему изменению, которое видно глазом. Секунды есть только у таймера и точки
 *  «идёт» на «Сейчас» (и у таймера в заголовке вкладки на компьютере). В остальное время хватает шага полоски
 *  урока (0,5 %, раз в 10–15 с) и начала минуты: звонки, «скоро уроки» и смена дня всегда приходятся на неё. */
function nextTick() {
  const ms = Date.now();
  const seconds = view === "now" ? M.timer || (M.live && !CALM) : LIVE_TITLE && M.timer;
  if (seconds) return 1000 - ms % 1000 + 15;
  let wait = 60000 - ms % 60000 + 15;
  if (S && S.kind === "LESSON" && +S.target > +S.start) {
    const a = +S.start, step = (+S.target - a) / 200, f = (ms - a) / step;
    wait = Math.min(wait, Math.max(50, a + (Math.floor(f + 0.5) + 0.5) * step - ms + 15));
  }
  return wait;
}
/** Вкладку свернули. На компьютере в заголовке остаются минуты до звонка (раз в минуту), на телефоне ничего не тикает. */
function background() {
  const now = new Date(), s = compute(now, src);
  const timed = LIVE_TITLE && !pick && s.target && (s.kind === "LESSON" || s.kind === "BREAK" || (s.kind === "BEFORE" && s.soon));
  if (!timed) { setTitle(baseTitle()); return; }
  const min = Math.max(1, Math.ceil((s.target - now) / 60000));
  setTitle((min < 60 ? min + " мин" : Math.floor(min / 60) + " ч " + min % 60 + " мин") + " · Звонок · " + title());
  tickT = setTimeout(tick, 60000 - Date.now() % 60000 + 15);
}
document.addEventListener("visibilitychange", () => { tick(); if (!document.hidden) loadChanges(); });
/** Для экранного диктора: только смена урока, а не каждая секунда. */
function announce() {
  if (pick || !M.title) return;
  $("announce").textContent = [M.status, M.title, M.room ? "кабинет " + M.room : ""].filter(Boolean).join(". ");
}

/* приглашение поставить на телефон */
function renderPromo() {
  const box = $("promo");
  const kind = STANDALONE || EMBED || store.get("promoOff", false) ? null : IOS ? "ios" : ANDROID ? "android" : null;
  box.hidden = !kind || pick;
  if (box.hidden) return;
  const close = h("button", {class: "icon-btn", type: "button", "aria-label": "Скрыть", onclick: () => { store.set("promoOff", true); box.hidden = true; }}, icon("close", "sm"));
  box.replaceChildren(kind === "ios"
    ? h("div", null, h("b", {text: "Звонок на экране «Домой»"}), h("p", {text: "Добавь сайт как приложение: откроется сразу на твоём расписании, даже в подвале без сети."}),
      h("a", {class: "btn btn-primary btn-sm", href: "#install", text: "Как добавить"}))
    : h("div", null, h("b", {text: "Приложение для Android"}), h("p", {text: "Таймер до звонка прямо на экране блокировки. Весит 130 КБ и не жрёт батарею."}),
      h("div", {class: "row"}, h("a", {class: "btn btn-primary btn-sm", href: APK, rel: "noopener", text: "Скачать"}), h("a", {class: "btn btn-quiet btn-sm", href: "#install", text: "Как установить"}))),
    close);
}

/* «Что нового» после обновления: показываем один раз тем, кто уже пользовался сайтом */
const NEWS = /*@NEWS@*/null;
(function initNews() {
  if (!NEWS) return;
  const key = NEWS.date + "|" + NEWS.title, seen = store.get("seenLog", null);
  if (seen === key) return;
  if (pick) { store.set("seenLog", key); return; } // новичку незачем
  $("newsText").textContent = NEWS.title; $("news").hidden = false;
  store.set("seenLog", key); // один раз: в следующий раз уже не покажем
  $("newsClose").onclick = () => { store.set("seenLog", key); $("news").hidden = true; };
  $("newsLink").onclick = () => store.set("seenLog", key);
})();
