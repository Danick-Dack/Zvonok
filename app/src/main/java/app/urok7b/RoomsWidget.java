package app.urok7b;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Виджет «Кабинеты»: какие кабинеты свободны сейчас и на следующем уроке (с заменами, как во вкладке «Кабинеты»).
 * Перерисовывается только на звонках вместе с остальными виджетами, телефон не будит.
 */
public class RoomsWidget extends AppWidgetProvider {

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

    static void updateAll(Context ctx, ZonedDateTime now, Theme t) {
        int[] ids = Updater.widgetIds(ctx, RoomsWidget.class);
        if (ids.length == 0) return;
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, build(ctx, now, t));
    }

    static RemoteViews build(Context ctx, ZonedDateTime now, Theme t) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_rooms);
        LessonWidget.background(rv, R.id.r_bg, t);
        Intent open = new Intent(ctx, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, "rooms")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        rv.setOnClickPendingIntent(R.id.r_root, PendingIntent.getActivity(ctx, 21, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        t.textColor(rv, t.muted(), R.id.r_head, R.id.r_head2);
        t.textColor(rv, t.text(), R.id.r_list);
        t.textColor(rv, t.soft(), R.id.r_list2);

        LocalDate today = now.toLocalDate();
        int min = now.getHour() * 60 + now.getMinute();
        String off = Schedule.offDay(today);
        Map<String, List<Schedule.Use>> use = Schedule.roomDay(today);
        List<Integer> slots = Schedule.slots(today, use);
        int[][] bells = Schedule.dayBells(today);
        int live = 0, next = 0;
        if (off == null) {
            for (int n : slots) if (live == 0 && min >= bells[n - 1][0] && min < bells[n - 1][1]) live = n;
            for (int n : slots) if (next == 0 && bells[n - 1][0] > min) next = n;
        }
        String head, list, head2 = "", list2 = "";
        if (off != null || slots.isEmpty()) {
            head = (off != null ? off : "Сегодня уроков нет").toUpperCase();
            list = "Все кабинеты свободны";
        } else if (live == 0 && next == 0) {
            head = "УРОКИ ЗАКОНЧИЛИСЬ";
            list = "Все кабинеты свободны";
        } else {
            int first = live > 0 ? live : next;
            List<String> free = Schedule.freeRooms(use, first);
            head = live > 0 ? "СВОБОДНЫ СЕЙЧАС" + count(free) + " · " + live + " УРОК"
                    : "СВОБОДНЫ НА " + next + " УРОКЕ" + count(free) + " · " + Schedule.hm(bells[next - 1][0]);
            list = rooms(free);
            int after = 0;
            for (int n : slots) if (after == 0 && n > first) after = n;
            if (after > 0) {
                List<String> free2 = Schedule.freeRooms(use, after);
                head2 = "НА " + after + " УРОКЕ" + count(free2) + " · " + Schedule.hm(bells[after - 1][0]);
                list2 = rooms(free2);
            }
        }
        rv.setTextViewText(R.id.r_head, head);
        rv.setTextViewText(R.id.r_list, list);
        rv.setTextViewText(R.id.r_head2, head2);
        rv.setTextViewText(R.id.r_list2, list2);
        rv.setViewVisibility(R.id.r_head2, head2.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setViewVisibility(R.id.r_list2, list2.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setContentDescription(R.id.r_root, head.toLowerCase() + ": " + list + (head2.isEmpty() ? "" : ". " + head2.toLowerCase() + ": " + list2));
        return rv;
    }

    /** Сколько свободно: длинный список в виджете обрезается, а число видно всегда. */
    private static String count(List<String> l) {
        return l.isEmpty() ? "" : ": " + l.size();
    }

    private static String rooms(List<String> l) {
        return l.isEmpty() ? "свободных нет" : String.join("  ", l);
    }
}
