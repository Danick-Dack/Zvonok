package app.urok7b;

import android.content.Intent;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/** Вкладка «Неделя»: эта и следующая неделя с заменами и каникулами, дни листаются пальцем. */
final class WeekTab {

    final ScrollView view;
    private final MainActivity a;
    private final TextView meta;
    private final LinearLayout seg, days, card;
    private int weekOff = -1, sel = -1;
    private MainActivity.Tick last;

    @android.annotation.SuppressLint("ClickableViewAccessibility") // смахивание дублирует кнопки дней, они доступны диктору
    WeekTab(MainActivity a) {
        this.a = a;
        view = new ScrollView(a);
        view.setVerticalScrollBarEnabled(false);
        LinearLayout root = Ui.vertical(a);
        root.setPadding(a.dp(18), a.dp(20), a.dp(18), a.dp(28));
        view.addView(root);
        root.addView(Ui.heading(Ui.text(a, "Неделя", 28, Ui.TEXT, "sans-serif-medium")));
        meta = Ui.text(a, "", 14, Ui.MUTED, null);
        root.addView(meta);
        seg = Ui.vertical(a);
        root.addView(seg, Ui.mw(a, 16));
        days = Ui.horizontal(a);
        root.addView(days, Ui.mw(a, 12));
        card = Ui.vertical(a);
        root.addView(card, Ui.mw(a, 8));
        TextView report = Ui.text(a, "Ошибка в расписании?", 14, Ui.ACCENT, "sans-serif-medium");
        report.setPadding(a.dp(4), a.dp(20), a.dp(4), a.dp(12));
        report.setOnClickListener(v -> a.startActivity(new Intent(a, Feedback.class)
                .putExtra(Feedback.EXTRA_ABOUT, "schedule").putExtra(Feedback.EXTRA_CLASS, Schedule.isCustom() ? "" : Schedule.title())
                .putExtra(Feedback.EXTRA_DAY, sel)));
        root.addView(report);

        // смахнуть влево или вправо: следующий или предыдущий день
        GestureDetector g = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || Math.abs(vx) < 600 || Math.abs(e2.getX() - e1.getX()) < a.dp(60)
                        || Math.abs(e2.getX() - e1.getX()) < 2 * Math.abs(e2.getY() - e1.getY())) return false;
                int n = count(), d = sel + (vx < 0 ? 1 : -1);
                if (d < 0 || d >= n) return false;
                Ui.tick(view);
                pick(d);
                return true;
            }
        });
        card.setOnTouchListener((v, e) -> g.onTouchEvent(e) || true);
    }

    private int count() {
        return Schedule.lessons(java.time.DayOfWeek.SATURDAY).isEmpty() ? 5 : 6;
    }

    private LocalDate monday(LocalDate today) {
        return today.minusDays(today.getDayOfWeek().getValue() - 1).plusWeeks(weekOff);
    }

    void render(MainActivity.Tick t) {
        last = t;
        LocalDate today = t.now.toLocalDate();
        int n = count(), dow = today.getDayOfWeek().getValue() - 1;
        if (weekOff < 0) weekOff = dow >= n ? 1 : 0; // в выходные сразу следующая неделя
        if (sel < 0 || sel >= n) sel = weekOff == 1 ? 0 : t.s.kind == Schedule.Kind.AFTER && dow + 1 < n ? dow + 1 : Math.min(dow, n - 1);
        LocalDate mon = monday(today), end = mon.plusDays(n - 1);
        meta.setText(Schedule.title() + " · " + mon.getDayOfMonth() + (mon.getMonth() == end.getMonth() ? "" : " " + Schedule.dateText(mon).split(" ")[1])
                + "–" + Schedule.dateText(end));
        seg.removeAllViews();
        seg.addView(Ui.segments(a, new String[]{"Эта неделя", "Следующая"}, weekOff, k -> { weekOff = k; sel = k == 1 ? 0 : -1; render(last); }));
        days.removeAllViews();
        for (int i = 0; i < n; i++) {
            final int k = i;
            LocalDate date = mon.plusDays(i);
            boolean on = i == sel, isToday = date.equals(today);
            LinearLayout b = Ui.vertical(a);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, a.dp(8), 0, a.dp(8));
            b.setBackground(on ? Ui.round(a, Ui.ACCENT, 14, 0) : isToday ? Ui.round(a, 0, 14, Ui.ACCENT) : null);
            b.addView(Ui.text(a, Schedule.dayShort(date.getDayOfWeek()), 15, on ? Ui.ON_ACCENT : Ui.TEXT, "sans-serif-medium"));
            b.addView(Ui.text(a, String.valueOf(date.getDayOfMonth()), 12, on ? Ui.ON_ACCENT : Ui.MUTED, null));
            for (int c = 0; c < 2; c++) ((TextView) b.getChildAt(c)).setGravity(Gravity.CENTER);
            b.setSelected(on);
            b.setContentDescription(Schedule.dayName(date.getDayOfWeek()) + ", " + Schedule.dateText(date) + (isToday ? ", сегодня" : ""));
            b.setOnClickListener(v -> { Ui.tick(v); pick(k); });
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(a.dp(2), 0, a.dp(2), 0);
            days.addView(b, lp);
        }
        renderDay(t, mon.plusDays(sel));
    }

    private void pick(int d) {
        sel = d;
        if (last != null) render(last);
    }

    private void renderDay(MainActivity.Tick t, LocalDate date) {
        card.removeAllViews();
        boolean isToday = date.equals(t.now.toLocalDate());
        String off = Schedule.dayOff(date);
        // в каникулы и праздники показываем обычный день недели, а сверху пометку
        List<Schedule.Lesson> l = off != null ? Schedule.lessons(date.getDayOfWeek()) : Schedule.lessonsOn(date);
        LinearLayout head = Ui.horizontal(a);
        head.setPadding(a.dp(4), a.dp(10), a.dp(4), 0);
        TextView h = Ui.heading(Ui.text(a, Schedule.dayName(date.getDayOfWeek()), 22, Ui.TEXT, "sans-serif-medium"));
        head.addView(h);
        if (isToday) {
            TextView tag = Ui.text(a, "сегодня", 12, Ui.ON_ACCENT, "sans-serif-medium");
            tag.setPadding(a.dp(8), a.dp(2), a.dp(8), a.dp(2));
            tag.setBackground(Ui.round(a, Ui.ACCENT, 8, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = a.dp(10);
            head.addView(tag, lp);
        }
        card.addView(head);
        TextView m = Ui.text(a, Schedule.dateText(date) + " · " + Day.range(l), 14, Ui.MUTED, null);
        m.setPadding(a.dp(4), a.dp(2), a.dp(4), 0);
        card.addView(m);
        Day.Strip strip = new Day.Strip(a);
        boolean live = isToday && off == null;
        strip.set(l, live && t.s.kind == Schedule.Kind.LESSON && t.s.current != null ? t.s.current.num : -1, live ? t.min : -1);
        card.addView(strip, new LinearLayout.LayoutParams(-1, a.dp(8)));
        ((LinearLayout.LayoutParams) strip.getLayoutParams()).topMargin = a.dp(12);
        TextView note = Day.noteView(a, date);
        if (note != null) card.addView(note);
        LinearLayout tl = Day.timeline(a, l, live, t.s, t.min, live && t.d.kind == Schedule.Kind.LESSON ? t.d.progress : -1, "Уроков нет");
        card.addView(tl, Ui.mw(a, 10));
    }
}
