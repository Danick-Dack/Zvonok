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

site = open(os.path.join(ROOT, "site", "index.html"), encoding="utf-8").read()
m = re.search(r"const DATA = (\{.*?\});\n", site)
if not m or json.loads(m[1].replace("<\\/", "</")) != data:
    errors.append("site/index.html собран из старых данных: запусти python tools/build_site.py")

print("\n".join(errors) or f"OK: {len(classes)} классов, {len(bells)} звонков")
sys.exit(1 if errors else 0)
