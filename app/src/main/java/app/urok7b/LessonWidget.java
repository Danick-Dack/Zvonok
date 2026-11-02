package app.urok7b;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

/** Виджет 2×2: урок, кабинет, таймер до звонка, прогресс и следующий урок. */
public class LessonWidget extends AppWidgetProvider {

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

    static boolean updateAll(Context ctx, Updater.Display d, Theme t) {
        int[] ids = Updater.widgetIds(ctx, LessonWidget.class);
        if (ids.length == 0) return false;
        RemoteViews rv = build(ctx, d, t);
        rv.setOnClickPendingIntent(R.id.w_root, Updater.openApp(ctx));
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, rv);
        return true;
    }

    /** Собрать вид виджета (используется и для предпросмотра в «Оформлении»). */
    static RemoteViews build(Context ctx, Updater.Display d, Theme t) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        background(rv, R.id.w_bg, t);

        rv.setTextViewText(R.id.w_label, d.label);
        if (d.dotTotal > 0 && t.dots) {
            rv.setViewVisibility(R.id.w_dots, View.VISIBLE);
            rv.setTextViewText(R.id.w_dots, Updater.dots(d, t));
        } else {
            rv.setViewVisibility(R.id.w_dots, View.GONE);
        }
        rv.setTextViewText(R.id.w_title, d.title);
        Updater.bindRoom(rv, R.id.w_room, d.room);
        t.chip(rv, R.id.w_room, true);

        Updater.bindTimer(rv, R.id.w_chrono, R.id.w_big, d, t);
        rv.setTextViewText(R.id.w_side_head, d.sideHead);
        rv.setTextViewText(R.id.w_side_value, d.sideShort);
        rv.setViewVisibility(R.id.w_side, d.sideHead.isEmpty() ? View.GONE : View.VISIBLE);
        Updater.bindProgress(rv, R.id.w_progress, t.progress ? d.progress : -1);
        t.progressColors(rv, R.id.w_progress);

        boolean hasNext = t.next && !d.nextSubject.isEmpty();
        rv.setViewVisibility(R.id.w_next, hasNext ? View.VISIBLE : View.GONE);
        rv.setTextViewText(R.id.w_next_subj, d.nextSubject);
        Updater.bindRoom(rv, R.id.w_next_room, d.nextRoom);
        t.chip(rv, R.id.w_next_room, false);

        t.textColor(rv, t.text(), R.id.w_title, R.id.w_chrono, R.id.w_big, R.id.w_side_value);
        t.textColor(rv, t.soft(), R.id.w_next_subj);
        t.textColor(rv, t.muted(), R.id.w_label, R.id.w_side_head, R.id.w_arrow);
        return rv;
    }

    /** Фон виджета: скруглённая форма, перекрашенная в цвет темы, с прозрачностью. */
    static void background(RemoteViews rv, int id, Theme t) {
        rv.setImageViewResource(id, t.shapeRes());
        rv.setInt(id, "setColorFilter", t.bg | 0xFF000000);
        rv.setInt(id, "setImageAlpha", Math.round(t.opacity * 2.55f));
    }
}
