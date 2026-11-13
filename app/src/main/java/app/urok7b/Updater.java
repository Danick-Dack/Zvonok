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
 *  • все будильники «не будящие» (RTC): спящий телефон приложение не будит. Исключение одно: «Вибрация
 *    перед уроком», если пользователь сам её включил (один RTC_WAKEUP перед каждым уроком, scheduleBuzz);
 *  • пока экран выключен, поминутных обновлений нет совсем — только на звонках;
 *  • при включении экрана служба сразу обновляет всё одним вызовом;
 *  • таймеры тикают сами (Chronometer), а в экономном режиме их нет вовсе;
 *  • на каникулах и в праздники будильник один раз в сутки (в полночь), поминутных обновлений нет.
 */
public final class Updater {

    public static final String CHANNEL = "lesson_live";
    public static final int NOTIF_ID = 7;
    public static final String ACTION_TICK = "app.urok7b.TICK";
    public static final String ACTION_REPOST = "app.urok7b.REPOST";
    public static final String PREFS = "settings";
    public static final String KEY_NOTIF = "notif_enabled";
    public static final String KEY_SCHOOL_ONLY = "school_only";
    public static final String KEY_BUZZ = "buzz_before_lesson";   // вибрация за 2 мин до урока
    public static final String KEY_LIVE = "live_update";          // Android 16: таймер урока в строке состояния
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
        public boolean extra;            // на экране кружок или ШОР, а не урок
        public String note = "";         // «Завтра СОЧ: алгебра» или «Завтра День Независимости, в школу не идём»
        public long wake;                // когда кружок выйдет на экран (0: не нужно)
    }

    /** За сколько до кружка в выходной он занимает экран; после уроков сразу. */
    static final long EXTRA_LEAD = 90 * 60_000L;

    private static String lastSig = "", lastNotifView = "", lastWidgetView = "";

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
        // каникулы, праздники, отменённые дни и выходные приложение знает само (расписание + замены с сайта)
        Display d = describe(s, now);
        boolean screenOn = isScreenOn(ctx);
        Theme t = Theme.load(ctx).forDisplay(d);

        boolean notif = notifWanted(ctx, d);
        boolean rooms = widgetIds(ctx, RoomsWidget.class).length > 0;
        boolean widgets = rooms || widgetIds(ctx, LessonWidget.class).length > 0
                || widgetIds(ctx, MiniWidget.class).length > 0
                || widgetIds(ctx, DayWidget.class).length > 0;
        // свободные кабинеты меняются на любом звонке, даже когда у своего класса уроки кончились
        long bell = rooms ? nextBell(now) : 0;
        String sig = d.sig + "|" + (rooms ? bell : 0);
        // Что видно глазом между звонками: полоса шагами по 5% и таймер без секунд (текст раз в минуту).
        // Уведомление ещё и пишет «осталось N мин» (часы, экран блокировки MIUI), кроме режима Live на Android 16.
        // Перерисовываем только то, что поменялось.
        String widgetView = sig + "|" + (d.progress < 0 ? -1 : d.progress / 5) + "|"
                + (d.timer && !t.seconds ? Schedule.fmtTimer(d.target - nowMs) : "");
        boolean minuteTitle = notif && !live(ctx, d);
        String notifView = widgetView + "|" + (minuteTitle ? d.notifTitle : "");
        boolean changed = force || !sig.equals(lastSig);
        lastSig = sig;

        if (changed || screenOn && !notifView.equals(lastNotifView)) {
            lastNotifView = notifView;
            notif = updateNotification(ctx, d, t, notif);
        }
        if (changed || screenOn && !widgetView.equals(lastWidgetView)) {
            lastWidgetView = widgetView;
            LessonWidget.updateAll(ctx, d, t);
            MiniWidget.updateAll(ctx, d, t);
            DayWidget.updateAll(ctx, d, t, s);
            RoomsWidget.updateAll(ctx, now, t);
        }
        scheduleBuzz(ctx, now, prefs(ctx).getBoolean(KEY_BUZZ, false));
        if (screenOn) Updates.background(ctx); // новости и новая версия: редко и только пока телефоном пользуются
        // раз в минуту нужно только тексту (таймер без секунд, «осталось N мин»), иначе хватит шагов полосы
        scheduleNext(ctx, s, d, nowMs, screenOn, notif || widgets, bell, !t.seconds || minuteTitle);
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

    /**
     * То же плюс всё, что зависит от выбора ученика и календаря: кружок или ШОР после уроков (отсчёт до начала,
     * потом до конца), СОР и СОЧ сегодня и завтра, завтрашний праздник.
     */
    public static Display describe(Schedule.Status s, ZonedDateTime now) {
        long ms = now.toInstant().toEpochMilli();
        Display d = describe(s, ms);
        boolean lessons = s.kind == Schedule.Kind.LESSON || s.kind == Schedule.Kind.BREAK || s.kind == Schedule.Kind.BEFORE;
        Plus.Extra x = lessons ? null : Plus.extraNow(now);
        if (x != null && !x.on && s.kind != Schedule.Kind.AFTER && x.target - ms > EXTRA_LEAD) {
            d.wake = x.target - EXTRA_LEAD;
            x = null;
        }
        if (x != null) {
            String from = Schedule.hm(x.start), to = Schedule.hm(x.end);
            long begin = Schedule.at(now.toLocalDate(), x.start, now.getZone());
            d.extra = d.live = d.timer = true;
            d.holiday = false;
            d.target = x.target;
            d.progress = x.on ? (int) Math.max(0, Math.min(100, (ms - begin) * 100 / Math.max(1, x.target - begin))) : -1;
            d.dotTotal = 0;
            d.label = x.on ? "ИДЁТ" : "СЕГОДНЯ В " + from;
            d.title = x.name;
            d.room = "";
            d.sideHead = x.on ? "КОНЕЦ" : "НАЧАЛО";
            d.sideValue = d.sideShort = x.on ? to : from;
            d.statHead = x.on ? "НАЧАЛО" : "КОНЕЦ";
            d.statValue = x.on ? from : to;
            if (!x.place.isEmpty()) d.foot = x.place;
            d.notifTitle = Plus.extraText(x, ms);
            d.notifText = from + "–" + to + (x.place.isEmpty() ? "" : " · " + x.place);
            d.compactTitle = x.name;
            d.compactText = (x.on ? "до " + to : "начало в " + from) + (x.place.isEmpty() ? "" : " · " + x.place);
            d.miniLabel = x.on ? "ИДЁТ" : "В " + from;
            d.miniRoom = "";
            d.miniSub = x.name;
            d.sig = "extra|" + x.name + "|" + x.on + "|" + x.target + "|" + Schedule.title();
        }
        d.note = Plus.note(now, lessons);
        // СОР или СОЧ по предмету, который сейчас на экране
        Schedule.Lesson shown = s.kind == Schedule.Kind.LESSON ? s.current : lessons ? s.next : null;
        for (Plus.Exam e : shown == null ? java.util.Collections.<Plus.Exam>emptyList() : Plus.examsOn(Schedule.cls(), now.toLocalDate())) {
            if (e.subject.equals(shown.subject)) { d.label += " · " + e.type; break; }
        }
        if (!lessons && !d.extra && !d.note.isEmpty()) d.foot = d.notifText = d.compactText = d.note; // вечером: что завтра
        d.sig += "|" + d.note;
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
                d.title = after ? "Отдыхай" : d.holiday ? (vac ? "Каникулы" : "Выходной") : "Отдыхай";
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
        Display d = describe(Schedule.compute(now), now);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) ensureChannel(nm);
        return build(ctx, d, Theme.load(ctx).forDisplay(d));
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
        Backdrop.notif(ctx, small, R.id.n_bg, R.id.n_root, t, false);
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
        Backdrop.notif(ctx, big, R.id.n_bg, R.id.n_root, t, true);
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

    /** Android 16 и включён «Таймер в строке состояния»: идёт отсчёт до звонка. */
    static boolean live(Context ctx, Display d) {
        return Build.VERSION.SDK_INT >= 36 && d.timer && prefs(ctx).getBoolean(KEY_LIVE, true);
    }

    /**
     * Android 16 (Live Updates): обычное уведомление без своей разметки и заливки. Система поднимает его наверх
     * и показывает отсчёт до звонка чипом в строке состояния; цифры тикают силами системы, приложение не просыпается.
     */
    @android.annotation.TargetApi(31)
    private static Notification buildLive(Context ctx, Display d) {
        String subj = d.kind == Schedule.Kind.LESSON ? d.title : d.nextSubject;
        Notification.Builder b = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(d.compactTitle)
                .setContentText(d.notifText)
                .setSubText(d.label.isEmpty() ? null : d.label.charAt(0) + d.label.substring(1).toLowerCase())
                .setProgress(100, Math.max(0, d.progress), false)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(d.target)
                .setShowWhen(true)
                .setColor(Hues.vivid(subj, true))
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setLocalOnly(true)
                .setDeleteIntent(repost(ctx))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openFromNotif(ctx));
        b.getExtras().putBoolean("android.requestPromotedOngoing", true); // Notification.EXTRA_REQUEST_PROMOTED_ONGOING
        Notification n = b.build();
        n.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        return n;
    }

    private static Notification build(Context ctx, Display d, Theme t) {
        if (live(ctx, d)) return buildLive(ctx, d);
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
                .setContentIntent(openFromNotif(ctx));
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

    /** Нажатие на уведомление: на заблокированном телефоне — большой таймер поверх блокировки, иначе главный экран. */
    static PendingIntent openFromNotif(Context ctx) {
        Intent i = new Intent(ctx, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return PendingIntent.getActivity(ctx, 2, i,
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

    /** Ближайший звонок сегодня (начало или конец урока, на котором кто-то учится), 0: таких нет. */
    static long nextBell(ZonedDateTime now) {
        java.time.LocalDate today = now.toLocalDate();
        if (Schedule.offDay(today) != null) return 0;
        int[][] bells = Schedule.dayBells(today);
        long nowMs = now.toInstant().toEpochMilli(), best = 0;
        for (int n : Schedule.slots(today, Schedule.roomDay(today))) {
            for (int edge : bells[n - 1]) {
                long t = Schedule.at(today, edge, now.getZone());
                if (t > nowMs && (best == 0 || t < best)) best = t;
            }
        }
        return best;
    }

    /** Когда на экране что-то поменяется само: следующая минута у текста «N мин», иначе следующий шаг полосы в 5%. */
    static long nextStep(Schedule.Status s, Display d, long nowMs, boolean perMinute) {
        if (perMinute || d.progress < 0 || d.extra) return (nowMs / 60_000 + 1) * 60_000;
        return Math.max(nowMs + 1000, s.startMillis + (d.progress / 5 + 1) * (s.targetMillis - s.startMillis) / 20);
    }

    private static void scheduleNext(Context ctx, Schedule.Status s, Display d, long nowMs,
                                     boolean screenOn, boolean active, long bell, boolean perMinute) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        ZonedDateTime now = ZonedDateTime.now();
        long nextMidnight = LocalDate.from(now).plusDays(1).atStartOfDay(now.getZone())
                .toInstant().toEpochMilli();

        // ближайшая «граница»: звонок, начало часа перед уроками или полночь
        long boundary;
        if (d.live) {
            boundary = d.extra ? d.target : s.targetMillis;
        } else if (s.windowStartMillis > nowMs) {
            boundary = Math.min(s.windowStartMillis, nextMidnight);
        } else {
            boundary = nextMidnight;
        }

        if (bell > 0) boundary = Math.min(boundary, bell);
        if (d.wake > nowMs) boundary = Math.min(boundary, d.wake);
        long when = boundary;
        if (d.live && active) {
            // Каждую минуту — только когда на экран смотрят. Если экран выключен, а служба жива,
            // она сама обновит всё при включении экрана, и поминутные тики не нужны.
            boolean minuteTicks = screenOn || !LessonService.isRunning();
            if (minuteTicks) when = Math.min(boundary, nextStep(s, d, nowMs, perMinute));
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
        if (!p.getBoolean(KEY_BUZZ, false)) return;
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
