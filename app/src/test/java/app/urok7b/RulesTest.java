package app.urok7b;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Правила экранов: какие разрешения обязательны (Perms.required) и какой звонок в своём расписании неверный (badBell). */
public class RulesTest {

    @Test
    public void requiredPermissions() {
        // обычный пункт: выключен — обязателен, включён — нет
        assertTrue(Perms.required("alarm", false, true, false, false));
        assertFalse(Perms.required("alarm", true, true, false, false));
        // батарея обязательна, даже если уведомление выключено
        assertTrue(Perms.required("battery", false, false, false, false));
        // непроверяемый пункт (автозапуск): обязателен, пока его ни разу не открывали
        assertTrue(Perms.required("autostart", null, true, false, false));
        assertFalse(Perms.required("autostart", null, true, false, true));
        // таймер в строке состояния Android 16 по желанию
        assertFalse(Perms.required("live", false, true, false, false));
        // уведомление с таймером выключено в «Ещё»: его каналы и сами уведомления не требуем...
        assertFalse(Perms.required("ch_live", false, false, false, false));
        assertFalse(Perms.required("ch_lock", false, false, false, false));
        assertFalse(Perms.required("notif", false, false, false, false));
        // ...но вибрации перед уроком уведомления нужны
        assertTrue(Perms.required("notif", false, false, true, false));
        assertTrue(Perms.required("ch_buzz", false, false, true, false));
        // уведомление с таймером включено: канал и экран блокировки обязательны
        assertTrue(Perms.required("ch_live", false, true, false, false));
    }

    private static String[][][] week(String... subjects) {
        String[][][] w = new String[6][9][2];
        for (String[][] d : w) for (String[] s : d) { s[0] = ""; s[1] = ""; }
        for (int i = 0; i < subjects.length; i++) w[0][i][0] = subjects[i];
        return w;
    }

    private static String[][] bells(String... times) {
        String[][] b = new String[9][2];
        for (String[] x : b) { x[0] = ""; x[1] = ""; }
        for (int i = 0; i < times.length; i++) b[i / 2][i % 2] = times[i];
        return b;
    }

    @Test
    public void bellsInOwnSchedule() {
        // всё верно, а звонки пустого 3 урока кривые: это не ошибка
        assertNull(EditorActivity.badBell(bells("8:00", "8:45", "8:50", "9:35", "bad", ""), week("Алгебра", "Физика")));
        // начало не разобрать: ошибка у начала 2 урока
        assertArrayEquals(new int[]{1, 0}, EditorActivity.badBell(bells("8:00", "8:45", "9:7x", "9:35"), week("Алгебра", "Физика")));
        // конец раньше начала: ошибка у конца
        assertArrayEquals(new int[]{0, 1}, EditorActivity.badBell(bells("8:30", "8:00"), week("Алгебра")));
        // пустой конец: тоже ошибка у конца
        assertArrayEquals(new int[]{0, 1}, EditorActivity.badBell(bells("8:30", ""), week("Алгебра")));
        // точка вместо двоеточия разбирается, как и на сайте
        assertNull(EditorActivity.badBell(bells("8.00", "8.45"), week("Алгебра")));
        // предмет из одних пробелов: урок пустой
        assertNull(EditorActivity.badBell(bells("", ""), week("   ")));
        // 2 урок начинается раньше, чем кончился 1: ошибка у начала 2 урока
        assertArrayEquals(new int[]{1, 2}, EditorActivity.badBell(bells("8:00", "9:00", "8:30", "9:15"), week("Алгебра", "Физика")));
        // между уроками пустой: сравнивается с последним уроком с предметом
        assertNull(EditorActivity.badBell(bells("8:00", "8:45", "8:10", "8:20", "8:50", "9:35"), week("Алгебра", "", "Физика")));
    }
}
