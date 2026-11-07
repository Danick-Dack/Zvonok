// Звонок 9Г: виджеты и приложение для iPhone (через бесплатное приложение Scriptable).
// Экран блокировки: прямоугольный, круглый, строка над часами. Рабочий стол и StandBy: маленький, средний, большой.
// Если запустить скрипт в Scriptable, откроется само приложение «Звонок»: «Сейчас», «Неделя», «Кабинеты» и «Ещё», как на сайте.
// Класс и своё расписание выбираются там же, виджеты показывают то же самое.
// Параметр виджета (зажми виджет → «Изменить»): «10А» покажет 10А, «кабинеты» свободные кабинеты, код темы с сайта поменяет цвета.
//
// Можно спросить Siri или нажать кнопку действия: в «Командах» добавь Scriptable → Run Script → «Звонок» и «Показать результат».
//
// Заряд почти не тратится: таймер на виджете тикает силами iOS, а сам скрипт iOS запускает изредка.
// В интернет скрипт ходит только на свой сайт: за файлом замен (не чаще раза в 3 часа) и за новой версией себя
// (не чаще раза в 6 часов). Новая версия ставится сама, только если подписана ключом «Звонка» (Ed25519),
// и приходит уведомление «Звонок обновился».
// Выключить самообновление: AUTO_UPDATE = false ниже. Без сети всё работает как обычно.

const AUTO_UPDATE = true;

// ---------- данные (сайт подставляет сюда класс по умолчанию) ----------
const CLASS = "9Г"; // класс скрипта: его новую версию виджет и скачивает
const THEME = ""; // код темы с сайта, например "ZV1;bg=0B1220;ac=60A5FA". Параметр виджета важнее
const VERSION = "de0979d656";
const BUILT = 1794057173; // время сборки (сайт подставляет своё): более старую версию поверх этой виджет не поставит
const UPDATE_KEY = ""; // открытый ключ подписи новых версий (пусто: сборка была без ключа)
const START = null; // своё расписание со страницы iPhone: при первом запуске переходит в настройки
const DATA = {"school":"Лицей №165","term":"2 четверть 2026–2027","updated":"2026-10-30","term_end":"2026-12-27","holidays":[["2026-12-16","2026-12-16","День Независимости"],["2026-12-28","2027-01-10","Зимние каникулы"],["2027-03-08","2027-03-08","Международный женский день"],["2027-03-22","2027-03-28","Весенние каникулы"],["2027-05-01","2027-05-01","Праздник единства народа Казахстана"],["2027-05-03","2027-05-03","Выходной за 1 мая"],["2027-05-07","2027-05-07","День защитника Отечества"],["2027-05-09","2027-05-10","День Победы"],["2027-05-26","2027-08-31","Летние каникулы"]],"bells":[["8:00","8:45"],["8:50","9:35"],["9:45","10:30"],["10:50","11:35"],["11:55","12:40"],["12:50","13:35"],["13:45","14:30"],["14:40","15:25"],["15:35","16:25"]],"subjects":{"alg":"Алгебра","geom":"Геометрия","mat":"Математика","fiz":"Физика","prf":"Практ. физика","kaz":"Казахский яз. и лит.","rus":"Русский язык","rlit":"Русская литература","eng":"Английский язык","istk":"История Казахстана","vist":"Всемирная история","geo":"География","bio":"Биология","him":"Химия","inf":"Информатика","prog":"Программирование","fk":"Физкультура","hud":"Худ. труд","kl":"Классный час","prav":"Основы права","nvp":"НВП","avto":"Автодело"},"classes":{"7А":[["kaz 42/21","kl 40","alg 24/35","fk с/з","istk 46","prf 48/50","geo 54"],["fiz 48/49","fiz 48/49","geom 24/35","geom 24/35","eng 44/35","prog 56/57","bio 25"],["rus 46","fk с/з","alg 24/35","alg 24/35","rlit 46","eng 45/47"],["fiz 48/49","fiz 48/49","alg 24/35","mat 24/35","rus 46","vist 54","kaz 42/21","fk с/з"],["kaz 42/21","inf 56/57","geom 24/35","rlit 46","hud 11/1","him 40","eng 25/20"]],"7Б":[["kl 40","kaz 42/21","istk 46","alg 24/35","mat 24/35","fk с/з","fk с/з","prf 48/73"],["geom 24/35","eng 44/47","fiz 48/49","fiz 48/49","prog 56/57","bio 25","hud 11/1"],["alg 24/35","alg 24/35","eng 45/47","geo 54","fk с/з","rus 46"],["kaz 42/21","alg 24/35","rus 46","rlit 46","fiz 48/49","fiz 48/49","vist 54"],["geom 24/35","geom 24/35","kaz 42/21","eng 25/47","him 40","inf 56/57","rlit 46"]],"7В":[["alg 24/35","alg 24/35","kaz 42/21","istk 46","fk с/з","geo 54","prf 48/73","kl 40"],["hud 11/1","geom 24/35","eng 44/47","bio 25","fiz 48/50","fiz 48/50","prog 56/57"],["fk с/з","rus 46","rlit 46","eng 45/47","alg 24/35"],["rus 46","kaz 42/21","fiz 48/49","fiz 48/49","alg 24/35","mat 24/35","fk с/з","vist 54"],["inf 56/57","kaz 42/21","eng 25/47","geom 24/35","geom 24/35","rlit 46","him 40"]],"8А":[["fiz 48/50","kl 25","rus 55","kaz 42/21","istk 41","alg 22/24","alg 22/24","fk с/з"],["eng 47/44","bio 25","kaz 42/43","him 40","geom 22/24","geom 22/24"],["eng 47/45","rlit 55","vist 41","fk с/з","prog 56/57","alg 22/24","fiz 48/50","prf 48/50"],["alg 22/24","inf 56/57","rus 55","rlit 55","eng 47/40","fk с/з"],["fiz 48/50","fiz 48/50","hud 11/1","kaz 42/21","geo 54","geom 22/24","mat 22/24"]],"8Б":[["kl 25","fiz 48/50","fk с/з","rus 55","alg 58/22","kaz 42/21","rlit 55"],["bio 25","kaz 42/43","geom 58/22","eng 47/44","vist 41","hud 11/1","him 40"],["rlit 55","eng 47/45","alg 58/22","alg 58/22","fiz 48/50","fiz 48/50","fk с/з","prog 56/57"],["fk с/з","alg 58/22","mat 58/22","inf 56/57","rus 55","eng 47/40"],["geom 58/22","geom 58/22","istk 41","geo 54","fiz 48/50","prf 48/50","kaz 42/21"]],"8В":[["alg 58/22","alg 58/22","fiz 48/50","fiz 48/50","kaz 42/21","rus 45","kl 41"],["kaz 42/43","hud 11/1","bio 25","geom 58/22","rlit 45","him 40","eng 35/44"],["alg 58/22","alg 58/22","fiz 48/50","fiz 48/50","eng 47/45","fk с/з","prog 56/57"],["inf 56/57","vist 41","rus 45","fk с/з","fk с/з","mat 58/22"],["geo 54","eng 20/25","geom 58/22","geom 58/22","kaz 42/21","rlit 45","prf 48/50","istk 41"]],"8Г":[["rus 45","fk с/з","alg 58/22","mat 58/22","fiz 48/50","istk 41","kaz 42/21","kl 25"],["geom 58/22","geom 58/22","hud 11/1","kaz 42/43","bio 25","eng 35/44","rlit 45","him 40"],["fiz 48/50","fiz 48/50","fk с/з","vist 41","alg 58/22","prog 56/57","eng 47/45"],["rus 45","fk с/з","inf 56/57","alg 58/22","alg 58/22","rlit 45"],["eng 20/25","geo 54","fiz 48/50","prf 48/50","geom 58/22","kaz 42/21"]],"9А":[["alg 36/59","alg 36/59","istk 41","fiz 73/72","kl 40","kaz 44/43","rus 45","prog 56/57"],["fk с/з","inf 56/57","geom 36/59","fiz 73/72","fiz 73/72","rus 45","prav 41","eng 47/28"],["geo 54","him 40","kaz 44/43","alg 36/59","alg 36/59","fk с/з","bio 25"],["vist 41","rlit 45","kaz 44/43","eng 47/1","fiz 73/72","prf 73/72","mat 36/59"],["rlit 45","eng 47/40","fk с/з","hud 11/1","geom 36/59","geom 36/59"]],"9Б":[["kl 46","istk 41","fiz 73/72","rus 45","kaz 44/43","alg 36/59","prog 56/57"],["inf 56/57","fiz 73/72","fiz 73/72","fk с/з","eng 47/46","geom 36/59","geom 36/59","hud 11/1"],["kaz 44/43","alg 36/59","mat 36/59","him 40","geo 54","bio 25","fk с/з"],["alg 36/59","alg 36/59","eng 47/1","fiz 73/72","vist 41","prav 53","rlit 45"],["eng 47/40","rlit 45","kaz 44/43","geom 36/59","rus 45","fk с/з","prf 73/72"]],"9В":[["istk 41","kaz 44/43","alg 36/59","mat 36/59","fiz 73/72","fiz 73/72","prog 56/57","kl 46"],["fiz 73/72","fk с/з","inf 56/57","hud 11/1","geom 36/59","eng 47/46","rlit 46"],["alg 36/59","geo 54","him 40","kaz 44/43","bio 25","prav 28","rus 46","fk с/з"],["eng 47/35","fiz 73/72","prf 73/72","kaz 44/43","alg 36/59","alg 36/59","rlit 46"],["geom 36/59","geom 36/59","rus 46","vist 41","fk с/з","eng 47/28"]],"9Г":[["fiz 73/72","fiz 73/72","kaz 44/43","istk 41","alg 36/59","prog 56/57","kl 40"],["geom 36/59","geom 36/59","fk с/з","inf 56/57","hud 11/1","fiz 73/72","eng 47/28","rlit 46"],["him 40","kaz 44/43","geo 54","rus 46","fk с/з","alg 36/59","mat 36/59","bio 25"],["fiz 73/72","eng 47/1","alg 36/59","alg 36/59","kaz 44/43","vist 41","prav 41"],["rus 46","rlit 46","geom 36/59","fk с/з","eng 47/73","prf 73/72"]],"10А":[["inf 56/57","geo 54","alg 28/34","eng 47/20","bio 25","rlit 55","kl 28","prog 57"],["fk с/з","geom 28/34","geom 28/34","istk 41","kaz 42/43","rus 55","prf 50/72"],["fiz 73/72","fiz 73/72","nvp 1","alg 28/34","alg 28/34","prav 41","avto 71","avto 71"],["rlit 55","him 40","kaz 42/21","kaz 42/21","alg 28/34","mat 28/34","eng 20/50","fk с/з","prog 56"],["fk с/з","istk 41","fiz 73/72","fiz 73/72","geom 28/34","vist 41","inf 56/57"]],"10Б":[["rlit 55","eng 47/20","kl 25","bio 25","geo 54","alg 58/34","alg 58/34"],["istk 41","prav 54","fk с/з","rus 55","geom 58/34","geom 58/34","kaz 42/43","prf 50/72"],["kaz 42/21","kaz 42/21","fiz 73/72","fiz 73/72","him 40","alg 58/34","mat 58/34","nvp 1"],["alg 58/34","rlit 55","istk 41","vist 41","fk с/з","inf 56/57","prog 56/57"],["fiz 73/72","fiz 73/72","inf 56/57","eng 20/40","fk с/з","geom 58/34","avto 71","avto 71"]],"10В":[["alg 28/34","alg 28/34","eng 47/20","geo 54","rlit 55","kl 40","bio 25","prf 53/50"],["geom 28/34","istk 41","fiz 53/50","fiz 53/50","rus 55","kaz 42/43","fk с/з","inf 56/57"],["alg 28/34","vist 41","rlit 55","nvp 1","prav 41","him 40"],["fiz 53/50","fiz 53/50","alg 28/34","mat 28/34","kaz 42/21","kaz 42/21","fk с/з","prog 56/57"],["geom 28/34","geom 28/34","eng 20/40","inf 56/57","istk 41","fk с/з","avto 71","avto 71"]],"10Г":[["eng 47/20","rlit 55","geo 54","alg 28/34","mat 28/34","bio 25","prf 53/50","kl 54"],["fiz 53/50","fiz 53/50","istk 41","geom 28/34","fk с/з","fk с/з","rus 55","kaz 42/43"],["vist 41","alg 28/34","alg 28/34","kaz 42/21","kaz 42/21","nvp 1","avto 71","avto 71"],["him 40","alg 28/34","fiz 53/50","fiz 53/50","inf 56/57","eng 20/50","rlit 55","prog 56/57"],["istk 41","fk с/з","geom 28/34","geom 28/34","inf 56/57","prav 55"]],"11А":[["kl 43","inf 57/56","fiz 53/49","fiz 53/49","alg 30/27","mat 30/27","prav 46","kaz 44/43"],["istk 54","rus 45","geom 30/27","geom 30/27","fk с/з","fk с/з","prf 53/49","avto 71","avto 71"],["alg 30/27","alg 30/27","fiz 53/49","inf 57/56","nvp 1","geo 54","him 40"],["kaz 44/43","vist 54","eng 20/40","bio 25","rlit 45","alg 30/27"],["istk 55","fiz 53/49","geom 30/27","rlit 45","eng 20/25","kaz 44/43","fk с/з","prog 57/56"]],"11Б":[["kl 54","rlit 45","alg 30/27","alg 30/27","inf 57/56","fiz 53/49","kaz 44/43"],["him 40","fk с/з","rus 45","istk 54","geom 30/27","geom 30/27","prav 54","prf 53/49"],["nvp 1","inf 57/56","alg 30/27","mat 30/27","fiz 53/49","fiz 53/49","geo 54"],["alg 30/27","kaz 44/43","vist 54","eng 20/40","bio 25","fk с/з","avto 71","avto 71"],["fiz 53/49","kaz 44/43","fk с/з","geom 30/27","istk 55","eng 20/25","rlit 45","prog 57/56"]],"11В":[["fiz 53/49","fiz 53/49","inf 57/56","kaz 44/43","rlit 45","prav 46","alg 30/27","kl 28"],["rus 45","geom 30/27","him 40","fk с/з","istk 54","prf 53/49","avto 71","avto 71"],["inf 57/56","nvp 1","eng 25/20","fiz 53/49","kaz 44/43","alg 30/27","mat 30/27"],["eng 25/20","alg 30/27","alg 30/27","rlit 45","vist 54","bio 25"],["geom 30/27","geom 30/27","fiz 53/49","istk 55","kaz 44/43","geo 54","fk с/з","fk с/з","prog 57/56"]],"11Г":[["alg 30/27","alg 30/27","rlit 45","inf 57/56","fiz 53/49","kl 28","avto 71","avto 71"],["geom 30/27","him 40","istk 54","rus 45","prf 53/49","prav 54","fk с/з","fk с/з"],["fiz 53/49","fiz 53/49","inf 57/56","eng 25/20","alg 30/27","kaz 44/43","nvp 1"],["vist 54","eng 25/20","bio 25","alg 30/27","mat 30/27","kaz 44/43"],["kaz 44/43","istk 55","rlit 45","fiz 53/49","geom 30/27","geom 30/27","geo 54","fk с/з","prog 57/56"]]}}; // всё расписание: классы, звонки, каникулы, предметы
const COLORS = {"Алгебра":["007AFF","0A84FF"],"Геометрия":["007AFF","0A84FF"],"Математика":["007AFF","0A84FF"],"Физика":["AF52DE","BF5AF2"],"Практ. физика":["AF52DE","BF5AF2"],"Казахский яз. и лит.":["FFCC00","FFD60A"],"Русский язык":["FF3B30","FF453A"],"Русская литература":["FF3B30","FF453A"],"Английский язык":["32ADE6","64D2FF"],"История Казахстана":["A2845E","AC8E68"],"Всемирная история":["A2845E","AC8E68"],"География":["00C7BE","63E6E2"],"Биология":["34C759","30D158"],"Химия":["30B0C7","40CBE0"],"Информатика":["5856D6","5E5CE6"],"Программирование":["5856D6","5E5CE6"],"Физкультура":["FF9500","FF9F0A"],"Худ. труд":["FF2D55","FF375F"],"Классный час":["8E8E93","8E8E93"],"Основы права":["A2845E","AC8E68"],"НВП":["34C759","30D158"],"Автодело":["FF9500","FF9F0A"]}; // цвета предметов: для светлого и тёмного фона
const SITE = "https://danick-dack.github.io/Zvonok/";
// ---------- конец данных ----------

// ---------- расписание ----------
// Правила дня (каникулы, замены, сокращённые звонки, что сейчас) общие с сайтом: сборка вставляет их ниже
// из site/src/js/rules.js. Здесь только то, что этим правилам нужно от виджета: неделя класса и своё расписание.

const DAYN = ["Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"]; // с понедельника, как на сайте
const WHEN = ["в понедельник", "во вторник", "в среду", "в четверг", "в пятницу", "в субботу", "в воскресенье"];
const DSHORT = ["вс", "пн", "вт", "ср", "чт", "пт", "сб"]; // по getDay(): с воскресенья
const MONTHS = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"];
const SHORT = {"Казахский яз. и лит.": "Казахский", "Английский язык": "Английский", "Русский язык": "Русский", "Русская литература": "Литература",
  "История Казахстана": "Ист. Казахстана", "Всемирная история": "Всем. история", "Программирование": "Программир.", "Практ. физика": "Пр. физика"};
let CHANGES = {days: {}}; // замены с сайта

function hm(min) { const m = min % 60; return Math.floor(min / 60) + ":" + (m < 10 ? "0" : "") + m; }
function two(n) { return (n < 10 ? "0" : "") + n; }
function iso(d) { return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate()); }
function dm(d) { return d.getDate() + " " + MONTHS[d.getMonth()]; }
function dow(d) { return (d.getDay() + 6) % 7; } // 0 = понедельник
function at(day, min) { return new Date(day.getFullYear(), day.getMonth(), day.getDate(), 0, min, 0, 0); }
function toMin(t) { const m = /^\s*(\d{1,2})[:.](\d{2})\s*$/.exec(String(t || "")); if (!m) return -1; const H = +m[1], M = +m[2]; return H < 24 && M < 60 ? H * 60 + M : -1; }
function short(s) { return SHORT[s] || s; }
function word(n, one, few, many) { const a = n % 10, b = n % 100; return n + " " + (a === 1 && b !== 11 ? one : a >= 2 && a <= 4 && (b < 12 || b > 14) ? few : many); }
function plural(n) { return word(n, "урок", "урока", "уроков"); }
function clean(s) { return String(s == null ? "" : s).replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 40); }
function roomName(r) { return r === "с/з" ? "спортзал" : r || ""; }
const BELLS = DATA.bells.map(b => [toMin(b[0]), toMin(b[1])]);

// Настройки: класс и своё расписание выбираются на экране приложения (это сайт «Звонка» внутри Scriptable),
// виджеты берут их отсюда. Параметр виджета важнее: «10А» покажет 10А, «кабинеты» свободные кабинеты.
function settingsPath() { const fm = FileManager.local(); return fm.joinPath(fm.documentsDirectory(), "zvonok-settings.json"); }
function loadSettings() {
  try {
    const fm = FileManager.local(), p = settingsPath();
    if (fm.fileExists(p)) { const s = JSON.parse(fm.readString(p)); if (s && typeof s === "object") return s; }
  } catch (e) { /* испорченный файл: как при первом запуске */ }
  return START ? {useCustom: true, custom: START} : {};
}
function saveSettings(s) {
  const out = {cls: typeof s.cls === "string" && DATA.classes[s.cls] ? s.cls : null, useCustom: !!s.useCustom,
    custom: s.custom && typeof s.custom === "object" && Array.isArray(s.custom.days) ? s.custom : null};
  try { FileManager.local().writeString(settingsPath(), JSON.stringify(out)); } catch (e) { /* без доступа к файлам виджет берёт класс скрипта */ }
}
const SET = loadSettings();
const PARAM = typeof args !== "undefined" && args.widgetParameter ? String(args.widgetParameter).trim() : "";
const ROOMS_MODE = /^(каб|своб|rooms)/i.test(PARAM);
const PARAM_CLASS = (() => {
  const m = /^(7|8|9|10|11)\s*([abvgабвг])$/i.exec(PARAM), c = m && m[1] + {a: "А", b: "Б", v: "В", g: "Г", "а": "А", "б": "Б", "в": "В", "г": "Г"}[m[2].toLowerCase()];
  return c && DATA.classes[c] ? c : null;
})();
const CLS = PARAM_CLASS || (DATA.classes[SET.cls] ? SET.cls : DATA.classes[CLASS] ? CLASS : Object.keys(DATA.classes)[0]);

const CW = {};
/** Обычная неделя класса в формате правил: 7 дней с понедельника, уроки {num, start, end, subject, room}. */
function classWeek(c) {
  if (CW[c]) return CW[c];
  const days = DATA.classes[c] || [];
  return CW[c] = Array.from({length: 7}, (_, d) => (days[d] || []).map((raw, i) => {
    if (!raw || !BELLS[i] || BELLS[i][0] < 0) return null;
    const sp = raw.indexOf(" "), code = sp < 0 ? raw : raw.slice(0, sp), room = sp < 0 ? "" : raw.slice(sp + 1);
    return {num: i + 1, start: BELLS[i][0], end: BELLS[i][1], subject: DATA.subjects[code] || code, room: roomName(room)};
  }).filter(Boolean));
}
/** Своё расписание из конструктора сайта: {bells: [[с, по]], days: [[[предмет, кабинет]]]} (каникулы и замены его не трогают). */
function customWeek(c) {
  const bells = (Array.isArray(c.bells) ? c.bells : DATA.bells).map(b => [toMin(b && b[0]), toMin(b && b[1])]);
  return Array.from({length: 7}, (_, d) => ((c.days || [])[d] || []).map((l, i) => {
    if (!l || !clean(l[0]) || !bells[i] || bells[i][0] < 0 || bells[i][1] <= bells[i][0]) return null;
    return {num: i + 1, start: bells[i][0], end: bells[i][1], subject: clean(l[0]), room: roomName(clean(l[1]))};
  }).filter(Boolean));
}
const OWN = !PARAM_CLASS && SET.useCustom && SET.custom && Array.isArray(SET.custom.days) ? customWeek(SET.custom) : null;
/** Уроки на дату: свой класс с каникулами, заменами и сокращёнными звонками или своё расписание. */
function lessonsOn(d) { return OWN ? OWN[dow(d)] : dayLessons(CLS, d); }
const SRC = {at: lessonsOn, real: !OWN};
/** Коротко, когда следующие уроки: «завтра», «пн», «12.01». */
function nShort(s) { return !s.nDate ? "" : s.nDays === 1 ? "завтра" : s.nDays > 6 ? s.nDate.getDate() + "." + two(s.nDate.getMonth() + 1) : DSHORT[s.nDate.getDay()]; }

// ---------- правила дня (сборка вставляет сюда site/src/js/rules.js, править там) ----------
/* Звонок · правила дня: каникулы, замены, сокращённые звонки и что происходит в момент now.
   Один файл на сайт и на виджет iPhone. Сборка (tools/build_site.py) вставляет его в скрипт Scriptable
   между метками, поэтому правила у сайта и виджета не расходятся. Править только здесь.
   Только объявления функций: виджет передаёт их в свой экран через toString().
   Снаружи нужны: DATA {holidays, subjects, term_end}, CHANGES, BELLS, classWeek(c), iso, dow, toMin, clean,
   roomName, hm, dm, at, word, DAYN и WHEN (с понедельника). */

/** Замены с сайта: оставляем только то, что правила умеют читать. Кривое поле в changes.json выпадает,
 *  а не роняет расписание у всех (на сайте и в виджете одинаково). */
function cleanChanges(j) {
  const out = {days: {}}, str = v => typeof v === "string", obj = v => !!v && typeof v === "object" && !Array.isArray(v);
  if (!obj(j)) return out;
  if (str(j.updated)) out.updated = j.updated;
  const days = obj(j.days) ? j.days : {};
  for (const date of Object.keys(days)) {
    const c = days[date];
    if (!/^\d{4}-\d\d-\d\d$/.test(date) || !obj(c)) continue;
    const day = {};
    if (str(c.note)) day.note = c.note;
    if (str(c.off) && c.off.trim()) day.off = c.off;
    else if (c.off === true) day.off = "Уроков нет"; // off: true тоже значит «уроков нет», пустая строка нет
    // пустой список звонков считаем ошибкой правки: звонки в этот день обычные
    if (Array.isArray(c.bells) && c.bells.length) day.bells = c.bells.map(b => Array.isArray(b) && str(b[0]) && str(b[1]) ? [b[0], b[1]] : null);
    if (obj(c.classes)) {
      day.classes = {};
      for (const cls of Object.keys(c.classes)) {
        const fix = c.classes[cls], f = {};
        if (!obj(fix)) continue;
        for (const n of Object.keys(fix)) {
          const v = fix[n];
          if (!/^\d{1,2}$/.test(n)) continue;
          if (v === null || str(v)) f[n] = v;
          else if (obj(v)) f[n] = {subject: str(v.subject) ? v.subject : undefined, room: str(v.room) || typeof v.room === "number" ? String(v.room) : undefined};
        }
        day.classes[cls] = f;
      }
    }
    out.days[date] = day;
  }
  return out;
}
/** Каникулы и праздники: [с, по, название]. */
function holiday(d) { const t = iso(d); return (DATA.holidays || []).find(x => t >= x[0] && t <= x[1]) || null; }
/** Замены на дату: data/changes.json (копия на момент сборки, свежая подтягивается с сайта). */
function chDay(d) { return (CHANGES.days || {})[iso(d)] || null; }
/** null, «нет» или «нет урока» в заменах: урока нет. */
function noLesson(v) { return v === null || (typeof v === "string" && /^нет/i.test(v.trim())); }
/** День без уроков у всех: каникулы, праздник или «off» в заменах. [с, по, название] */
function dayOff(d) { const x = holiday(d), c = chDay(d); return x || (c && c.off ? [iso(d), iso(d), clean(c.off)] : null); }
/** Звонки на дату: свои на день целиком из замен или обычные. Урока без звонка в этот день нет. */
function dayBells(d) {
  const c = chDay(d);
  if (!c || !Array.isArray(c.bells)) return BELLS;
  return c.bells.map((x, i) => { const s = x ? toMin(x[0]) : -1, e = x ? toMin(x[1]) : -1; return s >= 0 && e > s ? [s, e] : BELLS[i] || null; });
}
/** Уроки класса на конкретную дату: с каникулами, заменами и сокращёнными звонками. */
function dayLessons(c, d) {
  if (dayOff(d)) return [];
  const base = classWeek(c)[dow(d)], ch = chDay(d);
  if (!ch) return base;
  const bells = dayBells(d), fix = (ch.classes || {})[c] || {}, short = bells !== BELLS;
  const nums = [...new Set(base.map(l => l.num).concat(Object.keys(fix).map(Number).filter(n => n > 0)))].sort((a, b) => a - b);
  return nums.map(n => {
    let l = base.find(x => x.num === n);
    const f = fix[n];
    if (f !== undefined && noLesson(f)) return null;
    if (f && typeof f === "object") l = {num: n, changed: true,
      subject: f.subject ? DATA.subjects[f.subject] || clean(f.subject) : l ? l.subject : "",
      room: f.room != null ? roomName(clean(f.room)) : l ? l.room : ""};
    if (!l || !l.subject || !bells[n - 1]) return null;
    return Object.assign({}, l, {start: bells[n - 1][0], end: bells[n - 1][1], short});
  }).filter(Boolean);
}

/**
 * Что происходит в момент now. src = {at(date) → уроки на дату, real: учитывать каникулы}.
 * kind: LESSON (идёт урок), BREAK (перемена), BEFORE (до первого урока), AFTER (уроки кончились), OFF (уроков нет).
 */
function compute(now, src) {
  const s = {}, list = src.at(now), sec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
  if (list.length) {
    const first = list[0], last = list[list.length - 1];
    s.count = list.length; s.end = hm(last.end); s.list = list;
    if (sec < first.start * 60) {
      return Object.assign(s, {kind: "BEFORE", next: first, start: at(now, first.start - 60), target: at(now, first.start), soon: sec >= (first.start - 60) * 60});
    }
    for (let i = 0; i < list.length; i++) {
      const l = list[i];
      if (sec < l.end * 60) {
        if (sec >= l.start * 60) Object.assign(s, {kind: "LESSON", cur: l, idx: i, next: list[i + 1] || null, start: at(now, l.start), target: at(now, l.end)});
        else Object.assign(s, {kind: "BREAK", cur: list[i - 1], idx: i - 1, next: l, start: at(now, list[i - 1].end), target: at(now, l.start)});
        return s;
      }
    }
    s.kind = "AFTER";
  } else { s.kind = "OFF"; s.holiday = src.real ? dayOff(now) : null; }
  for (let d = 1; d <= 120; d++) {
    const date = new Date(now); date.setDate(date.getDate() + d);
    const l = src.at(date);
    if (!l.length) continue;
    const far = d > 6;
    Object.assign(s, {next: l[0], nList: l, nDate: date, nDays: d, nCount: l.length, nEnd: hm(l[l.length - 1].end),
      nLabel: d === 1 ? "Завтра" : far ? dm(date) : DAYN[dow(date)], nWhen: d === 1 ? "завтра" : far ? dm(date) : WHEN[dow(date)], target: at(date, l[0].start)});
    break;
  }
  return s;
}
/** Сколько учебных дней до ближайших каникул (сегодня считается, если уроки ещё не кончились). */
function vacation(now, src) {
  if (!src.real || holiday(now) || (DATA.term_end && iso(now) > DATA.term_end)) return null;
  const t = iso(now), v = (DATA.holidays || []).filter(x => /каникул/i.test(x[2]) && x[0] > t).sort((a, b) => a[0] < b[0] ? -1 : 1)[0];
  if (!v) return null;
  const d = new Date(now), nowMin = now.getHours() * 60 + now.getMinutes();
  let n = 0, today = false;
  for (let i = 0; i < 400 && iso(d) < v[0]; i++, d.setDate(d.getDate() + 1)) {
    const l = src.at(d);
    if (!l.length || (i === 0 && nowMin >= l[l.length - 1].end)) continue;
    n++; if (i === 0) today = true;
  }
  return n > 60 ? null : {n, today, name: v[2], from: v[0]}; // больше 60 дней: рано считать
}
function vacationText(v) {
  if (!v) return "";
  if (!v.n) return "Дальше " + v.name.toLowerCase();
  if (v.n === 1 && v.today) return "Сегодня последний учебный день, потом " + v.name.toLowerCase();
  return "До каникул " + word(v.n, "учебный день", "учебных дня", "учебных дней");
}
// ---------- конец правил дня ----------

// всё, что показывают виджеты и экран, в одном месте
function model(now) {
  const s = compute(now, SRC), n = s.next, c = s.cur;
  const m = {s, kind: s.kind, timer: false, target: s.target, progress: -1, big: "", bigSub: "", capW: "", capV: "", label: "", tag: "",
    title: "", room: "", changed: false, nextHead: "", nextSubj: "", nextRoom: "", nextChanged: false, home: false, go: "", hol: false, days: 0};
  const pct = () => Math.max(0, Math.min(1, (now - s.start) / (s.target - s.start)));
  if (s.kind === "LESSON") {
    const last = !n;
    Object.assign(m, {timer: true, progress: pct(), label: last ? "ПОСЛЕДНИЙ УРОК" : "УРОК " + (s.idx + 1) + " ИЗ " + s.count, title: c.subject,
      room: c.room, changed: c.changed, capW: "звонок", capV: hm(c.end), when: "до " + hm(c.end), home: last});
    m.tag = m.label;
    if (n) Object.assign(m, {nextHead: "ДАЛЬШЕ · " + hm(n.start), nextSubj: n.subject, nextRoom: n.room, nextChanged: n.changed, go: n.room});
    else Object.assign(m, {nextHead: "ПОТОМ", nextSubj: "Домой в " + hm(c.end)});
  } else if (s.kind === "BREAK") {
    const len = n.start - c.end, win = len > 25;
    Object.assign(m, {timer: true, progress: pct(), label: (win ? "ОКНО · " : "ПЕРЕМЕНА · ") + len + " МИН", tag: win ? "ОКНО" : "ПЕРЕМЕНА",
      title: win ? "Окно" : "Перемена", room: n.room, changed: n.changed, capW: "урок в", capV: hm(n.start), when: "до " + hm(n.start),
      nextHead: "ИДИ В", nextSubj: n.subject, nextRoom: n.room, nextChanged: n.changed, go: n.room});
  } else if (s.kind === "BEFORE") {
    Object.assign(m, {nextHead: "ПЕРВЫЙ · " + hm(n.start), nextSubj: n.subject, nextRoom: n.room, nextChanged: n.changed, go: n.room, room: n.room});
    if (s.soon) Object.assign(m, {timer: true, progress: pct(), label: "СКОРО УРОКИ", tag: "СКОРО УРОКИ", title: "До уроков", capW: "уроки в", capV: hm(n.start), when: "до " + hm(n.start)});
    else Object.assign(m, {label: "СЕГОДНЯ " + plural(s.count).toUpperCase(), tag: "СЕГОДНЯ", title: "Уроки с " + hm(n.start),
      big: hm(n.start), bigSub: "сегодня", capW: "до", capV: s.end});
  } else {
    const h = s.holiday, vac = !!h && /каникул/i.test(h[2]);
    m.hol = !!h && (!n || s.nDays > 1); // однодневный праздник выглядит как обычный вечер перед уроками
    m.label = s.kind === "AFTER" ? "УРОКИ ЗАКОНЧИЛИСЬ" : h ? h[2].toUpperCase() : "ВЫХОДНОЙ";
    m.tag = s.kind === "AFTER" ? (n ? s.nLabel.toUpperCase() : "ВСЁ") : vac ? "КАНИКУЛЫ" : "ВЫХОДНОЙ";
    m.title = s.kind === "AFTER" ? "Отдыхай" : h ? (vac ? "Каникулы" : "Выходной") : "Отдыхай";
    if (n) {
      Object.assign(m, {nextSubj: n.subject, nextRoom: n.room, nextChanged: n.changed, go: n.room});
      if (m.hol) {
        m.days = s.nDays;
        Object.assign(m, {big: String(s.nDays), bigSub: word(s.nDays, "день", "дня", "дней").replace(/^\d+ /, ""),
          capW: word(s.nDays, "день", "дня", "дней").replace(/^\d+ /, "") + " до уроков", capV: dm(s.nDate), nextHead: dm(s.nDate).toUpperCase() + " · " + hm(n.start)});
      } else Object.assign(m, {big: hm(n.start), bigSub: nShort(s), capW: "до", capV: s.nEnd, nextHead: s.nLabel.toUpperCase() + " · " + plural(s.nCount).toUpperCase()});
    } else m.big = "нет";
  }
  // подпись под таймером на экране в приложении
  m.ringSub = m.kind === "LESSON" ? "звонок в " + m.capV : m.timer ? m.capW + " " + m.capV : m.hol ? m.capW : m.kind === "BEFORE" ? "первый звонок" : s.next ? s.nWhen : "";
  return m;
}

// Когда iOS стоит перерисовать виджет (это только подсказка, iOS может опоздать)
function nextRefresh(m, now) {
  const s = m.s, soon = new Date(now.getTime() + 10 * 60000);
  if (s.kind === "LESSON" || s.kind === "BREAK") return new Date(Math.min(s.target.getTime() + 1000, soon.getTime()));
  if (s.kind === "BEFORE") return s.soon ? new Date(s.target.getTime() + 1000) : at(now, s.next.start - 60);
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 5);
}

// ---------- сеть ----------
// Радио телефона дорого будить, поэтому в сеть ходим редко и одним заходом. Замены с сайта берём не чаще
// раза в 3 часа, и в тот же заход (если с прошлой проверки прошло 6 часов) спрашиваем, нет ли новой версии:
// оба запроса идут одновременно. Если связь плохая и запрос долго висел, следующая попытка через 30 минут,
// а не на каждой перерисовке виджета. Когда сети нет совсем, запрос падает сразу и почти ничего не стоит:
// тогда пробуем при каждой перерисовке, как раньше, чтобы замены пришли, как только появится интернет.
const NET = {changes: false, update: false, st: {}, fm: null, stamp: ""};
function saveNet() { try { NET.fm.writeString(NET.stamp, JSON.stringify(NET.st)); } catch (e) {} }
function netPlan() {
  const now = Date.now();
  try {
    const fm = NET.fm = FileManager.local(), dir = fm.documentsDirectory(), saved = fm.joinPath(dir, "zvonok-changes.json");
    NET.stamp = fm.joinPath(dir, "zvonok-update.json");
    try { if (fm.fileExists(NET.stamp)) NET.st = JSON.parse(fm.readString(NET.stamp)) || {}; } catch (e) {}
    // Math.abs: если часы на телефоне перевели назад, не ждём, пока время «догонит» отметку
    const age = fm.fileExists(saved) ? Math.abs(now - fm.modificationDate(saved).getTime()) : Infinity;
    NET.changes = age > 3 * 3600000 && Math.abs(now - (NET.st.slow || 0)) > 30 * 60000;
    NET.update = NET.changes && AUTO_UPDATE && !!VERSION && typeof module !== "undefined" && Math.abs(now - (NET.st.t || 0)) > 6 * 3600000;
    if (NET.update) { NET.st.t = now; saveNet(); }
  } catch (e) { /* без доступа к файлам в сеть не ходим: иначе ходили бы при каждой перерисовке */ }
}

// замены: сохранённые с прошлого раза; свежие с сайта, только если netPlan сказал, что пора
async function loadChanges(fetch) {
  let fm = null, path = "", data = null;
  try {
    fm = FileManager.local(); path = fm.joinPath(fm.documentsDirectory(), "zvonok-changes.json");
    if (fm.fileExists(path)) data = JSON.parse(fm.readString(path));
  } catch (e) { data = null; }
  if (fetch) {
    const t0 = Date.now();
    try {
      const r = new Request(SITE + "changes.json");
      r.timeoutInterval = 4;
      const j = await r.loadJSON();
      if (j && typeof j.days === "object") { data = j; if (fm) fm.writeString(path, JSON.stringify(j)); }
    } catch (e) {
      // работаем с тем, что есть; запрос висел долго (плохая связь): радио не мучаем 30 минут
      if (Math.abs(Date.now() - t0) > 1500) { NET.st.slow = Date.now(); saveNet(); }
    }
  }
  return data && typeof data.days === "object" ? data : {days: {}};
}

// ---------- оформление ----------

// Код темы с сайта (make.html). op, r и s (непрозрачность, углы, секунды) нужны только Android:
// фон, скругление и тикающий таймер виджета на iPhone рисует сама iOS, их не настроить.
function parseTheme(code) {
  const t = {bg: "000000", ac: "FFFFFF", p: 1, n: 1, d: 1, sc: 0, tx: ""};
  code = String(code || "").trim();
  // своей темы нет: фирменный вид, кольцо и кабинет в цвете урока (в своей теме цвет урока как выбрали на сайте)
  if (!code.startsWith("ZV1") || code.length > 200) { t.sc = 1; return t; }
  for (const part of code.split(";")) {
    const k = part.split("=")[0].trim(), v = (part.split("=")[1] || "").trim();
    if ((k === "bg" || k === "ac" || k === "tx") && /^[0-9a-fA-F]{6}$/.test(v)) t[k] = v.toUpperCase();
    else if (k === "sc") t.sc = v === "1" ? 1 : 0;
    else if (k === "p" || k === "n" || k === "d") t[k] = v === "0" ? 0 : 1;
  }
  return t;
}
function lum(hex) { const n = parseInt(hex, 16); return (0.2126 * (n >> 16 & 255) + 0.7152 * (n >> 8 & 255) + 0.0722 * (n & 255)) / 255; }
function mix(a, b, t) {
  const x = parseInt(a, 16), y = parseInt(b, 16), ch = s => Math.round((x >> s & 255) * (1 - t) + (y >> s & 255) * t);
  return [16, 8, 0].map(s => ch(s).toString(16).padStart(2, "0")).join("").toUpperCase();
}
const TH = parseTheme(PARAM.startsWith("ZV1") ? PARAM : THEME);
const LIGHT = lum(TH.bg) > 0.5;
const FG = TH.tx || (LIGHT ? "111111" : "FFFFFF");
let ACC = Math.abs(lum(TH.ac) - lum(TH.bg)) < 0.25 ? FG : TH.ac; // при «цвете урока» меняется на цвет предмета
let TINT = ""; // цвет урока для мягкого свечения фона сверху (только в учебный день, при «цвете урока»)
const c = a => new Color("#" + FG, a);
/** Цвет предмета под фон виджета или null (без «цвета урока» или неизвестный предмет). */
function subjColor(subject, a) { const x = TH.sc && COLORS[subject]; return x ? new Color("#" + x[LIGHT ? 0 : 1], a) : null; }
const acc = a => new Color("#" + ACC, a);
const WHITE = a => new Color("#FFFFFF", a);

function background(w) {
  const g = new LinearGradient();
  const bg = TH.bg, glow = (x, k) => TINT ? mix(x, TINT, k) : x; // сверху мягкий цвет урока, как на сайте и в Android
  if (bg === "000000") { g.colors = [glow("1C1C21", 0.26), "0A0A0C", "000000"].map(x => new Color("#" + x)); g.locations = [0, 0.55, 1]; }
  else if (LIGHT) { g.colors = [glow(mix(bg, "FFFFFF", 0.55), 0.16), bg].map(x => new Color("#" + x)); g.locations = [0, 1]; }
  else { g.colors = [glow(mix(bg, "FFFFFF", 0.10), 0.24), bg, mix(bg, "000000", 0.35)].map(x => new Color("#" + x)); g.locations = [0, 0.6, 1]; }
  g.startPoint = new Point(0, 0); g.endPoint = new Point(0, 1);
  w.backgroundGradient = g;
}

// текст: на экране блокировки без цвета (iOS красит сам), иерархия только прозрачностью
function T(p, s, font, a, color, scale) {
  const t = p.addText(String(s));
  t.font = font;
  if (color) t.textColor = color;
  if (a != null && a < 1) t.textOpacity = a;
  t.lineLimit = 1;
  t.minimumScaleFactor = scale || 0.75;
  return t;
}
function timer(p, date, font, a, color, align) {
  const t = p.addDate(date);
  t.applyTimerStyle();
  t.font = font;
  if (color) t.textColor = color;
  if (a != null && a < 1) t.textOpacity = a;
  t.lineLimit = 1;
  t.minimumScaleFactor = 0.6;
  if (align === "c") t.centerAlignText(); else if (align === "r") t.rightAlignText();
  return t;
}
function row(p) { const r = p.addStack(); r.layoutHorizontally(); r.centerAlignContent(); return r; }
function fixed(p, w, h) { const s = p.addStack(); s.size = new Size(w, h || 0); return s; }
// строка по центру (внутри кольца)
function centered(p, fn) { const r = row(p); r.addSpacer(); fn(r); r.addSpacer(); return r; }
function rightCol(p, fn) { const r = row(p); r.addSpacer(); fn(r); return r; }

// кольцо прогресса: дорожка и дуга с круглыми концами
function ring(size, line, value, track, arc) {
  const ctx = new DrawContext();
  ctx.size = new Size(size, size); ctx.opaque = false; ctx.respectScreenScale = true;
  const cc = size / 2, r = cc - line / 2 - 0.5;
  ctx.setLineWidth(line);
  ctx.setStrokeColor(track);
  ctx.strokeEllipse(new Rect(cc - r, cc - r, 2 * r, 2 * r));
  if (value >= 0) {
    const v = Math.max(0.03, Math.min(1, value)), n = Math.max(2, Math.ceil(v * 120)), p = new Path();
    for (let i = 0; i <= n; i++) {
      const a = -Math.PI / 2 + 2 * Math.PI * v * i / n, pt = new Point(cc + r * Math.cos(a), cc + r * Math.sin(a));
      if (i) p.addLine(pt); else p.move(pt);
    }
    ctx.addPath(p); ctx.setStrokeColor(arc); ctx.strokePath();
    ctx.setFillColor(arc);
    for (const a of [-Math.PI / 2, -Math.PI / 2 + 2 * Math.PI * v])
      ctx.fillEllipse(new Rect(cc + r * Math.cos(a) - line / 2, cc + r * Math.sin(a) - line / 2, line, line));
  }
  return ctx.getImage();
}
function ringBox(p, D, L, v, track, arc) {
  const b = p.addStack();
  b.size = new Size(D, D); b.layoutVertically(); b.centerAlignContent();
  b.backgroundImage = ring(D, L, v, track, arc);
  return b;
}
function bar(p, W, H, v, track, fill) {
  const ctx = new DrawContext();
  ctx.size = new Size(W, H); ctx.opaque = false; ctx.respectScreenScale = true;
  const path = (w) => { const q = new Path(); q.addRoundedRect(new Rect(0, 0, w, H), H / 2, H / 2); return q; };
  ctx.addPath(path(W)); ctx.setFillColor(track); ctx.fillPath();
  ctx.addPath(path(Math.max(H, W * Math.max(0, Math.min(1, v))))); ctx.setFillColor(fill); ctx.fillPath();
  const img = p.addImage(ctx.getImage());
  img.imageSize = new Size(W, H);
  return img;
}
function line(p, W, color) {
  const ctx = new DrawContext();
  ctx.size = new Size(W, 1); ctx.opaque = false; ctx.respectScreenScale = true;
  ctx.setFillColor(color); ctx.fillRect(new Rect(0, 0, W, 1));
  const img = p.addImage(ctx.getImage());
  img.imageSize = new Size(W, 1);
}
// точки дня: прошедшие, текущий урок шире, будущие
function dots(p, m) {
  const s = m.s;
  if (!TH.d || !s.list || !(s.kind === "LESSON" || s.kind === "BREAK" || s.kind === "BEFORE")) return;
  // при 7 и больше уроках капсулы короче, чтобы влезали рядом с ярлыком
  const many = s.list.length > 6, st = p.addStack(); st.spacing = many ? 2 : 3; st.centerAlignContent();
  s.list.forEach((l, i) => {
    const cur = s.kind === "LESSON" && i === s.idx, done = s.idx != null && (i < s.idx || (s.kind === "BREAK" && i === s.idx));
    const d = st.addStack(); d.size = new Size(cur ? (many ? 10 : 14) : (many ? 6 : 8), 4); d.cornerRadius = 2;
    d.backgroundColor = cur ? acc(1) : done ? c(0.45) : c(0.16);
  });
}
function chip(p, text, size) {
  const ch = p.addStack();
  ch.cornerRadius = 7; ch.setPadding(2, 7, 2, 7); ch.backgroundColor = acc(0.22);
  T(ch, text, Font.boldRoundedSystemFont(size), 1, acc(1), 0.7);
  return ch;
}
function symbol(p, name, size, a, color) {
  const s = SFSymbol.named(name);
  if (!s) return null;
  const img = p.addImage(s.image);
  img.imageSize = new Size(size, size);
  if (color) img.tintColor = color;
  if (a != null && a < 1) img.imageOpacity = a;
  return img;
}
const room0 = r => r === "спортзал" ? "с/з" : r;
function hasLessonToday(m) { return m.kind === "LESSON" || m.kind === "BREAK" || m.kind === "BEFORE"; }

// ---------- экран блокировки ----------

function lockRect(m) {
  const w = new ListWidget();
  w.setPadding(0, 0, 0, 0);
  const s = m.s, n = s.next;
  // ряд 1
  const r1 = row(w);
  if (m.changed && (m.kind === "LESSON" || m.kind === "BREAK")) { if (symbol(r1, "arrow.triangle.2.circlepath", 11)) r1.addSpacer(3); } // нет значка: нет и отступа
  if (m.kind === "LESSON") {
    const right = m.home ? "домой" : "→ " + m.go; // «домой» словом, только если нет значка домика
    T(r1, short(m.title), Font.semiboldSystemFont(13), 1);
    if (m.room && short(m.title).length + m.room.length + (right.length || 2) <= 19) { r1.addSpacer(5); T(r1, m.room, Font.mediumSystemFont(13), 0.6); }
    r1.addSpacer();
    if (!m.home || !symbol(r1, "house.fill", 12, 0.7)) T(r1, right, Font.semiboldSystemFont(12), 0.7); // без значка домика текстом
  } else if (m.kind === "BREAK" || (m.kind === "BEFORE" && s.soon)) {
    T(r1, "→ " + short(n.subject), Font.semiboldSystemFont(13), 1);
    r1.addSpacer();
    if (n.room) {
      const pl = r1.addStack(); pl.backgroundColor = WHITE(0.22); pl.cornerRadius = 5; pl.setPadding(1, 5, 1, 5);
      T(pl, n.room, Font.boldRoundedSystemFont(12), 1);
    }
  } else if (m.kind === "BEFORE") {
    T(r1, m.title, Font.semiboldSystemFont(13), 1);
  } else {
    T(r1, m.hol ? m.s.holiday[2] : n ? s.nLabel + " · " + plural(s.nCount) : m.title, Font.semiboldSystemFont(13), 1);
  }
  w.addSpacer(1);
  // ряд 2: таймер и время звонка (опоздавшая перерисовка не обманет)
  const r2 = row(w);
  // ширины колонок заданы, иначе таймер iOS растягивается и сжимает подпись справа
  const lw = m.hol ? 52 : 100, left = fixed(r2, lw);
  if (m.timer) timer(left, m.target, Font.boldRoundedSystemFont(32), 1);
  else T(left, m.big, Font.boldRoundedSystemFont(32), 1, null, 0.6);
  r2.addSpacer();
  const col = fixed(r2, 154 - lw); col.layoutVertically();
  rightCol(col, x => T(x, m.capW, Font.mediumSystemFont(11), 0.6));
  rightCol(col, x => T(x, m.capV, Font.boldRoundedSystemFont(m.hol ? 13 : 17), 1));
  w.addSpacer(4);
  // ряд 3
  if (m.kind === "LESSON" || m.kind === "BREAK") {
    if (TH.p) bar(w, 156, 5, m.progress, WHITE(0.28), WHITE(1));
    else if (m.kind === "LESSON" && n) T(w, "Дальше: " + short(n.subject) + (n.room ? " · " + n.room : ""), Font.mediumSystemFont(12), 0.7);
  } else if (m.kind === "BEFORE" && s.soon) {
    T(w, "Сегодня " + plural(s.count) + " · до " + s.end, Font.mediumSystemFont(12), 0.7);
  } else if (n) {
    T(w, "→ " + short(n.subject) + (n.room ? " · " + n.room : ""), Font.mediumSystemFont(12), 0.7);
  }
  return w;
}

function lockCircle(m) {
  const w = new ListWidget();
  w.addAccessoryWidgetBackground = true;
  w.setPadding(0, 0, 0, 0);
  w.backgroundImage = ring(72, 5, m.timer ? m.progress : -1, WHITE(0.28), WHITE(1));
  w.addSpacer();
  if (m.hol && m.days) {
    const mon = new Date().getMonth();
    centered(w, r => symbol(r, mon === 11 || mon <= 1 ? "snowflake" : "sun.max.fill", 12));
    centered(w, r => T(r, m.big, Font.boldRoundedSystemFont(20), 1));
    centered(w, r => T(r, m.bigSub, Font.mediumSystemFont(10), 0.65));
  } else {
    centered(w, r => T(r, m.timer ? m.capV : m.bigSub || "", m.timer ? Font.semiboldRoundedSystemFont(10) : Font.semiboldSystemFont(10), 0.65));
    if (m.timer) { const t = timer(w, m.target, Font.boldRoundedSystemFont(16), 1, null, "c"); t.minimumScaleFactor = 0.55; }
    else centered(w, r => T(r, m.big, Font.boldRoundedSystemFont(17), 1, null, 0.55));
    centered(w, r => {
      if (m.home) { if (!symbol(r, "house.fill", 10, 0.85)) T(r, "домой", Font.semiboldRoundedSystemFont(11), 0.85, null, 0.6); }
      else if (m.go) T(r, (m.kind === "LESSON" ? "→" : "") + room0(m.go), Font.semiboldRoundedSystemFont(11), 0.85, null, 0.6);
    });
  }
  w.addSpacer();
  return w;
}

function inlineText(m) {
  const s = m.s, n = s.next, sub = l => "Замена: " + short(l.subject) + (l.room ? " · " + l.room : "");
  if (m.kind === "LESSON") return m.changed ? sub(s.cur) : m.home ? "До " + m.capV + ", потом домой" : "До " + m.capV + " → " + m.go;
  if (m.kind === "BREAK") return n.changed ? sub(n) : "Урок в " + hm(n.start) + (n.room ? " · " + n.room : "");
  if (m.kind === "BEFORE") return n.changed ? sub(n) : "Уроки в " + hm(n.start) + (n.room ? " · " + n.room : "");
  if (!n) return m.title;
  if (m.hol) { const e = new Date(s.holiday[1] + "T12:00:00"); return (m.title === "Каникулы" ? "Каникулы" : "Выходные") + " до " + e.getDate() + " " + MONTHS[e.getMonth()].slice(0, 3); }
  return (s.nDays === 1 ? "Завтра" : s.nDays > 6 ? dm(s.nDate) : "В " + DSHORT[s.nDate.getDay()]) + " в " + hm(n.start);
}
function lockInline(m) {
  const w = new ListWidget();
  w.addText(inlineText(m));
  return w;
}

// ---------- рабочий стол ----------

// центр кольца: таймер и время звонка, или крупное время / число дней
function ringCenter(b, m, big, small) {
  b.addSpacer();
  if (m.timer) {
    timer(b, m.target, Font.semiboldRoundedSystemFont(big), 1, c(1), "c");
    b.addSpacer(1);
    centered(b, r => T(r, m.when, Font.mediumRoundedSystemFont(small), 0.55, c(1)));
  } else {
    centered(b, r => T(r, m.big, Font.semiboldRoundedSystemFont(m.hol ? big * 1.25 : big + 1), 1, c(1), 0.5));
    if (m.bigSub) centered(b, r => T(r, m.bigSub, Font.mediumRoundedSystemFont(small), 0.55, c(1)));
  }
  b.addSpacer();
}
function headTitle(m) {
  if (m.kind === "BREAK" || (m.kind === "BEFORE" && m.s.soon)) return m.nextSubj;
  return m.title;
}

function small(m) {
  const w = new ListWidget();
  background(w);
  w.setPadding(14, 14, 14, 14);
  // кольцо слева, справа ярлык и кабинет; предмет отдельной строкой на всю ширину (рядом с кольцом ему мало места)
  const r2 = row(w);
  const b = ringBox(r2, 80, 8, m.timer ? m.progress : -1, c(0.16), acc(1));
  ringCenter(b, m, 20, 10);
  r2.addSpacer(8);
  const col = r2.addStack(); col.layoutVertically(); col.size = new Size(0, 80);
  T(col, m.tag.replace(" ИЗ ", "/").replace("ПОСЛЕДНИЙ УРОК", "ПОСЛЕДНИЙ"), Font.semiboldSystemFont(10), 0.42, c(1), 0.6);
  col.addSpacer();
  if (m.room && hasLessonToday(m)) chip(col, m.room, 15);
  if (m.changed && hasLessonToday(m)) { col.addSpacer(3); T(col, "замена", Font.semiboldSystemFont(10), 0.62, c(1)); }
  w.addSpacer(8);
  T(w, short(headTitle(m)), Font.semiboldSystemFont(16), 1, c(1), 0.7);
  w.addSpacer();
  if (TH.n) {
    const r3 = row(w), n = m.s.next, [, subj, rm] = bottomLine(m);
    let text = "", room = "";
    if (m.kind === "LESSON" && m.home) text = "Домой в " + m.capV;
    else if (hasLessonToday(m)) { text = short(subj); room = rm; }
    else if (n) { text = (m.hol ? m.s.nDate.getDate() + " " + MONTHS[m.s.nDate.getMonth()].slice(0, 3) + " · " : hm(n.start) + " ") + short(n.subject); room = m.hol ? "" : n.room; }
    if (text) {
      if (hasLessonToday(m) && !(m.kind === "BEFORE" && m.s.soon)) { T(r3, "→", Font.mediumSystemFont(12), 0.42, c(1)); r3.addSpacer(4); }
      T(r3, text, Font.mediumSystemFont(12), 0.62, c(1), 0.7);
      r3.addSpacer();
      if (room) T(r3, room, Font.semiboldRoundedSystemFont(12), 0.62, c(1));
    }
  }
  return w;
}

// правая часть medium и large: ярлык, заголовок с кабинетом, «дальше»
function sideInfo(col, m, opt) {
  const r1 = row(col);
  T(r1, m.label, Font.semiboldSystemFont(11), 0.42, c(1), 0.6);
  r1.addSpacer(6);
  dots(r1, m);
  col.addSpacer(4);
  T(col, headTitle(m), Font.semiboldSystemFont(opt.title), 1, c(1), 0.7);
  if ((m.room || m.changed) && hasLessonToday(m)) {
    col.addSpacer(4);
    const r2 = row(col);
    if (m.room) chip(r2, m.room, 14);
    if (m.changed) { r2.addSpacer(6); T(r2, "ЗАМЕНА", Font.semiboldSystemFont(10), 0.62, c(1)); }
  }
  col.addSpacer();
  if (!TH.n) return;
  if (opt.rule) { line(col, 190, c(0.10)); col.addSpacer(8); }
  const [head, subj, rm] = bottomLine(m);
  T(col, head, Font.semiboldSystemFont(11), 0.42, c(1), 0.7);
  col.addSpacer(2);
  const r3 = row(col);
  T(r3, subj, Font.mediumSystemFont(15), 0.8, c(1), 0.75);
  r3.addSpacer();
  if (rm) T(r3, rm, Font.semiboldRoundedSystemFont(14), 0.62, c(1));
}
// нижняя строка: на перемене и утром заголовок уже про следующий урок, поэтому тут то, что будет после него
function bottomLine(m) {
  const s = m.s;
  if (m.kind === "BREAK") { const a = s.list[s.list.indexOf(s.next) + 1]; return a ? ["ПОТОМ · " + hm(a.start), a.subject, a.room] : ["ПОТОМ", "Домой в " + hm(s.next.end), ""]; }
  if (m.kind === "BEFORE" && s.soon) return ["СЕГОДНЯ", plural(s.count) + " · до " + s.end, ""];
  return [m.nextHead, m.nextSubj, m.nextRoom];
}

function medium(m) {
  const w = new ListWidget();
  background(w);
  w.setPadding(14, 16, 14, 18);
  const r = row(w);
  const b = ringBox(r, 128, 10, m.timer ? m.progress : -1, c(0.16), acc(1));
  ringCenter(b, m, 30, 13);
  r.addSpacer(16);
  const col = r.addStack(); col.layoutVertically(); col.size = new Size(0, 128);
  sideInfo(col, m, {title: 20, rule: true});
  return w;
}

function large(m, now) {
  const w = new ListWidget();
  background(w);
  w.setPadding(16, 16, 14, 16);
  const top = row(w);
  const b = ringBox(top, 104, 9, m.timer ? m.progress : -1, c(0.16), acc(1));
  ringCenter(b, m, 26, 12);
  top.addSpacer(14);
  const col = top.addStack(); col.layoutVertically(); col.size = new Size(0, 104);
  sideInfo(col, m, {title: 20, rule: false});
  w.addSpacer(12);

  // весь день таймлайном: сегодня, а после уроков и в выходные ближайший учебный день
  const s = m.s, today = hasLessonToday(m), day = today ? now : s.nDate, list = day ? lessonsOn(day) : [];
  const nowMin = now.getHours() * 60 + now.getMinutes(), many = list.length >= 9, fs = many ? 13 : 14;
  for (const l of list) {
    const cur = today && s.kind === "LESSON" && s.cur.num === l.num;
    const next = today && s.kind !== "LESSON" && s.next && s.next.num === l.num;
    const past = today && !cur && l.end <= nowMin && s.kind !== "BEFORE";
    const a = past ? 0.32 : 1;
    const r = row(w);
    r.setPadding(many ? 3 : 4, 8, many ? 3 : 4, 8);
    if (cur) { r.backgroundColor = c(0.10); r.cornerRadius = 10; }
    else if (next) { r.backgroundColor = c(0.05); r.cornerRadius = 10; }
    const t = fixed(r, 38); t.addSpacer(); T(t, hm(l.start), Font.semiboldRoundedSystemFont(12), past ? 0.32 : 0.62, c(1));
    r.addSpacer(8);
    // полоска урока в цвете предмета (прошедшие бледнее), без «цвета урока» как раньше
    const mark = fixed(r, 4, 16); mark.cornerRadius = 2;
    mark.backgroundColor = subjColor(l.subject, past ? 0.35 : cur || next ? 1 : 0.8) || (cur ? acc(1) : next ? c(0.62) : past ? c(0.16) : c(0.30));
    r.addSpacer(8);
    T(r, l.subject, cur ? Font.semiboldSystemFont(fs) : Font.mediumSystemFont(fs), a, c(1), 0.8);
    r.addSpacer();
    if (l.changed) { T(r, "замена", Font.semiboldSystemFont(10), past ? 0.32 : 0.62, c(1)); r.addSpacer(6); }
    if ((cur || (next && s.kind === "BREAK")) && m.timer) { const tb = fixed(r, 44); timer(tb, m.target, Font.mediumRoundedSystemFont(11), 0.62, c(1), "r"); r.addSpacer(6); }
    if (l.room) T(r, l.room, Font.semiboldRoundedSystemFont(12), cur ? 1 : past ? 0.32 : 0.62, cur ? acc(1) : c(1));
    w.addSpacer(2);
  }
  w.addSpacer();
  const d = now, h = s.holiday, v = vacation(now, SRC);
  let foot = DSHORT[d.getDay()].toUpperCase() + ", " + dm(d).toUpperCase();
  if (h && /каникул/i.test(h[2])) foot = h[2].toUpperCase() + " · ДО " + dm(new Date(h[1] + "T12:00:00")).toUpperCase();
  else if (v) foot += " · " + vacationText(v).toUpperCase();
  T(w, foot, Font.semiboldSystemFont(10), 0.42, c(1), 0.7);
  return w;
}

// ---------- кабинеты (как во вкладке «Кабинеты» на сайте) ----------
const splitRooms = r => (r || "").split("/").map(x => x.trim()).filter(Boolean);
const ROOMS = (() => {
  const set = new Set();
  for (const c of Object.keys(DATA.classes)) for (const day of classWeek(c)) for (const l of day) splitRooms(l.room).forEach(r => set.add(r));
  return [...set].sort((a, b) => (parseInt(a) || 999) - (parseInt(b) || 999) || a.localeCompare(b, "ru"));
})();
/** Свободные кабинеты сейчас (или на ближайшем уроке) и на следующем; edge: ближайший звонок, когда список поменяется. */
function freeRooms(now) {
  const off = dayOff(now), min = now.getHours() * 60 + now.getMinutes(), use = {};
  if (!off) for (const c of Object.keys(DATA.classes)) for (const l of dayLessons(c, now)) splitRooms(l.room).forEach(r => (use[r] = use[r] || []).push(l.num));
  const bells = dayBells(now), slots = bells.map((b, i) => b && Object.values(use).some(u => u.includes(i + 1)) ? i + 1 : 0).filter(Boolean);
  const free = n => ROOMS.filter(r => r !== "спортзал" && !(use[r] || []).includes(n));
  const live = slots.find(n => min >= bells[n - 1][0] && min < bells[n - 1][1]) || 0, next = slots.find(n => bells[n - 1][0] > min) || 0;
  const edges = slots.flatMap(n => bells[n - 1]).filter(x => x > min);
  const r = {edge: edges.length ? at(now, Math.min(...edges)) : null};
  if (off || !slots.length) return Object.assign(r, {head: off ? off[2] : "Сегодня уроков нет", list: "Все кабинеты свободны"});
  if (!live && !next) return Object.assign(r, {head: "Уроки закончились", list: "Все кабинеты свободны"});
  const first = live || next, after = slots.find(n => n > first) || 0, f = free(first);
  const count = l => l.length ? ": " + l.length : ""; // длинный список в виджете обрезается, а число видно всегда
  Object.assign(r, {count: f.length, list: f.length ? f.join("  ") : "свободных нет",
    head: live ? "Свободны сейчас" + count(f) + " · " + live + " урок" : "Свободны на " + next + " уроке" + count(f) + " · " + hm(bells[next - 1][0])});
  if (after) { const f2 = free(after); Object.assign(r, {head2: "На " + after + " уроке" + count(f2) + " · " + hm(bells[after - 1][0]), list2: f2.length ? f2.join("  ") : "свободных нет"}); }
  return r;
}
function roomsWidget(f, r) {
  const w = new ListWidget();
  const text = (p, s, font, a, lines) => { const t = T(p, s, font, a, String(f).startsWith("accessory") ? null : c(1), 0.7); t.lineLimit = lines; return t; };
  if (f === "accessoryInline") { w.addText(r.count != null ? "Свободны: " + r.list : r.head); return w; }
  if (f === "accessoryCircular") {
    w.addAccessoryWidgetBackground = true;
    w.addSpacer();
    centered(w, x => T(x, r.count != null ? String(r.count) : "все", Font.boldRoundedSystemFont(20), 1));
    centered(w, x => T(x, "свободны", Font.mediumSystemFont(9), 0.7));
    w.addSpacer();
    return w;
  }
  if (f === "accessoryRectangular") {
    text(w, r.head.toUpperCase(), Font.semiboldSystemFont(11), 0.7, 1);
    text(w, r.list, Font.boldRoundedSystemFont(14), 1, 2);
    return w;
  }
  background(w);
  w.setPadding(14, 16, 14, 16);
  text(w, r.head.toUpperCase(), Font.semiboldSystemFont(11), 0.42, 1);
  w.addSpacer(4);
  const big = r.list.length <= (f === "small" ? 14 : 30); // пара кабинетов крупно, длинный список мельче
  text(w, r.list, Font.semiboldRoundedSystemFont(big ? 22 : f === "small" ? 15 : 17), 1, f === "small" ? 5 : f === "medium" ? 3 : 7);
  if (r.head2 && f !== "small") {
    w.addSpacer(8);
    text(w, r.head2.toUpperCase(), Font.semiboldSystemFont(11), 0.42, 1);
    w.addSpacer(2);
    text(w, r.list2, Font.mediumRoundedSystemFont(15), 0.7, f === "medium" ? 1 : 5);
  }
  w.addSpacer();
  return w;
}

// ---------- экран приложения ----------
// Запуск скрипта в Scriptable открывает сам сайт «Звонка»: «Сейчас», «Неделя», «Кабинеты» и «Ещё», как на сайте.
// Он скачивается один раз и хранится на телефоне, поэтому дальше открывается и без интернета; свежая копия
// подтягивается в фоне, когда есть сеть (не чаще раза в 6 часов). С ключом подписи в скрипте копия должна быть им подписана.
// Класс и своё расписание, выбранные там, сохраняются в настройки, и виджеты показывают то же самое.
function appPath() { const fm = FileManager.local(); return fm.joinPath(fm.documentsDirectory(), "zvonok-app.html"); }
async function fetchApp() {
  try {
    const r = new Request(SITE + "index.html");
    r.timeoutInterval = 10;
    const v = UPDATE_KEY ? new Request(SITE + "ios/versions.json") : null;
    if (v) v.timeoutInterval = 6;
    const [html, ver] = await Promise.all([r.loadString(), v ? v.loadJSON() : null]);
    if (!html || html.length < 50000 || !html.includes("zvNext")) return null;
    if (UPDATE_KEY && !signedByZvonok(html, ver && ver.app)) return null;
    FileManager.local().writeString(appPath(), html);
    return html;
  } catch (e) { return null; } // нет сети: откроется сохранённая копия
}
/** Без интернета и без сохранённой копии: коротко, что сейчас, и как открыть всё приложение. */
function offlineHtml(m, now) {
  const e = t => String(t).replace(/[&<>"]/g, ch => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;"})[ch]);
  return "<!doctype html><html lang=\"ru\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
    + "<body style=\"font:-apple-system-body;background:#000;color:#fff;padding:max(24px,env(safe-area-inset-top)) 22px 24px\">"
    + "<h1>Звонок " + e(OWN ? "· своё" : CLS) + "</h1><p style=\"font-size:1.3em\">" + e(spoken(m, now)) + "</p>"
    + "<p style=\"opacity:.7\">Чтобы открыть всё приложение (неделя, кабинеты, настройки), один раз подключись к интернету и запусти скрипт снова. "
    + "Потом оно будет открываться и без сети.</p></body></html>";
}
async function app(m, now) {
  const fm = FileManager.local();
  let html = null, age = Infinity;
  try { if (fm.fileExists(appPath())) { html = fm.readString(appPath()); age = Math.abs(Date.now() - fm.modificationDate(appPath()).getTime()); } } catch (e) { html = null; }
  if (!html) { html = await fetchApp(); age = 0; }
  const wv = new WebView(), base = SITE + "?app=scriptable";
  let page = base, wake = null;
  wv.shouldAllowRequest = req => {
    const u = String(req.url || ""), doc = u.split("#")[0];
    if (doc !== page.split("#")[0] && wake) { const f = wake; wake = null; f("nav"); } // открылась другая страница
    if (u.startsWith("about:") || u.startsWith(base)) { page = u; return true; }
    if (html && (doc === SITE || doc === SITE + "index.html")) { page = base; wv.loadHTML(html, base); return false; } // «на главную»: копия на телефоне
    if (u.startsWith(SITE)) { page = u; return true; } // другие страницы сайта: конструктор, «Написать нам», «Что нового»
    if (/^(https?|webcal|tg|mailto):/i.test(u)) Safari.open(u);
    return false;
  };
  if (!html) { await wv.loadHTML(offlineHtml(m, now)); await wv.present(true); return; }
  await wv.loadHTML(html, base);
  let open = true;
  const shown = wv.present(true).then(() => { open = false; return null; }, () => { open = false; return null; });
  // настройки: у новой копии страницы берём выбранное в виджетах, иначе запоминаем выбранное в приложении
  try {
    const st = JSON.parse(await wv.evaluateJavaScript("zvState()"));
    if (st && st.cls) saveSettings(st);
    else await wv.evaluateJavaScript("zvSync(" + JSON.stringify(Object.assign({cls: CLS}, SET)) + ")"); // сразу свой класс
  } catch (e) { /* страница без моста (старая копия): просто показываем */ }
  if (age > 6 * 3600000) fetchApp(); // в фоне; в следующий раз откроется свежая версия
  while (open) {
    const nav = new Promise(r => { wake = r; });
    const raw = await Promise.race([wv.evaluateJavaScript("typeof zvNext === \"function\" ? zvNext(completion) : completion(\"\")", true), shown, nav]).catch(() => "");
    if (!open || raw == null) break;
    if (raw === "nav") { await wv.waitForLoad().catch(() => {}); continue; }
    if (!raw) { await Promise.race([shown, nav]); continue; } // на странице без моста ждём, пока вернутся на главную
    let msg = {};
    try { msg = JSON.parse(raw); } catch (e) { continue; }
    try {
      if (msg.set) saveSettings(msg.set);
      if (typeof msg.open === "string" && /^(https?|webcal|tg|mailto):/i.test(msg.open)) Safari.open(msg.open);
      if (typeof msg.share === "string") await ShareSheet.present([msg.share]);
      if (typeof msg.copy === "string") Pasteboard.copy(msg.copy);
    } catch (e) { /* отказ поделиться и т. п. */ }
  }
}

// ---------- самообновление: новая версия скрипта для своего класса с сайта ----------
// Когда пора (netPlan: не чаще раза в 6 часов), спрашиваем ios/versions.json; если версия новее, скачиваем
// ios/<класс>.js, проверяем, что это целый скрипт «Звонка» для того же класса и что он собирается,
// и записываем поверх себя. Тема с сайта сохраняется.
// ---------- подпись новых версий: Ed25519 (RFC 8032) ----------
// Сборка подписывает скрипт каждого класса закрытым ключом «Звонка» (он есть только в секретах GitHub,
// в репозитории и на сайте его нет), а открытый ключ вписан в скрипт (UPDATE_KEY в данных выше).
// Если ключ вписан, виджет ставит только версию с верной подписью: подменённый на хостинге файл не примет.
// В Scriptable нет WebCrypto, поэтому SHA-512 и Ed25519 здесь свои, на BigInt; проверка занимает доли секунды
// и бывает только при выходе новой версии. Автотесты сверяют их с Python и libsodium.
function b64bytes(s) {
  const A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/", out = [];
  let buf = 0, n = 0;
  for (const ch of String(s || "").replace(/[^A-Za-z0-9+/]/g, "")) {
    buf = ((buf << 6) | A.indexOf(ch)) & 0xffff; n += 6;
    if (n >= 8) { n -= 8; out.push((buf >> n) & 255); }
  }
  return out;
}
function utf8(str) {
  const out = [];
  for (const ch of String(str)) {
    const c = ch.codePointAt(0);
    if (c < 0x80) out.push(c);
    else if (c < 0x800) out.push(0xc0 | c >> 6, 0x80 | c & 63);
    else if (c < 0x10000) out.push(0xe0 | c >> 12, 0x80 | c >> 6 & 63, 0x80 | c & 63);
    else out.push(0xf0 | c >> 18, 0x80 | c >> 12 & 63, 0x80 | c >> 6 & 63, 0x80 | c & 63);
  }
  return out;
}
function sha512(bytes) {
  const M = (1n << 64n) - 1n;
  if (!sha512.K) {
    // константы SHA-512 считаем, а не переписываем: 64 бита дробной части кубических корней первых 80 простых
    // (и квадратных корней первых 8 для начального значения), как в стандарте FIPS 180-4
    const primes = [];
    for (let x = 2; primes.length < 80; x++) if (primes.every(y => x % y)) primes.push(x);
    const root = (x, k) => {
      const K = BigInt(k);
      let r = 1n << BigInt(Math.ceil(x.toString(2).length / k));
      for (;;) { const s = ((K - 1n) * r + x / r ** (K - 1n)) / K; if (s >= r) return r; r = s; }
    };
    sha512.K = primes.map(x => root(BigInt(x) << 192n, 3) & M);
    sha512.H = primes.slice(0, 8).map(x => root(BigInt(x) << 128n, 2) & M);
  }
  const K = sha512.K, rotr = (x, n) => ((x >> n) | (x << (64n - n))) & M;
  const m = bytes.slice(), bits = BigInt(bytes.length) * 8n;
  m.push(0x80);
  while (m.length % 128 !== 112) m.push(0);
  for (let i = 15; i >= 0; i--) m.push(Number((bits >> BigInt(i * 8)) & 255n));
  let h = sha512.H.slice();
  const w = new Array(80);
  for (let o = 0; o < m.length; o += 128) {
    for (let t = 0; t < 16; t++) { let x = 0n; for (let j = 0; j < 8; j++) x = (x << 8n) | BigInt(m[o + t * 8 + j]); w[t] = x; }
    for (let t = 16; t < 80; t++) {
      const a = w[t - 15], b = w[t - 2];
      w[t] = (w[t - 16] + (rotr(a, 1n) ^ rotr(a, 8n) ^ (a >> 7n)) + w[t - 7] + (rotr(b, 19n) ^ rotr(b, 61n) ^ (b >> 6n))) & M;
    }
    let [a, b, c, dd, e, f, g, hh] = h;
    for (let t = 0; t < 80; t++) {
      const t1 = (hh + (rotr(e, 14n) ^ rotr(e, 18n) ^ rotr(e, 41n)) + ((e & f) ^ (~e & M & g)) + K[t] + w[t]) & M;
      const t2 = ((rotr(a, 28n) ^ rotr(a, 34n) ^ rotr(a, 39n)) + ((a & b) ^ (a & c) ^ (b & c))) & M;
      hh = g; g = f; f = e; e = (dd + t1) & M; dd = c; c = b; b = a; a = (t1 + t2) & M;
    }
    h = [a, b, c, dd, e, f, g, hh].map((x, i) => (h[i] + x) & M);
  }
  const out = [];
  for (const x of h) for (let j = 7; j >= 0; j--) out.push(Number((x >> BigInt(j * 8)) & 255n));
  return out;
}
function edVerify(pub, msg, sig) {
  const P = 2n ** 255n - 19n, L = 2n ** 252n + 27742317777372353535851937790883648493n;
  const mod = x => ((x % P) + P) % P;
  const pow = (b, e) => { let r = 1n; b = mod(b); for (; e > 0n; e >>= 1n, b = b * b % P) if (e & 1n) r = r * b % P; return r; };
  const inv = x => pow(x, P - 2n);
  const D = mod(-121665n * inv(121666n)), I = pow(2n, (P - 1n) / 4n);
  const num = b => { let x = 0n; for (let i = b.length - 1; i >= 0; i--) x = (x << 8n) | BigInt(b[i]); return x; };
  const xOf = (y, sign) => {
    if (y >= P) return null;
    const x2 = mod((y * y - 1n) * inv(D * y * y + 1n));
    if (x2 === 0n) return sign ? null : 0n;
    let x = pow(x2, (P + 3n) / 8n);
    if (mod(x * x - x2) !== 0n) x = x * I % P;
    if (mod(x * x - x2) !== 0n) return null;
    if (Number(x & 1n) !== sign) x = P - x;
    return x;
  };
  const point = b => { const y = num(b) & ((1n << 255n) - 1n), x = xOf(y, b[31] >> 7); return x === null ? null : [x, y, 1n, x * y % P]; };
  const add = (p, q) => {
    const A = mod((p[1] - p[0]) * (q[1] - q[0])), B = (p[1] + p[0]) * (q[1] + q[0]) % P;
    const C = 2n * p[3] * q[3] % P * D % P, Z = 2n * p[2] * q[2] % P;
    const E = mod(B - A), F = mod(Z - C), G = (Z + C) % P, H = (B + A) % P;
    return [E * F % P, G * H % P, F * G % P, E * H % P];
  };
  const mul = (s, p) => { let q = [0n, 1n, 1n, 0n]; for (; s > 0n; s >>= 1n, p = add(p, p)) if (s & 1n) q = add(q, p); return q; };
  const same = (p, q) => mod(p[0] * q[2] - q[0] * p[2]) === 0n && mod(p[1] * q[2] - q[1] * p[2]) === 0n;
  if (pub.length !== 32 || sig.length !== 64) return false;
  const A = point(pub), R = point(sig.slice(0, 32)), S = num(sig.slice(32));
  if (!A || !R || S >= L) return false;
  const gy = 4n * inv(5n) % P, gx = xOf(gy, 0), B = [gx, gy, 1n, gx * gy % P];
  const h = num(sha512(sig.slice(0, 32).concat(pub, msg))) % L;
  return same(mul(S, B), add(R, mul(h, A)));
}
/** Новая версия подписана ключом «Звонка». */
function signedByZvonok(code, sig) {
  try { return !!sig && edVerify(b64bytes(UPDATE_KEY), utf8(code), b64bytes(sig)); } catch (e) { return false; }
}
function compiles(code) {
  try { new (Object.getPrototypeOf(async function () {}).constructor)(code); return true; }
  catch (e) { return !(e instanceof SyntaxError); }
}
async function selfUpdate() {
  if (!AUTO_UPDATE || !VERSION || typeof module === "undefined") return;
  try {
    const slug = CLASS.replace(/[АБВГ]/g, x => ({"А": "A", "Б": "B", "В": "V", "Г": "G"})[x]);
    const r = new Request(SITE + "ios/versions.json");
    r.timeoutInterval = 4;
    const v = await r.loadJSON();
    const latest = v && v.classes && v.classes[slug];
    if (!latest || latest === VERSION) return;
    const rr = new Request(SITE + "ios/" + slug + ".js");
    rr.timeoutInterval = 8;
    let code = await rr.loadString();
    const ok = code && code.length > 20000 && code.length < 400000 && code.includes('const VERSION = "' + latest + '"')
      && code.includes('const CLASS = "' + CLASS + '"') && code.includes("Script.complete()");
    // новая версия должна быть собрана позже нашей: старую подписанную версию (откат) не ставим
    const built = +((code.match(/^const BUILT = (\d+);/m) || [])[1] || 0);
    // с ключом в скрипте ставим только подписанную версию; без ключа (первая сборка без секрета) как раньше
    if (!ok || built <= BUILT || (UPDATE_KEY && !signedByZvonok(code, v.sig && v.sig[slug])) || !compiles(code)) return;
    if (THEME) code = code.replace('const THEME = "";', "const THEME = " + JSON.stringify(THEME) + ";");
    const icloud = FileManager.iCloud();
    const target = icloud.isFileStoredIniCloud(module.filename) ? icloud : FileManager.local();
    target.writeString(module.filename, code);
    const n = new Notification();
    n.title = "Звонок обновился";
    n.body = v.note ? "Что нового: " + v.note : "Новое расписание и улучшения уже в виджете.";
    n.openURL = SITE + "changelog.html";
    await n.schedule();
  } catch (e) { /* нет сети или нет доступа к файлу: попробуем в следующий раз */ }
}

// ---------- Siri и «Команды»: ответ одной фразой ----------
function spoken(m, now) {
  const s = m.s, n = s.next;
  const say = t => t.replace(/яз\. и лит\./g, "язык и литература").replace(/^Практ\. /, "практическая ").replace(/^Худ\. /, "художественный ")
    .replace(/^Ист\. /, "история ").replace(/^Всем\. /, "всемирная ").replace(/^НВП$/, "Н В П");
  const low = t => { t = say(t); return /^[А-ЯЁ][а-яё]/.test(t) ? t[0].toLowerCase() + t.slice(1) : t; };
  const where = r => !r ? "" : r === "спортзал" ? ", спортзал" : ", кабинет " + r.split("/").filter(Boolean).join(" или ");
  const left = t => {
    const min = Math.max(1, Math.ceil((t.getTime() - now.getTime()) / 60000));
    return min < 60 ? word(min, "минута", "минуты", "минут") : word(Math.floor(min / 60), "час", "часа", "часов") + (min % 60 ? " " + word(min % 60, "минута", "минуты", "минут") : "");
  };
  if (s.kind === "LESSON") return "Сейчас " + low(s.cur.subject) + where(s.cur.room) + ". До звонка " + left(s.target) + "."
    + (n ? " Дальше " + low(n.subject) + where(n.room) + "." : " Это последний урок.");
  if (s.kind === "BREAK") return (n.start - s.cur.end > 25 ? "Окно" : "Перемена") + ". Через " + left(s.target) + " " + low(n.subject) + where(n.room) + ".";
  if (s.kind === "BEFORE") return (s.soon ? "Первый урок через " + left(s.target) : "Сегодня " + plural(s.count) + ", первый в " + hm(n.start)) + ": " + low(n.subject) + where(n.room) + ".";
  const head = s.kind === "AFTER" ? "Уроки закончились." : s.holiday ? s.holiday[2] + "." : "Сегодня выходной.";
  if (!n) return head;
  return head + (s.nLabel === "Завтра" ? " Завтра первый урок в " : " Следующие уроки " + s.nWhen + ". Первый в ") + hm(n.start) + ": " + low(n.subject) + where(n.room) + ".";
}

// ---------- запуск ----------

netPlan();
CHANGES = cleanChanges((await Promise.all([loadChanges(NET.changes), NET.update ? selfUpdate() : null]))[0]);
const now = new Date();
const inWidget = config.runsInWidget || config.runsInAccessoryWidget;
if (inWidget && ROOMS_MODE) {
  // виджет «кабинеты»: перерисовка на ближайшем звонке, когда список поменяется
  const r = freeRooms(now), w = roomsWidget(config.widgetFamily, r);
  w.refreshAfterDate = r.edge ? new Date(r.edge.getTime() + 1000) : new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 5);
  Script.setWidget(w);
} else {
  const m = model(now);
  // «цвет урока»: кольцо и кабинет в цвете текущего предмета, на перемене следующего
  if (TH.sc) {
    const subj = m.s.kind === "LESSON" ? m.s.cur && m.s.cur.subject : m.s.next && m.s.next.subject;
    const col = subj && COLORS[subj];
    if (col) ACC = col[LIGHT ? 0 : 1];
    if (col && hasLessonToday(m)) TINT = col[1]; // в выходной фон не красим уроком понедельника
  }
  if (inWidget) {
    const f = config.widgetFamily;
    const w = f === "accessoryRectangular" ? lockRect(m) : f === "accessoryCircular" ? lockCircle(m) : f === "accessoryInline" ? lockInline(m)
      : f === "medium" ? medium(m) : f === "large" || f === "extraLarge" ? large(m, now) : small(m);
    w.refreshAfterDate = nextRefresh(m, now);
    Script.setWidget(w);
  } else if (config.runsWithSiri || config.runsInShortcuts) {
    Script.setShortcutOutput(spoken(m, now));
  } else {
    Script.setShortcutOutput(spoken(m, now));
    await app(m, now);
  }
}
Script.complete();
