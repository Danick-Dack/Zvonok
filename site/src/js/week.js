/* Звонок · вкладка «Неделя»: дни листаются пальцем, на компьютере все сразу */
/* ---------- «Неделя» ---------- */
let weekOff = null, selDay = null;
const dayCount = () => WEEK[5] && WEEK[5].length ? 6 : 5;
function weekStart(off) { const d = new Date(); d.setHours(12, 0, 0, 0); d.setDate(d.getDate() - dow(d) + 7 * off); return d; }
function renderWeek() {
  const now = new Date(), n = dayCount(), today = dow(now);
  if (weekOff == null) weekOff = today >= n ? 1 : 0; // в выходные сразу следующая неделя
  const start = weekStart(weekOff), days = Array.from({length: n}, (_, d) => { const x = new Date(start); x.setDate(x.getDate() + d); return x; });
  if (selDay == null || selDay >= n) selDay = weekOff ? 0 : today < n ? (S && S.kind === "AFTER" && today + 1 < n ? today + 1 : today) : 0;
  $("weekMeta").textContent = (useCustom ? "Своё расписание" : cls) + " · " + days[0].getDate() + (days[0].getMonth() === days[n - 1].getMonth() ? "" : " " + MONTHS[days[0].getMonth()]) + "–" + dm(days[n - 1]);
  $$("#weekSeg button").forEach((b, i) => b.setAttribute("aria-pressed", String(i === weekOff)));
  const chips = $("days"), box = $("weekGrid");
  chips.textContent = ""; box.textContent = "";
  box.style.setProperty("--n", n);
  days.forEach((date, d) => {
    const isToday = iso(date) === iso(now);
    chips.append(h("button", {type: "button", role: "tab", id: "dtab" + d, "aria-selected": String(d === selDay), "aria-controls": "dcard" + d, tabindex: d === selDay ? "0" : "-1",
      class: isToday ? "today" : "", onclick: () => pickDay(d, true)}, DAYS[d], h("small", {text: date.getDate()})));
    const list = useCustom || dayOff(date) ? WEEK[d] : dayLessons(cls, date), note = noteEl(dayNote(date));
    box.append(h("section", {class: "day-card" + (isToday ? " today" : ""), id: "dcard" + d, role: "tabpanel", "aria-labelledby": "dtab" + d},
      h("div", {class: "day-head"}, h("h2", null, DAYN[d], isToday ? h("span", {class: "tag hot", text: "сегодня"}) : null), h("span", {text: dm(date) + " · " + dayRange(list)})),
      strip(list, isToday && !dayOff(date), S),
      note,
      timeline(list, isToday && !dayOff(date), S || compute(now, src), "Уроков нет")));
  });
  requestAnimationFrame(() => scrollToDay(selDay, false));
}
let weekAuto = 0; // когда неделю листал код, а не палец
function scrollToDay(d, smooth) {
  const box = $("weekGrid"), card = box.children[d];
  if (!card || box.scrollWidth <= box.clientWidth + 4) return;
  weekAuto = Date.now();
  box.scrollTo({left: card.offsetLeft - box.children[0].offsetLeft, behavior: smooth ? "smooth" : "auto"});
}
function pickDay(d, scroll) {
  selDay = d;
  $$("#days button").forEach((b, i) => { b.setAttribute("aria-selected", String(i === d)); b.tabIndex = i === d ? 0 : -1; });
  if (scroll) scrollToDay(d, true);
}
// листание дней пальцем: подсвечиваем день, на котором остановились
(() => {
  let t;
  $("weekGrid").addEventListener("scroll", () => {
    clearTimeout(t);
    t = setTimeout(() => {
      if (Date.now() - weekAuto < 700) return;
      const box = $("weekGrid"), first = box.children[0], second = box.children[1];
      if (!first || !second || box.scrollWidth <= box.clientWidth + 4) return;
      const d = Math.round(box.scrollLeft / (second.offsetLeft - first.offsetLeft));
      if (d !== selDay && d >= 0 && d < box.children.length) pickDay(d, false);
    }, 90);
  }, {passive: true});
})();
addEventListener("resize", () => { if (view === "week" && selDay != null) scrollToDay(selDay, false); }, {passive: true});
$("days").addEventListener("keydown", e => {
  const n = dayCount(), d = e.key === "ArrowRight" ? (selDay + 1) % n : e.key === "ArrowLeft" ? (selDay + n - 1) % n : null;
  if (d == null) return;
  e.preventDefault(); pickDay(d, true); $("dtab" + d).focus();
});
$$("#weekSeg button").forEach((b, i) => b.onclick = () => { weekOff = i; selDay = null; renderWeek(); });
$("printBtn").onclick = () => window.print();
$("report").onclick = () => {
  const d = new Date(weekStart(weekOff || 0)); d.setDate(d.getDate() + (selDay || 0));
  $("report").href = "feedback.html?about=schedule&class=" + encodeURIComponent(useCustom ? "" : cls) + "&day=" + (selDay || 0);
};
