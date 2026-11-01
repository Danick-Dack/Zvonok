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
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Единая точка: пересчитать состояние, обновить уведомление и виджеты, завести следующий будильник.
 *
 * Экономия батареи:
 *  • все будильники «не будящие» (RTC): спящий телефон приложение не будит никогда;
 *  • пока экран выключен, поминутных обновлений нет совсем — только на звонках;
 *  • при включении экрана служба сразу обновляет всё одним вызовом;
 *  • таймеры в уведомлении и виджетах тикают сами (Chronometer), приложение для этого не нужно.
 */
public final class Updater {

    public static final String CHANNEL = "lesson_live";
    public static final int NOTIF_ID = 7;
    public static final String ACTION_TICK = "app.urok7b.TICK";
    public static final String ACTION_REPOST = "app.urok7b.REPOST";
    public static final String PREFS = "settings";
    public static final String KEY_NOTIF = "notif_enabled";

    // Палитра: чистый чёрный и оттенки белого.
    static final int WHITE = Color.WHITE;
    static final int DOT_DONE = Color.parseColor("#5A5A5A");
    static final int DOT_NOW = Color.WHITE;
    static final int DOT_LEFT = Color.parseColor("#262626");

    /** Готовые тексты для уведомления, виджетов и главного экрана. */
    public static final class Display {
        public Schedule.Kind kind;
        public boolean live;          // идут уроки (урок, перемена, последний час до уроков)
        public String label = "";     // "УРОК 3 / 7"
        public String title = "";     // "Физика"
        public String room = "";      // кабинет текущего урока
        public boolean timer;         // обратный отсчёт до target
        public long target;
        public String big = "";       // крупно, когда таймера нет: "8:00"
        public String sideHead = "";  // "ЗВОНОК"
        public String sideValue = ""; // "10:30"
        public String sideShort = ""; // то же, короче (для виджета)
        public String statHead = "";  // "ДОМОЙ В"
        public String statValue = ""; // "14:30"
        public int progress = -1;     // 0..100, -1 — нет
        public int dotTotal;          // точки уроков дня (0 — не показывать)
        public int dotDone;
        public int dotCurrent;
        public String nextHead = "";  // "ДАЛЕЕ · 10:50"
        public String nextSubject = "";
        public String nextRoom = "";
        public String foot = "";      // нижняя строка
        public String notifTitle = "";   // стандартный текст (экран блокировки MIUI, часы)
        public String notifText = "";
        public String compactTitle = ""; // свёрнутое уведомление
        public String compactText = "";
        public String miniLabel = "";    // виджет 2×1
        public String miniRoom = "";
        public String miniSub = "";
        public String sig = "";          // меняется только при смене урока / перемены / дня
    }

    private static String lastSig = "";

    private Updater() {}

    /** Полное обновление (открыли приложение, включили экран, перезагрузка...). */
    public static void update(Context ctx) {
        update(ctx, true);
    }

    /**
     * @param force false — плановый тик: если экран выключен и ничего не поменялось,
     *              ничего не перерисовываем, только заводим следующий будильник.
     */
    public static void update(Context ctx, boolean force) {
        ZonedDateTime now = ZonedDateTime.now();
        long nowMs = now.toInstant().toEpochMilli();
        Schedule.Status s = Schedule.compute(now);
        Display d = describe(s, nowMs);
        boolean screenOn = isScreenOn(ctx);

        boolean notif = notifEnabled(ctx);
        boolean widgets = Updater.widgetIds(ctx, LessonWidget.class).length > 0
                || Updater.widgetIds(ctx, MiniWidget.class).length > 0;

        if (force || screenOn || !d.sig.equals(lastSig)) {
            lastSig = d.sig;
            notif = updateNotification(ctx, d);
            LessonWidget.updateAll(ctx, d);
            MiniWidget.updateAll(ctx, d);
        }
        scheduleNext(ctx, s, d, nowMs, screenOn, notif || widgets);
    }

    static boolean isScreenOn(Context ctx) {
        PowerManager pm = ctx.getSystemService(PowerManager.class);
        return pm == null || pm.isInteractive();
    }

    // ---------- тексты ----------

    public static Display describe(Schedule.Status s, long now) {
        Display d = new Display();
        d.kind = s.kind;
        d.target = s.targetMillis;
        String left = Schedule.fmtLeft(s.targetMillis - now);
        Schedule.Lesson n = s.next;

        switch (s.kind) {
            case LESSON: {
                Schedule.Lesson c = s.current;
                d.live = true;
                d.timer = true;
                d.progress = pct(s, now);
                d.dotTotal = s.todayCount;
                d.dotDone = c.num - 1;
                d.dotCurrent = c.num;
                d.label = "УРОК " + c.num + " / " + s.todayCount;
                d.title = c.subject;
                d.room = c.room;
                d.sideHead = "ЗВОНОК";
                d.sideValue = d.sideShort = c.endText();
                d.statHead = "ДОМОЙ В";
                d.statValue = s.todayEnd;
                int rest = s.todayCount - c.num;
                d.foot = rest == 0 ? "Последний урок, дальше домой"
                        : "Потом ещё " + Schedule.lessonsWord(rest) + " · до " + s.todayEnd;
                d.notifTitle = c.subject + " · осталось " + left;
                if (n != null) {
                    d.nextHead = "ДАЛЕЕ · " + n.startText();
                    d.nextSubject = n.subject;
                    d.nextRoom = n.room;
                    d.notifText = "Звонок в " + c.endText() + " → " + n.subject + ", " + n.roomText();
                    d.compactText = "Звонок " + c.endText() + " → " + n.subject + " · " + n.room;
                    d.miniRoom = n.room;
                    d.miniSub = n.subject;
                } else {
                    d.nextHead = "ПОСЛЕДНИЙ УРОК";
                    d.nextSubject = "Домой в " + c.endText();
                    d.notifText = "Последний урок · домой в " + c.endText();
                    d.compactText = d.notifText;
                    d.miniRoom = "домой";
                    d.miniSub = "в " + c.endText();
                }
                d.compactTitle = c.subject + (c.room.isEmpty() ? "" : " · " + c.roomText());
                d.miniLabel = "УРОК " + c.num + "/" + s.todayCount;
                break;
            }
            case BREAK: {
                Schedule.Lesson p = s.current;
                d.live = true;
                d.timer = true;
                d.progress = pct(s, now);
                d.dotTotal = s.todayCount;
                d.dotDone = p.num;
                d.label = "ПЕРЕМЕНА · " + (n.start - p.end) + " МИН";
                d.title = "Перемена";
                d.sideHead = "НА УРОК";
                d.sideValue = d.sideShort = n.startText();
                d.statHead = "ДОМОЙ В";
                d.statValue = s.todayEnd;
                d.nextHead = "УРОК " + n.num + " · " + n.startText();
                d.nextSubject = n.subject;
                d.nextRoom = n.room;
                d.foot = "Осталось " + Schedule.lessonsWord(s.todayCount - p.num) + " · до " + s.todayEnd;
                d.notifTitle = "Перемена · осталось " + left;
                d.notifText = "Звонок в " + n.startText() + " → " + n.subject + ", " + n.roomText();
                d.compactTitle = "Перемена → " + n.subject;
                d.compactText = (n.room.isEmpty() ? "" : n.roomText() + " · ") + "звонок " + n.startText();
                d.miniLabel = "ПЕРЕМЕНА";
                d.miniRoom = n.room;
                d.miniSub = n.subject;
                break;
            }
            case BEFORE: {
                d.live = s.notify;
                d.timer = s.notify; // отсчёт — только в последний час перед уроками
                d.dotTotal = s.todayCount;
                d.label = s.notify ? "ДО УРОКОВ" : "СЕГОДНЯ";
                d.title = s.notify ? "Скоро уроки" : "Сегодня " + Schedule.lessonsWord(s.todayCount);
                d.big = n.startText();
                d.sideHead = s.notify ? "ЗВОНОК" : "ДЕНЬ";
                d.sideValue = s.notify ? n.startText() : "Сегодня";
                d.sideShort = d.sideValue;
                d.statHead = "УРОКОВ";
                d.statValue = s.todayCount + " · до " + s.todayEnd;
                d.nextHead = "ПЕРВЫЙ УРОК · " + n.startText();
                d.nextSubject = n.subject;
                d.nextRoom = n.room;
                d.foot = "Сегодня " + Schedule.lessonsWord(s.todayCount) + " · "
                        + n.startText() + "–" + s.todayEnd;
                d.notifTitle = s.notify ? "До уроков " + left : "Сегодня уроки с " + n.startText();
                d.notifText = "Первый: " + n.subject + ", " + n.roomText();
                d.compactTitle = s.notify ? "Скоро уроки → " + n.subject
                        : "Сегодня " + Schedule.lessonsWord(s.todayCount) + " с " + n.startText();
                d.compactText = (n.room.isEmpty() ? "" : n.roomText() + " · ")
                        + (s.notify ? "звонок " + n.startText() : n.subject);
                d.miniLabel = s.notify ? "ДО УРОКОВ" : "СЕГОДНЯ С";
                d.miniRoom = n.room;
                d.miniSub = n.subject;
                break;
            }
            default: {
                boolean after = s.kind == Schedule.Kind.AFTER;
                d.label = after ? "УРОКИ ЗАКОНЧИЛИСЬ" : "ВЫХОДНОЙ";
                d.title = after ? "Свободен" : "Отдыхай";
                if (n != null) {
                    d.big = n.startText();
                    d.sideHead = "ДЕНЬ";
                    d.sideValue = s.nextDayLabel;
                    d.sideShort = s.nextDayShort;
                    d.statHead = "УРОКОВ";
                    d.statValue = s.nextDayCount + " · до " + s.nextDayEnd;
                    d.nextHead = s.nextDayLabel.toUpperCase() + " · " + n.startText();
                    d.nextSubject = n.subject;
                    d.nextRoom = n.room;
                    d.foot = s.nextDayLabel + ": " + Schedule.lessonsWord(s.nextDayCount) + ", "
                            + n.startText() + "–" + s.nextDayEnd;
                    d.notifTitle = d.title + " · " + s.nextDayWhen + " с " + n.startText();
                    d.notifText = "Первый урок: " + n.subject + ", " + n.roomText();
                    d.compactText = (n.room.isEmpty() ? "" : n.roomText() + " · ") + n.subject;
                    d.miniLabel = s.nextDayShort.toUpperCase() + " С";
                    d.miniRoom = n.room;
                    d.miniSub = n.subject;
                } else {
                    d.big = "—";
                    d.notifTitle = d.title;
                    d.miniLabel = d.label;
                }
                d.compactTitle = d.notifTitle;
            }
        }
        // минуты в подпись не входят, иначе при выключенном экране экономии не будет
        d.sig = s.kind + "|" + (s.current == null ? 0 : s.current.num) + "|"
                + (s.next == null ? 0 : s.next.num) + "|" + d.timer + "|" + s.targetMillis;
        return d;
    }

    private static int pct(Schedule.Status s, long now) {
        long total = s.targetMillis - s.startMillis;
        if (total <= 0) return 0;
        long p = (now - s.startMillis) * 100 / total;
        return (int) Math.max(0, Math.min(100, p));
    }

    /** Точки уроков дня: прошедшие серые, текущий белый, остальные тёмные. */
    static CharSequence dots(Display d) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 1; i <= d.dotTotal; i++) {
            int color = i == d.dotCurrent ? DOT_NOW : (i <= d.dotDone ? DOT_DONE : DOT_LEFT);
            int start = sb.length();
            sb.append('●');
            sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    /** База для Chronometer: момент target в шкале elapsedRealtime. */
    static long chronoBase(long target) {
        return SystemClock.elapsedRealtime() + (target - System.currentTimeMillis());
    }

    // ---------- уведомление ----------

    public static boolean notifEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_NOTIF, true);
    }

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void ensureChannel(NotificationManager nm) {
        // старый «беззвучный» канал MIUI прятал на экране блокировки
        if (nm.getNotificationChannel("lesson_now") != null) nm.deleteNotificationChannel("lesson_now");
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        // DEFAULT, а не LOW: такие уведомления видны на экране блокировки. Звук и вибрация выключены.
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Текущий урок",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Одно уведомление: сколько осталось до звонка, следующий урок и кабинет");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.enableLights(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    /** Собрать уведомление для текущего момента (для службы). */
    public static Notification buildNow(Context ctx) {
        ZonedDateTime now = ZonedDateTime.now();
        Display d = describe(Schedule.compute(now), now.toInstant().toEpochMilli());
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) ensureChannel(nm);
        return build(ctx, d);
    }

    /** @return true, если уведомление показано. */
    private static boolean updateNotification(Context ctx, Display d) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return false;
        if (!notifEnabled(ctx)) {
            LessonService.stop(ctx);
            nm.cancel(NOTIF_ID);
            return false;
        }
        ensureChannel(nm);
        try {
            nm.notify(NOTIF_ID, build(ctx, d));
        } catch (RuntimeException e) {
            return false; // нет разрешения на уведомления
        }
        LessonService.ensureRunning(ctx);
        return true;
    }

    private static Notification build(Context ctx, Display d) {
        String pkg = ctx.getPackageName();

        // свёрнутый вид: две строки слева, большой таймер справа
        RemoteViews small = new RemoteViews(pkg, R.layout.notif_small);
        small.setTextViewText(R.id.n_title, d.compactTitle);
        small.setTextViewText(R.id.n_text, d.compactText);
        small.setViewVisibility(R.id.n_text, d.compactText.isEmpty() ? View.GONE : View.VISIBLE);
        bindTimer(small, R.id.n_chrono, R.id.n_big, d);

        // развёрнутый вид: всё сразу
        RemoteViews big = new RemoteViews(pkg, R.layout.notif_big);
        big.setTextViewText(R.id.n_label, d.label);
        big.setTextViewText(R.id.n_title, d.title);
        bindRoom(big, R.id.n_room, d.room);
        if (d.dotTotal > 0) {
            big.setViewVisibility(R.id.n_dots, View.VISIBLE);
            big.setTextViewText(R.id.n_dots, dots(d));
        } else {
            big.setViewVisibility(R.id.n_dots, View.GONE);
        }
        bindTimer(big, R.id.n_chrono, R.id.n_big, d);
        big.setTextViewText(R.id.n_side_head, d.sideHead);
        big.setTextViewText(R.id.n_side_value, d.sideValue);
        big.setTextViewText(R.id.n_stat_head, d.statHead);
        big.setTextViewText(R.id.n_stat_value, d.statValue);
        big.setViewVisibility(R.id.n_stat, d.statHead.isEmpty() ? View.GONE : View.VISIBLE);
        bindProgress(big, R.id.n_progress, d.progress);
        boolean hasNext = !d.nextSubject.isEmpty();
        big.setViewVisibility(R.id.n_next, hasNext ? View.VISIBLE : View.GONE);
        big.setTextViewText(R.id.n_next_head, d.nextHead);
        big.setTextViewText(R.id.n_next_subj, d.nextSubject);
        bindRoom(big, R.id.n_next_room, d.nextRoom);

        Notification.Builder b = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(d.notifTitle)
                .setContentText(d.notifText)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(small)
                .setCustomBigContentView(big)
                // чёрный фон всего уведомления (работает для уведомлений службы)
                .setColor(Color.BLACK)
                .setColorized(true)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false) // таймер уже есть внутри уведомления
                .setLocalOnly(true)
                // если всё-таки смахнули (Android 14+ это разрешает) — сразу вернуть
                .setDeleteIntent(repost(ctx))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STATUS)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setContentIntent(openApp(ctx));
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        Notification n = b.build();
        // «не очищается» кнопкой «Очистить всё» и не смахивается там, где система это уважает
        n.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        return n;
    }

    /** Таймер (если идёт отсчёт) или крупный текст на его месте. */
    static void bindTimer(RemoteViews rv, int chronoId, int bigId, Display d) {
        if (d.timer) {
            rv.setChronometer(chronoId, chronoBase(d.target), null, true);
            rv.setChronometerCountDown(chronoId, true);
            rv.setViewVisibility(chronoId, View.VISIBLE);
            rv.setViewVisibility(bigId, View.GONE);
        } else {
            rv.setChronometer(chronoId, SystemClock.elapsedRealtime(), null, false);
            rv.setViewVisibility(chronoId, View.GONE);
            rv.setTextViewText(bigId, d.big);
            rv.setViewVisibility(bigId, View.VISIBLE);
        }
    }

    static void bindRoom(RemoteViews rv, int id, String room) {
        boolean has = room != null && !room.isEmpty();
        rv.setViewVisibility(id, has ? View.VISIBLE : View.GONE);
        if (has) rv.setTextViewText(id, room);
    }

    static void bindProgress(RemoteViews rv, int id, int progress) {
        if (progress >= 0) {
            rv.setViewVisibility(id, View.VISIBLE);
            rv.setProgressBar(id, 100, progress, false);
        } else {
            rv.setViewVisibility(id, View.GONE);
        }
    }

    private static PendingIntent repost(Context ctx) {
        Intent i = new Intent(ctx, TickReceiver.class).setAction(ACTION_REPOST);
        return PendingIntent.getBroadcast(ctx, 3, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ---------- будильник ----------

    private static void scheduleNext(Context ctx, Schedule.Status s, Display d, long nowMs,
                                     boolean screenOn, boolean active) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        ZonedDateTime now = ZonedDateTime.now();
        long nextMidnight = LocalDate.from(now).plusDays(1).atStartOfDay(now.getZone())
                .toInstant().toEpochMilli();

        // ближайшая «граница»: звонок, начало окна перед уроками или полночь
        long boundary;
        if (d.live) {
            boundary = s.targetMillis;
        } else if (s.windowStartMillis > nowMs) {
            boundary = Math.min(s.windowStartMillis, nextMidnight);
        } else {
            boundary = nextMidnight;
        }

        long when = boundary;
        if (d.live && active) {
            // Каждую минуту — только когда на экран смотрят. Если экран выключен, а служба жива,
            // она сама разбудит обновление при включении экрана, и поминутные тики не нужны.
            boolean minuteTicks = screenOn || !LessonService.isRunning();
            if (minuteTicks) when = Math.min(boundary, (nowMs / 60_000 + 1) * 60_000);
        }
        when += 500; // чуть после звонка, чтобы точно попасть в новое состояние

        Intent i = new Intent(ctx, TickReceiver.class).setAction(ACTION_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExact(AlarmManager.RTC, when, pi);
            else am.setWindow(AlarmManager.RTC, when, 60_000, pi);
        } catch (SecurityException e) {
            am.setWindow(AlarmManager.RTC, when, 60_000, pi);
        }
    }

    public static int[] widgetIds(Context ctx, Class<?> provider) {
        try {
            int[] ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, provider));
            return ids == null ? new int[0] : ids;
        } catch (RuntimeException e) {
            return new int[0];
        }
    }
}
