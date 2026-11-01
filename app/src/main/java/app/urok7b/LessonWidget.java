package app.urok7b;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.RemoteViews;

public class LessonWidget extends AppWidgetProvider {

    private static final int DOT_DONE = Color.parseColor("#6E6E6E");
    private static final int DOT_NOW = Color.parseColor("#FFFFFF");
    private static final int DOT_LEFT = Color.parseColor("#2E2E2E");

    @Override
    public void onUpdate(Context ctx, AppWidgetManager manager, int[] ids) {
        Updater.update(ctx);
    }

    @Override
    public void onEnabled(Context ctx) {
        Updater.update(ctx);
    }

    /** @return true, если на экране есть хотя бы один виджет. */
    static boolean updateAll(Context ctx, Updater.Display d) {
        int[] ids = Updater.widgetIds(ctx, LessonWidget.class);
        if (ids == null || ids.length == 0) return false;

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        rv.setTextViewText(R.id.w_label, d.label);
        rv.setTextViewText(R.id.w_title, d.title);
        rv.setTextViewText(R.id.w_sub, d.subtitle);
        rv.setViewVisibility(R.id.w_sub, d.subtitle.isEmpty() ? View.GONE : View.VISIBLE);

        // точки уроков дня: прошедшие серые, текущий белый, остальные тёмные
        if (d.dotTotal > 0) {
            rv.setViewVisibility(R.id.w_dots, View.VISIBLE);
            rv.setTextViewText(R.id.w_dots, dots(d));
        } else {
            rv.setViewVisibility(R.id.w_dots, View.GONE);
        }

        // обратный отсчёт
        if (d.timer) {
            long base = SystemClock.elapsedRealtime() + (d.target - System.currentTimeMillis());
            rv.setViewVisibility(R.id.w_chrono, View.VISIBLE);
            rv.setChronometer(R.id.w_chrono, base, null, true);
            rv.setChronometerCountDown(R.id.w_chrono, true);
        } else {
            rv.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime(), null, false);
            rv.setViewVisibility(R.id.w_chrono, View.GONE);
        }

        if (d.progress >= 0) {
            rv.setViewVisibility(R.id.w_progress, View.VISIBLE);
            rv.setProgressBar(R.id.w_progress, 100, d.progress, false);
        } else {
            rv.setViewVisibility(R.id.w_progress, View.GONE);
        }

        // следующий урок
        boolean hasNext = !d.nextSubject.isEmpty();
        rv.setViewVisibility(R.id.w_next_row, hasNext ? View.VISIBLE : View.GONE);
        rv.setTextViewText(R.id.w_next_head, d.nextHead);
        rv.setTextViewText(R.id.w_next_subj, d.nextSubject);
        rv.setTextViewText(R.id.w_next_room, d.nextRoom);
        rv.setViewVisibility(R.id.w_next_room, d.nextRoom.isEmpty() ? View.GONE : View.VISIBLE);

        rv.setOnClickPendingIntent(R.id.w_root, Updater.openApp(ctx));
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, rv);
        return true;
    }

    private static CharSequence dots(Updater.Display d) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 1; i <= d.dotTotal; i++) {
            int color = i == d.dotCurrent ? DOT_NOW : (i <= d.dotDone ? DOT_DONE : DOT_LEFT);
            int start = sb.length();
            sb.append('●');
            sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }
}
