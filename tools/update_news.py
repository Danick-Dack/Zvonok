"""Новости лицея для вкладки «Новости»: публичное превью канала Telegram и свои объявления → site/news.json и site/news/<номер>.jpg.

  python tools/update_news.py

Запускает GitHub Actions раз в час (pages.yml), в репозиторий ничего не коммитит. Сайт и приложения читают только
news.json со своего сайта: в Telegram с телефонов никто не ходит. Из поста берём текст и одну картинку. Картинку
пересохраняем через Pillow: в файле остаются только пиксели, без метаданных и без того, что могло быть спрятано в оригинале.
Свои объявления (с фото из data/news/) и скрытые посты лежат в data/news.json. Если Telegram не ответил, остаются прошлые новости с сайта.
"""
import datetime
import html
import io
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from html.parser import HTMLParser

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "site")
CHANNEL = "sl165_official"
SITE_URL = "https://danick-dack.github.io/Zvonok/"
KEEP = 30               # сколько последних постов держим
MAX_TEXT = 4000
MAX_IMAGE = 8 << 20     # больше 8 МБ картинку не качаем
IMAGE_HOSTS = (".telesco.pe", ".telegram-cdn.org", ".cdn-telegram.org")
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


OWN_BASE = 1_000_000_000  # номера своих объявлений: от миллиарда, с номерами канала не пересекаются


def save_image(data, pid):
    """Картинка поста: пересохраняем в JPEG не шире 960 px, в файле остаются только пиксели. → (ширина, высота) или None."""
    from PIL import Image
    try:
        im = Image.open(io.BytesIO(data))
        im = im.convert("RGB")
        im.thumbnail((960, 1280))
        os.makedirs(os.path.join(OUT, "news"), exist_ok=True)
        im.save(os.path.join(OUT, "news", "%d.jpg" % pid), "JPEG", quality=78, optimize=True, progressive=True)
        return im.size
    except Exception as e:  # битая картинка: пост останется без неё
        print("картинка поста %d не сохранилась: %s" % (pid, e))
        return None


def tg_image(url, pid):
    """Картинка канала: только https с серверов Telegram."""
    u = urllib.parse.urlsplit(url)
    if u.scheme != "https" or not u.hostname or not u.hostname.endswith(IMAGE_HOSTS):
        return None
    try:
        return save_image(fetch(url, MAX_IMAGE), pid)
    except Exception as e:
        print("картинка поста %d не скачалась: %s" % (pid, e))
        return None


def admin():
    """data/news.json: свои объявления (фото к ним в data/news/<номер>.jpg) и номера постов канала, которые убрали с сайта.
    Файл правят через GitHub, поэтому каждое поле проверяем, как чужой текст: в ленту попадает только простой текст."""
    try:
        with open(os.path.join(ROOT, "data", "news.json"), encoding="utf-8-sig") as f:
            j = json.load(f)
    except FileNotFoundError:
        return [], set()
    own = []
    for p in j.get("posts", [])[:KEEP]:
        if not (isinstance(p, dict) and isinstance(p.get("id"), int) and p["id"] > OWN_BASE and isinstance(p.get("text"), str)
                and isinstance(p.get("date"), str) and re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?(Z|[+-]\d\d:\d\d)", p["date"])):
            continue
        post = {"id": p["id"], "date": p["date"], "text": plain(html.escape(p["text"]).replace("\n", "<br>")), "own": True}
        if p.get("photo") == "%d.jpg" % p["id"]:
            try:
                with open(os.path.join(ROOT, "data", "news", p["photo"]), "rb") as f:
                    data = f.read(MAX_IMAGE + 1)
                size = len(data) <= MAX_IMAGE and save_image(data, p["id"])
                if size:
                    post.update(photo="news/%d.jpg" % p["id"], w=size[0], h=size[1])
            except OSError as e:
                print("фото объявления %d не найдено: %s" % (p["id"], e))
        if post["text"] or post.get("photo"):
            own.append(post)
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
        src = p.pop("src", None)
        p.pop("pics", None)
        if src:
            size = tg_image(src, p["id"])
            if size:
                p.update(photo="news/%d.jpg" % p["id"], w=size[0], h=size[1])
        elif p.get("photo"):  # старый пост: его картинка уже лежит на сайте
            try:
                data = fetch(SITE_URL + "news/%d.jpg" % p["id"], MAX_IMAGE)
                os.makedirs(os.path.join(OUT, "news"), exist_ok=True)
                with open(os.path.join(OUT, "news", "%d.jpg" % p["id"]), "wb") as f:
                    f.write(data)
                p["photo"] = "news/%d.jpg" % p["id"]
            except Exception:
                for k in ("photo", "w", "h"):
                    p.pop(k, None)
        if not p.get("photo"):
            p.pop("more", None)
        if p.get("text") or p.get("photo"):
            items.append(p)
    own = [p for p in own if p["id"] not in hidden]
    items = sorted(items + own, key=when, reverse=True)[:KEEP]
    with open(os.path.join(OUT, "news.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"channel": CHANNEL, "items": items}, f, ensure_ascii=False, separators=(",", ":"))
    print("news.json: %d постов, свежих из Telegram %d, своих %d" % (len(items), len(fresh), len(own)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
