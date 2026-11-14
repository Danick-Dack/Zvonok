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
        // все разрешения обязательные: выключенный пункт нужно включить, включённый не трогаем
        assertTrue(Perms.required(false, false));
        assertFalse(Perms.required(true, false));
        // открывали пункт раньше или нет, выключенный всё равно обязателен
        assertTrue(Perms.required(false, true));
        // непроверяемый пункт (автозапуск): обязателен, пока его ни разу не открывали
        assertTrue(Perms.required(null, false));
        assertFalse(Perms.required(null, true));
    }

    @Test
    public void releaseTag() {
        assertArrayEquals(new String[]{"6.3", "91"}, Updates.parseTag("v6.3-build-91"));
        // пробные версии и чужие строки обновлением не считаются
        assertNull(Updates.parseTag("v6.3-beta-build-91"));
        assertNull(Updates.parseTag("6.3"));
        assertNull(Updates.parseTag(null));
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
