"""Проверка data/schedule.json и того, что сайт собран из актуальных данных.  python tools/check_data.py"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
errors = []


def no_repeats(pairs):
    """Повтор ключа в JSON (два одинаковых предмета или класса) молча затёр бы первый: такое не пропускаем."""
    keys = [k for k, _ in pairs]
    errors.extend(f"ключ «{k}» повторяется" for k in sorted({k for k in keys if keys.count(k) > 1}))
    return dict(pairs)


data = json.load(open(os.path.join(ROOT, "data", "schedule.json"), encoding="utf-8"), object_pairs_hook=no_repeats)


def mins(t):
    m = re.fullmatch(r"(\d{1,2}):(\d{2})", t)
    return int(m[1]) * 60 + int(m[2]) if m else None


bells = [(mins(a), mins(b)) for a, b in data["bells"]]
for i, (a, b) in enumerate(bells):
    if a is None or b is None or b <= a:
        errors.append(f"звонок {i + 1}: неверное время {data['bells'][i]}")
    elif i and bells[i - 1][1] is not None and a < bells[i - 1][1]:
        errors.append(f"звонок {i + 1} начинается раньше конца предыдущего")

# названия предметов и праздников показываются везде как есть: без управляющих символов и не длиннее 40 знаков
# (столько оставляют приложения и сайт у чужих данных: замен, своего расписания)
for name in list(data["subjects"].values()) + [h[2] for h in data.get("holidays", []) if len(h) == 3]:
    if not isinstance(name, str) or not name.strip() or len(name) > 40 or re.search(r"[\x00-\x1f\x7f]", name):
        errors.append(f"название «{name}»: пустое, длиннее 40 знаков или с управляющими символами")

classes = data["classes"]
if len(classes) != 19:
    errors.append(f"классов {len(classes)}, ожидалось 19")
for cls, days in classes.items():
    if not re.fullmatch(r"(7|8|9|10|11)[АБВГ]", cls):
        errors.append(f"странное имя класса {cls}")
    if len(days) != 5:
        errors.append(f"{cls}: дней {len(days)}, ожидалось 5")
    for d, day in enumerate(days):
        if not day:
            errors.append(f"{cls}, день {d + 1}: нет уроков")
        if len(day) > len(bells):
            errors.append(f"{cls}, день {d + 1}: уроков больше, чем звонков")
        for i, raw in enumerate(day):
            if not raw:
                continue
            code, _, room = raw.partition(" ")
            if code not in data["subjects"]:
                errors.append(f"{cls}, день {d + 1}, урок {i + 1}: нет предмета «{code}»")
            if not re.fullmatch(r"с/з|\d+(/\d+)?|\d+/|/\d+", room):
                errors.append(f"{cls}, день {d + 1}, урок {i + 1}: странный кабинет «{room}»")

DATE = r"\d{4}-\d\d-\d\d"
# каникулы: [с, по, название]
for h in data.get("holidays", []):
    if len(h) != 3 or not all(re.fullmatch(DATE, x) for x in h[:2]) or h[0] > h[1] or not h[2]:
        errors.append(f"странные каникулы {h}")
if not re.fullmatch(DATE, data.get("term_end", "")):
    errors.append("нет term_end (последний день четверти)")
elif data["term_end"] < data.get("updated", ""):
    errors.append("term_end раньше даты обновления: забыли поменять четверть")

# СОР и СОЧ: {класс: {предмет: [даты]}} и {класс: {предмет: дата}}. Только учебные дни этой четверти
import datetime


def exam_date(where, d):
    if not isinstance(d, str) or not re.fullmatch(DATE, d):
        errors.append(f"{where}: дата должна быть ГГГГ-ММ-ДД, а не {d!r}")
        return
    try:
        day = datetime.date.fromisoformat(d)
    except ValueError:
        errors.append(f"{where}: такой даты нет: {d}")
        return
    if day.weekday() > 4:
        errors.append(f"{where}: {d} это выходной")
    if any(h[0] <= d <= h[1] for h in data.get("holidays", []) if len(h) == 3):
        errors.append(f"{where}: {d} это каникулы или праздник")
    if d > data.get("term_end", "9999"):
        errors.append(f"{where}: {d} позже конца четверти")


for kind in ("sor", "soch"):
    for cls, subj in data.get(kind, {}).items():
        if cls not in classes:
            errors.append(f"{kind}: нет класса {cls}")
        for code, dates in subj.items():
            where = f"{kind}, {cls}, {code}"
            if code not in data["subjects"]:
                errors.append(f"{where}: нет такого предмета")
            if kind == "sor" and (not isinstance(dates, list) or not dates or len(set(dates)) != len(dates)):
                errors.append(f"{where}: нужен непустой список дат без повторов")
                continue
            for d in dates if kind == "sor" else [dates]:
                exam_date(where, d)
# подписи к урокам: адрес базы Firebase или пусто (подписи выключены)
if not re.fullmatch(r"(https://[a-z0-9-]+(\.[a-z0-9-]+)*\.(firebaseio\.com|firebasedatabase\.app))?", data.get("notes", "")):
    errors.append("notes: нужен адрес вида https://имя.firebasedatabase.app без косой черты в конце или пустая строка")
# ШОР: группы с расписанием, ученик отмечает свои
ids = [g.get("id") for g in data.get("shor", [])]
if len(set(ids)) != len(ids):
    errors.append("shor: id групп повторяются")
for g in data.get("shor", []):
    where = f"shor, {g.get('id')}"
    if not all(isinstance(g.get(k), str) and g[k].strip() and len(g[k]) <= 40 for k in ("id", "subject", "coach", "place")):
        errors.append(f"{where}: id, subject, coach и place должны быть строками не длиннее 40 знаков")
    if not g.get("grades") or any(x not in (7, 8, 9, 10, 11) for x in g["grades"]):
        errors.append(f"{where}: grades из 7–11")
    for t in g.get("times") or [None]:
        ok = isinstance(t, list) and len(t) == 3 and t[0] in range(7) and mins(str(t[1])) is not None and mins(str(t[2])) is not None
        if not ok or mins(t[2]) <= mins(t[1]):
            errors.append(f"{where}: время {t}: нужно [день 0–6, \"чч:мм\", \"чч:мм\"], конец позже начала")
# праздничные темы: [с ММ-ДД, по ММ-ДД, значок, цвет, название, фраза]
for t in data.get("themes", []):
    ok = (isinstance(t, list) and len(t) == 6 and all(isinstance(x, str) for x in t) and all(re.fullmatch(r"(0[1-9]|1[0-2])-([0-2]\d|3[01])", x) for x in t[:2])
          and re.fullmatch(r"[0-9A-Fa-f]{6}", t[3]) and t[2] and 0 < len(t[4]) <= 40 and len(t[5]) <= 80 and "—" not in t[4] + t[5])
    if not ok:
        errors.append(f"themes: странная тема {t}")

# замены: data/changes.json
changes = json.load(open(os.path.join(ROOT, "data", "changes.json"), encoding="utf-8"))
if not isinstance(changes.get("days"), dict):
    errors.append("changes.json: нет объекта days")
for date, ch in (changes.get("days") or {}).items():
    where = f"changes.json, {date}"
    if not re.fullmatch(DATE, date):
        errors.append(f"{where}: дата должна быть ГГГГ-ММ-ДД")
    if not isinstance(ch, dict):
        errors.append(f"{where}: ожидался объект")
        continue
    extra = set(ch) - {"note", "off", "bells", "classes"}
    if extra:
        errors.append(f"{where}: неизвестные поля {sorted(extra)}")
    nb = len(bells)
    if "bells" in ch:
        bb = ch["bells"]
        if not isinstance(bb, list) or not bb:
            errors.append(f"{where}: bells должен быть списком звонков")
            bb = []
        nb, prev = len(bb), None
        for i, b in enumerate(bb):
            a, z = (mins(b[0]), mins(b[1])) if isinstance(b, list) and len(b) == 2 else (None, None)
            if a is None or z is None or z <= a or (prev is not None and a < prev):
                errors.append(f"{where}: звонок {i + 1} неверный {b}")
            prev = z
    for cls, fix in (ch.get("classes") or {}).items():
        if cls not in classes:
            errors.append(f"{where}: нет класса {cls}")
            continue
        for n, f in fix.items():
            if not n.isdigit() or not 1 <= int(n) <= nb:
                errors.append(f"{where}, {cls}: нет урока номер {n}")
            elif f is None or (isinstance(f, str) and f.strip().lower().startswith("нет")):
                pass
            elif not isinstance(f, dict) or not f or set(f) - {"subject", "room"}:
                errors.append(f'{where}, {cls}, урок {n}: нужно {{"subject": ..., "room": ...}} или "нет урока"')
            elif "room" in f and not re.fullmatch(r"с/з|спортзал|\d+(/\d+)?|\d+/|/\d+|", str(f["room"])):
                errors.append(f"{where}, {cls}, урок {n}: странный кабинет «{f['room']}»")

site = open(os.path.join(ROOT, "site", "index.html"), encoding="utf-8").read()
m = re.search(r"const DATA = (\{.*?\});\n", site)
if not m or json.loads(m[1].replace("<\\/", "</")) != data:
    errors.append("site/index.html собран из старых данных: запусти python tools/build_site.py")

# журнал изменений: data/changelog.json, от новых к старым
log = json.load(open(os.path.join(ROOT, "data", "changelog.json"), encoding="utf-8"))
for i, r in enumerate(log):
    where = f"changelog.json, запись {i + 1}"
    if not re.fullmatch(DATE, r.get("date", "")):
        errors.append(f"{where}: нет даты ГГГГ-ММ-ДД")
    elif i and r["date"] > log[i - 1].get("date", ""):
        errors.append(f"{where}: записи должны идти от новых к старым")
    if not r.get("platforms") or set(r["platforms"]) - {"site", "android", "ios"}:
        errors.append(f"{where}: platforms из site, android, ios")
    if not r.get("title") or not r.get("items") or len(r["items"]) > 4:
        errors.append(f"{where}: нужен title и от 1 до 4 пунктов")
    if "—" in json.dumps(r, ensure_ascii=False):
        errors.append(f"{where}: длинное тире, замени на точку, запятую или двоеточие")
# версия приложения (5.0-beta и 5.0-dev считаются 5.0) уже описана в «Что нового»: запись с ней есть в журнале
app_ver = re.search(r'versionName "([\d.]+)', open(os.path.join(ROOT, "app", "build.gradle"), encoding="utf-8").read())
android_vers = {r.get("version") for r in log if "android" in r.get("platforms", [])}
if not app_ver or app_ver[1] not in android_vers:
    errors.append(f"build.gradle versionName {app_ver and app_ver[1]}: в changelog.json нет записи этой версии для android")

sc = os.path.join(ROOT, "site", "changes.json")
if not os.path.exists(sc) or json.load(open(sc, encoding="utf-8")) != changes:
    errors.append("site/changes.json устарел: запусти python tools/build_site.py")

# свои объявления: только известные поля, номера от миллиарда, простой текст до 2000 знаков, фото только своё data/news/<номер>.jpg
na = json.load(open(os.path.join(ROOT, "data", "news.json"), encoding="utf-8"))
if set(na) != {"posts", "hidden"} or not isinstance(na["posts"], list) or not isinstance(na["hidden"], list):
    errors.append("news.json: нужны только списки posts и hidden")
else:
    for i, p in enumerate(na["posts"]):
        photo = isinstance(p, dict) and p.get("photo")
        if not (isinstance(p, dict) and set(p) - {"photo"} == {"id", "date", "text"} and isinstance(p["id"], int) and 10 ** 9 < p["id"] < 2 ** 31
                and isinstance(p["date"], str) and re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ", p["date"])
                and isinstance(p["text"], str) and len(p["text"]) <= 2000 and (p["text"].strip() or photo)):
            errors.append(f"news.json, объявление {i + 1}: нужны id (от 10^9), date (ГГГГ-ММ-ДДTЧЧ:ММ:ССZ) и text до 2000 знаков или фото")
        elif "photo" in p:
            f = os.path.join(ROOT, "data", "news", str(p["photo"]))
            if p["photo"] != f"{p['id']}.jpg" or not os.path.isfile(f) or os.path.getsize(f) > 8 << 20 or open(f, "rb").read(3) != b"\xff\xd8\xff":
                errors.append(f"news.json, объявление {i + 1}: фото должно быть data/news/{p['id']}.jpg (JPEG до 8 МБ)")
    if not all(isinstance(x, int) and 0 < x < 2 ** 31 for x in na["hidden"]):
        errors.append("news.json: hidden только из номеров постов")

print("\n".join(errors) or f"OK: {len(classes)} классов, {len(bells)} звонков")
sys.exit(1 if errors else 0)
