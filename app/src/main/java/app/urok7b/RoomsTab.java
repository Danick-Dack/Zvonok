package app.urok7b;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Вкладка «Кабинеты»: свободные на любом уроке любого дня и кто сидит в кабинете, с заменами (как на сайте). */
final class RoomsTab {

    final ScrollView view;
    private final MainActivity a;
    private final LinearLayout days, slots, free, nextFree, roomCard;
    private final EditText q;
    private int day = -1, slot = -1;
    private MainActivity.Tick last;

    RoomsTab(MainActivity a) {
        this.a = a;
        view = new ScrollView(a);
        view.setVerticalScrollBarEnabled(false);
        LinearLayout root = Ui.vertical(a);
        root.setPadding(a.dp(18), a.dp(20), a.dp(18), a.dp(28));
        view.addView(root);
        root.addView(Ui.heading(Ui.text(a, "Кабинеты", 28, Ui.TEXT, "sans-serif-medium")));
        root.addView(Ui.text(a, "Свободные на любом уроке и кто где сидит, с заменами", 14, Ui.MUTED, null));

        // поиск кабинета
        LinearLayout search = Ui.horizontal(a);
        search.setBackground(Ui.round(a, Ui.FIELD, 16, Ui.LINE));
        search.setPadding(a.dp(12), 0, a.dp(4), 0);
        search.addView(Ui.icon(a, R.drawable.ic_search, Ui.MUTED, 22));
        q = new EditText(a);
        q.setHint("Номер кабинета, например 46");
        q.setTextSize(16);
        q.setTextColor(Ui.TEXT);
        q.setHintTextColor(Ui.MUTED);
        q.setBackground(null);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setMinHeight(a.dp(52));
        q.setContentDescription("Найти кабинет");
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable e) { if (last != null) renderRoom(last); }
        });
        search.addView(q, Ui.weight(1));
        root.addView(search, Ui.mw(a, 16));
        roomCard = Ui.card(a);
        roomCard.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14));
        root.addView(roomCard, Ui.mw(a, 10));

        days = Ui.horizontal(a);
        root.addView(days, Ui.mw(a, 18));
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        slots = Ui.horizontal(a);
        hs.addView(slots);
        root.addView(hs, Ui.mw(a, 10));
        free = Ui.card(a);
        free.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(16));
        root.addView(free, Ui.mw(a, 12));
        nextFree = Ui.card(a);
        nextFree.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(16));
        root.addView(nextFree, Ui.mw(a, 12));
        root.addView(Ui.linkRow(a, "Для учителей: уроки предмета", "Все уроки предмета у всех классов на пять дней", null, v -> pickSubject()), Ui.mw(a, 12));
    }

    /** Ближайшие пять учебных дней по порядку. */
    private static List<LocalDate> fiveDays() {
        List<LocalDate> out = new ArrayList<>();
        for (int d = 0; d < 5; d++) out.add(dateOf(LocalDate.now(), d));
        Collections.sort(out);
        return out;
    }

    private void pickSubject() {
        TreeSet<String> all = new TreeSet<>();
        for (LocalDate date : fiveDays())
            for (List<Schedule.Use> us : Schedule.roomDay(date).values()) for (Schedule.Use u : us) all.add(u.subject);
        String[] list = all.toArray(new String[0]);
        new android.app.AlertDialog.Builder(a, Ui.dialogTheme()).setTitle("Какой предмет")
                .setItems(list, (d, i) -> week(list[i], false)).setNegativeButton("Отмена", null).show();
    }

    /**
     * Для учителей: неделя одного кабинета или все уроки одного предмета у всех классов, с заменами.
     * Имён учителей в расписании лицея нет, поэтому только кабинет, класс, предмет и время.
     */
    private void week(String what, boolean room) {
        LinearLayout box = Ui.vertical(a);
        box.setPadding(a.dp(22), 0, a.dp(22), a.dp(8));
        for (LocalDate date : fiveDays()) {
            String off = Schedule.offDay(date);
            TextView h = Ui.text(a, Schedule.dayName(date.getDayOfWeek()) + ", " + Schedule.dateText(date)
                    + (off != null ? " · " + off.toLowerCase(Locale.ROOT) + ", обычное расписание" : ""), 15, Ui.TEXT, "sans-serif-medium");
            h.setPadding(0, a.dp(14), 0, a.dp(4));
            box.addView(h);
            // урок и класс → строка; урок в двух кабинетах (42/21) одной строкой
            TreeMap<String, String> rows = new TreeMap<>();
            for (Map.Entry<String, List<Schedule.Use>> e : Schedule.roomDay(date).entrySet()) {
                for (Schedule.Use u : e.getValue()) {
                    if (room ? !e.getKey().equals(what) : !u.subject.equals(what)) continue;
                    String key = String.format(Locale.ROOT, "%02d %02d %s", u.num, u.cls.length(), u.cls), old = rows.get(key);
                    rows.put(key, old != null ? old + "/" + e.getKey() : u.num + " · " + Schedule.hm(u.start) + "–" + Schedule.hm(u.end) + "   " + u.cls
                            + (room ? " " + Schedule.lowFirst(u.subject) : Character.isDigit(e.getKey().charAt(0)) ? ", каб. " + e.getKey() : ", " + e.getKey()));
                }
            }
            if (rows.isEmpty()) box.addView(note(room ? "Весь день свободен" : "Уроков нет"));
            for (String r : rows.values()) {
                TextView t = Ui.text(a, r, 15, Ui.TEXT, null);
                t.setPadding(0, a.dp(5), 0, a.dp(5));
                box.addView(t);
            }
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        new android.app.AlertDialog.Builder(a, Ui.dialogTheme()).setTitle(room ? (Character.isDigit(what.charAt(0)) ? "Кабинет " + what : "Спортзал") : what)
                .setView(sv).setPositiveButton("Закрыть", null).show();
    }

    void focusSearch() {
        q.requestFocus();
        q.post(() -> {
            InputMethodManager im = a.getSystemService(InputMethodManager.class);
            if (im != null) im.showSoftInput(q, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    /** Дата выбранного дня недели: сегодня или ближайший такой день впереди. */
    private static LocalDate dateOf(LocalDate today, int d) {
        return today.plusDays((d - (today.getDayOfWeek().getValue() - 1) + 7) % 7);
    }

    void render(MainActivity.Tick t) {
        last = t;
        LocalDate today = t.now.toLocalDate();
        int dow = today.getDayOfWeek().getValue() - 1;
        if (day < 0) day = dow < 5 ? dow : 0;
        days.removeAllViews();
        for (int i = 0; i < 5; i++) {
            final int k = i;
            TextView b = Ui.text(a, i == dow ? "Сегодня" : Schedule.dayShort(java.time.DayOfWeek.of(i + 1)), 14,
                    i == day ? Ui.ON_ACCENT : Ui.TEXT, "sans-serif-medium");
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(a.dp(44));
            b.setBackground(i == day ? Ui.round(a, Ui.ACCENT, 14, 0) : Ui.round(a, Ui.FIELD, 14, Ui.LINE));
            b.setSelected(i == day);
            b.setContentDescription(i == dow ? "Сегодня" : Schedule.dayName(java.time.DayOfWeek.of(i + 1)));
            b.setOnClickListener(v -> { Ui.tick(v); day = k; slot = -1; render(t); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, i == dow ? 1.6f : 1);
            lp.setMargins(a.dp(2), 0, a.dp(2), 0);
            days.addView(b, lp);
        }
        boolean isToday = day == dow;
        LocalDate date = dateOf(today, day);
        String off = Schedule.offDay(date);
        Map<String, List<Schedule.Use>> use = Schedule.roomDay(date);
        int[][] bells = Schedule.dayBells(date);
        List<Integer> ss = Schedule.slots(date, use);
        int live = 0, next = 0;
        if (isToday && off == null) {
            for (int n : ss) if (live == 0 && t.min >= bells[n - 1][0] && t.min < bells[n - 1][1]) live = n;
            for (int n : ss) if (next == 0 && bells[n - 1][0] > t.min) next = n;
        }
        if (slot < 0 || (slot > 0 && !ss.contains(slot))) slot = live > 0 ? live : next > 0 ? next : isToday ? 0 : ss.isEmpty() ? 0 : ss.get(0);

        slots.removeAllViews();
        for (int n : ss) {
            final int k = n;
            LinearLayout b = Ui.vertical(a);
            b.setGravity(Gravity.CENTER);
            b.setPadding(a.dp(14), a.dp(6), a.dp(14), a.dp(6));
            boolean on = n == slot;
            b.setBackground(on ? Ui.round(a, Ui.ACCENT, 14, 0) : Ui.round(a, Ui.FIELD, 14, n == live ? Ui.ACCENT : Ui.LINE));
            TextView num = Ui.text(a, String.valueOf(n), 17, on ? Ui.ON_ACCENT : Ui.TEXT, "sans-serif-medium");
            TextView tm = Ui.text(a, Schedule.hm(bells[n - 1][0]), 12, on ? Ui.ON_ACCENT : Ui.MUTED, null);
            num.setGravity(Gravity.CENTER);
            tm.setGravity(Gravity.CENTER);
            b.addView(num);
            b.addView(tm);
            b.setSelected(on);
            b.setContentDescription(n + " урок, " + Schedule.hm(bells[n - 1][0]) + "–" + Schedule.hm(bells[n - 1][1]) + (n == live ? ", идёт сейчас" : ""));
            b.setOnClickListener(v -> { Ui.tick(v); slot = slot == k ? 0 : k; render(t); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = a.dp(6);
            slots.addView(b, lp);
        }

        free.removeAllViews();
        if (slot > 0) {
            List<String> list = Schedule.freeRooms(use, slot);
            free.addView(Ui.heading(Ui.text(a, slot == live ? "Свободны сейчас" : "Свободны на " + slot + " уроке", 19, Ui.TEXT, "sans-serif-medium")));
            free.addView(note(slot + " урок, " + Schedule.hm(bells[slot - 1][0]) + "–" + Schedule.hm(bells[slot - 1][1]) + " · "
                    + (list.isEmpty() ? "свободных нет" : Schedule.word(list.size(), "кабинет", "кабинета", "кабинетов"))
                    + (off != null ? ". " + off + ": уроков нет, показан обычный день" : list.isEmpty() ? "" : ". Нажми на кабинет, чтобы увидеть его день")));
            free.addView(chips(list), Ui.mw(a, 12));
        } else {
            boolean over = isToday && !ss.isEmpty() && t.min >= bells[ss.get(ss.size() - 1) - 1][1];
            free.addView(Ui.heading(Ui.text(a, off != null ? off : over ? "Уроки закончились" : "Все кабинеты", 19, Ui.TEXT, "sans-serif-medium")));
            free.addView(note(off != null || over ? "Сейчас свободны все кабинеты. Нажми на любой, чтобы увидеть его день." : "Выбери урок выше, чтобы увидеть свободные."));
            free.addView(chips(Schedule.rooms()), Ui.mw(a, 12));
        }

        // во время урока сразу видно, что освободится к следующему
        int after = 0;
        if (slot > 0 && slot == live) for (int n : ss) if (after == 0 && n > slot) after = n;
        nextFree.removeAllViews();
        nextFree.setVisibility(after > 0 ? View.VISIBLE : View.GONE);
        if (after > 0) {
            List<String> l2 = Schedule.freeRooms(use, after);
            nextFree.addView(Ui.heading(Ui.text(a, "На следующем уроке", 19, Ui.TEXT, "sans-serif-medium")));
            nextFree.addView(note(after + " урок, " + Schedule.hm(bells[after - 1][0]) + "–" + Schedule.hm(bells[after - 1][1]) + " · "
                    + (l2.isEmpty() ? "свободных нет" : Schedule.word(l2.size(), "кабинет", "кабинета", "кабинетов"))));
            nextFree.addView(chips(l2), Ui.mw(a, 12));
        }
        renderRoom(t);
    }

    private TextView note(String s) {
        TextView t = Ui.text(a, s, 14, Ui.MUTED, null);
        t.setPadding(0, a.dp(4), 0, 0);
        return t;
    }

    private Ui.Flow chips(List<String> rooms) {
        Ui.Flow f = new Ui.Flow(a, 8);
        for (String r : rooms) {
            TextView c = Ui.pill(a, r, false, v -> {
                Ui.tick(v);
                q.setText(r);
                q.setSelection(r.length());
                view.smoothScrollTo(0, 0);
            });
            c.setMinWidth(a.dp(56));
            c.setMinHeight(a.dp(44));
            // свободные кабинеты зелёные, как на сайте; спортзал в общем списке нейтральный
            if (!Schedule.SPORT.equals(r)) {
                c.setTextColor(Ui.OK);
                c.setBackground(Ui.round(a, (Ui.OK & 0x00FFFFFF) | 0x24000000, 14, 0));
            }
            c.setContentDescription(Character.isDigit(r.charAt(0)) ? "Кабинет " + r : r);
            f.addView(c);
        }
        return f;
    }

    /** Выбранный кабинет: кто там на каждом уроке, свободен ли сейчас. */
    private void renderRoom(MainActivity.Tick t) {
        roomCard.removeAllViews();
        String raw = q.getText().toString().trim().toLowerCase(java.util.Locale.ROOT)
                .replaceFirst("^каб(инет)?\\.?\\s*", "").replaceFirst("^с/з$|^спорт.*", Schedule.SPORT);
        roomCard.setVisibility(raw.isEmpty() ? View.GONE : View.VISIBLE);
        if (raw.isEmpty()) return;
        String room = find(raw);
        if (room == null) for (String part : raw.split("/")) if (room == null && !part.trim().isEmpty()) room = find(part.trim());
        if (room == null) {
            roomCard.addView(Ui.heading(Ui.text(a, "Кабинет " + raw, 19, Ui.TEXT, "sans-serif-medium")));
            roomCard.addView(note("Такого кабинета нет в расписании. Проверь номер."));
            return;
        }
        LocalDate today = t.now.toLocalDate();
        int dow = today.getDayOfWeek().getValue() - 1;
        boolean isToday = day == dow;
        LocalDate date = dateOf(today, day);
        String off = Schedule.offDay(date);
        Map<String, List<Schedule.Use>> use = Schedule.roomDay(date);
        int[][] bells = Schedule.dayBells(date);
        List<Integer> ss = Schedule.slots(date, use);
        roomCard.addView(Ui.heading(Ui.text(a, Character.isDigit(room.charAt(0)) ? "Кабинет " + room : "Спортзал", 19, Ui.TEXT, "sans-serif-medium")));
        if (isToday && off == null && !ss.isEmpty() && t.min < bells[ss.get(ss.size() - 1) - 1][1]) {
            Schedule.RoomState st = Schedule.roomNow(room, use, t.min);
            TextView now = Ui.text(a, st.text, 15, st.busy ? Ui.BAD : Ui.OK, "sans-serif-medium");
            now.setPadding(0, a.dp(6), 0, a.dp(4));
            roomCard.addView(now);
        } else {
            roomCard.addView(note((isToday ? "Сегодня" : Schedule.dayName(date.getDayOfWeek()))
                    + (off != null ? ", " + off.toLowerCase(java.util.Locale.ROOT) + ", обычное расписание" : "")));
        }
        int live = 0;
        if (isToday && off == null) for (int n : ss) if (t.min >= bells[n - 1][0] && t.min < bells[n - 1][1]) live = n;
        for (int n : ss) {
            LinearLayout r = Ui.horizontal(a);
            r.setGravity(Gravity.TOP);
            r.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
            if (n == live) r.setBackground(Ui.round(a, (Ui.ACCENT & 0x00FFFFFF) | 0x1F000000, 12, 0));
            TextView tm = Ui.text(a, n + " · " + Schedule.hm(bells[n - 1][0]) + "–" + Schedule.hm(bells[n - 1][1]), 13, Ui.MUTED, null);
            tm.setLayoutParams(new LinearLayout.LayoutParams(a.dp(108), -2));
            r.addView(tm);
            StringBuilder who = new StringBuilder();
            List<Schedule.Use> us = use.get(room);
            if (us != null) for (Schedule.Use u : us) if (u.num == n) who.append(who.length() > 0 ? ", " : "").append(u.cls).append(" ").append(Schedule.lowFirst(u.subject));
            TextView w = Ui.text(a, who.length() > 0 ? who.toString() : "свободен", 15, who.length() > 0 ? Ui.TEXT : Ui.OK, null);
            w.setLayoutParams(Ui.weight(1));
            r.addView(w);
            r.setContentDescription(n + " урок: " + w.getText());
            roomCard.addView(r);
        }
        String found = room;
        TextView wk = Ui.text(a, "Неделя кабинета ›", 15, Ui.ACCENT, "sans-serif-medium");
        wk.setPadding(a.dp(8), a.dp(12), a.dp(8), a.dp(4));
        wk.setOnClickListener(v -> week(found, true));
        roomCard.addView(wk);
    }

    private static String find(String x) {
        for (String r : Schedule.rooms()) if (r.toLowerCase(java.util.Locale.ROOT).equals(x)) return r;
        for (String r : Schedule.rooms()) if (r.toLowerCase(java.util.Locale.ROOT).startsWith(x)) return r;
        return null;
    }
}
