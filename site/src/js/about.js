/* Звонок · сайт о проекте: живая карточка «Сейчас» (то же расписание, что в приложении), счётчик дней проекта.
   На компьютере сайт открывается с этой страницы; «Открыть расписание» запоминается, и дальше сразу приложение. */
let abCls = DATA.classes[store.get("class", null)] ? store.get("class", null) : "7Б";
const abSrc = {at: d => dayLessons(abCls, d), real: true};
let abT = 0;

function abTick() {
  clearTimeout(abT);
  if (document.hidden) return; // свернули вкладку: ничего не считаем
  const now = new Date(), s = compute(now, abSrc), n = s.next;
  const live = s.kind === "LESSON" || s.kind === "BREAK" || (s.kind === "BEFORE" && s.soon);
  const show = s.kind === "LESSON" ? s.cur : n;
  $("abLive").className = "card ab-live hue-" + hueOf(show && show.subject);
  $("abDot").className = "ab-dot" + (live ? "" : " off");
  let status, cap = "", note = "";
  if (s.kind === "LESSON") {
    status = "Урок " + (s.idx + 1) + " из " + s.count; cap = "до звонка в " + hm(s.cur.end);
    note = n ? "Дальше в " + hm(n.start) + ": " + n.subject + (n.room ? ", " + n.room : "") : "Последний урок, потом домой";
  } else if (s.kind === "BREAK") {
    status = n.start - s.cur.end > 25 ? "Окно" : "Перемена"; cap = "до урока в " + hm(n.start); note = "Впереди ещё " + plural(s.count - s.idx - 1) + ", до " + s.end;
  } else if (s.kind === "BEFORE") {
    status = s.soon ? "Скоро уроки" : "Сегодня " + plural(s.count); cap = s.soon ? "до первого урока" : "первый урок";
    note = "Сегодня " + plural(s.count) + ", до " + s.end;
  } else {
    status = s.kind === "AFTER" ? "Уроки закончились" : s.holiday ? s.holiday[2] : "Выходной";
    cap = n ? (s.nDays === 1 ? "завтра" : s.nWhen) + " первый урок" : "";
    note = n ? (s.nDays === 1 ? "Завтра" : s.nLabel) + ": " + plural(s.nCount) + ", до " + s.nEnd : "Уроков пока нет";
  }
  $("abStatus").textContent = status;
  const tm = $("abTimer");
  tm.textContent = live ? fmtTimer(s.target - now) : n ? hm(n.start) : "Отдыхай";
  tm.classList.toggle("word", !live && !n);
  $("abCap").textContent = cap;
  $("abBar").parentElement.classList.toggle("off", !live);
  if (live) $("abBar").style.transform = "scaleX(" + Math.max(0, Math.min(1, (now - s.start) / (s.target - s.start))).toFixed(3) + ")";
  $("abSubj").textContent = show ? show.subject : "";
  $("abRoom").textContent = show ? show.room : "";
  $("abNext").textContent = note;
  // секунды нужны только идущему таймеру; без него хватает раза в минуту (звонки всегда в начале минуты)
  const ms = Date.now();
  abT = setTimeout(abTick, live ? 1000 - ms % 1000 + 15 : 60000 - ms % 60000 + 15);
}

function abAge() {
  const age = projectAgeText(new Date());
  $("abAgeN").textContent = age.n;
  $("abAgeT").textContent = age.days + " «Звонку»";
  $("abAgeS").textContent = "первая версия: " + age.born;
}

const sel = $("abCls");
CLASSES.forEach(c => sel.append(h("option", {value: c, text: c})));
sel.value = abCls;
sel.onchange = () => { abCls = sel.value; store.set("class", abCls); abTick(); };
// пришли за расписанием: на компьютере дальше сразу приложение, без этой страницы
$$("[data-app]").forEach(a => a.addEventListener("click", () => store.set("pcApp", true)));
// вернулись на вкладку: класс могли сменить в расписании, показываем тот же
document.addEventListener("visibilitychange", () => {
  const c = store.get("class", null);
  if (!document.hidden && DATA.classes[c] && c !== abCls) { abCls = c; sel.value = c; }
  abTick();
});
abAge();
// скачивания приложения и сколько телефонов за сутки проверили обновление: числа от GitHub,
// сборка сайта обновляет stats.json раз в сутки
const stat = (id, n, forms) => {
  if (!(n > 0)) return;
  $(id + "N").textContent = n.toLocaleString("ru-RU");
  $(id + "T").textContent = word(n, ...forms).slice(String(n).length + 1);
  $(id).hidden = false;
};
fetch("stats.json", {cache: "no-cache"}).then(r => r.ok ? r.json() : null).then(s => {
  if (!s) return;
  stat("abDl", s.downloads, ["скачивание", "скачивания", "скачиваний"]);
  stat("abUse", s.day, ["телефон", "телефона", "телефонов"]);
}).catch(() => {});
abTick();
