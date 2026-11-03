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

# копия сайта без CSP: сам сайт запрещает eval, а тестам нужен доступ к его переменным
copies = []
for src, dst in (("index.html", "_site_under_test.html"), ("changelog.html", "_changelog_under_test.html"),
                 ("feedback.html", "_feedback_under_test.html")):
    page = open(os.path.join(ROOT, "site", src), encoding="utf-8").read()
    page = re.sub(r'<meta http-equiv="Content-Security-Policy"[^>]*>', "", page)
    copies.append(os.path.join(ROOT, "tools", dst))
    open(copies[-1], "w", encoding="utf-8").write(page)
url = "file:///" + os.path.join(ROOT, "tools", "site_test.html").replace("\\", "/").lstrip("/")
dom = subprocess.run([chrome, "--headless=new", "--no-sandbox", "--disable-gpu", "--allow-file-access-from-files",
                      "--virtual-time-budget=8000", "--dump-dom", url],
                     capture_output=True, text=True, encoding="utf-8", timeout=120).stdout
for c in copies:
    os.remove(c)
m = re.search(r'<pre id="out">(.*?)</pre>', dom, re.S)
lines = html.unescape(m[1]).splitlines() if m else []
print("\n".join(lines) or "нет результатов")
failed = [l for l in lines if l.startswith("FAIL")]
print(f"\n{len(lines) - len(failed) - 1} прошло, {len(failed)} упало")
sys.exit(1 if failed or "DONE" not in lines else 0)
