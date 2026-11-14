/* Звонок · гимн, игры на перемену, звуки, серия дней, иконка, вибрация перед звонком, подписи к урокам.
   Без интернета работает всё, кроме подписей: их видит весь класс, поэтому они живут в сети. */

function openDlg(d) { if (typeof d.showModal === "function") d.showModal(); else d.setAttribute("open", ""); }
function closeDlg(d) { if (typeof d.close === "function") d.close(); else d.removeAttribute("open"); }
["words", "noteSheet"].forEach(id => $(id).addEventListener("click", e => { if (e.target === $(id) || e.target.closest("[data-close]")) closeDlg($(id)); }));

/* ---------- гимн лицея ---------- */
const ANTHEM = /*@ANTHEM@*/null || [];
$("anthemText").replaceChildren(...ANTHEM.map(lines => h("p", null, lines.map((line, i) => [i ? h("br") : null, line]))));

/* ---------- «Слова»: угадай слово из 5 букв за 6 попыток. Слово дня одно на всех, дальше случайные ---------- */
const WORDS = /*@WORDS@*/null || [];
const KEYS = ["йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю"];
const MARKS = ["нет", "есть, но не тут", "на месте"];
let wd = null;
const dayNum = () => { const d = new Date(); return Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 864e5); };
/** Оценка попытки по буквам: 2 на месте, 1 есть в слове, 0 нет. Повторы букв считаются честно. */
function wordsScore(word, guess) {
  const res = [0, 0, 0, 0, 0], left = {};
  for (let i = 0; i < 5; i++) if (guess[i] === word[i]) res[i] = 2; else left[word[i]] = (left[word[i]] || 0) + 1;
  for (let i = 0; i < 5; i++) if (!res[i] && left[guess[i]]) { res[i] = 1; left[guess[i]]--; }
  return res;
}
function wordsStart(daily) {
  const day = dayNum(), saved = store.get("words", null);
  const rows = daily && saved && saved.day === day && Array.isArray(saved.rows) ? saved.rows.filter(w => /^[а-я]{5}$/.test(w)).slice(0, 6) : [];
  wd = {word: WORDS[daily ? day % WORDS.length : Math.floor(Math.random() * WORDS.length)], rows, cur: "", daily};
  wordsDraw();
}
const wordsOver = () => wd.rows.includes(wd.word) || wd.rows.length >= 6;
/** anim: "flip" переворачивает только что введённую строку, "shake" трясёт недописанную. */
function wordsDraw(anim) {
  const won = wd.rows.includes(wd.word), over = wordsOver(), best = {};
  $("wordsH").textContent = wd.daily ? "Слово дня" : "Слова";
  $("wordsGrid").replaceChildren(...Array.from({length: 6}, (_, r) => {
    const g = wd.rows[r], sc = g && wordsScore(wd.word, g), txt = g || (r === wd.rows.length && !over ? wd.cur : "");
    if (g) for (let i = 0; i < 5; i++) best[g[i]] = Math.max(best[g[i]] || 0, sc[i] + 1);
    return h("div", {class: "w-row" + (anim === "flip" && r === wd.rows.length - 1 ? " flip" : anim === "shake" && r === wd.rows.length ? " shake" : ""),
      role: "img", "aria-label": g ? g + ": " + sc.map((x, i) => g[i] + " " + MARKS[x]).join(", ") : txt || "пусто"},
      Array.from({length: 5}, (_, i) => h("span", {class: "w-cell" + (g ? " s" + sc[i] : txt[i] ? " on" : ""), style: "--i:" + i, text: txt[i] || ""})));
  }));
  $("wordsMsg").textContent = won ? "Угадал с " + wd.rows.length + "-й попытки! Всего угадано слов: " + store.get("wordsWon", 0)
    : over ? "Не вышло. Это было слово «" + wd.word + "»" : "Попытка " + (wd.rows.length + 1) + " из 6. Зелёная буква на месте, жёлтая есть в слове";
  $("wordsMore").hidden = !over;
  const key = (k, text, label) => h("button", {type: "button", class: "s" + ((best[k] || 0) - 1) + (text ? " wide" : ""), "data-k": k, "aria-label": label || k, text: text || k});
  $("wordsKeys").replaceChildren(...KEYS.map((row, r) => h("div", {class: "w-keys"},
    r === 2 ? key("enter", "Ввод", "Ввод") : null, [...row].map(ch => key(ch)), r === 2 ? key("back", "⌫", "Стереть") : null)));
}
function wordsKey(k) {
  if (wordsOver()) return;
  if (k === "enter") {
    if (wd.cur.length < 5) return wordsDraw("shake");
    wd.rows.push(wd.cur); wd.cur = "";
    if (wd.daily) store.set("words", {day: dayNum(), rows: wd.rows});
    if (wd.rows.includes(wd.word)) store.set("wordsWon", store.get("wordsWon", 0) + 1);
    return wordsDraw("flip");
  }
  wd.cur = k === "back" ? wd.cur.slice(0, -1) : (wd.cur + k).slice(0, 5);
  wordsDraw();
}
$("wordsKeys").addEventListener("click", e => { const b = e.target.closest("[data-k]"); if (b) wordsKey(b.dataset.k); });
$("words").addEventListener("keydown", e => {
  if (e.ctrlKey || e.metaKey || e.altKey || e.target.closest("[data-close],#wordsMore")) return;
  const k = e.key === "Enter" ? "enter" : e.key === "Backspace" ? "back" : /^[а-яё]$/i.test(e.key) ? e.key.toLowerCase().replace("ё", "е") : "";
  if (k) { e.preventDefault(); wordsKey(k); }
});
$("wordsMore").onclick = () => wordsStart(false);
$("playWords").onclick = () => { wordsStart(true); openDlg($("words")); };
$("playSnake").onclick = () => snake();

/* ---------- звуки: тиканье на уроках и переменах, звонок на звонке. Звук рисует сам браузер, файлов нет ---------- */
let AC = null;
const soundOn = () => !!store.get("sound", false);
/** Браузер даёт звук только после касания страницы: до него молчим. */
function audio() {
  const A = window.AudioContext || window.webkitAudioContext;
  if (!AC && A) AC = new A();
  if (AC && AC.state === "suspended") AC.resume().catch(() => {});
  return AC && AC.state === "running" ? AC : null;
}
function beep(ac, type, freq, at, len, vol) {
  const o = ac.createOscillator(), g = ac.createGain(), t = ac.currentTime + at;
  o.type = type; o.frequency.value = freq;
  g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(.0001, t + len);
  o.connect(g); g.connect(ac.destination); o.start(t); o.stop(t + len + .02);
}
function tickSound(sec) { const ac = soundOn() && audio(); if (ac) beep(ac, "square", sec % 2 ? 1800 : 2200, 0, .03, .04); }
/** Школьный звонок: молоточек часто бьёт по чашке две секунды. Значок звука при этом качается и без звука. */
function ring() {
  const live = $("live"), ac = soundOn() && audio();
  live.classList.add("ringing"); setTimeout(() => live.classList.remove("ringing"), 2300);
  if (ac) for (let i = 0; i < 44; i++) { beep(ac, "square", 2093, i * .05, .045, .05); beep(ac, "triangle", 3136, i * .05, .045, .04); }
}
function renderSound() {
  const on = soundOn(), b = $("sndBtn");
  b.replaceChildren(on ? icon("bell") : icon("belloff"));
  b.setAttribute("aria-pressed", String(on)); b.setAttribute("aria-label", "Звуки: тиканье и звонок"); b.title = on ? "Звуки включены" : "Звуки выключены";
  $("soundToggle").checked = on;
}
function setSound(on) {
  store.set("sound", on); renderSound(); tickSound(0);
  toast(on ? "Звуки включены: часы тикают, на звонке звенит звонок" : "Звуки выключены");
  tick();
}
$("sndBtn").onclick = () => setSound(!soundOn());
$("soundToggle").onchange = e => setSound(e.target.checked);
addEventListener("pointerdown", () => { if (soundOn()) audio(); }, {passive: true});
renderSound();

/* ---------- вибрация за 3 минуты до звонка на урок и на перемену (там, где браузер её умеет) ---------- */
let buzzFor = 0;
function preBuzz(now) {
  const left = M.timer ? M.target - now : 0;
  if (left <= 170000 || left > 180000 || buzzFor === +M.target || !store.get("buzz", false) || !navigator.vibrate) return;
  buzzFor = +M.target;
  navigator.vibrate([300, 150, 300]); toast("Через 3 минуты звонок");
}
$("buzzRow").hidden = !navigator.vibrate || EMBED; // в приложении iPhone своя вибрация: уведомления Scriptable (plus.js)
$("buzzToggle").checked = !!store.get("buzz", false);
$("buzzToggle").onchange = e => { store.set("buzz", e.target.checked); if (e.target.checked) navigator.vibrate([300, 150, 300]); };

/* ---------- серия дней: растёт, когда заходишь посмотреть расписание. Выходные и каникулы её не рвут ---------- */
function streak() {
  const st = store.get("streak", null) || {}, today = iso(new Date());
  if (pick || st.day === today) return st;
  let kept = /^\d{4}-\d\d-\d\d$/.test(st.day) && st.day < today && st.n > 0;
  for (let d = new Date(st.day + "T12:00:00"), i = 0; kept; i++) {
    d.setDate(d.getDate() + 1);
    if (iso(d) >= today) break;
    if (i > 120 || src.at(d).length) kept = false; // пропущен учебный день
  }
  const n = kept ? st.n + 1 : 1, next = {day: today, n, best: Math.max(st.best || 0, n)};
  store.set("streak", next);
  return Object.assign({grew: true}, next);
}
const daysWord = n => word(n, "день", "дня", "дней");
function renderStreak() {
  const st = streak(), b = $("streak");
  b.hidden = pick || !st.n;
  if (b.hidden) return;
  $("streakN").textContent = st.n;
  b.setAttribute("aria-label", "Серия: " + daysWord(st.n) + " подряд");
  if (!st.grew) return;
  b.classList.add("grew"); setTimeout(() => b.classList.remove("grew"), 1400);
  if (st.n > 1) toast("Серия: " + daysWord(st.n) + " подряд");
}
$("streak").onclick = () => { const st = store.get("streak", {}); toast("Серия " + daysWord(st.n) + ", рекорд " + st.best + ". Выходные и каникулы её не рвут"); };

/* ---------- иконка для экрана «Домой»: цвет фона, знака и кольца. Свои картинки сборка берёт из папки icons/ ---------- */
const ICON_SET = [["dark", "Тёмная", "#0B0B0C", "#FFFFFF", "#2E2E32"], ["light", "Светлая", "#F4F4F1", "#141414", "#D6D6D0"], ["blue", "Синяя", "#0062DA", "#FFFFFF", "#3F88E8"],
  ["green", "Зелёная", "#1F7A36", "#FFFFFF", "#4E9A62"], ["pink", "Розовая", "#C70F45", "#FFFFFF", "#DA4B74"], ["sun", "Солнечная", "#FFCC00", "#141414", "#E0B000"]].concat(/*@ICONS_EXTRA@*/[]);
const iconPick = () => ICON_SET.find(v => v[0] === store.get("icon", "dark")) || ICON_SET[0];
/** Иконка как PNG: часы «Звонка», а с text вместо стрелок номер класса. Свою картинку отдаём как есть. */
function iconPng(v, size, text) {
  if (v[5]) return v[5];
  const c = h("canvas", {width: size, height: size}), g = c.getContext("2d"), m = size / 2, k = size / 90;
  g.fillStyle = v[2]; g.fillRect(0, 0, size, size);
  g.lineWidth = 5.5 * k; g.lineCap = g.lineJoin = "round";
  g.strokeStyle = v[4]; g.beginPath(); g.arc(m, m, 25 * k, 0, Math.PI * 2); g.stroke();
  g.strokeStyle = g.fillStyle = v[3]; g.beginPath(); g.arc(m, m, 25 * k, -Math.PI / 2, Math.PI); g.stroke();
  if (text) {
    g.textAlign = "center"; g.textBaseline = "middle";
    g.font = "700 " + Math.round(size * (text.length > 2 ? .2 : .24)) + "px -apple-system,'Segoe UI',Roboto,sans-serif";
    g.fillText(text, m, m + size * .01);
  } else {
    g.lineWidth = 5 * k; g.beginPath(); g.moveTo(m, m - 13 * k); g.lineTo(m, m); g.lineTo(m + 7.5 * k, m + 4.3 * k); g.stroke();
    g.beginPath(); g.arc(m, m, 3.4 * k, 0, Math.PI * 2); g.fill();
  }
  return c.toDataURL("image/png");
}
function renderIcons() {
  const cur = iconPick();
  $("iconRow").replaceChildren(...ICON_SET.map(v => h("button", {type: "button", "aria-pressed": String(v === cur), "aria-label": "Иконка: " + v[1],
    onclick: () => { store.set("icon", v[0]); homeIcon(); renderIcons(); toast("Готово. Добавь сайт на экран «Домой» заново"); }},
    h("img", {src: iconPng(v, 96), alt: "", width: 48, height: 48}), v[1])));
}

/* ---------- подписи к урокам: что будет на уроке, пишут сами ученики, видит весь класс. Только с интернетом ---------- */
const NOTES_URL = DATA.notes || "";
let NOTES = store.get("notes", null) || {}, notesAt = 0, noteFor = null; // {cls, days: {"2026-11-16": {n3: {t: "текст"}}}}
const RUDE = /(^|[^а-яё])((на|по|ни|о|а)?ху[йеёяию]|бля|[её]б|сук[аиу](?![а-яё])|муд[аи]|чмо(?![а-яё])|лох(?![а-яё]))|пизд|говн|жоп|пид[оа]?р|шлюх|г[ао]ндон|залуп|дроч|(за|на|вы|по|про|до|съ|отъ|разъ|у|долбо)[её]б/;
const notesOn = () => !!NOTES_URL && !pick && !useCustom;
const noteClean = t => String(t == null ? "" : t).replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 80);
function noteOf(date, num) {
  const x = notesOn() && NOTES.cls === cls && ((NOTES.days || {})[iso(date)] || {})["n" + num];
  return x ? noteClean(x.t) : "";
}
/** Кнопка подписи в строке урока: текст подписи или карандаш. У прошедших уроков только текст. */
function noteEl(date, l, past) {
  const t = date && noteOf(date, l.num);
  if (!date || !notesOn() || (past && !t) || iso(date) < iso(new Date())) return t ? h("small", {class: "lnote", text: t}) : null;
  return h("button", {class: "lnote" + (t ? "" : " add"), type: "button", "aria-label": t ? "Подпись: " + t + ". Изменить" : "Подписать урок: " + l.subject,
    onclick: () => editNote(date, l)}, t || icon("edit", "sm"));
}
function loadNotes() {
  if (!notesOn() || !ONLINE_SITE || (NOTES.cls === cls && Date.now() - notesAt < 300000)) return;
  notesAt = Date.now();
  const c = cls;
  fetch(NOTES_URL + "/notes/" + slugOf(c) + ".json?orderBy=%22$key%22&startAt=%22" + iso(new Date()) + "%22", {cache: "no-store"})
    .then(r => r.ok ? r.json() : Promise.reject()).then(j => {
      const next = {cls: c, days: j && typeof j === "object" ? j : {}};
      if (JSON.stringify(next) === JSON.stringify(NOTES)) return;
      NOTES = next; store.set("notes", NOTES); refresh();
    }).catch(() => {});
}
function editNote(date, l) {
  if (!navigator.onLine) return toast("Подписи работают только с интернетом");
  noteFor = {date: iso(date), num: l.num, cls};
  $("noteH").textContent = l.subject;
  $("noteMeta").textContent = DAYN[dow(date)] + ", " + dm(date) + " · урок " + l.num;
  $("noteText").value = noteOf(date, l.num);
  openDlg($("noteSheet")); $("noteText").focus();
}
$("noteForm").onsubmit = e => {
  e.preventDefault();
  const t = noteClean($("noteText").value), f = noteFor;
  if (RUDE.test(t.toLowerCase())) return toast("Давай без грубостей");
  fetch(NOTES_URL + "/notes/" + slugOf(f.cls) + "/" + f.date + "/n" + f.num + ".json", t ? {method: "PUT", body: JSON.stringify({t, at: {".sv": "timestamp"}})} : {method: "DELETE"})
    .then(r => {
      if (!r.ok) throw 0;
      if (NOTES.cls !== f.cls) NOTES = {cls: f.cls, days: {}};
      const day = NOTES.days[f.date] = NOTES.days[f.date] || {};
      if (t) day["n" + f.num] = {t}; else delete day["n" + f.num];
      store.set("notes", NOTES); closeDlg($("noteSheet")); refresh();
      toast(t ? "Подпись сохранена, её видит весь класс" : "Подпись убрана");
    }).catch(() => toast("Не получилось сохранить. Проверь интернет"));
};
