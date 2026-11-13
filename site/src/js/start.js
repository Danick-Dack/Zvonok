/* Звонок · запуск главной: модули выше только объявляют, а работать всё начинает здесь */
/* ---------- старт ---------- */
rebuild();
S = compute(new Date(), src); key = stateKey(S, new Date());
route(true);
// открыли сразу «Неделю» или «Кабинеты»: «Сейчас» тоже считаем, от него зависят заголовок и ритм тиков
if (view !== "now") { dirty.now = false; renderNow(new Date()); }
renderHeader();
renderScheme();
document.addEventListener("zv:scheme", renderScheme);
tick();
loadChanges();
loadOlympiads();
loadNews();
examTab();
pingOnce();
homeIcon();
registerSW();
