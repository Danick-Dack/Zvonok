package app.urok7b;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Расписание 7Б (2 четверть 2026–2027) и расчёт текущего состояния. */
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
            this.room = room;
        }

        public String startText() { return hm(start); }
        public String endText() { return hm(end); }

        /** "каб. 24/35" или "спортзал". */
        public String roomText() {
            if (room == null || room.isEmpty()) return "";
            return Character.isDigit(room.charAt(0)) ? "каб. " + room : room;
        }
    }

    public enum Kind { BEFORE, LESSON, BREAK, AFTER, DAY_OFF }

    public static final class Status {
        public Kind kind;
        public Lesson current;       // урок сейчас (LESSON) или предыдущий урок (BREAK)
        public Lesson next;          // следующий урок
        public String nextDayLabel;  // "Завтра", "Понедельник"... (только AFTER / DAY_OFF)
        public int todayCount;       // сколько уроков сегодня
        public int nextDayCount;     // сколько уроков в тот день
        public String nextDayEnd;    // во сколько заканчиваются
        public long startMillis;     // начало текущего отрезка (для прогресса)
        public long targetMillis;    // когда звонок / начало уроков
        public boolean notify;       // показывать уведомление
        public long windowStartMillis; // когда начнётся окно уведомления (BEFORE / AFTER / DAY_OFF)
    }

    /** За сколько минут до первого урока показывать уведомление. */
    public static final int LEAD_MINUTES = 60;

    private static final int[][] BELLS = {
            {8 * 60, 8 * 60 + 45},
            {8 * 60 + 50, 9 * 60 + 35},
            {9 * 60 + 45, 10 * 60 + 30},
            {10 * 60 + 50, 11 * 60 + 35},
            {11 * 60 + 55, 12 * 60 + 40},
            {12 * 60 + 50, 13 * 60 + 35},
            {13 * 60 + 45, 14 * 60 + 30},
            {14 * 60 + 40, 15 * 60 + 25},
    };

    private static final String SPORT = "спортзал";

    /** "Предмет|кабинет" по номеру урока; null — урока нет. */
    private static final Map<DayOfWeek, String[]> WEEK = new EnumMap<>(DayOfWeek.class);

    static {
        WEEK.put(DayOfWeek.MONDAY, new String[]{
                "Классный час|40",
                "Казахский яз. и лит.|42/21",
                "История Казахстана|46",
                "Алгебра|24/35",
                "Математика|24/35",
                "Физкультура|" + SPORT,
                "Физкультура|" + SPORT,
                "Пр. физика|48/73",
        });
        WEEK.put(DayOfWeek.TUESDAY, new String[]{
                "Геометрия|24/35",
                "Английский язык|44/47",
                "Физика|48/49",
                "Физика|48/49",
                "Программирование|56/57",
                "Биология|25",
                "Худ. труд|11/1",
        });
        WEEK.put(DayOfWeek.WEDNESDAY, new String[]{
                "Алгебра|24/35",
                "Алгебра|24/35",
                "Английский язык|45/47",
                "География|54",
                "Физкультура|" + SPORT,
                "Русский язык|46",
        });
        WEEK.put(DayOfWeek.THURSDAY, new String[]{
                "Казахский яз. и лит.|42/21",
                "Алгебра|24/35",
                "Русский язык|46",
                "Русская литература|46",
                "Физика|48/49",
                "Физика|48/49",
                "Всемирная история|54",
        });
        WEEK.put(DayOfWeek.FRIDAY, new String[]{
                "Геометрия|24/35",
                "Геометрия|24/35",
                "Казахский яз. и лит.|42/21",
                "Английский язык|25/47",
                "Химия|40",
                "Информатика|56/57",
                "Русская литература|46",
        });
    }

    private Schedule() {}

    public static List<Lesson> lessons(DayOfWeek day) {
        String[] raw = WEEK.get(day);
        if (raw == null) return Collections.emptyList();
        List<Lesson> out = new ArrayList<>();
        for (int i = 0; i < raw.length && i < BELLS.length; i++) {
            if (raw[i] == null) continue;
            String[] p = raw[i].split("\\|", 2);
            out.add(new Lesson(i + 1, BELLS[i][0], BELLS[i][1], p[0], p.length > 1 ? p[1] : ""));
        }
        return out;
    }

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
                    s.notify = true;
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
            s.targetMillis = at(date, first.start, zone);
            s.windowStartMillis = at(date, first.start - LEAD_MINUTES, zone);
            break;
        }
        s.notify = false;
        return s;
    }

    public static String dayName(DayOfWeek d) {
        switch (d) {
            case MONDAY: return "Понедельник";
            case TUESDAY: return "Вторник";
            case WEDNESDAY: return "Среда";
            case THURSDAY: return "Четверг";
            case FRIDAY: return "Пятница";
            case SATURDAY: return "Суббота";
            default: return "Воскресенье";
        }
    }

    /** "12 мин", "1 ч 05 мин". Округляет вверх до минуты. */
    public static String fmtLeft(long ms) {
        long min = ms <= 0 ? 0 : (ms + 59_999) / 60_000;
        if (min < 60) return min + " мин";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " ч" : h + " ч " + (m < 10 ? "0" : "") + m + " мин";
    }

    public static String hm(int minutes) {
        int m = minutes % 60;
        return (minutes / 60) + ":" + (m < 10 ? "0" : "") + m;
    }

    private static long at(LocalDate date, int minutes, ZoneId zone) {
        return date.atStartOfDay(zone).plusMinutes(minutes).toInstant().toEpochMilli();
    }
}
