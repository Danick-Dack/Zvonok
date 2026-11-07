/* Звонок · вкладка «Олимпиады»: актуальные олимпиады в Алматы и международные онлайн-турниры (7–11 классы) */
/* ---------- «Олимпиады» ---------- */

const OLYMP_SUBJECTS = [
  ["Математика", "blue"],
  ["Физика", "purple"],
  ["Информатика", "indigo"],
  ["Химия", "teal"],
  ["Биология", "green"],
  ["География", "mint"],
  ["Английский язык", "cyan"],
  ["Казахский яз. и лит.", "yellow"],
  ["Русский язык и лит.", "red"],
  ["История и право", "brown"],
  ["Астрономия и инженерия", "orange"]
];

const OLYMP_HUE = Object.fromEntries(OLYMP_SUBJECTS);
const ALL_OLYMP_SUBJS = OLYMP_SUBJECTS.map(s => s[0]);

const OLYMP_DEFAULT = [
  {
    id: "rosh-almaty",
    title: "Республиканская олимпиада школьников (РОШ) · г. Алматы",
    org: "Центр «Алматы дарыны» и РНПЦ «Дарын»",
    grades: [9, 10, 11],
    subjects: ALL_OLYMP_SUBJS.slice(0, 10),
    primary: "Математика",
    hue: "blue",
    status: "Идёт сейчас",
    statusKind: "hot",
    dateText: "Ноябрь – Декабрь 2026 (район), Январь 2027 (город)",
    startDate: "2026-11-01",
    endDate: "2027-01-31",
    dateOrder: 1,
    place: "Алматы · Оффлайн",
    desc: "Главная государственная олимпиада Казахстана по всем общеобразовательным предметам ЕМЦ и ОГЦ. Победители районного и городского этапов в Алматы выходят в республиканский финал.",
    url: "https://almaty.daryny.kz/"
  },
  {
    id: "junior-almaty",
    title: "Республиканская юниорская олимпиада · г. Алматы",
    org: "Центр «Алматы дарыны»",
    grades: [7, 8],
    subjects: ALL_OLYMP_SUBJS.slice(0, 10),
    primary: "Математика",
    hue: "green",
    status: "Регистрация и туры",
    statusKind: "hot",
    dateText: "Ноябрь 2026 – Февраль 2027",
    startDate: "2026-11-01",
    endDate: "2027-02-28",
    dateOrder: 2,
    place: "Алматы · Оффлайн",
    desc: "Официальная предметная олимпиада для 7 и 8 классов по естественно-математическому и общественно-гуманитарному направлениям в школах Алматы.",
    url: "https://almaty.daryny.kz/"
  },
  {
    id: "amc-almaty",
    title: "American Mathematics Competitions (AMC 10 / AMC 12)",
    org: "MAA · Smart Arena Казахстан",
    grades: [7, 8, 9, 10, 11],
    subjects: ["Математика"],
    primary: "Математика",
    hue: "blue",
    status: "Идёт сейчас",
    statusKind: "hot",
    dateText: "Ноябрь 2026 (туры A и B)",
    startDate: "2026-11-01",
    endDate: "2026-11-30",
    dateOrder: 3,
    place: "Алматы · Оффлайн",
    desc: "Международный математический конкурс (AMC 10 для 7–10 классов, AMC 12 для 11 классов). Проводится на аккредитованных площадках в Алматы и открывает отбор на этап AIME.",
    url: "https://smart-arena.kz/"
  },
  {
    id: "presidential-kz",
    title: "Президентская олимпиада РК (ЕМЦ)",
    org: "РНПЦ «Дарын»",
    grades: [11],
    subjects: ["Математика", "Физика", "Химия", "Биология"],
    primary: "Физика",
    hue: "purple",
    status: "Идёт сейчас",
    statusKind: "hot",
    dateText: "Ноябрь – Декабрь 2026",
    startDate: "2026-11-01",
    endDate: "2026-12-25",
    dateOrder: 4,
    place: "Алматы и финал РК",
    desc: "Комплексная олимпиада для учащихся 11 классов: каждый участник выполняет задания сразу по четырем дисциплинам (математика, физика, химия и биология).",
    url: "https://daryn.kz/"
  },
  {
    id: "euler-math",
    title: "Олимпиада им. Леонарда Эйлера по математике",
    org: "Matol.kz · Оргкомитет олимпиады",
    grades: [7, 8],
    subjects: ["Математика"],
    primary: "Математика",
    hue: "blue",
    status: "Дистанционный тур",
    statusKind: "hot",
    dateText: "Ноябрь 2026 – Февраль 2027",
    startDate: "2026-11-01",
    endDate: "2027-02-28",
    dateOrder: 5,
    place: "Онлайн и Алматы",
    desc: "Профильная математическая олимпиада для учащихся 7–8 классов с исследовательскими задачами уровня республиканских и международных финалов (онлайн-отбор и оффлайн-финал в Алматы).",
    url: "https://matol.kz/"
  },
  {
    id: "apmo-silkroad-almaty",
    title: "Олимпиады «Шёлковый путь» и АПМО · г. Алматы",
    org: "Matol.kz · РНПЦ «Дарын»",
    grades: [9, 10, 11],
    subjects: ["Математика"],
    primary: "Математика",
    hue: "blue",
    status: "По графику",
    statusKind: "ok",
    dateText: "Февраль – Март 2027",
    startDate: "2027-02-01",
    endDate: "2027-03-31",
    dateOrder: 6,
    place: "Алматы · Оффлайн",
    desc: "Международные математические олимпиады «Шёлковый путь» и Азиатско-Тихоокеанская математическая олимпиада (АПМО), оффлайн-туры которых ежегодно проводятся на площадках в Алматы.",
    url: "https://matol.kz/"
  },
  {
    id: "science-projects-almaty",
    title: "Городской конкурс научных проектов школьников · Алматы",
    org: "Центр «Алматы дарыны»",
    grades: [8, 9, 10, 11],
    subjects: ALL_OLYMP_SUBJS,
    primary: "Астрономия и инженерия",
    hue: "orange",
    status: "Идёт отбор",
    statusKind: "hot",
    dateText: "Ноябрь 2026 – Февраль 2027",
    startDate: "2026-11-01",
    endDate: "2027-02-28",
    dateOrder: 7,
    place: "Алматы · Оффлайн",
    desc: "Защита научных и инженерных исследований школьников Алматы по секциям прикладной физики, математики, информатики, химии, биологии, экологии, истории и языкознания.",
    url: "https://almaty.daryny.kz/"
  },
  {
    id: "usaco-open",
    title: "USACO · Открытая олимпиада по программированию",
    org: "USA Computing Olympiad",
    grades: [7, 8, 9, 10, 11],
    subjects: ["Информатика"],
    primary: "Информатика",
    hue: "indigo",
    status: "Открыто онлайн",
    statusKind: "ok",
    dateText: "Декабрь 2026 – Март 2027 (4 тура)",
    startDate: "2026-12-01",
    endDate: "2027-03-31",
    dateOrder: 8,
    place: "Международная · Онлайн",
    desc: "Четыре бесплатных международных интернет-контеста по алгоритмическому программированию (C++, Python, Java) в дивизионах Bronze, Silver, Gold и Platinum.",
    url: "https://usaco.org/"
  },
  {
    id: "physicscup",
    title: "Physics Cup · Международный кубок по физике",
    org: "TalTech · Physics Cup",
    grades: [9, 10, 11],
    subjects: ["Физика", "Астрономия и инженерия"],
    primary: "Физика",
    hue: "purple",
    status: "Идёт сейчас",
    statusKind: "hot",
    dateText: "Ноябрь 2026 – Март 2027",
    startDate: "2026-11-01",
    endDate: "2027-03-31",
    dateOrder: 9,
    place: "Международная · Онлайн",
    desc: "Открытый международный онлайн-турнир по физике и астрофизике: сложные задачи с еженедельными подсказками и глобальным рейтингом школьников.",
    url: "https://physicscup.ee/"
  },
  {
    id: "izho-almaty",
    title: "Международная Жаутыковская олимпиада (IZhO)",
    org: "РФМШ Алматы · РНПЦ «Дарын»",
    grades: [9, 10, 11],
    subjects: ["Математика", "Физика", "Информатика"],
    primary: "Физика",
    hue: "purple",
    status: "Зимний финал",
    statusKind: "warn",
    dateText: "Январь – Февраль 2027",
    startDate: "2027-01-10",
    endDate: "2027-02-20",
    dateOrder: 10,
    place: "Алматы · РФМШ",
    desc: "Престижная международная олимпиада специализированных физико-математических лицеев и школ, ежегодно проходящая в Алматы на базе РФМШ.",
    url: "https://izho.kz/"
  },
  {
    id: "ogzho-almaty",
    title: "Открытая Городская Жаутыковская олимпиада (ОГЖО)",
    org: "РФМШ Алматы",
    grades: [7, 8, 9],
    subjects: ["Математика", "Физика", "Информатика"],
    primary: "Математика",
    hue: "blue",
    status: "Весенний набор",
    statusKind: "warn",
    dateText: "Февраль – Апрель 2027",
    startDate: "2027-02-01",
    endDate: "2027-04-30",
    dateOrder: 11,
    place: "Алматы · Оффлайн",
    desc: "Открытое первенство города Алматы по математике, физике и информатике для учащихся 7–9 классов.",
    url: "https://fizmat.kz/"
  },
  {
    id: "faio-almaty",
    title: "Fizmat AI Olympiad (FAIO) · Искусственный интеллект",
    org: "РФМШ Алматы",
    grades: [8, 9, 10, 11],
    subjects: ["Информатика", "Математика"],
    primary: "Информатика",
    hue: "indigo",
    status: "Открытый отбор",
    statusKind: "ok",
    dateText: "Декабрь 2026 – Февраль 2027",
    startDate: "2026-12-01",
    endDate: "2027-02-28",
    dateOrder: 12,
    place: "Онлайн и финал в Алматы",
    desc: "Олимпиада по искусственному интеллекту, машинному обучению и анализу данных: дистанционный тур и оффлайн-тур в Алматы.",
    url: "https://fizmat.kz/"
  },
  {
    id: "turgor-almaty",
    title: "Международный математический «Турнир городов»",
    org: "Оргкомитет Турнира городов · Алматы",
    grades: [8, 9, 10, 11],
    subjects: ["Математика"],
    primary: "Математика",
    hue: "blue",
    status: "Два тура в году",
    statusKind: "ok",
    dateText: "Октябрь 2026 · Февраль – Март 2027",
    startDate: "2026-10-01",
    endDate: "2027-03-31",
    dateOrder: 13,
    place: "Алматы · Оффлайн-площадки",
    desc: "Международное соревнование по математике с исследовательскими задачами базового и сложного уровней. Проводится оффлайн на площадках в Алматы.",
    url: "https://www.turgor.ru/"
  },
  {
    id: "smagulov-almaty",
    title: "Математическая олимпиада им. Ш. Смагулова",
    org: "Matol.kz · Механико-математический факультет",
    grades: [7],
    subjects: ["Математика"],
    primary: "Математика",
    hue: "blue",
    status: "По графику",
    statusKind: "ok",
    dateText: "Декабрь 2026 – Апрель 2027",
    startDate: "2026-12-01",
    endDate: "2027-04-30",
    dateOrder: 14,
    place: "Алматы · Оффлайн и онлайн",
    desc: "Специализированная олимпиада в Алматы для учащихся 7 классов (индивидуальный и командный зачеты по олимпиадной математике).",
    url: "https://matol.kz/"
  },
  {
    id: "beyond-nat-sci",
    title: "Олимпиадные отборы и сборы по химии, биологии и географии",
    org: "Фонд Beyond Curriculum (BeyondOlympiads)",
    grades: [7, 8, 9, 10, 11],
    subjects: ["Химия", "Биология", "География"],
    primary: "Химия",
    hue: "teal",
    status: "Открытый доступ",
    statusKind: "ok",
    dateText: "Ноябрь 2026 – Май 2027",
    startDate: "2026-11-01",
    endDate: "2027-05-31",
    dateOrder: 15,
    place: "Алматы и онлайн",
    desc: "Учебно-тренировочные контесты, отборы на Менделеевскую олимпиаду (ММО), IChO, IBO и IGeO, архивы решений и календарь туров РК.",
    url: "https://olympiads.bc-pf.org/"
  },
  {
    id: "ling-almaty",
    title: "Республиканская олимпиада по лингвистике · г. Алматы",
    org: "РНПЦ «Дарын» · Центр «Алматы дарыны»",
    grades: [8, 9, 10, 11],
    subjects: ["Английский язык", "Казахский яз. и лит.", "Русский язык и лит."],
    primary: "Английский язык",
    hue: "cyan",
    status: "Регистрация и туры",
    statusKind: "ok",
    dateText: "Ноябрь 2026 – Март 2027",
    startDate: "2026-11-01",
    endDate: "2027-03-31",
    dateOrder: 16,
    place: "Алматы · Оффлайн",
    desc: "Официальная олимпиада по теоретической и прикладной лингвистике (городской этап в Алматы и республиканский отбор на международную олимпиаду IOL).",
    url: "https://daryn.kz/"
  },
  {
    id: "turlom-open",
    title: "Турнир им. М.В. Ломоносова и открытые онлайн-олимпиады",
    org: "Оргкомитет Турнира Ломоносова · Олимпиада.ру",
    grades: [7, 8, 9, 10, 11],
    subjects: ["Математика", "Физика", "Астрономия и инженерия", "Химия", "Биология", "История и право", "Русский язык и лит.", "Английский язык"],
    primary: "История и право",
    hue: "brown",
    status: "Открыто онлайн",
    statusKind: "ok",
    dateText: "Ноябрь 2026 – Март 2027",
    startDate: "2026-11-01",
    endDate: "2027-03-31",
    dateOrder: 17,
    place: "Международная · Онлайн",
    desc: "Многопредметный турнир и открытые дистанционные этапы международных олимпиад («Физтех», МОШ, «Высшая проба») со свободным участием из Казахстана.",
    url: "https://turlom.olimpiada.ru/"
  },
  {
    id: "kio-kz",
    title: "Казахстанские интернет-олимпиады (КИО) по всем предметам",
    org: "Центр дистанционных олимпиад (cdo.kz)",
    grades: [7, 8, 9, 10, 11],
    subjects: ALL_OLYMP_SUBJS.slice(0, 10),
    primary: "География",
    hue: "mint",
    status: "Круглый год",
    statusKind: "ok",
    dateText: "Ноябрь 2026 – Май 2027",
    startDate: "2026-09-01",
    endDate: "2027-05-31",
    dateOrder: 18,
    place: "Казахстан · Онлайн",
    desc: "Республиканские дистанционные олимпиады по всем предметам школьной программы: математика, физика, языки, история Казахстана, право, география, биология и химия.",
    url: "https://cdo.kz/"
  },
  {
    id: "daryn-games",
    title: "Интеллектуальные конкурсы «Кенгуру», «Ақ бота», «British Bulldog»",
    org: "РНПЦ «Дарын» (games.daryn.kz)",
    grades: [7, 8, 9, 10, 11],
    subjects: ["Математика", "Физика", "Химия", "Биология", "Английский язык", "Казахский яз. и лит.", "Русский язык и лит."],
    primary: "Казахский яз. и лит.",
    hue: "yellow",
    status: "Регистрация",
    statusKind: "ok",
    dateText: "Ноябрь 2026 – Апрель 2027",
    startDate: "2026-11-01",
    endDate: "2027-04-30",
    dateOrder: 19,
    place: "Алматы и онлайн",
    desc: "Массовые международные и республиканские конкурсы: «Кенгуру» (математика), «Ақ бота» (естествознание), «British Bulldog» (английский язык) и «Русский медвежонок».",
    url: "https://games.daryn.kz/"
  },
  {
    id: "codeforces-rounds",
    title: "Codeforces Rounds · Спортивное программирование",
    org: "Codeforces",
    grades: [7, 8, 9, 10, 11],
    subjects: ["Информатика"],
    primary: "Информатика",
    hue: "indigo",
    status: "Каждую неделю",
    statusKind: "ok",
    dateText: "Постоянно · 2–3 раунда в неделю",
    startDate: "2026-09-01",
    endDate: "2027-08-31",
    dateOrder: 20,
    place: "Международная · Онлайн",
    desc: "Открытые международные раунды по алгоритмам и структурам данных для регулярной тренировки к городским и республиканским олимпиадам по информатике.",
    url: "https://codeforces.com/"
  }
];

const OLYMP_PORTALS = [
  ["Алматы дарыны", "Городские и районные этапы олимпиад в Алматы, положения и протоколы", "https://almaty.daryny.kz/"],
  ["РНПЦ «Дарын»", "Республиканский календарь олимпиад МП РК и регистрация на туры", "https://daryn.kz/"],
  ["BeyondOlympiads", "Календарь олимпиад Казахстана, сборы и архив задач по всем предметам", "https://olympiads.bc-pf.org/"],
  ["Matol.kz", "Олимпиадная математика Казахстана: расписание, задачи и решения", "https://matol.kz/"],
  ["РФМШ Алматы", "Открытая Городская Жаутыковская олимпиада (ОГЖО), IZhO и FAIO", "https://fizmat.kz/"],
  ["КИО · cdo.kz", "Казахстанские дистанционные предметные олимпиады для 7–11 классов", "https://cdo.kz/"]
];

const OLYMP_EMBED = /*@OLYMPIADS@*/null || {updated: "2026-11-07", items: OLYMP_DEFAULT};

/** Сколько полных календарных дней прошло после даты окончания олимпиады (endDate).
 *  <= 0: олимпиада ещё идёт или впереди; 1..3: недавно завершилась (скрыта из основного списка); > 3: удаляется полностью. */
function olympDaysAfterEnd(item, now) {
  if (!item || typeof item.endDate !== "string" || !/^\d{4}-\d\d-\d\d$/.test(item.endDate)) return 0;
  const d = now || new Date();
  const todayMs = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const [ey, em, ed] = item.endDate.split("-").map(Number);
  const endMs = new Date(ey, em - 1, ed).getTime();
  return Math.round((todayMs - endMs) / 86400000);
}

/** Состояние олимпиады на дату now:
 *  - если прошло > 3 дней после endDate: deleted = true (полностью удаляется);
 *  - если прошло 1..3 дня после endDate: expired = true (скрывается из актуального списка);
 *  - если сейчас между startDate и endDate: статус автоматически становится активным («Идёт сейчас» / hot). */
function olympState(item, now) {
  const d = now || new Date();
  const afterEnd = olympDaysAfterEnd(item, d);
  if (afterEnd > 3) return {deleted: true, expired: true, status: "Завершилась", statusKind: "", priority: 99};
  if (afterEnd >= 1) return {deleted: false, expired: true, status: "Завершилась", statusKind: "", priority: 9};
  const todayMs = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  let daysToStart = 0;
  if (typeof item.startDate === "string" && /^\d{4}-\d\d-\d\d$/.test(item.startDate)) {
    const [sy, sm, sd] = item.startDate.split("-").map(Number);
    daysToStart = Math.round((new Date(sy, sm - 1, sd).getTime() - todayMs) / 86400000);
  }
  const futurePhrases = ["По графику", "Зимний финал", "Весенний набор"];
  if (daysToStart <= 0) {
    const isFutureLabel = futurePhrases.includes(item.status);
    return {
      deleted: false,
      expired: false,
      status: isFutureLabel ? "Идёт сейчас" : (item.status || "Идёт сейчас"),
      statusKind: isFutureLabel ? "hot" : (item.statusKind || "hot"),
      priority: 0
    };
  }
  if (daysToStart <= 21) {
    return {
      deleted: false,
      expired: false,
      status: item.status === "По графику" ? "Скоро старт" : (item.status || "Скоро старт"),
      statusKind: item.statusKind === "ok" ? "warn" : (item.statusKind || "warn"),
      priority: 1
    };
  }
  return {
    deleted: false,
    expired: false,
    status: item.status || "По графику",
    statusKind: item.statusKind || "ok",
    priority: 2
  };
}

/** Очистка и фильтрация списка олимпиад: проверяем поля, ссылки и полностью удаляем олимпиады,
 *  у которых прошло больше 3 дней после окончания (endDate). */
function cleanOlympiads(raw, now) {
  const d = now || new Date();
  const list = Array.isArray(raw) ? raw : (raw && Array.isArray(raw.items) ? raw.items : []);
  const seen = new Set();
  const out = [];
  for (const x of list) {
    if (!x || typeof x !== "object") continue;
    if (typeof x.id !== "string" || !x.id || seen.has(x.id)) continue;
    if (typeof x.title !== "string" || !x.title.trim()) continue;
    if (typeof x.url !== "string" || !/^https:\/\//.test(x.url)) continue;
    if (!Array.isArray(x.grades) || !x.grades.length) continue;
    if (!Array.isArray(x.subjects) || !x.subjects.length) continue;
    if (olympDaysAfterEnd(x, d) > 3) continue; // полное удаление через 3 дня после окончания
    seen.add(x.id);
    out.push(Object.assign({}, x, {
      title: x.title.replace(/—/g, "–"),
      desc: String(x.desc || "").replace(/—/g, "–"),
      dateText: String(x.dateText || "").replace(/—/g, "–"),
      org: String(x.org || "").replace(/—/g, "–"),
      place: String(x.place || "Алматы · Оффлайн").replace(/—/g, "–")
    }));
  }
  return out;
}

function initOlympData() {
  const now = new Date();
  const cached = store.get("olympData", null);
  let base = OLYMP_EMBED;
  if (cached && typeof cached === "object" && Array.isArray(cached.items)) {
    if (!base.updated || (cached.updated && cached.updated >= base.updated)) {
      base = cached;
    }
  }
  const cleaned = cleanOlympiads(base.items, now);
  if (cached && Array.isArray(cached.items) && cleaned.length !== cached.items.length) {
    store.set("olympData", {updated: base.updated || iso(now), items: cleaned});
  }
  return cleaned;
}

let OLYMPIADS = initOlympData();
let olympAt = 0;

/** Фоновое обновление олимпиад:
 *  1) подтягивает свежий olympiads.json с сайта (если открыт по сети);
 *  2) проверяет ближайшие открытые онлайн-раунды на Codeforces API;
 *  3) удаляет олимпиады, завершившиеся более 3 дней назад. */
function loadOlympiads() {
  const now = new Date();
  const purged = cleanOlympiads(OLYMPIADS, now);
  if (purged.length !== OLYMPIADS.length) {
    OLYMPIADS = purged;
    if (view === "olymp") renderOlymp(); else dirty.olymp = true;
  }
  if (Date.now() - olympAt < 600000) return;
  olympAt = Date.now();

  if (ONLINE_SITE) {
    fetch("olympiads.json", {cache: "no-cache"}).then(r => r.ok ? r.json() : null).then(j => {
      if (!j || !Array.isArray(j.items)) return;
      const next = cleanOlympiads(j.items, new Date());
      if (!next.length) return;
      store.set("olympData", {updated: j.updated || iso(new Date()), items: next});
      if (JSON.stringify(next) !== JSON.stringify(OLYMPIADS)) {
        OLYMPIADS = next;
        if (view === "olymp") renderOlymp(); else dirty.olymp = true;
      }
    }).catch(() => {});
  }

  // Живое обновление ближайшего раунда Codeforces (не чаще раза в час)
  const cfLast = store.get("olympCfAt", 0);
  if (Date.now() - cfLast > 3600000 && typeof fetch === "function") {
    fetch("https://codeforces.com/api/contest.list?gym=false").then(r => r.ok ? r.json() : null).then(j => {
      if (!j || j.status !== "OK" || !Array.isArray(j.result)) return;
      const nowSec = Math.floor(Date.now() / 1000);
      const upcoming = j.result
        .filter(c => c && c.phase === "BEFORE" && typeof c.startTimeSeconds === "number" && c.startTimeSeconds > nowSec)
        .sort((a, b) => a.startTimeSeconds - b.startTimeSeconds);
      if (!upcoming.length) return;
      const nextRound = upcoming[0];
      const nextDate = new Date(nextRound.startTimeSeconds * 1000);
      const cfItem = OLYMPIADS.find(x => x.id === "codeforces-rounds");
      if (cfItem) {
        cfItem.status = "Ближайший раунд";
        cfItem.statusKind = "hot";
        cfItem.dateText = dm(nextDate) + " · 2–3 раунда в неделю";
        store.set("olympCfAt", Date.now());
        store.set("olympData", {updated: iso(new Date()), items: OLYMPIADS});
        if (view === "olymp") renderOlymp(); else dirty.olymp = true;
      }
    }).catch(() => {});
  }
}

let oGrade = 0; // 0 = все классы, иначе 7..11
let oSubjs = []; // массив выбранных предметов (пусто = все предметы)
let oFormat = ""; // "" = все, "almaty" = оффлайн в Алматы, "online" = онлайн
let oQuery = "";
let oMenuReady = false;

function toggleOlympSubj(subj) {
  const i = oSubjs.indexOf(subj);
  if (i >= 0) oSubjs.splice(i, 1);
  else oSubjs.push(subj);
  renderOlymp();
}

function formatGrades(grades) {
  if (grades.length === 1) return grades[0] + " класс";
  const min = Math.min(...grades), max = Math.max(...grades);
  if (max - min + 1 === grades.length) return min + "–" + max + " классы";
  return grades.join(", ") + " классы";
}

function olympCard(item, now) {
  const hue = item.hue || OLYMP_HUE[item.primary] || "blue";
  const st = olympState(item, now);
  const subjTags = item.subjects.length >= 9
    ? [h("span", {class: "olymp-subj-tag hue-" + hue, text: "Все школьные предметы (" + item.subjects.length + ")"})]
    : item.subjects.map(s => h("span", {class: "olymp-subj-tag hue-" + (OLYMP_HUE[s] || "gray"), text: s}));

  return h("article", {class: "olymp-card hue-" + hue},
    h("div", {class: "olymp-top"},
      h("span", {class: "tag olymp-date-badge " + (st.statusKind || ""), text: st.status + " · " + item.dateText}),
      h("span", {class: "tag", text: item.place})
    ),
    h("h3", {text: item.title}),
    h("p", {class: "olymp-desc", text: item.desc}),
    h("div", {class: "olymp-tags"}, subjTags),
    h("div", {class: "olymp-meta"},
      h("div", null, h("b", {text: "Организатор:"}), h("span", {text: item.org}))
    ),
    h("div", {class: "olymp-foot"},
      h("span", {class: "olymp-grades-tag", text: formatGrades(item.grades)}),
      h("a", {class: "btn btn-quiet btn-sm", href: item.url, target: "_blank", rel: "noopener noreferrer"},
        "Официальный сайт", icon("out"))
    )
  );
}

function closeSubjMenu() {
  const menu = $("subjMenu"), btn = $("subjBtn");
  if (!menu || menu.hidden) return;
  menu.hidden = true;
  if (btn) btn.setAttribute("aria-expanded", "false");
}

function initOlymp() {
  if (oMenuReady) return;
  oMenuReady = true;

  const btn = $("subjBtn"), menu = $("subjMenu"), drop = $("subjDrop");
  if (btn && menu) {
    btn.onclick = e => {
      e.stopPropagation();
      const open = menu.hidden;
      menu.hidden = !open;
      btn.setAttribute("aria-expanded", String(open));
    };
    menu.onclick = e => e.stopPropagation();
    document.addEventListener("click", e => {
      if (drop && !drop.contains(e.target) && !(e.composedPath && e.composedPath().includes(drop))) closeSubjMenu();
    });
    document.addEventListener("keydown", e => {
      if (e.key === "Escape") closeSubjMenu();
    });
  }

  $$("#olympFormat button").forEach(b => b.onclick = () => {
    oFormat = b.dataset.fmt;
    $$("#olympFormat button").forEach(x => x.setAttribute("aria-pressed", String(x.dataset.fmt === oFormat)));
    renderOlymp();
  });

  const qEl = $("oq");
  if (qEl) {
    let qFrame = 0;
    qEl.addEventListener("input", () => {
      cancelAnimationFrame(qFrame);
      qFrame = requestAnimationFrame(() => { oQuery = qEl.value.trim().toLowerCase(); renderOlymp(); });
    });
  }

  const linksBox = $("olympLinks");
  if (linksBox && !linksBox.children.length) {
    OLYMP_PORTALS.forEach(([name, info, url]) => linksBox.append(
      h("a", {class: "olymp-link", href: url, target: "_blank", rel: "noopener noreferrer"},
        h("b", null, h("span", {text: name}), icon("out")),
        h("small", {text: info})
      )
    ));
  }
}

function renderSubjMenu() {
  const menu = $("subjMenu"), btnText = $("subjBtnText"), pickedBox = $("olympPicked");
  if (btnText) {
    btnText.textContent = !oSubjs.length
      ? "Все предметы"
      : oSubjs.length === 1
        ? oSubjs[0]
        : "Предметы: " + oSubjs.length;
  }
  if (menu) {
    const prevScroll = menu.scrollTop;
    menu.textContent = "";
    menu.append(h("div", {class: "subj-menu-head"},
      h("span", {text: "Выбери предметы"}),
      h("div", {class: "subj-menu-acts"},
        oSubjs.length ? h("button", {type: "button", text: "Сбросить", onclick: e => { e.stopPropagation(); oSubjs = []; renderOlymp(); }}) : null,
        h("button", {type: "button", class: "subj-done", text: "Готово", onclick: e => { e.stopPropagation(); closeSubjMenu(); }})
      )
    ));
    OLYMP_SUBJECTS.forEach(([s, hue]) => {
      const on = oSubjs.includes(s);
      menu.append(h("button", {
        type: "button",
        class: "subj-opt hue-" + hue,
        "aria-pressed": String(on),
        onclick: e => { e.stopPropagation(); toggleOlympSubj(s); }
      },
        h("span", {class: "subj-chk", "aria-hidden": "true"}, on ? icon("check") : null),
        h("i", {class: "subj-dot", "aria-hidden": "true"}),
        h("span", {text: s})
      ));
    });
    menu.scrollTop = prevScroll;
  }
  if (pickedBox) {
    pickedBox.textContent = "";
    pickedBox.hidden = !oSubjs.length;
    if (oSubjs.length) {
      oSubjs.forEach(s => {
        const hue = OLYMP_HUE[s] || "blue";
        pickedBox.append(h("button", {
          type: "button",
          class: "olymp-pick-chip hue-" + hue,
          "aria-label": "Убрать предмет " + s,
          onclick: () => toggleOlympSubj(s)
        }, h("span", {text: s}), icon("close")));
      });
      if (oSubjs.length > 1) {
        pickedBox.append(h("button", {
          type: "button",
          class: "olymp-pick-clear",
          text: "Сбросить все",
          onclick: () => { oSubjs = []; renderOlymp(); }
        }));
      }
    }
  }
}

function renderOlymp() {
  initOlymp();
  const now = new Date();
  // Полное удаление олимпиад, у которых с даты окончания прошло более 3 дней
  OLYMPIADS = cleanOlympiads(OLYMPIADS, now);
  const userGrade = !pick && !useCustom && cls ? parseInt(cls) : 0;

  // Кнопки выбора класса (7–11 классы)
  const gBox = $("olympGrades");
  gBox.textContent = "";
  const gBtn = (g, label) => h("button", {
    type: "button",
    "aria-pressed": String(oGrade === g),
    onclick: () => { oGrade = oGrade === g ? 0 : g; renderOlymp(); }
  }, label);

  gBox.append(gBtn(0, "Все классы (7–11)"));
  if (userGrade >= 7 && userGrade <= 11) {
    gBox.append(gBtn(userGrade, "Мой " + cls));
  }
  [7, 8, 9, 10, 11].forEach(g => gBox.append(gBtn(g, g + " класс")));

  renderSubjMenu();
  $$("#olympFormat button").forEach(x => x.setAttribute("aria-pressed", String(x.dataset.fmt === oFormat)));

  // Фильтрация: завершившиеся олимпиады (1..3 дня после endDate) сразу скрываются из актуального списка,
  // а через 3 дня после endDate полностью удаляются в cleanOlympiads()
  const filtered = OLYMPIADS.filter(item => {
    const st = olympState(item, now);
    if (st.expired || st.deleted) return false;
    if (oGrade && !item.grades.includes(oGrade)) return false;
    if (oSubjs.length && !oSubjs.some(s => item.subjects.includes(s))) return false;
    if (oFormat === "almaty" && !/алматы|оффлайн/i.test(item.place)) return false;
    if (oFormat === "online" && !/онлайн/i.test(item.place)) return false;
    if (oQuery) {
      const hay = (item.title + " " + item.org + " " + item.desc + " " + item.place + " " + item.dateText + " " + item.subjects.join(" ")).toLowerCase();
      if (!hay.includes(oQuery)) return false;
    }
    return true;
  });

  // Сводка
  const subjSummary = !oSubjs.length ? "все предметы" : oSubjs.join(", ");
  const countTxt = word(filtered.length, "олимпиада", "олимпиады", "олимпиад")
    + (oGrade ? " · " + oGrade + " класс" : " · 7–11 классы")
    + " · " + subjSummary
    + (oFormat === "almaty" ? " · оффлайн в Алматы" : oFormat === "online" ? " · онлайн" : "");
  $("olympCount").textContent = countTxt;

  const listBox = $("olympList");
  listBox.textContent = "";

  if (!filtered.length) {
    listBox.append(h("div", {class: "card empty-card"},
      h("h2", {class: "card-title", text: "По выбранным фильтрам олимпиад нет"}),
      h("p", {class: "note", text: "Попробуй выбрать другие предметы, формат или параллель классов."}),
      h("div", {class: "row", style: "margin-top:12px"},
        h("button", {class: "btn btn-quiet btn-sm", type: "button", onclick: () => {
          oGrade = 0; oSubjs = []; oFormat = ""; oQuery = ""; $("oq").value = ""; renderOlymp();
        }}, "Сбросить фильтры")
      )
    ));
    return;
  }

  filtered.slice().sort((a, b) => {
    const sa = olympState(a, now), sb = olympState(b, now);
    return (sa.priority - sb.priority) || ((a.dateOrder || 99) - (b.dateOrder || 99));
  }).forEach(x => listBox.append(olympCard(x, now)));
}
