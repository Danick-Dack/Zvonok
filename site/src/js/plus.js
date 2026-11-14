/* Звонок · СОР и СОЧ, новости лицея, кружки и ШОР, праздничные темы, свой фон, счётчик устройств, пасхалки */

/* ---------- СОР и СОЧ ---------- */
let examCls = null;
/** Показать или спрятать вкладку: она нужна, пока хоть у одного класса впереди есть СОР или СОЧ. */
function examTab() {
  const on = examsEnd() >= iso(new Date());
  $$('a[data-tab="exams"]').forEach(a => { a.hidden = !on; });
  if (!on && view === "exams") location.hash = "now";
  return on;
}
function examDay(date, list, today) {
  const d = new Date(date + "T12:00:00"), n = Math.round((d - new Date(today + "T12:00:00")) / 864e5);
  const when = n === 0 ? "сегодня" : n === 1 ? "завтра" : n > 1 ? "через " + word(n, "день", "дня", "дней") : "";
  return h("article", {class: "exam-day" + (n === 0 ? " today" : n < 0 ? " past" : "")},
    h("div", {class: "exam-date"}, h("b", {text: d.getDate()}), h("small", {text: MONTHS[d.getMonth()].slice(0, 3) + ", " + DAYS[dow(d)].toLowerCase()})),
    h("ul", null, list.map(x => h("li", {class: "hue-" + hueOf(x.subject)}, h("span", {class: "tag " + (x.type === "СОЧ" ? "soch" : "sor"), text: x.type}), h("b", {text: x.subject})))),
    when ? h("span", {class: "exam-when", text: when}) : null);
}
function renderExams() {
  const c = DATA.classes[examCls] ? examCls : cls, today = iso(new Date()), all = examList(c), box = $("examList"), days = {};
  $("examClasses").replaceChildren(...CLASSES.map(x => h("button", {type: "button", "aria-pressed": String(x === c), text: x, onclick: () => { examCls = x; renderExams(); }})));
  const on = $("examClasses").querySelector('[aria-pressed="true"]');
  if (on) requestAnimationFrame(() => on.scrollIntoView({block: "nearest", inline: "center"}));
  all.forEach(x => (days[x.date] = days[x.date] || []).push(x));
  const dates = Object.keys(days).sort(), next = dates.filter(d => d >= today), past = dates.filter(d => d < today);
  const left = all.filter(x => x.date >= today).length;
  $("examsMeta").textContent = c + " · " + DATA.term + (left ? " · впереди " + left : "");
  box.replaceChildren(...(next.length ? next.map(d => examDay(d, days[d], today))
    : [h("p", {class: "exam-none", text: all.length ? "У " + c + " всё сдано. Можно выдохнуть." : "Графика для " + c + " пока нет."})]));
  if (past.length) box.append(h("details", {class: "exam-past"}, h("summary", null, "Уже прошли: " + past.length, icon("down", "sm")), past.reverse().map(d => examDay(d, days[d], today))));
}

/* ---------- новости: канал лицея в Telegram. Сайт сам в Telegram не ходит, сборка раз в час кладёт посты в news.json ---------- */
const TG = "https://t.me/sl165_official";
let FEED = null, feedAt = 0, feedFail = false;
/** Из файла берём только то, что умеем показать: номер, дату, текст, число фото в Telegram и пометку своего объявления.
    Свежие сверху по времени: свои объявления (номера от миллиарда) идут вперемешку с постами канала. */
function cleanFeed(j) {
  const items = j && Array.isArray(j.items) ? j.items : [];
  return items.filter(p => p && Number.isInteger(p.id) && p.id > 0 && typeof p.date === "string" && !isNaN(Date.parse(p.date))).slice(0, 40).map(p => ({
    id: p.id, date: p.date, at: Date.parse(p.date), text: typeof p.text === "string" ? p.text.slice(0, 4000) : "", own: p.own === true,
    pics: Number.isInteger(p.pics) && p.pics > 0 ? p.pics : 0, video: p.video === true})).sort((a, b) => b.at - a.at);
}
function loadNews() {
  if (!ONLINE_SITE || Date.now() - feedAt < 600000) return;
  feedAt = Date.now();
  fetch("news.json", {cache: "no-cache"}).then(r => r.ok ? r.json() : null).then(j => {
    const items = cleanFeed(j);
    feedFail = !items.length;
    if (items.length) FEED = items;
    newsDot();
    if (view === "news") renderNews();
  }).catch(() => { feedFail = true; if (view === "news") renderNews(); });
}
/** Точка на вкладке, пока есть непрочитанный пост. */
function newsDot() {
  const fresh = !!FEED && FEED[0].at > store.get("newsAt", 0) && view !== "news";
  $$('a[data-tab="news"]').forEach(a => a.classList.toggle("dot", fresh));
}
/** Текст поста: ссылки https становятся ссылками, остальное остаётся текстом (никакого HTML из канала). */
function postText(text) {
  return text.split(/(https?:\/\/[^\s<>"']+)/).map((part, i) => {
    if (!(i % 2)) return part;
    const url = part.replace(/[.,;:!?)»]+$/, "");
    return [h("a", {href: url, target: "_blank", rel: "noopener noreferrer", text: url.replace(/^https?:\/\/(www\.)?/, "").slice(0, 40)}), part.slice(url.length)];
  });
}
function renderNews() {
  const box = $("feed");
  if (!FEED) {
    box.replaceChildren(h("p", {class: "feed-empty", text: feedFail || !ONLINE_SITE ? "Новости подтянутся, когда появится интернет." : "Загружаю новости…"}),
      h("a", {class: "btn btn-quiet", href: TG, target: "_blank", rel: "noopener noreferrer", text: "Открыть канал в Telegram"}));
    loadNews();
    return;
  }
  store.set("newsAt", FEED[0].at); newsDot();
  const now = new Date();
  box.replaceChildren(...FEED.map(p => {
    // фото канала на сайт не копируем (на них ученики): только ссылка на пост, где они есть
    const d = new Date(p.date), link = TG + "/" + p.id, long = p.text.length > 420;
    const body = h("p", {class: "post-text" + (long ? " clamp" : "")}, postText(p.text));
    return h("article", {class: "post" + (p.own ? " own" : "")},
      h("div", {class: "post-in"},
        h("time", {datetime: p.date, text: (p.own ? "Объявление «Звонка» · " : "") + (iso(d) === iso(now) ? "Сегодня" : dm(d) + (d.getFullYear() === now.getFullYear() ? "" : " " + d.getFullYear())) + ", " + hm(d.getHours() * 60 + d.getMinutes())}),
        p.text ? body : null,
        h("div", {class: "row"},
          long ? h("button", {class: "btn btn-quiet btn-sm", type: "button", text: "Читать целиком", onclick: e => { body.classList.remove("clamp"); e.target.remove(); }}) : null,
          p.own ? null : h("a", {class: "btn btn-line btn-sm", href: link, target: "_blank", rel: "noopener noreferrer"},
            p.video ? "Видео в Telegram" : p.pics ? word(p.pics, "фото", "фото", "фото") + " в Telegram" : "В Telegram", icon("out", "sm")))));
  }));
}

/* ---------- кружки и ШОР ---------- */
/** Выбранные занятия из хранилища: только известные поля, не больше 12 кружков. */
function extraSet() {
  const e = store.get("extras", null) || {}, str = v => typeof v === "string";
  return {shor: (Array.isArray(e.shor) ? e.shor : []).filter(str),
    clubs: (Array.isArray(e.clubs) ? e.clubs : []).filter(c => c && str(c.name) && Array.isArray(c.times)).slice(0, 12)
      .map(c => ({name: clean(c.name), place: clean(c.place), times: c.times.filter(t => Array.isArray(t) && t[0] >= 0 && t[0] < 7 && toMin(t[1]) >= 0 && toMin(t[2]) > toMin(t[1])).slice(0, 7)}))};
}
let EXTRA = extraSet(), EXTRA_ON = false;
function saveExtras() { store.set("extras", EXTRA); refresh(); renderExtras(); }
const timesText = times => times.map(t => DAYS[t[0]] + " " + t[1] + "–" + t[2]).join(", ");
function renderExtras() {
  const grade = pick || useCustom ? 0 : parseInt(cls), n = EXTRA.shor.length + EXTRA.clubs.length;
  $("extrasSum").textContent = n ? "Выбрано: " + n : "Занятия после уроков";
  const groups = (DATA.shor || []).slice().sort((a, b) => b.grades.includes(grade) - a.grades.includes(grade));
  $("shorList").replaceChildren(...groups.map(g => h("label", {class: "pick-row" + (!grade || g.grades.includes(grade) ? "" : " other")},
    h("input", {type: "checkbox", checked: EXTRA.shor.includes(g.id), onchange: e => {
      EXTRA.shor = EXTRA.shor.filter(x => x !== g.id).concat(e.target.checked ? [g.id] : []); saveExtras(); }}),
    h("span", null, h("b", {text: g.subject + " · " + g.coach}),
      h("small", {text: (g.grades.length > 1 ? g.grades[0] + "–" + g.grades[g.grades.length - 1] : g.grades[0]) + " кл. · " + timesText(g.times) + " · " + g.place})))));
  $("clubList").replaceChildren(...EXTRA.clubs.map((c, i) => h("div", {class: "pick-row"},
    h("span", null, h("b", {text: c.name}), h("small", {text: timesText(c.times) + (c.place ? " · " + c.place : "")})),
    h("button", {class: "icon-btn", type: "button", "aria-label": "Удалить «" + c.name + "»", onclick: () => { EXTRA.clubs.splice(i, 1); saveExtras(); }}, icon("trash", "sm")))));
}
(function initExtras() {
  $("clubDays").append(...DAYS.map((d, i) => h("button", {type: "button", "aria-pressed": "false", "data-d": i, text: d,
    onclick: e => e.target.setAttribute("aria-pressed", String(e.target.getAttribute("aria-pressed") !== "true"))})));
  $("clubForm").onsubmit = e => {
    e.preventDefault();
    const days = $$('#clubDays [aria-pressed="true"]').map(b => +b.dataset.d), from = $("clubFrom").value, to = $("clubTo").value, name = clean($("clubName").value);
    if (!name) return toast("Напиши название кружка");
    if (!days.length) return toast("Выбери дни недели");
    if (!(toMin(to) > toMin(from)) || toMin(from) < 0) return toast("Конец должен быть позже начала");
    if (EXTRA.clubs.length >= 12) return toast("Больше 12 кружков не влезет. Удали лишний");
    EXTRA.clubs.push({name, place: clean($("clubPlace").value), times: days.map(d => [d, from, to])});
    e.target.reset(); $$("#clubDays button").forEach(b => b.setAttribute("aria-pressed", "false"));
    saveExtras(); toast("Добавил «" + name + "»");
  };
})();
/** Карточка на «Сейчас»: идёт занятие или оно ближайшее после уроков. Пока идут уроки, не отвлекаем раньше чем за полтора часа. */
function renderExtra(now, s) {
  const x = pick ? null : extraNow(now, EXTRA), busy = s && (s.kind === "LESSON" || s.kind === "BREAK" || s.kind === "BEFORE");
  EXTRA_ON = !!x && (x.on || !busy || x.target - now < 90 * 60000);
  $("extra").hidden = !EXTRA_ON;
  if (!EXTRA_ON) return;
  $("extraText").textContent = extraText(x, now);
  $("extraSub").textContent = hm(x.start) + "–" + hm(x.end) + (x.place ? " · " + x.place : "");
}

/* ---------- праздничные темы: DATA.themes, в каникулы своя. Выключаются в «Оформлении» ---------- */
function festNow(now) {
  if (!store.get("fest", true)) return null;
  const f = festive(now), hol = !f && src.real && holiday(now);
  return f && /^[0-9a-f]{6}$/i.test(f[3]) ? f : hol && /каникул/i.test(hol[2]) ? ["", "", "🎈", "34C759", hol[2], "Отдыхай, расписание подождёт"] : null;
}
let festKey = null;
function renderFest(now) {
  const f = festNow(now), root = document.documentElement, k = f ? f[4] + now.getFullYear() : "";
  if (k !== festKey) {
    festKey = k;
    if (f) { root.dataset.fest = ""; root.style.setProperty("--fest", "#" + f[3]); root.style.setProperty("--fest-e", '"' + clean(f[2]).replace(/["\\]/g, "") + '"'); }
    else { delete root.dataset.fest; root.style.removeProperty("--fest"); root.style.removeProperty("--fest-e"); }
  }
  const show = !!f && !pick && store.get("festSeen", "") !== k;
  $("fest").hidden = !show;
  if (!show) return;
  $("festIcon").textContent = f[2]; $("festName").textContent = f[4]; $("festText").textContent = f[5] || "";
  $("fest").onclick = () => { store.set("festSeen", k); rain(f[2]); setTimeout(() => { $("fest").hidden = true; }, 1600); };
}
$("festToggle").checked = !!store.get("fest", true);
$("festToggle").onchange = e => { store.set("fest", e.target.checked); renderFest(new Date()); };

/* ---------- свой фон: картинка ужимается и хранится только в этом браузере ---------- */
function applyBg() {
  const bg = store.get("bg", null), ok = typeof bg === "string" && /^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(bg), root = document.documentElement;
  if (ok) { root.dataset.bg = ""; root.style.setProperty("--bgimg", 'url("' + bg + '")'); } else { delete root.dataset.bg; root.style.removeProperty("--bgimg"); }
  $("bgClear").hidden = !ok;
}
$("bgFile").onchange = e => {
  const file = e.target.files[0], img = new Image();
  if (!file) return;
  img.onload = () => {
    const k = Math.min(1, 1280 / Math.max(img.width, img.height)), c = h("canvas", {width: Math.round(img.width * k), height: Math.round(img.height * k)});
    c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
    URL.revokeObjectURL(img.src);
    store.set("bg", c.toDataURL("image/jpeg", .72));
    applyBg();
    toast(store.get("bg", null) ? "Фон поставлен" : "Картинка слишком большая для памяти браузера");
  };
  img.onerror = () => toast("Не получилось открыть эту картинку");
  img.src = URL.createObjectURL(file);
  e.target.value = "";
};
$("bgClear").onclick = () => { store.del("bg"); applyBg(); toast("Фон убран"); };
applyBg();

/* в приложении iPhone: фон виджетов и уведомления (сохраняет и присылает сам скрипт Scriptable) */
if (EMBED) {
  const ntf = () => Object.assign({exam: true, news: true}, store.get("ntf", null));
  $("ntfExam").checked = ntf().exam; $("ntfNews").checked = ntf().news;
  $("ntfExam").onchange = $("ntfNews").onchange = () => store.set("ntf", {exam: $("ntfExam").checked, news: $("ntfNews").checked});
  $("wbgPick").onclick = () => bridge({bg: "pick"});
  $("wbgClear").onclick = () => { bridge({bg: "clear"}); toast("Фон виджетов убран"); };
}

/* ---------- сколько устройств пользуется «Звонком» ----------
   Раз в сутки страница скачивает пустой файл ping.json из релиза на GitHub. GitHub сам считает скачивания файла,
   сборка сайта раз в сутки берёт прирост счётчика. Мы не видим и не храним ни IP, ни чего-либо об устройстве. */
function pingOnce() {
  const today = iso(new Date());
  if (!ONLINE_SITE || EMBED || !navigator.onLine || store.get("ping", "") === today) return; // в приложении iPhone считает сам скрипт
  store.set("ping", today);
  fetch("https://github.com/Danick-Dack/Zvonok/releases/latest/download/ping.json", {mode: "no-cors", cache: "no-store", credentials: "omit", referrerPolicy: "no-referrer"}).catch(() => {});
}

/* ---------- пасхалки ---------- */
/** Дождь из значка на пару секунд (в спокойном режиме просто надпись). */
function rain(sign) {
  if (CALM) return toast(sign);
  const box = h("div", {class: "rain", "aria-hidden": "true"}, Array.from({length: 18}, (_, i) =>
    h("i", {text: sign, style: "left:" + (i * 5.6 + Math.random() * 3) + "%;animation-delay:" + (Math.random() * .7).toFixed(2) + "s;font-size:" + (18 + Math.round(Math.random() * 16)) + "px"})));
  document.body.append(box);
  setTimeout(() => box.remove(), 2600);
}
const QUOTES = ["Звонок для учителя. Но таймер для тебя.", "До конца урока всегда чуть дольше, чем кажется.", "Кто понял жизнь, тот сидит на последней парте.",
  "Забыл форму: классика жанра.", "Домашка, сделанная вечером, утром не снится.", "Вторая смена обуви спасает мир.", "«Достаём двойные листочки»: фраза, от которой холодеет спина.",
  "Сегодня точно спросят. Или нет.", "Столовая: успей за одну перемену.", "Учебник дома, знания тоже."];
(function initEggs() {
  // 1. набери на клавиатуре «165», «звонок» или код Konami
  const CODES = {"165": () => { rain("🔔"); toast("165: лицей, в котором звенит «Звонок»"); }, "звонок": () => { rain("⏰"); toast("Дзынь!"); }, "zvonok": () => { rain("⏰"); toast("Дзынь!"); },
    "ArrowUpArrowUpArrowDownArrowDownArrowLeftArrowRightArrowLeftArrowRightba": () => { rain("⭐"); toast("Чит-код принят. Пятёрка в четверти не гарантируется"); }};
  let typed = "";
  addEventListener("keydown", e => {
    if (/^(INPUT|TEXTAREA|SELECT)$/.test((e.target || {}).tagName) || e.ctrlKey || e.metaKey || e.altKey) return;
    typed = (typed + (e.key.length === 1 ? e.key.toLowerCase() : e.key)).slice(-80);
    for (const k in CODES) if (typed.endsWith(k)) { typed = ""; CODES[k](); }
  });
  // 2. подержи палец на таймере: школьная мудрость
  let hold = 0;
  const dial = $("lDial"), stop = () => clearTimeout(hold);
  dial.addEventListener("pointerdown", () => { hold = setTimeout(() => toast(QUOTES[Math.floor(Math.random() * QUOTES.length)]), 700); });
  ["pointerup", "pointerleave", "pointercancel"].forEach(ev => dial.addEventListener(ev, stop));
  dial.addEventListener("contextmenu", e => e.preventDefault());
  // 3. пять быстрых нажатий на таймер: кольцо делает круг почёта
  let taps = 0, tapT = 0;
  dial.addEventListener("click", () => {
    taps++; clearTimeout(tapT); tapT = setTimeout(() => { taps = 0; }, 500);
    if (taps >= 5) { taps = 0; dial.classList.add("spin"); setTimeout(() => dial.classList.remove("spin"), 1000); }
  });
  // 4. первого апреля страница один раз переворачивается
  const now = new Date();
  if (now.getMonth() === 3 && now.getDate() === 1 && !CALM && store.get("fool", 0) !== now.getFullYear()) {
    store.set("fool", now.getFullYear());
    document.body.classList.add("flip"); setTimeout(() => document.body.classList.remove("flip"), 2200);
  }
  // 6. вход в панель объявлений: во вкладке «Новости» три нажатия на заголовок, потом три нажатия на вкладку «Новости».
  //    Это не защита (код открыт): панель просит код, а без ключа GitHub на этом устройстве ничего не может.
  let heads = 0, headAt = 0, armed = 0, tabs = 0, tabAt = 0;
  $("newsH").addEventListener("click", () => {
    const now = Date.now();
    heads = now - headAt < 1500 ? heads + 1 : 1; headAt = now;
    if (heads >= 3) { heads = 0; armed = now; }
  });
  $$('a[data-tab="news"]').forEach(a => a.addEventListener("click", () => {
    const now = Date.now();
    if (now - armed > 15000) return;
    tabs = now - tabAt < 1500 ? tabs + 1 : 1; tabAt = now;
    if (tabs >= 3) { tabs = 0; armed = 0; location.href = "panel.html"; }
  }));
  // 5. в консоли для любопытных
  try { console.log("%cЗвонок", "font:700 28px system-ui", "\nЛюбишь заглядывать под капот? Нам такие нужны: " + SITE + "feedback.html"); } catch (e) {}
})();
