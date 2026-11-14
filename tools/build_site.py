"""
Сборка сайта «Звонок» в папку site/.

  py tools/build_site.py

Исходники лежат в site/src/: страницы (*.html), стили (css/) и скрипты (js/). Сборка встраивает их
в каждую страницу, поэтому любая страница работает без интернета и даже как файл из Telegram.
Встроенные скрипты разрешены в CSP по SHA-256, а не через 'unsafe-inline'.

Данные: data/schedule.json (расписание), data/changes.json (замены), data/changelog.json («Что нового»),
ios/Zvonok.js (скрипт виджетов iPhone для Scriptable).

Что получается:
  site/index.html            главная: таймер, неделя, кабинеты, установка (одним файлом)
  site/make.html             конструктор: своё расписание и цвета виджетов
  site/iphone.html           iPhone: видеогайд, скрипт виджета для своего класса, Siri
  site/changelog.html        «Что нового»
  site/feedback.html         «Написать нам» (анонимно)
  site/poster.html           плакат A4 с QR-кодом класса
  site/404.html              короткие ссылки вида …/Zvonok/10a
  site/cal/*.ics             календарь каждого класса с напоминанием за 3 минуты до урока
  site/changes.json, site/schedule.json   данные для сайта, приложения Android и скрипта iPhone
  site/manifest.webmanifest, site/sw.js, иконки, site/og.png   приложение с экрана «Домой» и превью ссылки
Нужны пакеты: qrcode, pillow (pip install qrcode pillow).
"""
import base64
import datetime
import hashlib
import io
import json
import math
import os
import re
import subprocess
import urllib.request

import qrcode
import qrcode.image.svg
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import ed25519

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "site", "src")
OUT = os.path.join(ROOT, "site")
SITE_URL = "https://danick-dack.github.io/Zvonok/"
APK_URL = "https://github.com/Danick-Dack/Zvonok/releases/latest/download/Zvonok-165.apk"

# фирменные цвета (как в css/tokens.css)
INK, PAPER = (11, 11, 12), (244, 244, 241)

# Цвета предметов: у каждого предмета свой оттенок, урок узнаётся с одного взгляда.
# Оттенок: (яркий на светлом, текст на светлом, яркий на тёмном, текст на тёмном). Близко к системным цветам iPhone.
# Текстовые варианты дают контраст не ниже 4,5:1 с фоном (WCAG AA); яркие идут на кольца, точки и полосы.
HUES = {
    "red":    ("#FF3B30", "#C40013", "#FF453A", "#FF6961"),
    "orange": ("#FF9500", "#B83D00", "#FF9F0A", "#FFB340"),
    "yellow": ("#FFCC00", "#8F5A00", "#FFD60A", "#FFD426"),
    "green":  ("#34C759", "#1F7A36", "#30D158", "#30DB5B"),
    "mint":   ("#00C7BE", "#0B7570", "#63E6E2", "#66D4CF"),
    "teal":   ("#30B0C7", "#006F83", "#40CBE0", "#5DE6FF"),
    "cyan":   ("#32ADE6", "#006A9A", "#64D2FF", "#70D7FF"),
    "blue":   ("#007AFF", "#0058D6", "#0A84FF", "#409CFF"),
    "indigo": ("#5856D6", "#3D3BB8", "#5E5CE6", "#8B89FF"),
    "purple": ("#AF52DE", "#8944AB", "#BF5AF2", "#DA8FFF"),
    "pink":   ("#FF2D55", "#BA0E40", "#FF375F", "#FF6482"),
    "brown":  ("#A2845E", "#765C3E", "#AC8E68", "#C4A27A"),
    "gray":   ("#8E8E93", "#636366", "#8E8E93", "#AEAEB2"),
}
# предмет → оттенок: родственные предметы рядом по цвету (математика синяя, языки тёплые, науки холодные)
SUBJECT_HUE = {"alg": "blue", "geom": "blue", "mat": "blue", "fiz": "purple", "prf": "purple", "kaz": "yellow",
               "rus": "red", "rlit": "red", "eng": "cyan", "istk": "brown", "vist": "brown", "geo": "mint", "bio": "green",
               "him": "teal", "inf": "indigo", "prog": "indigo", "fk": "orange", "hud": "pink", "kl": "gray", "prav": "brown",
               "nvp": "green", "avto": "orange"}

# главный цвет на выбор: кнопки, вкладки, отметки «сейчас». Первый по умолчанию.
# (ключ, название, светлая тема: цвет и текст на нём, тёмная: цвет и текст на нём)
ACCENTS = [
    ("blue", "Синий", "#0062DA", "#FFFFFF", "#409CFF", "#0B0B0C"),
    ("indigo", "Индиго", "#3D3BB8", "#FFFFFF", "#8B89FF", "#0B0B0C"),
    ("purple", "Фиолетовый", "#8944AB", "#FFFFFF", "#DA8FFF", "#0B0B0C"),
    ("pink", "Розовый", "#C70F45", "#FFFFFF", "#FF6482", "#0B0B0C"),
    ("red", "Красный", "#D70015", "#FFFFFF", "#FF6961", "#0B0B0C"),
    ("orange", "Оранжевый", "#B83D00", "#FFFFFF", "#FFB340", "#0B0B0C"),
    ("green", "Зелёный", "#1F7A36", "#FFFFFF", "#30DB5B", "#0B0B0C"),
    ("mint", "Мятный", "#0B7570", "#FFFFFF", "#66D4CF", "#0B0B0C"),
    ("mono", "Без цвета", "#141414", "#FFFFFF", "#F3F3F1", "#0B0B0C"),
]
ACCENT = (64, 156, 255)  # синий из тёмной темы: для картинок


def rgba(hexc, a):
    r, g, b = (int(hexc[i:i + 2], 16) for i in (1, 3, 5))
    return "rgba(%d,%d,%d,%s)" % (r, g, b, a)


def themed(sel, light, dark):
    """Правило для светлой темы и для тёмной (по системе и вручную)."""
    return "\n".join([
        "%s{%s}" % (sel % ":root", light),
        '@media (prefers-color-scheme:dark){%s{%s}}' % (sel % ':root:not([data-theme="light"])', dark),
        "%s{%s}" % (sel % ':root[data-theme="dark"]', dark)])


def accent_css():
    """CSS для выбранного главного цвета и цветов предметов."""
    out = []
    for key, _, la, li, da, di in ACCENTS[1:]:
        out.append(themed('%%s[data-accent="%s"]' % key,
                          "--accent:%s;--accent-ink:%s;--accent-soft:%s" % (la, li, rgba(la, ".1")),
                          "--accent:%s;--accent-ink:%s;--accent-soft:%s" % (da, di, rgba(da, ".16"))))
    for key, (lv, lt, dv, dt) in HUES.items():
        # --sv яркий (кольцо, точки), --st текст, --ss мягкий фон, --sf заливка, --si текст на заливке
        out.append(themed("%%s .hue-%s" % key,
                          "--sv:%s;--st:%s;--ss:%s;--sf:%s;--si:#FFFFFF" % (lv, lt, rgba(lv, ".2"), lt),
                          "--sv:%s;--st:%s;--ss:%s;--sf:%s;--si:#0B0B0C" % (dv, dt, rgba(dv, ".24"), dt)))
    return "\n".join(out)


def subject_colors(data):
    """Название предмета → [яркий на светлом, яркий на тёмном] без «#» (для виджета iPhone)."""
    return {name: [HUES[h][0][1:], HUES[h][2][1:]] for name, h in subject_hues(data).items()}


def subject_hues(data):
    """Название предмета → оттенок (для своих расписаний сайт подберёт оттенок по названию сам)."""
    return {name: SUBJECT_HUE.get(code, "gray") for code, name in data["subjects"].items()}


def read(*p):
    with open(os.path.join(ROOT, *p), encoding="utf-8") as f:
        return f.read()


def write(name, text):
    path = os.path.join(OUT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def js_literal(obj):
    """JSON для вставки внутрь <script>: без «</script>» и невидимых разделителей строк."""
    t = json.dumps(obj, ensure_ascii=False, separators=(",", ":"))
    return (t.replace("</", "<" + chr(92) + "/")
            .replace(chr(0x2028), chr(92) + "u2028")
            .replace(chr(0x2029), chr(92) + "u2029"))


def min_css(css):
    """Без комментариев и лишних пробелов. Строки в кавычках не трогаем."""
    parts = re.split(r'("(?:[^"\\]|\\.)*"|\'(?:[^\'\\]|\\.)*\')', css)
    out = []
    for i, p in enumerate(parts):
        if i % 2:
            out.append(p)
            continue
        p = re.sub(r"/\*.*?\*/", "", p, flags=re.S)
        p = re.sub(r"\s+", " ", p)
        p = re.sub(r"\s*([{};,>])\s*", r"\1", p)
        p = re.sub(r":\s+", ":", p)
        p = p.replace(";}", "}")
        out.append(p)
    return "".join(out).strip()


# рисунок в шапке вкладки: её значок из спрайта, крупно и бледно (маска, цвет задаёт CSS)
TAB_ART = {"week": "now", "rooms": "door", "exams": "exam", "news": "news", "more": "more", "olymp": "cup", "get": "phone"}


def art_css():
    svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='#000' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'>%s</svg>"
    enc = lambda name: (svg % ICONS[name].replace('"', "'").replace("currentColor", "#000")).replace("#", "%23").replace("<", "%3C").replace(">", "%3E")
    return "\n".join('.th-%s{--art:url("data:image/svg+xml,%s")}' % (tab, enc(name)) for tab, name in TAB_ART.items())


def bundle_css(names):
    css = "\n".join(read("site", "src", "css", n + ".css") for n in names)
    return min_css(css.replace("/*@ACCENTS@*/", accent_css()).replace("/*@ART@*/", art_css()))


REGEX_AFTER = set("(,=:[!&|?{};+-*%<>~^")
REGEX_WORDS = ("return", "typeof", "case", "in", "of", "void", "delete", "new", "else", "do", "throw", "yield", "await")


def min_js(js):
    """Сжатие кода сайта: без комментариев, отступов и пустых строк. Переводы строк остаются (автоточки с запятой
    работают как раньше), строки, шаблоны `…` и регулярные выражения копируются как есть."""
    out, i, n = [], 0, len(js)

    def regex_ok():
        tail = "".join(out[-12:]).rstrip()
        return not tail or tail[-1] in REGEX_AFTER or re.search(r"(?<![\w$.])(%s)$" % "|".join(REGEX_WORDS), tail)

    while i < n:
        c = js[i]
        if c in "\"'`":  # строка или шаблон: до закрывающей кавычки, с учётом \
            j = i + 1
            while j < n and js[j] != c:
                j += 2 if js[j] == "\\" else 1
            if c != "`" and j < n and "\n" in js[i:j]:
                raise ValueError("перевод строки внутри строки: %r" % js[i:i + 40])
            out.append(js[i:j + 1])
            i = j + 1
        elif js.startswith("//", i):
            i = js.find("\n", i)
            i = n if i < 0 else i
        elif js.startswith("/*", i):
            j = js.find("*/", i + 2)
            if j < 0:
                raise ValueError("незакрытый комментарий")
            out.append(" ")  # a/*…*/b не должно склеиться в ab
            i = j + 2
        elif c == "/" and regex_ok():  # регулярное выражение: до / вне [...]
            j, cls = i + 1, False
            while j < n and (js[j] != "/" or cls):
                if js[j] == "\n":
                    raise ValueError("не похоже на регулярное выражение: %r" % js[i:i + 40])
                cls = (cls or js[j] == "[") and js[j] != "]"
                j += 2 if js[j] == "\\" else 1
            out.append(js[i:j + 1])
            i = j + 1
        else:
            j = i
            while j < n and js[j] not in "\"'`/":
                j += 1
            j = max(j, i + 1)
            out.append(js[i:j])
            i = j
    return _trim_lines(out)


def _trim_lines(chunks):
    """Убирает пробелы в начале и конце строк и пустые строки, не заходя внутрь строк и шаблонов."""
    res, at_line_start = [], True
    for ch in chunks:
        if ch[:1] in "\"'`" or (ch[:1] == "/" and len(ch) > 1):
            res.append(ch)
            at_line_start = False
            continue
        parts = ch.split("\n")
        for k, part in enumerate(parts):
            if k:
                # перевод строки: сначала срезаем хвостовые пробелы прошлой строки
                while res and res[-1] and res[-1][-1] in " \t" and res[-1][:1] not in "\"'`":
                    res[-1] = res[-1].rstrip(" \t")
                if res and not at_line_start:
                    res.append("\n")
                at_line_start = True
            if at_line_start:
                part = part.lstrip(" \t")
            if part:
                res.append(part)
                at_line_start = False
    return "".join(res).strip() + "\n"


def bundle_js(names):
    js = "\n".join(read("site", "src", "js", n + ".js") for n in names)
    return js.replace("/*@ACCENTS@*/null", js_literal([{"id": a[0], "name": a[1], "light": a[2], "dark": a[4]} for a in ACCENTS]))


# ---------- значки: один спрайт на страницу, <svg><use href="#i-…"></svg> ----------
ICONS = {
    "now": '<circle cx="12" cy="12" r="8.6"/><path d="M12 7.6V12l2.9 1.9"/>',
    "week": '<rect x="3.6" y="5" width="16.8" height="15.4" rx="3"/><path d="M3.6 9.8h16.8M8.2 3v3.6M15.8 3v3.6"/>',
    "door": '<path d="M6 20.6V4.8a1.2 1.2 0 0 1 1.2-1.2h9.6A1.2 1.2 0 0 1 18 4.8v15.8M3.8 20.6h16.4"/><circle cx="14.6" cy="12.4" r=".7" fill="currentColor"/>',
    "more": '<circle cx="5.6" cy="12" r="1.1" fill="currentColor"/><circle cx="12" cy="12" r="1.1" fill="currentColor"/><circle cx="18.4" cy="12" r="1.1" fill="currentColor"/>',
    "search": '<circle cx="10.6" cy="10.6" r="6.2"/><path d="M15.2 15.2l4.8 4.8"/>',
    "down": '<path d="M6.5 9.5l5.5 5.5 5.5-5.5"/>',
    "right": '<path d="M9.5 6.5l5.5 5.5-5.5 5.5"/>',
    "close": '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>',
    "sun": '<circle cx="12" cy="12" r="3.8"/><path d="M12 2.8v2.1M12 19.1v2.1M4.9 4.9l1.5 1.5M17.6 17.6l1.5 1.5M2.8 12h2.1M19.1 12h2.1M4.9 19.1l1.5-1.5M17.6 6.4l1.5-1.5"/>',
    "moon": '<path d="M19.8 14.6A8.2 8.2 0 0 1 9.4 4.2a8.2 8.2 0 1 0 10.4 10.4z"/>',
    "auto": '<circle cx="12" cy="12" r="8.4"/><path d="M12 3.6v16.8a8.4 8.4 0 0 0 0-16.8z" fill="currentColor"/>',
    "share": '<path d="M12 3.4v11.2M8.2 7.2L12 3.4l3.8 3.8"/><path d="M8.4 10.6H6.6a1.6 1.6 0 0 0-1.6 1.6v6.8a1.6 1.6 0 0 0 1.6 1.6h10.8a1.6 1.6 0 0 0 1.6-1.6v-6.8a1.6 1.6 0 0 0-1.6-1.6h-1.8"/>',
    "addsq": '<rect x="4" y="4" width="16" height="16" rx="4.2"/><path d="M12 8.4v7.2M8.4 12h7.2"/>',
    "download": '<path d="M12 4v11M7.6 10.8L12 15.2l4.4-4.4M5 19.8h14"/>',
    "copy": '<rect x="8.4" y="8.4" width="11.6" height="11.6" rx="2.6"/><path d="M15.6 8.4V6.6A2.6 2.6 0 0 0 13 4H6.6A2.6 2.6 0 0 0 4 6.6V13a2.6 2.6 0 0 0 2.6 2.6h1.8"/>',
    "check": '<path d="M5.2 12.6l4.3 4.3 9.3-9.6"/>',
    "out": '<path d="M13.6 4.2h6.2v6.2M19.6 4.4l-8.4 8.4M18 14v4.6a1.4 1.4 0 0 1-1.4 1.4H5.4A1.4 1.4 0 0 1 4 18.6V7.4A1.4 1.4 0 0 1 5.4 6H10"/>',
    "bell": '<path d="M6.2 16.4V11a5.8 5.8 0 0 1 11.6 0v5.4l1.6 1.8H4.6z"/><path d="M10 20.4a2.1 2.1 0 0 0 4 0"/>',
    "edit": '<path d="M4.4 19.6h4L19 9l-4-4L4.4 15.6z"/><path d="M13.4 6.6l4 4"/>',
    "phone": '<rect x="6.6" y="2.8" width="10.8" height="18.4" rx="2.6"/><path d="M10.6 18.2h2.8"/>',
    "siri": '<path d="M12 3.6a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0v-5a3 3 0 0 1 3-3z"/><path d="M6.4 11.4a5.6 5.6 0 0 0 11.2 0M12 17v3.4"/>',
    "plus": '<path d="M12 5.4v13.2M5.4 12h13.2"/>',
    "help": '<circle cx="12" cy="12" r="8.6"/><path d="M9.6 9.4a2.5 2.5 0 0 1 4.8.9c0 1.7-2.4 2.2-2.4 3.7"/><circle cx="12" cy="17" r=".6" fill="currentColor"/>',
    "info": '<circle cx="12" cy="12" r="8.6"/><path d="M12 11v5.4"/><circle cx="12" cy="7.9" r=".6" fill="currentColor"/>',
    "lock": '<rect x="5" y="10.4" width="14" height="10" rx="2.4"/><path d="M8.2 10.4V8a3.8 3.8 0 0 1 7.6 0v2.4"/>',
    "trash": '<path d="M5 7h14M10 4.4h4M7 7l.8 12.2a1.4 1.4 0 0 0 1.4 1.3h5.6a1.4 1.4 0 0 0 1.4-1.3L17 7"/>',
    "exam": '<rect x="5" y="3.6" width="14" height="17.2" rx="2.6"/><path d="M8.6 8.4h6.8M8.6 12h3.4M9.2 16.2l1.7 1.7 3.8-4"/>',
    "news": '<path d="M4 5.4h13v12.8a1.6 1.6 0 0 0 3.2 0V9.2H17"/><path d="M4 5.4v12.8a1.8 1.8 0 0 0 1.8 1.8h12.8M7.4 9h6.2M7.4 12.4h6.2M7.4 15.8h3.6"/>',
    "star": '<path d="M12 3.6l2.5 5.3 5.7.7-4.2 4 1.1 5.7L12 16.5l-5.1 2.8 1.1-5.7-4.2-4 5.7-.7z"/>',
    "mail": '<rect x="3.6" y="5.4" width="16.8" height="13.2" rx="2.6"/><path d="M4.4 7.4l7.6 5.6 7.6-5.6"/>',
    "play": '<circle cx="12" cy="12" r="8.6"/><path d="M10.2 8.6v6.8l5.4-3.4z" fill="currentColor"/>',
    "flag": '<path d="M5.6 20.6V4.2M5.6 5h10.2l-1.9 3.4 1.9 3.4H5.6"/>',
    "cup": '<path d="M7 4h10v5a5 5 0 0 1-10 0V4zM7 6H4.5a1.5 1.5 0 0 0-1.5 1.5v.5a4 4 0 0 0 4 4M17 6h2.5A1.5 1.5 0 0 1 21 7.5v.5a4 4 0 0 1-4 4M12 14v5M8.5 19h7"/>',
}


def icon_sprite(page):
    used = sorted(set(re.findall(r'href="#i-([a-z]+)"', page)) | set(re.findall(r'icon\("([a-z]+)"', page)))
    missing = [u for u in used if u not in ICONS]
    assert not missing, "нет значков: %s" % missing
    syms = "".join('<symbol id="i-%s" viewBox="0 0 24 24">%s</symbol>' % (n, ICONS[n]) for n in used)
    return '<svg width="0" height="0" style="position:absolute" aria-hidden="true">' + syms + "</svg>"


# фирменный знак: часы-таймер. Тёмное кольцо, белая дуга на три четверти и стрелки, одним цветом.
# Те же размеры, что у иконки приложения Android (app/src/main/res/drawable/ic_launcher_fg.xml, сетка 108×108).
MARK = ('<svg viewBox="25 25 58 58" aria-hidden="true"><circle class="mark-track" cx="54" cy="54" r="25" fill="none" stroke-width="5.5"/>'
        '<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M54 29a25 25 0 1 1-25 25" stroke-width="5.5"/>'
        '<path d="M54 41v13l7.5 4.3" stroke-width="5"/></g><circle cx="54" cy="54" r="3.4" fill="currentColor"/></svg>')


def clock(d, cx, cy, k, fg=(255, 255, 255), track=(44, 44, 48), hands=True):
    """Часы на картинке PIL: сетка 108×108 с центром в (cx, cy), масштаб k пикселей на единицу сетки."""
    r, w = 25 * k, 5.5 * k
    box = [cx - r - w / 2, cy - r - w / 2, cx + r + w / 2, cy + r + w / 2]  # PIL рисует линию внутрь рамки
    d.ellipse(box, outline=track, width=round(w))
    d.arc(box, start=-90, end=180, fill=fg, width=round(w))
    dot = lambda x, y, rr: d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=fg)
    dot(cx, cy - r, w / 2)                        # скруглённые концы дуги
    dot(cx - r, cy, w / 2)
    if hands:
        p = lambda x, y: (cx + (x - 54) * k, cy + (y - 54) * k)
        hw = 5 * k
        d.line([p(54, 41), p(54, 54), p(61.5, 58.3)], fill=fg, width=round(hw), joint="curve")
        for x, y in ((54, 41), (61.5, 58.3)):
            dot(*p(x, y), hw / 2)
        dot(cx, cy, 3.4 * k)


# ---------- картинки ----------
def icon(size, maskable=False, bg=INK):
    """Иконка: тёмный квадрат и белые часы (как в приложении Android). Для маски Android часы мельче."""
    s = size * 4
    im = Image.new("RGB", (s, s), bg)
    clock(ImageDraw.Draw(im), s / 2, s / 2, s / 108 * (1.0 if maskable else 1.2))
    return im.resize((size, size), Image.LANCZOS)


def font(size, bold=False, light=False):
    names = (["seguisb.ttf", "segoeuib.ttf", "DejaVuSans-Bold.ttf", "arialbd.ttf"] if bold
             else ["segoeuisl.ttf", "segoeuil.ttf", "DejaVuSans-ExtraLight.ttf", "DejaVuSans.ttf", "arial.ttf"] if light
             else ["segoeui.ttf", "DejaVuSans.ttf", "arial.ttf"])
    for n in names:
        for d in (r"C:\Windows\Fonts", "/usr/share/fonts/truetype/dejavu", "/Library/Fonts"):
            f = os.path.join(d, n)
            if os.path.exists(f):
                return ImageFont.truetype(f, size)
    return ImageFont.load_default(size)


def og_image():
    """Превью ссылки 1200×630: часы-таймер и название. Белое на тёмном, как иконка."""
    W, H = 1200, 630
    im = Image.new("RGB", (W, H), INK)
    d = ImageDraw.Draw(im)
    clock(d, 905, 315, 8.2, track=(36, 36, 40))
    d.text((76, 118), "Лицей №165", font=font(30, bold=True), fill=(143, 143, 138))
    d.text((68, 160), "Звонок", font=font(132, bold=True), fill=(243, 243, 241))
    d.text((80, 352), "Сколько до звонка,", font=font(40), fill=(198, 198, 194))
    d.text((80, 402), "какой урок дальше", font=font(40), fill=(198, 198, 194))
    d.text((80, 452), "и в каком кабинете", font=font(40), fill=(198, 198, 194))
    d.text((80, 546), SITE_URL.replace("https://", "").rstrip("/"), font=font(24, bold=True), fill=(96, 96, 100))
    return im


def qr_code(url):
    """QR как строка «размер:hex» (страница рисует его сама): 19 классов весят ~3 КБ вместо ~60 КБ SVG."""
    q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=2)
    q.add_data(url)
    q.make(fit=True)
    m = q.get_matrix()
    bits = "".join("1" if x else "0" for row in m for x in row)
    bits += "0" * (-len(bits) % 4)
    return "%d:%s" % (len(m), "".join("%x" % int(bits[i:i + 4], 2) for i in range(0, len(bits), 4)))


def png_data_uri(im):
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


# заставки iPhone при запуске с экрана «Домой»: размер экрана в pt и плотность
SPLASH = [(375, 667, 2), (414, 736, 3), (375, 812, 3), (414, 896, 2), (414, 896, 3), (390, 844, 3), (428, 926, 3),
          (393, 852, 3), (430, 932, 3), (402, 874, 3), (420, 912, 3), (440, 956, 3)]


def splash_images():
    """PNG-заставки в site/splash/ и <link> для них (светлая и тёмная, как тема телефона)."""
    os.makedirs(os.path.join(OUT, "splash"), exist_ok=True)
    links = []
    for w, h, k in SPLASH:
        for dark in (False, True):
            W, H = w * k, h * k
            bg = INK if dark else PAPER
            im = Image.new("RGB", (W, H), bg)
            ic = icon(round(96 * k), bg=bg) if dark else icon(round(96 * k))
            # на светлой заставке иконка остаётся тёмной плиткой со скруглением, как на экране «Домой»
            mask = Image.new("L", ic.size, 0)
            ImageDraw.Draw(mask).rounded_rectangle([0, 0, ic.size[0] - 1, ic.size[1] - 1], radius=round(22 * k), fill=255)
            im.paste(ic, ((W - ic.size[0]) // 2, (H - ic.size[1]) // 2 - round(20 * k)), mask)
            name = "splash/%dx%d%s.png" % (W, H, "d" if dark else "")
            im.save(os.path.join(OUT, name), optimize=True)
            links.append('<link rel="apple-touch-startup-image" href="%s" media="screen and (device-width:%dpx) and (device-height:%dpx) and '
                         '(-webkit-device-pixel-ratio:%d) and (orientation:portrait) and (prefers-color-scheme:%s)">'
                         % (name, w, h, k, "dark" if dark else "light"))
    return "\n".join(links)


# ---------- скрипт iPhone (Scriptable) ----------
def sign_key():
    """Закрытый ключ подписи новых версий скрипта (Ed25519, 32 байта в base64): переменная IOS_SIGN_KEY
    (секрет GitHub Actions) или файл ~/.zvonok/ios-sign.key. В репозитории ключа нет и быть не должно.
    Без ключа скрипты собираются без подписи и без открытого ключа внутри: виджеты обновляются как раньше."""
    raw = os.environ.get("IOS_SIGN_KEY", "").strip()
    if not raw:
        f = os.path.join(os.path.expanduser("~"), ".zvonok", "ios-sign.key")
        raw = open(f, encoding="utf-8").read().strip() if os.path.exists(f) else ""
    if not raw:
        print("скрипт iPhone: ключа подписи нет (IOS_SIGN_KEY), версии без подписи")
        return None
    seed = base64.b64decode(raw)
    if len(seed) != 32:
        raise SystemExit("IOS_SIGN_KEY: нужен закрытый ключ Ed25519, 32 байта в base64")
    # чужой или опечатанный ключ запер бы виджеты навсегда: такой сборке лучше упасть
    if base64.b64encode(ed25519.public_key(seed)).decode() != PUBLIC_KEY:
        raise SystemExit("IOS_SIGN_KEY: это не ключ «Звонка» (открытая часть не совпадает с PUBLIC_KEY)")
    return seed


# Открытая часть ключа подписи (не секрет: она и так вписана в каждый скрипт). По ней сборка и проверки
# убеждаются, что в секрете IOS_SIGN_KEY лежит именно ключ «Звонка».
PUBLIC_KEY = "3P0BRx5wF1s6d2rpOK65p4Spf+v8vxgrRX7zQXravwg="
SIGN_SEED = None   # заполняет main()
UPDATE_KEY = ""    # открытый ключ, вписывается в скрипт


RULES_A = "// ---------- правила дня (сборка вставляет сюда site/src/js/rules.js, править там) ----------"
RULES_B = "// ---------- конец правил дня ----------"


def with_rules(js):
    """Правила дня одни на сайт и виджет: между метками в скрипте всегда текущий site/src/js/rules.js."""
    a = js.index(RULES_A) + len(RULES_A)
    b = js.index(RULES_B)
    return js[:a] + "\n" + read("site", "src", "js", "rules.js").strip() + "\n" + js[b:]


def data_block(data, cls, version):
    """Данные скрипта iPhone: класс по умолчанию и всё расписание (класс можно сменить в приложении)."""
    j = lambda o: json.dumps(o, ensure_ascii=False, separators=(",", ":"))
    return "\n".join([
        "// ---------- данные (сайт подставляет сюда класс по умолчанию) ----------",
        'const CLASS = "%s"; // класс скрипта: его новую версию виджет и скачивает' % cls,
        'const THEME = ""; // код темы с сайта, например "ZV1;bg=0B1220;ac=60A5FA". Параметр виджета важнее',
        'const VERSION = "%s";' % version,
        "const BUILT = 0; // время сборки (сайт подставляет своё): более старую версию поверх этой виджет не поставит",
        'const UPDATE_KEY = "%s"; // открытый ключ подписи новых версий (пусто: сборка была без ключа)' % UPDATE_KEY,
        "const START = null; // своё расписание со страницы iPhone: при первом запуске переходит в настройки",
        "const DATA = " + j(data) + "; // всё расписание: классы, звонки, каникулы, предметы",
        "const COLORS = " + j(subject_colors(data)) + "; // цвета предметов: для светлого и тёмного фона",
        "const SITE = " + j(SITE_URL) + ";",
        ""])


def with_data(js, block):
    a = js.index("// ---------- данные")
    b = js.index("// ---------- конец данных ----------")
    return js[:a] + block + js[b:]


def build_time():
    """Время последнего коммита: растёт от версии к версии и одинаково при пересборке того же коммита."""
    try:
        return int(subprocess.run(["git", "-C", ROOT, "log", "-1", "--format=%ct"], capture_output=True, text=True, check=True).stdout)
    except (OSError, ValueError, subprocess.CalledProcessError):
        return int(datetime.datetime.now().timestamp())


def github_counts():
    """Счётчики GitHub по всем релизам (все версии): сколько раз скачали APK, сколько раз приложения Android скачали
    version.json, проверяя обновление, и сколько раз сайт и приложение iPhone скачали пустой ping.json (раз в сутки
    с устройства). Считает сам GitHub: мы не видим ни IP, ни устройств. None, если GitHub не ответил."""
    head = {"Accept": "application/vnd.github+json", "User-Agent": "zvonok-site-build"}
    if os.environ.get("GITHUB_TOKEN"):
        head["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
    apk = checks = pings = 0
    page = 1
    try:
        while True:
            url = "https://api.github.com/repos/Danick-Dack/Zvonok/releases?per_page=100&page=%d" % page
            with urllib.request.urlopen(urllib.request.Request(url, headers=head), timeout=15) as r:
                rel = json.load(r)
            for a in (a for x in rel for a in x["assets"]):
                if a["name"].endswith(".apk"):
                    apk += a["download_count"]
                elif a["name"] == "version.json":
                    checks += a["download_count"]
                elif a["name"] == "ping.json":
                    pings += a["download_count"]
            if len(rel) < 100:
                return apk, checks, pings
            page += 1
    except (OSError, ValueError, KeyError, TypeError):
        return None


def stats():
    """stats.json для «О проекте»: скачивания APK и сколько раз за сутки приложения проверили обновление.
    «За сутки» = прирост счётчика version.json с прошлого замера (прошлый stats.json с сайта), пересчитанный на 24 часа."""
    counts = github_counts()
    if not counts:
        return None
    try:
        with urllib.request.urlopen(SITE_URL + "stats.json", timeout=10) as r:
            old = json.load(r)
    except (OSError, ValueError):
        old = {}
    now = int(datetime.datetime.now().timestamp())
    # day: телефоны Android за сутки, web: сайт и приложение iPhone за сутки
    out = {"downloads": counts[0], "checks": counts[1], "pings": counts[2], "at": now, "day": old.get("day", 0), "web": old.get("web", 0)}
    if "checks" in old and "at" in old:
        span = now - old["at"]
        if span >= 12 * 3600:
            out["day"] = max(0, round((counts[1] - old["checks"]) * 86400 / span))
            if "pings" in old:
                out["web"] = max(0, round((counts[2] - old["pings"]) * 86400 / span))
        else:  # прошлый замер был недавно: следующий считаем от него
            out["checks"], out["pings"], out["at"] = old["checks"], old.get("pings", counts[2]), old["at"]
    return out


def class_scripts(data, changelog, app_html):
    """site/ios/<класс>.js и site/ios/versions.json: виджет iPhone сам скачивает свежую версию для своего класса.
    Скрипты классов отличаются только классом по умолчанию. versions.json подписывает и сам сайт (index.html):
    его копию приложение iPhone хранит у себя и открывает без интернета."""
    tpl = with_rules(read("ios", "Zvonok.js"))
    os.makedirs(os.path.join(OUT, "ios"), exist_ok=True)
    versions, sigs, built = {}, {}, build_time()
    for c in data["classes"]:
        js = with_data(tpl, data_block(data, c, "__VERSION__")).replace("// Звонок:", "// Звонок %s:" % c, 1)
        ver = hashlib.sha256(js.encode("utf-8")).hexdigest()[:10]
        versions[slug(c)] = ver
        # время сборки не входит в номер версии: пересборка без изменений не рассылает обновление
        final = js.replace("__VERSION__", ver).replace("const BUILT = 0;", "const BUILT = %d;" % built, 1)
        write("ios/%s.js" % slug(c), final)
        if SIGN_SEED:  # подпись ровно тех байт, что лежат на сайте: виджет проверит её перед тем, как поставить версию
            sigs[slug(c)] = base64.b64encode(ed25519.sign(SIGN_SEED, final.encode("utf-8"))).decode()
    note = next((r["title"] for r in changelog if "ios" in r["platforms"]), "")
    extra = {"sig": sigs, "app": base64.b64encode(ed25519.sign(SIGN_SEED, app_html.encode("utf-8"))).decode()} if SIGN_SEED else {}
    write("ios/versions.json", json.dumps(dict({"note": note, "classes": versions}, **extra), ensure_ascii=False, indent=1))
    return versions


def ios_sample(data):
    """ios/Zvonok.js держим рабочим для 7Б со свежими данными (его можно вставить в Scriptable как есть)."""
    js = read("ios", "Zvonok.js")
    out = with_rules(with_data(js, data_block(data, "7Б", "")))
    if out != js:
        with open(os.path.join(ROOT, "ios", "Zvonok.js"), "w", encoding="utf-8", newline="\n") as f:
            f.write(out)


# ---------- календари классов ----------
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


# ---------- страницы ----------
NAV = [("./#now", "Сейчас"), ("./#week", "Неделя"), ("./#rooms", "Кабинеты"), ("./#news", "Новости"), ("./#more", "Ещё"), ("./#get", "Скачать на телефон")]
TABBAR = [("./#now", "now", "Сейчас"), ("./#week", "week", "Неделя"), ("./#rooms", "door", "Кабинеты"), ("./#news", "news", "Новости"), ("./#more", "more", "Ещё")]


def header(active=None):
    """Шапка отдельных страниц: тот же знак и меню, что на главной. На телефоне вкладки внизу, как в приложении."""
    links = "".join('<a href="%s"%s>%s</a>' % (h, ' aria-current="page"' if h == active else "", t) for h, t in NAV)
    tabs = "".join('<a href="%s"><svg class="icon" aria-hidden="true"><use href="#i-%s"/></svg><span>%s</span></a>' % t for t in TABBAR)
    return ('<header class="top"><div class="wrap top-in"><a class="brand" href="about" aria-label="Звонок, о проекте">%s<span>Звонок</span></a>'
            '<nav class="nav-top" aria-label="Разделы">%s</nav>'
            '<button class="icon-btn" id="schemeBtn" type="button" aria-label="Тема"></button></div></header>'
            '<nav class="tabbar" aria-label="Разделы">%s</nav>' % (MARK, links, tabs))


FOOTER = ('<footer class="foot"><div class="wrap"><nav aria-label="Ссылки"><a href="./">Расписание</a><a href="./#news">Новости</a><a href="./#olymp">Олимпиады</a><a href="about">О проекте</a><a href="make.html">Конструктор</a>'
          '<a href="iphone.html">iPhone</a><a href="android.html">Android</a><a href="changelog.html">Что нового</a><a href="feedback.html">Написать нам</a></nav>'
          '<p>«Звонок» сделал ученик лицея №165 для своих. Это неофициальный проект, все права защищены.</p></div></footer>')

# На компьютере в браузере главная сначала показывает сайт о проекте (about.html). Расписание: кнопка там
# (запоминается), ссылки с #вкладкой, ?app и установленное приложение. Проверка до отрисовки, без мигания.
# Не чаще раза за сеанс вкладки: логотип на странице о проекте ведёт в расписание, а если без сети вместо
# about.html придёт запасная главная (старый воркер без этой страницы), второй раз она не перенаправит.
HEAD_PC = ('(function(){try{var L=location,S=sessionStorage;if(/^http/.test(L.protocol)&&!L.hash&&!new URLSearchParams(L.search).has("app")'
           '&&!(navigator.standalone||matchMedia("(display-mode: standalone)").matches)'
           '&&matchMedia("(hover: hover) and (pointer: fine) and (min-width: 1024px)").matches'
           '&&localStorage.getItem("zv.pcApp")!=="true"&&!S.getItem("zv.toAbout")){S.setItem("zv.toAbout","1");L.replace("about")}}catch(e){}})();')

# тема и цвет до первой отрисовки, чтобы страница не мигала светлой.
# Обе вставки в <head> в своих функциях: их переменные не должны пересекаться с let/const скрипта страницы.
HEAD_JS = ('(function(){try{if(top!==self)void top.location.href}catch(e){document.documentElement.style.display="none"}})();'
           '(function(){try{var d=document.documentElement,g=function(k){return JSON.parse(localStorage.getItem("zv."+k))},a=g("accent"),s=g("scheme");'
           'if(typeof a==="string"&&/^[a-z]+$/.test(a)&&a!=="' + ACCENTS[0][0] + '")d.setAttribute("data-accent",a);if(g("plain")===true)d.setAttribute("data-plain","");'
           'if(s==="light"||s==="dark"){d.setAttribute("data-theme",s);'
           'var m=document.querySelectorAll(\'meta[name="theme-color"]\');for(var i=0;i<m.length;i++)m[i].setAttribute("content",s==="dark"?"#0B0B0C":"#F4F4F1")}}catch(e){}})();')


def csp(page, connect="'self'"):
    """CSP по хешам: выполняются только скрипты, которые собрала сборка."""
    hashes = []
    # хешируем и блок данных JSON-LD: браузер его не выполняет, но валидаторы ждут, что каждый <script> разрешён
    for m in re.finditer(r"<script[^>]*>(.*?)</script>", page, re.S):
        hashes.append("'sha256-%s'" % base64.b64encode(hashlib.sha256(m.group(1).encode("utf-8")).digest()).decode())
    return ("default-src 'self'; script-src %s; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
            "connect-src %s; manifest-src 'self' blob:; worker-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'"
            % (" ".join(sorted(set(hashes))), connect))


def page(name, css, js, extra=None, connect="'self'", common=None):
    t = read("site", "src", name)
    common = common or {}
    js_text = bundle_js(js) if js else ""
    for k, v in (extra or {}).items():
        assert k in js_text or k in t, k
        js_text = js_text.replace(k, v)
        t = t.replace(k, v)
    js_text = min_js(js_text) if js_text else ""
    t = t.replace("@JS@", js_text)
    rep = {"@CSS@": bundle_css(css) if css else "", "@HEAD_JS@": HEAD_JS, "@HEADER@": header(), "@FOOTER@": FOOTER, "@MARK@": MARK}
    rep.update(common)
    for k, v in rep.items():
        t = t.replace(k, v)
    t = t.replace("@ICONS@", icon_sprite(t))
    t = t.replace("@CSP@", csp(t, connect))
    left = re.findall(r"@[A-Z_]{3,}@", t)
    assert not left, (name, left)
    write(name, t)
    return t


# счётчик устройств: пустой ping.json из релиза GitHub (github.com отвечает переадресацией на хранилище файлов)
PING_HOSTS = "https://github.com https://objects.githubusercontent.com https://release-assets.githubusercontent.com"


def main():
    global SIGN_SEED, UPDATE_KEY
    SIGN_SEED = sign_key()
    UPDATE_KEY = base64.b64encode(ed25519.public_key(SIGN_SEED)).decode() if SIGN_SEED else ""
    data = json.loads(read("data", "schedule.json"))
    changes = json.loads(read("data", "changes.json"))
    changelog = json.loads(read("data", "changelog.json"))
    olympiads = json.loads(read("data", "olympiads.json"))
    today_date = datetime.date.today()
    olympiads["items"] = [
        item for item in olympiads.get("items", [])
        if not item.get("endDate") or (today_date - datetime.date.fromisoformat(item["endDate"])).days <= 3
    ]
    ios_sample(data)
    classes = sorted(data["classes"], key=lambda c: (int(re.match(r"\d+", c)[0]), c))
    favicon = png_data_uri(icon(64))
    qrs = dict([("", qr_code(SITE_URL))] + [(c, qr_code(SITE_URL + "#" + slug(c).lower())) for c in classes])
    # те же QR-коды в приложение Android («Поделиться с классом»): кодировщик QR в APK не нужен
    os.makedirs(os.path.join(ROOT, "app", "src", "main", "assets"), exist_ok=True)
    with open(os.path.join(ROOT, "app", "src", "main", "assets", "qr.json"), "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(qrs, ensure_ascii=False, separators=(",", ":")) + "\n")
    startup = splash_images()
    jsonld = js_literal({
        "@context": "https://schema.org", "@type": "WebApplication", "name": "Звонок", "url": SITE_URL, "inLanguage": "ru",
        "description": "Расписание лицея №165: сколько до звонка, какой урок следующий и в каком кабинете.",
        "applicationCategory": "EducationalApplication", "operatingSystem": "Android, iOS, Web", "isAccessibleForFree": True,
        "offers": {"@type": "Offer", "price": "0", "priceCurrency": "KZT"}})
    common = {"@FAVICON@": favicon, "@SITE_URL@": SITE_URL, "@APK_URL@": APK_URL, "@STARTUP@": startup, "@JSONLD@": jsonld}
    data_js = {"/*@DATA@*/null": js_literal(data), "/*@CHANGES@*/null": js_literal(changes), "/*@HUES@*/null": js_literal(subject_hues(data))}

    news = next(({"date": r["date"], "title": r["title"]} for r in changelog if {"site", "ios"} & set(r["platforms"])), None)
    show = read("site", "src", "show.html")
    get_cta = ('<a class="btn btn-primary btn-sm" href="android.html"><svg class="icon" aria-hidden="true"><use href="#i-play"/></svg>Гайд для Android</a>'
               '<a class="btn btn-quiet btn-sm" href="iphone.html#guide"><svg class="icon" aria-hidden="true"><use href="#i-play"/></svg>Видеогайд для iPhone</a>')
    common["@SHOW@"] = show.replace("@SHOW_CTA@", get_cta)
    index = page("index.html", ["tokens", "base", "components", "app", "show"], ["core", "embed", "schedule", "rules", "app", "now", "week", "rooms", "more", "plus", "olymp", "snake", "show", "start"],
                 dict(data_js, **{"/*@QRS@*/null": js_literal(qrs), "/*@NEWS@*/null": js_literal(news), "/*@LOG@*/[]": js_literal(changelog[:4]),
                                  "/*@OLYMPIADS@*/null": js_literal(olympiads), "@HEAD_JS@": HEAD_JS + HEAD_PC}),
                 connect="'self' https://codeforces.com " + PING_HOSTS, common=common)
    page("make.html", ["tokens", "base", "components", "pages", "make"], ["core", "schedule", "rules", "make"], data_js, common=common)
    about_cta = ('<a class="btn btn-primary" href="./#get" data-app><svg class="icon" aria-hidden="true"><use href="#i-download"/></svg>Поставить на телефон</a>'
                 '<a class="btn btn-quiet" href="./#now" data-app>Открыть расписание</a>')
    page("about.html", ["tokens", "base", "components", "pages", "about", "show"], ["core", "schedule", "rules", "pages", "about", "show"], data_js,
         connect="'self' " + PING_HOSTS, common=dict(common, **{"@SHOW@": show.replace("@SHOW_CTA@", about_cta)}))
    page("android.html", ["tokens", "base", "components", "pages", "show"], ["core", "pages", "show"], common=common)
    page("changelog.html", ["tokens", "base", "components", "pages"], ["core", "pages", "changelog"],
         {"/*@CHANGELOG@*/[]": js_literal(changelog)}, common=common)
    page("feedback.html", ["tokens", "base", "components", "pages"], ["core", "pages", "feedback"],
         {"/*@CLASSES@*/[]": js_literal(classes)}, connect="'self' https://api.web3forms.com https://formsubmit.co", common=common)
    versions = class_scripts(data, changelog, index)
    page("iphone.html", ["iphone_guide", "iphone"], ["core", "schedule", "rules", "iphone", "iphone_guide"],
         dict(data_js, **{"/*@IOSVER@*/null": js_literal(versions)}), common=common)
    page("poster.html", ["tokens", "poster"], ["poster"], {"/*@QRS@*/null": js_literal(qrs), "/*@CLASSES@*/[]": js_literal(classes)}, common=common)
    # панель объявлений: ходит только на api.github.com, в поиск не попадает
    page("panel.html", ["tokens", "base", "components", "pages"], ["core", "pages", "panel"], connect="'self' https://api.github.com", common=common)
    page("404.html", ["tokens", "base", "components", "pages"], ["core", "pages", "notfound"], {"/*@CLASSES@*/[]": js_literal(classes)}, common=common)

    icon(180).save(os.path.join(OUT, "apple-touch-icon.png"), optimize=True)
    icon(192).save(os.path.join(OUT, "icon-192.png"), optimize=True)
    icon(512).save(os.path.join(OUT, "icon-512.png"), optimize=True)
    icon(512, maskable=True).save(os.path.join(OUT, "icon-maskable.png"), optimize=True)
    write("manifest.webmanifest", json.dumps({
        "id": "./",
        "name": "Звонок. Расписание лицея №165",
        "short_name": "Звонок",
        "description": "Сколько до звонка, какой урок следующий и в каком кабинете. Все классы лицея №165, работает без интернета.",
        "lang": "ru",
        "dir": "ltr",
        "start_url": "./",
        "scope": "./",
        "display": "standalone",
        "orientation": "portrait",
        "background_color": "#0B0B0C",
        "theme_color": "#0B0B0C",
        "categories": ["education", "productivity", "utilities"],
        "icons": [
            {"src": "icon-192.png", "sizes": "192x192", "type": "image/png"},
            {"src": "icon-512.png", "sizes": "512x512", "type": "image/png"},
            {"src": "icon-maskable.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable"},
        ],
        "shortcuts": [
            {"name": "Неделя", "short_name": "Неделя", "url": "./#week", "icons": [{"src": "icon-192.png", "sizes": "192x192"}]},
            {"name": "Свободные кабинеты", "short_name": "Кабинеты", "url": "./#rooms", "icons": [{"src": "icon-192.png", "sizes": "192x192"}]},
        ],
    }, ensure_ascii=False, indent=2))

    os.makedirs(os.path.join(OUT, "cal"), exist_ok=True)
    for c in data["classes"]:
        with open(os.path.join(OUT, "cal", slug(c) + ".ics"), "w", encoding="utf-8", newline="") as f:
            f.write(calendar(data, changes, c))
    write("changes.json", json.dumps(changes, ensure_ascii=False, indent=1))
    write("olympiads.json", json.dumps(olympiads, ensure_ascii=False, indent=1))
    # расписание целиком: приложение и сайт узнают из него, что вышло новое расписание
    write("schedule.json", json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    write(".nojekyll", "")
    # счётчик скачиваний для «О проекте»: отдельный файл, чтобы его смена раз в сутки не обновляла весь сайт у людей
    s = stats() if os.environ.get("CI") or os.environ.get("ZV_STATS") else None
    if s:
        write("stats.json", json.dumps(s))
    # в GitHub Actions другие шрифты: картинку превью не перерисовываем, берём закоммиченную
    if not os.environ.get("CI") or not os.path.exists(os.path.join(OUT, "og.png")):
        og_image().save(os.path.join(OUT, "og.png"), optimize=True)

    # service worker: версия меняется вместе с любым файлом, поэтому обновление приходит само
    shell = ["./", "index.html", "about.html", "make.html", "iphone.html", "android.html", "changelog.html", "feedback.html", "poster.html",
             "manifest.webmanifest", "icon-192.png", "icon-512.png", "icon-maskable.png", "apple-touch-icon.png", "changes.json", "olympiads.json"]
    h = hashlib.sha256()
    for f in shell[1:] + ["schedule.json"]:
        with open(os.path.join(OUT, f), "rb") as fh:
            h.update(fh.read())
    sw = read("site", "src", "sw.js").replace("@VERSION@", h.hexdigest()[:10]).replace("/*@SHELL@*/[]", json.dumps(shell))
    write("sw.js", sw)
    print("site/index.html", len(index.encode()) // 1024, "KB")


if __name__ == "__main__":
    main()
