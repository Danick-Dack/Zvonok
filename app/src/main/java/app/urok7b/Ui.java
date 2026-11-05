package app.urok7b;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Общие элементы интерфейса и цвета приложения. Тема как на сайте: светлая, тёмная или как в системе,
 * и главный цвет на выбор. В тёмной теме фон чисто чёрный: на OLED-экранах так меньше расход заряда.
 */
final class Ui {

    static final String KEY_SCHEME = "app_scheme"; // auto | light | dark
    static final String KEY_ACCENT = "app_accent"; // blue, indigo, ...

    /** Главный цвет: ключ, название, цвет в светлой теме, цвет в тёмной (как на сайте). */
    static final String[][] ACCENTS = {
            {"blue", "Синий", "0062DA", "409CFF"},
            {"indigo", "Индиго", "3D3BB8", "8B89FF"},
            {"purple", "Фиолетовый", "8944AB", "DA8FFF"},
            {"pink", "Розовый", "C70F45", "FF6482"},
            {"red", "Красный", "D70015", "FF6961"},
            {"orange", "Оранжевый", "B83D00", "FFB340"},
            {"green", "Зелёный", "1F7A36", "30DB5B"},
            {"mint", "Мятный", "0B7570", "66D4CF"},
            {"mono", "Без цвета", "141414", "F3F3F1"},
    };

    static boolean DARK = true;
    static int BG, CARD, RAISED, FIELD, LINE, TEXT, SOFT, MUTED, FAINT, TRACK;
    static int ACCENT, ON_ACCENT, OK, WARN, BAD;
    /** Меняется при каждой смене оформления: экраны по нему понимают, что пора перерисоваться. */
    static int version;

    static {
        set(true, ACCENTS[0]);
    }

    private Ui() {}

    private static void set(boolean dark, String[] accent) {
        DARK = dark;
        if (dark) {
            BG = 0xFF000000; CARD = 0xFF101012; RAISED = 0xFF1C1C1E; FIELD = 0xFF161618; LINE = 0xFF232326;
            TEXT = 0xFFF3F3F1; SOFT = 0xFFC6C6C2; MUTED = 0xFF8F8F8A; FAINT = 0xFF4A4A4F; TRACK = 0xFF1C1C1F;
            OK = 0xFF30DB5B; WARN = 0xFFFFD426; BAD = 0xFFFF6961; ON_ACCENT = 0xFF0B0B0C;
        } else {
            BG = 0xFFF4F4F1; CARD = 0xFFFFFFFF; RAISED = 0xFFECECE8; FIELD = 0xFFECECE8; LINE = 0xFFE3E3DE;
            TEXT = 0xFF141414; SOFT = 0xFF3B3B38; MUTED = 0xFF676762; FAINT = 0xFFB4B4AE; TRACK = 0xFFE6E6E1;
            OK = 0xFF1F7A36; WARN = 0xFF8F5A00; BAD = 0xFFC40013; ON_ACCENT = 0xFFFFFFFF;
        }
        ACCENT = 0xFF000000 | Integer.parseInt(accent[dark ? 3 : 2], 16);
        version++;
    }

    static String scheme(Context c) {
        return Updater.prefs(c).getString(KEY_SCHEME, "auto");
    }

    static String accentKey(Context c) {
        return Updater.prefs(c).getString(KEY_ACCENT, ACCENTS[0][0]);
    }

    /** Цвета по настройкам и теме системы. Дёшево: вызывается в начале каждого экрана. */
    static void palette(Context c) {
        String s = scheme(c);
        boolean dark = "dark".equals(s) || (!"light".equals(s)
                && (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        String key = accentKey(c);
        String[] acc = ACCENTS[0];
        for (String[] a : ACCENTS) if (a[0].equals(key)) acc = a;
        int before = ACCENT ^ (DARK ? 1 : 0);
        int nowAcc = 0xFF000000 | Integer.parseInt(acc[dark ? 3 : 2], 16);
        if (dark != DARK || nowAcc != ACCENT || before == 0) set(dark, acc);
    }

    /** Цвета окна: фон без белой вспышки, строка состояния и навигации под тему. */
    static void apply(Activity a) {
        palette(a);
        Window w = a.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(BG));
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        View decor = w.getDecorView();
        int f = decor.getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        decor.setSystemUiVisibility(DARK ? f & ~light : f | light);
    }

    /** Тема системных окон (выбор класса, вопросы) под цвета приложения. */
    static int dialogTheme() {
        return DARK ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Экран со стрелкой «назад», заголовком и подзаголовком. Возвращает колонку для содержимого. */
    static LinearLayout screen(Activity a, String title, String sub) {
        apply(a);
        // экран пересобирается целиком (после «Проверить обновления», выбора класса и т.п.): сохраняем прокрутку
        android.view.ViewGroup content = a.findViewById(android.R.id.content);
        android.view.View prev = content != null && content.getChildCount() > 0 ? content.getChildAt(0) : null;
        final int keepY = prev instanceof ScrollView ? prev.getScrollY() : 0;
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout root = vertical(a);
        root.setPadding(dp(a, 18), dp(a, 18), dp(a, 18), dp(a, 40));
        sv.addView(root);

        TextView back = text(a, "←", 24, TEXT, null);
        back.setPadding(0, 0, dp(a, 16), dp(a, 6));
        back.setOnClickListener(v -> a.finish());
        back.setContentDescription("Назад");
        root.addView(back);
        root.addView(text(a, title, 28, TEXT, "sans-serif-medium"));
        if (sub != null) {
            TextView s = text(a, sub, 13, MUTED, null);
            s.setPadding(0, dp(a, 4), 0, 0);
            root.addView(s);
        }
        a.setContentView(sv);
        if (keepY > 0) {
            // прокрутить до первого кадра, иначе на миг видно верх экрана
            sv.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    sv.getViewTreeObserver().removeOnPreDrawListener(this);
                    sv.scrollTo(0, keepY);
                    return false; // этот кадр пропускаем, следующий уже на месте
                }
            });
        }
        return root;
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = vertical(c);
        l.setBackground(round(c, CARD, 24, LINE));
        l.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        return l;
    }

    static TextView text(Context c, String s, float sp, int color, String family) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (family != null) t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return t;
    }

    static TextView caps(Context c, String s) {
        TextView t = text(c, s, 10, MUTED, "sans-serif-medium");
        t.setLetterSpacing(0.16f);
        t.setPadding(dp(c, 4), dp(c, 26), 0, dp(c, 10));
        return t;
    }

    static GradientDrawable round(Context c, int color, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, radiusDp));
        g.setColor(color);
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    static LinearLayout.LayoutParams mw(Context c, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, top);
        return lp;
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    /** Кнопка: главная в главном цвете, остальные спокойные. */
    static TextView button(Context c, String s, boolean primary, View.OnClickListener l) {
        TextView b = text(c, s, 15, primary ? ON_ACCENT : TEXT, "sans-serif-medium");
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        b.setBackground(primary ? round(c, ACCENT, 16, 0) : round(c, RAISED, 16, LINE));
        b.setOnClickListener(l);
        b.setLayoutParams(mw(c, 10));
        return b;
    }

    /** Маленькая «таблетка» для выбора. */
    static TextView pill(Context c, String s, boolean selected, View.OnClickListener l) {
        TextView t = text(c, s, 14, selected ? ON_ACCENT : TEXT, "sans-serif-medium");
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        t.setBackground(selected ? round(c, ACCENT, 14, 0) : round(c, FIELD, 14, LINE));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c, 8);
        lp.bottomMargin = dp(c, 8);
        t.setLayoutParams(lp);
        return t;
    }

    static EditText field(Context c, String value, String hint) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setBackground(round(c, FIELD, 12, LINE));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        return e;
    }

    static LinearLayout twoLines(Context c, String title, String sub) {
        LinearLayout t = vertical(c);
        t.addView(text(c, title, 15, TEXT, null));
        if (sub != null && !sub.isEmpty()) {
            TextView s = text(c, sub, 12, MUTED, null);
            s.setPadding(0, dp(c, 2), 0, 0);
            t.addView(s);
        }
        return t;
    }

    /** Строка настройки с переключателем (включённый зелёный, как в системе). */
    static LinearLayout switchRow(Context c, String title, String sub, boolean on, Switch.OnCheckedChangeListener l) {
        LinearLayout r = horizontal(c);
        r.setPadding(0, dp(c, 10), 0, dp(c, 10));
        LinearLayout t = twoLines(c, title, sub);
        t.setLayoutParams(weight(1));
        r.addView(t);
        Switch sw = new Switch(c);
        sw.setChecked(on);
        sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Color.WHITE, DARK ? 0xFF8A8A8A : 0xFFFFFFFF}));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{OK, DARK ? 0xFF3A3A3E : 0xFFCFCFCA}));
        sw.setOnCheckedChangeListener(l);
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        return r;
    }

    /** Строка-ссылка со стрелкой справа. */
    static LinearLayout linkRow(Context c, String title, String sub, String value, View.OnClickListener l) {
        LinearLayout r = horizontal(c);
        r.setPadding(0, dp(c, 12), 0, dp(c, 12));
        LinearLayout t = twoLines(c, title, sub);
        t.setLayoutParams(weight(1));
        r.addView(t);
        if (value != null) {
            TextView v = text(c, value, 14, SOFT, "sans-serif-medium");
            v.setPadding(dp(c, 10), 0, dp(c, 6), 0);
            r.addView(v);
        }
        r.addView(text(c, "›", 22, MUTED, null));
        r.setOnClickListener(l);
        return r;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    /** Плашка кабинета в цвете предмета: «сейчас» залита, остальные на мягком фоне. */
    static TextView room(Context c, String room, String subject, boolean now, float sp) {
        TextView t = text(c, room, sp, now ? Hues.onStrong(DARK) : Hues.strong(subject, DARK), "sans-serif-medium");
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setBackground(round(c, now ? Hues.strong(subject, DARK) : Hues.soft(subject, DARK), 10, 0));
        return t;
    }

    /** Выбор класса: таблетки по параллелям. После выбора вызывает done. */
    static void pickClass(Activity a, Runnable done) {
        String cur = Updater.prefs(a).getString(Schedule.KEY_CLASS, Schedule.DEFAULT_CLASS);
        LinearLayout box = vertical(a);
        box.setPadding(dp(a, 20), dp(a, 8), dp(a, 20), dp(a, 8));
        android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        String grade = null;
        LinearLayout row = null;
        for (String c : Schedule.classes(a)) {
            String g = c.replaceAll("\\D", "");
            if (!g.equals(grade)) {
                grade = g;
                TextView h = caps(a, g + " КЛАССЫ");
                h.setPadding(0, dp(a, 12), 0, dp(a, 6));
                box.addView(h);
                row = horizontal(a);
                box.addView(row);
            }
            row.addView(pill(a, c, c.equals(cur) && !Schedule.isCustom(), v -> {
                Updater.prefs(a).edit().putString(Schedule.KEY_CLASS, c).putBoolean(Schedule.KEY_USE_CUSTOM, false).apply();
                Schedule.invalidate();
                Updater.update(a);
                if (dlg[0] != null) dlg[0].dismiss();
                done.run();
            }));
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        dlg[0] = new android.app.AlertDialog.Builder(a, dialogTheme())
                .setTitle("Твой класс").setView(sv).setNegativeButton("Закрыть", null).show();
    }

    /** Оформление приложения: тема и главный цвет. После выбора вызывает done (экран перерисуется). */
    static void pickLook(Activity a, Runnable done) {
        LinearLayout box = vertical(a);
        box.setPadding(dp(a, 20), dp(a, 4), dp(a, 20), dp(a, 8));
        android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        box.addView(caps(a, "ТЕМА"));
        LinearLayout themes = horizontal(a);
        String[][] modes = {{"auto", "Как в системе"}, {"light", "Светлая"}, {"dark", "Тёмная"}};
        String curMode = scheme(a);
        for (String[] m : modes) {
            themes.addView(pill(a, m[1], m[0].equals(curMode), v -> {
                Updater.prefs(a).edit().putString(KEY_SCHEME, m[0]).apply();
                if (dlg[0] != null) dlg[0].dismiss();
                done.run();
            }));
        }
        box.addView(themes);
        box.addView(caps(a, "ГЛАВНЫЙ ЦВЕТ"));
        String curAcc = accentKey(a);
        View[] dots = new View[ACCENTS.length];
        for (int i = 0; i < ACCENTS.length; i++) {
            String[] acc = ACCENTS[i];
            LinearLayout cell = vertical(a);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, 0, dp(a, 6), dp(a, 10));
            View dot = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF000000 | Integer.parseInt(acc[DARK ? 3 : 2], 16));
            g.setStroke(dp(a, acc[0].equals(curAcc) ? 3 : 1), acc[0].equals(curAcc) ? TEXT : LINE);
            dot.setBackground(g);
            cell.addView(dot, new LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)));
            TextView name = text(a, acc[1], 11, SOFT, null);
            name.setGravity(Gravity.CENTER);
            cell.addView(name, new LinearLayout.LayoutParams(dp(a, 74), ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setContentDescription("Цвет: " + acc[1]);
            cell.setOnClickListener(v -> {
                Updater.prefs(a).edit().putString(KEY_ACCENT, acc[0]).apply();
                if (dlg[0] != null) dlg[0].dismiss();
                done.run();
            });
            dots[i] = cell;
        }
        box.addView(wrapRows(a, dots, 4));
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        dlg[0] = new android.app.AlertDialog.Builder(a, dialogTheme())
                .setTitle("Оформление").setView(sv).setNegativeButton("Закрыть", null).show();
    }

    /** Ряд таблеток с переносом строк (простая «сетка»). */
    static LinearLayout wrapRows(Context c, View[] items, int perRow) {
        LinearLayout col = vertical(c);
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % perRow == 0) {
                row = horizontal(c);
                col.addView(row);
            }
            row.addView(items[i]);
        }
        return col;
    }
}
