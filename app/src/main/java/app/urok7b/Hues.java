package app.urok7b;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Цвета предметов, как на сайте: у каждого предмета свой оттенок, урок узнаётся с одного взгляда.
 * Математика синяя, физика фиолетовая, химия бирюзовая, языки тёплые. Для своих предметов
 * (своё расписание, замены) оттенок подбирается по похожему названию или по буквам.
 */
final class Hues {

    // яркий на светлом, текст на светлом, яркий на тёмном, текст на тёмном (контраст текста не ниже 4,5:1)
    static final int[][] C = {   // GoldenTest сверяет с HUES из tools/build_site.py
            {0xFFFF3B30, 0xFFC40013, 0xFFFF453A, 0xFFFF6961}, // 0 red
            {0xFFFF9500, 0xFFB83D00, 0xFFFF9F0A, 0xFFFFB340}, // 1 orange
            {0xFFFFCC00, 0xFF8F5A00, 0xFFFFD60A, 0xFFFFD426}, // 2 yellow
            {0xFF34C759, 0xFF1F7A36, 0xFF30D158, 0xFF30DB5B}, // 3 green
            {0xFF00C7BE, 0xFF0B7570, 0xFF63E6E2, 0xFF66D4CF}, // 4 mint
            {0xFF30B0C7, 0xFF006F83, 0xFF40CBE0, 0xFF5DE6FF}, // 5 teal
            {0xFF32ADE6, 0xFF006A9A, 0xFF64D2FF, 0xFF70D7FF}, // 6 cyan
            {0xFF007AFF, 0xFF0058D6, 0xFF0A84FF, 0xFF409CFF}, // 7 blue
            {0xFF5856D6, 0xFF3D3BB8, 0xFF5E5CE6, 0xFF8B89FF}, // 8 indigo
            {0xFFAF52DE, 0xFF8944AB, 0xFFBF5AF2, 0xFFDA8FFF}, // 9 purple
            {0xFFFF2D55, 0xFFBA0E40, 0xFFFF375F, 0xFFFF6482}, // 10 pink
            {0xFFA2845E, 0xFF765C3E, 0xFFAC8E68, 0xFFC4A27A}, // 11 brown
            {0xFF8E8E93, 0xFF636366, 0xFF8E8E93, 0xFFAEAEB2}, // 12 gray
    };
    private static final int RED = 0, ORANGE = 1, YELLOW = 2, GREEN = 3, MINT = 4, TEAL = 5, CYAN = 6, BLUE = 7,
            INDIGO = 8, PURPLE = 9, PINK = 10, BROWN = 11, GRAY = 12;
    /** Для неизвестных предметов: все оттенки, кроме серого. */
    private static final int[] HASH = {BLUE, PURPLE, YELLOW, RED, CYAN, BROWN, MINT, GREEN, TEAL, INDIGO, ORANGE, PINK};

    /** Код предмета из schedule.json → оттенок. Тот же список, что в tools/build_site.py. */
    private static final Map<String, Integer> CODE = new HashMap<>();
    static {
        String[][] m = {{"alg", "7"}, {"geom", "7"}, {"mat", "7"}, {"fiz", "9"}, {"prf", "9"}, {"kaz", "2"}, {"rus", "0"},
                {"rlit", "0"}, {"eng", "6"}, {"istk", "11"}, {"vist", "11"}, {"geo", "4"}, {"bio", "3"}, {"him", "5"},
                {"inf", "8"}, {"prog", "8"}, {"fk", "1"}, {"hud", "10"}, {"kl", "12"}, {"prav", "11"}, {"nvp", "3"}, {"avto", "1"}};
        for (String[] p : m) CODE.put(p[0], Integer.parseInt(p[1]));
    }

    private static final Map<String, Integer> BY_NAME = new LinkedHashMap<>(); // в порядке предметов в schedule.json, как на сайте

    private Hues() {}

    /** Расписание загружено заново: запомнить, как называются предметы. */
    static void names(Map<String, String> codeToName) {
        BY_NAME.clear();
        for (Map.Entry<String, String> e : codeToName.entrySet()) {
            Integer h = CODE.get(e.getKey());
            BY_NAME.put(e.getValue(), h == null ? GRAY : h);
        }
    }

    static int index(String subject) {
        String s = subject == null ? "" : subject.trim();
        if (s.isEmpty()) return -1;
        Integer h = BY_NAME.get(s);
        if (h != null) return h;
        String low = s.toLowerCase(Locale.ROOT);
        String head = low.length() > 5 ? low.substring(0, 5) : low;
        for (Map.Entry<String, Integer> e : BY_NAME.entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).startsWith(head)) return e.getValue();
        }
        long x = 0;
        for (int i = 0; i < low.length(); i++) x = (x * 31 + low.charAt(i)) & 0xFFFFFFFFL;
        return HASH[(int) (x % HASH.length)];
    }

    /** Яркий цвет оттенка по номеру (значки разделов в «Ещё»). */
    static int color(int hue, boolean dark) {
        return C[hue][dark ? 2 : 0];
    }

    /** Яркий цвет предмета: кольцо, точки, полоски. Без предмета — главный цвет. */
    static int vivid(String subject, boolean dark) {
        int i = index(subject);
        return i < 0 ? Ui.ACCENT : C[i][dark ? 2 : 0];
    }

    /** Цвет для текста и заливки плашки (контрастный). */
    static int strong(String subject, boolean dark) {
        int i = index(subject);
        return i < 0 ? Ui.ACCENT : C[i][dark ? 3 : 1];
    }

    /** Мягкий фон плашки: яркий цвет с прозрачностью. */
    static int soft(String subject, boolean dark) {
        return (vivid(subject, dark) & 0x00FFFFFF) | (dark ? 0x2E000000 : 0x24000000);
    }

    /** Текст на заливке strong(): белый на светлой теме, почти чёрный на тёмной. */
    static int onStrong(boolean dark) {
        return dark ? 0xFF0B0B0C : 0xFFFFFFFF;
    }
}
