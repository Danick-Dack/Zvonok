"""Итог Lighthouse в аннотации сборки: по каждому отчёту четыре оценки; ниже 100 видно сразу, с причинами.
python3 tools/lh_summary.py lh-*.json"""
import json
import sys

NAMES = {"performance": "скорость", "accessibility": "доступность", "best-practices": "практики", "seo": "SEO"}
low = 0
for path in sys.argv[1:]:
    try:
        r = json.load(open(path, encoding="utf-8"))
    except (OSError, ValueError):
        print("::warning::%s: отчёта нет" % path)
        low += 1
        continue
    scores = {k: round((r["categories"][k]["score"] or 0) * 100) for k in NAMES}
    line = "%s: %s" % (path, ", ".join("%s %d" % (NAMES[k], v) for k, v in scores.items()))
    bad = [k for k, v in scores.items() if v < 100]
    if not bad:
        print("::notice::" + line)
        continue
    low += 1
    why = []
    for k in bad:
        for ref in r["categories"][k]["auditRefs"]:
            a = r["audits"].get(ref["id"], {})
            if ref.get("weight") and a.get("score") is not None and a["score"] < 1:
                nodes = [i.get("node", {}).get("snippet", "")[:90] for i in (a.get("details") or {}).get("items", [])[:3] if isinstance(i, dict)]
                why.append(a.get("title", ref["id"]) + (" (" + a.get("displayValue", "") + ")" if a.get("displayValue") else "")
                           + (": " + " | ".join(n for n in nodes if n) if any(nodes) else ""))
    print("::warning::" + line + " · " + "; ".join(why[:6]))
sys.exit(0)
