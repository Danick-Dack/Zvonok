"""
Сборка сайта «Звонок» в папку site/.

  python tools/build_site.py

Берёт data/schedule.json, site/src/index.html, ios/Uroki7B.js и собирает:
  site/index.html          : весь сайт одним файлом (работает офлайн, даже как файл из Telegram)
  site/poster.html         : плакат A4 с QR-кодом для класса
  site/cal/*.ics           : календарь каждого класса с напоминанием за 3 минуты до урока
  site/changes.json        : замены и сокращённые уроки (копия data/changes.json)
  site/schedule.json       : всё расписание (копия data/schedule.json) для приложения
  site/og.png              : картинка для превью ссылки в мессенджерах
  site/changelog.html      : «Что нового» из data/changelog.json
  site/feedback.html       : «Написать нам» (анонимно, письмом через FormSubmit)
  site/manifest.webmanifest, site/sw.js, иконки: чтобы сайт ставился «как приложение»
Нужны пакеты: qrcode, pillow (pip install qrcode pillow).
"""
import base64
import io
import json
import os
import re

import qrcode
import qrcode.image.svg
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SITE_URL = "https://danick-dack.github.io/Zvonok/"
APK_URL = "https://github.com/Danick-Dack/Zvonok/releases/latest/download/Zvonok-165.apk"
OUT = os.path.join(ROOT, "site")


LOGO = ('<svg viewBox="0 0 108 108" aria-hidden="true"><circle cx="54" cy="54" r="24" fill="none" stroke="#2a2a2a" stroke-width="6"/>'
        '<path d="M54 30A24 24 0 1 1 30 54" fill="none" stroke="#fff" stroke-width="6" stroke-linecap="round"/>'
        '<path d="M54 42v12l8 5" fill="none" stroke="#fff" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/></svg>')
NAV = [("./#schedule", "Расписание"), ("./#rooms", "Кабинеты"), ("./#install", "Установить"), ("./#faq", "Вопросы"),
       ("changelog.html", "Что нового"), ("feedback.html", "Написать нам")]


def header(active):
    """Шапка для отдельных страниц: тот же логотип и меню, что на главной."""
    links = "".join('<a href="%s"%s>%s</a>' % (h, ' aria-current="page"' if h == active else "", t) for h, t in NAV)
    return ('<header><div class="wrap bar"><a class="logo" href="./" aria-label="Звонок">%s Звонок</a><nav>%s</nav></div></header>'
            % (LOGO, links))


def read(*p):
    with open(os.path.join(ROOT, *p), encoding="utf-8") as f:
        return f.read()


def write(name, text):
    with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def qr_svg(url):
    img = qrcode.make(url, image_factory=qrcode.image.svg.SvgPathImage, box_size=10, border=2,
                      error_correction=qrcode.constants.ERROR_CORRECT_M)
    buf = io.BytesIO()
    img.save(buf)
    svg = buf.getvalue().decode("utf-8")
    svg = re.sub(r"<\?xml[^>]*\?>", "", svg).strip()
    svg = re.sub(r'width="[^"]*"\s+height="[^"]*"', 'width="100%" height="100%"', svg, count=1)
    return svg


def icon(size, pad_ratio=0.0):
    """Иконка: чёрный квадрат, тонкое кольцо-таймер с белой дугой и стрелками."""
    s = size * 4
    im = Image.new("RGBA", (s, s), (0, 0, 0, 255))
    d = ImageDraw.Draw(im)
    c = s / 2
    r = s * (0.30 - pad_ratio * 0.3)
    w = max(4, int(s * 0.045))
    box = [c - r, c - r, c + r, c + r]
    d.ellipse(box, outline=(42, 42, 42, 255), width=w)
    d.arc(box, start=-90, end=180, fill=(255, 255, 255, 255), width=w)
    # стрелки
    d.line([(c, c - r * 0.5), (c, c)], fill=(255, 255, 255, 255), width=w)
    d.line([(c, c), (c + r * 0.34, c + r * 0.22)], fill=(255, 255, 255, 255), width=w)
    d.ellipse([c - w * 0.9, c - w * 0.9, c + w * 0.9, c + w * 0.9], fill=(255, 255, 255, 255))
    return im.resize((size, size), Image.LANCZOS)


def qr_code(url):
    """QR как строка «размер:hex» (сайт рисует его сам): 19 классов весят ~3 КБ вместо ~60 КБ SVG."""
    q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=2)
    q.add_data(url)
    q.make(fit=True)
    m = q.get_matrix()
    bits = "".join("1" if x else "0" for row in m for x in row)
    bits += "0" * (-len(bits) % 4)
    return "%d:%s" % (len(m), "".join("%x" % int(bits[i:i + 4], 2) for i in range(0, len(bits), 4)))


def font(size, bold=False):
    names = (["seguisb.ttf", "segoeuib.ttf", "DejaVuSans-Bold.ttf", "arialbd.ttf"] if bold
             else ["segoeuisl.ttf", "segoeuil.ttf", "DejaVuSans-ExtraLight.ttf", "DejaVuSans.ttf", "arial.ttf"])
    dirs = [r"C:\Windows\Fonts", "/usr/share/fonts/truetype/dejavu", "/Library/Fonts"]
    for n in names:
        for d in dirs:
            f = os.path.join(d, n)
            if os.path.exists(f):
                return ImageFont.truetype(f, size)
    return ImageFont.load_default(size)


def og_image():
    """Превью ссылки 1200×630: чёрный фон, кольцо-таймер и название."""
    W, H = 1200, 630
    im = Image.new("RGB", (W, H), (0, 0, 0))
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse([700, -300, 1500, 500], fill=34)
    from PIL import ImageFilter
    im.paste((255, 255, 255), (0, 0), glow.filter(ImageFilter.GaussianBlur(120)))
    d = ImageDraw.Draw(im)
    cx, cy, r, w = 930, 315, 170, 16
    box = [cx - r, cy - r, cx + r, cy + r]
    d.ellipse(box, outline=(40, 40, 42), width=w)
    d.arc(box, start=-90, end=170, fill=(255, 255, 255), width=w)
    t = font(84)
    d.text((cx, cy - 8), "24:16", font=t, fill=(255, 255, 255), anchor="mm")
    d.text((cx, cy + 62), "ДО ЗВОНКА", font=font(22, True), fill=(138, 138, 145), anchor="mm")
    d.text((80, 150), "ЛИЦЕЙ №165", font=font(26, True), fill=(138, 138, 145))
    d.text((72, 190), "Звонок.", font=font(150), fill=(255, 255, 255))
    d.text((80, 390), "Сколько до звонка,", font=font(40), fill=(199, 199, 204))
    d.text((80, 440), "какой урок дальше и где кабинет", font=font(40), fill=(199, 199, 204))
    d.text((80, 540), SITE_URL.replace("https://", "").rstrip("/"), font=font(24, True), fill=(90, 90, 98))
    return im


def scriptable_template(data):
    """Скрипт для Scriptable (iPhone): общие данные (каникулы, предметы, сайт) вписываем сразу,
    а класс, звонки, неделю и тему сайт подставит сам (метки __CLASS__, __BELLS__, __WEEK__, __THEME__)."""
    js = read("ios", "Uroki7B.js")
    a = js.index("// ---------- данные")
    b = js.index("// ---------- конец данных ----------")
    j = lambda o: json.dumps(o, ensure_ascii=False, separators=(",", ":"))
    block = "\n".join([
        "// ---------- данные (сайт подставляет сюда выбранный класс) ----------",
        'const CLASS = "__CLASS__";',
        'const THEME = "__THEME__"; // код темы с сайта, например "ZV1;bg=0B1220;ac=60A5FA". Параметр виджета важнее',
        "const BELLS = __BELLS__;",
        "const WEEK = __WEEK__;",
        "const HOLIDAYS = " + j(data["holidays"]) + ";",
        "const TERM_END = " + j(data["term_end"]) + ";",
        "const SUBJECTS = " + j(data["subjects"]) + ";",
        "const SITE = " + j(SITE_URL) + ";",
        ""])
    js = js[:a] + block + js[b:]
    js = js.replace("// Звонок 7Б:", "// Звонок __CLASS__:")
    return js


def ios_sample(data):
    """ios/Uroki7B.js держим рабочим для 7Б со свежими данными (его можно вставить в Scriptable как есть)."""
    js = read("ios", "Uroki7B.js")
    a = js.index("// ---------- данные")
    b = js.index("// ---------- конец данных ----------")
    j = lambda o: json.dumps(o, ensure_ascii=False, separators=(",", ":"))
    bells = [[int(x.split(":")[0]) * 60 + int(x.split(":")[1]) for x in pair] for pair in data["bells"]]
    week = {}
    for d, day in enumerate(data["classes"]["7Б"]):
        arr = []
        for raw in day:
            code, _, room = (raw or "").partition(" ")
            arr.append([data["subjects"].get(code, code), "спортзал" if room == "с/з" else room] if raw else ["", ""])
        if arr:
            week[str(d + 1)] = arr
    block = "\n".join([
        "// ---------- данные (сайт подставляет сюда выбранный класс) ----------",
        'const CLASS = "7Б";',
        'const THEME = ""; // код темы с сайта, например "ZV1;bg=0B1220;ac=60A5FA". Параметр виджета важнее',
        "const BELLS = " + j(bells) + ";",
        "const WEEK = " + j(week) + ";",
        "const HOLIDAYS = " + j(data["holidays"]) + ";",
        "const TERM_END = " + j(data["term_end"]) + ";",
        "const SUBJECTS = " + j(data["subjects"]) + ";",
        "const SITE = " + j(SITE_URL) + ";",
        ""])
    out = js[:a] + block + js[b:]
    if out != js:
        with open(os.path.join(ROOT, "ios", "Uroki7B.js"), "w", encoding="utf-8", newline="\n") as f:
            f.write(out)


def js_literal(obj):
    """JSON для вставки внутрь <script>: без «</script>» и невидимых разделителей строк."""
    t = json.dumps(obj, ensure_ascii=False, separators=(",", ":"))
    return (t.replace("</", "<" + chr(92) + "/")
            .replace(chr(0x2028), chr(92) + "u2028")
            .replace(chr(0x2029), chr(92) + "u2029"))


LAT = {"А": "A", "Б": "B", "В": "V", "Г": "G"}
FIRST_MONDAY = (2026, 11, 2)


def slug(cls):
    return "".join(LAT.get(ch, ch) for ch in cls)


def ics_text(t):
    return t.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")


def ics_fold(line):
    out, cur = [], ""
    for ch in line:
        if len((cur + ch).encode()) > (75 if not out else 74):
            out.append(cur)
            cur = ""
        cur += ch
    out.append(cur)
    return "\r\n ".join(out)


def day_lessons(data, changes, cls, date):
    """Уроки класса на дату с каникулами и заменами (та же логика, что на сайте и в приложениях).
    Возвращает [(номер, начало, конец, предмет, кабинет)]."""
    iso = date.isoformat()
    ch = changes.get("days", {}).get(iso) or {}
    if any(h[0] <= iso <= h[1] for h in data["holidays"]) or ch.get("off"):
        return []
    bells = list(data["bells"])
    if ch.get("bells"):  # звонки на этот день целиком
        bells = [b or (data["bells"][i] if i < len(data["bells"]) else None) for i, b in enumerate(ch["bells"])]
    raw = data["classes"][cls][date.weekday()] if date.weekday() < len(data["classes"][cls]) else []
    out = {}
    for i, r in enumerate(raw):
        if r and i < len(bells) and bells[i]:
            code, _, room = r.partition(" ")
            out[i + 1] = [data["subjects"].get(code, code), room]
    for n, f in ((ch.get("classes") or {}).get(cls) or {}).items():
        n = int(n)
        if f is None or (isinstance(f, str) and f.strip().lower().startswith("нет")):
            out.pop(n, None)
        elif isinstance(f, dict) and n <= len(bells) and bells[n - 1]:
            old = out.get(n, ["", ""])
            subj = f.get("subject") or old[0]
            out[n] = [data["subjects"].get(subj, subj), f["room"] if f.get("room") is not None else old[1]]
    return [(n, bells[n - 1][0], bells[n - 1][1], v[0], "спортзал" if v[1] == "с/з" else v[1])
            for n, v in sorted(out.items()) if v[0]]


def calendar(data, changes, cls):
    """Календарь класса: уроки повторяются раз в неделю до конца четверти, кроме каникул и праздников.
    Дни с заменами вырезаны из повторов и добавлены отдельными событиями. За 3 минуты до урока
    iPhone показывает напоминание."""
    import datetime
    base = datetime.date(*FIRST_MONDAY)
    end = datetime.date.fromisoformat(data["term_end"])
    stamp = datetime.datetime.strptime(data["updated"], "%Y-%m-%d").strftime("%Y%m%dT000000Z")
    days = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"]
    lines = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Zvonok//165//RU", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
             "X-WR-CALNAME:Звонок " + cls, "X-WR-CALDESC:Расписание " + cls + ", " + data["school"],
             "X-PUBLISHED-TTL:PT12H", "REFRESH-INTERVAL;VALUE=DURATION:PT12H"]
    t = lambda date, hm: date.strftime("%Y%m%d") + "T%02d%02d00" % tuple(int(x) for x in hm.split(":"))

    def where(room):
        return room if room == "спортзал" or not room else "каб. " + room

    def event(uid, date, start, stop, subj, room, num, extra=()):
        what = subj + (", " + where(room) if room else "")
        return ["BEGIN:VEVENT", "UID:" + uid, "DTSTAMP:" + stamp,
                "DTSTART:" + t(date, start), "DTEND:" + t(date, stop), *extra,
                "SUMMARY:" + ics_text(what), "LOCATION:" + ics_text(room), "DESCRIPTION:" + ics_text("%d урок" % num),
                "TRANSP:OPAQUE",
                "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT3M", "DESCRIPTION:" + ics_text("Через 3 мин: " + what), "END:VALARM",
                "END:VEVENT"]

    # даты четверти, где день отличается от обычной недели: каникулы, праздники, замены
    special = set()
    date = base
    while date <= end:
        iso = date.isoformat()
        if any(h[0] <= iso <= h[1] for h in data["holidays"]) or iso in changes.get("days", {}):
            special.add(date)
        date += datetime.timedelta(days=1)

    for d, lessons in enumerate(data["classes"][cls]):
        first = base + datetime.timedelta(days=d)
        for i, raw in enumerate(lessons):
            if not raw or i >= len(data["bells"]):
                continue
            code, _, room = raw.partition(" ")
            room = "спортзал" if room == "с/з" else room
            ex = sorted(x for x in special if x.weekday() == d)
            extra = ["RRULE:FREQ=WEEKLY;BYDAY=%s;UNTIL=%sT235959" % (days[d], end.strftime("%Y%m%d"))]
            extra += ["EXDATE:" + t(x, data["bells"][i][0]) for x in ex]
            lines += event("%s-%d-%d@zvonok165" % (slug(cls), d + 1, i + 1), first, data["bells"][i][0], data["bells"][i][1],
                           data["subjects"].get(code, code), room, i + 1, extra)
    for date in sorted(special):
        for n, start, stop, subj, room in day_lessons(data, changes, cls, date):
            lines += event("%s-%s-%d@zvonok165" % (slug(cls), date.strftime("%Y%m%d"), n), date, start, stop, subj, room, n)
    lines.append("END:VCALENDAR")
    return "\r\n".join(ics_fold(l) for l in lines) + "\r\n"


def main():
    data = json.loads(read("data", "schedule.json"))
    changes = json.loads(read("data", "changes.json"))
    ios_sample(data)
    tpl = read("site", "src", "index.html")

    ico = icon(180)
    buf = io.BytesIO()
    icon(64).save(buf, "PNG")
    favicon = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

    page = (tpl
            .replace("/*@DATA@*/null", js_literal(data))
            .replace("/*@SCRIPTABLE@*/null", js_literal(scriptable_template(data)))
            .replace("/*@CHANGES@*/null", js_literal(changes))
            .replace("/*@QRS@*/null", js_literal(dict([("", qr_code(SITE_URL))] + [(c, qr_code(SITE_URL + "#" + slug(c).lower())) for c in data["classes"]])))
            .replace("@QR@", qr_svg(SITE_URL))
            .replace("@FAVICON@", favicon)
            .replace("@SITE_URL@", SITE_URL)
            .replace("@APK_URL@", APK_URL))
    assert "@" + "QR@" not in page
    write("index.html", page)

    poster = (read("site", "src", "poster.html")
              .replace("@QR@", qr_svg(SITE_URL))
              .replace("@FAVICON@", favicon)
              .replace("@SITE_URL@", SITE_URL.replace("https://", "").rstrip("/")))
    write("poster.html", poster)

    ico.save(os.path.join(OUT, "apple-touch-icon.png"))
    icon(192).save(os.path.join(OUT, "icon-192.png"))
    icon(512).save(os.path.join(OUT, "icon-512.png"))
    icon(512, pad_ratio=0.35).save(os.path.join(OUT, "icon-maskable.png"))

    write("manifest.webmanifest", json.dumps({
        "name": "Звонок. Расписание лицея №165",
        "short_name": "Звонок",
        "description": "Расписание всех классов лицея №165: таймер до звонка, кабинет, следующий урок. Работает офлайн.",
        "lang": "ru",
        "start_url": "./",
        "scope": "./",
        "display": "standalone",
        "background_color": "#000000",
        "theme_color": "#000000",
        "icons": [
            {"src": "icon-192.png", "sizes": "192x192", "type": "image/png"},
            {"src": "icon-512.png", "sizes": "512x512", "type": "image/png"},
            {"src": "icon-maskable.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable"},
        ],
    }, ensure_ascii=False, indent=2))
    css = read("site", "src", "common.css")
    classes = sorted(data["classes"], key=lambda c: (int(re.match(r"\d+", c)[0]), c))
    for name, extra in (("changelog.html", {"/*@CHANGELOG@*/[]": js_literal(json.loads(read("data", "changelog.json")))}),
                        ("feedback.html", {"/*@CLASSES@*/[]": js_literal(classes)}),
                        ("iphone.html", {})):
        t = read("site", "src", name).replace("@COMMON_CSS@", css).replace("@HEADER@", header(name)).replace("@FAVICON@", favicon)
        for k, v in extra.items():
            assert k in t, k
            t = t.replace(k, v)
        write(name, t)
    write("sw.js", read("site", "src", "sw.js"))
    write(".nojekyll", "")
    os.makedirs(os.path.join(OUT, "cal"), exist_ok=True)
    for c in data["classes"]:
        with open(os.path.join(OUT, "cal", slug(c) + ".ics"), "w", encoding="utf-8", newline="") as f:
            f.write(calendar(data, changes, c))
    write("changes.json", json.dumps(changes, ensure_ascii=False, indent=1))
    # расписание целиком: приложение скачивает его отсюда, чтобы новое расписание пришло без обновления APK
    write("schedule.json", json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    # в GitHub Actions другие шрифты: картинку превью не перерисовываем, берём закоммиченную
    if not os.environ.get("CI") or not os.path.exists(os.path.join(OUT, "og.png")):
        og_image().save(os.path.join(OUT, "og.png"), optimize=True)
    print("site/index.html", len(page) // 1024, "KB")


if __name__ == "__main__":
    main()
