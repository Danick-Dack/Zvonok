"""Проверка data/schedule.json и того, что сайт собран из актуальных данных.  python tools/check_data.py"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
data = json.load(open(os.path.join(ROOT, "data", "schedule.json"), encoding="utf-8"))
errors = []


def mins(t):
    m = re.fullmatch(r"(\d{1,2}):(\d{2})", t)
    return int(m[1]) * 60 + int(m[2]) if m else None


bells = [(mins(a), mins(b)) for a, b in data["bells"]]
for i, (a, b) in enumerate(bells):
    if a is None or b is None or b <= a:
        errors.append(f"звонок {i + 1}: неверное время {data['bells'][i]}")
    elif i and bells[i - 1][1] is not None and a < bells[i - 1][1]:
        errors.append(f"звонок {i + 1} начинается раньше конца предыдущего")

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

sc = os.path.join(ROOT, "site", "changes.json")
if not os.path.exists(sc) or json.load(open(sc, encoding="utf-8")) != changes:
    errors.append("site/changes.json устарел: запусти python tools/build_site.py")

print("\n".join(errors) or f"OK: {len(classes)} классов, {len(bells)} звонков")
sys.exit(1 if errors else 0)
