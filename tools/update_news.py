"""Новости лицея для вкладки «Новости»: публичное превью канала Telegram и свои объявления → site/news.json.

  python tools/update_news.py

Запускает GitHub Actions раз в час (pages.yml), в репозиторий ничего не коммитит. Сайт и приложения читают только
news.json со своего сайта: в Telegram с телефонов никто не ходит. Из поста берём только текст, дату и число фото:
сами фото остаются в Telegram (на них ученики), у поста есть ссылка на оригинал. Свои объявления и список скрытых постов
лежат в data/news_admin.json (их правит панель «Звонка»). Если Telegram не ответил, остаются прошлые новости с сайта.
"""
import datetime
import html
import json
import os
import re
import sys
import urllib.request
from html.parser import HTMLParser

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "site")
CHANNEL = "sl165_official"
SITE_URL = "https://danick-dack.github.io/Zvonok/"
KEEP = 30               # сколько последних постов держим
MAX_TEXT = 4000
UA = {"User-Agent": "Mozilla/5.0 (compatible; zvonok-news)"}


def fetch(url, limit):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
        data = r.read(limit + 1)
    if len(data) > limit:
        raise ValueError("слишком большой ответ: " + url)
    return data


class Text(HTMLParser):
    """Текст поста без разметки: <br> это перенос строки, остальные теги выбрасываем."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.out = []

    def handle_starttag(self, tag, attrs):
        if tag == "br":
            self.out.append("\n")

    def handle_data(self, data):
        self.out.append(data)


def inner_div(chunk, start):
    """HTML внутри <div>, который открывается в позиции start (с учётом вложенных div)."""
    depth, body = 0, chunk.index(">", start) + 1
    for m in re.finditer(r"<(/?)div\b", chunk[start:]):
        depth += -1 if m.group(1) else 1
        if not depth:
            return chunk[body:start + m.start()]
    return chunk[body:]


def plain(fragment):
    p = Text()
    p.feed(fragment)
    text = "".join(p.out).replace("\r", "")
    text = re.sub(r"[\x00-\x09\x0b-\x1f\x7f  ]", " ", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r" ?\n ?", "\n", text)
    return re.sub(r"\n{3,}", "\n\n", text).strip()[:MAX_TEXT]


def parse(page):
    posts = []
    for chunk in page.split('<div class="tgme_widget_message_wrap')[1:]:
        m = re.search(r'data-post="%s/(\d+)"' % CHANNEL, chunk)
        d = re.search(r'<a class="tgme_widget_message_date"[^>]*><time datetime="([^"]+)"', chunk)
        if not m or not d:
            continue
        t = re.search(r'<div class="tgme_widget_message_text js-message_text"', chunk)
        photos = re.findall(r"tgme_widget_message_photo_wrap[^>]*background-image:url\('([^']+)'\)", chunk)
        thumbs = re.findall(r"tgme_widget_message_video_thumb\"[^>]*background-image:url\('([^']+)'\)", chunk)
        post = {"id": int(m.group(1)), "date": d.group(1), "text": plain(inner_div(chunk, t.start())) if t else ""}
        pics = [html.unescape(u) for u in photos + thumbs]
        if pics:
            post["src"] = pics[0]
            if len(pics) > 1:
                post["more"] = len(pics) - 1
        if thumbs and not photos:
            post["video"] = True
        if post["text"] or pics:
            posts.append(post)
    return posts


OWN_BASE = 1_000_000_000  # номера постов из панели «Звонка»: от миллиарда, с номерами канала не пересекаются


def admin():
    """data/news_admin.json: свои объявления из панели «Звонка» и номера постов канала, которые убрали с сайта.
    Файл правят через GitHub, поэтому каждое поле проверяем, как чужой текст: в ленту попадает только простой текст."""
    try:
        with open(os.path.join(ROOT, "data", "news_admin.json"), encoding="utf-8-sig") as f:
            j = json.load(f)
    except FileNotFoundError:
        return [], set()
    own = []
    for p in j.get("posts", [])[:KEEP]:
        if not (isinstance(p, dict) and isinstance(p.get("id"), int) and p["id"] > OWN_BASE and isinstance(p.get("text"), str)
                and isinstance(p.get("date"), str) and re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?(Z|[+-]\d\d:\d\d)", p["date"])):
            continue
        text = plain(html.escape(p["text"]).replace("\n", "<br>"))
        if text:
            own.append({"id": p["id"], "date": p["date"], "text": text, "own": True})
    hidden = {x for x in j.get("hidden", []) if isinstance(x, int)}
    return own, hidden


def when(p):
    """Порядок ленты: по времени поста, в одну секунду — по номеру."""
    return datetime.datetime.fromisoformat(p["date"].replace("Z", "+00:00")).timestamp(), p["id"]


def published():
    """Посты канала, которые уже лежат на сайте: из них берём те, которых в превью канала уже не видно."""
    try:
        old = json.loads(fetch(SITE_URL + "news.json", 1 << 20))
        return [p for p in old.get("items", []) if isinstance(p, dict) and isinstance(p.get("id"), int) and 0 < p["id"] < OWN_BASE]
    except Exception:
        return []


def main():
    old = published()
    own, hidden = admin()
    try:
        fresh = parse(fetch("https://t.me/s/" + CHANNEL, 4 << 20).decode("utf-8", "replace"))
    except Exception as e:
        print("Telegram не ответил (%s): остаются прошлые новости" % e)
        fresh = []
    if not fresh and not old and not own:
        print("новостей нет: news.json не тронут")
        return 0
    seen = {p["id"] for p in fresh}
    items = []
    for p in fresh + [p for p in old if p["id"] not in seen]:
        if p["id"] in hidden:
            continue
        # Фото канала на сайт не копируем: на них ученики, а чужие снимки без согласия выкладывать нельзя.
        # Остаётся только число фото и ссылка на пост в Telegram.
        n = 1 + p.get("more", 0) if p.pop("src", None) or p.get("photo") else p.get("pics", 0)
        for k in ("photo", "w", "h", "more"):
            p.pop(k, None)
        if n:
            p["pics"] = n
        if p.get("text") or n:
            items.append(p)
    own = [p for p in own if p["id"] not in hidden]
    items = sorted(items + own, key=when, reverse=True)[:KEEP]
    with open(os.path.join(OUT, "news.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"channel": CHANNEL, "items": items}, f, ensure_ascii=False, separators=(",", ":"))
    print("news.json: %d постов, свежих из Telegram %d, своих %d" % (len(items), len(fresh), len(own)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
