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
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.RemoteViews;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Единая точка: пересчитать состояние, обновить уведомление и виджеты, завести следующий будильник.
 *
 * Экономия батареи:
 *  • все будильники «не будящие» (RTC): спящий телефон приложение не будит никогда;
 *  • пока экран выключен, поминутных обновлений нет совсем — только на звонках;
 *  • при включении экрана служба сразу обновляет всё одним вызовом;
 *  • таймеры тикают сами (Chronometer), а в экономном режиме их нет вовсе;
 *  • на каникулах не работает ничего: ни службы, ни будильников.
 */
public final class Updater {

    public static final String CHANNEL = "lesson_live";
    public static final int NOTIF_ID = 7;
    public static final String ACTION_TICK = "app.urok7b.TICK";
    public static final String ACTION_REPOST = "app.urok7b.REPOST";
    public static final String PREFS = "settings";
    public static final String KEY_NOTIF = "notif_enabled";
    public static final String KEY_SCHOOL_ONLY = "school_only";
    public static final String KEY_VACATION = "vacation";
    public static final String KEY_BUZZ = "buzz_before_lesson";   // вибрация за 2 мин до урока
    static final String ACTION_BUZZ = "app.urok7b.BUZZ";
    static final String CHANNEL_BUZZ = "lesson_buzz";
    static final int BUZZ_ID = 8;
    static final long BUZZ_LEAD = 2 * 60_000L;

    /** Готовые тексты для уведомления, виджетов и главного экрана. */
    public static final class Display {
        public Schedule.Kind kind;
        public boolean live;          // идут уроки (урок, перемена, последний час до уроков)
        public String label = "";     // "УРОК 3 ИЗ 7"
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
        public String nextHead = "";  // "ДАЛЬШЕ · 10:50"
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
        public boolean holiday;          // каникулы или праздник
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
        Schedule.load(ctx);
        ZonedDateTime now = ZonedDateTime.now();
        long nowMs = now.toInstant().toEpochMilli();
        Schedule.Status s = Schedule.compute(now);
        boolean vacation = prefs(ctx).getBoolean(KEY_VACATION, false);
        Display d = vacation ? vacation() : describe(s, nowMs);
        boolean screenOn = isScreenOn(ctx);
        Theme t = Theme.load(ctx);

        boolean notif = !vacation && notifWanted(ctx, d);
        boolean widgets = widgetIds(ctx, LessonWidget.class).length > 0
                || widgetIds(ctx, MiniWidget.class).length > 0
                || widgetIds(ctx, DayWidget.class).length > 0;

        if (force || screenOn || !d.sig.equals(lastSig)) {
            lastSig = d.sig;
            notif = updateNotification(ctx, d, t, notif);
            LessonWidget.updateAll(ctx, d, t);
            MiniWidget.updateAll(ctx, d, t);
            DayWidget.updateAll(ctx, d, t, s);
        }
        scheduleBuzz(ctx, now, !vacation && prefs(ctx).getBoolean(KEY_BUZZ, false));
        if (vacation) {
            cancelAlarm(ctx);
            return;
        }
        // без секунд таймер — это текст, и его надо обновлять раз в минуту, пока экран включён
        scheduleNext(ctx, s, d, nowMs, screenOn, notif || widgets);
    }

    /** Уведомление включено и (если выбрано «только в учебное время») сейчас учебное время. */
    static boolean notifWanted(Context ctx, Display d) {
        if (!notifEnabled(ctx)) return false;
        return !prefs(ctx).getBoolean(KEY_SCHOOL_ONLY, false) || d.live;
    }

    static boolean isScreenOn(Context ctx) {
        PowerManager pm = ctx.getSystemService(PowerManager.class);
        return pm == null || pm.isInteractive();
    }

    // ---------- тексты ----------

    static Display vacation() {
        Display d = new Display();
        d.kind = Schedule.Kind.DAY_OFF;
        d.label = "КАНИКУЛЫ";
        d.title = "Отдыхай";
        d.big = "☼";
        d.notifTitle = d.compactTitle = "Каникулы";
        d.miniLabel = "КАНИКУЛЫ";
        d.sig = "vacation";
        return d;
    }

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
                d.dotDone = (s.index < 0 ? c.num - 1 : s.index);
                d.dotCurrent = d.dotDone + 1;
                int idx = s.index < 0 ? c.num - 1 : s.index;
                d.label = (n == null ? "ПОСЛЕДНИЙ УРОК" : "УРОК " + (idx + 1) + " ИЗ " + s.todayCount) + (c.changed ? " · ЗАМЕНА" : "");
                d.title = c.subject;
                d.room = c.room;
                d.sideHead = "ЗВОНОК";
                d.sideValue = d.sideShort = c.endText();
                d.statHead = "ДОМОЙ В";
                d.statValue = s.todayEnd;
                int rest = s.todayCount - idx - 1;
                d.foot = rest <= 0 ? "Последний урок, дальше домой"
                        : "Потом ещё " + Schedule.lessonsWord(rest) + " · до " + s.todayEnd;
                d.notifTitle = c.subject + " · осталось " + left;
                if (n != null) {
                    d.nextHead = "ДАЛЬШЕ · " + n.startText() + (n.changed ? " · ЗАМЕНА" : "");
                    d.nextSubject = n.subject;
                    d.nextRoom = n.room;
                    d.notifText = "Звонок в " + c.endText() + " → " + n.subject
                            + (n.room.isEmpty() ? "" : ", " + n.roomText());
                    d.compactText = "Звонок " + c.endText() + " → " + n.subject
                            + (n.room.isEmpty() ? "" : " · " + n.room);
                    d.miniRoom = n.room;
                    d.miniSub = n.subject;
                } else {
                    d.nextHead = "ПОТОМ";
                    d.nextSubject = "Домой в " + c.endText();
                    d.notifText = "Последний урок · домой в " + c.endText();
                    d.compactText = d.notifText;
                    d.miniRoom = "домой";
                    d.miniSub = "в " + c.endText();
                }
                d.compactTitle = c.subject + (c.room.isEmpty() ? "" : " · " + c.roomText());
                d.miniLabel = n == null ? "ПОСЛЕДНИЙ" : "УРОК " + (idx + 1) + "/" + s.todayCount;
                break;
            }
            case BREAK: {
                Schedule.Lesson p = s.current;
                d.live = true;
                d.timer = true;
                d.progress = pct(s, now);
                d.dotTotal = s.todayCount;
                d.dotDone = (s.index < 0 ? p.num - 1 : s.index) + 1;
                d.label = (n.start - p.end > 25 ? "ОКНО · " : "ПЕРЕМЕНА · ") + (n.start - p.end) + " МИН";
                d.title = n.start - p.end > 25 ? "Окно" : "Перемена";
                d.sideHead = "НА УРОК";
                d.sideValue = d.sideShort = n.startText();
                d.statHead = "ДОМОЙ В";
                d.statValue = s.todayEnd;
                d.nextHead = "ИДИ В" + (n.changed ? " · ЗАМЕНА" : "");
                d.nextSubject = n.subject;
                d.nextRoom = n.room;
                d.foot = "Осталось " + Schedule.lessonsWord(s.todayCount - d.dotDone) + " · до " + s.todayEnd;
                d.notifTitle = d.title + " · осталось " + left;
                d.notifText = "Звонок в " + n.startText() + " → " + n.subject
                        + (n.room.isEmpty() ? "" : ", " + n.roomText());
                d.compactTitle = d.title + " → " + n.subject;
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
                d.label = s.notify ? "СКОРО УРОКИ" : "СЕГОДНЯ " + Schedule.lessonsWord(s.todayCount).toUpperCase();
                d.title = s.notify ? "Скоро уроки" : "Уроки с " + n.startText();
                d.big = n.startText();
                d.sideHead = s.notify ? "ЗВОНОК" : "ДЕНЬ";
                d.sideValue = s.notify ? n.startText() : "Сегодня";
                d.sideShort = d.sideValue;
                d.statHead = "УРОКОВ";
                d.statValue = s.todayCount + " · до " + s.todayEnd;
                d.nextHead = "ПЕРВЫЙ · " + n.startText() + (n.changed ? " · ЗАМЕНА" : "");
                d.nextSubject = n.subject;
                d.nextRoom = n.room;
                d.foot = "Сегодня " + Schedule.lessonsWord(s.todayCount) + " · "
                        + n.startText() + "–" + s.todayEnd;
                d.notifTitle = s.notify ? "До уроков " + left : "Сегодня уроки с " + n.startText();
                d.notifText = "Первый: " + n.subject + (n.room.isEmpty() ? "" : ", " + n.roomText());
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
                boolean vac = Schedule.isVacation(s.holiday);
                d.holiday = s.holiday != null;
                boolean longOff = d.holiday && s.nextDays > 1; // однодневный праздник как обычный вечер
                d.label = after ? "УРОКИ ЗАКОНЧИЛИСЬ" : d.holiday ? s.holiday.toUpperCase() : "ВЫХОДНОЙ";
                d.title = after ? "Свободен" : d.holiday ? (vac ? "Каникулы" : "Выходной") : "Отдыхай";
                if (n != null && longOff) {
                    // каникулы: крупно сколько дней до уроков
                    String date = Schedule.dateText(s.nextDate);
                    d.big = String.valueOf(s.nextDays);
                    d.sideHead = "ДО УРОКОВ";
                    d.sideValue = date;
                    d.sideShort = date;
                    d.statHead = "УРОКОВ";
                    d.statValue = s.nextDayCount + " · до " + s.nextDayEnd;
                    d.nextHead = date.toUpperCase() + " · " + n.startText();
                    d.nextSubject = n.subject;
                    d.nextRoom = n.room;
                    d.foot = "Уроки с " + date + ", " + n.startText();
                    d.notifTitle = d.title + " · уроки с " + date;
                    d.notifText = "Первый урок: " + n.subject + (n.room.isEmpty() ? "" : ", " + n.roomText());
                    d.compactText = d.notifText;
                    d.miniLabel = vac ? "КАНИКУЛЫ" : "ВЫХОДНОЙ";
                    d.miniRoom = "";
                    d.miniSub = "уроки с " + date;
                } else if (n != null) {
                    d.big = n.startText();
                    d.sideHead = "ДЕНЬ";
                    d.sideValue = s.nextDayLabel;
                    d.sideShort = s.nextDayShort;
                    d.statHead = "УРОКОВ";
                    d.statValue = s.nextDayCount + " · до " + s.nextDayEnd;
                    d.nextHead = s.nextDayLabel.toUpperCase() + " · " + Schedule.lessonsWord(s.nextDayCount).toUpperCase();
                    d.nextSubject = n.subject;
                    d.nextRoom = n.room;
                    d.foot = s.nextDayLabel + ": " + Schedule.lessonsWord(s.nextDayCount) + ", "
                            + n.startText() + "–" + s.nextDayEnd;
                    d.notifTitle = d.title + " · " + s.nextDayWhen + " с " + n.startText();
                    d.notifText = "Первый урок: " + n.subject + (n.room.isEmpty() ? "" : ", " + n.roomText());
                    d.compactText = (n.room.isEmpty() ? "" : n.roomText() + " · ") + n.subject;
                    d.miniLabel = s.nextDayShort.toUpperCase() + " С";
                    d.miniRoom = n.room;
                    d.miniSub = n.subject;
                } else {
                    d.big = "нет";
                    d.notifTitle = d.title;
                    d.miniLabel = d.label;
                }
                d.compactTitle = d.notifTitle;
            }
        }
        // минуты в подпись не входят, иначе при выключенном экране экономии не будет
        d.sig = s.kind + "|" + d.title + "|" + d.room + "|" + d.nextSubject + "|" + d.nextRoom + "|"
                + (s.current == null ? 0 : s.current.num) + "|"
                + (s.next == null ? 0 : s.next.num) + "|" + d.timer + "|" + s.targetMillis
                + "|" + Schedule.title();
        return d;
    }

    private static int pct(Schedule.Status s, long now) {
        long total = s.targetMillis - s.startMillis;
        if (total <= 0) return 0;
        long p = (now - s.startMillis) * 100 / total;
        return (int) Math.max(0, Math.min(100, p));
    }

    /** Точки уроков дня: прошедшие приглушены, текущий — акцент, остальные тёмные. */
    static CharSequence dots(Display d, int now, int done, int left) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 1; i <= d.dotTotal; i++) {
            int color = i == d.dotCurrent ? now : (i <= d.dotDone ? done : left);
            int start = sb.length();
            sb.append('▬');
            sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    static CharSequence dots(Display d, Theme t) {
        return dots(d, t.accentVisible(), t.muted(), t.faint());
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
        Schedule.load(ctx);
        ZonedDateTime now = ZonedDateTime.now();
        Display d = describe(Schedule.compute(now), now.toInstant().toEpochMilli());
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) ensureChannel(nm);
        return build(ctx, d, Theme.load(ctx));
    }

    /** @return true, если уведомление показано. */
    private static boolean updateNotification(Context ctx, Display d, Theme t, boolean wanted) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return false;
        if (!wanted) {
            LessonService.stop(ctx);
            nm.cancel(NOTIF_ID);
            return false;
        }
        ensureChannel(nm);
        try {
            nm.notify(NOTIF_ID, build(ctx, d, t));
        } catch (RuntimeException e) {
            return false; // нет разрешения на уведомления
        }
        LessonService.ensureRunning(ctx);
        return true;
    }

    /** Свёрнутый вид уведомления (используется и для предпросмотра). */
    static RemoteViews notifSmall(Context ctx, Display d, Theme t) {
        RemoteViews small = new RemoteViews(ctx.getPackageName(), R.layout.notif_small);
        small.setInt(R.id.n_root, "setBackgroundColor", t.bg);
        small.setTextViewText(R.id.n_title, d.compactTitle);
        small.setTextViewText(R.id.n_text, d.compactText);
        small.setViewVisibility(R.id.n_text, d.compactText.isEmpty() ? View.GONE : View.VISIBLE);
        t.textColor(small, t.text(), R.id.n_title, R.id.n_chrono, R.id.n_big);
        t.textColor(small, t.muted(), R.id.n_text);
        bindTimer(small, R.id.n_chrono, R.id.n_big, d, t);
        return small;
    }

    /** Развёрнутый вид уведомления (используется и для предпросмотра). */
    static RemoteViews notifBig(Context ctx, Display d, Theme t) {
        RemoteViews big = new RemoteViews(ctx.getPackageName(), R.layout.notif_big);
        big.setInt(R.id.n_root, "setBackgroundColor", t.bg);
        big.setTextViewText(R.id.n_label, d.label);
        big.setTextViewText(R.id.n_title, d.title);
        bindRoom(big, R.id.n_room, d.room);
        t.chip(big, R.id.n_room, true);
        if (d.dotTotal > 0 && t.dots) {
            big.setViewVisibility(R.id.n_dots, View.VISIBLE);
            big.setTextViewText(R.id.n_dots, dots(d, t));
        } else {
            big.setViewVisibility(R.id.n_dots, View.GONE);
        }
        bindTimer(big, R.id.n_chrono, R.id.n_big, d, t);
        big.setTextViewText(R.id.n_side_head, d.sideHead);
        big.setTextViewText(R.id.n_side_value, d.sideValue);
        big.setTextViewText(R.id.n_stat_head, d.statHead);
        big.setTextViewText(R.id.n_stat_value, d.statValue);
        big.setViewVisibility(R.id.n_stat, d.statHead.isEmpty() ? View.GONE : View.VISIBLE);
        bindProgress(big, R.id.n_progress, t.progress ? d.progress : -1);
        t.progressColors(big, R.id.n_progress);
        boolean hasNext = t.next && !d.nextSubject.isEmpty();
        big.setViewVisibility(R.id.n_next, hasNext ? View.VISIBLE : View.GONE);
        big.setTextViewText(R.id.n_next_head, d.nextHead);
        big.setTextViewText(R.id.n_next_subj, d.nextSubject);
        bindRoom(big, R.id.n_next_room, d.nextRoom);
        t.chip(big, R.id.n_next_room, false);
        t.textColor(big, t.text(), R.id.n_title, R.id.n_chrono, R.id.n_big, R.id.n_side_value);
        t.textColor(big, t.soft(), R.id.n_stat_value, R.id.n_next_subj);
        t.textColor(big, t.muted(), R.id.n_label, R.id.n_side_head, R.id.n_stat_head, R.id.n_next_head);
        return big;
    }

    private static Notification build(Context ctx, Display d, Theme t) {
        Notification.Builder b = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(d.notifTitle)
                .setContentText(d.notifText)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(notifSmall(ctx, d, t))
                .setCustomBigContentView(notifBig(ctx, d, t))
                // фон всего уведомления в цвет темы (работает для уведомлений службы)
                .setColor(t.bg)
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
                .setContentIntent(openApp(ctx));
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        Notification n = b.build();
        // «не очищается» кнопкой «Очистить всё» и не смахивается там, где система это уважает
        n.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        return n;
    }

    /** Таймер (если идёт отсчёт и включены секунды) или крупный текст на его месте. */
    static void bindTimer(RemoteViews rv, int chronoId, int bigId, Display d, Theme t) {
        if (d.timer && t.seconds) {
            rv.setChronometer(chronoId, chronoBase(d.target), null, true);
            rv.setChronometerCountDown(chronoId, true);
            rv.setViewVisibility(chronoId, View.VISIBLE);
            rv.setViewVisibility(bigId, View.GONE);
        } else {
            rv.setChronometer(chronoId, SystemClock.elapsedRealtime(), null, false);
            rv.setViewVisibility(chronoId, View.GONE);
            rv.setTextViewText(bigId, d.timer
                    ? Schedule.fmtTimer(d.target - System.currentTimeMillis()) : d.big);
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

    private static PendingIntent tickIntent(Context ctx) {
        Intent i = new Intent(ctx, TickReceiver.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void cancelAlarm(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(tickIntent(ctx));
    }

    private static void scheduleNext(Context ctx, Schedule.Status s, Display d, long nowMs,
                                     boolean screenOn, boolean active) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        ZonedDateTime now = ZonedDateTime.now();
        long nextMidnight = LocalDate.from(now).plusDays(1).atStartOfDay(now.getZone())
                .toInstant().toEpochMilli();

        // ближайшая «граница»: звонок, начало часа перед уроками или полночь
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
            // она сама обновит всё при включении экрана, и поминутные тики не нужны.
            boolean minuteTicks = screenOn || !LessonService.isRunning();
            if (minuteTicks) when = Math.min(boundary, (nowMs / 60_000 + 1) * 60_000);
        }
        when += 500; // чуть после звонка, чтобы точно попасть в новое состояние

        PendingIntent pi = tickIntent(ctx);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExact(AlarmManager.RTC, when, pi);
            else am.setWindow(AlarmManager.RTC, when, 60_000, pi);
        } catch (SecurityException e) {
            am.setWindow(AlarmManager.RTC, when, 60_000, pi);
        }
    }

    // ---------- вибрация перед уроком ----------

    private static PendingIntent buzzIntent(Context ctx) {
        Intent i = new Intent(ctx, TickReceiver.class).setAction(ACTION_BUZZ);
        return PendingIntent.getBroadcast(ctx, 4, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Начало ближайшего урока, до которого ещё больше 2 минут (0: уроков впереди нет). */
    static long nextBuzzLesson(ZonedDateTime now) {
        long nowMs = now.toInstant().toEpochMilli();
        ZonedDateTime t = now;
        for (int i = 0; i < 4; i++) {
            Schedule.Status s = Schedule.compute(t);
            long start = s.nextStartMillis;
            if (start == 0) {
                // последний урок дня: смотрим, что будет после звонка
                if (s.targetMillis <= t.toInstant().toEpochMilli()) return 0;
                start = s.targetMillis;
            } else if (start - BUZZ_LEAD > nowMs) {
                return start;
            }
            t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(start + 1000), now.getZone());
        }
        return 0;
    }

    /**
     * Одно точное будильное напоминание (RTC_WAKEUP) за 2 минуты до ближайшего звонка на урок.
     * Только если пользователь сам включил вибрацию в настройках.
     */
    private static void scheduleBuzz(Context ctx, ZonedDateTime now, boolean on) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = buzzIntent(ctx);
        long start = on ? nextBuzzLesson(now) : 0;
        if (start == 0) {
            am.cancel(pi);
            return;
        }
        long when = start - BUZZ_LEAD;
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            else am.setWindow(AlarmManager.RTC_WAKEUP, when, 60_000, pi);
        } catch (SecurityException e) {
            am.setWindow(AlarmManager.RTC_WAKEUP, when, 60_000, pi);
        }
    }

    /** Сработало напоминание: тихо вибрируем, если урок правда вот-вот начнётся. */
    static void buzz(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (!p.getBoolean(KEY_BUZZ, false) || p.getBoolean(KEY_VACATION, false)) return;
        Schedule.load(ctx);
        ZonedDateTime now = ZonedDateTime.now();
        long nowMs = now.toInstant().toEpochMilli();
        Schedule.Status s = Schedule.compute(now);
        long left = s.nextStartMillis - nowMs;
        if (s.next == null || left <= 0 || left > BUZZ_LEAD + 60_000) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_BUZZ) == null) {
            // без звука, только вибрация; в беззвучном режиме и «Не беспокоить» телефон промолчит сам
            NotificationChannel ch = new NotificationChannel(CHANNEL_BUZZ, "Вибрация перед уроком",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Короткая вибрация за 2 минуты до звонка на урок");
            ch.setSound(null, null);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 120, 140, 120});
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Schedule.Lesson n = s.next;
        Notification.Builder b = new Notification.Builder(ctx, CHANNEL_BUZZ)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Через " + Math.max(1, Math.round(left / 60000f)) + " мин: " + n.subject)
                .setContentText(n.room.isEmpty() ? "Звонок в " + n.startText() : n.roomText() + " · звонок в " + n.startText())
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setShowWhen(false)
                .setTimeoutAfter(BUZZ_LEAD + 30_000)
                .setContentIntent(openApp(ctx));
        try {
            nm.notify(BUZZ_ID, b.build());
        } catch (RuntimeException ignored) {
            // нет разрешения на уведомления
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
