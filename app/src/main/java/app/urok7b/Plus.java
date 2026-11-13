package app.urok7b;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * СОР и СОЧ, кружки и ШОР, праздники. Те же правила, что на сайте и в виджете iPhone
 * (site/src/js/rules.js, блоки «СОР и СОЧ», «кружки и ШОР», «праздники»): править вместе.
 * Данные из schedule.json: sor, soch, shor, themes. Выбор ученика (группы ШОР и свои кружки) лежит в настройках.
 */
final class Plus {

    static final String KEY_EXTRAS = "extras_json"; // {shor: [id], clubs: [{name, place, times: [[день 0–6, "с", "по"]]}]}
    static final String KEY_FEST = "fest_themes";   // праздничные темы включены

    static final class Exam {
        final String date, type, subject;

        Exam(String date, String type, String subject) {
            this.date = date;
            this.type = type;
            this.subject = subject;
        }

        String key() { return date + (type.equals("СОЧ") ? "0" : "1") + subject; }
    }

    /** Занятие после уроков. on: уже идёт (target это конец), иначе target это начало. */
    static final class Extra {
        String name, place;
        int start, end;
        boolean on;
        long target;
    }

    // Ссылки меняются целиком (новое расписание, новый выбор), сами объекты после этого только читаются:
    // замки не нужны, и нет встречных замков с Schedule.
    private static volatile JSONObject sor, soch;
    private static volatile JSONArray shor, themes;
    private static volatile JSONObject set = new JSONObject();
    private static volatile String setRaw;

    private Plus() {}

    static void parse(JSONObject root) {
        sor = root.optJSONObject("sor");
        soch = root.optJSONObject("soch");
        shor = root.optJSONArray("shor");
        themes = root.optJSONArray("themes");
    }

    /** Выбор ученика из настроек (строка JSON или null). Кривая запись читается как пустая. */
    static void select(String json) {
        if (json == null ? setRaw == null : json.equals(setRaw)) return;
        setRaw = json;
        try {
            set = json == null ? new JSONObject() : new JSONObject(json);
        } catch (Exception e) {
            set = new JSONObject();
        }
    }

    static JSONObject set() { return set; }
    static JSONArray shor() { JSONArray g = shor; return g == null ? new JSONArray() : g; }

    // ---------- СОР и СОЧ ----------

    /** Все СОР и СОЧ класса по порядку: в один день сначала СОЧ, внутри по названию предмета. */
    static List<Exam> examList(String cls) {
        List<Exam> out = new ArrayList<>();
        JSONObject allSor = sor, allSoch = soch;
        JSONObject r = allSor == null ? null : allSor.optJSONObject(cls), q = allSoch == null ? null : allSoch.optJSONObject(cls);
        for (Iterator<String> it = r == null ? Collections.<String>emptyIterator() : r.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONArray dates = r.optJSONArray(k);
            for (int i = 0; dates != null && i < dates.length(); i++) out.add(new Exam(dates.optString(i), "СОР", Schedule.subjectName(k)));
        }
        for (Iterator<String> it = q == null ? Collections.<String>emptyIterator() : q.keys(); it.hasNext(); ) {
            String k = it.next();
            if (q.opt(k) instanceof String) out.add(new Exam(q.optString(k), "СОЧ", Schedule.subjectName(k)));
        }
        Collections.sort(out, (a, b) -> a.key().compareTo(b.key()));
        return out;
    }

    /** СОР и СОЧ класса на дату. У своего расписания графика лицея нет. */
    static List<Exam> examsOn(String cls, LocalDate d) {
        List<Exam> out = new ArrayList<>();
        if (Schedule.isCustom()) return out;
        String t = d.toString();
        for (Exam x : examList(cls)) if (x.date.equals(t)) out.add(x);
        return out;
    }

    /** «СОЧ: алгебра, физика. СОР: химия» */
    static String examText(List<Exam> list) {
        StringBuilder out = new StringBuilder();
        for (String type : new String[]{"СОЧ", "СОР"}) {
            StringBuilder s = new StringBuilder();
            for (Exam x : list) if (x.type.equals(type)) s.append(s.length() > 0 ? ", " : "").append(x.subject.toLowerCase(Locale.ROOT));
            if (s.length() > 0) out.append(out.length() > 0 ? ". " : "").append(type).append(": ").append(s);
        }
        return out.toString();
    }

    /** Последний день, когда в лицее у кого-то СОР или СОЧ; "" если графика нет. После него вкладка прячется. */
    static String examsEnd() {
        String last = "";
        for (String c : Schedule.classes()) for (Exam x : examList(c)) if (x.date.compareTo(last) > 0) last = x.date;
        return last;
    }

    /** Строка для уведомления и виджетов: «Сегодня СОЧ: …», иначе «Завтра СОР: …», иначе про завтрашний праздник. */
    static String note(ZonedDateTime now, boolean lessonsToday) {
        LocalDate today = now.toLocalDate();
        List<Exam> a = examsOn(Schedule.cls(), today), b = examsOn(Schedule.cls(), today.plusDays(1));
        if (!a.isEmpty() && lessonsToday) return "Сегодня " + examText(a);
        return !b.isEmpty() ? "Завтра " + examText(b) : offSoon(now);
    }

    // ---------- кружки и ШОР ----------

    /** Занятия после уроков на дату, по времени. В каникулы и праздники их нет. */
    static List<Extra> extrasOn(LocalDate d) {
        List<Extra> out = new ArrayList<>();
        if (Schedule.holidayAny(d) != null) return out;
        int w = d.getDayOfWeek().getValue() - 1;
        JSONObject mine = set;
        JSONArray groups = shor, picked = mine.optJSONArray("shor"), clubs = mine.optJSONArray("clubs");
        for (int i = 0; groups != null && picked != null && i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            if (g == null) continue;
            for (int k = 0; k < picked.length(); k++) {
                if (g.optString("id").equals(picked.optString(k))) add(out, "ШОР · " + g.optString("subject"), g.optString("place"), g.optJSONArray("times"), w);
            }
        }
        for (int i = 0; clubs != null && i < clubs.length(); i++) {
            JSONObject c = clubs.optJSONObject(i);
            if (c != null) add(out, Schedule.clean(c.optString("name")), Schedule.clean(c.optString("place")), c.optJSONArray("times"), w);
        }
        Collections.sort(out, (a, b) -> Integer.compare(a.start, b.start));
        return out;
    }

    private static void add(List<Extra> out, String name, String place, JSONArray times, int w) {
        for (int i = 0; times != null && i < times.length(); i++) {
            JSONArray t = times.optJSONArray(i);
            if (t == null || !(t.opt(0) instanceof Integer) || t.optInt(0) != w || name.isEmpty()) continue;
            int s = Schedule.parseTime(t.optString(1)), e = Schedule.parseTime(t.optString(2));
            if (s < 0 || e <= s) continue;
            Extra x = new Extra();
            x.name = name;
            x.place = place;
            x.start = s;
            x.end = e;
            out.add(x);
        }
    }

    /** Ближайшее занятие сегодня: идёт или впереди. null: на сегодня всё. */
    static Extra extraNow(ZonedDateTime now) {
        int m = now.getHour() * 60 + now.getMinute();
        for (Extra x : extrasOn(now.toLocalDate())) {
            if (m >= x.end) continue;
            x.on = m >= x.start;
            x.target = Schedule.at(now.toLocalDate(), x.on ? x.end : x.start, now.getZone());
            return x;
        }
        return null;
    }

    /** «ШОР · Физика: до конца 1 ч 5 мин» или «…: до начала 20 мин». */
    static String extraText(Extra x, long nowMs) {
        long left = Math.max(1, (x.target - nowMs + 59_999) / 60_000), h = left / 60, m = left % 60;
        return x.name + ": до " + (x.on ? "конца " : "начала ") + ((h > 0 ? h + " ч " : "") + (m > 0 || h == 0 ? m + " мин" : "")).trim();
    }

    // ---------- праздники ----------

    /** Ближайший учебный день (завтра, а в пятницу понедельник) выпал на праздник. Про каникулы говорит vacationText. */
    static String offSoon(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        DayOfWeek w = today.getDayOfWeek();
        if (Schedule.isCustom() || Schedule.holidayAny(today) != null || w.getValue() > 5) return "";
        String x = Schedule.holidayAny(today.plusDays(w == DayOfWeek.FRIDAY ? 3 : 1));
        return x == null || Schedule.isVacation(x) ? "" : (w == DayOfWeek.FRIDAY ? "В понедельник " : "Завтра ") + x + ", в школу не идём";
    }

    /** Праздничная тема на дату: {значок, цвет RRGGBB, название, фраза} или null. Отрезок может переходить через Новый год. */
    static String[] festive(LocalDate d) {
        String t = d.toString().substring(5);
        JSONArray all = themes;
        for (int i = 0; all != null && i < all.length(); i++) {
            JSONArray x = all.optJSONArray(i);
            if (x == null || x.length() < 5) continue;
            String a = x.optString(0), b = x.optString(1);
            boolean in = a.compareTo(b) <= 0 ? t.compareTo(a) >= 0 && t.compareTo(b) <= 0 : t.compareTo(a) >= 0 || t.compareTo(b) <= 0;
            if (in && x.optString(3).matches("[0-9a-fA-F]{6}")) return new String[]{x.optString(2), x.optString(3), x.optString(4), x.optString(5)};
        }
        String hol = Schedule.isCustom() ? null : Schedule.holidayAny(d);
        return Schedule.isVacation(hol) ? new String[]{"🎈", "34C759", hol, "Отдыхай, расписание подождёт"} : null;
    }
}
