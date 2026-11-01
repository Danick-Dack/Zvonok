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

    /** @return true, если на экране есть хотя бы один такой виджет. */
    static boolean updateAll(Context ctx, Updater.Display d) {
        int[] ids = Updater.widgetIds(ctx, LessonWidget.class);
        if (ids.length == 0) return false;

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        rv.setTextViewText(R.id.w_label, d.label);
        if (d.dotTotal > 0) {
            rv.setViewVisibility(R.id.w_dots, View.VISIBLE);
            rv.setTextViewText(R.id.w_dots, Updater.dots(d));
        } else {
            rv.setViewVisibility(R.id.w_dots, View.GONE);
        }
        rv.setTextViewText(R.id.w_title, d.title);
        Updater.bindRoom(rv, R.id.w_room, d.room);

        Updater.bindTimer(rv, R.id.w_chrono, R.id.w_big, d);
        rv.setTextViewText(R.id.w_side_head, d.sideHead);
        rv.setTextViewText(R.id.w_side_value, d.sideShort);
        rv.setViewVisibility(R.id.w_side, d.sideHead.isEmpty() ? View.GONE : View.VISIBLE);
        Updater.bindProgress(rv, R.id.w_progress, d.progress);

        boolean hasNext = !d.nextSubject.isEmpty();
        rv.setViewVisibility(R.id.w_next, hasNext ? View.VISIBLE : View.GONE);
        rv.setTextViewText(R.id.w_next_subj, d.nextSubject);
        Updater.bindRoom(rv, R.id.w_next_room, d.nextRoom);

        rv.setOnClickPendingIntent(R.id.w_root, Updater.openApp(ctx));
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, rv);
        return true;
    }
}
