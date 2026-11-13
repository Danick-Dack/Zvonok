package app.urok7b;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Вкладка «СОР/СОЧ»: график суммативных работ любого класса по датам, ближайшие сверху.
 * Вкладка сама исчезает, когда у всех классов всё сдано (MainActivity смотрит на Plus.examsEnd).
 */
final class ExamsTab {

    final ScrollView view;
    private final MainActivity a;
    private final TextView meta;
    private final LinearLayout classes, list;
    private String cls;
    private boolean showPast;

    ExamsTab(MainActivity a) {
        this.a = a;
        view = new ScrollView(a);
        view.setVerticalScrollBarEnabled(false);
        LinearLayout root = Ui.vertical(a);
        root.setPadding(a.dp(18), a.dp(20), a.dp(18), a.dp(28));
        view.addView(root);
        root.addView(Ui.heading(Ui.text(a, "СОР и СОЧ", 28, Ui.TEXT, "sans-serif-medium")));
        meta = Ui.text(a, "", 14, Ui.MUTED, null);
        root.addView(meta);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        classes = Ui.horizontal(a);
        hs.addView(classes);
        root.addView(hs, Ui.mw(a, 14));
        list = Ui.vertical(a);
        root.addView(list);
        TextView src = Ui.text(a, "График утверждён приказом лицея на четверть. Учитель может сдвинуть дату: если заметил ошибку, напиши нам в «Ещё».", 13, Ui.MUTED, null);
        src.setPadding(a.dp(4), a.dp(18), a.dp(4), 0);
        root.addView(src);
    }

    void render(MainActivity.Tick t) {
        String mine = Schedule.cls();
        if (cls == null || !Schedule.classes().contains(cls)) cls = mine;
        LocalDate today = t.now.toLocalDate();
        classes.removeAllViews();
        for (String c : Schedule.classes()) {
            classes.addView(Ui.pill(a, c, c.equals(cls), v -> { Ui.tick(v); cls = c; showPast = false; render(t); }));
        }
        List<Plus.Exam> all = Plus.examList(cls), next = new ArrayList<>(), past = new ArrayList<>();
        for (Plus.Exam x : all) (x.date.compareTo(today.toString()) >= 0 ? next : past).add(x);
        meta.setText(cls + " · " + Schedule.term() + (next.isEmpty() ? "" : " · впереди " + next.size()));
        list.removeAllViews();
        if (next.isEmpty()) {
            TextView e = Ui.text(a, all.isEmpty() ? "Графика для " + cls + " пока нет." : "У " + cls + " всё сдано. Можно выдохнуть.", 16, Ui.TEXT, null);
            e.setPadding(a.dp(16), a.dp(18), a.dp(16), a.dp(18));
            e.setBackground(Ui.round(a, (Ui.OK & 0x00FFFFFF) | 0x26000000, 16, 0));
            list.addView(e, Ui.mw(a, 4));
        }
        days(next, today);
        if (past.isEmpty()) return;
        TextView more = Ui.text(a, (showPast ? "Скрыть прошедшие" : "Уже прошли: " + past.size()), 14, Ui.ACCENT, "sans-serif-medium");
        more.setPadding(a.dp(4), a.dp(16), a.dp(4), a.dp(8));
        more.setOnClickListener(v -> { showPast = !showPast; render(t); });
        list.addView(more);
        if (showPast) {
            java.util.Collections.reverse(past);
            days(past, today);
        }
    }

    /** Карточка на каждый день: число слева, работы справа, «завтра» или «через 5 дней». */
    private void days(List<Plus.Exam> exams, LocalDate today) {
        LinearLayout card = null, col = null;
        String last = "";
        for (Plus.Exam x : exams) {
            if (!x.date.equals(last)) {
                last = x.date;
                LocalDate d;
                try {
                    d = LocalDate.parse(x.date);
                } catch (Exception e) {
                    continue;
                }
                long n = ChronoUnit.DAYS.between(today, d);
                card = Ui.card(a);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12));
                if (n == 0) card.setBackground(Ui.round(a, Ui.CARD, 18, Ui.ACCENT));
                if (n < 0) card.setAlpha(0.6f);
                LinearLayout date = Ui.vertical(a);
                date.setGravity(Gravity.CENTER);
                TextView num = Ui.text(a, String.valueOf(d.getDayOfMonth()), 24, Ui.TEXT, "sans-serif-medium");
                num.setGravity(Gravity.CENTER);
                date.addView(num);
                TextView mon = Ui.text(a, Schedule.dateText(d).split(" ")[1].substring(0, 3) + ", " + Schedule.dayShort(d.getDayOfWeek()).toLowerCase(), 12, Ui.MUTED, null);
                mon.setGravity(Gravity.CENTER);
                date.addView(mon);
                card.addView(date, new LinearLayout.LayoutParams(a.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
                col = Ui.vertical(a);
                LinearLayout.LayoutParams lp = Ui.weight(1);
                lp.leftMargin = a.dp(10);
                card.addView(col, lp);
                String when = n == 0 ? "сегодня" : n == 1 ? "завтра" : n > 1 ? "через " + Schedule.word((int) n, "день", "дня", "дней") : "";
                if (!when.isEmpty()) card.addView(Ui.text(a, when, 13, n <= 1 ? Ui.ACCENT : Ui.MUTED, n <= 1 ? "sans-serif-medium" : null));
                card.setFocusable(true);
                card.setContentDescription(Schedule.dateText(d) + (when.isEmpty() ? "" : ", " + when));
                list.addView(card, Ui.mw(a, 10));
            }
            if (col == null) continue;
            boolean soch = x.type.equals("СОЧ");
            LinearLayout r = Ui.horizontal(a);
            r.setPadding(0, a.dp(3), 0, a.dp(3));
            TextView tag = Ui.text(a, x.type, 11, soch ? 0xFFFFFFFF : Ui.WARN, "sans-serif-medium");
            tag.setPadding(a.dp(8), a.dp(2), a.dp(8), a.dp(2));
            tag.setBackground(Ui.round(a, soch ? Ui.BAD : (Ui.WARN & 0x00FFFFFF) | 0x33000000, 9, 0));
            r.addView(tag);
            TextView s = Ui.text(a, x.subject, 16, Ui.TEXT, null);
            s.setPadding(a.dp(8), 0, 0, 0);
            r.addView(s, Ui.weight(1));
            col.addView(r);
            card.setContentDescription(card.getContentDescription() + ". " + x.type + ": " + x.subject);
        }
    }
}
