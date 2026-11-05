/* Звонок · «Написать нам»: анонимное письмо на почту проекта, без аккаунтов.
   Шлём только тип, текст и класс. Основной путь Web3Forms (ключ публичный по замыслу сервиса,
   письма идут только на почту проекта), запасной FormSubmit. */
const W3F_KEY = "47a818a4-abad-4577-951b-19ed93e1ae86";
const FALLBACK = "https://formsubmit.co/ajax/zvonok165lyceumalmaty@gmail.com";
const CLASS_LIST = /*@CLASSES@*/[];
const MAX = 1000, PAUSE = 30000;
const KINDS = ["Идея", "Ошибка", "Другое"];
const DAY_NAMES = ["понедельник", "вторник", "среда", "четверг", "пятница", "суббота"];
let kind = KINDS[0], waitT;
const txt = $("text"), sel = $("cls"), send = $("send");

function renderKind() {
  const box = $("kind"); box.textContent = "";
  KINDS.forEach(k => box.append(h("button", {type: "button", "aria-pressed": String(k === kind), text: k, onclick: () => { kind = k; renderKind(); }})));
}
[["", "Не указывать"]].concat(CLASS_LIST.map(c => [c, c])).forEach(([v, t]) => sel.append(h("option", {value: v, text: t})));
txt.value = store.get("fbDraft", "");
// пришли из расписания: «Ошибка в расписании?» подставляет класс и день
(() => {
  const q = new URLSearchParams(location.search);
  if (q.get("about") !== "schedule") return;
  kind = "Ошибка";
  const c = q.get("class"), d = +q.get("day");
  if (c && CLASS_LIST.includes(c)) sel.value = c;
  if (!txt.value.trim()) txt.value = "Ошибка в расписании" + (c ? " " + c : "") + (DAY_NAMES[d] && q.has("day") ? ", " + DAY_NAMES[d] : "") + ".\nУрок: \nКак правильно: ";
})();
const left = () => Math.max(0, store.get("fbLast", 0) + PAUSE - Date.now());
function update() {
  const n = txt.value.length, w = left();
  $("cnt").textContent = n + " / " + MAX; $("cnt").classList.toggle("hi", n >= 950);
  send.disabled = txt.value.trim().length < 3 || w > 0;
  if (w > 0) { send.textContent = "Можно через " + Math.ceil(w / 1000) + " с"; clearTimeout(waitT); waitT = setTimeout(update, 1000 - w % 1000 + 20); }
  else send.textContent = txt.value.trim().length < 3 ? "Сначала напиши пару слов" : "Отправить";
}
txt.addEventListener("input", () => { store.set("fbDraft", txt.value); $("err").hidden = true; update(); });
function fail(t) { $("err").textContent = t; $("err").hidden = false; }
async function submit(e) {
  e.preventDefault();
  const text = txt.value.trim().slice(0, MAX);
  if (text.length < 3) return;
  // бот заполнил невидимое поле: делаем вид, что отправили, и ничего не шлём
  if ($("site").value) { $("form").hidden = true; $("done").hidden = false; return; }
  if (left() > 0) { fail("Подожди полминуты и отправь ещё раз."); update(); return; }
  // спам почти всегда со ссылками: живому человеку хватает одной-двух
  if ((text.match(/https?:|www\./gi) || []).length > 3) { fail("Слишком много ссылок. Оставь одну-две."); return; }
  if (navigator.onLine === false) { fail("Нет интернета. Текст сохранён, отправь, когда сеть появится."); return; }
  const fields = {"Тип": kind, "Сообщение": text, "Класс": sel.value || "не указан"};
  const post = (url, body) => fetch(url, {method: "POST", headers: {"Content-Type": "application/json", Accept: "application/json"}, body: JSON.stringify(body), credentials: "omit", referrerPolicy: "origin"})
    .then(r => r.json().catch(() => ({})).then(j => { if (!r.ok || j.success === false || j.success === "false") throw new Error(r.status); }));
  send.disabled = true; send.textContent = "Отправляю…";
  try {
    try {
      await post("https://api.web3forms.com/submit", Object.assign({access_key: W3F_KEY, subject: "Звонок: " + kind, from_name: "Звонок · анонимно"}, fields));
    } catch (x) {
      await post(FALLBACK, Object.assign({_subject: "Звонок: " + kind, _template: "box", _captcha: "false"}, fields));
    }
    store.set("fbLast", Date.now()); store.set("fbDraft", ""); txt.value = "";
    $("form").hidden = true; $("done").hidden = false; $("done").focus();
  } catch (x) {
    fail("Не получилось отправить. Текст сохранён, попробуй чуть позже.");
  }
  update();
}
$("form").addEventListener("submit", submit);
$("again").onclick = () => { $("done").hidden = true; $("form").hidden = false; update(); txt.focus(); };
renderKind(); update();
