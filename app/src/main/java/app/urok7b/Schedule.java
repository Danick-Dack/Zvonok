package app.urok7b;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Расписание лицея №165 и расчёт текущего состояния.
 * Данные всех классов — в data/schedule.json (тот же файл использует сайт).
 * Своё расписание хранится только на телефоне, в настройках приложения.
 */
public final class Schedule {

    public static final class Lesson {
        public final int num;      // номер урока
        public final int start;    // минуты от начала суток
        public final int end;
        public final String subject;
        public final String room;

        Lesson(int num, int start, int end, String subject, String room) {
            this.num = num;
            this.start = start;
            this.end = end;
            this.subject = subject;
            this.room = room == null ? "" : room;
        }

        public String startText() { return hm(start); }
        public String endText() { return hm(end); }

        /** "каб. 24/35" или "спортзал". */
        public String roomText() {
            if (room.isEmpty()) return "";
            return Character.isDigit(room.charAt(0)) ? "каб. " + room : room;
        }
    }

    public enum Kind { BEFORE, LESSON, BREAK, AFTER, DAY_OFF }

    public static final class Status {
        public Kind kind;
        public Lesson current;       // урок сейчас (LESSON) или предыдущий урок (BREAK)
        public Lesson next;          // следующий урок
        public String nextDayLabel;  // "Завтра", "Понедельник"... (только AFTER / DAY_OFF)
        public String nextDayShort;  // "Завтра", "Пн"...
        public String nextDayWhen;   // "завтра", "в понедельник"...
        public String todayEnd;      // во сколько сегодня последний звонок
        public int todayCount;       // сколько уроков сегодня
        public int nextDayCount;     // сколько уроков в тот день
        public String nextDayEnd;    // во сколько заканчиваются
        public long startMillis;     // начало текущего отрезка (для прогресса)
        public long targetMillis;    // когда звонок / начало уроков
        public boolean notify;       // последний час перед уроками (BEFORE)
        public long windowStartMillis; // когда начнётся этот час (BEFORE / AFTER / DAY_OFF)
    }

    /** За сколько минут до первого урока включать отсчёт. */
    public static final int LEAD_MINUTES = 60;
    public static final int MAX_DAYS = 6;      // Пн–Сб
    public static final int MAX_SLOTS = 10;
    public static final String DEFAULT_CLASS = "7Б";
    public static final String SPORT = "спортзал";

    static final String KEY_CLASS = "class";
    static final String KEY_CUSTOM = "custom_json";
    static final String KEY_USE_CUSTOM = "use_custom";

    // ---- исходные данные (читаются один раз) ----
    private static String school = "Лицей №165";
    private static String term = "";
    private static int[][] baseBells;
    private static final Map<String, String> SUBJECTS = new HashMap<>();
    private static final Map<String, String[][]> CLASSES = new HashMap<>();
    private static final List<String> CLASS_ORDER = new ArrayList<>();

    // ---- текущая неделя (выбранный класс или своё расписание) ----
    @SuppressWarnings("unchecked")
    private static List<Lesson>[] week = new List[7];
    private static String loadedKey;
    private static String title = DEFAULT_CLASS;
    private static boolean custom;

    private Schedule() {}

    // ================= загрузка =================

    /** Подготовить расписание. Дёшево: повторный вызов без изменений ничего не делает. */
    public static synchronized void load(Context ctx) {
        ensureBase(ctx);
        SharedPreferences p = Updater.prefs(ctx);
        String cls = p.getString(KEY_CLASS, DEFAULT_CLASS);
        if (!CLASSES.containsKey(cls)) cls = DEFAULT_CLASS;
        boolean useCustom = p.getBoolean(KEY_USE_CUSTOM, false);
        String json = useCustom ? p.getString(KEY_CUSTOM, null) : null;
        String key = cls + "|" + useCustom + "|" + (json == null ? 0 : json.hashCode());
        if (key.equals(loadedKey)) return;

        List<Lesson>[] w = null;
        if (json != null) {
            try {
                w = fromCustom(new JSONObject(json));
            } catch (Exception ignored) {
                w = null;
            }
        }
        custom = w != null;
        if (w == null) w = fromClass(cls);
        week = w;
        title = custom ? "Своё" : cls;
        loadedKey = key;
    }

    public static synchronized void invalidate() {
        loadedKey = null;
    }

    private static void ensureBase(Context ctx) {
        if (baseBells != null) return;
        try (InputStream in = ctx.getAssets().open("schedule.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            JSONObject root = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            school = root.optString("school", school);
            term = root.optString("term", "");
            baseBells = parseBells(root.getJSONArray("bells"));
            JSONObject subj = root.getJSONObject("subjects");
            for (Iterator<String> it = subj.keys(); it.hasNext(); ) {
                String k = it.next();
                SUBJECTS.put(k, subj.getString(k));
            }
            JSONObject cls = root.getJSONObject("classes");
            for (Iterator<String> it = cls.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray days = cls.getJSONArray(k);
                String[][] d = new String[days.length()][];
                for (int i = 0; i < days.length(); i++) {
                    JSONArray day = days.getJSONArray(i);
                    d[i] = new String[day.length()];
                    for (int j = 0; j < day.length(); j++) d[i][j] = day.optString(j, "");
                }
                CLASSES.put(k, d);
                CLASS_ORDER.add(k);
            }
            Collections.sort(CLASS_ORDER, (a, b) -> {
                int ga = grade(a), gb = grade(b);
                return ga != gb ? Integer.compare(ga, gb) : a.compareTo(b);
            });
        } catch (Exception e) {
            // файл расписания встроен в APK; если он повреждён — показываем пустую неделю
            baseBells = new int[0][];
        }
    }

    private static int grade(String cls) {
        int g = 0;
        for (int i = 0; i < cls.length() && Character.isDigit(cls.charAt(i)); i++) {
            g = g * 10 + (cls.charAt(i) - '0');
        }
        return g;
    }

    static int[][] parseBells(JSONArray arr) throws Exception {
        int n = Math.min(arr.length(), MAX_SLOTS);
        int[][] b = new int[n][];
        for (int i = 0; i < n; i++) {
            JSONArray p = arr.getJSONArray(i);
            int s = parseTime(p.getString(0)), e = parseTime(p.getString(1));
            b[i] = s >= 0 && e > s ? new int[]{s, e} : null;
        }
        return b;
    }

    /** "8:05" → 485, ошибка → -1. */
    public static int parseTime(String t) {
        if (t == null) return -1;
        t = t.trim().replace('.', ':');
        int c = t.indexOf(':');
        if (c <= 0 || c >= t.length() - 1) return -1;
        try {
            int h = Integer.parseInt(t.substring(0, c));
            int m = Integer.parseInt(t.substring(c + 1));
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Lesson>[] fromClass(String cls) {
        List<Lesson>[] w = new List[7];
        String[][] days = CLASSES.get(cls);
        for (int d = 0; d < 7; d++) {
            List<Lesson> out = new ArrayList<>();
            if (days != null && d < days.length) {
                String[] raw = days[d];
                for (int i = 0; i < raw.length && i < baseBells.length; i++) {
                    String r = raw[i];
                    if (r == null || r.isEmpty() || baseBells[i] == null) continue;
                    int sp = r.indexOf(' ');
                    String code = sp < 0 ? r : r.substring(0, sp);
                    String room = sp < 0 ? "" : r.substring(sp + 1).trim();
                    out.add(new Lesson(i + 1, baseBells[i][0], baseBells[i][1],
                            subjectName(code), roomName(room)));
                }
            }
            w[d] = out;
        }
        return w;
    }

    /**
     * Своё расписание: {"v":1,"bells":[["8:00","8:45"],...],"days":[[["Алгебра","24/35"],...],...]}
     * Пустой предмет — урока нет.
     */
    @SuppressWarnings("unchecked")
    static List<Lesson>[] fromCustom(JSONObject o) throws Exception {
        int[][] bells = o.has("bells") ? parseBells(o.getJSONArray("bells")) : baseBells;
        JSONArray days = o.getJSONArray("days");
        List<Lesson>[] w = new List[7];
        for (int d = 0; d < 7; d++) {
            List<Lesson> out = new ArrayList<>();
            if (d < days.length() && d < MAX_DAYS) {
                JSONArray day = days.getJSONArray(d);
                for (int i = 0; i < day.length() && i < bells.length; i++) {
                    JSONArray l = day.optJSONArray(i);
                    if (l == null || bells[i] == null) continue;
                    String subj = clean(l.optString(0, ""));
                    if (subj.isEmpty()) continue;
                    out.add(new Lesson(i + 1, bells[i][0], bells[i][1], subj, roomName(clean(l.optString(1, "")))));
                }
            }
            w[d] = out;
        }
        return w;
    }

    /** Убрать переводы строк и ограничить длину (данные из чужого кода импорта). */
    static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private static String subjectName(String code) {
        String s = SUBJECTS.get(code);
        return s == null ? code : s;
    }

    private static String roomName(String room) {
        return "с/з".equals(room) ? SPORT : room;
    }

    // ================= доступ =================

    public static synchronized List<Lesson> lessons(DayOfWeek day) {
        List<Lesson> l = week[day.getValue() - 1];
        return l == null ? Collections.emptyList() : l;
    }

    /** Уроки класса из встроенного расписания (для редактора: «взять за основу»). */
    public static synchronized List<Lesson> classLessons(Context ctx, String cls, DayOfWeek day) {
        ensureBase(ctx);
        return fromClass(cls)[day.getValue() - 1];
    }

    public static synchronized List<String> classes(Context ctx) {
        ensureBase(ctx);
        return new ArrayList<>(CLASS_ORDER);
    }

    public static synchronized int[][] bells(Context ctx) {
        ensureBase(ctx);
        return baseBells;
    }

    public static synchronized List<String> subjectNames(Context ctx) {
        ensureBase(ctx);
        List<String> l = new ArrayList<>(SUBJECTS.values());
        Collections.sort(l);
        return l;
    }

    /** "7Б" или "Своё". */
    public static synchronized String title() { return title; }
    public static synchronized boolean isCustom() { return custom; }
    public static String school() { return school; }
    public static String term() { return term; }

    // ================= расчёт состояния =================

    public static Status compute(ZonedDateTime now) {
        Status s = new Status();
        LocalDate today = now.toLocalDate();
        ZoneId zone = now.getZone();
        int sec = now.toLocalTime().toSecondOfDay();
        List<Lesson> list = lessons(today.getDayOfWeek());

        if (!list.isEmpty()) {
            Lesson first = list.get(0);
            Lesson last = list.get(list.size() - 1);
            s.todayCount = list.size();
            s.todayEnd = last.endText();
            if (sec < first.start * 60) {
                s.kind = Kind.BEFORE;
                s.next = first;
                s.nextDayCount = list.size();
                s.nextDayEnd = last.endText();
                s.targetMillis = at(today, first.start, zone);
                s.windowStartMillis = at(today, first.start - LEAD_MINUTES, zone);
                s.startMillis = s.windowStartMillis;
                s.notify = sec >= (first.start - LEAD_MINUTES) * 60;
                return s;
            }
            for (int i = 0; i < list.size(); i++) {
                Lesson l = list.get(i);
                if (sec < l.end * 60) {
                    if (sec >= l.start * 60) {
                        s.kind = Kind.LESSON;
                        s.current = l;
                        s.next = i + 1 < list.size() ? list.get(i + 1) : null;
                        s.startMillis = at(today, l.start, zone);
                        s.targetMillis = at(today, l.end, zone);
                    } else {
                        Lesson prev = list.get(i - 1);
                        s.kind = Kind.BREAK;
                        s.current = prev;
                        s.next = l;
                        s.startMillis = at(today, prev.end, zone);
                        s.targetMillis = at(today, l.start, zone);
                    }
                    return s;
                }
            }
            s.kind = Kind.AFTER;
        } else {
            s.kind = Kind.DAY_OFF;
        }

        for (int d = 1; d <= 7; d++) {
            LocalDate date = today.plusDays(d);
            List<Lesson> l = lessons(date.getDayOfWeek());
            if (l.isEmpty()) continue;
            Lesson first = l.get(0);
            s.next = first;
            s.nextDayCount = l.size();
            s.nextDayEnd = l.get(l.size() - 1).endText();
            s.nextDayLabel = d == 1 ? "Завтра" : dayName(date.getDayOfWeek());
            s.nextDayShort = d == 1 ? "Завтра" : dayShort(date.getDayOfWeek());
            s.nextDayWhen = d == 1 ? "завтра" : dayWhen(date.getDayOfWeek());
            s.targetMillis = at(date, first.start, zone);
            s.windowStartMillis = at(date, first.start - LEAD_MINUTES, zone);
            break;
        }
        return s;
    }

    // ================= текст =================

    public static String dayName(DayOfWeek d) {
        String[] n = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};
        return n[d.getValue() - 1];
    }

    public static String dayShort(DayOfWeek d) {
        String[] n = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        return n[d.getValue() - 1];
    }

    /** "в понедельник", "во вторник", "в среду"... */
    public static String dayWhen(DayOfWeek d) {
        String[] n = {"в понедельник", "во вторник", "в среду", "в четверг", "в пятницу",
                "в субботу", "в воскресенье"};
        return n[d.getValue() - 1];
    }

    /** "1 урок", "3 урока", "7 уроков". */
    public static String lessonsWord(int n) {
        int a = n % 10, b = n % 100;
        if (a == 1 && b != 11) return n + " урок";
        if (a >= 2 && a <= 4 && (b < 12 || b > 14)) return n + " урока";
        return n + " уроков";
    }

    /** "12 мин", "1 ч 05 мин". Округляет вверх до минуты. */
    public static String fmtLeft(long ms) {
        long min = ms <= 0 ? 0 : (ms + 59_999) / 60_000;
        if (min < 60) return min + " мин";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " ч" : h + " ч " + (m < 10 ? "0" : "") + m + " мин";
    }

    /** Коротко для таймера без секунд: "24 мин", "1:05". */
    public static String fmtTimer(long ms) {
        long min = ms <= 0 ? 0 : (ms + 59_999) / 60_000;
        if (min < 60) return min + " мин";
        long m = min % 60;
        return (min / 60) + ":" + (m < 10 ? "0" : "") + m;
    }

    public static String hm(int minutes) {
        int m = minutes % 60;
        return (minutes / 60) + ":" + (m < 10 ? "0" : "") + m;
    }

    private static long at(LocalDate date, int minutes, ZoneId zone) {
        return date.atStartOfDay(zone).plusMinutes(minutes).toInstant().toEpochMilli();
    }
}
