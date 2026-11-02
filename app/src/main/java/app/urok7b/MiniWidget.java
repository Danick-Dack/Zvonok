package app.urok7b;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

/** Маленький виджет 2×1: таймер до звонка слева, кабинет и предмет следующего урока справа. */
public class MiniWidget extends AppWidgetProvider {

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
        int[] ids = Updater.widgetIds(ctx, MiniWidget.class);
        if (ids.length == 0) return false;
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        for (int id : ids) {
            RemoteViews rv = build(ctx, d, t);
            int h = LessonWidget.heightDp(m, id);
            if (h > 0 && h < 72) {
                // низкая ячейка: таймер меньше, полоску прогресса прячем
                rv.setTextViewTextSize(R.id.m_chrono, android.util.TypedValue.COMPLEX_UNIT_SP, 22);
                rv.setTextViewTextSize(R.id.m_big, android.util.TypedValue.COMPLEX_UNIT_SP, 22);
                rv.setViewVisibility(R.id.m_progress, View.GONE);
            }
            rv.setOnClickPendingIntent(R.id.m_root, Updater.openApp(ctx));
            m.updateAppWidget(id, rv);
        }
        return true;
    }

    static RemoteViews build(Context ctx, Updater.Display d, Theme t) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_mini);
        LessonWidget.background(rv, R.id.m_bg, t);
        rv.setTextViewText(R.id.m_label, d.miniLabel);
        Updater.bindTimer(rv, R.id.m_chrono, R.id.m_big, d, t);
        boolean right = t.next && !(d.miniRoom.isEmpty() && d.miniSub.isEmpty());
        rv.setViewVisibility(R.id.m_right, right ? View.VISIBLE : View.GONE);
        Updater.bindRoom(rv, R.id.m_room, d.miniRoom);
        t.chip(rv, R.id.m_room, false);
        rv.setTextViewText(R.id.m_sub, d.miniSub);
        Updater.bindProgress(rv, R.id.m_progress, t.progress ? d.progress : -1);
        t.progressColors(rv, R.id.m_progress);
        t.textColor(rv, t.text(), R.id.m_chrono, R.id.m_big);
        t.textColor(rv, t.muted(), R.id.m_label, R.id.m_sub);
        return rv;
    }
}
