package app.urok7b;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * «Слова», как на сайте: угадай слово из 5 букв за 6 попыток. Слово дня одно на весь лицей (тот же список data/words.txt
 * и тот же счёт дней), дальше случайные. Работает без интернета, счёт остаётся на телефоне.
 */
final class Words {

    private static final String[] KEYS = {"йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю"};
    private static final String[] MARKS = {"нет", "есть, но не тут", "на месте"};
    private static final String KEY_DAY = "words_day", KEY_ROWS = "words_rows", KEY_WON = "words_won", ENTER = "enter", BACK = "back";

    private final Activity a;
    private final String[] words;
    private final LinearLayout[] rows = new LinearLayout[6];
    private final TextView[][] cells = new TextView[6][5];
    private final Map<String, TextView> keys = new HashMap<>();
    private final List<String> tries = new ArrayList<>();
    private final TextView msg, more;
    private final LinearLayout box;
    private String word, cur = "";
    private boolean daily;

    static void show(Activity a) {
        String[] words;
        try {
            words = Ui.asset(a, "words.txt").trim().split("\\s+");
        } catch (java.io.IOException e) {
            return;
        }
        ScrollView scroll = new ScrollView(a); // экран набок: поле и клавиши не помещаются по высоте
        scroll.addView(new Words(a, words).box);
        new AlertDialog.Builder(a, Ui.dialogTheme()).setTitle("Слова").setView(scroll).setNegativeButton("Закрыть", null).show();
    }

    private Words(Activity a, String[] words) {
        this.a = a;
        this.words = words;
        box = Ui.vertical(a);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), 0);
        for (int r = 0; r < 6; r++) {
            rows[r] = Ui.horizontal(a);
            rows[r].setFocusable(true);
            for (int i = 0; i < 5; i++) {
                TextView c = cells[r][i] = Ui.text(a, "", 24, Ui.TEXT, "sans-serif-medium");
                c.setGravity(Gravity.CENTER);
                c.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46));
                lp.setMargins(Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3));
                rows[r].addView(c, lp);
            }
            box.addView(rows[r], new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        msg = Ui.text(a, "", 14, Ui.SOFT, null);
        msg.setGravity(Gravity.CENTER);
        msg.setMinLines(2);
        msg.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        box.addView(msg, Ui.mw(a, 8));
        more = Ui.button(a, "Ещё слово", true, v -> start(false));
        box.addView(more);
        for (int r = 0; r < KEYS.length; r++) {
            LinearLayout line = Ui.horizontal(a);
            if (r == 2) line.addView(key(ENTER, "Ввод", "Ввод"), Ui.weight(1.7f));
            for (char ch : KEYS[r].toCharArray()) line.addView(key(String.valueOf(ch), String.valueOf(ch).toUpperCase(), String.valueOf(ch)), Ui.weight(1));
            if (r == 2) line.addView(key(BACK, "⌫", "Стереть"), Ui.weight(1.7f));
            box.addView(line, Ui.mw(a, r == 0 ? 10 : 0));
        }
        start(true);
    }

    private TextView key(String k, String label, String spoken) {
        TextView b = Ui.text(a, label, k.length() > 1 ? 13 : 16, Ui.TEXT, "sans-serif-medium");
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(a, 46));
        b.setContentDescription(spoken);
        b.setMaxLines(1);
        b.setOnClickListener(v -> { Ui.tick(v); press(k); });
        keys.put(k, b);
        return b;
    }

    /** Оценка попытки по буквам: 2 на месте, 1 есть в слове, 0 нет. Повторы букв считаются честно. */
    static int[] score(String word, String guess) {
        int[] res = new int[5];
        Map<Character, Integer> left = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            if (guess.charAt(i) == word.charAt(i)) res[i] = 2;
            else left.merge(word.charAt(i), 1, Integer::sum);
        }
        for (int i = 0; i < 5; i++) {
            if (res[i] == 0 && left.getOrDefault(guess.charAt(i), 0) > 0) {
                res[i] = 1;
                left.merge(guess.charAt(i), -1, Integer::sum);
            }
        }
        return res;
    }

    private void start(boolean daily) {
        this.daily = daily;
        SharedPreferences p = Updater.prefs(a);
        long day = LocalDate.now().toEpochDay();
        tries.clear();
        cur = "";
        if (daily && p.getLong(KEY_DAY, 0) == day)
            for (String w : p.getString(KEY_ROWS, "").split(",")) if (w.matches("[а-я]{5}") && tries.size() < 6) tries.add(w);
        word = words[daily ? (int) (day % words.length) : (int) (Math.random() * words.length)];
        draw();
    }

    private boolean over() {
        return tries.contains(word) || tries.size() >= 6;
    }

    private void press(String k) {
        if (over()) return;
        if (k.equals(ENTER)) {
            LinearLayout row = rows[tries.size()];
            if (cur.length() < 5) {
                ObjectAnimator.ofFloat(row, View.TRANSLATION_X, 0, -Ui.dp(a, 7), Ui.dp(a, 7), 0).setDuration(300).start();
                return;
            }
            tries.add(cur);
            cur = "";
            SharedPreferences.Editor e = Updater.prefs(a).edit();
            if (daily) e.putLong(KEY_DAY, LocalDate.now().toEpochDay()).putString(KEY_ROWS, String.join(",", tries));
            if (tries.contains(word)) e.putInt(KEY_WON, Updater.prefs(a).getInt(KEY_WON, 0) + 1);
            e.apply();
            draw();
            for (int i = 0; i < 5; i++) { // буквы переворачиваются по очереди
                View c = row.getChildAt(i);
                c.setRotationX(90);
                c.animate().rotationX(0).setStartDelay(i * 90L).setDuration(300).start();
            }
            return;
        }
        cur = k.equals(BACK) ? cur.substring(0, Math.max(0, cur.length() - 1)) : cur.length() < 5 ? cur + k : cur;
        draw();
    }

    private void draw() {
        boolean won = tries.contains(word), over = over();
        Map<String, Integer> best = new HashMap<>(); // лучшая оценка каждой буквы: красит клавиши
        for (int r = 0; r < 6; r++) {
            String g = r < tries.size() ? tries.get(r) : null, txt = g != null ? g : r == tries.size() && !over ? cur : "";
            int[] sc = g == null ? null : score(word, g);
            StringBuilder spoken = new StringBuilder(g == null ? txt.isEmpty() ? "пусто" : txt : g + ":");
            for (int i = 0; i < 5; i++) {
                String ch = i < txt.length() ? txt.substring(i, i + 1) : "";
                cells[r][i].setText(ch.toUpperCase());
                paint(cells[r][i], sc == null ? -1 : sc[i], 12, ch.isEmpty() ? Ui.LINE : Ui.MUTED);
                if (sc == null) continue;
                best.merge(ch, sc[i], Math::max);
                spoken.append(' ').append(ch).append(' ').append(MARKS[sc[i]]).append(i < 4 ? "," : "");
            }
            rows[r].setContentDescription(spoken);
        }
        for (Map.Entry<String, TextView> k : keys.entrySet()) paint(k.getValue(), best.getOrDefault(k.getKey(), -1), 8, 0);
        msg.setText(won ? "Угадал с " + tries.size() + "-й попытки! Всего угадано слов: " + Updater.prefs(a).getInt(KEY_WON, 0)
                : over ? "Не вышло. Это было слово «" + word + "»"
                : (daily ? "Слово дня. " : "") + "Попытка " + (tries.size() + 1) + " из 6. Зелёная буква на месте, жёлтая есть в слове");
        more.setVisibility(over ? View.VISIBLE : View.GONE);
    }

    /** Клетка или клавиша по оценке: -1 ещё не проверяли, 0 буквы нет, 1 есть, 2 на месте. */
    private void paint(TextView v, int s, int radius, int stroke) {
        int[] fill = {Ui.RAISED, 0xFFE0A800, 0xFF1F9D45}, ink = {Ui.MUTED, 0xFF141414, 0xFFFFFFFF};
        android.graphics.drawable.Drawable bg = Ui.round(a, s < 0 ? stroke == 0 ? Ui.RAISED : 0 : fill[s], radius, s < 0 ? stroke : 0);
        // зазор между клавишами: фон меньше самой клавиши, а палец попадает по всей
        v.setBackground(radius == 8 ? new android.graphics.drawable.InsetDrawable(bg, Ui.dp(a, 2)) : bg);
        v.setTextColor(s < 0 ? Ui.TEXT : ink[s]);
    }
}
