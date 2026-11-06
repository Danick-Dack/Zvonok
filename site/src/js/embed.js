/* Звонок · сайт внутри приложения Scriptable на iPhone (там же виджеты «Звонка»): ?app=scriptable.
   Scriptable забирает события через zvNext(completion): что выбрать для виджетов, какую ссылку открыть в Safari,
   чем поделиться и что скопировать. Сам он только сохраняет настройки и открывает ссылки, кода страницы не трогает. */
const EMBED = /[?&]app=scriptable(&|$)/.test(location.search);
const SYNCED = ["class", "custom", "useCustom"];
const bridgeQ = [];
let bridgeWait = null;
/** Отдать событие приложению; false, если страница открыта не в нём. */
function bridge(msg) {
  if (!EMBED) return false;
  const text = JSON.stringify(msg);
  if (bridgeWait) { const done = bridgeWait; bridgeWait = null; done(text); } else bridgeQ.push(text);
  return true;
}
/** Настройки, которые нужны виджетам: класс и своё расписание. */
const zvState = () => ({cls: store.get("class", null), custom: store.get("custom", null), useCustom: !!store.get("useCustom", false)});
if (EMBED) {
  document.documentElement.classList.add("embed");
  window.zvNext = done => { if (bridgeQ.length) done(bridgeQ.shift()); else bridgeWait = done; };
  window.zvState = () => JSON.stringify(zvState());
  // Свежая установка (пустое хранилище страницы): берём то, что уже выбрано в виджетах
  window.zvSync = s => {
    if (store.get("class", null) || !s || typeof s !== "object") return false;
    if (typeof s.cls === "string" && DATA.classes[s.cls]) store.set("class", s.cls);
    if (s.custom && typeof s.custom === "object") { store.set("custom", s.custom); store.set("useCustom", !!s.useCustom); }
    // как после выбора класса в шапке, но без перезагрузки: она пошла бы в сеть за живой страницей
    custom = store.get("custom", null);
    useCustom = !!store.get("useCustom", false) && !!custom;
    if (DATA.classes[store.get("class", null)]) cls = store.get("class", null);
    pick = !useCustom && !DATA.classes[store.get("class", null)];
    rebuild(); refresh();
    return true;
  };
  const set0 = store.set;
  let syncT = 0;
  store.set = (k, v) => {
    set0(k, v);
    if (SYNCED.includes(k)) { clearTimeout(syncT); syncT = setTimeout(() => bridge({set: zvState()}), 50); }
  };
  // ссылки наружу и файлы: в Safari (в приложении у страницы нет вкладок и загрузок)
  document.addEventListener("click", e => {
    const a = e.target.closest && e.target.closest("a[href]");
    if (!a || !(a.target === "_blank" || a.hasAttribute("download") || !/^https?:/.test(a.href))) return;
    if (/^(https?|webcal|tg|mailto):/.test(a.href)) { e.preventDefault(); bridge({open: a.href}); }
  }, true);
}
