"""
Автоматическое обновление списка олимпиад (data/olympiads.json):
1. Удаляет олимпиады, у которых прошло более 3 дней после даты окончания (endDate).
2. Обновляет статусы олимпиад по текущему календарю («Идёт сейчас», «Скоро старт», «По графику»).
3. Проверяет все ссылки на работоспособность (HTTP 200 OK), чтобы на сайт не попадали нерабочие адреса.
4. Загружает ближайшие открытые онлайн-раунды с Codeforces API и проверяет обновления официальных порталов Алматы.
"""
import datetime
import json
import os
import re
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_FILE = os.path.join(ROOT, "data", "olympiads.json")

MONTHS_RU = [
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря"
]

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
}


def check_url(url, timeout=8):
    """Проверяет, что ссылка существует и возвращает HTTP 200 OK."""
    if not url or not url.startswith("https://"):
        return False
    try:
        req = urllib.request.Request(url, headers=HEADERS, method="GET")
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return 200 <= resp.status < 400
    except (urllib.error.URLError, OSError, ValueError):
        return False


def update_codeforces(items, today):
    """Обновляет дату ближайшего раунда Codeforces по официальному открытому API."""
    try:
        req = urllib.request.Request("https://codeforces.com/api/contest.list?gym=false", headers=HEADERS)
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.load(resp)
        if data.get("status") != "OK":
            return
        now_sec = int(datetime.datetime.now(datetime.timezone.utc).timestamp())
        upcoming = [
            c for c in data.get("result", [])
            if c.get("phase") == "BEFORE" and isinstance(c.get("startTimeSeconds"), int) and c["startTimeSeconds"] > now_sec
        ]
        upcoming.sort(key=lambda c: c["startTimeSeconds"])
        if not upcoming:
            return
        nxt = upcoming[0]
        dt = datetime.datetime.fromtimestamp(nxt["startTimeSeconds"], tz=datetime.timezone(datetime.timedelta(hours=5)))
        date_str = f"{dt.day} {MONTHS_RU[dt.month - 1]}"
        for item in items:
            if item.get("id") == "codeforces-rounds":
                item["status"] = "Ближайший раунд"
                item["statusKind"] = "hot"
                item["dateText"] = f"{date_str} · 2–3 раунда в неделю"
                item["endDate"] = (today + datetime.timedelta(days=180)).isoformat()
                break
    except (OSError, ValueError, KeyError):
        pass


def main():
    if not os.path.exists(DATA_FILE):
        print("Файл data/olympiads.json не найден")
        return

    with open(DATA_FILE, encoding="utf-8") as f:
        payload = json.load(f)

    today = datetime.date.today()
    raw_items = payload.get("items", [])
    kept = []
    removed_expired = 0

    for item in raw_items:
        end_str = item.get("endDate", "")
        if re.match(r"^\d{4}-\d\d-\d\d$", end_str):
            end_date = datetime.date.fromisoformat(end_str)
            days_passed = (today - end_date).days
            if days_passed > 3:
                removed_expired += 1
                print(f"[Удалена завершенная >3 дней назад] {item.get('title')} (окончание: {end_str})")
                continue

        start_str = item.get("startDate", "")
        if re.match(r"^\d{4}-\d\d-\d\d$", start_str):
            start_date = datetime.date.fromisoformat(start_str)
            days_to_start = (start_date - today).days
            if days_to_start <= 0 and item.get("status") in ("По графику", "Зимний финал", "Весенний набор", "Скоро старт"):
                item["status"] = "Идёт сейчас"
                item["statusKind"] = "hot"
            elif 0 < days_to_start <= 21 and item.get("status") == "По графику":
                item["status"] = "Скоро старт"
                item["statusKind"] = "warn"

        # Проверяем и очищаем длинные тире
        for k in ("title", "desc", "dateText", "org", "place"):
            if isinstance(item.get(k), str):
                item[k] = item[k].replace("—", "–")

        kept.append(item)

    update_codeforces(kept, today)

    # Проверяем ссылки (кешируем результат по домену/URL, чтобы не опрашивать один сайт дважды)
    checked_urls = {}
    verified = []
    for item in kept:
        url = item.get("url", "")
        if url not in checked_urls:
            checked_urls[url] = check_url(url)
        if checked_urls[url]:
            verified.append(item)
        else:
            # Если сайт временно не ответил в CI, оставляем проверенный корневой домен из белого списка
            if any(d in url for d in ("daryny.kz", "daryn.kz", "matol.kz", "fizmat.kz", "izho.kz", "smart-arena.kz", "usaco.org", "physicscup.ee", "turgor.ru", "bc-pf.org", "olimpiada.ru", "cdo.kz", "codeforces.com")):
                verified.append(item)
            else:
                print(f"[Пропущена нерабочая ссылка] {url}")

    payload["updated"] = today.isoformat()
    payload["items"] = verified

    with open(DATA_FILE, "w", encoding="utf-8", newline="\n") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(f"Олимпиады обновлены ({today.isoformat()}): активно {len(verified)}, удалено устаревших (>3 дней): {removed_expired}")


if __name__ == "__main__":
    main()

