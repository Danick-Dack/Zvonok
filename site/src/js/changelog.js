/* Звонок · «Что нового» из data/changelog.json */
const LOG = /*@CHANGELOG@*/[];
const NAMES = {site: "Сайт", android: "Android", ios: "iPhone"};
let filter = "";
try { filter = sessionStorage.getItem("zv.logFilter") || ""; } catch (e) {}
function renderLog() {
  const flt = $("flt"); flt.textContent = "";
  [["", "Всё"], ["site", "Сайт"], ["android", "Android"], ["ios", "iPhone"]].forEach(([k, n]) => flt.append(h("button", {type: "button", "aria-pressed": String(k === filter), text: n,
    onclick: () => { filter = k; try { sessionStorage.setItem("zv.logFilter", k); } catch (e) {} renderLog(); }})));
  const list = $("list"); list.textContent = "";
  let last = "";
  LOG.forEach((r, i) => {
    if (filter && !r.platforms.includes(filter)) return;
    if (r.date !== last) {
      const [y, m, d] = r.date.split("-").map(Number);
      list.append(h("h2", {class: "log-date", text: d + " " + MONTHS[m - 1] + " " + y}));
      last = r.date;
    }
    list.append(h("article", {class: "card rel" + (i === 0 ? " newest" : "")},
      h("div", {class: "row"}, i === 0 ? h("span", {class: "tag hot", text: "Новое"}) : null,
        r.platforms.map(p => h("span", {class: "tag", text: NAMES[p] + (p === "android" && r.version ? " " + r.version : "")}))),
      h("h3", {text: r.title}),
      h("ul", null, r.items.map(t => h("li", {text: t})))));
  });
  if (!list.children.length) list.append(h("p", {class: "note", text: "Тут пока пусто."}));
}
renderLog();
