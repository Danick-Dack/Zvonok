/* Звонок · конструктор: своё расписание и цвета виджетов и уведомления */

/* ---------- своё расписание ---------- */
const SLOTS = 9;
const baseClass = () => DATA.classes[store.get("class", null)] ? store.get("class", null) : CLASSES[0];
let ed = makeEditor(store.get("custom", null));
function makeEditor(c) {
  const e = {
    bells: Array.from({length: SLOTS}, (_, i) => c && c.bells && c.bells[i] ? [clean(c.bells[i][0]), clean(c.bells[i][1])] : (DATA.bells[i] ? DATA.bells[i].slice() : ["", ""])),
    days: Array.from({length: 6}, (_, d) => Array.from({length: SLOTS}, (_, i) => { const l = c && c.days && c.days[d] && c.days[d][i]; return l ? [clean(l[0]), clean(l[1])] : ["", ""]; }))
  };
  if (!c) fillFromClass(e, baseClass());
  return e;
}
function fillFromClass(e, c) {
  const w = classWeek(c);
  e.days = Array.from({length: 6}, (_, d) => Array.from({length: SLOTS}, (_, i) => { const l = w[d].find(x => x.num === i + 1); return l ? [l.subject, l.room] : ["", ""]; }));
  e.bells = Array.from({length: SLOTS}, (_, i) => DATA.bells[i] ? DATA.bells[i].slice() : ["", ""]);
}
function edJson() {
  return {v: 1, bells: ed.bells.map(b => [String(b[0]).trim(), String(b[1]).trim()]),
    days: ed.days.map(day => { let last = -1; day.forEach((l, i) => { if (l[0].trim()) last = i; }); return day.slice(0, last + 1).map(l => [clean(l[0]), clean(l[1])]); })};
}
const b64e = s => btoa(String.fromCharCode(...new TextEncoder().encode(s)));
const b64d = s => new TextDecoder().decode(Uint8Array.from(atob(s), c => c.charCodeAt(0)));
const edCode = () => "ZVS1:" + b64e(JSON.stringify(edJson()));
let cDay = Math.min(dow(new Date()), 5);
/** Звонок урока i: пустой или верный, и урок не начинается раньше конца предыдущего заполненного (как в Android). */
function bellOk(i) {
  const b = ed.bells[i], s = toMin(b[0]), e = toMin(b[1]);
  if (!b[0] && !b[1]) return true;
  const prev = ed.bells.slice(0, i).reverse().find(x => x[0] || x[1]);
  return s >= 0 && e > s && (!prev || !(toMin(prev[1]) > s));
}
function renderEditor() {
  const days = $("cDays"); days.textContent = "";
  for (let d = 0; d < 6; d++) days.append(h("button", {type: "button", "aria-pressed": String(d === cDay), text: DAYS[d], "aria-label": DAYN[d], onclick: () => { cDay = d; renderEditor(); }}));
  const box = $("cSlots"); box.textContent = "";
  for (let i = 0; i < SLOTS; i++) {
    const l = ed.days[cDay][i];
    const s = h("input", {class: "field", list: "subjList", placeholder: "Предмет", "aria-label": "Предмет, " + (i + 1) + " урок", autocomplete: "off"}); s.value = l[0];
    s.oninput = () => { l[0] = s.value; saveEd(); };
    const r = h("input", {class: "field", placeholder: "Каб.", "aria-label": "Кабинет, " + (i + 1) + " урок", autocomplete: "off"}); r.value = l[1];
    r.oninput = () => { l[1] = r.value; saveEd(); };
    box.append(h("div", {class: "slot"}, h("span", {class: "k"}, String(i + 1), h("small", {text: ed.bells[i][0]})), s, r));
  }
  const bells = $("cBells"); bells.textContent = "";
  const marks = []; // смена одного звонка может исправить или сломать следующий: проверяем все строки
  ed.bells.forEach((b, i) => {
    const mk = (k, ph, lab) => {
      const x = h("input", {class: "field", inputmode: "decimal", placeholder: ph, "aria-label": (i + 1) + " урок, " + lab, autocomplete: "off"});
      x.value = b[k];
      x.oninput = () => { b[k] = x.value; marks.forEach(m => m()); saveEd(); const k0 = box.children[i] && box.children[i].querySelector(".k small"); if (k0) k0.textContent = b[0]; };
      return x;
    };
    const a = mk(0, "8:00", "начало"), z = mk(1, "8:45", "конец");
    const mark = () => { const ok = bellOk(i); a.setAttribute("aria-invalid", String(!ok)); z.setAttribute("aria-invalid", String(!ok)); };
    marks.push(mark);
    mark();
    bells.append(h("span", {text: (i + 1) + " урок"}), a, z);
  });
}
function saveEd() {
  const c = edJson();
  store.set("custom", c);
}
function renderUse() { $("cUse").checked = !!store.get("useCustom", false) && !!store.get("custom", null); }
/** Календарь .ics из своего расписания: каждую неделю до конца четверти, без каникул, напоминание за 3 минуты. */
function icsFor(week, name) {
  const pad = n => String(n).padStart(2, "0"), ymd = d => d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate());
  const tm = (d, m) => ymd(d) + "T" + pad(Math.floor(m / 60)) + pad(m % 60) + "00";
  const esc2 = t => String(t).replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,");
  const now = new Date(), mon = new Date(now); mon.setHours(12, 0, 0, 0); mon.setDate(mon.getDate() - dow(mon));
  const end = new Date((DATA.term_end || iso(now)) + "T12:00:00");
  const stamp = now.toISOString().replace(/[-:]/g, "").replace(/\.\d+/, "");
  const L = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Zvonok//custom//RU", "CALSCALE:GREGORIAN", "X-WR-CALNAME:Звонок · " + name];
  const BY = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"];
  week.forEach((list, d) => list.forEach(l => {
    const first = new Date(mon); first.setDate(first.getDate() + d);
    const ex = [];
    for (const x = new Date(first); x <= end; x.setDate(x.getDate() + 7)) if (holiday(x)) ex.push("EXDATE:" + tm(x, l.start));
    const what = l.subject + (l.room ? ", " + (/^\d/.test(l.room) ? "каб. " + l.room : l.room) : "");
    L.push("BEGIN:VEVENT", "UID:custom-" + (d + 1) + "-" + l.num + "-" + ymd(now) + "@zvonok165", "DTSTAMP:" + stamp,
      "DTSTART:" + tm(first, l.start), "DTEND:" + tm(first, l.end), "RRULE:FREQ=WEEKLY;BYDAY=" + BY[d] + ";UNTIL=" + ymd(end) + "T235959", ...ex,
      "SUMMARY:" + esc2(what), "LOCATION:" + esc2(l.room), "DESCRIPTION:" + esc2(l.num + " урок"),
      "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT3M", "DESCRIPTION:" + esc2("Через 3 мин: " + what), "END:VALARM", "END:VEVENT");
  }));
  L.push("END:VCALENDAR");
  return L.join("\r\n") + "\r\n";
}
function download(name, text, type) {
  const a = h("a", {href: URL.createObjectURL(new Blob([text], {type: type || "text/plain"})), download: name});
  document.body.append(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
}
(function initEditor() {
  const dl = $("subjList"); [...new Set(Object.values(DATA.subjects))].sort().forEach(s => dl.append(h("option", {value: s})));
  const from = $("cFrom"); CLASSES.forEach(c => from.append(h("option", {value: c, text: "Класс " + c}))); from.value = baseClass();
  $("cTake").onclick = () => { fillFromClass(ed, from.value); saveEd(); renderEditor(); toast("Взято расписание " + from.value); };
  $("cClear").onclick = () => { ed.days[cDay] = Array.from({length: SLOTS}, () => ["", ""]); saveEd(); renderEditor(); toast(DAYN[cDay] + " очищен"); };
  $("cBellsReset").onclick = () => { ed.bells = Array.from({length: SLOTS}, (_, i) => DATA.bells[i] ? DATA.bells[i].slice() : ["", ""]); saveEd(); renderEditor(); };
  $("cUse").onchange = e => {
    if (e.target.checked) saveEd();
    store.set("useCustom", e.target.checked);
    toast(e.target.checked ? "На сайте теперь своё расписание" : "На сайте снова расписание класса");
  };
  $("cCopy").onclick = () => copyText(edCode(), "Код скопирован. Вставь его в приложении");
  $("cShare").onclick = () => {
    const t = "Моё расписание для «Звонка». В приложении: ⚙ → «Своё расписание» → «Вставить код».\n\n" + edCode();
    if (navigator.share) navigator.share({text: t}).catch(() => {}); else copyText(t, "Скопировано, отправь другу");
  };
  $("cIcs").onclick = () => {
    const w = customWeek(edJson());
    if (!w.some(d => d.length)) { toast("Сначала заполни хотя бы один день"); return; }
    download("Звонок.ics", icsFor(w, "своё расписание"), "text/calendar");
  };
  $("cLoad").onclick = () => {
    const t = $("cCode").value, p = t.indexOf("ZVS1:");
    try {
      if (p < 0 || t.length > 40000) throw 0;
      const c = JSON.parse(b64d(t.slice(p + 5).split(/\s/)[0]));
      if (!c || !Array.isArray(c.days)) throw 0;
      ed = makeEditor(c); saveEd(); renderEditor();
      store.set("useCustom", true); renderUse(); $("cCode").value = "";
      toast("Расписание загружено и включено на сайте");
    } catch (e) { toast("Не похоже на код расписания"); }
  };
  renderEditor(); renderUse();
  addEventListener("storage", e => { if (e.key === "zv.useCustom") renderUse(); });
})();

/* ---------- цвета виджетов и уведомления (код темы для приложения и виджетов iPhone) ---------- */
const PRESETS = [["Чёрный", "000000", "FFFFFF"], ["Графит", "1C1C1E", "FFFFFF"], ["Полночь", "0B1220", "60A5FA"], ["Мята", "04140F", "5EEAD4"],
  ["Лаванда", "120D1F", "A78BFA"], ["Бордо", "1A0A0E", "FB7185"], ["Песок", "F4EFE6", "1F1F1F"], ["Белый", "FFFFFF", "000000"]];
const ACCENTS_W = ["FFFFFF", "FF5A36", "5EEAD4", "60A5FA", "A78BFA", "F472B6", "FBBF24", "A3E635", "000000"];
const TH0 = TH_DEFAULT;
let th = cleanTheme(store.get("theme", {}));
const lum = hex => { const n = parseInt(hex, 16); return (0.2126 * (n >> 16 & 255) + 0.7152 * (n >> 8 & 255) + 0.0722 * (n & 255)) / 255; };
const thCode = () => themeString(th);
function renderTheme() {
  store.set("theme", th);
  const light = lum(th.bg) > .5, text = th.tx ? "#" + th.tx : light ? "#111" : "#fff", soft = th.tx ? text + "d9" : light ? "#3a3a3a" : "#d0d0d0", muted = th.tx ? text + "99" : light ? "#6b6b6b" : "#8a8a8a",
    faint = light ? "rgba(0,0,0,.2)" : "#2a2a2a", chipBg = light ? "rgba(0,0,0,.08)" : "#1e1e1e", track = light ? "rgba(0,0,0,.13)" : "#262626";
  // в предпросмотре идёт физика: с «цветом урока» акцент фиолетовый, как у предмета
  const accV = th.sc ? (light ? "AF52DE" : "BF5AF2") : Math.abs(lum(th.ac) - lum(th.bg)) < .25 ? (light ? "111111" : "FFFFFF") : th.ac, onAcc = lum(accV) > .5 ? "#000" : "#fff";
  const bgA = "#" + th.bg + Math.round(th.op * 2.55).toString(16).padStart(2, "0"), rad = th.r <= 16 ? 12 : th.r >= 32 ? 36 : 26;
  const chip = (t, now) => h("span", {class: "pv-chip", style: `background:${now ? "#" + accV : chipBg};color:${now ? onAcc : text}`, text: t});
  const dots = () => th.d ? h("span", {class: "pv-dots"}, [0, 1, 2, 3, 4, 5, 6].map(i => h("span", {style: "color:" + (i === 2 ? "#" + accV : i < 2 ? muted : faint), text: "●"}))) : null;
  const tm = size => h("div", {class: "pv-tm", style: `font-size:${size}px;color:${text}`, text: th.s ? "24:16" : "24 мин"});
  const bar = () => th.p ? h("div", {class: "pv-bar", style: "background:" + track}, h("i", {style: "background:#" + accV})) : null;
  const lab = (t, extra) => h("span", {class: "pv-lab", style: "color:" + muted + (extra || ""), text: t});
  const pv = $("pv"); pv.textContent = "";
  pv.append(h("div", null, h("p", {class: "pv-cap", text: "Уведомление"}), h("div", {class: "pv-n", style: "background:#" + th.bg},
    h("div", {class: "pv-row"}, lab("УРОК 3 ИЗ 7"), dots()),
    h("div", {class: "pv-title", style: "color:" + text}, h("span", {text: "Физика"}), chip("48/49", true)),
    h("div", {class: "pv-row", style: "align-items:flex-end;margin-top:6px"}, tm(42), h("div", {style: "margin-left:auto;text-align:right"}, lab("ЗВОНОК"), h("div", {style: "color:" + text + ";font-weight:600", text: "10:30"}))),
    bar(),
    th.n ? h("div", {class: "pv-row", style: "margin-top:12px"}, h("div", {style: "flex:1;min-width:0"}, lab("ДАЛЬШЕ · 10:50"), h("div", {style: "color:" + soft + ";font-size:14px", text: "Программирование"})), chip("56/57", false)) : null)));
  pv.append(h("div", {class: "pv-ws"},
    h("div", null, h("p", {class: "pv-cap", text: "Виджет 2×2"}), h("div", {class: "pv-2", style: `background:${bgA};border-radius:${rad}px`},
      h("div", {class: "pv-row"}, lab("УРОК 3 / 7", ";font-size:9.5px"), dots()),
      h("div", {class: "pv-title", style: "color:" + text}, h("span", {text: "Физика"}), chip("48/49", true)),
      h("div", {class: "pv-row", style: "align-items:flex-end"}, tm(30), h("div", {style: "margin-left:auto;text-align:right"}, lab("ЗВОНОК", ";font-size:8px"), h("div", {style: "color:" + text + ";font-size:12px;font-weight:600", text: "10:30"}))),
      bar(),
      th.n ? h("div", {class: "pv-nx", style: "color:" + soft}, h("span", {style: "color:" + muted, text: "→"}), h("span", {text: "Программирование"})) : null)),
    h("div", null, h("p", {class: "pv-cap", text: "Виджет 2×1"}), h("div", {class: "pv-1", style: `background:${bgA};border-radius:${rad}px`},
      h("div", {class: "pv-row"}, h("div", {style: "flex:1"}, lab("УРОК 3/7", ";font-size:9.5px"), tm(26)), th.n ? chip("56/57", false) : null),
      bar()))));
  const pr = $("thPresets"); pr.textContent = "";
  PRESETS.forEach(([n, bg, ac]) => pr.append(h("button", {type: "button", "aria-pressed": String(th.bg === bg && th.ac === ac), onclick: () => { th.bg = bg; th.ac = ac; renderTheme(); }},
    h("i", {style: `background:#${bg};color:#${ac}`, "aria-hidden": "true", text: "●"}), n)));
  const acc = $("thAcc"); acc.textContent = "";
  ACCENTS_W.forEach(a => acc.append(h("button", {type: "button", "aria-pressed": String(th.ac === a), style: "background:#" + a, "aria-label": "Акцент #" + a, onclick: () => { th.ac = a; renderTheme(); }})));
  $("thOp").value = th.op; $("thOpV").textContent = th.op + "%";
  const rr = $("thR"); rr.textContent = "";
  [[12, "Острые"], [26, "Обычные"], [36, "Круглые"]].forEach(([r, n]) => rr.append(h("button", {type: "button", "aria-pressed": String(Math.abs(th.r - r) < 6), text: n, onclick: () => { th.r = r; renderTheme(); }})));
  ["s", "p", "n", "d", "sc"].forEach(k => { $("th" + k.toUpperCase()).checked = !!th[k]; });
  const tx = $("thTX"); tx.textContent = "";
  [["", "Сам"], ["FFFFFF", "Белый"], ["111111", "Чёрный"]].forEach(([v, n]) => tx.append(h("button", {type: "button", "aria-pressed": String((th.tx || "") === v), text: n, onclick: () => { th.tx = v; renderTheme(); }})));
}
$("thOp").oninput = e => { th.op = +e.target.value; renderTheme(); };
["s", "p", "n", "d", "sc"].forEach(k => { $("th" + k.toUpperCase()).onchange = e => { th[k] = e.target.checked ? 1 : 0; renderTheme(); }; });
$("thCopy").onclick = () => copyText(thCode(), "Код темы скопирован");
$("thReset").onclick = () => { th = Object.assign({}, TH0); renderTheme(); toast("Цвета сброшены"); };

/* вкладки конструктора; make.html#theme открывает цвета */
function tab(theme) {
  $("tSched").setAttribute("aria-selected", String(!theme)); $("tTheme").setAttribute("aria-selected", String(theme));
  $("pSched").hidden = theme; $("pTheme").hidden = !theme;
  if (theme) renderTheme();
}
$("tSched").onclick = () => { tab(false); try { history.replaceState(null, "", "#schedule"); } catch (e) {} };
$("tTheme").onclick = () => { tab(true); try { history.replaceState(null, "", "#theme"); } catch (e) {} };
$$('[role="tab"]').forEach((t, i, all) => t.addEventListener("keydown", e => {
  if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
  const n = all[(i + (e.key === "ArrowRight" ? 1 : all.length - 1)) % all.length]; n.click(); n.focus();
}));
tab(location.hash === "#theme");
