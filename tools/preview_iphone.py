"""
Предпросмотр виджетов iPhone (скрипт Scriptable) без iPhone: PNG через безголовый Chrome.

  py tools/preview_iphone.py                    # все листы для 7Б
  py tools/preview_iphone.py 10А lock home      # класс и листы: lock, home, large, mono, app
  py tools/preview_iphone.py 7Б home --param "ZV1;bg=0B1220;ac=60A5FA"
  py tools/preview_iphone.py 7Б lock --changes my_changes.json   # что скачает скрипт вместо changes.json

Сначала собери сайт: py tools/build_site.py (скрипт берётся из site/iphone.html через scriptFor()).
Картинки: .tmp/team/iphone_<лист>.png
"""
import os
import re
import shutil
import subprocess
import sys
import urllib.parse

from PIL import Image, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHROME = os.environ.get("CHROME") or next(
    (p for p in [shutil.which("google-chrome"), shutil.which("chromium"),
                 r"C:\Program Files\Google\Chrome\Application\chrome.exe"] if p and os.path.exists(p)), None)
SHEETS = ["lock", "home", "large", "mono", "app"]
SIZE = {"lock": (1300, 1300), "home": (1250, 1800), "large": (1650, 1500), "mono": (1250, 1800), "app": (1700, 950)}


def main():
    argv = sys.argv[1:]
    param = None
    if "--param" in argv:
        i = argv.index("--param")
        param = argv[i + 1]
        del argv[i:i + 2]
    changes = None
    if "--changes" in argv:
        i = argv.index("--changes")
        changes = argv[i + 1]
        del argv[i:i + 2]
    cls = next((a for a in argv if a not in SHEETS), "7Б")
    sheets = [a for a in argv if a in SHEETS] or SHEETS
    out_name = "iphone_%s.png" if not (param or changes) else "iphone_%s_x.png"
    if not CHROME:
        sys.exit("Chrome не найден (переменная CHROME)")

    # копия страницы iPhone без CSP рядом с инструментом: нужен доступ к scriptFor() из iframe
    page = open(os.path.join(ROOT, "site", "iphone.html"), encoding="utf-8").read()
    page = re.sub(r'<meta http-equiv="Content-Security-Policy"[^>]*>', "", page)
    site = os.path.join(ROOT, "tools", "_site_for_preview.html")
    open(site, "w", encoding="utf-8").write(page)
    out_dir = os.path.join(ROOT, ".tmp", "team")
    os.makedirs(out_dir, exist_ok=True)
    tool = "file:///" + os.path.join(ROOT, "tools", "scriptable_preview.html").replace("\\", "/").lstrip("/")
    try:
        for sh in sheets:
            q = {"site": "_site_for_preview.html", "cls": cls, "sheet": sh}
            if param:
                q["param"] = param
            if changes:
                shutil.copy(changes, os.path.join(ROOT, "tools", "_changes_for_preview.json"))
                q["changes"] = "_changes_for_preview.json"
            png = os.path.join(out_dir, out_name % sh)
            w, h = SIZE[sh]
            subprocess.run([CHROME, "--headless=new", "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
                            "--allow-file-access-from-files", "--force-device-scale-factor=1",
                            "--window-size=%d,%d" % (w, h), "--virtual-time-budget=15000",
                            "--screenshot=" + png, tool + "?" + urllib.parse.urlencode(q)],
                           capture_output=True, timeout=180)
            crop(png)
            print(png)
    finally:
        os.remove(site)
        if changes and os.path.exists(os.path.join(ROOT, "tools", "_changes_for_preview.json")):
            os.remove(os.path.join(ROOT, "tools", "_changes_for_preview.json"))


def crop(png):
    """Обрезать пустой фон снизу и справа."""
    im = Image.open(png).convert("RGB")
    bg = Image.new("RGB", im.size, im.getpixel((im.width - 1, im.height - 1)))
    box = ImageChops.difference(im, bg).getbbox()
    if box:
        im.crop((0, 0, min(im.width, box[2] + 16), min(im.height, box[3] + 16))).save(png)


if __name__ == "__main__":
    main()
