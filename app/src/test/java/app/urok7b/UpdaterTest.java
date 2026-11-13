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
        return at("7Б", month, day, hour, min);
    }

    /** Данные эталона (расписание и замены, в том числе кривые) для класса cls на момент 2026-month-day hour:min. */
    private static Schedule.Status at(String cls, int month, int day, int hour, int min) throws Exception {
        Schedule.init(GoldenTest.G.getJSONObject("schedule").toString(), GoldenTest.G.getJSONObject("changes").toString(), cls, null);
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

    /** Вторник 6:00: до уроков больше часа, отсчёта ещё нет. */
    @Test
    public void earlyMorning() throws Exception {
        Updater.Display d = Updater.describe(at(10, 6, 0), ms(10, 6, 0));
        assertEquals(Schedule.Kind.BEFORE, d.kind);
        assertTrue("рано: без отсчёта", !d.timer);
    }

    /** Понедельник 15:00: последний, 8 урок, в уведомлении «домой в 15:25». */
    @Test
    public void lastLesson() throws Exception {
        Updater.Display d = Updater.describe(at(9, 15, 0), ms(9, 15, 0));
        assertEquals(Schedule.Kind.LESSON, d.kind);
        assertTrue("последний урок: " + d.notifText, d.notifText.contains("домой в 15:25"));
    }

    /** Четверг 12 ноября, сокращённые уроки по заменам: 1 урок 8:00–8:30, отсчёт до 8:30. */
    @Test
    public void shortBells() throws Exception {
        Updater.Display d = Updater.describe(at(12, 8, 20), ms(12, 8, 20));
        assertEquals(Schedule.Kind.LESSON, d.kind);
        assertEquals("звонок по сокращённому расписанию", ms(12, 8, 30), d.target);
    }

    /** Среда 11 ноября, 8А: по замене 3 урок (всемирная история) в спортзале; кривая замена «"4": "Физика"» отброшена, как на сайте. */
    @Test
    public void substitution() throws Exception {
        Updater.Display at3 = Updater.describe(at("8А", 11, 11, 10, 0), ms(11, 10, 0));
        assertEquals("Всемирная история", at3.title);
        assertEquals("спортзал", at3.room);
        Updater.Display at4 = Updater.describe(at("8А", 11, 11, 11, 0), ms(11, 11, 0));
        assertEquals("кривая замена не применилась", "Физкультура", at4.title);
    }

    /** 16 декабря, День Независимости: один выходной среди недели, без счётчика дней каникул. */
    @Test
    public void oneDayHoliday() throws Exception {
        long now = ZonedDateTime.of(2026, 12, 16, 10, 0, 0, 0, ALMATY).toInstant().toEpochMilli();
        Updater.Display d = Updater.describe(at(12, 16, 10, 0), now);
        assertTrue("праздник", d.holiday && !d.live);
        assertEquals("Выходной", d.title);
    }

    private static ZonedDateTime when(int month, int day, int hour, int min) {
        return ZonedDateTime.of(2026, month, day, hour, min, 0, 0, ALMATY);
    }

    private static Updater.Display full(String cls, int month, int day, int hour, int min) throws Exception {
        return Updater.describe(at(cls, month, day, hour, min), when(month, day, hour, min));
    }

    /** Вторник 24 ноября, уроки 7Б кончились в 14:30, выбран ШОР по физике (вт 14:30–16:30): таймер до его конца. */
    @Test
    public void clubAfterLessons() throws Exception {
        Plus.select("{\"shor\":[\"fiz7\"],\"clubs\":[{\"name\":\"Шахматы\",\"place\":\"каб. 12\",\"times\":[[1,\"17:00\",\"18:00\"]]}]}");
        try {
            Updater.Display d = full("7Б", 11, 24, 15, 0);
            long now = when(11, 24, 15, 0).toInstant().toEpochMilli(), end = when(11, 24, 16, 30).toInstant().toEpochMilli();
            assertEquals("по урокам это вечер", Schedule.Kind.AFTER, d.kind);
            assertTrue("занятие на экране, отсчёт идёт", d.extra && d.live && d.timer);
            assertEquals("ШОР · Физика", d.title);
            assertEquals("ИДЁТ", d.label);
            assertEquals("отсчёт до конца занятия", end, d.target);
            assertEquals("прошло 30 из 120 минут", 25, d.progress);
            assertEquals("ШОР · Физика: до конца 1 ч 30 мин", d.notifTitle);
            assertEquals("каб. 48/49", d.foot);
            assertEquals("во время занятия тик раз в минуту", (now / 60_000 + 1) * 60_000, Updater.nextStep(at("7Б", 11, 24, 15, 0), d, now, false));
            // между занятиями: до шахмат полчаса
            d = full("7Б", 11, 24, 16, 30);
            assertEquals("Шахматы", d.title);
            assertEquals("СЕГОДНЯ В 17:00", d.label);
            assertEquals("Шахматы: до начала 30 мин", d.notifTitle);
            assertEquals("до начала полосы нет", -1, d.progress);
            // всё кончилось: снова обычный вечер, уведомление «только в учебное время» может уйти
            d = full("7Б", 11, 24, 18, 0);
            assertTrue("занятий больше нет", !d.extra && !d.live && !d.timer);
            assertEquals("Отдыхай", d.title);
            // пока идут уроки, кружок экран не занимает
            d = full("7Б", 11, 24, 10, 0);
            assertTrue(!d.extra && d.kind == Schedule.Kind.LESSON);
        } finally {
            Plus.select(null);
        }
    }

    /** Суббота 21 ноября, ШОР по математике в 10:00: с утра обычный выходной, за полтора часа занятие выходит на экран. */
    @Test
    public void clubOnDayOff() throws Exception {
        Plus.select("{\"shor\":[\"mat1\"]}");
        try {
            Updater.Display d = full("7Б", 11, 21, 8, 0);
            assertTrue("за два часа ещё рано", !d.extra && !d.live);
            assertEquals("проснуться за полтора часа до начала", when(11, 21, 8, 30).toInstant().toEpochMilli(), d.wake);
            d = full("7Б", 11, 21, 8, 40);
            assertTrue(d.extra && d.live && d.timer);
            assertEquals("ШОР · Математика", d.title);
            assertEquals(when(11, 21, 10, 0).toInstant().toEpochMilli(), d.target);
            assertEquals(0, d.wake);
            // каникулы: занятий нет, хотя вторник и группа выбрана
            Plus.select("{\"shor\":[\"mat1\",\"fiz7\"]}");
            d = Updater.describe(at("7Б", 12, 29, 15, 0), when(12, 29, 15, 0));
            assertTrue("в каникулы кружков нет", !d.extra && d.holiday);
        } finally {
            Plus.select(null);
        }
    }

    /** СОР и СОЧ: вечером накануне строка в уведомлении, на самом уроке пометка в ярлыке. */
    @Test
    public void examsInNotification() throws Exception {
        Updater.Display d = full("7Б", 12, 17, 16, 0); // четверг после уроков, в пятницу СОЧ и два СОР
        assertEquals("Завтра СОЧ: казахский яз. и лит.. СОР: английский язык, информатика", d.note);
        assertEquals("вечером это нижняя строка", d.note, d.foot);
        assertEquals("и текст уведомления", d.note, d.notifText);
        d = full("7Б", 12, 21, 11, 0); // понедельник, 4 урок алгебра, сегодня по ней СОЧ
        assertEquals("Алгебра", d.title);
        assertTrue("пометка на уроке: " + d.label, d.label.endsWith(" · СОЧ"));
        assertEquals("Сегодня СОЧ: алгебра", d.note);
        assertTrue("остаток урока остаётся в заголовке", d.notifTitle.contains("осталось"));
        d = full("7Б", 12, 21, 9, 0); // 2 урок казахский: СОЧ не по нему
        assertTrue("у другого урока пометки нет: " + d.label, !d.label.contains("СОЧ"));
        d = full("7Б", 11, 10, 16, 0); // обычный вечер
        assertEquals("", d.note);
        String sig = full("7Б", 12, 17, 16, 0).sig;
        assertTrue("подпись меняется вместе со строкой про завтра", sig.endsWith("|" + full("7Б", 12, 17, 16, 0).note) && !sig.equals(full("7Б", 11, 10, 16, 0).sig));
    }

    /** Накануне праздника среди недели: «завтра в школу не идём»; про каникулы эта строка молчит. */
    @Test
    public void holidayTomorrow() throws Exception {
        assertEquals("Завтра День Независимости, в школу не идём", full("7Б", 12, 15, 16, 0).note);
        assertEquals("в пятницу перед каникулами строки нет", "", full("11Г", 12, 25, 16, 30).note);
        assertEquals("в сам праздник напоминаем про завтрашние работы", "Завтра СОЧ: русский язык. СОР: алгебра", full("7Б", 12, 16, 10, 0).note);
    }
}
