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

    static boolean updateAll(Context ctx, Updater.Display d) {
        int[] ids = Updater.widgetIds(ctx, MiniWidget.class);
        if (ids.length == 0) return false;

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_mini);
        rv.setTextViewText(R.id.m_label, d.miniLabel);
        Updater.bindTimer(rv, R.id.m_chrono, R.id.m_big, d);
        if (d.miniRoom.isEmpty() && d.miniSub.isEmpty()) {
            rv.setViewVisibility(R.id.m_right, View.GONE);
        } else {
            rv.setViewVisibility(R.id.m_right, View.VISIBLE);
            Updater.bindRoom(rv, R.id.m_room, d.miniRoom);
            rv.setTextViewText(R.id.m_sub, d.miniSub);
        }
        Updater.bindProgress(rv, R.id.m_progress, d.progress);

        rv.setOnClickPendingIntent(R.id.m_root, Updater.openApp(ctx));
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, rv);
        return true;
    }
}
