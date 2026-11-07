package app.urok7b;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/** Общее у вкладок «Сейчас» и «Неделя»: лента уроков с переменами, полоска дня и пометка дня. */
final class Day {
    /** Метка полоски идущего урока: экран «Сейчас» двигает её на каждом тике, не пересобирая список. */
    static final String CUR_BAR = "cur_progress";


    private Day() {}

    /** «8 уроков · 8:00–15:25» или «уроков нет». */
    static String range(List<Schedule.Lesson> l) {
        return l.isEmpty() ? "уроков нет"
                : Schedule.lessonsWord(l.size()) + " · " + l.get(0).startText() + "–" + l.get(l.size() - 1).endText();
    }

    /** Пометка дня, как на сайте: {цвет, текст} или null. Каникулы зелёные, замены жёлтые, отмены красные. */
    static Object[] note(LocalDate date) {
        String off = Schedule.dayOff(date);
        if (off != null) return new Object[]{Ui.OK, off + ". Уроков нет, ниже обычное расписание этого дня."};
        org.json.JSONObject ch = Schedule.changesOn(date);
        if (ch == null) return null;
        List<Integer> gone = Schedule.cancelledOn(date);
        boolean changed = !gone.isEmpty();
        for (Schedule.Lesson l : Schedule.lessonsOn(date)) changed |= l.changed || l.shortBell;
        String n = Schedule.clean(ch.optString("note", ""));
        StringBuilder t = new StringBuilder(!n.isEmpty() ? n : changed ? "В этот день расписание изменено" : "");
        if (!gone.isEmpty()) {
            if (t.length() > 0) t.append(". ");
            t.append(gone.size() > 1 ? "Отменены уроки " : "Отменён урок ");
            for (int i = 0; i < gone.size(); i++) t.append(i > 0 ? ", " : "").append(gone.get(i));
        }
        return t.length() == 0 ? null : new Object[]{gone.isEmpty() ? Ui.WARN : Ui.BAD, t.toString()};
    }

    /** Плашка пометки дня (или null). */
    static TextView noteView(Context c, LocalDate date) {
        Object[] n = note(date);
        if (n == null) return null;
        TextView t = Ui.text(c, (String) n[1], 14, Ui.TEXT, null);
        t.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        t.setBackground(Ui.round(c, ((int) n[0] & 0x00FFFFFF) | 0x26000000, 14, 0));
        t.setLayoutParams(Ui.mw(c, 10));
        return t;
    }

    /**
     * Уроки дня с переменами. live: это сегодня, отмечаем прошедшие, идущий и следующий.
     * progress: сколько процентов идущего урока прошло (-1, если урока сейчас нет). Его полоска помечена CUR_BAR.
     */
    static LinearLayout timeline(Context c, List<Schedule.Lesson> list, boolean live, Schedule.Status s,
                                 int nowMin, int progress, String empty) {
        LinearLayout box = Ui.vertical(c);
        if (list.isEmpty()) {
            TextView e = Ui.text(c, empty, 15, Ui.MUTED, null);
            e.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), 0, Ui.dp(c, 8));
            box.addView(e);
            return box;
        }
        for (int i = 0; i < list.size(); i++) {
            Schedule.Lesson x = list.get(i);
            if (i > 0) {
                int len = x.start - list.get(i - 1).end;
                boolean on = live && s.kind == Schedule.Kind.BREAK && s.next != null && s.next.num == x.num;
                if (len > 0) box.addView(row(c, null, (on ? "Сейчас " : "") + (len > 25 ? "окно " : "перемена ") + len + " мин",
                        on, false, false, -1, false, false));
            }
            boolean cur = live && s.kind == Schedule.Kind.LESSON && s.current != null && s.current.num == x.num;
            boolean next = live && (s.kind == Schedule.Kind.BREAK || s.kind == Schedule.Kind.BEFORE) && s.next != null && s.next.num == x.num;
            box.addView(row(c, x, null, cur, next, live && !cur && x.end <= nowMin, cur ? progress : -1,
                    i == 0, i == list.size() - 1));
        }
        return box;
    }

    /** Строка ленты: время | точка на линии | урок с кабинетом (или перемена, если x == null). */
    private static View row(Context c, Schedule.Lesson x, String gap, boolean cur, boolean next, boolean past,
                            int progress, boolean first, boolean last) {
        LinearLayout r = Ui.horizontal(c);
        r.setGravity(Gravity.TOP);
        if (past) r.setAlpha(0.45f);

        LinearLayout time = Ui.vertical(c);
        time.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 50), ViewGroup.LayoutParams.WRAP_CONTENT));
        time.setPadding(0, Ui.dp(c, 13), 0, 0);
        if (x != null) {
            time.addView(Ui.text(c, x.startText(), 15, Ui.TEXT, "sans-serif-medium"));
            time.addView(Ui.text(c, x.endText(), 12, Ui.MUTED, null));
        }
        r.addView(time);

        FrameLayout node = new FrameLayout(c);
        node.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 22), ViewGroup.LayoutParams.MATCH_PARENT));
        View line = new View(c);
        line.setBackgroundColor(Ui.LINE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 2),
                last ? Ui.dp(c, 20) : ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
        lp.topMargin = first ? Ui.dp(c, 20) : 0;
        node.addView(line, lp);
        if (x != null) {
            View dot = new View(c);
            int size = cur ? 14 : 10, hue = Ui.vivid(x.subject);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(cur ? hue : past ? Ui.FAINT : Ui.BG);
            g.setStroke(Ui.dp(c, next ? 3 : 2), past ? Ui.FAINT : hue);
            dot.setBackground(g);
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(Ui.dp(c, size), Ui.dp(c, size), Gravity.CENTER_HORIZONTAL);
            np.topMargin = Ui.dp(c, cur ? 17 : 19);
            node.addView(dot, np);
        }
        r.addView(node);

        if (x == null) {
            TextView t = Ui.text(c, gap, 12, cur ? Ui.SOFT : Ui.MUTED, cur ? "sans-serif-medium" : null);
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 5), 0, Ui.dp(c, 5));
            r.addView(t);
            return r;
        }
        LinearLayout box = Ui.horizontal(c);
        box.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        if (cur) box.setBackground(Ui.round(c, Ui.soft(x.subject), 18, 0));
        else if (next) box.setBackground(Ui.round(c, Ui.CARD, 18, Ui.vivid(x.subject)));
        LinearLayout.LayoutParams bp = Ui.weight(1);
        bp.leftMargin = Ui.dp(c, 6);
        box.setLayoutParams(bp);
        LinearLayout mid = Ui.vertical(c);
        mid.setLayoutParams(Ui.weight(1));
        mid.addView(Ui.text(c, x.subject, 16, Ui.TEXT, cur ? "sans-serif-medium" : null));
        String mark = x.changed ? "замена" : x.shortBell ? "сокращённый" : "";
        TextView sub = Ui.text(c, (cur ? "Идёт, урок " + x.num : next ? "Следующий, урок " + x.num
                        : "Урок " + x.num + " · " + (x.end - x.start) + " мин") + (mark.isEmpty() ? "" : " · " + mark), 13,
                cur || next ? Ui.strong(x.subject) : mark.isEmpty() ? Ui.MUTED : Ui.WARN, cur || next ? "sans-serif-medium" : null);
        sub.setPadding(0, Ui.dp(c, 2), 0, 0);
        mid.addView(sub);
        if (progress >= 0) {
            ProgressBar pb = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(progress);
            pb.setTag(CUR_BAR);
            pb.setProgressDrawable(c.getDrawable(R.drawable.progress_mono));
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.vivid(x.subject)));
            pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.TRACK));
            pb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 3));
            pl.topMargin = Ui.dp(c, 8);
            mid.addView(pb, pl);
        }
        box.addView(mid);
        if (!x.room.isEmpty()) {
            TextView chip = Ui.room(c, x.room, x.subject, cur, 14);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.leftMargin = Ui.dp(c, 10);
            box.addView(chip, cp);
        }
        // диктор читает строку целиком: «8:50–9:35. Физика, кабинет 48. Идёт, урок 2»
        r.setContentDescription(x.startText() + "–" + x.endText() + ". " + x.subject
                + (x.room.isEmpty() ? "" : ", " + Ui.roomSpoken(x.room)) + ". " + sub.getText());
        r.setFocusable(true);
        r.addView(box);
        return r;
    }

    /** Полоска дня: отрезок на каждый урок в цвете предмета, длина по времени, промежутки это перемены. */
    static final class Strip extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private List<Schedule.Lesson> list;
        private int cur = -1, nowMin = -1;

        Strip(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void set(List<Schedule.Lesson> list, int curNum, int nowMin) {
            this.list = list;
            this.cur = curNum;
            this.nowMin = nowMin;
            setVisibility(list == null || list.isEmpty() ? GONE : VISIBLE);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (list == null || list.isEmpty()) return;
            int a = list.get(0).start, total = list.get(list.size() - 1).end - a;
            float w = getWidth(), h = getHeight(), gap = Ui.dp(getContext(), 2), r = h / 2;
            for (Schedule.Lesson l : list) {
                float x0 = w * (l.start - a) / total, x1 = w * (l.end - a) / total - gap;
                paint.setColor(Ui.vivid(l.subject));
                paint.setAlpha(l.num == cur ? 255 : nowMin >= 0 && l.end <= nowMin ? 70 : 150);
                box.set(x0, 0, Math.max(x0 + gap, x1), h);
                c.drawRoundRect(box, r, r, paint);
            }
        }
    }
}
