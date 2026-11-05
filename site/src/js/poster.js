"use strict";
/* Звонок · плакат: QR сразу на класс (poster.html#7b) или на весь сайт */
(() => {
  const QRS = /*@QRS@*/null, CLS = /*@CLASSES@*/[], SITE = "@SITE_URL@";
  const LAT = {"А": "a", "Б": "b", "В": "v", "Г": "g"}, CYR = {a: "А", b: "Б", v: "В", g: "Г"};
  const slug = c => c.replace(/[АБВГ]/g, x => LAT[x]).toLowerCase();
  const sel = document.getElementById("pc");
  function svg(code) {
    const [n, hex] = code.split(":"), N = +n;
    let bits = "", p = "";
    for (const ch of hex) bits += parseInt(ch, 16).toString(2).padStart(4, "0");
    for (let i = 0; i < N * N; i++) if (bits[i] === "1") p += "M" + i % N + " " + Math.floor(i / N) + "h1v1h-1z";
    return '<svg viewBox="0 0 ' + N + " " + N + '" shape-rendering="crispEdges" aria-hidden="true"><path d="' + p + '" fill="#000"/></svg>';
  }
  function draw() {
    const c = sel.value, url = c ? SITE + "#" + slug(c) : SITE;
    document.getElementById("qr").innerHTML = svg(QRS[c || ""]);
    document.getElementById("qr").setAttribute("aria-label", "QR-код: " + url);
    document.getElementById("for").textContent = c ? "Расписание " + c : "Все классы";
    document.getElementById("url").textContent = url.replace(/^https:\/\//, "");
    document.title = (c ? "Плакат " + c : "Плакат") + " · Звонок";
  }
  [["", "Все классы"]].concat(CLS.map(c => [c, c])).forEach(([v, t]) => { const o = document.createElement("option"); o.value = v; o.textContent = t; sel.append(o); });
  const m = /^#?(7|8|9|10|11)([abvg])$/i.exec(location.hash);
  sel.value = m && CLS.includes(m[1] + CYR[m[2].toLowerCase()]) ? m[1] + CYR[m[2].toLowerCase()] : "";
  sel.onchange = () => { draw(); try { history.replaceState(null, "", sel.value ? "#" + slug(sel.value) : location.pathname); } catch (e) {} };
  document.getElementById("print").onclick = () => window.print();
  draw();
})();
