package app.urok7b;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * Оформление виджетов и уведомления (сам экран приложения не меняется).
 * Код темы общий с сайтом: "ZV1;bg=000000;ac=FFFFFF;op=100;r=26;s=1;p=1;n=1;d=1".
 */
public final class Theme {

    static final String KEY = "theme";

    public int bg = Color.BLACK;
    public int accent = Color.WHITE;
    public int opacity = 100;   // 30..100, прозрачность фона виджетов
    public int radius = 26;     // 12 / 26 / 36
    public boolean seconds = true;  // живой таймер с секундами (иначе — минуты, экономнее)
    public boolean progress = true;
    public boolean next = true;
    public boolean dots = true;
    public boolean subjectColor = false; // кольцо, полоса и кабинет в цвете текущего урока
    public int textColor = 0;            // свой цвет текста; 0 — сам по фону

    public static final String[][] PRESETS = {
            // название, фон, акцент
            {"Чёрный", "000000", "FFFFFF"},
            {"Графит", "1C1C1E", "FFFFFF"},
            {"Полночь", "0B1220", "60A5FA"},
            {"Мята", "04140F", "5EEAD4"},
            {"Лаванда", "120D1F", "A78BFA"},
            {"Бордо", "1A0A0E", "FB7185"},
            {"Песок", "F4EFE6", "1F1F1F"},
            {"Белый", "FFFFFF", "000000"},
    };

    public static final String[] ACCENTS = {"FFFFFF", "5EEAD4", "60A5FA", "A78BFA", "F472B6",
            "FBBF24", "A3E635", "FB7185", "000000"};

    public static Theme load(Context ctx) {
        Theme t = decode(Updater.prefs(ctx).getString(KEY, null));
        return t == null ? new Theme() : t;
    }

    public void save(Context ctx) {
        Updater.prefs(ctx).edit().putString(KEY, encode()).apply();
    }

    public String encode() {
        return "ZV1;bg=" + hex(bg) + ";ac=" + hex(accent) + ";op=" + opacity + ";r=" + radius
                + ";s=" + b(seconds) + ";p=" + b(progress) + ";n=" + b(next) + ";d=" + b(dots)
                + ";sc=" + b(subjectColor) + (textColor != 0 ? ";tx=" + hex(textColor) : "");
    }

    /** Разбор кода темы. Всё, что не распознано, остаётся по умолчанию; мусор → null. */
    public static Theme decode(String code) {
        if (code == null) return null;
        code = code.trim();
        if (!code.startsWith("ZV1") || code.length() > 200) return null;
        Theme t = new Theme();
        for (String part : code.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq).trim(), v = part.substring(eq + 1).trim();
            try {
                switch (k) {
                    case "bg": t.bg = color(v, t.bg); break;
                    case "ac": t.accent = color(v, t.accent); break;
                    case "op": t.opacity = clamp(Integer.parseInt(v), 30, 100); break;
                    case "r": t.radius = clamp(Integer.parseInt(v), 0, 40); break;
                    case "s": t.seconds = !"0".equals(v); break;
                    case "p": t.progress = !"0".equals(v); break;
                    case "n": t.next = !"0".equals(v); break;
                    case "d": t.dots = !"0".equals(v); break;
                    case "sc": t.subjectColor = "1".equals(v); break;
                    case "tx": t.textColor = color(v, 0); break;
                    default: break;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return t;
    }

    // ---------- цвета ----------

    public boolean light() { return lum(bg) > 0.5; }
    public int text() { return textColor != 0 ? textColor : light() ? 0xFF111111 : 0xFFFFFFFF; }
    public int soft() { return textColor != 0 ? (textColor & 0x00FFFFFF) | 0xD9000000 : light() ? 0xFF3A3A3A : 0xFFD0D0D0; }
    public int muted() { return textColor != 0 ? (textColor & 0x00FFFFFF) | 0x99000000 : light() ? 0xFF6B6B6B : 0xFF7E7E7E; }

    /** Для показа сейчас: при «цвете урока» акцент берётся у текущего (на перемене у следующего) предмета. */
    public Theme forDisplay(Updater.Display d) {
        if (!subjectColor || d == null) return this;
        String subj = d.kind == Schedule.Kind.LESSON ? d.title : d.nextSubject;
        if (subj == null || subj.isEmpty() || Hues.index(subj) < 0) return this;
        Theme t = decode(encode());
        if (t == null) return this;
        t.accent = Hues.vivid(subj, !light());
        return t;
    }
    public int faint() { return light() ? 0x33000000 : 0xFF2A2A2A; }
    public int chip() { return light() ? 0x14000000 : 0xFF1E1E1E; }
    public int track() { return light() ? 0x22000000 : 0xFF262626; }
    public int onAccent() { return lum(accent) > 0.5 ? 0xFF000000 : 0xFFFFFFFF; }
    /** Белый акцент на белом фоне не виден — подменяем текстом. */
    public int accentVisible() { return Math.abs(lum(accent) - lum(bg)) < 0.25 ? text() : accent; }

    public int bgWithAlpha() {
        return (Math.round(opacity * 2.55f) << 24) | (bg & 0x00FFFFFF);
    }

    public int shapeRes() {
        if (radius <= 16) return R.drawable.shape_r12;
        if (radius >= 32) return R.drawable.shape_r36;
        return R.drawable.shape_r26;
    }

    // ---------- применение к RemoteViews ----------

    void textColor(RemoteViews rv, int color, int... ids) {
        for (int id : ids) rv.setTextColor(id, color);
    }

    /** Фон плашки кабинета (Android 12+, на старых — стандартный). */
    void chip(RemoteViews rv, int id, boolean now) {
        if (Build.VERSION.SDK_INT >= 31) {
            int c = now ? accentVisible() : chip();
            rv.setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(c));
            rv.setTextColor(id, now ? (lum(c) > 0.5 ? 0xFF000000 : 0xFFFFFFFF) : text());
        } else {
            // старые Android не умеют перекрашивать фон: белая и тёмная плашки как есть
            rv.setTextColor(id, now ? 0xFF000000 : 0xFFFFFFFF);
        }
    }

    void progressColors(RemoteViews rv, int id) {
        if (Build.VERSION.SDK_INT >= 31) {
            rv.setColorStateList(id, "setProgressTintList", ColorStateList.valueOf(accentVisible()));
            rv.setColorStateList(id, "setProgressBackgroundTintList", ColorStateList.valueOf(track()));
        }
    }

    // ---------- helpers ----------

    static double lum(int c) {
        return (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0;
    }

    static String hex(int c) {
        return String.format("%06X", c & 0xFFFFFF);
    }

    static int color(String hex, int def) {
        hex = hex.replace("#", "");
        if (!hex.matches("[0-9a-fA-F]{6}")) return def;
        return 0xFF000000 | Integer.parseInt(hex, 16);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String b(boolean v) {
        return v ? "1" : "0";
    }
}
