/* Звонок · короткие ссылки: …/Zvonok/10a и …/Zvonok/10а открывают расписание класса */
(() => {
  const CLS = /*@CLASSES@*/[], CYR = {a: "А", b: "Б", v: "В", g: "Г", "а": "А", "б": "Б", "в": "В", "г": "Г"};
  let tail = "";
  try { tail = decodeURIComponent(location.pathname.replace(/\/+$/, "").split("/").pop() || ""); } catch (e) {}
  const m = /^(7|8|9|10|11)([abvgабвг])$/i.exec(tail);
  const c = m && m[1] + CYR[m[2].toLowerCase()];
  if (c && CLS.includes(c)) {
    const base = location.pathname.replace(/\/+$/, "").split("/").slice(0, -1).join("/") + "/";
    location.replace(base + "#" + m[1] + "abvg"["АБВГ".indexOf(CYR[m[2].toLowerCase()])]);
  }
})();

// Кнопка «На главную» пару раз увиливает от мышки, как кто-то от доски. Потом сдаётся. На телефоне не убегает.
(() => {
  const b = $("home");
  if (!b || !matchMedia("(hover: hover) and (pointer: fine)").matches || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  let left = 3;
  b.addEventListener("mouseenter", () => {
    if (!left) return;
    left--;
    const dx = (Math.random() < .5 ? -1 : 1) * (90 + Math.random() * 80), dy = (Math.random() - .5) * 40;
    b.style.transform = left ? "translate(" + dx + "px," + dy + "px)" : "";
    if (!left) { b.textContent = "Ладно, проходи"; }
  });
})();
