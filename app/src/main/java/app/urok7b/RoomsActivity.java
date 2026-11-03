package app.urok7b;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Кабинеты: какие свободны сейчас и кто сидит в кабинете на каждом уроке. Для ярлыков на иконке. */
public class RoomsActivity extends Activity {

    static final String EXTRA_MODE = "mode"; // "free" или "find"

    private EditText q;
    private LinearLayout found, free;
    private DayOfWeek day;
    private boolean todayIsDay;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Schedule.load(this);
        ZonedDateTime now = ZonedDateTime.now();
        day = now.getDayOfWeek();
        todayIsDay = true;
        // в выходной показываем ближайший учебный день
        for (int i = 0; i < 7 && (day.getValue() > 6 || !anyLessons(day)); i++) {
            day = day.plus(1);
            todayIsDay = false;
        }

        LinearLayout root = Ui.screen(this, "Кабинеты", todayIsDay ? "Сегодня" : Schedule.dayName(day));

        root.addView(Ui.caps(this, "КТО В КАБИНЕТЕ"));
        q = Ui.field(this, "", "Номер кабинета, например 46");
        q.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(q, Ui.mw(this, 0));
        found = Ui.vertical(this);
        root.addView(found, Ui.mw(this, 8));
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { renderFound(); }
        });

        free = Ui.vertical(this);
        root.addView(free);
        renderFree(now);
        renderFound();

        if ("find".equals(getIntent().getStringExtra(EXTRA_MODE))) {
            q.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private boolean anyLessons(DayOfWeek d) {
        for (String c : Schedule.classes(this)) if (!Schedule.classLessons(this, c, d).isEmpty()) return true;
        return false;
    }

    static List<String> rooms(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String r : raw.split("[/,;]")) {
            r = r.trim();
            if (!r.isEmpty()) out.add(r);
        }
        return out;
    }

    private void renderFound() {
        found.removeAllViews();
        String want = q.getText().toString().trim().toLowerCase();
        if (want.isEmpty()) return;
        LinearLayout card = Ui.card(this);
        int hits = 0;
        int[][] bells = Schedule.bells(this);
        for (int n = 1; n <= bells.length; n++) {
            if (bells[n - 1] == null) continue;
            StringBuilder who = new StringBuilder();
            for (String c : Schedule.classes(this)) {
                for (Schedule.Lesson l : Schedule.classLessons(this, c, day)) {
                    if (l.num != n) continue;
                    for (String r : rooms(l.room)) {
                        if (r.toLowerCase().equals(want)) {
                            if (who.length() > 0) who.append(", ");
                            who.append(c).append(" · ").append(l.subject);
                        }
                    }
                }
            }
            if (who.length() == 0) continue;
            hits++;
            if (card.getChildCount() > 0) card.addView(Ui.divider(this));
            LinearLayout row = Ui.twoLines(this, who.toString(),
                    n + " урок · " + Schedule.hm(bells[n - 1][0]) + "–" + Schedule.hm(bells[n - 1][1]));
            row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            card.addView(row);
        }
        if (hits == 0) {
            TextView t = Ui.text(this, "В этом кабинете " + (todayIsDay ? "сегодня" : Schedule.dayWhen(day)) + " уроков нет", 14, Ui.MUTED, null);
            t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            card.addView(t);
        }
        found.addView(card);
    }

    private void renderFree(ZonedDateTime now) {
        int[][] bells = Schedule.bells(this);
        int mins = now.toLocalTime().getHour() * 60 + now.toLocalTime().getMinute();
        int slot = -1;
        boolean during = false;
        if (todayIsDay) {
            for (int n = 1; n <= bells.length; n++) {
                if (bells[n - 1] == null) continue;
                if (mins < bells[n - 1][1]) {
                    slot = n;
                    during = mins >= bells[n - 1][0];
                    break;
                }
            }
        } else {
            for (int n = 1; n <= bells.length; n++) if (bells[n - 1] != null) { slot = n; break; }
        }

        TreeSet<String> all = new TreeSet<>(RoomsActivity::cmp), busy = new TreeSet<>(RoomsActivity::cmp);
        for (String c : Schedule.classes(this)) {
            for (DayOfWeek d : DayOfWeek.values()) {
                for (Schedule.Lesson l : Schedule.classLessons(this, c, d)) {
                    all.addAll(rooms(l.room));
                    if (d == day && l.num == slot) busy.addAll(rooms(l.room));
                }
            }
        }
        String head;
        if (slot < 0) head = "УРОКИ ЗАКОНЧИЛИСЬ · СВОБОДНО ВСЁ";
        else if (during) head = "СВОБОДНЫ СЕЙЧАС · " + slot + " УРОК";
        else if (todayIsDay) head = "СВОБОДНЫ НА " + slot + " УРОКЕ";
        else head = Schedule.dayName(day).toUpperCase() + " · СВОБОДНЫ НА 1 УРОКЕ";
        free.addView(Ui.caps(this, head));
        List<View> chips = new ArrayList<>();
        for (String r : all) {
            if (busy.contains(r)) continue;
            chips.add(Ui.pill(this, r, false, v -> { q.setText(r); q.setSelection(r.length()); }));
        }
        LinearLayout card = Ui.card(this);
        card.addView(Ui.wrapRows(this, chips.toArray(new View[0]), 4));
        free.addView(card);
    }

    private static int cmp(String a, String b) {
        int x = num(a), y = num(b);
        return x != y ? Integer.compare(x, y) : a.compareTo(b);
    }

    private static int num(String s) {
        String d = s.replaceAll("\\D.*", "");
        try {
            return d.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(d);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
