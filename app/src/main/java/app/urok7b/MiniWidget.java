package app.urok7b;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

/** Маленький виджет 2×1: таймер до звонка и кабинет следующего урока. */
public class MiniWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager manager, int[] ids) {
        Updater.update(ctx);
    }

    @Override
    public void onEnabled(Context ctx) {
        Updater.update(ctx);
    }

    static boolean updateAll(Context ctx, Updater.Display d) {
        int[] ids = Updater.widgetIds(ctx, MiniWidget.class);
        if (ids == null || ids.length == 0) return false;

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_mini);
        rv.setTextViewText(R.id.m_label, d.miniLabel);

        if (d.timer) {
            long base = SystemClock.elapsedRealtime() + (d.target - System.currentTimeMillis());
            rv.setViewVisibility(R.id.m_chrono, View.VISIBLE);
            rv.setViewVisibility(R.id.m_title, View.GONE);
            rv.setChronometer(R.id.m_chrono, base, null, true);
            rv.setChronometerCountDown(R.id.m_chrono, true);
        } else {
            rv.setChronometer(R.id.m_chrono, SystemClock.elapsedRealtime(), null, false);
            rv.setViewVisibility(R.id.m_chrono, View.GONE);
            rv.setViewVisibility(R.id.m_title, View.VISIBLE);
            rv.setTextViewText(R.id.m_title, d.title);
        }

        rv.setTextViewText(R.id.m_next, d.miniNext);
        rv.setViewVisibility(R.id.m_next, d.miniNext.isEmpty() ? View.GONE : View.VISIBLE);

        rv.setOnClickPendingIntent(R.id.m_root, Updater.openApp(ctx));
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, rv);
        return true;
    }
}
