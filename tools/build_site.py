"""
Сборка сайта «Звонок» в папку site/.

  python tools/build_site.py

Берёт data/schedule.json, site/src/index.html, ios/Uroki7B.js и собирает:
  site/index.html          : весь сайт одним файлом (работает офлайн, даже как файл из Telegram)
  site/poster.html         : плакат A4 с QR-кодом для класса
  site/cal/*.ics           : календарь каждого класса для виджета на экране блокировки iPhone
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
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SITE_URL = "https://danick-dack.github.io/Zvonok/"
APK_URL = "https://github.com/Danick-Dack/Zvonok/releases/latest/download/Zvonok-165.apk"
OUT = os.path.join(ROOT, "site")


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


def scriptable_template():
    """Скрипт для Scriptable (iPhone) с метками вместо данных 7Б: сайт подставит любой класс."""
    js = read("ios", "Uroki7B.js")
    js = re.sub(r"const BELLS = \[.*?\];", "const BELLS = __BELLS__;", js, count=1, flags=re.S)
    js = re.sub(r"const WEEK = \{.*?\n\};", "const WEEK = __WEEK__;", js, count=1, flags=re.S)
    # пустые уроки («окна») пропускаем
    js = js.replace(
        "  return raw.map((p, i) => ({\n    num: i + 1, start: BELLS[i][0], end: BELLS[i][1], subject: p[0], room: p[1],\n  }));",
        "  return raw.map((p, i) => (p && p[0] ? {\n    num: i + 1, start: BELLS[i][0], end: BELLS[i][1], subject: p[0], room: p[1],\n  } : null)).filter(Boolean);")
    js = js.replace("Уроки 7Б", "__TITLE__").replace("Расписание 7Б", "Расписание __CLASS__")
    assert "__BELLS__" in js and "__WEEK__" in js, "не нашёл данные в ios/Uroki7B.js"
    return js


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


def calendar(data, cls):
    """Календарь класса: каждый урок повторяется раз в неделю. iPhone показывает ближайший урок
    в виджете «Календарь» на экране блокировки, без сторонних приложений."""
    import datetime
    base = datetime.date(*FIRST_MONDAY)
    stamp = datetime.datetime.strptime(data["updated"], "%Y-%m-%d").strftime("%Y%m%dT000000Z")
    days = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"]
    lines = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Zvonok//165//RU", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
             "X-WR-CALNAME:Звонок " + cls, "X-WR-CALDESC:Расписание " + cls + ", " + data["school"],
             "X-PUBLISHED-TTL:PT12H", "REFRESH-INTERVAL;VALUE=DURATION:PT12H"]
    for d, lessons in enumerate(data["classes"][cls]):
        date = base + datetime.timedelta(days=d)
        for i, raw in enumerate(lessons):
            if not raw or i >= len(data["bells"]):
                continue
            code, _, room = raw.partition(" ")
            room = "спортзал" if room == "с/з" else room
            subj = data["subjects"].get(code, code)
            t = lambda hm: date.strftime("%Y%m%d") + "T%02d%02d00" % tuple(int(x) for x in hm.split(":"))
            lines += ["BEGIN:VEVENT", "UID:%s-%d-%d@zvonok165" % (slug(cls), d + 1, i + 1), "DTSTAMP:" + stamp,
                      "DTSTART:" + t(data["bells"][i][0]), "DTEND:" + t(data["bells"][i][1]),
                      "RRULE:FREQ=WEEKLY;BYDAY=" + days[d],
                      "SUMMARY:" + ics_text(subj + (", " + (room if room == "спортзал" else "каб. " + room) if room else "")),
                      "LOCATION:" + ics_text(room), "DESCRIPTION:" + ics_text("%d урок" % (i + 1)),
                      "TRANSP:OPAQUE", "END:VEVENT"]
    lines.append("END:VCALENDAR")
    return "\r\n".join(ics_fold(l) for l in lines) + "\r\n"


def main():
    data = json.loads(read("data", "schedule.json"))
    tpl = read("site", "src", "index.html")

    ico = icon(180)
    buf = io.BytesIO()
    icon(64).save(buf, "PNG")
    favicon = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

    page = (tpl
            .replace("/*@DATA@*/null", js_literal(data))
            .replace("/*@SCRIPTABLE@*/null", js_literal(scriptable_template()))
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
    write("sw.js", read("site", "src", "sw.js"))
    write(".nojekyll", "")
    os.makedirs(os.path.join(OUT, "cal"), exist_ok=True)
    for c in data["classes"]:
        with open(os.path.join(OUT, "cal", slug(c) + ".ics"), "w", encoding="utf-8", newline="") as f:
            f.write(calendar(data, c))
    print("site/index.html", len(page) // 1024, "KB")


if __name__ == "__main__":
    main()
