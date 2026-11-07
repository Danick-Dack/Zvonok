/* Звонок · вкладка «Ещё»: установка, календарь, поделиться, оформление, своя иконка */
/* ---------- «Ещё» ---------- */
const calPath = c => "cal/" + c.replace(/[АБВГ]/g, x => LAT[x].toUpperCase()) + ".ics";
const calWebcal = c => SITE.replace(/^https?:/, "webcal:") + calPath(c);
const shareUrl = () => pick || useCustom ? SITE : SITE + "#" + slugOf(cls);
// сообщение для чата класса: сначала знакомая всем боль, потом польза, потом «ничего не надо делать»
const shareMsg = () => "Больше не надо спрашивать в чате «а какой кабинет?». «Звонок» показывает, сколько до звонка, какой урок дальше и в каком кабинете, с заменами. Без регистрации и рекламы, работает без интернета.\n"
  + (pick || useCustom ? "Открыть: " : "Наш " + cls + " сразу тут: ") + shareUrl();
function qrSvg(code) {
  const [n, hex] = code.split(":"), N = +n;
  let bits = "", p = "";
  for (const ch of hex) bits += parseInt(ch, 16).toString(2).padStart(4, "0");
  for (let i = 0; i < N * N; i++) if (bits[i] === "1") p += "M" + i % N + " " + Math.floor(i / N) + "h1v1h-1z";
  return '<svg viewBox="0 0 ' + N + " " + N + '" shape-rendering="crispEdges" aria-hidden="true"><path d="' + p + '" fill="#000"/></svg>';
}
let osPick = IOS ? "ios" : "android";
function renderOs() {
  $$("#osSeg button").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.os === osPick)));
  $("osIos").hidden = osPick !== "ios"; $("osAndroid").hidden = osPick !== "android";
}
$$("#osSeg button").forEach(b => b.onclick = () => { osPick = b.dataset.os; renderOs(); });
const BRANDS = {
  xiaomi: ["Xiaomi, Redmi, POCO", [
    ["Автозапуск", "Настройки → Приложения → Звонок → включи «Автозапуск»."],
    ["Батарея без ограничений", "Там же: «Контроль активности» (или «Батарея») → «Нет ограничений»."],
    ["Закрепи в недавних", "Открой недавние приложения, зажми карточку «Звонка» и нажми на замок. Тогда система его не закроет."]]],
  samsung: ["Samsung", [
    ["Батарея без ограничений", "Настройки → Приложения → Звонок → Батарея → «Не ограничено»."],
    ["Не в спящих", "Настройки → Батарея → «Ограничения фонового использования»: «Звонка» не должно быть среди спящих приложений."]]],
  transsion: ["Tecno, Infinix, itel", [
    ["Автозапуск", "Phone Master (или «Менеджер телефона») → «Автозапуск» → включи «Звонок»."],
    ["Работа в фоне", "Настройки → Батарея → Звонок → разреши работу в фоне без «умной» оптимизации."]]],
  oppo: ["realme, OPPO, OnePlus", [
    ["Фоновая активность", "Настройки → Приложения → Звонок → Батарея → «Разрешить фоновую активность»."],
    ["Автозапуск", "Там же: «Автозапуск» или «Разрешить автоматический запуск»."]]],
  vivo: ["vivo, iQOO", [
    ["Работа в фоне", "Настройки → Батарея → «Фоновое энергопотребление» → Звонок → разрешить."],
    ["Автозапуск", "i Manager → Приложения → «Автозапуск» → включи «Звонок»."]]],
  huawei: ["Huawei, Honor", [
    ["Запуск приложений", "Настройки → Батарея → «Запуск приложений» → Звонок → «Управлять вручную» и включи все три пункта."]]],
  stock: ["Google Pixel, Motorola, Nothing, Nokia и другие", [
    ["Батарея без ограничений", "Настройки → Приложения → Звонок → Батарея → «Без ограничений»."]]]
};
function renderBrand() {
  const b = BRANDS[$("brand").value], box = $("brandSteps");
  box.textContent = ""; box.hidden = !b;
  if (b) b[1].forEach(([t, p]) => box.append(h("li", null, h("div", null, h("b", {text: t}), h("p", {text: p})))));
}
(function initMore() {
  const sel = $("brand");
  sel.append(h("option", {value: "", text: "Выбери марку"}));
  Object.keys(BRANDS).forEach(k => sel.append(h("option", {value: k, text: BRANDS[k][0]})));
  sel.onchange = renderBrand;
  $("apk").href = APK;
  $("iosDone").hidden = !STANDALONE && !EMBED;
  $("shareBtn").onclick = () => bridge({share: shareMsg()})
    || (navigator.share ? navigator.share({title: "Звонок", text: shareMsg().split("\n")[0], url: shareUrl()}).catch(() => {}) : copyText(shareUrl(), "Ссылка скопирована"));
  $("copyBtn").onclick = () => copyText(shareUrl(), "Ссылка скопирована");
  $("msgBtn").onclick = () => copyText(shareMsg(), "Сообщение для чата скопировано");
  $("useCustom").onchange = e => { setCustom(e.target.checked); toast(useCustom ? "Показываю своё расписание" : "Показываю расписание " + cls); };
  $("calIcs").onclick = () => { if (useCustom) { download("Звонок.ics", icsFor(WEEK, "Своё расписание"), "text/calendar"); return false; } };
  renderOs();
})();
function renderMore() {
  // мини-счётчик в «О проекте»: не на главном экране, а здесь и на сайте о проекте
  const age = projectAgeText(new Date()), ageEl = $("age");
  if (ageEl) ageEl.textContent = "«Звонку» " + age.n + " " + age.days + ". Первая версия вышла " + age.born + ".";
  const c = pick ? null : cls;
  // календарь класса: подписка обновляется сама; для своего расписания файл собираем тут же
  $("calNote").textContent = useCustom ? "Своё расписание можно скачать файлом и открыть в календаре. Сам он не обновится, при изменениях скачай заново."
    : c ? "Уроки " + c + " появятся в календаре телефона. Каникулы и замены учтены, календарь обновляется сам." : "Сначала выбери класс.";
  $("calApple").hidden = $("calGoogle").hidden = useCustom || !c;
  if (c) {
    $("calApple").href = calWebcal(c); $("calApple").textContent = "Подписаться в Календаре: " + c;
    $("calGoogle").href = "https://calendar.google.com/calendar/render?cid=" + calWebcal(c);
  }
  $("calIcs").hidden = !c && !useCustom;
  $("calIcs").href = useCustom ? "#calendar" : c ? calPath(c) : "#";
  $("calIcs").setAttribute("download", useCustom ? "Звонок.ics" : "Звонок " + c + ".ics");
  const has = !!store.get("custom", null);
  $("customSwitch").hidden = !has; $("useCustom").checked = useCustom;
  // поделиться: ссылка и QR сразу на класс
  const url = shareUrl(), code = QRS && QRS[pick || useCustom ? "" : cls];
  if (code) { $("qr").innerHTML = qrSvg(code); $("qr").setAttribute("aria-label", "QR-код: " + url); }
  $("shareNote").textContent = pick || useCustom ? "Ссылка на сайт. Наведи камеру телефона на QR-код." : "Ссылка и QR-код сразу откроют расписание " + cls + ".";
  $("shTg").href = "https://t.me/share/url?url=" + encodeURIComponent(url) + "&text=" + encodeURIComponent(shareMsg().split("\n")[0]);
  $("shWa").href = "https://wa.me/?text=" + encodeURIComponent(shareMsg());
  $("shTg").hidden = $("shWa").hidden = !!navigator.share;
  $("poster").href = "poster.html" + (pick || useCustom ? "" : "#" + slugOf(cls));
  $("term").textContent = "Расписание: " + DATA.term + ", утверждено " + DATA.updated.split("-").reverse().join(".") + ".";
  renderLook();
}
/* оформление: тема и цвет акцента */
function renderLook() {
  const seg = $("schemeSeg"); seg.textContent = "";
  [["auto", "Как в системе"], ["light", "Светлая"], ["dark", "Тёмная"]].forEach(([k, n]) => seg.append(h("button", {type: "button", "aria-pressed": String(scheme() === k), text: n,
    onclick: () => { applyScheme(k); renderScheme(); renderLook(); }})));
  $("hueToggle").checked = !store.get("plain", false);
  $("iconToggle").checked = !!store.get("classIcon", false);
  const row = $("accentRow"), dark = isDark(), cur = accent(); row.textContent = "";
  const light = hex => { const n = parseInt(hex.slice(1), 16); return (0.2126 * (n >> 16 & 255) + 0.7152 * (n >> 8 & 255) + 0.0722 * (n & 255)) / 255 > .55; };
  ACCENTS.forEach(a => {
    const c = dark ? a.dark : a.light;
    row.append(h("button", {type: "button", "aria-pressed": String(a.id === cur), "aria-label": "Цвет: " + a.name, onclick: () => { applyAccent(a.id); renderLook(); }},
      h("i", {style: "--sw:" + c + ";--swi:" + (light(c) ? "#111" : "#fff")}, a.id === cur ? icon("check") : null), a.name));
  });
}
document.addEventListener("zv:scheme", () => { if (!$("v-more").hidden) renderLook(); else dirty.more = true; });
$("hueToggle").onchange = e => {
  store.set("plain", !e.target.checked);
  if (e.target.checked) delete document.documentElement.dataset.plain; else document.documentElement.dataset.plain = "";
};
function download(name, text, type) {
  const a = h("a", {href: URL.createObjectURL(new Blob([text], {type: type || "text/plain"})), download: name});
  document.body.append(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
}

/* Своя иконка на экране «Домой»: кольцо часов «Звонка» с номером класса внутри.
   iPhone берёт apple-touch-icon, Android манифест; оба читают их в момент добавления на экран. */
function homeIcon() {
  const apple = document.querySelector('link[rel="apple-touch-icon"]'), man = document.querySelector('link[rel="manifest"]');
  if (!apple || !man || !ONLINE_SITE || EMBED) return;
  if (!homeIcon.orig) homeIcon.orig = [apple.getAttribute("href"), man.getAttribute("href")];
  if (!store.get("classIcon", false) || pick || useCustom) { apple.setAttribute("href", homeIcon.orig[0]); man.setAttribute("href", homeIcon.orig[1]); return; }
  const png = size => {
    const c = document.createElement("canvas"); c.width = c.height = size;
    const g = c.getContext("2d"), m = size / 2, r = size * .34, w = size * .07;
    g.fillStyle = "#0B0B0C"; g.fillRect(0, 0, size, size);
    g.lineWidth = w; g.lineCap = "round"; g.strokeStyle = "#2E2E32"; g.beginPath(); g.arc(m, m, r, 0, Math.PI * 2); g.stroke();
    g.strokeStyle = "#FFFFFF"; g.beginPath(); g.arc(m, m, r, -Math.PI / 2, Math.PI); g.stroke();
    g.fillStyle = "#FFFFFF"; g.textAlign = "center"; g.textBaseline = "middle";
    g.font = "700 " + Math.round(size * (cls.length > 2 ? .2 : .24)) + "px -apple-system,'Segoe UI',Roboto,sans-serif";
    g.fillText(cls, m, m + size * .01);
    return c.toDataURL("image/png");
  };
  apple.setAttribute("href", png(180));
  const base = location.href.split("#")[0].replace(/[^/]*$/, "");
  const m = {name: "Звонок · " + cls, short_name: "Звонок " + cls, lang: "ru", start_url: base + "#" + slugOf(cls), scope: base, display: "standalone",
    background_color: "#0B0B0C", theme_color: "#0B0B0C", icons: [{src: png(192), sizes: "192x192", type: "image/png"}, {src: png(512), sizes: "512x512", type: "image/png"}]};
  man.setAttribute("href", URL.createObjectURL(new Blob([JSON.stringify(m)], {type: "application/manifest+json"})));
}
$("iconToggle").onchange = e => { store.set("classIcon", e.target.checked); homeIcon(); toast(e.target.checked ? "Готово. Добавь сайт на экран «Домой» заново" : "Иконка снова обычная"); };
