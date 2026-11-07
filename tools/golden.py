"""Эталонные ответы правил дня для приложения Android.

  py tools/golden.py           пересобрать app/src/test/resources/golden.json из site/src/js/rules.js
  py tools/golden.py --check   проверить, что файл совпадает с текущими правилами (это делает CI)

Правила дня одни на сайт и виджет iPhone (rules.js), а Android считает их на Java (Schedule.java).
Скрипт прогоняет rules.js в безголовом Chrome на заготовленных данных: все классы, урок, перемена, окно,
до и после уроков, выходной, каникулы, праздник, замены, сокращённые звонки, полночь и перевод часов.
Юнит-тесты Android (GoldenTest) сверяют с этим файлом каждый ответ.
"""
import ast
import asyncio
import base64
import json
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "test", "resources", "golden.json")

# Замены на неделю 9–25 ноября 2026: всё, что умеет changes.json, и то, что в нём может оказаться по ошибке.
CHANGES = {"updated": "2026-11-08", "days": {
    "2026-11-11": {"note": "Замены у 7Б и 8А", "classes": {
        "7Б": {"2": {"subject": "fiz", "room": "48"}, "5": "нет урока", "9": {"subject": "Шахматы", "room": 12}},
        "8А": {"1": None, "3": {"room": "спортзал"}, "4": "Физика", "x": {"subject": "geo"}, "3a": None},
        "9А": "нет"}},
    "2026-11-12": {"note": "Сокращённые уроки", "bells": [["8:00", "8:30"], ["8:35", "9:05"], ["9:15", "9:45"], ["9:55", "10:25"],
                                                        ["10:35", "11:05"], ["11:10", "11:40"], ["11:45", "12:15"], ["12:20", "12:50"]]},
    "2026-11-13": {"off": "Актированный день"},
    "2026-11-17": {"bells": [["8:00", "7:00"], None, ["9.45", "10.30"], "8:00", ["8:5", "9:00"]], "classes": {"9Б": {"01": {"subject": "him"}}}},
    "2026-11-18": {"off": True},
    "2026-11-19": {"off": "  "},
    "2026-11-20": {"bells": []},
    "2026-11-24": {"classes": {"10А": {"2": {"subject": 5, "room": ["x"]}, "3": {"subject": "Робототехника"}, "4": {"room": 7}}}},
    "bad-date": {"off": "x"},
    "2026-11-25": "мусор",
}}
# своё расписание с ошибками, как его может прислать друг: пустые предметы, кривые звонки, лишние пробелы
CUSTOM = {"v": 1, "bells": [["8:30", "9:15"], ["9:20", "10:05"], ["bad", ""], ["10:20", "10:00"], ["11:00", "11:45"], None],
          "days": [[["Алгебра", "24/35"], ["", "1"], ["Физика", "48"], ["Химия", "40"], ["  Шахматы ", "с/з"], ["Лишний", "9"]],
                   [], [["Рисование", ""]], [], [], [["Суббота-урок", "5"]]]}

MON = ["2026-11-09T06:00:00", "2026-11-09T07:30:00", "2026-11-09T08:00:00", "2026-11-09T08:44:59", "2026-11-09T08:45:00",
       "2026-11-09T10:40:00", "2026-11-09T12:45:00", "2026-11-09T14:35:00", "2026-11-09T16:30:00", "2026-11-09T23:59:59",
       "2026-11-10T00:00:00"]
CHANGED = ["2026-11-11T09:00:00", "2026-11-11T12:00:00", "2026-11-12T08:20:00", "2026-11-12T12:30:00",
           "2026-11-17T09:00:00", "2026-11-24T09:00:00"]
SPECIAL = ["2026-11-13T10:00:00", "2026-11-14T12:00:00", "2026-11-15T20:00:00", "2026-11-18T10:00:00", "2026-11-19T10:00:00",
           "2026-11-20T10:00:00", "2026-12-15T16:00:00", "2026-12-16T10:00:00", "2026-12-25T16:00:00", "2026-12-26T12:00:00",
           "2026-12-28T10:00:00", "2027-01-11T07:59:00", "2027-03-05T12:00:00", "2027-03-19T10:00:00", "2027-03-19T16:00:00",
           "2027-05-25T09:00:00", "2027-08-31T12:00:00", "2027-11-01T10:00:00"]
FEW = ["7Б", "8А", "10А", "11Г"]
CUSTOM_TIMES = ["2026-11-09T08:00:00", "2026-11-09T11:30:00", "2026-11-09T12:00:00", "2026-11-10T10:00:00",
                "2026-11-14T10:00:00", "2026-12-16T07:45:00"]
ZONES = [
    ("Asia/Almaty", [("*", MON + CHANGED), (FEW, SPECIAL), (["custom"], CUSTOM_TIMES)], True),
    # Египет переводит часы в полночь на пятницу (учебный день) и обратно в конце четверга: звонки должны
    # остаться по стенным часам. 00:30 30 апреля не бывает, такие часы сдвигаются вперёд.
    ("Africa/Cairo", [(["7Б", "8А"], ["2027-04-30T00:30:00", "2027-04-30T07:30:00", "2027-04-30T08:10:00",
                                      "2026-10-29T23:30:00", "2026-10-30T07:30:00"])], False),
    ("America/New_York", [(["7Б"], ["2026-11-01T01:30:00", "2026-11-02T07:15:00"])], False),
]
TABLES = {"dates": ["2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13", "2026-11-14", "2026-11-17",
                    "2026-11-18", "2026-11-19", "2026-11-20", "2026-11-24", "2026-12-16"],
          "roomDates": ["2026-11-09", "2026-11-11", "2026-11-12", "2026-11-13"],
          "roomNow": [["24", 490], ["48", 580], ["спортзал", 600], ["12", 900], ["46", 420], ["56", 760]],
          "extraSubjects": ["Шахматы", "Робототехника", "алгебра и начала анализа", "Физ-ра", "", "Art"]}


def chrome_path():
    return os.environ.get("CHROME") or next(
        (p for p in [shutil.which("google-chrome"), shutil.which("chromium"), shutil.which("chromium-browser"),
                     r"C:\Program Files\Google\Chrome\Application\chrome.exe"] if p and os.path.exists(p)), None)


class Page:
    """Минимальный клиент Chrome DevTools: открыть пустую вкладку, задать часовой пояс, выполнить код."""

    async def start(self):
        self.dir = tempfile.mkdtemp(prefix="zvgold")
        self.proc = subprocess.Popen([chrome_path(), "--headless=new", "--no-sandbox", "--remote-debugging-port=0",
                                      "--user-data-dir=" + self.dir, "--no-first-run", "about:blank"],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        port_file = os.path.join(self.dir, "DevToolsActivePort")
        for _ in range(300):
            if os.path.exists(port_file) and open(port_file).read().strip():
                break
            await asyncio.sleep(0.05)
        port = int(open(port_file).read().split()[0])
        tabs = json.load(urllib.request.urlopen("http://127.0.0.1:%d/json/list" % port, timeout=5))
        path = next(t for t in tabs if t["type"] == "page")["webSocketDebuggerUrl"].split(str(port), 1)[1]
        self.r, self.w = await asyncio.open_connection("127.0.0.1", port, limit=2 ** 27)
        key = base64.b64encode(os.urandom(16)).decode()
        self.w.write(("GET %s HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                      "Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\n\r\n" % (path, key)).encode())
        await self.w.drain()
        await self.r.readuntil(b"\r\n\r\n")
        self.id = 0
        return self

    async def send(self, method, **params):
        self.id += 1
        data = json.dumps({"id": self.id, "method": method, "params": params}).encode()
        n, mask = len(data), os.urandom(4)
        head = bytes([0x81]) + (bytes([0x80 | n]) if n < 126 else bytes([0xFE]) + struct.pack(">H", n) if n < 65536
                                else bytes([0xFF]) + struct.pack(">Q", n))
        self.w.write(head + mask + bytes(b ^ mask[k % 4] for k, b in enumerate(data)))
        await self.w.drain()
        buf = b""
        while True:
            h = await self.r.readexactly(2)
            size = h[1] & 0x7F
            if size == 126:
                size = struct.unpack(">H", await self.r.readexactly(2))[0]
            elif size == 127:
                size = struct.unpack(">Q", await self.r.readexactly(8))[0]
            buf += await self.r.readexactly(size)
            if not h[0] & 0x80:
                continue
            msg, buf = json.loads(buf.decode("utf-8")), b""
            if msg.get("id") == self.id:
                if "error" in msg:
                    raise RuntimeError(msg["error"])
                return msg.get("result", {})

    async def js(self, code):
        r = await self.send("Runtime.evaluate", expression=code, returnByValue=True)
        if "exceptionDetails" in r:
            d = r["exceptionDetails"]
            raise RuntimeError(d.get("exception", {}).get("description", d.get("text")))
        return r["result"].get("value")

    def close(self):
        self.proc.kill()
        self.proc.wait()
        shutil.rmtree(self.dir, ignore_errors=True)


def read(*p):
    with open(os.path.join(ROOT, *p), encoding="utf-8") as f:
        return f.read()


def site_table(name):
    """Таблица из tools/build_site.py, без запуска сборки."""
    tree = ast.parse(read("tools", "build_site.py"))
    return next(ast.literal_eval(n.value) for n in tree.body
                if isinstance(n, ast.Assign) and getattr(n.targets[0], "id", "") == name)


def subject_hues(data):
    """Тот же оттенок предмета, что вписывает сборка сайта (SUBJECT_HUE в tools/build_site.py)."""
    table = site_table("SUBJECT_HUE")
    return {name: table.get(code, "gray") for code, name in data["subjects"].items()}


async def generate():
    data = json.loads(read("data", "schedule.json"))
    j = lambda o: json.dumps(o, ensure_ascii=False)
    code = "\n".join(read("site", "src", "js", f + ".js") for f in ("core", "schedule", "rules"))
    code = (code.replace("/*@DATA@*/null", j(data)).replace("/*@CHANGES@*/null", j(CHANGES))
            .replace("/*@HUES@*/null", j(subject_hues(data))) + "\n" + read("tools", "golden.js"))
    page = await Page().start()
    try:
        await page.js(code)
        out = {"about": "Создано tools/golden.py из site/src/js/rules.js. Руками не править.",
               "schedule": data, "changes": CHANGES, "custom": CUSTOM,
               "palette": site_table("HUES"), "cases": []}   # palette: сами цвета оттенков, Hues.C в Android сверяется с ними
        for tz, plan, tables in ZONES:
            await page.send("Emulation.setTimezoneOverride", timezoneId=tz)
            cfg = dict(TABLES, tz=tz, plan=plan, custom=CUSTOM, tables=tables)
            res = await page.js("JSON.stringify(goldenRun(%s))" % j(cfg))
            res = json.loads(res)
            out["cases"] += res.pop("cases")
            out.update(res)
        return out
    finally:
        page.close()


def dump(g):
    """Одна строка на случай: так в истории git видно, какой ответ поменялся."""
    head = {k: v for k, v in g.items() if k != "cases"}
    body = json.dumps(head, ensure_ascii=False, separators=(",", ":"))
    cases = ",\n".join(json.dumps(c, ensure_ascii=False, separators=(",", ":")) for c in g["cases"])
    return body[:-1] + ',\n"cases":[\n' + cases + "\n]}\n"


def main():
    if not chrome_path():
        sys.exit("Chrome не найден (переменная CHROME)")
    text = dump(asyncio.run(generate()))
    if "--check" in sys.argv:
        old = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
        if old != text:
            sys.exit("app/src/test/resources/golden.json устарел: правила дня поменялись, запусти py tools/golden.py")
        print("Эталон Android совпадает с rules.js: %d случаев" % len(json.loads(text)["cases"]))
        return
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("golden.json: %d случаев, %d КБ" % (len(json.loads(text)["cases"]), len(text.encode()) // 1024))


if __name__ == "__main__":
    main()
