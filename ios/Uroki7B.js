// Уроки 7Б: версия для iPhone (приложение Scriptable)
// Расписание 7Б, 2 четверть 2026–2027.
// Виджеты: экран блокировки (прямоугольный, круглый, строка над часами)
// и рабочий стол (маленький, средний, большой). Всё в чёрном стиле.
//
// Экономия заряда: скрипт запускается только в момент звонка (~15 раз в день),
// а секундный таймер на виджете тикает силами самой iOS, без запусков скрипта.

// ---------- расписание ----------

const BELLS = [
  [480, 525], [530, 575], [585, 630], [650, 695],
  [715, 760], [770, 815], [825, 870], [880, 925],
];

const SPORT = "спортзал";

// ключ: день недели (1 это понедельник … 5 это пятница), значение: [предмет, кабинет] по номеру урока
const WEEK = {
  1: [
    ["Классный час", "40"],
    ["Казахский яз. и лит.", "42/21"],
    ["История Казахстана", "46"],
    ["Алгебра", "24/35"],
    ["Математика", "24/35"],
    ["Физкультура", SPORT],
    ["Физкультура", SPORT],
    ["Пр. физика", "48/73"],
  ],
  2: [
    ["Геометрия", "24/35"],
    ["Английский язык", "44/47"],
    ["Физика", "48/49"],
    ["Физика", "48/49"],
    ["Программирование", "56/57"],
    ["Биология", "25"],
    ["Худ. труд", "11/1"],
  ],
  3: [
    ["Алгебра", "24/35"],
    ["Алгебра", "24/35"],
    ["Английский язык", "45/47"],
    ["География", "54"],
    ["Физкультура", SPORT],
    ["Русский язык", "46"],
  ],
  4: [
    ["Казахский яз. и лит.", "42/21"],
    ["Алгебра", "24/35"],
    ["Русский язык", "46"],
    ["Русская литература", "46"],
    ["Физика", "48/49"],
    ["Физика", "48/49"],
    ["Всемирная история", "54"],
  ],
  5: [
    ["Геометрия", "24/35"],
    ["Геометрия", "24/35"],
    ["Казахский яз. и лит.", "42/21"],
    ["Английский язык", "25/47"],
    ["Химия", "40"],
    ["Информатика", "56/57"],
    ["Русская литература", "46"],
  ],
};

const DAY_NAMES = ["Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"];

// ---------- логика (та же, что в Android-версии) ----------

function hm(min) {
  const m = min % 60;
  return Math.floor(min / 60) + ":" + (m < 10 ? "0" : "") + m;
}

function roomText(room) {
  if (!room) return "";
  return /^\d/.test(room) ? "каб. " + room : room;
}

function at(day, minutes) {
  return new Date(day.getFullYear(), day.getMonth(), day.getDate(), 0, minutes, 0, 0);
}

function lessons(jsDay) {
  const raw = WEEK[jsDay] || [];
  return raw.map((p, i) => ({
    num: i + 1, start: BELLS[i][0], end: BELLS[i][1], subject: p[0], room: p[1],
  }));
}

function compute(now) {
  const s = { kind: null, current: null, next: null, todayCount: 0 };
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const sec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
  const list = lessons(now.getDay());

  if (list.length) {
    const first = list[0];
    const last = list[list.length - 1];
    s.todayCount = list.length;
    if (sec < first.start * 60) {
      s.kind = "BEFORE";
      s.next = first;
      s.dayEnd = hm(last.end);
      s.start = at(today, first.start - 60);
      s.target = at(today, first.start);
      return s;
    }
    for (let i = 0; i < list.length; i++) {
      const l = list[i];
      if (sec < l.end * 60) {
        if (sec >= l.start * 60) {
          s.kind = "LESSON";
          s.current = l;
          s.next = list[i + 1] || null;
          s.start = at(today, l.start);
          s.target = at(today, l.end);
        } else {
          const prev = list[i - 1];
          s.kind = "BREAK";
          s.current = prev;
          s.next = l;
          s.start = at(today, prev.end);
          s.target = at(today, l.start);
        }
        return s;
      }
    }
    s.kind = "AFTER";
  } else {
    s.kind = "OFF";
  }

  for (let d = 1; d <= 7; d++) {
    const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() + d);
    const l = lessons(date.getDay());
    if (!l.length) continue;
    s.next = l[0];
    s.nextCount = l.length;
    s.nextEnd = hm(l[l.length - 1].end);
    s.nextLabel = d === 1 ? "Завтра" : DAY_NAMES[date.getDay()];
    s.target = at(date, l[0].start);
    break;
  }
  return s;
}

function describe(s, now) {
  const d = { timer: false, progress: -1, dotTotal: 0, dotDone: 0, dotCurrent: 0 };
  const n = s.next;
  const c = s.current;
  d.target = s.target;
  d.nextHead = "ДАЛЬШЕ";
  d.nextSubject = n ? n.subject : "";
  d.nextRoom = n ? n.room : "";
  const pct = () => {
    const total = s.target - s.start;
    return total > 0 ? Math.max(0, Math.min(1, (now - s.start) / total)) : 0;
  };

  if (s.kind === "LESSON") {
    d.timer = true;
    d.progress = pct();
    d.dotTotal = s.todayCount; d.dotDone = c.num - 1; d.dotCurrent = c.num;
    d.label = "УРОК " + c.num + " / " + s.todayCount;
    d.title = c.subject;
    d.subtitle = roomText(c.room) + " · звонок " + hm(c.end);
    d.line1 = "Звонок на перемену в " + hm(c.end);
    d.line2 = n ? "Дальше: " + n.subject + " · " + roomText(n.room) : "Последний урок";
    if (!n) { d.nextSubject = "Домой"; d.nextRoom = hm(c.end); }
    d.lockTop = c.subject + " · до " + hm(c.end);
    d.lockBottom = n ? "→ " + n.room + " · " + n.subject : "→ домой в " + hm(c.end);
    d.inline = c.subject + " до " + hm(c.end) + (n ? " → " + n.room : " → домой");
    d.circleTop = "урок " + c.num;
  } else if (s.kind === "BREAK") {
    d.timer = true;
    d.progress = pct();
    d.dotTotal = s.todayCount; d.dotDone = c.num;
    d.label = "ПЕРЕМЕНА · " + n.num + " / " + s.todayCount;
    d.title = "Перемена";
    d.subtitle = "звонок на урок " + hm(n.start);
    d.line1 = "Звонок на урок в " + hm(n.start);
    d.line2 = "Дальше: " + n.subject + " · " + roomText(n.room);
    d.lockTop = "Перемена · до " + hm(n.start);
    d.lockBottom = "→ " + n.room + " · " + n.subject;
    d.inline = "Перемена до " + hm(n.start) + " → " + n.room;
    d.circleTop = "пер.";
  } else if (s.kind === "BEFORE") {
    d.timer = true;
    d.dotTotal = s.todayCount;
    d.label = "СЕГОДНЯ " + s.todayCount + " УРОКОВ";
    d.title = "До уроков";
    d.subtitle = "первый звонок " + hm(n.start) + " · до " + s.dayEnd;
    d.nextHead = "ПЕРВЫЙ";
    d.line1 = "Первый звонок в " + hm(n.start);
    d.line2 = "Первый урок: " + n.subject + " · " + roomText(n.room);
    d.lockTop = "Уроки с " + hm(n.start);
    d.lockBottom = "→ " + n.room + " · " + n.subject;
    d.inline = "Уроки с " + hm(n.start) + " → " + n.room;
    d.circleTop = "старт";
  } else {
    const after = s.kind === "AFTER";
    d.label = after ? "УРОКИ ЗАКОНЧИЛИСЬ" : "ВЫХОДНОЙ";
    d.title = after ? "Свободен" : "Отдыхай";
    if (n) {
      d.subtitle = s.nextLabel + " · " + s.nextCount + " ур., до " + s.nextEnd;
      d.nextHead = s.nextLabel.toUpperCase() + " " + hm(n.start);
      d.line1 = d.subtitle;
      d.line2 = "Первый урок: " + n.subject + " · " + roomText(n.room);
      d.lockTop = d.title;
      d.lockBottom = hm(n.start) + " → " + n.room + " · " + n.subject;
      d.inline = s.nextLabel + " с " + hm(n.start) + " → " + n.room;
    } else {
      d.subtitle = d.line1 = d.line2 = d.lockBottom = "";
      d.nextHead = "";
      d.lockTop = d.inline = d.title;
    }
    d.circleTop = after ? "всё" : "вых.";
  }
  return d;
}

// Когда iOS стоит перерисовать виджет: ровно на ближайшем звонке, иначе в полночь.
function nextRefresh(s, now) {
  const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 5);
  if (s.kind === "LESSON" || s.kind === "BREAK" || s.kind === "BEFORE") {
    return new Date(s.target.getTime() + 1000);
  }
  return midnight;
}

// ---------- оформление ----------

const BLACK = new Color("#000000");
const WHITE = new Color("#FFFFFF");
const GREY = new Color("#8A8A8A");
const MUTED = new Color("#7A7A7A");
const DIM = new Color("#5C5C5C");
const SOFT = new Color("#E6E6E6");

function txt(parent, str, font, color, opts) {
  const t = parent.addText(str);
  t.font = font;
  if (color) t.textColor = color;
  t.lineLimit = 1;
  t.minimumScaleFactor = (opts && opts.scale) || 0.7;
  return t;
}

function timer(parent, date, size, opts) {
  const t = parent.addDate(date);
  t.applyTimerStyle();
  t.font = (opts && opts.font) || Font.thinSystemFont(size);
  if (!(opts && opts.noColor)) t.textColor = WHITE;
  t.lineLimit = 1;
  t.minimumScaleFactor = 0.5;
  if (opts && opts.right) t.rightAlignText();
  if (opts && opts.center) t.centerAlignText();
  return t;
}

function dots(parent, d) {
  if (!d.dotTotal) return;
  const st = parent.addStack();
  st.spacing = 3;
  st.centerAlignContent();
  for (let i = 1; i <= d.dotTotal; i++) {
    const dot = st.addStack();
    dot.size = new Size(4, 4);
    dot.cornerRadius = 2;
    dot.backgroundColor = i === d.dotCurrent ? WHITE : (i <= d.dotDone ? new Color("#6E6E6E") : new Color("#2E2E2E"));
  }
}

function progressBar(parent, value, width) {
  const ctx = new DrawContext();
  ctx.size = new Size(width, 2);
  ctx.opaque = false;
  ctx.respectScreenScale = true;
  ctx.setFillColor(new Color("#262626"));
  ctx.fillRect(new Rect(0, 0, width, 2));
  ctx.setFillColor(WHITE);
  ctx.fillRect(new Rect(0, 0, Math.max(1, width * value), 2));
  const img = parent.addImage(ctx.getImage());
  img.imageSize = new Size(width, 2);
  return img;
}

function roomChip(parent, room, size) {
  const chip = parent.addStack();
  chip.backgroundColor = new Color("#161616");
  chip.borderColor = new Color("#2A2A2A");
  chip.borderWidth = 1;
  chip.cornerRadius = 7;
  chip.setPadding(2, 7, 2, 7);
  txt(chip, room, Font.mediumSystemFont(size), WHITE);
}

function header(w, d, labelSize) {
  const top = w.addStack();
  top.centerAlignContent();
  txt(top, d.label, Font.mediumSystemFont(labelSize), GREY, { scale: 0.6 });
  top.addSpacer();
  dots(top, d);
}

// маленький 2×2
function smallWidget(d) {
  const w = new ListWidget();
  w.backgroundColor = BLACK;
  w.setPadding(14, 15, 14, 15);
  header(w, d, 9);
  w.addSpacer(5);
  txt(w, d.title, Font.mediumSystemFont(16), WHITE);
  if (d.subtitle) txt(w, d.subtitle, Font.systemFont(11), MUTED, { scale: 0.6 });
  if (d.timer) {
    w.addSpacer(2);
    timer(w, d.target, 32);
  }
  if (d.progress >= 0) {
    w.addSpacer(5);
    progressBar(w, d.progress, 124);
  }
  w.addSpacer();
  if (d.nextSubject) {
    const row = w.addStack();
    row.centerAlignContent();
    txt(row, d.nextSubject, Font.systemFont(12), SOFT, { scale: 0.6 });
    row.addSpacer(4);
    if (d.nextRoom) roomChip(row, d.nextRoom, 10);
  }
  return w;
}

// средний 4×2
function mediumWidget(d) {
  const w = new ListWidget();
  w.backgroundColor = BLACK;
  w.setPadding(14, 18, 14, 18);
  header(w, d, 10);
  w.addSpacer(4);
  const mid = w.addStack();
  mid.centerAlignContent();
  const left = mid.addStack();
  left.layoutVertically();
  left.size = new Size(d.timer ? 170 : 0, 0);
  txt(left, d.title, Font.mediumSystemFont(19), WHITE);
  if (d.subtitle) txt(left, d.subtitle, Font.systemFont(12), MUTED, { scale: 0.6 });
  mid.addSpacer();
  if (d.timer) timer(mid, d.target, 36, { right: true });
  if (d.progress >= 0) {
    w.addSpacer(8);
    progressBar(w, d.progress, 290);
  }
  w.addSpacer();
  if (d.nextSubject) {
    const row = w.addStack();
    row.centerAlignContent();
    txt(row, d.nextHead, Font.mediumSystemFont(9), DIM);
    row.addSpacer(8);
    txt(row, d.nextSubject, Font.systemFont(13), SOFT);
    row.addSpacer();
    if (d.nextRoom) roomChip(row, d.nextRoom, 12);
  }
  return w;
}

// большой: таймер + весь день
function largeWidget(d, s, now) {
  const w = new ListWidget();
  w.backgroundColor = BLACK;
  w.setPadding(16, 18, 16, 18);
  header(w, d, 10);
  w.addSpacer(4);
  const mid = w.addStack();
  mid.centerAlignContent();
  const left = mid.addStack();
  left.layoutVertically();
  left.size = new Size(d.timer ? 170 : 0, 0);
  txt(left, d.title, Font.mediumSystemFont(20), WHITE);
  if (d.subtitle) txt(left, d.subtitle, Font.systemFont(12), MUTED, { scale: 0.6 });
  mid.addSpacer();
  if (d.timer) timer(mid, d.target, 38, { right: true });
  if (d.progress >= 0) {
    w.addSpacer(8);
    progressBar(w, d.progress, 290);
  }
  w.addSpacer(12);

  const showToday = s.kind === "LESSON" || s.kind === "BREAK" || s.kind === "BEFORE";
  let date = now;
  if (!showToday && s.target) date = s.target;
  const list = lessons(date.getDay());
  const nowMin = now.getHours() * 60 + now.getMinutes();
  for (const l of list) {
    const current = showToday && s.kind === "LESSON" && s.current.num === l.num;
    const next = showToday && s.kind !== "LESSON" && s.next && s.next.num === l.num;
    const past = showToday && !current && l.end <= nowMin && s.kind !== "BEFORE";
    const row = w.addStack();
    row.centerAlignContent();
    row.setPadding(3, 8, 3, 8);
    if (current || next) {
      row.backgroundColor = current ? new Color("#1A1A1A") : new Color("#0E0E0E");
      row.cornerRadius = 8;
    }
    const col = past ? new Color("#3C3C3C") : WHITE;
    const sub = past ? new Color("#3C3C3C") : MUTED;
    const num = txt(row, String(l.num), Font.mediumSystemFont(11), sub);
    row.addSpacer(8);
    txt(row, hm(l.start), Font.systemFont(11), sub);
    row.addSpacer(10);
    txt(row, l.subject, current ? Font.semiboldSystemFont(13) : Font.systemFont(13), col);
    row.addSpacer();
    txt(row, l.room, Font.mediumSystemFont(11), current ? WHITE : sub);
    w.addSpacer(2);
  }
  w.addSpacer();
  return w;
}

// экран блокировки: прямоугольный
function lockRectWidget(d) {
  const w = new ListWidget();
  w.setPadding(0, 2, 0, 2);
  txt(w, d.lockTop, Font.semiboldSystemFont(13), null, { scale: 0.7 });
  if (d.timer) {
    timer(w, d.target, 22, { font: Font.boldSystemFont(22), noColor: true });
  } else if (d.lockBottom) {
    txt(w, d.subtitle || "", Font.systemFont(12), null, { scale: 0.6 });
  }
  if (d.lockBottom) {
    const b = txt(w, d.lockBottom, Font.systemFont(13), null, { scale: 0.6 });
    b.textOpacity = 0.85;
  }
  return w;
}

// экран блокировки: круглый
function lockCircleWidget(d) {
  const w = new ListWidget();
  w.addAccessoryWidgetBackground = true;
  w.setPadding(4, 4, 4, 4);
  const top = txt(w, d.circleTop, Font.mediumSystemFont(10), null, { scale: 0.6 });
  top.centerAlignText();
  if (d.timer) {
    timer(w, d.target, 14, { font: Font.semiboldSystemFont(14), noColor: true, center: true });
  }
  const room = d.nextRoom || "";
  if (room) {
    const r = txt(w, room, Font.systemFont(10), null, { scale: 0.5 });
    r.centerAlignText();
    r.textOpacity = 0.8;
  }
  return w;
}

// экран блокировки: строка над часами
function lockInlineWidget(d) {
  const w = new ListWidget();
  w.addText(d.inline);
  return w;
}

// ---------- экран в приложении ----------

function appHtml() {
  const core = [hm, roomText, at, lessons, compute, describe].map(f => f.toString()).join("\n\n");
  return `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<style>
:root{color-scheme:dark}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;background:#000;color:#fff;font:16px -apple-system,system-ui,sans-serif;padding:max(20px,env(safe-area-inset-top)) 22px 40px}
.caps{font-size:11px;letter-spacing:.16em;color:#6e6e6e;font-weight:600;text-transform:uppercase}
.head{display:flex;justify-content:space-between}
#label{margin-top:34px}
#title{font-size:30px;font-weight:300;margin-top:4px}
#timer{font-size:76px;font-weight:100;line-height:1;font-variant-numeric:tabular-nums;margin-top:2px}
.bar{height:2px;background:#262626;margin-top:10px}.bar i{display:block;height:2px;background:#fff;width:0}
#l1{margin-top:16px}#l2{margin-top:4px;color:#6e6e6e}
.chips{display:flex;gap:8px;margin-top:40px;overflow-x:auto}
.chip{border:1px solid #1c1c1c;border-radius:18px;padding:8px 16px;font-size:14px;color:#6e6e6e;font-weight:500}
.chip.today{border-color:#6e6e6e;color:#fff}.chip.sel{background:#fff;border-color:#fff;color:#000}
.list{margin-top:18px}
.row{display:flex;align-items:center;padding:12px 14px;border-radius:16px}
.row.cur{background:#161616;border:1px solid #3a3a3a}.row.nxt{background:#0b0b0b;border:1px solid #1c1c1c}
.row.past{opacity:.3}
.num{width:22px;color:#6e6e6e;font-size:13px;font-weight:600}
.mid{flex:1}.sub{font-size:12px;color:#6e6e6e;margin-top:2px}
.room{border:1px solid #1c1c1c;border-radius:9px;padding:4px 10px;font-size:13px;font-weight:600;color:#9a9a9a}
.row.cur .room{color:#fff}
.brk{font-size:11px;color:#3c3c3c;padding:2px 0 2px 50px}.brk.now{color:#bdbdbd}
#sum{font-size:13px;color:#6e6e6e;margin-top:12px}
.card{margin-top:40px;background:#0b0b0b;border:1px solid #1c1c1c;border-radius:22px;padding:16px 18px;font-size:13px;color:#9a9a9a;line-height:1.5}
.card b{color:#fff;font-weight:600}
</style></head><body>
<div class="head"><span class="caps">Уроки 7Б</span><span class="caps" id="date"></span></div>
<div class="caps" id="label"></div>
<div id="title"></div>
<div id="timer"></div>
<div class="bar" id="bar"><i id="fill"></i></div>
<div id="l1"></div><div id="l2"></div>
<div class="chips" id="chips"></div>
<div class="list" id="list"></div>
<div id="sum"></div>
<div class="card"><b>Как добавить виджет</b><br>
Экран блокировки: зажми его → «Настроить» → экран блокировки → нажми на поле с виджетами → Scriptable → прямоугольный. Потом нажми на добавленный виджет и в поле Script выбери «Уроки 7Б».<br><br>
Рабочий стол: зажми пустое место → «+» (или «Изменить» → «Добавить виджет») → Scriptable → выбери размер. Нажми на виджет и выбери скрипт «Уроки 7Б».</div>
<script>
const BELLS=${JSON.stringify(BELLS)};
const WEEK=${JSON.stringify(WEEK)};
const DAY_NAMES=${JSON.stringify(DAY_NAMES)};
${core}
const MONTHS=["января","февраля","марта","апреля","мая","июня","июля","августа","сентября","октября","ноября","декабря"];
const SHORT=["ВС","ПН","ВТ","СР","ЧТ","ПТ","СБ"];
let sel=null;
function two(n){return (n<10?"0":"")+n}
function fmt(ms){if(ms<0)ms=0;const t=Math.floor(ms/1000),h=Math.floor(t/3600),m=Math.floor(t%3600/60),s=t%60;return h?h+":"+two(m)+":"+two(s):two(m)+":"+two(s)}
function defDay(now,s){const wd=now.getDay();if(wd>=1&&wd<=5&&s.kind!=="AFTER")return wd;for(let i=1;i<=7;i++){const x=new Date(now.getFullYear(),now.getMonth(),now.getDate()+i).getDay();if(x>=1&&x<=5)return x}return 1}
function render(){
 const now=new Date(),s=compute(now),d=describe(s,now);
 document.getElementById("date").textContent=SHORT[now.getDay()]+", "+now.getDate()+" "+MONTHS[now.getMonth()];
 document.getElementById("label").textContent=d.label;
 document.getElementById("title").textContent=d.title;
 const tm=document.getElementById("timer");tm.style.display=d.timer?"block":"none";if(d.timer)tm.textContent=fmt(d.target-now);
 const bar=document.getElementById("bar");bar.style.display=d.progress>=0?"block":"none";document.getElementById("fill").style.width=(Math.max(0,d.progress)*100)+"%";
 document.getElementById("l1").textContent=d.line1||"";document.getElementById("l2").textContent=d.line2||"";
 if(sel===null)sel=defDay(now,s);
 const chips=document.getElementById("chips");chips.innerHTML="";
 ["Пн","Вт","Ср","Чт","Пт"].forEach((n,i)=>{const c=document.createElement("div");c.className="chip"+(i+1===now.getDay()?" today":"")+(i+1===sel?" sel":"");c.textContent=n;c.onclick=()=>{sel=i+1;render()};chips.appendChild(c)});
 const list=document.getElementById("list");list.innerHTML="";
 const isToday=sel===now.getDay(),nowMin=now.getHours()*60+now.getMinutes(),ls=lessons(sel);
 ls.forEach((l,i)=>{
  if(i>0){const b=document.createElement("div");const bn=isToday&&s.kind==="BREAK"&&s.next&&s.next.num===l.num;b.className="brk"+(bn?" now":"");b.textContent=(bn?"● перемена сейчас · ":"перемена ")+(l.start-ls[i-1].end)+" мин";list.appendChild(b)}
  const cur=isToday&&s.kind==="LESSON"&&s.current.num===l.num;
  const nx=isToday&&(s.kind==="BREAK"||s.kind==="BEFORE")&&s.next&&s.next.num===l.num;
  const past=isToday&&!cur&&l.end<=nowMin&&s.kind!=="BEFORE";
  const r=document.createElement("div");r.className="row"+(cur?" cur":nx?" nxt":"")+(past?" past":"");
  r.innerHTML='<div class="num">'+l.num+'</div><div class="mid"><div>'+l.subject+'</div><div class="sub">'+hm(l.start)+' – '+hm(l.end)+(cur?"   · сейчас":nx?"   · дальше":"")+'</div></div><div class="room">'+l.room+'</div>';
  list.appendChild(r)});
 document.getElementById("sum").textContent=ls.length?ls.length+" уроков · "+hm(ls[0].start)+"–"+hm(ls[ls.length-1].end):"";
}
render();setInterval(render,1000);
</script></body></html>`;
}

// ---------- запуск ----------

const now = new Date();
const status = compute(now);
const display = describe(status, now);

if (config.runsInWidget || config.runsInAccessoryWidget) {
  const family = config.widgetFamily;
  let widget;
  if (family === "accessoryRectangular") widget = lockRectWidget(display);
  else if (family === "accessoryCircular") widget = lockCircleWidget(display);
  else if (family === "accessoryInline") widget = lockInlineWidget(display);
  else if (family === "medium") widget = mediumWidget(display);
  else if (family === "large" || family === "extraLarge") widget = largeWidget(display, status, now);
  else widget = smallWidget(display);
  widget.refreshAfterDate = nextRefresh(status, now);
  Script.setWidget(widget);
} else {
  const wv = new WebView();
  await wv.loadHTML(appHtml());
  await wv.present(true);
}
Script.complete();
