package app.urok7b;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Расписание лицея №165 и расчёт текущего состояния.
 * Данные всех классов в data/schedule.json (тот же файл у сайта). Правила дня те же, что в site/src/js/rules.js:
 * юнит-тесты сверяют ответы с эталоном, который tools/golden.py снимает с rules.js.
 * Своё расписание хранится только на телефоне, в настройках приложения.
 */
public final class Schedule {

    public static final class Lesson {
        public final int num;      // номер урока
        public final int start;    // минуты от начала суток
        public final int end;
        public final String subject;
        public final String room;
        public final boolean changed;    // замена из changes.json
        public final boolean shortBell;  // сокращённые звонки в этот день

        Lesson(int num, int start, int end, String subject, String room) {
            this(num, start, end, subject, room, false, false);
        }

        Lesson(int num, int start, int end, String subject, String room, boolean changed, boolean shortBell) {
            this.num = num;
            this.start = start;
            this.end = end;
            this.subject = subject;
            this.room = room == null ? "" : room;
            this.changed = changed;
            this.shortBell = shortBell;
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
        public String holiday;       // название каникул / праздника (DAY_OFF), иначе null
        public List<Lesson> list = Collections.emptyList(); // уроки сегодня
        public int index = -1;       // номер текущего (LESSON) или предыдущего (BREAK) урока в list
        public LocalDate nextDate;   // день следующего урока (AFTER / DAY_OFF)
        public int nextDays;         // через сколько дней он
        public long nextStartMillis; // когда начнётся следующий урок (0: неизвестно)
    }

    /** Сколько учебных дней до каникул. */
    public static final class Vacation {
        public int days;
        public boolean today;
        public String name;
    }

    /** Кто занимается в кабинете: урок, класс, предмет, время. */
    public static final class Use {
        public final int num, start, end;
        public final String cls, subject;

        Use(Lesson l, String cls) {
            num = l.num; start = l.start; end = l.end; subject = l.subject; this.cls = cls;
        }
    }

    /** Кабинет сейчас: занят ли и что сказать. */
    public static final class RoomState {
        public final boolean busy;
        public final String text;

        RoomState(boolean busy, String text) {
            this.busy = busy;
            this.text = text;
        }
    }

    /** За сколько минут до первого урока включать отсчёт. */
    public static final int LEAD_MINUTES = 60;
    public static final int MAX_DAYS = 6;      // Пн–Сб
    private static final int MAX_BELLS = 20;   // защита от мусора в данных
    public static final String DEFAULT_CLASS = "7Б";
    public static final String SPORT = "спортзал";

    static final String KEY_CLASS = "class";
    static final String KEY_CUSTOM = "custom_json";
    static final String KEY_USE_CUSTOM = "use_custom";
    static final String KEY_CHANGES = "changes_json"; // замены, скачанные с сайта
    static final String KEY_SCHEDULE = "schedule_json"; // всё расписание, скачанное с сайта

    // ---- исходные данные (читаются один раз) ----
    private static String school = "Лицей №165";
    private static String term = "";
    private static int[][] baseBells;
    private static final Map<String, String> SUBJECTS = new LinkedHashMap<>();
    private static final Map<String, String[][]> CLASSES = new HashMap<>();
    private static final List<String> CLASS_ORDER = new ArrayList<>();
    private static final List<String[]> HOLIDAYS = new ArrayList<>(); // {с, по, название}
    private static final Map<String, List<Lesson>[]> WEEKS = new HashMap<>(); // обычные недели классов
    private static List<String> rooms;
    private static String termEnd = "";
    private static String assetChanges;                                // changes.json из APK
    private static JSONObject changes = new JSONObject();              // дата → замены
    private static String cls = DEFAULT_CLASS;

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
        boolean useCustom = p.getBoolean(KEY_USE_CUSTOM, false);
        String json = useCustom ? p.getString(KEY_CUSTOM, null) : null;
        String saved = p.getString(KEY_CHANGES, null);
        String key = cls + "|" + useCustom + "|" + (json == null ? 0 : json.hashCode()) + "|" + (saved == null ? 0 : saved.hashCode());
        if (key.equals(loadedKey)) return;
        if (assetChanges == null) {
            try {
                assetChanges = readAsset(ctx, "changes.json");
            } catch (Exception e) {
                assetChanges = "";
            }
        }
        select(cls, json, saved);
        loadedKey = key;
    }

    /** Данные без Android: для юнит-тестов (расписание, замены, класс, своё расписание или null). */
    static synchronized void init(String scheduleJson, String changesJson, String cls, String customJson) {
        resetBase();
        try {
            parseBase(new JSONObject(scheduleJson));
        } catch (Exception e) {
            baseBells = new int[0][];
        }
        assetChanges = "";
        select(cls, customJson, changesJson);
    }

    private static void select(String c, String customJson, String savedChanges) {
        loadChanges(savedChanges);
        cls = CLASSES.containsKey(c) ? c : CLASSES.containsKey(DEFAULT_CLASS) || CLASS_ORDER.isEmpty() ? DEFAULT_CLASS : CLASS_ORDER.get(0);
        List<Lesson>[] w = null;
        if (customJson != null) {
            try {
                w = fromCustom(new JSONObject(customJson));
            } catch (Exception ignored) {
                w = null;
            }
        }
        custom = w != null;
        week = custom ? w : classWeek(cls);
        title = custom ? "Своё" : cls;
    }

    public static synchronized void invalidate() {
        loadedKey = null;
    }

    /** Скачали новое расписание: при следующем обращении перечитать его. */
    public static synchronized void resetBase() {
        baseBells = null;
        HOLIDAYS.clear();
        SUBJECTS.clear();
        CLASSES.clear();
        CLASS_ORDER.clear();
        WEEKS.clear();
        rooms = null;
        loadedKey = null;
    }

    /** Расписание из APK или скачанное с сайта: берём более свежее (по полю updated). */
    private static JSONObject pickSchedule(Context ctx) throws Exception {
        JSONObject best = new JSONObject(readAsset(ctx, "schedule.json"));
        String saved = Updater.prefs(ctx).getString(KEY_SCHEDULE, null);
        if (saved != null) {
            try {
                JSONObject o = new JSONObject(saved);
                if (o.optJSONObject("classes") != null && o.optJSONArray("bells") != null
                        && o.optString("updated", "").compareTo(best.optString("updated", "")) >= 0) best = o;
            } catch (Exception ignored) {
                // испорченный файл пропускаем
            }
        }
        return best;
    }

    private static void ensureBase(Context ctx) {
        if (baseBells != null) return;
        try {
            parseBase(pickSchedule(ctx));
        } catch (Exception e) {
            // файл расписания встроен в APK; если он повреждён, показываем пустую неделю
            baseBells = new int[0][];
        }
    }

    private static void parseBase(JSONObject root) throws Exception {
        school = root.optString("school", school);
        term = root.optString("term", "");
        termEnd = root.optString("term_end", "");
        JSONArray hol = root.optJSONArray("holidays");
        for (int i = 0; hol != null && i < hol.length(); i++) {
            JSONArray h = hol.optJSONArray(i);
            if (h != null && h.length() >= 3) HOLIDAYS.add(new String[]{h.optString(0), h.optString(1), h.optString(2)});
        }
        JSONObject subj = root.getJSONObject("subjects");
        for (Iterator<String> it = subj.keys(); it.hasNext(); ) {
            String k = it.next();
            SUBJECTS.put(k, subj.getString(k));
        }
        Hues.names(SUBJECTS);
        JSONObject cls = root.getJSONObject("classes");
        for (Iterator<String> it = cls.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONArray days = cls.getJSONArray(k);
            String[][] d = new String[days.length()][];
            for (int i = 0; i < days.length(); i++) {
                JSONArray day = days.optJSONArray(i);
                d[i] = new String[day == null ? 0 : day.length()];
                for (int j = 0; j < d[i].length; j++) d[i][j] = day.isNull(j) ? "" : day.optString(j, "");
            }
            CLASSES.put(k, d);
            CLASS_ORDER.add(k);
        }
        Collections.sort(CLASS_ORDER, (a, b) -> {
            int ga = grade(a), gb = grade(b);
            return ga != gb ? Integer.compare(ga, gb) : a.compareTo(b);
        });
        baseBells = parseBells(root.getJSONArray("bells"));
    }

    private static String readAsset(Context ctx, String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Замены: из APK или скачанные с сайта, берём более свежие (по полю updated). */
    private static void loadChanges(String saved) {
        JSONObject best = null;
        for (String src : new String[]{assetChanges, saved}) {
            if (src == null || src.isEmpty()) continue;
            try {
                JSONObject o = new JSONObject(src);
                if (o.optJSONObject("days") == null) continue;
                if (best == null || o.optString("updated", "").compareTo(best.optString("updated", "")) >= 0) best = o;
            } catch (Exception ignored) {
                // испорченный файл пропускаем
            }
        }
        changes = best == null ? new JSONObject() : best.optJSONObject("days");
    }

    private static int grade(String cls) {
        int g = 0;
        for (int i = 0; i < cls.length() && Character.isDigit(cls.charAt(i)); i++) {
            g = g * 10 + (cls.charAt(i) - '0');
        }
        return g;
    }

    /** Звонки [[с, по]]: кривая пара (или не пара) даёт null, урока с таким номером нет. */
    static int[][] parseBells(JSONArray arr) {
        int n = Math.min(arr.length(), MAX_BELLS);
        int[][] b = new int[n][];
        for (int i = 0; i < n; i++) b[i] = bell(arr.optJSONArray(i));
        return b;
    }

    private static int[] bell(JSONArray p) {
        if (p == null) return null;
        int s = parseTime(text(p.opt(0))), e = parseTime(text(p.opt(1)));
        return s >= 0 && e > s ? new int[]{s, e} : null;
    }

    /** Строка из JSON или null, если там не строка. */
    private static String text(Object o) {
        return o instanceof String ? (String) o : null;
    }

    private static final Pattern TIME = Pattern.compile("^\\s*(\\d{1,2})[:.](\\d{2})\\s*$");

    /** "8:05" или "8.05" → 485, ошибка → -1 (как toMin на сайте). */
    public static int parseTime(String t) {
        if (t == null) return -1;
        Matcher m = TIME.matcher(t);
        if (!m.matches()) return -1;
        int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
        return h < 24 && min < 60 ? h * 60 + min : -1;
    }

    /** Обычная неделя класса (без замен и каникул), один раз на класс. */
    @SuppressWarnings("unchecked")
    private static List<Lesson>[] classWeek(String cls) {
        List<Lesson>[] cached = WEEKS.get(cls);
        if (cached != null) return cached;
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
                    String room = sp < 0 ? "" : r.substring(sp + 1);
                    out.add(new Lesson(i + 1, baseBells[i][0], baseBells[i][1], subjectName(code), roomName(room)));
                }
            }
            w[d] = Collections.unmodifiableList(out);
        }
        WEEKS.put(cls, w);
        return w;
    }

    /**
     * Своё расписание: {"v":1,"bells":[["8:00","8:45"],...],"days":[[["Алгебра","24/35"],...],...]}
     * Пустой предмет или кривой звонок: урока нет. Каникулы и замены своё расписание не трогают.
     */
    @SuppressWarnings("unchecked")
    static List<Lesson>[] fromCustom(JSONObject o) throws Exception {
        int[][] bells = o.optJSONArray("bells") != null ? parseBells(o.getJSONArray("bells")) : baseBells;
        JSONArray days = o.getJSONArray("days");
        List<Lesson>[] w = new List[7];
        for (int d = 0; d < 7; d++) {
            List<Lesson> out = new ArrayList<>();
            JSONArray day = d < MAX_DAYS ? days.optJSONArray(d) : null;
            for (int i = 0; day != null && i < day.length() && i < bells.length; i++) {
                JSONArray l = day.optJSONArray(i);
                if (l == null || bells[i] == null) continue;
                String subj = clean(l.optString(0, ""));
                if (subj.isEmpty()) continue;
                out.add(new Lesson(i + 1, bells[i][0], bells[i][1], subj, roomName(clean(l.optString(1, "")))));
            }
            w[d] = out;
        }
        return w;
    }

    /** Убрать управляющие символы и ограничить длину (данные из чужого кода импорта и замен). */
    static String clean(String s) {
        if (s == null) return "";
        s = trim(s.replaceAll("[\\x00-\\x1f\\x7f]", " "));
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    /** trim как в JavaScript: убирает и неразрывные пробелы. */
    static String trim(String s) {
        int a = 0, b = s.length();
        while (a < b && space(s.charAt(a))) a++;
        while (b > a && space(s.charAt(b - 1))) b--;
        return s.substring(a, b);
    }

    private static boolean space(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\uFEFF';
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

    /** Каникулы или праздник по schedule.json (только для расписания класса). */
    public static synchronized String holidayName(LocalDate d) {
        return custom ? null : holiday(d);
    }

    private static String holiday(LocalDate d) {
        String t = d.toString();
        for (String[] h : HOLIDAYS) if (t.compareTo(h[0]) >= 0 && t.compareTo(h[1]) <= 0) return h[2];
        return null;
    }

    /** День без уроков у всех (каникулы, праздник или «off» в заменах), даже если включено своё расписание. */
    public static synchronized String offDay(LocalDate d) {
        String h = holiday(d);
        if (h != null) return h;
        JSONObject ch = changes.optJSONObject(d.toString());
        Object off = ch == null ? null : ch.opt("off");
        if (off instanceof String && !trim((String) off).isEmpty()) return clean((String) off);
        return Boolean.TRUE.equals(off) ? "Уроков нет" : null;
    }

    /** То же для экрана: у своего расписания каникул и замен нет. */
    public static synchronized String dayOff(LocalDate d) {
        return custom ? null : offDay(d);
    }

    public static boolean isVacation(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("каникул");
    }

    /** Замены на дату (для пометок на главном экране), иначе null. */
    public static synchronized JSONObject changesOn(LocalDate d) {
        return custom ? null : changes.optJSONObject(d.toString());
    }

    /** Номера уроков класса, отменённых заменой в этот день. */
    public static synchronized List<Integer> cancelledOn(LocalDate d) {
        List<Integer> out = new ArrayList<>();
        JSONObject fix = fixFor(changesOn(d), cls);
        if (fix == null) return out;
        for (Iterator<String> it = fix.keys(); it.hasNext(); ) {
            String k = it.next();
            if (k.matches("\\d{1,2}") && noLesson(fix.opt(k))) out.add(Integer.parseInt(k));
        }
        Collections.sort(out);
        return out;
    }

    private static JSONObject fixFor(JSONObject ch, String c) {
        JSONObject classes = ch == null ? null : ch.optJSONObject("classes");
        return classes == null ? null : classes.optJSONObject(c);
    }

    private static boolean noLesson(Object f) {
        return f == null || f == JSONObject.NULL
                || (f instanceof String && trim((String) f).toLowerCase(Locale.ROOT).startsWith("нет"));
    }

    /** Звонки на дату: свои на весь день из замен или обычные (пустой список в заменах: обычные). */
    public static synchronized int[][] dayBells(LocalDate date) {
        JSONObject ch = changes.optJSONObject(date.toString());
        JSONArray cb = ch == null ? null : ch.optJSONArray("bells");
        if (cb == null || cb.length() == 0) return baseBells;
        int[][] bells = new int[Math.min(cb.length(), MAX_BELLS)][];
        for (int i = 0; i < bells.length; i++) {
            int[] b = bell(cb.optJSONArray(i));
            bells[i] = b != null ? b : i < baseBells.length ? baseBells[i] : null;
        }
        return bells;
    }

    /**
     * Уроки класса на дату: каникулы, замены и сокращённые звонки (как на сайте и на iPhone).
     * Урока без звонка в этот день нет.
     */
    private static List<Lesson> dayLessons(String c, LocalDate date) {
        if (offDay(date) != null) return Collections.emptyList();
        List<Lesson> base = classWeek(c)[date.getDayOfWeek().getValue() - 1];
        JSONObject ch = changes.optJSONObject(date.toString());
        if (ch == null) return base;
        int[][] bells = dayBells(date);
        boolean shortDay = bells != baseBells;
        JSONObject fix = fixFor(ch, c);
        int n = 0;
        for (Lesson l : base) n = Math.max(n, l.num);
        if (fix != null) {
            for (Iterator<String> it = fix.keys(); it.hasNext(); ) {
                String k = it.next();
                if (k.matches("\\d{1,2}")) n = Math.max(n, Integer.parseInt(k));
            }
        }
        List<Lesson> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Lesson b = null;
            for (Lesson l : base) if (l.num == i) b = l;
            String subject = b == null ? "" : b.subject;
            String room = b == null ? "" : b.room;
            boolean changed = false;
            String k = String.valueOf(i);
            if (fix != null && fix.has(k)) {
                Object f = fix.opt(k);
                if (noLesson(f)) continue;
                if (f instanceof JSONObject) {
                    JSONObject o = (JSONObject) f;
                    changed = true;
                    Object sj = o.opt("subject"), rm = o.opt("room");
                    if (sj instanceof String && !((String) sj).isEmpty()) {
                        String name = SUBJECTS.get(sj);
                        subject = name != null ? name : clean((String) sj);
                    }
                    if (rm instanceof String || rm instanceof Number) room = roomName(clean(rm.toString()));
                }
            }
            if (subject.isEmpty() || i > bells.length || bells[i - 1] == null) continue;
            out.add(new Lesson(i, bells[i - 1][0], bells[i - 1][1], subject, room, changed, shortDay));
        }
        return out;
    }

    /** Уроки на дату для выбранного класса; своё расписание замены и каникулы не трогают. */
    public static synchronized List<Lesson> lessonsOn(LocalDate date) {
        return custom ? lessons(date.getDayOfWeek()) : dayLessons(cls, date);
    }

    /** Уроки класса из встроенного расписания (для редактора: «взять за основу»). */
    public static synchronized List<Lesson> classLessons(Context ctx, String cls, DayOfWeek day) {
        ensureBase(ctx);
        return classWeek(cls)[day.getValue() - 1];
    }

    public static synchronized List<String> classes(Context ctx) {
        ensureBase(ctx);
        return new ArrayList<>(CLASS_ORDER);
    }

    static synchronized List<String> classes() {
        return new ArrayList<>(CLASS_ORDER);
    }

    public static synchronized int[][] bells(Context ctx) {
        ensureBase(ctx);
        return baseBells;
    }

    public static synchronized List<String> subjectNames(Context ctx) {
        ensureBase(ctx);
        List<String> l = new ArrayList<>(new LinkedHashSet<>(SUBJECTS.values()));
        Collections.sort(l);
        return l;
    }

    /** "7Б" или "Своё". */
    public static synchronized String title() { return title; }
    public static synchronized boolean isCustom() { return custom; }
    /** Класс из настроек (и когда включено своё расписание). */
    public static synchronized String cls() { return cls; }
    public static String school() { return school; }
    public static String term() { return term; }
    public static String termEnd() { return termEnd; }

    // ================= кабинеты =================

    /** "42/21": две группы в двух кабинетах. */
    public static List<String> splitRooms(String r) {
        List<String> out = new ArrayList<>();
        for (String x : (r == null ? "" : r).split("/")) if (!trim(x).isEmpty()) out.add(trim(x));
        return out;
    }

    /** Все кабинеты из обычного расписания всех классов: по номеру, спортзал и прочие в конце. */
    public static synchronized List<String> rooms() {
        if (rooms != null) return rooms;
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String c : CLASS_ORDER) for (List<Lesson> day : classWeek(c)) for (Lesson l : day) set.addAll(splitRooms(l.room));
        List<String> out = new ArrayList<>(set);
        Collator ru = Collator.getInstance(new Locale("ru"));
        Collections.sort(out, (a, b) -> {
            int x = roomNum(a), y = roomNum(b);
            return x != y ? Integer.compare(x, y) : ru.compare(a, b);
        });
        return rooms = Collections.unmodifiableList(out);
    }

    /** Номер в начале названия (как parseInt на сайте), без номера 999. */
    private static int roomNum(String r) {
        int i = 0, n = 0;
        while (i < r.length() && i < 6 && Character.isDigit(r.charAt(i))) n = n * 10 + (r.charAt(i++) - '0');
        return i == 0 || n == 0 ? 999 : n;
    }

    /** Кто где сидит в эту дату (с заменами). В день без уроков показываем обычный день недели. */
    public static synchronized Map<String, List<Use>> roomDay(LocalDate date) {
        boolean off = offDay(date) != null;
        int d = date.getDayOfWeek().getValue() - 1;
        Map<String, List<Use>> use = new HashMap<>();
        for (String c : CLASS_ORDER) {
            for (Lesson l : off ? classWeek(c)[d] : dayLessons(c, date)) {
                for (String r : splitRooms(l.room)) {
                    List<Use> list = use.get(r);
                    if (list == null) use.put(r, list = new ArrayList<>());
                    list.add(new Use(l, c));
                }
            }
        }
        return use;
    }

    /** Номера уроков, на которых в эту дату кто-то занимается. */
    public static synchronized List<Integer> slots(LocalDate date, Map<String, List<Use>> use) {
        int[][] bells = dayBells(date);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < bells.length; i++) {
            if (bells[i] == null) continue;
            boolean used = false;
            for (List<Use> us : use.values()) for (Use u : us) if (u.num == i + 1) used = true;
            if (used) out.add(i + 1);
        }
        return out;
    }

    /** Свободные кабинеты на уроке (спортзал не в счёт). */
    public static synchronized List<String> freeRooms(Map<String, List<Use>> use, int slot) {
        List<String> out = new ArrayList<>();
        for (String r : rooms()) {
            if (SPORT.equals(r)) continue;
            boolean busy = false;
            List<Use> us = use.get(r);
            if (us != null) for (Use u : us) if (u.num == slot) busy = true;
            if (!busy) out.add(r);
        }
        return out;
    }

    /** Короткий ответ: кто сейчас в кабинете и когда он освободится. */
    public static RoomState roomNow(String room, Map<String, List<Use>> use, int nowMin) {
        List<Use> u = new ArrayList<>(use.containsKey(room) ? use.get(room) : Collections.<Use>emptyList());
        Collections.sort(u, (a, b) -> Integer.compare(a.num, b.num));
        List<Use> cur = new ArrayList<>();
        for (Use x : u) if (nowMin >= x.start && nowMin < x.end) cur.add(x);
        if (!cur.isEmpty()) {
            int n = cur.get(0).num, end = cur.get(0).end;
            for (Use x; (x = find(u, n + 1)) != null; n++) end = x.end; // уроки подряд: занят до конца цепочки
            return new RoomState(true, "Сейчас " + who(cur) + ". Освободится в " + hm(end));
        }
        for (Use x : u) {
            if (x.start < nowMin) continue;
            List<Use> same = new ArrayList<>();
            for (Use y : u) if (y.num == x.num) same.add(y);
            return new RoomState(false, "Сейчас свободен. Дальше " + who(same) + " в " + hm(x.start));
        }
        return new RoomState(false, "Свободен до конца дня");
    }

    private static Use find(List<Use> u, int num) {
        for (Use x : u) if (x.num == num) return x;
        return null;
    }

    private static String who(List<Use> list) {
        LinkedHashSet<String> classes = new LinkedHashSet<>();
        for (Use x : list) classes.add(x.cls);
        return String.join(", ", classes) + ", " + lowFirst(list.get(0).subject);
    }

    /** «Физика» → «физика», а «НВП» и «Python» как есть. */
    static String lowFirst(String t) {
        return t.matches("^[А-ЯЁ][а-яё].*") ? Character.toLowerCase(t.charAt(0)) + t.substring(1) : t;
    }

    // ================= расчёт состояния =================

    public static Status compute(ZonedDateTime now) {
        Status s = new Status();
        LocalDate today = now.toLocalDate();
        ZoneId zone = now.getZone();
        int sec = now.toLocalTime().toSecondOfDay();
        List<Lesson> list = lessonsOn(today);
        s.list = list;

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
                s.nextStartMillis = s.targetMillis;
                return s;
            }
            for (int i = 0; i < list.size(); i++) {
                Lesson l = list.get(i);
                if (sec < l.end * 60) {
                    if (sec >= l.start * 60) {
                        s.kind = Kind.LESSON;
                        s.current = l;
                        s.index = i;
                        s.next = i + 1 < list.size() ? list.get(i + 1) : null;
                        s.startMillis = at(today, l.start, zone);
                        s.targetMillis = at(today, l.end, zone);
                        s.nextStartMillis = s.next == null ? 0 : at(today, s.next.start, zone);
                    } else {
                        Lesson prev = list.get(i - 1);
                        s.kind = Kind.BREAK;
                        s.current = prev;
                        s.index = i - 1;
                        s.next = l;
                        s.startMillis = at(today, prev.end, zone);
                        s.targetMillis = at(today, l.start, zone);
                        s.nextStartMillis = s.targetMillis;
                    }
                    return s;
                }
            }
            s.kind = Kind.AFTER;
        } else {
            s.kind = Kind.DAY_OFF;
            s.holiday = dayOff(today);
        }

        // ближайший учебный день (каникулы бывают длинными, поэтому смотрим на 120 дней вперёд)
        for (int d = 1; d <= 120; d++) {
            LocalDate date = today.plusDays(d);
            List<Lesson> l = lessonsOn(date);
            if (l.isEmpty()) continue;
            Lesson first = l.get(0);
            boolean far = d > 6;
            s.next = first;
            s.nextDate = date;
            s.nextDays = d;
            s.nextDayCount = l.size();
            s.nextDayEnd = l.get(l.size() - 1).endText();
            s.nextDayLabel = d == 1 ? "Завтра" : far ? dateText(date) : dayName(date.getDayOfWeek());
            s.nextDayShort = d == 1 ? "Завтра" : far
                    ? date.getDayOfMonth() + "." + (date.getMonthValue() < 10 ? "0" : "") + date.getMonthValue()
                    : dayShort(date.getDayOfWeek());
            s.nextDayWhen = d == 1 ? "завтра" : far ? dateText(date) : dayWhen(date.getDayOfWeek());
            s.targetMillis = at(date, first.start, zone);
            s.nextStartMillis = s.targetMillis;
            s.windowStartMillis = at(date, first.start - LEAD_MINUTES, zone);
            break;
        }
        return s;
    }

    /**
     * Учебные дни до ближайших каникул: сегодня считается, пока не кончился последний урок.
     * null: своё расписание, сейчас каникулы, четверть кончилась или до каникул больше 60 дней.
     */
    public static synchronized Vacation vacation(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        String t = today.toString();
        if (custom || holiday(today) != null || (!termEnd.isEmpty() && t.compareTo(termEnd) > 0)) return null;
        String[] v = null;
        for (String[] h : HOLIDAYS) {
            if (isVacation(h[2]) && h[0].compareTo(t) > 0 && (v == null || h[0].compareTo(v[0]) < 0)) v = h;
        }
        if (v == null) return null;
        int nowMin = now.getHour() * 60 + now.getMinute(), n = 0;
        boolean td = false;
        for (int i = 0; i < 400; i++) {
            LocalDate d = today.plusDays(i);
            if (d.toString().compareTo(v[0]) >= 0) break;
            List<Lesson> l = lessonsOn(d);
            if (l.isEmpty() || (i == 0 && nowMin >= l.get(l.size() - 1).end)) continue;
            n++;
            if (i == 0) td = true;
        }
        if (n > 60) return null;
        Vacation r = new Vacation();
        r.days = n;
        r.today = td;
        r.name = v[2];
        return r;
    }

    /** "До каникул 18 учебных дней" (тот же текст, что на сайте и на iPhone). */
    public static String vacationText(Vacation v) {
        if (v == null) return "";
        String name = v.name.toLowerCase(Locale.ROOT);
        if (v.days == 0) return "Дальше " + name;
        if (v.days == 1 && v.today) return "Сегодня последний учебный день, потом " + name;
        return "До каникул " + word(v.days, "учебный день", "учебных дня", "учебных дней");
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
        return word(n, "урок", "урока", "уроков");
    }

    /** Число со склонённым словом: word(5, "день", "дня", "дней") = "5 дней". */
    public static String word(int n, String one, String few, String many) {
        int a = n % 10, b = n % 100;
        if (a == 1 && b != 11) return n + " " + one;
        if (a >= 2 && a <= 4 && (b < 12 || b > 14)) return n + " " + few;
        return n + " " + many;
    }

    /** "11 января". */
    public static String dateText(LocalDate d) {
        String[] m = {"января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября",
                "октября", "ноября", "декабря"};
        return d.getDayOfMonth() + " " + m[d.getMonthValue() - 1];
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

    /** Момент «min минут от начала дня» по стенным часам: в день перевода часов звонок в 8:00 остаётся в 8:00. */
    static long at(LocalDate date, int minutes, ZoneId zone) {
        return date.atStartOfDay().plusMinutes(minutes).atZone(zone).toInstant().toEpochMilli();
    }
}
