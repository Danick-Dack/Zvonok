/* Звонок · «кино» в телефоне: сцены .sc сменяются сами, пока блок виден на экране и вкладка открыта.
   Подписи: data-t (заголовок) и data-s (текст) у сцены. Точки или шаги гайда переключают сцену и останавливают автопоказ.
   Вне экрана и в свёрнутой вкладке таймера нет вообще; в спокойном режиме (reduced motion) листается только руками. */
(() => {
  const calm = !!window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
  document.querySelectorAll("[data-show]").forEach(root => {
    const scenes = Array.from(root.querySelectorAll(".sc")), cap = root.querySelector(".show-cap"), dots = root.querySelector(".show-dots");
    const steps = Array.from(root.querySelectorAll(".show-steps button"));
    if (!scenes.length) return;
    let i = 0, timer = 0, visible = false, auto = !calm;
    const buttons = steps.length ? steps : scenes.map((s, n) => {
      const b = document.createElement("button");
      b.type = "button"; b.setAttribute("aria-label", "Сцена " + (n + 1) + ": " + s.dataset.t);
      if (dots) dots.append(b);
      return b;
    });
    function go(n) {
      i = (n + scenes.length) % scenes.length;
      scenes.forEach((s, k) => s.classList.toggle("on", k === i));
      buttons.forEach((b, k) => b.setAttribute("aria-current", String(k === i)));
      if (cap) { cap.firstElementChild.textContent = scenes[i].dataset.t || ""; cap.lastElementChild.textContent = scenes[i].dataset.s || ""; }
    }
    function plan() {
      clearTimeout(timer);
      if (auto && visible && !document.hidden) timer = setTimeout(() => { go(i + 1); plan(); }, +root.dataset.show || 3800);
    }
    buttons.forEach((b, n) => b.addEventListener("click", () => { auto = false; go(n); plan(); }));
    root.querySelectorAll("[data-step]").forEach(b => b.addEventListener("click", () => { auto = false; go(i + +b.dataset.step); plan(); }));
    if ("IntersectionObserver" in window) new IntersectionObserver(e => { visible = e[0].isIntersecting; plan(); }, {threshold: .35}).observe(root);
    else visible = true;
    document.addEventListener("visibilitychange", plan);
    go(0); plan();
  });
})();
