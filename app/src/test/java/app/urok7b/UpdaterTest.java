package app.urok7b;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Что видно в уведомлении и виджетах (Updater.describe) и когда приложение просыпается в следующий раз (nextStep). */
public class UpdaterTest {

    private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");

    private static Schedule.Status at(int day, int hour, int min) throws Exception {
        return at(11, day, hour, min);
    }

    private static Schedule.Status at(int month, int day, int hour, int min) throws Exception {
        Schedule.init(GoldenTest.G.getJSONObject("schedule").toString(), GoldenTest.G.getJSONObject("changes").toString(), "7Б", null);
        return Schedule.compute(ZonedDateTime.of(2026, month, day, hour, min, 0, 0, ALMATY));
    }

    private static long ms(int day, int hour, int min) {
        return ZonedDateTime.of(2026, 11, day, hour, min, 0, 0, ALMATY).toInstant().toEpochMilli();
    }

    /** Вторник 10 ноября, 10:20: идёт 3 урок (9:45–10:30). */
    @Test
    public void lesson() throws Exception {
        Schedule.Status s = at(10, 10, 20);
        long now = ms(10, 10, 20);
        Updater.Display d = Updater.describe(s, now);
        assertEquals(Schedule.Kind.LESSON, d.kind);
        assertTrue("идёт отсчёт", d.live && d.timer);
        assertEquals("отсчёт до звонка", s.targetMillis, d.target);
        assertEquals("прошло 35 из 45 минут", 77, d.progress);
        assertTrue("в уведомлении остаток: " + d.notifTitle, d.notifTitle.contains("осталось"));

        // таймер текстом «N мин»: следующий тик в начале следующей минуты
        assertEquals(ms(10, 10, 21), Updater.nextStep(s, d, now, true));
        // с секундами (их рисует система): следующий тик там, где полоса шагнёт на 5% (80% = 10:21)
        long step = Updater.nextStep(s, d, now, false);
        assertEquals(ms(10, 10, 21), step);
        Updater.Display later = Updater.describe(at(10, 10, 21), step + 500);
        assertEquals("на тике полоса уже на следующем шаге", d.progress / 5 + 1, later.progress / 5);
    }

    /** Перемена 10:30–10:50 перед 4 уроком. */
    @Test
    public void breakTime() throws Exception {
        Schedule.Status s = at(10, 10, 38);
        Updater.Display d = Updater.describe(s, ms(10, 10, 38));
        assertEquals(Schedule.Kind.BREAK, d.kind);
        assertTrue(d.timer);
        assertEquals("перемена 20 минут, прошло 8", 40, d.progress);
        long step = Updater.nextStep(s, d, ms(10, 10, 38), false);
        assertTrue("тик позже сейчас и не позже звонка", step > ms(10, 10, 38) && step <= s.targetMillis);
    }

    /** Суббота: уроков нет, полосы нет. Если экран включён, тик раз в минуту (а не на звонке, которого нет). */
    @Test
    public void weekend() throws Exception {
        Schedule.Status s = at(14, 12, 0);
        Updater.Display d = Updater.describe(s, ms(14, 12, 0));
        assertTrue("в выходной не идёт отсчёт урока", !d.live);
        assertEquals(-1, d.progress);
        assertEquals(ms(14, 12, 1), Updater.nextStep(s, d, ms(14, 12, 0), false));
    }

    /** Вторник 7:30: последний час до уроков, отсчёт до первого звонка 8:00. */
    @Test
    public void beforeLessons() throws Exception {
        Schedule.Status s = at(10, 7, 30);
        Updater.Display d = Updater.describe(s, ms(10, 7, 30));
        assertEquals(Schedule.Kind.BEFORE, d.kind);
        assertTrue("за час до уроков идёт отсчёт", d.live && d.timer);
        assertEquals(ms(10, 8, 0), d.target);
    }

    /** Вторник 16:40: уроки кончились, отсчёта и полосы нет. */
    @Test
    public void afterLessons() throws Exception {
        Updater.Display d = Updater.describe(at(10, 16, 40), ms(10, 16, 40));
        assertEquals(Schedule.Kind.AFTER, d.kind);
        assertTrue("после уроков нет отсчёта", !d.live && !d.timer);
        assertEquals("УРОКИ ЗАКОНЧИЛИСЬ", d.label);
        assertEquals(-1, d.progress);
    }

    /** 28 декабря: зимние каникулы, крупно сколько дней до уроков (11 января). */
    @Test
    public void holidays() throws Exception {
        long now = ZonedDateTime.of(2026, 12, 28, 10, 0, 0, 0, ALMATY).toInstant().toEpochMilli();
        Updater.Display d = Updater.describe(at(12, 28, 10, 0), now);
        assertTrue("каникулы", d.holiday && !d.live);
        assertEquals("Каникулы", d.title);
        assertEquals("до уроков 14 дней", "14", d.big);
    }
}
