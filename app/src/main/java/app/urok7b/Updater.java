package app.urok7b;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/** Единая точка: пересчитать состояние, обновить уведомление и виджет, завести следующий будильник. */
public final class Updater {

    public static final String CHANNEL = "lesson_now";
    public static final int NOTIF_ID = 7;
    public static final String ACTION_TICK = "app.urok7b.TICK";
    public static final String PREFS = "settings";
    public static final String KEY_NOTIF = "notif_enabled";

    // Чёрный минимализм: только белый и оттенки серого.
    public static final int C_LESSON = Color.parseColor("#FFFFFF");
    public static final int C_BREAK = Color.parseColor("#FFFFFF");
    public static final int C_BEFORE = Color.parseColor("#FFFFFF");
    public static final int C_OFF = Color.parseColor("#8A8A8A");
    public static final int C_MUTED = Color.parseColor("#7A7A7A");
    public static final int C_DIM = Color.parseColor("#333333");
    public static final int C_NOTIF = Color.parseColor("#8E8E93");

    /** Готовые тексты для уведомления, виджета и экрана приложения. */
    public static final class Display {
        public String label;       // "УРОК 3 / 8"
        public String title;       // крупно в виджете
        public String subtitle;    // под заголовком
        public String nextHead;    // "ДАЛЬШЕ", "ЗАВТРА 8:00"...
        public String nextSubject;
        public String nextRoom;
        public String notifTitle;  // заголовок уведомления
        public String line1;       // строки уведомления
        public String line2;
        public String subText;
        public int accent;
        public boolean timer;      // показывать обратный отсчёт
        public long target;
        public int progress = -1;  // 0..100, -1 — нет
        public boolean isBreak;
        public int dotTotal;       // точки уроков за день (0 — не показывать)
        public int dotDone;        // сколько уроков уже прошло
        public int dotCurrent;     // номер текущего урока (0 — нет)
    }

    private Updater() {}

    public static void update(Context ctx) {
        ZonedDateTime now = ZonedDateTime.now();
        Schedule.Status s = Schedule.compute(now);
        Display d = describe(s, now.toInstant().toEpochMilli());
        boolean shown = updateNotification(ctx, s, d);
        boolean widgets = LessonWidget.updateAll(ctx, d);
        scheduleNext(ctx, s, now, shown || widgets);
    }

    private static String room(Schedule.Lesson l) {
        return l == null ? "" : l.room;
    }

    public static Display describe(Schedule.Status s, long now) {
        Display d = new Display();
        d.target = s.targetMillis;
        String left = Schedule.fmtLeft(s.targetMillis - now);
        Schedule.Lesson n = s.next;
        String nextInfo = n == null ? "" : n.subject + " · " + n.roomText();
        d.nextHead = "ДАЛЬШЕ";
        d.nextSubject = n == null ? "" : n.subject;
        d.nextRoom = room(n);

        switch (s.kind) {
            case LESSON: {
                Schedule.Lesson c = s.current;
                d.accent = C_LESSON;
                d.timer = true;
                d.progress = pct(s, now);
                d.dotTotal = s.todayCount;
                d.dotDone = c.num - 1;
                d.dotCurrent = c.num;
                d.label = "УРОК " + c.num + " / " + s.todayCount;
                d.title = c.subject;
                d.subtitle = c.roomText() + " · звонок " + c.endText();
                d.notifTitle = c.subject + " · осталось " + left;
                d.subText = "Урок " + c.num + "/" + s.todayCount + " · " + c.roomText();
                if (n != null) {
                    d.line1 = "Звонок на перемену в " + c.endText();
                    d.line2 = "Дальше: " + nextInfo;
                } else {
                    d.line1 = "Звонок домой в " + c.endText();
                    d.line2 = "Последний урок";
                    d.nextSubject = "Домой";
                    d.nextRoom = c.endText();
                }
                break;
            }
            case BREAK: {
                d.accent = C_BREAK;
                d.timer = true;
                d.isBreak = true;
                d.progress = pct(s, now);
                d.dotTotal = s.todayCount;
                d.dotDone = s.current.num;
                d.label = "ПЕРЕМЕНА · " + n.num + " / " + s.todayCount;
                d.title = "Перемена";
                d.subtitle = "звонок на урок " + n.startText();
                d.notifTitle = "Перемена · осталось " + left;
                d.subText = "Перемена · дальше урок " + n.num + "/" + s.todayCount;
                d.line1 = "Звонок на урок в " + n.startText();
                d.line2 = "Дальше: " + nextInfo;
                break;
            }
            case BEFORE: {
                d.accent = C_BEFORE;
                d.timer = true;
                d.dotTotal = s.todayCount;
                d.label = "СЕГОДНЯ " + s.todayCount + " УРОКОВ";
                d.title = "До уроков";
                d.subtitle = "первый звонок " + n.startText() + " · до " + s.nextDayEnd;
                d.nextHead = "ПЕРВЫЙ";
                d.notifTitle = "До уроков " + left;
                d.subText = "Сегодня " + s.todayCount + " ур. · до " + s.nextDayEnd;
                d.line1 = "Первый звонок в " + n.startText();
                d.line2 = "Первый урок: " + nextInfo;
                break;
            }
            default: {
                d.accent = C_OFF;
                d.timer = false;
                d.label = s.kind == Schedule.Kind.AFTER ? "УРОКИ ЗАКОНЧИЛИСЬ" : "ВЫХОДНОЙ";
                d.title = s.kind == Schedule.Kind.AFTER ? "Свободен" : "Отдыхай";
                if (n != null) {
                    d.subtitle = s.nextDayLabel + " · " + s.nextDayCount + " ур., до " + s.nextDayEnd;
                    d.nextHead = s.nextDayLabel.toUpperCase() + " " + n.startText();
                } else {
                    d.subtitle = "";
                    d.nextHead = "";
                }
                d.notifTitle = d.title;
                d.line1 = d.subtitle;
                d.line2 = n == null ? "" : "Первый урок: " + nextInfo;
                d.subText = "";
            }
        }
        return d;
    }

    private static int pct(Schedule.Status s, long now) {
        long total = s.targetMillis - s.startMillis;
        if (total <= 0) return 0;
        long p = (now - s.startMillis) * 100 / total;
        return (int) Math.max(0, Math.min(100, p));
    }

    // ---------- уведомление ----------

    public static boolean notifEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_NOTIF, true);
    }

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void ensureChannel(NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Текущий урок",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Одно тихое уведомление: сколько осталось до звонка, следующий урок и кабинет");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private static boolean updateNotification(Context ctx, Schedule.Status s, Display d) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return false;
        if (!notifEnabled(ctx) || !s.notify) {
            nm.cancel(NOTIF_ID);
            return false;
        }
        ensureChannel(nm);

        String collapsed = d.line1 + (d.line2.isEmpty() ? "" : " · " + d.line2);

        Notification.Builder b = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(d.notifTitle)
                .setContentText(collapsed)
                .setSubText(d.subText)
                .setStyle(new Notification.BigTextStyle().bigText(d.line1 + "\n" + d.line2))
                .setColor(C_NOTIF)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(d.target)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(openApp(ctx));
        if (d.progress >= 0) b.setProgress(100, d.progress, false);

        try {
            nm.notify(NOTIF_ID, b.build());
            return true;
        } catch (SecurityException e) {
            return false; // нет разрешения на уведомления
        }
    }

    public static PendingIntent openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ---------- будильник ----------

    private static void scheduleNext(Context ctx, Schedule.Status s, ZonedDateTime now, boolean active) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        long nowMs = now.toInstant().toEpochMilli();
        long nextMidnight = LocalDate.from(now).plusDays(1).atStartOfDay(now.getZone())
                .toInstant().toEpochMilli();

        long when;
        boolean inSchool = s.kind == Schedule.Kind.LESSON || s.kind == Schedule.Kind.BREAK
                || (s.kind == Schedule.Kind.BEFORE && s.notify);
        if (inSchool && active) {
            when = (nowMs / 60_000 + 1) * 60_000; // следующая целая минута
        } else if (inSchool) {
            when = s.targetMillis;                 // хотя бы на звонке
        } else if (s.windowStartMillis > nowMs) {
            when = Math.min(s.windowStartMillis, nextMidnight);
        } else {
            when = nextMidnight;
        }
        when += 500; // чуть после звонка, чтобы точно попасть в новое состояние

        Intent i = new Intent(ctx, TickReceiver.class).setAction(ACTION_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    public static int[] widgetIds(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        return m.getAppWidgetIds(new ComponentName(ctx, LessonWidget.class));
    }
}
