package app.urok7b;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Общие элементы интерфейса: чёрный минимализм, как на главном экране. */
final class Ui {

    static final int BG = Color.BLACK;
    static final int CARD = Color.parseColor("#0B0B0B");
    static final int RAISED = Color.parseColor("#161616");
    static final int FIELD = Color.parseColor("#121212");
    static final int LINE = Color.parseColor("#1E1E1E");
    static final int TEXT = Color.WHITE;
    static final int SOFT = Color.parseColor("#BDBDBD");
    static final int MUTED = Color.parseColor("#7A7A7A");
    static final int FAINT = Color.parseColor("#3A3A3A");

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Экран со стрелкой «назад», заголовком и подзаголовком. Возвращает колонку для содержимого. */
    static LinearLayout screen(Activity a, String title, String sub) {
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

    /** Кнопка: primary — белая, иначе тёмная. */
    static TextView button(Context c, String s, boolean primary, View.OnClickListener l) {
        TextView b = text(c, s, 15, primary ? BG : TEXT, "sans-serif-medium");
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        b.setBackground(primary ? round(c, TEXT, 16, 0) : round(c, RAISED, 16, LINE));
        b.setOnClickListener(l);
        b.setLayoutParams(mw(c, 10));
        return b;
    }

    /** Маленькая «таблетка» для выбора. */
    static TextView pill(Context c, String s, boolean selected, View.OnClickListener l) {
        TextView t = text(c, s, 14, selected ? BG : TEXT, "sans-serif-medium");
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        t.setBackground(selected ? round(c, TEXT, 14, 0) : round(c, FIELD, 14, LINE));
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
        e.setHintTextColor(FAINT);
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

    /** Строка настройки с переключателем. */
    static LinearLayout switchRow(Context c, String title, String sub, boolean on, Switch.OnCheckedChangeListener l) {
        LinearLayout r = horizontal(c);
        r.setPadding(0, dp(c, 10), 0, dp(c, 10));
        LinearLayout t = twoLines(c, title, sub);
        t.setLayoutParams(weight(1));
        r.addView(t);
        Switch sw = new Switch(c);
        sw.setChecked(on);
        sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{TEXT, Color.parseColor("#8A8A8A")}));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Color.parseColor("#6A6A6A"), Color.parseColor("#2A2A2A")}));
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
