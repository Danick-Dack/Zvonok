/* Звонок · iPhone: скрипт для Scriptable (виджеты и приложение «Звонок»), календарь класса */
const IOS_VERSIONS = /*@IOSVER@*/null || {}; // версии скриптов классов: виджет сам скачает новую
const SITE_URL = "@SITE_URL@";
const OWN = "__own__";
const upSlug = c => c.replace(/[АБВГ]/g, x => LAT[x].toUpperCase());

/* код темы из конструктора: те же цвета у виджета iPhone, что у виджетов Android */
function themeCode() {
  const t = cleanTheme(store.get("theme", {})); // только проверенные поля: код встаёт внутрь кавычек в скрипте
  if (Object.keys(t).every(k => t[k] === TH_DEFAULT[k])) return ""; // ничего не меняли: скрипт и так в фирменных цветах
  return themeString(t);
}
/** Скрипт класса с сайта (ios/<класс>.js) с темой и, для своего расписания, с ним внутри (дальше оно живёт в настройках). */
function personalize(code, own) {
  const t = themeCode();
  if (t) code = code.replace('const THEME = "";', "const THEME = " + JSON.stringify(t) + ";");
  if (own) code = code.replace("const START = null;", "const START = " + JSON.stringify(own) + ";");
  return code;
}
const calUrl = c => SITE_URL.replace(/^https?:/, "webcal:") + "cal/" + upSlug(c) + ".ics";

(function initKit() {
  const sel = $("kCls");
  if (!sel) return;
  CLASSES.forEach(c => sel.append(h("option", {value: c, text: "Класс " + c})));
  if (store.get("custom", null)) sel.append(h("option", {value: OWN, text: "Своё расписание"}));
  const saved = store.get("class", null);
  sel.value = store.get("useCustom", false) && store.get("custom", null) ? OWN : DATA.classes[saved] ? saved : CLASSES[0];
  let code = "", want = "";
  // скрипт скачиваем заранее, при выборе класса: копировать на iPhone можно только сразу по нажатию
  const sync = () => {
    const own = sel.value === OWN, base = own ? (DATA.classes[saved] ? saved : CLASSES[0]) : sel.value;
    $("kCal").hidden = own;
    if (!own) { $("kCal").href = calUrl(sel.value); $("kCal").textContent = "Подписаться: " + sel.value; }
    code = "";
    const key = want = sel.value;
    fetch("ios/" + upSlug(base) + ".js").then(r => r.ok ? r.text() : Promise.reject(r.status))
      .then(t => { if (want === key) code = personalize(t, own ? store.get("custom", null) : null); })
      .catch(() => {});
  };
  sel.onchange = sync; sync();
  const ready = () => { if (!code) toast(navigator.onLine === false ? "Нужен интернет, чтобы взять скрипт" : "Скрипт ещё загружается, нажми через секунду"); return !!code; };
  $("kCopy").onclick = () => { if (ready()) copyText(code, "Скрипт скопирован. Теперь открой Scriptable"); };
  $("kFile").onclick = () => {
    if (!ready()) return;
    const a = h("a", {href: URL.createObjectURL(new Blob([code], {type: "text/javascript"})), download: "Звонок.js"});
    document.body.append(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
  };
})();
registerSW();
