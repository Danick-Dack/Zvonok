"""Тесты сборщика новостей (tools/update_news.py) на вредном и кривом HTML.  python tools/test_news.py

Превью канала Telegram для нас чужой текст: что бы там ни оказалось, в news.json должен попасть только
обычный текст, номер поста, дата и число фото. Фото на сайт не копируются.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import update_news as n

fails = []


def ok(cond, name):
    print(("PASS " if cond else "FAIL ") + name)
    if not cond:
        fails.append(name)


def post(pid, body, extra="", date="2026-11-06T09:45:54+00:00"):
    return ('<div class="tgme_widget_message_wrap js-widget_message_wrap"><div class="tgme_widget_message" data-post="sl165_official/%s">%s%s'
            '<a class="tgme_widget_message_date" href="#"><time datetime="%s" class="time">09:45</time></a></div></div>' % (pid, extra, body, date))


def text(inner):
    return '<div class="tgme_widget_message_text js-message_text" dir="auto">%s</div>' % inner


PHOTO = '<a class="tgme_widget_message_photo_wrap x" style="width:800px;background-image:url(\'%s\')"></a>'

page = "<html><body>" + "".join([
    post(1, text('Привет<br/>лицей <b>жирный</b> <i class="emoji" style="background-image:url(\'//x/e.png\')"><b>💚</b></i>')),
    post(2, text('<script>alert(1)</script><img src=x onerror=alert(2)> &lt;b&gt;не тег&lt;/b&gt; &amp; <a href="javascript:alert(3)">ссылка</a>')),
    post(3, text('внешний <div class="in">вложенный <div>глубже</div></div> хвост') + '<div class="tgme_widget_message_footer">подвал</div>'),
    post(4, text("с фото"), PHOTO % "https://cdn4.telesco.pe/file/abc.jpg" + PHOTO % "https://cdn4.telesco.pe/file/def.jpg"),
    post(5, "", '<i class="tgme_widget_message_video_thumb" style="background-image:url(\'https://cdn1.telesco.pe/file/v.jpg\')"></i>'),
    post(6, ""),                                                    # ни текста, ни картинки: не новость
    post("abc", text("кривой номер")),                              # не число: пропускаем
    post(7, text("x" * 9000 + "\x00\x07 конец")),              # длинный и с управляющими символами
    post(8, text("много\n\n\n\n\nпустых   строк\t\tи пробелов")),
    '<div class="tgme_widget_message_wrap"><div data-post="other_channel/9">' + text("чужой канал") + "</div></div>",
    post(10, text("без даты")).replace("tgme_widget_message_date", "nodate"),
]) + "</body></html>"

p = {x["id"]: x for x in n.parse(page)}
ok(sorted(p) == [1, 2, 3, 4, 5, 7, 8], "в ленту попали только настоящие посты своего канала: %s" % sorted(p))
ok(p[1]["text"] == "Привет\nлицей жирный 💚", "перенос строки и эмодзи сохраняются, теги уходят: %r" % p[1]["text"])
ok("<" not in p[2]["text"].replace("<b>не тег</b>", "") and "alert(1)" in p[2]["text"] and "<b>не тег</b>" in p[2]["text"] and "javascript:" not in p[2]["text"],
   "теги script и img выброшены, экранированный текст остался текстом, адрес javascript: не попал: %r" % p[2]["text"])
ok(p[3]["text"] == "внешний вложенный глубже хвост", "вложенные блоки читаются целиком, подвал поста в текст не попадает: %r" % p[3]["text"])
ok(p[4]["src"].endswith("abc.jpg") and p[4]["more"] == 1 and "video" not in p[4], "альбом: первая картинка и «ещё 1 фото»")
ok(p[5]["src"].endswith("v.jpg") and p[5].get("video") is True and p[5]["text"] == "", "видео без подписи: обложка и пометка")
ok(len(p[7]["text"]) == n.MAX_TEXT and "\x00" not in p[7]["text"] and " " not in p[7]["text"], "длинный текст обрезан, управляющих символов нет")
ok(p[8]["text"] == "много\n\nпустых строк и пробелов", "лишние пустые строки и пробелы схлопнуты: %r" % p[8]["text"])
ok(all(set(x) <= {"id", "date", "text", "src", "more", "video"} for x in p.values()), "в записи только известные поля")

# свои объявления из панели и скрытые посты: data/news_admin.json проверяется как чужой текст
import json, tempfile
tmp = tempfile.mkdtemp()
os.makedirs(os.path.join(tmp, "data")); os.makedirs(os.path.join(tmp, "site"))
B = n.OWN_BASE
with open(os.path.join(tmp, "data", "news_admin.json"), "w", encoding="utf-8") as f:
    json.dump({"posts": [
        {"id": B + 5, "date": "2026-11-07T08:00:00Z", "text": "<b>Завтра</b> линейка\n\n\n\nв 8:00 \x00"},
        {"id": 77, "date": "2026-11-07T08:00:00Z", "text": "номер из чужого диапазона"},
        {"id": B + 6, "date": "вчера", "text": "кривая дата"},
        {"id": B + 7, "date": "2026-11-07T09:00:00Z", "text": 5},
        {"id": B + 8, "date": "2026-11-07T10:00:00Z", "text": "скрыт панелью"},
        "не объект"], "hidden": [4, B + 8, "x"]}, f, ensure_ascii=False)
real = (n.fetch, n.ROOT, n.OUT)
n.ROOT, n.OUT = tmp, os.path.join(tmp, "site")
old_site = {"items": [{"id": 30, "date": "2026-11-01T09:00:00+00:00", "text": "старый", "photo": "news/3.jpg", "w": 1, "h": 1, "more": 2},
                      {"id": B + 1, "date": "2026-11-01T09:00:00+00:00", "text": "свой пост, которого уже нет в файле"}]}
n.fetch = lambda url, limit: (page.encode() if "t.me" in url else json.dumps(old_site).encode())
try:
    n.main()
    with open(os.path.join(tmp, "site", "news.json"), encoding="utf-8") as f:
        out = json.load(f)["items"]
finally:
    n.fetch, n.ROOT, n.OUT = real
ids = [x["id"] for x in out]
own = [x for x in out if x.get("own")]
ok([x["id"] for x in own] == [B + 5] and own[0]["text"] == "<b>Завтра</b> линейка\n\nв 8:00",
   "в ленту попал только правильный свой пост, его текст остался текстом: %r" % own)
ok(4 not in ids and B + 8 not in ids and B + 1 not in ids, "скрытые посты и удалённые свои объявления пропали: %s" % ids)
ok(not any("photo" in x or "src" in x or "w" in x for x in out), "фото канала на сайт не копируются")
ok(next(x for x in out if x["id"] == 30)["pics"] == 3 and next(x for x in out if x["id"] == 5).get("pics") == 1,
   "у поста осталось только число фото для ссылки на Telegram")
ok(ids == sorted(ids, key=lambda i: n.when(next(x for x in out if x["id"] == i)), reverse=True), "лента по времени, свежие сверху")
ok(not os.path.exists(os.path.join(tmp, "site", "news")), "папки с картинками больше нет")

print("\n%d упало" % len(fails) if fails else "\nвсё прошло")
sys.exit(1 if fails else 0)
