/* Звонок · iPhone: анимация гайда. Шаги, «палец» и экраны телефона; на reduced-motion без движения */
(() => {
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const D = $('#display'), phone = $('#phone'), pwrap = $('#pwrap'), finger = $('#finger');
const RM = matchMedia('(prefers-reduced-motion: reduce)').matches;
let S = 1;

/* ---------- sizing ---------- */
function fit(){
  const col = pwrap.parentElement.getBoundingClientRect().width || 330;
  const availH = Math.max(480, innerHeight - 190);
  S = Math.min(1, (col - 4) / 316, availH / 666);
  phone.style.transform = `scale(${S})`;
  pwrap.style.width = 316 * S + 'px';
  pwrap.style.height = 666 * S + 'px';
}
addEventListener('resize', fit);

/* ---------- clones of the lock face ---------- */
const face = $('#s-lock .lockface');
$$('[data-clone="lock"]').forEach(el => { const c = face.cloneNode(true); c.querySelectorAll('[id]').forEach(n => n.removeAttribute('id')); el.appendChild(c); });
const live = $('[data-clone="lock-live"]');
const lc = face.cloneNode(true);
lc.querySelectorAll('[id]').forEach(n => n.removeAttribute('id'));
live.replaceWith(lc);
lc.id = 'le-face';
lc.querySelector('.l-wrow').id = 'le-slot';
lc.querySelector('.lw').id = 'le-lw';

/* ---------- fake clock: Tuesday 11:12, lesson 4 ends 11:35 ---------- */
const T0 = Date.now(), START = 11 * 3600 + 12 * 60 + 8, END = 11 * 3600 + 35 * 60, LSTART = 10 * 3600 + 50 * 60;
function tick(){
  const now = START + Math.floor((Date.now() - T0) / 1000) % 900;
  const rem = END - now;
  const t = Math.floor(rem / 60) + ':' + String(rem % 60).padStart(2, '0');
  const hm = Math.floor(now / 3600) + ':' + String(Math.floor(now / 60) % 60).padStart(2, '0');
  $$('[data-timer]').forEach(e => e.textContent = t);
  $$('[data-clock],[data-sbclock]').forEach(e => e.textContent = hm);
  $$('[data-prog]').forEach(e => e.style.width = ((now - LSTART) / (END - LSTART) * 100).toFixed(1) + '%');
}
tick();
let clockT = 0;
// часы в макете идут, только пока телефон на экране
// уже идут и нужно идти (или стоят и нужно стоять): ничего не трогаем, интервал не пересоздаётся
function clock(on){ if (!!clockT === on) return; clearInterval(clockT); clockT = 0; if (on) { tick(); clockT = setInterval(tick, 1000); } }

/* ---------- keyboard ---------- */
const KB = { en: ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'], ru: ['йцукенгшщзх', 'фывапролджэ', 'ячсмитьбю'] };
const kb = $('#kb');
const shiftSvg = '<svg viewBox="0 0 18 18" fill="none" stroke="#fff" stroke-width="1.5" stroke-linejoin="round"><path d="M9 2.5 2.5 9.5H6v6h6v-6h3.5z"/></svg>';
const delSvg = '<svg viewBox="0 0 18 18" fill="none" stroke="#fff" stroke-width="1.5" stroke-linejoin="round"><path d="M6 3.5h9.5v11H6L1.5 9z"/><path d="M8.5 6.5l4 5M12.5 6.5l-4 5" stroke-linecap="round"/></svg>';
const globeSvg = '<svg viewBox="0 0 18 18" fill="none" stroke="#fff" stroke-width="1.3"><circle cx="9" cy="9" r="6.5"/><path d="M2.5 9h13M9 2.5c-2.5 3-2.5 10 0 13M9 2.5c2.5 3 2.5 10 0 13"/></svg>';
for (const l of ['en', 'ru']) {
  const r = KB[l];
  const keys = s => [...s].map(c => `<span class="k" data-k="${c}">${c}</span>`).join('');
  kb.insertAdjacentHTML('beforeend', `<div class="kb-l" data-l="${l}">
    <div class="kr">${keys(r[0])}</div>
    <div class="kr">${keys(r[1])}</div>
    <div class="kr"><span class="k sp" data-k="shift">${shiftSvg}</span>${keys(r[2])}<span class="k sp" data-k="del">${delSvg}</span></div>
    <div class="kr"><span class="k sp">123</span><span class="k sp">${globeSvg}</span><span class="k wide" data-k=" ">${l === 'ru' ? 'Пробел' : 'space'}</span><span class="k ret">return</span></div>
  </div>`);
}
function kbShow(lang, ret, blue){ kb.dataset.l = lang; const r = $(`.kb-l[data-l="${lang}"] .ret`, kb); r.textContent = ret; r.classList.toggle('blue', !!blue); kb.classList.add('x-on'); }
function kbHide(){ kb.classList.remove('x-on'); }
const kbRet = () => $(`.kb-l[data-l="${kb.dataset.l}"] .ret`, kb);
async function hit(k){ const el = $(`.kb-l[data-l="${kb.dataset.l}"] [data-k="${CSS.escape(k)}"]`, kb); if (!el) return; el.classList.add('x-hit'); await wait(90); el.classList.remove('x-hit'); }

/* ---------- code for the editor ---------- */
const CODE = [
'function describe(s, now) {',
'  const d = { timer: false, progress: -1 };',
'  const n = s.next;',
'  const c = s.current;',
'  d.target = s.target;',
'  if (s.kind === "LESSON") {',
'    d.timer = true;',
'    d.title = c.subject;',
'    d.lockTop = c.subject + " · до " + hm(c.end);',
'    d.lockBottom = "→ " + n.room + " · " + n.subject;',
'  }',
'  return d;',
'}',
'',
'// экран блокировки: прямоугольный',
'function lockRectWidget(d) {',
'  const w = new ListWidget();',
'  w.setPadding(0, 2, 0, 2);',
'  txt(w, d.lockTop, Font.semiboldSystemFont(13));',
'  timer(w, d.target, 22);',
'  return w;',
'}',
'',
'const now = new Date();',
'const status = compute(now);',
'const display = describe(status, now);',
'widget.refreshAfterDate = nextRefresh(status, now);',
'Script.setWidget(widget);',
'Script.complete();'
];
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
function hl(line){
  return esc(line).replace(/(\/\/.*$)|("(?:[^"\\]|\\.)*")|\b(function|const|let|if|else|return|new|true|false)\b|\b(\d+)\b|\b([a-zA-Z]+)(?=\()/g,
    (m, c, s, k, n, f) => c ? `<span class="tc">${c}</span>` : s ? `<span class="ts">${s}</span>` : k ? `<span class="tk">${k}</span>` : n ? `<span class="tn">${n}</span>` : `<span class="tf">${f}</span>`);
}
function fillCode(anim){
  const el = $('#ed-code');
  el.classList.toggle('x-paste', !!anim);
  el.innerHTML = CODE.map((l, i) => `<div class="ln" style="animation-delay:${i * 18}ms"><span class="no">${i + 1}</span><span>${hl(l) || ' '}</span></div>`).join('');
}

/* ---------- timeline engine ---------- */
const CANCEL = Symbol('cancel');
let runId = 0, paused = false, cur = null, z = 10;
// Сон без опроса: один таймер на всё оставшееся время. Пауза, «дальше» и «назад» будят спящих сразу,
// а на паузе спим, пока не разбудят (раньше тут проверка шла 25 раз в секунду, даже на паузе).
const wakers = new Set();
function nap(ms){
  return new Promise(r => {
    const w = () => { clearTimeout(t); wakers.delete(w); r(); };
    const t = setTimeout(w, ms);
    wakers.add(w);
  });
}
const wakeAll = () => [...wakers].forEach(w => w());
async function wait(ms){
  const id = runId; let left = RM ? ms * .75 : ms;
  while (left > 0) {
    if (id !== runId) throw CANCEL;
    if (paused) { await nap(864e5); continue; }
    const t0 = performance.now();
    await nap(left);
    left -= performance.now() - t0;
  }
  if (id !== runId) throw CANCEL;
}
function pt(el){
  const d = D.getBoundingClientRect(), r = el.getBoundingClientRect();
  return { x: (r.left + r.width / 2 - d.left) / S, y: (r.top + r.height / 2 - d.top) / S };
}
let fx = 150, fy = 700;
const rnd = (a, b) => a + Math.random() * (b - a);
const place = (x, y) => { finger.style.transform = `translate(${x}px, ${y}px)`; };
// A real thumb never travels in a ruler-straight line: move along a gently bent curve, ease in and out.
// Путь считаем заранее (кадр на каждые ~16 мс) и отдаём браузеру одной анимацией transform: её крутит
// поток компоновки, а скрипт и стили страницы не просыпаются 60 раз в секунду, как было с requestAnimationFrame.
function glide(x, y, dur){
  const id = runId, x0 = fx, y0 = fy;
  const dx = x - x0, dy = y - y0, len = Math.hypot(dx, dy) || 1;
  const bend = rnd(.06, .2) * len * (Math.random() < .5 ? -1 : 1);
  const cx = (x0 + x) / 2 - dy / len * bend, cy = (y0 + y) / 2 + dx / len * bend;
  const at = k => { const e = k < .5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2, u = 1 - e;
    return [u * u * x0 + 2 * u * e * cx + e * e * x, u * u * y0 + 2 * u * e * cy + e * e * y]; };
  const n = Math.max(12, Math.round(dur / 16)), frames = [];
  for (let i = 0; i <= n; i++) { const p = at(i / n); frames.push({transform: `translate(${p[0].toFixed(2)}px, ${p[1].toFixed(2)}px)`}); }
  place(x, y); // под анимацией уже стоит конечная точка: когда она кончится, палец не прыгнет назад
  const a = finger.animate(frames, {duration: dur, easing: 'linear'});
  if (paused) a.pause();
  return new Promise((res, rej) => {
    // пауза, «дальше» и «назад» будят всех ждущих (wakeAll): тут ставим анимацию на паузу или снимаем
    const check = () => {
      wakers.delete(check);
      if (id !== runId) {
        const p = at(Math.min(1, (a.currentTime || 0) / dur));
        a.cancel(); fx = p[0]; fy = p[1]; place(fx, fy);
        return rej(CANCEL);
      }
      if (paused) a.pause(); else if (a.playState === 'paused') a.play();
      wakers.add(check);
    };
    wakers.add(check);
    a.onfinish = () => { wakers.delete(check); fx = x; fy = y; a.cancel(); res(); };
  });
}
async function go(sel, o = {}){
  const el = typeof sel === 'string' ? $(sel, D) : sel;
  const p = pt(el);
  // people rarely hit the exact centre of a button
  const x = p.x + (o.dx || 0) + rnd(-3, 3), y = p.y + (o.dy || 0) + rnd(-2.5, 2.5);
  if (!finger.classList.contains('x-show')) { fx = x + rnd(20, 50); fy = y + rnd(70, 110); place(fx, fy); void finger.offsetWidth; finger.classList.add('x-show'); }
  const dist = Math.hypot(x - fx, y - fy);
  const dur = o.dur || Math.round(Math.min(950, Math.max(360, dist * rnd(1.4, 1.9))));
  await glide(x, y, RM ? dur * .6 : dur);
  await wait(rnd(110, 300));
}
function ripple(){
  if (RM) return;
  const r = document.createElement('div'); r.className = 'rip';
  r.style.left = fx + 'px'; r.style.top = fy + 'px';
  D.appendChild(r); setTimeout(() => r.remove(), 600);
}
async function tap(){
  finger.classList.add('x-press'); ripple();
  await wait(170);
  finger.classList.remove('x-press');
  await wait(160);
}
async function hold(ms, onPress){
  finger.style.setProperty('--hold', ms + 'ms');
  finger.classList.add('x-press', 'x-hold');
  onPress && onPress(true);
  await wait(ms);
  finger.classList.remove('x-press', 'x-hold');
  onPress && onPress(false);
  ripple();
  await wait(120);
}
async function drag(dx, ms = 520){
  finger.classList.add('x-press');
  await wait(140);
  await glide(fx + dx, fy + rnd(-6, 6), ms);
}
function hideFinger(){ finger.classList.remove('x-show', 'x-press', 'x-hold'); }
async function type(sel, str, first){
  const el = $(sel, D);
  for (let i = 0; i < str.length; i++) {
    const ch = str[i];
    if (ch !== ch.toLowerCase()) { await hit('shift'); }
    hit(ch.toLowerCase()).catch(() => {});
    if (i === 0 && first) first();
    el.textContent += ch;
    // uneven rhythm, with the odd short think
    await wait(rnd(85, 210) + (Math.random() < .15 ? rnd(150, 320) : 0));
  }
}
const add = (sel, ...c) => $(sel, D).classList.add(...c);
const rem = (sel, ...c) => $(sel, D).classList.remove(...c);

function syncSB(){ D.classList.toggle('x-lockmode', !!cur && cur.hasAttribute('data-nosbtime')); }
function show(id, enter){
  const s = $(id, D); s.dataset.enter = enter || 'fade';
  s.style.zIndex = ++z; s.classList.add('x-on'); cur = s; syncSB();
}
async function open(id, enter, originSel){
  const inc = $(id, D), out = cur;
  inc.dataset.enter = enter;
  if (originSel) { const p = pt($(originSel, D)); inc.style.transformOrigin = `${p.x}px ${p.y}px`; }
  else inc.style.transformOrigin = '50% 50%';
  D.classList.add('noanim'); inc.classList.remove('x-on'); void inc.offsetWidth; D.classList.remove('noanim'); void inc.offsetWidth;
  inc.style.zIndex = ++z;
  inc.classList.add('x-on'); cur = inc; syncSB();
  await wait(520);
  if (out && out !== inc) { out.dataset.enter = 'fade'; out.classList.remove('x-on'); }
}
async function back(id){
  const to = $(id, D), out = cur;
  to.dataset.enter = 'fade';
  to.style.zIndex = z - 1; to.classList.add('x-on');
  cur = to; syncSB();
  out.classList.remove('x-on');
  await wait(520);
  to.style.zIndex = ++z;
}

function hardReset(){
  D.classList.add('noanim');
  [D, ...$$('*', D)].forEach(el => {
    if (el.classList) [...el.classList].forEach(c => c.startsWith('x-') && el.classList.remove(c));
  });
  $$('.scr', D).forEach(s => { s.style.zIndex = ''; s.style.transformOrigin = ''; s.dataset.enter = 'fade'; });
  $('#as-q').textContent = '';
  $('#ed-title').textContent = 'Untitled Script';
  $('#ed-code').innerHTML = '';
  $('#sf-sel-v').textContent = 'Класс 7А';
  $('#sf-copy').textContent = 'Скопировать скрипт';
  $('#cfg-choose').textContent = 'Choose'; rem('#cfg-choose', 'set');
  D.dataset.lw = 'none'; D.dataset.hw = 'none';
  hideFinger(); fx = 150; fy = 700;
  $$('.rip', D).forEach(r => r.remove());
  cur = null; z = 10;
}
function settle(){ void D.offsetWidth; requestAnimationFrame(() => requestAnimationFrame(() => D.classList.remove('noanim'))); }

/* ---------- steps ---------- */
const STEPS = [
{
  chip: 'Scriptable',
  title: 'Установи Scriptable',
  txt: 'Это бесплатное приложение для виджетов. В App Store найди <q>Scriptable</q> от Simon B. Støvring, установи и <b>открой один раз</b>. Без этого его не будет в списке виджетов.',
  beats: ['Открой App Store', 'В поиске набери Scriptable', 'Нажми «Загрузить»', 'Нажми «Открыть»'],
  tip: 'Если App Store попросит подтвердить, подтверди через Face ID или пароль Apple ID. <b>Приложение бесплатное.</b>',
  setup(){ show('#s-home'); },
  async run(B){
    B(0); await go('#a-appstore .ic'); await tap(); await open('#s-appstore', 'zoom', '#a-appstore .ic');
    B(1); await go('#as-field'); await tap(); add('#as-field', 'x-focus'); kbShow('en', 'search', true); await wait(450);
    await type('#as-q', 'Scriptable'); await wait(250);
    await go(kbRet()); await tap(); kbHide(); rem('#as-field', 'x-focus'); add('#s-appstore', 'x-results'); await wait(700);
    B(2); await go('#as-get'); await tap(); add('#as-get', 'x-loading'); await wait(2300); add('#as-get', 'x-done'); await wait(500);
    B(3); await go('#as-get'); await tap(); hideFinger(); await open('#s-scr', 'zoom', '#as-get'); add('#s-home', 'x-has'); await wait(1000);
  }
},
{
  chip: 'Скрипт',
  title: 'Скопируй скрипт своего класса',
  txt: 'Открой эту страницу в Safari. Вверху, в блоке <q>Скрипт виджета</q>, выбери свой класс и нажми <q>Скопировать скрипт</q>.',
  beats: ['Открой эту страницу в Safari', 'Выбери свой класс', 'Нажми «Скопировать скрипт»'],
  tip: 'Расписание твоего класса уже внутри скрипта, поэтому виджет работает и офлайн. Если интернет есть, он сам подтянет замены.',
  setup(){ show('#s-home'); add('#s-home', 'x-has'); },
  async run(B){
    B(0); await go('#d-safari'); await tap(); await open('#s-safari', 'zoom', '#d-safari'); await wait(500);
    B(1); await go('#sf-sel'); await tap(); add('#sf-menu', 'x-on'); await wait(600);
    await go('#sf-opt'); await tap(); rem('#sf-menu', 'x-on'); $('#sf-sel-v').textContent = 'Класс 7Б'; await wait(600);
    B(2); await go('#sf-copy'); await tap(); $('#sf-copy').textContent = 'Скопировано'; add('#sf-copy', 'x-ok'); add('#toast', 'x-on'); hideFinger(); await wait(1800); rem('#toast', 'x-on'); await wait(400);
  }
},
{
  chip: 'Вставка',
  title: 'Вставь скрипт в Scriptable',
  txt: 'Открой Scriptable и нажми <q>+</q> справа сверху. Откроется пустой скрипт: нажми в поле и выбери <q>Вставить</q>.',
  beats: ['Нажми «+» справа сверху', 'Нажми в пустое поле', 'Выбери «Вставить»', 'Скрипт на месте'],
  tip: 'Скрипт длинный, так и должно быть. Ничего в нём не меняй.',
  setup(){ show('#s-scr'); add('#s-home', 'x-has'); },
  async run(B){
    B(0); await go('#sc-plus'); await tap(); await open('#s-editor', 'up'); await wait(300);
    B(1); await go('#ed-area', { dy: -170, dx: -40 }); await tap(); add('#s-editor', 'x-focus'); kbShow('en', 'return'); await wait(450); add('#callout', 'x-on'); await wait(500);
    B(2); await go('#callout-paste'); await tap(); rem('#callout', 'x-on'); fillCode(true); hideFinger(); await wait(1100);
    B(3); kbHide(); rem('#s-editor', 'x-focus'); await wait(500); add('#ed-code', 'x-scrolled'); await wait(1400); rem('#ed-code', 'x-scrolled'); await wait(1300);
  }
},
{
  chip: 'Название',
  title: 'Назови его «Звонок»',
  txt: 'Нажми на <q>Untitled Script</q> сверху и введи <q>Звонок</q>. Потом нажми <q>Done</q> слева сверху. Scriptable на английском: Done значит «Готово».',
  beats: ['Нажми на «Untitled Script»', 'Введи «Звонок»', 'Нажми «Done»', 'Скрипт сохранён'],
  tip: 'Название нужно, чтобы потом быстро найти скрипт, когда будешь настраивать виджет.',
  setup(){ show('#s-editor', 'up'); fillCode(false); add('#s-home', 'x-has'); },
  async run(B){
    B(0); await go('#ed-title'); await tap(); add('#s-editor', 'x-rename'); kbShow('ru', 'Готово', true); await wait(650);
    B(1); await type('#ed-title', 'Звонок', () => { $('#ed-title').textContent = ''; add('#s-editor', 'x-typing'); }); await wait(500);
    B(2); await go('#ed-done'); await tap(); kbHide(); rem('#s-editor', 'x-rename', 'x-typing'); hideFinger(); await wait(250); await back('#s-scr');
    B(3); add('#s-scr', 'x-tile'); await wait(1500);
  }
},
{
  chip: 'Настройка',
  title: 'Открой настройку экрана блокировки',
  txt: 'Разблокируй телефон через Face ID или Touch ID, но <b>не смахивай вверх</b>. Зажми экран, нажми <q>Настроить</q> и выбери <q>Экран блокировки</q>.',
  beats: ['Разблокируй, не смахивая', 'Зажми экран на секунду', 'Нажми «Настроить»', 'Выбери «Экран блокировки»'],
  tip: 'Если «Настроить» не появилось, значит телефон ещё заблокирован. Замок вверху должен быть открыт.',
  setup(){ show('#s-lock'); add('#s-home', 'x-has'); },
  async run(B){
    B(0); await wait(400); add('#s-lock', 'x-faceid'); await wait(1300); add('#s-lock', 'x-unlocked'); await wait(300); rem('#s-lock', 'x-faceid'); await wait(900);
    B(1); await go('#l-center'); await hold(1100, on => $('#s-lock').classList.toggle('x-press', on)); await open('#s-gallery', 'shrink'); await wait(300);
    B(2); await go('#g-custom'); await tap(); await open('#s-choose', 'fade'); await wait(400);
    B(3); await go('#ch-lock .mini'); await tap(); await open('#s-ledit', 'grow', '#ch-lock .mini'); hideFinger(); await wait(900);
  }
},
{
  chip: 'Виджет',
  title: 'Добавь виджет Scriptable',
  txt: 'Нажми на поле под часами. В списке найди <q>Scriptable</q> и выбери <b>прямоугольный</b> виджет. Потом закрой список крестиком.',
  beats: ['Нажми на поле под часами', 'Найди Scriptable', 'Выбери прямоугольный виджет', 'Закрой список'],
  tip: 'Пока скрипт не выбран, виджет пишет «Select script in widget configurator». Это нормально, исправим на следующем шаге.',
  setup(){ show('#s-ledit'); add('#s-home', 'x-has'); },
  async run(B){
    B(0); await go('#le-slot'); await tap(); add('#wsheet', 'x-on'); await wait(800);
    B(1); add('#wsheet', 'x-scroll'); await wait(700); await go('#ws-scriptable'); await tap(); add('#wsheet', 'x-app'); await wait(700);
    B(2); await go('#ws-rect'); await tap(); D.dataset.lw = 'blank'; add('#le-lw', 'x-pop'); await wait(1000);
    B(3); await go('#ws-close'); await tap(); rem('#wsheet', 'x-on'); hideFinger(); await wait(1200);
  }
},
{
  chip: 'Выбор скрипта',
  title: 'Выбери скрипт «Звонок»',
  txt: 'Нажми на новый виджет. В строке <q>Script</q> нажми <q>Choose</q> и выбери <q>Звонок</q>. Нажми <q>Готово</q> справа сверху. Всё, таймер на экране блокировки.',
  beats: ['Нажми на виджет', 'Script → Choose', 'Выбери «Звонок»', 'Нажми «Готово»', 'Таймер тикает сам'],
  tip: 'Виджет показывает урок, кабинет и сколько осталось до звонка. Дальше по желанию: виджет на рабочий стол.',
  setup(){ show('#s-ledit'); D.dataset.lw = 'blank'; add('#s-home', 'x-has'); },
  async run(B){
    B(0); await go('#le-lw'); await tap(); add('#config', 'x-on'); await wait(650);
    B(1); await go('#cfg-choose'); await tap(); add('#config', 'x-list'); await wait(650);
    B(2); await go('#cfg-zvonok'); await tap(); rem('#config', 'x-list'); $('#cfg-choose').textContent = 'Звонок'; add('#cfg-choose', 'set'); await wait(800);
    await go('#le-empty'); await tap(); rem('#config', 'x-on'); D.dataset.lw = 'real'; rem('#le-lw', 'x-pop'); void $('#le-lw').offsetWidth; add('#le-lw', 'x-pop'); await wait(1000);
    B(3); await go('#le-done'); await tap(); await open('#s-gallery', 'shrink'); await wait(450); await go('#g-card'); await tap(); hideFinger(); add('#s-lock', 'x-unlocked'); await open('#s-lock', 'grow');
    B(4); await wait(300); add('#lw', 'x-glow'); await wait(3400);
  }
},
{
  chip: 'Рабочий стол',
  opt: true,
  title: 'Виджет на рабочий стол',
  txt: 'Это не обязательно: таймер на экране блокировки уже работает. Если хочешь ещё и на рабочем столе, зажми пустое место, нажми <q>Изменить</q> → <q>Добавить виджет</q>. Найди <q>Scriptable</q>, выбери размер и нажми <q>Добавить виджет</q>. Потом зажми виджет → <q>Изменить виджет</q> → Script → <q>Звонок</q>.',
  beats: ['Посмотри, как он будет выглядеть', 'Зажми пустое место', '«Изменить» → «Добавить виджет»', 'Scriptable → размер → «Добавить»', 'Зажми виджет → «Изменить виджет»', 'Script → «Звонок»', 'Готово: так он выглядит'],
  tip: 'Есть три размера: маленький, средний и большой (с расписанием на весь день). Как они выглядят, показано ниже. На iOS 17 вместо «Изменить» сверху слева будет кнопка «+».',
  setup(){ show('#s-home'); add('#s-home', 'x-has'); D.dataset.lw = 'real'; },
  async run(B){
    B(0); add('#promo', 'x-on'); await wait(1300); add('#promo-mw', 'x-glow'); await wait(3400);
    await go('#promo-go'); await tap(); rem('#promo', 'x-on'); await wait(650);
    B(1); await go('#h-empty'); await hold(1000); add('#s-home', 'x-jiggle'); await wait(600);
    B(2); await go('#h-edit'); await tap(); add('#h-menu', 'x-on'); await wait(550); await go('#hm-add'); await tap(); rem('#h-menu', 'x-on'); add('#wgal', 'x-on'); await wait(800);
    B(3); await go('#wg-scriptable'); await tap(); add('#wgal', 'x-app'); await wait(800);
    await go('#wgal .wg-view', { dx: 70 }); await drag(-150); add('#wgal', 'x-medium'); finger.classList.remove('x-press'); await wait(700);
    await go('#wg-add'); await tap(); rem('#wgal', 'x-on'); D.dataset.hw = 'blank'; add('#s-home', 'x-widget'); await wait(900);
    await go('#h-done'); await tap(); rem('#s-home', 'x-jiggle'); await wait(600);
    B(4); await go('#h-mw'); await hold(700); add('#s-home', 'x-ctx'); await wait(600); await go('#ctx-edit'); await tap(); rem('#s-home', 'x-ctx'); add('#config', 'x-home', 'x-on'); await wait(650);
    B(5); await go('#cfg-choose'); await tap(); add('#config', 'x-list'); await wait(650);
    await go('#cfg-zvonok'); await tap(); rem('#config', 'x-list'); $('#cfg-choose').textContent = 'Звонок'; add('#cfg-choose', 'set'); await wait(800);
    await go('#cfg-back', { dy: -230 }); await tap(); rem('#config', 'x-on'); hideFinger(); await wait(350); rem('#config', 'x-home'); D.dataset.hw = 'real'; add('#h-mw', 'x-pop'); await wait(900);
    B(6); add('#h-mw', 'x-glow'); await wait(3600);
  }
}
];

/* ---------- panel ---------- */
const chips = $('#chips'), stepEl = $('#step'), beatsEl = $('#beats'), capText = $('#cap-text'), capNum = $('#cap-num'), bar = $('#bar');
// колёсико мыши листает шаги вбок (на ПК)
chips.addEventListener('wheel', e => {
  if (chips.scrollWidth <= chips.clientWidth || Math.abs(e.deltaX) > Math.abs(e.deltaY)) return;
  e.preventDefault(); chips.scrollLeft += e.deltaY;
}, { passive: false });
const check = '<svg viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2.5 6.5l2.5 2.5 4.5-5.5"/></svg>';
const tipIcon = '<svg viewBox="0 0 18 18" fill="none" stroke="currentColor" stroke-width="1.6"><circle cx="9" cy="9" r="7.2"/><path d="M9 8.2v4.6M9 5.4v.1" stroke-linecap="round"/></svg>';
STEPS.forEach((s, i) => {
  const b = document.createElement('button');
  b.className = 'chip'; b.type = 'button';
  b.innerHTML = `<b>${i + 1}</b>${s.chip}${s.opt ? '<i>по желанию</i>' : ''}`;
  b.addEventListener('click', () => play(i));
  chips.appendChild(b);
});
let idx = 0, done = new Set();
function render(i){
  const s = STEPS[i];
  $$('.chip', chips).forEach((c, j) => { c.classList.toggle('on', j === i); c.classList.toggle('done', done.has(j) && j !== i); });
  const on = $$('.chip', chips)[i];
  chips.scrollTo({ left: on.offsetLeft - 16, behavior: RM ? 'auto' : 'smooth' });
  stepEl.classList.add('swap');
  setTimeout(() => {
    $('#step-of').innerHTML = `Шаг ${i + 1} из ${STEPS.length}` + (s.opt ? '<span class="opt-badge">по желанию</span>' : '');
    $('#step-title').textContent = s.title;
    $('#step-txt').innerHTML = s.txt;
    beatsEl.innerHTML = s.beats.map((b, j) => `<li><span class="dot"><span>${j + 1}</span>${check}</span>${b}</li>`).join('');
    $('#tip').innerHTML = s.tip ? tipIcon + `<span>${s.tip}</span>` : '';
    stepEl.classList.remove('swap');
  }, 180);
  setCap(i, s.beats[0], true);
  bar.style.transform = 'scaleX(0)';
}
function setCap(i, text, quiet){
  capNum.textContent = i + 1;
  capText.classList.add('swap');
  setTimeout(() => { capText.textContent = text; capText.classList.remove('swap'); }, quiet ? 0 : 160);
}
function beat(i, j){
  const s = STEPS[i];
  $$('li', beatsEl).forEach((li, k) => { li.classList.toggle('ok', k < j); li.classList.toggle('now', k === j); });
  setCap(i, s.beats[j]);
  bar.style.transform = 'scaleX(' + (j / s.beats.length) + ')';
}
function finishBeats(i){
  $$('li', beatsEl).forEach(li => { li.classList.remove('now'); li.classList.add('ok'); });
  bar.style.transform = 'scaleX(1)';
  done.add(i);
  $$('.chip', chips)[i].classList.add('done');
}

/* ---------- playback ---------- */
const bPlay = $('#b-play');
function setPaused(p){
  paused = p;
  D.classList.toggle('is-paused', p);
  $('#i-pause').hidden = p; $('#i-play').hidden = !p;
  bPlay.setAttribute('aria-label', p ? 'Смотреть' : 'Пауза');
  wakeAll();
}
let ended = false;
async function play(i){
  const id = ++runId;
  wakeAll(); // прошлый показ просыпается и тихо заканчивается
  idx = i; ended = false;
  if (paused) setPaused(false);
  hardReset();
  render(i);
  STEPS[i].setup();
  settle();
  try {
    await wait(900);
    await STEPS[i].run(j => beat(i, j));
    finishBeats(i);
    if (i < STEPS.length - 1) { await wait(1300); if (id === runId) play(i + 1); }
    else { ended = true; setCap(i, 'Готово! Можно смотреть сначала'); setPaused(true); }
  } catch (e) { if (e !== CANCEL) console.error(e); }
}
bPlay.addEventListener('click', () => { if (ended) { done.clear(); play(0); } else setPaused(!paused); });
$('#b-next').addEventListener('click', () => play(Math.min(STEPS.length - 1, idx + 1)));
$('#b-prev').addEventListener('click', () => play(Math.max(0, idx - 1)));
$('#b-again').addEventListener('click', () => play(idx));
addEventListener('keydown', e => {
  if (e.target.closest('button') && (e.key === ' ' || e.key === 'Enter')) return;
  if (e.key === 'ArrowRight') play(Math.min(STEPS.length - 1, idx + 1));
  else if (e.key === 'ArrowLeft') play(Math.max(0, idx - 1));
  else if (e.key === ' ') { e.preventDefault(); bPlay.click(); }
});

/* ---------- за кадром не играем ----------
   Гайд начинается, когда до него долистали, замирает, когда ушёл с экрана или вкладку свернули,
   и продолжает с того же места, когда вернулись. Свою паузу пользователь снимает сам. */
let seen = false, onScreen = false, autoPaused = false;
function sync(){
  const v = onScreen && !document.hidden;
  clock(v);
  if (!seen) { if (v) { seen = true; play(0); } return; }
  if (!v && !paused && !ended) { autoPaused = true; setPaused(true); }
  else if (v && autoPaused) { autoPaused = false; if (paused && !ended) setPaused(false); }
}
fit();
(document.fonts && document.fonts.ready ? document.fonts.ready : Promise.resolve()).then(() => {
  fit();
  document.addEventListener('visibilitychange', sync);
  if ('IntersectionObserver' in window) new IntersectionObserver(es => { onScreen = es[es.length - 1].intersectionRatio >= .3; sync(); }, {threshold: [0, .3]}).observe(pwrap);
  else { onScreen = true; sync(); }
});
})();
