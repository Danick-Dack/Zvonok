"""Тесты сайта в безголовом Chrome.  python tools/test_site.py"""
import html
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
chrome = os.environ.get("CHROME") or next(
    (p for p in [shutil.which("google-chrome"), shutil.which("chromium"), shutil.which("chromium-browser"),
                 r"C:\Program Files\Google\Chrome\Application\chrome.exe"] if p and os.path.exists(p)), None)
if not chrome:
    sys.exit("Chrome не найден (переменная CHROME)")

# копии страниц без CSP: сами страницы запрещают eval, а тестам нужен доступ к их переменным
copies = []
for src, dst in (("index.html", "_site_under_test.html"), ("changelog.html", "_changelog_under_test.html"),
                 ("feedback.html", "_feedback_under_test.html"), ("make.html", "_make_under_test.html"),
                 ("iphone.html", "_iphone_under_test.html"), ("about.html", "_about_under_test.html")):
    page = open(os.path.join(ROOT, "site", src), encoding="utf-8").read()
    page = re.sub(r'<meta http-equiv="Content-Security-Policy"[^>]*>', "", page)
    copies.append(os.path.join(ROOT, "tools", dst))
    open(copies[-1], "w", encoding="utf-8").write(page)
# Подпись новых версий скрипта iPhone. Тестовый ключ (только для тестов) подписывает «новую версию» 7Б,
# виджет в браузере проверяет её своей реализацией Ed25519 и SHA-512; заодно сверяем подписи на сайте.
import base64, hashlib, json
sys.path.insert(0, os.path.join(ROOT, "tools"))
import ed25519
TEST_SEED = bytes(range(32))
b64 = lambda b: base64.b64encode(b).decode()
served = open(os.path.join(ROOT, "site", "ios", "7B.js"), encoding="utf-8").read()
built = int(re.search(r"^const BUILT = (\d+);", served, re.M)[1])
def version(name, when):  # та же версия 7Б, но с другим номером и временем сборки
    code = re.sub(r'const VERSION = "[^"]*"', 'const VERSION = "%s"' % name, served, count=1)
    return re.sub(r"^const BUILT = \d+;", "const BUILT = %d;" % when, code, count=1, flags=re.M)
fresh, old = version("new0000001", built + 1), version("old0000001", built - 1)
fixture = {"pub": b64(ed25519.public_key(TEST_SEED)), "code": fresh, "sig": b64(ed25519.sign(TEST_SEED, fresh.encode("utf-8"))),
           "sha": {"abc": hashlib.sha512(b"abc").hexdigest(), "code": hashlib.sha512(fresh.encode("utf-8")).hexdigest()},
           "old": {"code": old, "sig": b64(ed25519.sign(TEST_SEED, old.encode("utf-8")))}}
versions = json.load(open(os.path.join(ROOT, "site", "ios", "versions.json"), encoding="utf-8"))
real_key = re.search(r'const UPDATE_KEY = "([^"]*)"', served)[1]
if real_key:  # сайт собран с ключом: подпись 7Б на сайте должна сходиться с ключом внутри скрипта
    fixture["real"] = {"pub": real_key, "code": served, "sig": versions.get("sig", {}).get("7B", "")}
    if not ed25519.verify(base64.b64decode(real_key), served.encode("utf-8"), base64.b64decode(fixture["real"]["sig"] or "AA==")):
        sys.exit("подпись ios/7B.js на сайте не сходится с открытым ключом в скрипте")
try:  # сверка с libsodium, если она есть (на GitHub Actions её нет, там хватает проверки в браузере)
    import nacl.signing
    if nacl.signing.SigningKey(TEST_SEED).sign(fresh.encode("utf-8")).signature != base64.b64decode(fixture["sig"]):
        sys.exit("Ed25519 в tools/ed25519.py разошёлся с libsodium")
except ImportError:
    pass
# скрипты всех классов, как их отдаёт сайт (страница iPhone теперь берёт их с сайта, а не собирает сама)
LAT = {"А": "A", "Б": "B", "В": "V", "Г": "G"}
classes = json.load(open(os.path.join(ROOT, "data", "schedule.json"), encoding="utf-8"))["classes"]
fixture["scripts"] = {c: open(os.path.join(ROOT, "site", "ios", re.sub("[АБВГ]", lambda m: LAT[m.group(0)], c) + ".js"), encoding="utf-8").read()
                      for c in classes}
copies.append(os.path.join(ROOT, "tools", "_sig_fixture.js"))
open(copies[-1], "w", encoding="utf-8").write("window.SIG_FIXTURE = " + json.dumps(fixture, ensure_ascii=False) + ";\n")

url = "file:///" + os.path.join(ROOT, "tools", "site_test.html").replace("\\", "/").lstrip("/")
try:
    dom = subprocess.run([chrome, "--headless=new", "--no-sandbox", "--disable-gpu", "--allow-file-access-from-files",
                          "--virtual-time-budget=90000", "--dump-dom", url],
                         capture_output=True, text=True, encoding="utf-8", timeout=180).stdout
finally:
    for c in copies:
        os.remove(c)
m = re.search(r'<pre id="out">(.*?)</pre>', dom, re.S)
lines = html.unescape(m[1]).splitlines() if m else []
print("\n".join(lines) or "нет результатов")
failed = [l for l in lines if l.startswith("FAIL")]
print(f"\n{len(lines) - len(failed) - 1} прошло, {len(failed)} упало")
sys.exit(1 if failed or "DONE" not in lines else 0)
