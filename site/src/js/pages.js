/* Звонок · общее для отдельных страниц: кнопка темы в шапке и работа без интернета */
(function initPage() {
  const b = $("schemeBtn");
  if (!b) return;
  // значки темы для сборки: icon("auto") icon("sun") icon("moon")
  const UI = {auto: ["auto", "как в системе"], light: ["sun", "светлая"], dark: ["moon", "тёмная"]};
  const draw = () => { const s = scheme(); b.replaceChildren(icon(UI[s][0])); b.setAttribute("aria-label", "Тема: " + UI[s][1] + ". Сменить"); b.title = "Тема: " + UI[s][1]; };
  b.onclick = () => { const s = SCHEMES[(SCHEMES.indexOf(scheme()) + 1) % SCHEMES.length]; applyScheme(s); draw(); toast("Тема: " + UI[s][1]); };
  document.addEventListener("zv:scheme", draw);
  addEventListener("storage", e => { if (e.key === "zv.scheme") { applyScheme(); draw(); } if (e.key === "zv.accent") applyAccent(); });
  draw();
  if (location.protocol === "file:") {
    $$('a[href^="./#"], a[href="./"]').forEach(a => {
      const h = a.getAttribute("href");
      a.setAttribute("href", h === "./" ? "index.html" : "index.html" + h.slice(2));
    });
  }
})();
registerSW();
