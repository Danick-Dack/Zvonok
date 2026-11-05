/* Звонок · iPhone: скрипт виджета для своего класса (Scriptable), календарь класса */
const SCRIPTABLE = /*@SCRIPTABLE@*/null;
const IOS_VERSIONS = /*@IOSVER@*/null || {}; // версии скриптов классов: виджет сам скачает новую
const SITE_URL = "@SITE_URL@";
const OWN = "__own__";

/* код темы из конструктора: те же цвета у виджета iPhone, что у виджетов Android */
function themeCode() {
  const t = cleanTheme(store.get("theme", {})); // только проверенные поля: код встаёт внутрь кавычек в скрипте
  if (Object.keys(t).every(k => t[k] === TH_DEFAULT[k])) return ""; // ничего не меняли: скрипт и так в фирменных цветах
  return themeString(t);
}
/** Скрипт для Scriptable: класс, звонки, неделя и тема вписаны внутрь, поэтому виджет работает без сети. */
function scriptFor(c) {
  let week = {}, bells = BELLS, name = c;
  if (c === OWN) {
    const own = store.get("custom", null) || {};
    const w = customWeek(own);
    bells = (own.bells || DATA.bells).map(b => [toMin(b && b[0]), toMin(b && b[1])]);
    name = "своё";
    for (let d = 0; d < 6; d++) { const arr = []; w[d].forEach(l => arr[l.num - 1] = [l.subject, l.room]); for (let i = 0; i < arr.length; i++) if (!arr[i]) arr[i] = ["", ""]; if (arr.length) week[d + 1] = arr; }
  } else {
    const w = classWeek(c);
    for (let d = 0; d < 6; d++) { const arr = []; w[d].forEach(l => arr[l.num - 1] = [l.subject, l.room]); for (let i = 0; i < arr.length; i++) if (!arr[i]) arr[i] = ["", ""]; if (arr.length) week[d + 1] = arr; }
  }
  const ver = c === OWN ? "" : IOS_VERSIONS[c.replace(/[АБВГ]/g, x => ({"А": "A", "Б": "B", "В": "V", "Г": "G"})[x])] || "";
  return SCRIPTABLE.split("__BELLS__").join(JSON.stringify(bells)).split("__WEEK__").join(JSON.stringify(week))
    .split("__THEME__").join(themeCode()).split("__VERSION__").join(ver).split("__CLASS__").join(name);
}
const calUrl = c => SITE_URL.replace(/^https?:/, "webcal:") + "cal/" + c.replace(/[АБВГ]/g, x => ({"А": "A", "Б": "B", "В": "V", "Г": "G"})[x]) + ".ics";

(function initKit() {
  const sel = $("kCls");
  if (!sel) return;
  CLASSES.forEach(c => sel.append(h("option", {value: c, text: "Класс " + c})));
  if (store.get("custom", null)) sel.append(h("option", {value: OWN, text: "Своё расписание"}));
  const saved = store.get("class", null);
  sel.value = store.get("useCustom", false) && store.get("custom", null) ? OWN : DATA.classes[saved] ? saved : CLASSES[0];
  const sync = () => {
    const own = sel.value === OWN;
    $("kCal").hidden = own;
    if (!own) { $("kCal").href = calUrl(sel.value); $("kCal").textContent = "Подписаться: " + sel.value; }
  };
  sel.onchange = sync; sync();
  $("kCopy").onclick = () => copyText(scriptFor(sel.value), "Скрипт скопирован. Теперь открой Scriptable");
  $("kFile").onclick = () => {
    const a = h("a", {href: URL.createObjectURL(new Blob([scriptFor(sel.value)], {type: "text/javascript"})), download: "Звонок.js"});
    document.body.append(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
  };
})();
registerSW();
