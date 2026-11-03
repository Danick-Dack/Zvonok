package app.urok7b;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** Виджет 4×2: слева урок, кабинет и таймер, справа все уроки дня (текущий выделен). */
public class DayWidget extends AppWidgetProvider {

    private static final int[][] ROWS = {
            {R.id.d_r0, R.id.d_r0_t, R.id.d_r0_s, R.id.d_r0_r},
            {R.id.d_r1, R.id.d_r1_t, R.id.d_r1_s, R.id.d_r1_r},
            {R.id.d_r2, R.id.d_r2_t, R.id.d_r2_s, R.id.d_r2_r},
            {R.id.d_r3, R.id.d_r3_t, R.id.d_r3_s, R.id.d_r3_r},
            {R.id.d_r4, R.id.d_r4_t, R.id.d_r4_s, R.id.d_r4_r},
            {R.id.d_r5, R.id.d_r5_t, R.id.d_r5_s, R.id.d_r5_r},
            {R.id.d_r6, R.id.d_r6_t, R.id.d_r6_s, R.id.d_r6_r},
            {R.id.d_r7, R.id.d_r7_t, R.id.d_r7_s, R.id.d_r7_r}
    };

    @Override
    public void onUpdate(Context ctx, AppWidgetManager manager, int[] ids) {
        Updater.update(ctx);
    }

    @Override
    public void onEnabled(Context ctx) {
        Updater.update(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager m, int id, Bundle options) {
        Updater.update(ctx);
    }

    static void updateAll(Context ctx, Updater.Display d, Theme t, Schedule.Status s) {
        int[] ids = Updater.widgetIds(ctx, DayWidget.class);
        if (ids.length == 0) return;
        RemoteViews rv = build(ctx, d, t, s);
        rv.setOnClickPendingIntent(R.id.d_root, Updater.openApp(ctx));
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, rv);
    }

    static RemoteViews build(Context ctx, Updater.Display d, Theme t, Schedule.Status s) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_day);
        LessonWidget.background(rv, R.id.d_bg, t);

        rv.setTextViewText(R.id.d_label, d.label);
        rv.setTextViewText(R.id.d_title, d.title);
        Updater.bindRoom(rv, R.id.d_room, d.room);
        t.chip(rv, R.id.d_room, true);
        Updater.bindTimer(rv, R.id.d_chrono, R.id.d_big, d, t);
        String side = d.sideHead.isEmpty() ? "" : d.sideHead.toLowerCase() + " " + d.sideValue;
        rv.setTextViewText(R.id.d_side, side);
        rv.setViewVisibility(R.id.d_side, side.isEmpty() ? View.GONE : View.VISIBLE);
        Updater.bindProgress(rv, R.id.d_progress, t.progress ? d.progress : -1);
        t.progressColors(rv, R.id.d_progress);
        t.textColor(rv, t.text(), R.id.d_title, R.id.d_chrono, R.id.d_big);
        t.textColor(rv, t.muted(), R.id.d_label, R.id.d_side, R.id.d_day, R.id.d_empty);

        // сегодня, пока уроки не кончились; иначе ближайший учебный день
        boolean today = s.kind == Schedule.Kind.BEFORE || s.kind == Schedule.Kind.LESSON || s.kind == Schedule.Kind.BREAK;
        List<Schedule.Lesson> list = today ? s.list
                : s.nextDate != null ? Schedule.lessonsOn(s.nextDate) : java.util.Collections.<Schedule.Lesson>emptyList();
        rv.setTextViewText(R.id.d_day, today ? "СЕГОДНЯ" : s.nextDayLabel != null ? s.nextDayLabel.toUpperCase() : "");

        // текущий урок (на перемене: следующий) и окно из 8 строк вокруг него
        int cur = -1;
        if (s.kind == Schedule.Kind.LESSON) cur = s.index;
        else if (s.kind == Schedule.Kind.BREAK) cur = s.index + 1;
        int from = Math.max(0, Math.min(cur - 2, list.size() - ROWS.length));
        if (cur < 0) from = 0;
        int nowBg = t.light() ? R.drawable.row_now_light : R.drawable.row_now_dark;
        for (int i = 0; i < ROWS.length; i++) {
            int[] r = ROWS[i];
            int k = from + i;
            if (k >= list.size()) {
                rv.setViewVisibility(r[0], list.isEmpty() ? View.GONE : View.INVISIBLE);
                continue;
            }
            Schedule.Lesson l = list.get(k);
            rv.setViewVisibility(r[0], View.VISIBLE);
            rv.setTextViewText(r[1], Schedule.hm(l.start));
            rv.setTextViewText(r[2], l.changed ? l.subject + " ·" : l.subject);
            rv.setTextViewText(r[3], l.room);
            boolean now = k == cur, past = today && cur >= 0 ? k < cur : false;
            rv.setInt(r[0], "setBackgroundResource", now ? nowBg : 0);
            int main = past ? t.muted() : t.text();
            t.textColor(rv, main, r[2]);
            t.textColor(rv, now ? t.text() : t.muted(), r[1], r[3]);
        }
        rv.setViewVisibility(R.id.d_empty, list.isEmpty() ? View.VISIBLE : View.GONE);
        rv.setTextViewText(R.id.d_empty, d.holiday ? "Каникулы" : "Уроков нет");
        return rv;
    }
}
