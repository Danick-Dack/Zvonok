/* Пасхалка. Ткни в «Звонок» в шапке три раза быстро подряд: откроется змейка, скоротать перемену.
   Одно нажатие, как обычно, открывает «О проекте» (через треть секунды, если следом не тапают).
   Цвета у змейки как у предметов, рекорд хранится в этом браузере. Проверять, сколько времени ушло, никто не будет. */
(() => {
  let taps = 0, t;
  $$(".brand").forEach(b => b.addEventListener("click", e => {
    if (e.ctrlKey || e.metaKey || e.shiftKey || e.button) return; // в новой вкладке: обычная ссылка
    e.preventDefault();
    taps++; clearTimeout(t);
    if (taps >= 3) { taps = 0; snake(); return; }
    t = setTimeout(() => { taps = 0; location.href = b.href; }, 350);
  }));
})();
function snake() {
  const dlg = $("game"), cv = $("gameCv"), x = cv.getContext("2d"), N = 15, Z = cv.width / N;
  const COLORS = ["#007AFF", "#AF52DE", "#FFCC00", "#FF3B30", "#32ADE6", "#00C7BE", "#34C759", "#FF9500"];
  let body, dir, want, food, score, timer = 0, over;
  const best = () => store.get("snake", 0);
  const place = () => { do { food = [Math.floor(Math.random() * N), Math.floor(Math.random() * N)]; } while (body.some(p => p[0] === food[0] && p[1] === food[1])); };
  function draw() {
    const cs = getComputedStyle(document.documentElement), v = k => cs.getPropertyValue(k).trim();
    x.fillStyle = v("--surface-2"); x.fillRect(0, 0, cv.width, cv.height);
    body.forEach((p, i) => { x.fillStyle = COLORS[Math.floor(i / 3) % COLORS.length]; x.beginPath(); x.roundRect ? x.roundRect(p[0] * Z + 1, p[1] * Z + 1, Z - 2, Z - 2, 5) : x.rect(p[0] * Z + 1, p[1] * Z + 1, Z - 2, Z - 2); x.fill(); });
    x.fillStyle = v("--accent"); x.beginPath(); x.arc(food[0] * Z + Z / 2, food[1] * Z + Z / 2, Z / 2 - 3, 0, Math.PI * 2); x.fill();
    $("gameScore").textContent = over ? "Звонок! Перемена кончилась. Счёт " + score + (score && score >= best() ? ", это рекорд" : ", рекорд " + best()) + ". Нажми на поле, чтобы сыграть ещё"
      : "Счёт " + score + " · рекорд " + best();
  }
  function step() {
    dir = want;
    const head = [(body[0][0] + dir[0] + N) % N, (body[0][1] + dir[1] + N) % N];
    if (body.some(p => p[0] === head[0] && p[1] === head[1])) {
      over = true; clearInterval(timer); timer = 0;
      if (score > best()) store.set("snake", score);
      draw(); return;
    }
    body.unshift(head);
    if (head[0] === food[0] && head[1] === food[1]) { score++; place(); } else body.pop();
    draw();
  }
  function start() { body = [[7, 7], [6, 7], [5, 7]]; dir = want = [1, 0]; score = 0; over = false; place(); draw(); clearInterval(timer); timer = setInterval(step, 140); }
  const turn = d => { if (d[0] !== -dir[0] || d[1] !== -dir[1]) want = d; };
  const keys = e => {
    if (!dlg.open || !dlg.contains(e.target)) return; // клавиши страницы и экранного диктора не трогаем
    const d = {ArrowUp: [0, -1], ArrowDown: [0, 1], ArrowLeft: [-1, 0], ArrowRight: [1, 0], w: [0, -1], s: [0, 1], a: [-1, 0], d: [1, 0]}[e.key];
    if (d) { e.preventDefault(); turn(d); }
  };
  let sx = 0, sy = 0;
  cv.ontouchstart = e => { sx = e.touches[0].clientX; sy = e.touches[0].clientY; };
  cv.ontouchend = e => {
    const dx = e.changedTouches[0].clientX - sx, dy = e.changedTouches[0].clientY - sy;
    if (Math.max(Math.abs(dx), Math.abs(dy)) > 20) turn(Math.abs(dx) > Math.abs(dy) ? [Math.sign(dx), 0] : [0, Math.sign(dy)]);
  };
  cv.onclick = () => { if (over) start(); };
  $$("#game .pad button").forEach(b => { b.onclick = () => turn(b.dataset.d.split(",").map(Number)); });
  document.addEventListener("keydown", keys);
  dlg.addEventListener("close", () => { clearInterval(timer); timer = 0; document.removeEventListener("keydown", keys); }, {once: true});
  dlg.addEventListener("click", e => { if (e.target === dlg || e.target.closest("[data-close]")) dlg.close(); });
  if (typeof dlg.showModal === "function") dlg.showModal(); else dlg.setAttribute("open", "");
  start();
}
