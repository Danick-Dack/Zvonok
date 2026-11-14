/* Звонок · вкладка «Кабинеты»: свободные на любом уроке и день любого кабинета */
/* ---------- «Кабинеты» ---------- */
let rDay = null, rSlot = null, rRoom = "";
function dateOfDay(d) { const n = new Date(); n.setHours(12, 0, 0, 0); n.setDate(n.getDate() + (d - dow(n) + 7) % 7); return n; }
function renderRooms() {
  renderTeach();
  const now = new Date(), today = dow(now), nowMin = now.getHours() * 60 + now.getMinutes();
  if (rDay == null) rDay = today < 5 ? today : 0;
  const sel = $("rday");
  if (!sel.options.length) for (let d = 0; d < 5; d++) sel.append(h("option", {value: d, text: d === today ? "Сегодня" : DAYN[d]}));
  sel.value = rDay;
  const isToday = rDay === today, date = isToday ? now : dateOfDay(rDay), off = dayOff(date), use = roomDay(date), bells = dayBells(date);
  const used = n => Object.values(use).some(us => us.some(u => u.num === n));
  const slots = bells.map((b, i) => b && used(i + 1) ? i + 1 : 0).filter(Boolean);
  const liveSlot = isToday && !off ? slots.find(n => nowMin >= bells[n - 1][0] && nowMin < bells[n - 1][1]) || 0 : 0;
  const nextSlot = isToday && !off ? slots.find(n => bells[n - 1][0] > nowMin) || 0 : 0;
  if (rSlot == null || (rSlot && !slots.includes(rSlot))) rSlot = liveSlot || nextSlot || (isToday ? 0 : slots[0] || 0);
  const strip = $("slots"); strip.textContent = "";
  slots.forEach(n => strip.append(h("button", {type: "button", "aria-pressed": String(n === rSlot), class: n === liveSlot ? "live" : "",
    "aria-label": n + " урок, " + hm(bells[n - 1][0]) + "–" + hm(bells[n - 1][1]) + (n === liveSlot ? ", идёт сейчас" : ""),
    onclick: () => { rSlot = n === rSlot ? 0 : n; renderRooms(); }}, String(n), h("small", {text: hm(bells[n - 1][0])}))));
  // свободные на выбранном уроке
  const free = $("free"); free.textContent = ""; free.classList.toggle("all", !rSlot);
  const chip = r => h("button", {type: "button", text: r, "aria-label": /^\d/.test(r) ? "Кабинет " + r : r, onclick: () => { rRoom = r; $("rq").value = r; renderRooms(); $("roomCard").scrollIntoView({block: "nearest", behavior: "smooth"}); }});
  if (rSlot) {
    const b = bells[rSlot - 1], list = ROOMS.filter(r => r !== "спортзал" && !(use[r] || []).some(u => u.num === rSlot));
    $("freeH").textContent = rSlot === liveSlot ? "Свободны сейчас" : "Свободны на " + rSlot + " уроке";
    $("freeNote").textContent = rSlot + " урок, " + hm(b[0]) + "–" + hm(b[1]) + " · " + (list.length ? word(list.length, "кабинет", "кабинета", "кабинетов") : "свободных нет")
      + (off ? ". " + off[2] + ": уроков нет, показан обычный день" : list.length ? ". Нажми на кабинет, чтобы увидеть его день" : "");
    list.forEach(r => free.append(chip(r)));
  } else {
    const over = isToday && slots.length && nowMin >= bells[slots[slots.length - 1] - 1][1];
    $("freeH").textContent = off ? off[2] : over ? "Уроки закончились" : "Все кабинеты";
    $("freeNote").textContent = off || over ? "Сейчас свободны все кабинеты. Нажми на любой, чтобы увидеть его день." : "Выбери урок выше, чтобы увидеть свободные.";
    ROOMS.forEach(r => free.append(chip(r)));
  }
  // во время урока сразу видно, что освободится к следующему
  const nf = $("nextFree"), after = rSlot && rSlot === liveSlot ? slots.find(n => n > rSlot) || 0 : 0;
  nf.hidden = !after;
  if (after) {
    const b2 = bells[after - 1], l2 = ROOMS.filter(r => r !== "спортзал" && !(use[r] || []).some(u => u.num === after));
    $("nextFreeH").textContent = "На следующем уроке";
    $("nextFreeNote").textContent = after + " урок, " + hm(b2[0]) + "–" + hm(b2[1]) + " · " + (l2.length ? word(l2.length, "кабинет", "кабинета", "кабинетов") : "свободных нет");
    const box2 = $("nextFreeList"); box2.textContent = ""; l2.forEach(r => box2.append(chip(r)));
  }
  // выбранный кабинет: кто там на каждом уроке
  const q = $("rq").value.trim().toLowerCase().replace(/^каб(инет)?\.?\s*/, "").replace(/^с\/з$|^спорт.*/, "спортзал");
  const find = x => ROOMS.find(r => r.toLowerCase() === x) || ROOMS.find(r => r.toLowerCase().startsWith(x));
  const room = q ? find(q) || q.split("/").map(x => x.trim()).filter(Boolean).map(find).find(Boolean) || null : null;
  const card = $("roomCard"); card.textContent = ""; card.hidden = !q;
  if (!q) return;
  if (!room) { card.append(h("h2", {class: "card-title", text: "Кабинет " + q}), h("p", {class: "note", text: "Такого кабинета нет в расписании. Проверь номер."})); return; }
  card.append(h("h2", {class: "card-title", text: /^\d/.test(room) ? "Кабинет " + room : "Спортзал"}));
  if (isToday && !off && slots.length && nowMin < bells[slots[slots.length - 1] - 1][1]) {
    const st = roomNow(room, use, nowMin);
    card.append(h("p", {class: "room-now" + (st.busy ? " busy" : ""), text: st.text}));
  } else card.append(h("p", {class: "note", text: (isToday ? "Сегодня" : DAYN[rDay]) + (off ? ", " + off[2].toLowerCase() + ", обычное расписание" : "")}));
  bells.forEach((b, i) => {
    if (!b || !slots.includes(i + 1)) return;
    const u = (use[room] || []).filter(x => x.num === i + 1);
    card.append(h("div", {class: "slot-row" + (i + 1 === liveSlot ? " now" : "")}, h("span", {class: "t", text: (i + 1) + " · " + hm(b[0]) + "–" + hm(b[1])}),
      u.length ? h("span", null, u.map((x, k) => [k ? ", " : "", h("b", {text: x.cls}), " " + lowFirst(x.subject)])) : h("span", {class: "free-txt", text: "свободен"})));
  });
}
$("rday").onchange = e => { rDay = +e.target.value; rSlot = null; renderRooms(); };
// печатают быстро: рисуем не чаще раза за кадр, а не на каждую букву
let rqFrame = 0;
$("rq").addEventListener("input", () => { cancelAnimationFrame(rqFrame); rqFrame = requestAnimationFrame(() => { rRoom = $("rq").value; renderRooms(); }); });

/* ---------- для учителей: неделя одного кабинета или все уроки одного предмета у всех классов ----------
   Данных об учителях в расписании нет, поэтому только то, что в нём точно есть: кабинет, класс, предмет, время. */
let teachKey = "";
function renderTeach() {
  const mode = $("tMode").value, sel = $("tWhat");
  const list = mode === "room" ? ROOMS : [...new Set(CLASSES.flatMap(c => classWeek(c).flat().map(l => l.subject)))].sort((a, b) => a.localeCompare(b, "ru"));
  if (sel.dataset.mode !== mode) {
    sel.replaceChildren(...list.map(x => h("option", {value: x, text: mode === "room" && /^\d/.test(x) ? "Кабинет " + x : x})));
    sel.dataset.mode = mode;
  }
  const what = sel.value, key = mode + "|" + what + "|" + iso(new Date()) + "|" + chVer;
  if (key === teachKey) return;
  teachKey = key;
  const days = [0, 1, 2, 3, 4].map(dateOfDay).sort((a, b) => a - b);
  $("tWeek").replaceChildren(...days.map(date => {
    const off = dayOff(date), bells = dayBells(date), rows = [];
    if (mode === "room") (roomDay(date)[what] || []).forEach(u => rows.push({num: u.num, start: u.start, end: u.end, who: u.cls, what: lowFirst(u.subject)}));
    else CLASSES.forEach(c => (off ? classWeek(c)[dow(date)] : dayLessons(c, date)).filter(l => l.subject === what)
      .forEach(l => rows.push({num: l.num, start: l.start, end: l.end, who: c, what: l.room ? "каб. " + l.room : ""})));
    rows.sort((a, b) => a.num - b.num || a.who.localeCompare(b.who, "ru"));
    return h("div", null, h("h3", {text: DAYN[dow(date)] + ", " + dm(date) + (off ? " · " + off[2].toLowerCase() + ", обычное расписание" : "")}),
      rows.length ? rows.map(r => h("div", {class: "slot-row"}, h("span", {class: "t", text: r.num + " · " + hm(r.start) + "–" + hm(r.end)}),
        h("span", null, h("b", {text: r.who}), r.what ? " " + r.what : "")))
        : h("p", {class: "note", text: mode === "room" ? "Весь день свободен" : "Уроков нет"}));
  }));
}
$("tMode").onchange = () => renderTeach();
$("tWhat").onchange = () => renderTeach();
