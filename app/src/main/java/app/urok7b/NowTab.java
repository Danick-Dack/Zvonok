package app.urok7b;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/** Вкладка «Сейчас»: кольцо-таймер, что дальше и уроки дня, как на сайте. */
final class NowTab {

    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};
    private static final String KEY_NEWS = "seen_news";

    final ScrollView view;
    private final MainActivity a;
    private final LinearLayout hero, nextCard, dots, list, news, streak;
    private final TextView date, badge, status, title, big, caption, sideH, sideV, statH, statV, update, fest;
    private final TextView nextH, nextS, foot, vac, tz, dayTitle, dayMeta, streakN;
    private final android.widget.ImageView sound;
    private final Chronometer chrono;
    private final RingView ring;
    private final FrameLayout roomBox, nextRoomBox, noteBox, ringBox;
    private long lastTarget = -1;
    private String lastList = "";
    private int taps;
    private long tapT;

    NowTab(MainActivity a) {
        this.a = a;
        view = new ScrollView(a);
        view.setVerticalScrollBarEnabled(false);
        LinearLayout root = Ui.vertical(a);
        root.setPadding(a.dp(18), a.dp(20), a.dp(18), a.dp(28));
        view.addView(root);

        // шапка: «Звонок» (три нажатия: змейка), дата и класс
        LinearLayout head = Ui.horizontal(a);
        LinearLayout t = Ui.vertical(a);
        t.setLayoutParams(Ui.weight(1));
        TextView brand = Ui.heading(Ui.text(a, "Звонок", 28, Ui.TEXT, "sans-serif-medium"));
        brand.setOnClickListener(v -> secret());
        t.addView(brand);
        date = Ui.text(a, "", 14, Ui.MUTED, null);
        t.addView(date);
        head.addView(t);
        // серия дней, как на сайте: растёт, когда заходишь посмотреть расписание
        streak = Ui.horizontal(a);
        streak.setPadding(a.dp(8), 0, a.dp(11), 0);
        streak.setBackground(Ui.round(a, (Ui.WARN & 0x00FFFFFF) | 0x26000000, 16, 0));
        streak.addView(Ui.icon(a, R.drawable.ic_fire, Ui.WARN, 16));
        streakN = Ui.text(a, "", 14, Ui.WARN, "sans-serif-medium");
        streakN.setPadding(a.dp(3), 0, 0, 0);
        streak.addView(streakN);
        streak.setOnClickListener(v -> {
            int[] s = Fun.streak(a);
            android.widget.Toast.makeText(a, "Серия " + Schedule.word(s[0], "день", "дня", "дней") + ", рекорд " + s[1]
                    + ". Выходные и каникулы её не рвут", android.widget.Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, a.dp(32));
        sp.rightMargin = a.dp(8);
        head.addView(streak, sp);
        badge = Ui.text(a, "", 16, Ui.ON_ACCENT, "sans-serif-medium");
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(a.dp(52));
        badge.setPadding(a.dp(14), 0, a.dp(14), 0);
        badge.setBackground(Ui.round(a, Ui.ACCENT, 16, 0));
        badge.setOnClickListener(v -> { Ui.tick(v); Ui.pickClass(a, a::rebuild); });
        head.addView(badge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, a.dp(48)));
        root.addView(head);

        // вышла новая версия
        update = Ui.text(a, "", 15, Ui.ON_ACCENT, "sans-serif-medium");
        update.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12));
        update.setBackground(Ui.round(a, Ui.ACCENT, 16, 0));
        update.setOnClickListener(v -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Updates.APK_URL))));
        root.addView(update, Ui.mw(a, 14));

        // праздник: значок, название и пара слов. Нажатие прячет до следующего праздника
        fest = Ui.text(a, "", 15, Ui.TEXT, "sans-serif-medium");
        fest.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12));
        root.addView(fest, Ui.mw(a, 14));

        // «что нового» один раз после обновления
        news = Ui.horizontal(a);
        news.setBackground(Ui.round(a, Ui.CARD, 18, Ui.LINE));
        news.setPadding(a.dp(16), a.dp(8), a.dp(6), a.dp(8));
        root.addView(news, Ui.mw(a, 14));

        // главная карточка с кольцом
        hero = Ui.vertical(a);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(a.dp(20), a.dp(18), a.dp(20), a.dp(22));
        hero.setFocusable(true);
        LinearLayout top = Ui.horizontal(a);
        status = Ui.text(a, "", 13, Ui.SOFT, "sans-serif-medium");
        status.setPadding(a.dp(11), a.dp(6), a.dp(12), a.dp(6));
        status.setBackground(Ui.round(a, Ui.RAISED, 12, 0));
        top.addView(status);
        top.addView(new View(a), Ui.weight(1));
        dots = Ui.horizontal(a);
        top.addView(dots);
        hero.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ringBox = new FrameLayout(a);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, 0); // размер задаёт fitRing()
        rp.topMargin = a.dp(14);
        ring = new RingView(a);
        ringBox.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout center = Ui.vertical(a);
        center.setGravity(Gravity.CENTER);
        chrono = new Chronometer(a);
        chrono.setCountDown(true);
        chrono.setOnChronometerTickListener(c -> Fun.play(a, false)); // со звуком часы тикают каждую секунду, пока экран открыт
        big = Ui.text(a, "", 50, Ui.TEXT, null);
        for (TextView x : new TextView[]{chrono, big}) {
            x.setTextColor(Ui.TEXT);
            x.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            x.setFontFeatureSettings("tnum");
            x.setIncludeFontPadding(false);
            x.setGravity(Gravity.CENTER);
            center.addView(x);
        }
        caption = Ui.text(a, "", 13, Ui.MUTED, null);
        caption.setPadding(0, a.dp(8), 0, 0);
        caption.setGravity(Gravity.CENTER);
        center.addView(caption);
        ringBox.addView(center, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // подержать палец на таймере: школьная мудрость (как на сайте)
        ringBox.setOnLongClickListener(v -> {
            android.widget.Toast.makeText(a, QUOTES[(int) (Math.random() * QUOTES.length)], android.widget.Toast.LENGTH_LONG).show();
            return true;
        });
        // звук: на уроках и переменах тикают часы, на звонке звенит звонок. Колокольчик качается на звонке и без звука
        sound = Ui.icon(a, R.drawable.ic_bell, Ui.MUTED, 22);
        sound.setPadding(a.dp(9), a.dp(9), a.dp(9), a.dp(9));
        sound.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        sound.setOnClickListener(v -> {
            boolean on = !Fun.soundOn(a);
            Updater.prefs(a).edit().putBoolean(Fun.KEY_SOUND, on).apply();
            renderSound();
            Fun.play(a, false);
            android.widget.Toast.makeText(a, on ? "Звуки включены: часы тикают, на звонке звенит звонок" : "Звуки выключены",
                    android.widget.Toast.LENGTH_SHORT).show();
        });
        ringBox.addView(sound, new FrameLayout.LayoutParams(a.dp(40), a.dp(40), Gravity.TOP | Gravity.END));
        hero.addView(ringBox, rp);
        fitRing();

        LinearLayout titleRow = Ui.horizontal(a);
        titleRow.setGravity(Gravity.CENTER);
        titleRow.setPadding(0, a.dp(12), 0, 0);
        title = Ui.text(a, "", 24, Ui.TEXT, "sans-serif-medium");
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title);
        roomBox = new FrameLayout(a);
        titleRow.addView(roomBox);
        hero.addView(titleRow);

        LinearLayout stats = Ui.horizontal(a);
        LinearLayout c1 = stat(), c2 = stat();
        sideH = (TextView) c1.getChildAt(0);
        sideV = (TextView) c1.getChildAt(1);
        statH = (TextView) c2.getChildAt(0);
        statV = (TextView) c2.getChildAt(1);
        View sep = new View(a);
        sep.setBackgroundColor(Ui.LINE);
        stats.addView(c1);
        stats.addView(sep, new LinearLayout.LayoutParams(a.dp(1), a.dp(36)));
        stats.addView(c2);
        hero.addView(stats, Ui.mw(a, 18));
        root.addView(hero, Ui.mw(a, 16));

        // что дальше
        nextCard = Ui.card(a);
        nextCard.setPadding(a.dp(18), a.dp(14), a.dp(18), a.dp(16));
        nextH = Ui.text(a, "", 13, Ui.MUTED, null);
        nextCard.addView(nextH);
        LinearLayout nr = Ui.horizontal(a);
        nr.setPadding(0, a.dp(6), 0, 0);
        nextS = Ui.text(a, "", 18, Ui.TEXT, null);
        nextS.setLayoutParams(Ui.weight(1));
        nr.addView(nextS);
        nextRoomBox = new FrameLayout(a);
        nr.addView(nextRoomBox);
        nextCard.addView(nr);
        foot = Ui.text(a, "", 13, Ui.MUTED, null);
        foot.setPadding(0, a.dp(10), 0, 0);
        nextCard.addView(foot);
        nextCard.setFocusable(true);
        root.addView(nextCard, Ui.mw(a, 12));

        vac = Ui.text(a, "", 14, Ui.MUTED, null);
        vac.setPadding(a.dp(4), a.dp(12), a.dp(4), 0);
        root.addView(vac);
        // звонки идут по времени Алматы (UTC+5); если на телефоне другой пояс, честно предупреждаем
        tz = Ui.text(a, "Часы телефона не в казахстанском времени (UTC+5), поэтому звонки могут показываться со сдвигом. "
                + "Проверь часовой пояс в настройках телефона.", 14, Ui.TEXT, null);
        tz.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10));
        tz.setBackground(Ui.round(a, (Ui.WARN & 0x00FFFFFF) | 0x26000000, 14, 0));
        root.addView(tz, Ui.mw(a, 12));

        // уроки дня: сегодня, а после уроков и в выходные ближайший учебный день
        LinearLayout dh = Ui.horizontal(a);
        dh.setGravity(Gravity.BOTTOM);
        dh.setPadding(a.dp(4), a.dp(24), a.dp(4), a.dp(4));
        dayTitle = Ui.heading(Ui.text(a, "", 20, Ui.TEXT, "sans-serif-medium"));
        dayTitle.setLayoutParams(Ui.weight(1));
        dh.addView(dayTitle);
        root.addView(dh);
        dayMeta = Ui.text(a, "", 13, Ui.MUTED, null);
        dayMeta.setPadding(a.dp(4), 0, a.dp(4), 0);
        root.addView(dayMeta);
        noteBox = new FrameLayout(a);
        root.addView(noteBox);
        list = Ui.vertical(a);
        root.addView(list, Ui.mw(a, 10));

        TextView by = Ui.text(a, "Звонок · " + Schedule.school(), 12, Ui.FAINT, null);
        by.setGravity(Gravity.CENTER);
        by.setPadding(0, a.dp(28), 0, 0);
        root.addView(by, Ui.mw(a, 0));
        renderNews();
    }

    private LinearLayout stat() {
        LinearLayout c = Ui.vertical(a);
        c.setGravity(Gravity.CENTER);
        c.setLayoutParams(Ui.weight(1));
        TextView h = Ui.text(a, "", 12, Ui.MUTED, null);
        c.addView(h);
        c.addView(Ui.text(a, "", 17, Ui.TEXT, "sans-serif-medium"));
        return c;
    }

    /**
     * Кольцо 236 dp, при крупном шрифте в системе до 1,25 раза больше. Экран набок: не выше 45% высоты,
     * чтобы таймер был виден сразу. Цифры меняются вместе с кольцом. Вызывается и при повороте экрана.
     */
    void fitRing() {
        android.content.res.Configuration c = a.getResources().getConfiguration();
        float k = Math.min(1.25f, Math.max(1f, c.fontScale));
        float side = Math.min(236 * k, c.screenHeightDp * 0.45f);
        ViewGroup.LayoutParams p = ringBox.getLayoutParams();
        p.width = p.height = a.dp(side);
        ringBox.setLayoutParams(p);
        for (TextView x : new TextView[]{chrono, big}) x.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 50 * side / 236);
        // набок высоты мало: сразу показываем карточку с таймером, шапка остаётся выше (прокрутка вверх)
        if (c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
            view.postOnAnimation(() -> view.scrollTo(0, hero.getTop() - a.dp(8)));
    }

    void pause() {
        chrono.stop();
        lastTarget = -1;
    }

    /** Каждую минуту: кольцо и тексты. Список уроков только когда он правда поменялся. */
    void render(MainActivity.Tick t, boolean changed) {
        Schedule.Status s = t.s;
        Updater.Display d = t.d;
        LocalDate today = t.now.toLocalDate();
        date.setText(Schedule.dayName(today.getDayOfWeek()) + ", " + today.getDayOfMonth() + " " + MONTHS[today.getMonthValue() - 1]);
        badge.setText(Schedule.title());
        badge.setContentDescription((Schedule.isCustom() ? "Своё расписание" : "Класс " + Schedule.title()) + ". Сменить");
        boolean avail = Updates.available(a);
        update.setVisibility(avail ? View.VISIBLE : View.GONE);
        if (avail) update.setText("Вышла версия " + Updates.latestText(a) + ". Нажми, чтобы обновить");

        boolean side = a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        status.setText((d.live ? "●  " : "") + Ui.calm(d.label) + (side ? "  ·  " + Schedule.title() : "")); // набок шапка с классом выше
        status.setTextColor(d.live ? Ui.TEXT : Ui.SOFT);
        buildDots(d);
        renderSound();
        int[] st = Fun.streak(a);
        streakN.setText(String.valueOf(st[0]));
        streak.setContentDescription("Серия: " + Schedule.word(st[0], "день", "дня", "дней") + " подряд");
        if (st[2] == 1) {
            Fun.pop(streak, 1200, 1, .6f, 1.35f, .95f, 1f);
            if (st[0] > 1) android.widget.Toast.makeText(a, "Серия: " + Schedule.word(st[0], "день", "дня", "дней") + " подряд",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
        long was = lastTarget;
        if (d.timer) {
            if (d.target != lastTarget) {
                chrono.setBase(Updater.chronoBase(d.target));
                chrono.start();
                lastTarget = d.target;
            }
        } else {
            chrono.stop();
            big.setText(d.big);
            lastTarget = -1;
        }
        if (was > 0 && was != lastTarget && Math.abs(t.ms - was) < 5000) ring(); // таймер дошёл до нуля: это звонок
        chrono.setVisibility(d.timer ? View.VISIBLE : View.GONE);
        big.setVisibility(d.timer ? View.GONE : View.VISIBLE);
        caption.setText(d.extra ? (d.progress >= 0 ? "до конца" : "до начала") : caption(s).toLowerCase(java.util.Locale.ROOT));
        String subj = d.extra ? null : d.kind == Schedule.Kind.LESSON ? d.title
                : d.kind == Schedule.Kind.BREAK || d.kind == Schedule.Kind.BEFORE ? d.nextSubject : null;
        // праздничная тема: значок, фраза и цвет праздника у кольца, пока на нём нет урока
        String[] f = Updater.prefs(a).getBoolean(Plus.KEY_FEST, true) ? Plus.festive(today) : null;
        String festKey = f == null ? "" : f[2] + today.getYear();
        boolean festOn = f != null && !festKey.equals(Updater.prefs(a).getString("fest_seen", ""));
        fest.setVisibility(festOn ? View.VISIBLE : View.GONE);
        int festColor = f == null ? Ui.ACCENT : 0xFF000000 | Integer.parseInt(f[1], 16);
        if (festOn) {
            fest.setText(f[0] + "  " + f[2] + (f[3].isEmpty() ? "" : "\n" + f[3]));
            fest.setBackground(Ui.round(a, (festColor & 0x00FFFFFF) | 0x33000000, 16, 0));
            fest.setOnClickListener(v -> { Updater.prefs(a).edit().putString("fest_seen", festKey).apply(); fest.setVisibility(View.GONE); });
        }
        ring.setColor(subj == null ? festColor : Ui.vivid(subj));
        ring.setProgress(d.progress >= 0 ? d.progress / 100f : -1);
        hero.setBackground(heroBg(d.timer ? subj : null));
        title.setText(d.title);
        setRoom(roomBox, d.room, subj, d.timer);
        sideH.setText(Ui.calm(d.sideHead));
        sideV.setText(d.sideValue);
        statH.setText(Ui.calm(d.statHead));
        statV.setText(d.statValue);
        hero.setContentDescription(spoken(t));

        nextCard.setVisibility(d.nextSubject.isEmpty() ? View.GONE : View.VISIBLE);
        nextH.setText(Ui.calm(d.nextHead));
        nextS.setText(d.nextSubject);
        setRoom(nextRoomBox, d.nextRoom, d.nextSubject, false);
        foot.setText(d.foot);
        foot.setVisibility(d.foot.isEmpty() ? View.GONE : View.VISIBLE);
        String v = Schedule.vacationText(Schedule.vacation(t.now));
        if (!d.note.isEmpty() && !d.note.equals(d.foot)) v = d.note + (v.isEmpty() ? "" : "\n" + v); // СОР и СОЧ, праздник завтра
        vac.setText(v);
        vac.setVisibility(v.isEmpty() ? View.GONE : View.VISIBLE);
        int off = t.now.getOffset().getTotalSeconds();
        tz.setVisibility(off == 5 * 3600 || off == 6 * 3600 ? View.GONE : View.VISIBLE); // +6: Алматы на старой базе поясов

        boolean live = s.kind != Schedule.Kind.AFTER && !s.list.isEmpty();
        LocalDate day = live ? today : s.nextDate;
        List<Schedule.Lesson> l = live ? s.list : day == null ? Collections.<Schedule.Lesson>emptyList() : Schedule.lessonsOn(day);
        String sig = d.sig + "|" + today + "|" + Ui.version;
        android.widget.ProgressBar bar = list.findViewWithTag(Day.CUR_BAR);
        if (bar != null && d.progress >= 0) bar.setProgress(d.progress);
        if (!changed && sig.equals(lastList)) return;
        lastList = sig;
        dayTitle.setText(live ? "Сегодня" : s.nextDays == 1 ? "Завтра" : day != null ? Schedule.dayName(day.getDayOfWeek()) : "Дальше");
        dayMeta.setText(day == null ? "" : Schedule.dateText(day) + " · " + Day.range(l));
        noteBox.removeAllViews();
        TextView note = day == null ? null : Day.noteView(a, day);
        if (note != null) noteBox.addView(note);
        list.removeAllViews();
        list.addView(Day.timeline(a, l, day, live, s, t.min, d.kind == Schedule.Kind.LESSON ? d.progress : -1, "Ближайших уроков нет"));
    }

    private void renderSound() {
        boolean on = Fun.soundOn(a);
        sound.setImageResource(on ? R.drawable.ic_bell : R.drawable.ic_belloff);
        sound.setColorFilter(on ? Ui.ACCENT : Ui.MUTED);
        sound.setContentDescription("Звуки: тиканье и звонок. " + (on ? "Включены" : "Выключены"));
    }

    /** Звонок: кольцо вздрагивает, колокольчик качается, а со звуком ещё и звенит. */
    private void ring() {
        Fun.play(a, true);
        Fun.pop(ringBox, 460, 5, 1f, 1.03f, 1f);
        android.animation.ObjectAnimator swing = android.animation.ObjectAnimator.ofFloat(sound, View.ROTATION, 0, 22, -22, 0);
        swing.setDuration(230);
        swing.setRepeatCount(9);
        swing.start();
    }

    private String caption(Schedule.Status s) {
        switch (s.kind) {
            case LESSON: return "ДО ЗВОНКА";
            case BREAK: return "ДО УРОКА";
            case BEFORE: return s.notify ? "ДО УРОКОВ" : "ПЕРВЫЙ ЗВОНОК";
            default:
                if (s.next == null) return "";
                if (s.holiday != null && s.nextDays > 1) return Schedule.word(s.nextDays, "ДЕНЬ", "ДНЯ", "ДНЕЙ").replaceAll("^\\d+ ", "") + " ДО УРОКОВ";
                return s.nextDayLabel.toUpperCase() + " В";
        }
    }

    /** Одной фразой для диктора: «Урок 3 из 7. Физика, кабинет 48. До звонка 24 мин, звонок в 10:30». */
    static String spoken(MainActivity.Tick t) {
        Updater.Display d = t.d;
        StringBuilder b = new StringBuilder(d.label.isEmpty() ? "" : d.label.toLowerCase() + ". ");
        if (!d.title.isEmpty()) b.append(d.title).append(d.room.isEmpty() ? "" : ", " + Ui.roomSpoken(d.room)).append(". ");
        if (d.timer) b.append(t.s.kind == Schedule.Kind.LESSON ? "До звонка " : "До урока ").append(Schedule.fmtLeft(d.target - t.ms)).append(". ");
        else if (!d.big.isEmpty() && !d.sideValue.isEmpty()) b.append(d.sideValue).append(", ").append(d.big).append(". ");
        if (!d.nextSubject.isEmpty()) b.append("Дальше ").append(d.nextSubject).append(d.nextRoom.isEmpty() ? "" : ", " + Ui.roomSpoken(d.nextRoom)).append(".");
        return b.toString().trim();
    }

    /** Капсулы уроков дня: прошедшие тусклые, текущий длиннее и в цвете предмета. */
    private void buildDots(Updater.Display d) {
        dots.removeAllViews();
        dots.setVisibility(d.dotTotal > 0 ? View.VISIBLE : View.GONE);
        for (int i = 1; i <= d.dotTotal; i++) {
            boolean cur = i == d.dotCurrent;
            View c = new View(a);
            c.setBackground(Ui.round(a, cur ? Ui.vivid(d.title) : i <= d.dotDone ? Ui.FAINT : Ui.TRACK, 2, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(cur ? 18 : 10), a.dp(4));
            lp.leftMargin = a.dp(4);
            dots.addView(c, lp);
        }
    }

    private void setRoom(FrameLayout box, String room, String subject, boolean now) {
        box.removeAllViews();
        if (room == null || room.isEmpty()) return;
        TextView c = subject == null || subject.isEmpty() ? Ui.room(a, room, "", false, 15) : Ui.room(a, room, subject, now, 15);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = a.dp(10);
        box.addView(c, lp);
    }

    /** Фон карточки таймера: мягкий цвет предмета сверху, как на сайте. */
    private GradientDrawable heroBg(String subject) {
        int top = subject == null ? Ui.CARD : Ui.soft(subject);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, Ui.CARD, Ui.CARD});
        g.setCornerRadius(a.dp(32));
        g.setStroke(a.dp(1), Ui.LINE);
        return g;
    }

    // ---------- «что нового» после обновления ----------

    /** Свежая запись для Android из data/changelog.json (её же видит сайт): {дата|заголовок, заголовок}. */
    private static String[] newest(Context c) {
        try {
            JSONArray log = new JSONArray(Changelog.read(c));
            for (int i = 0; i < log.length(); i++) {
                JSONObject r = log.getJSONObject(i);
                if (r.getJSONArray("platforms").toString().contains("android"))
                    return new String[]{r.getString("date") + "|" + r.getString("title"), r.getString("title")};
            }
        } catch (Exception ignored) {
            // нет журнала: показывать нечего
        }
        return null;
    }

    static void markNewsSeen(Context c) {
        String[] n = newest(c);
        if (n != null) Updater.prefs(c).edit().putString(KEY_NEWS, n[0]).apply();
    }

    private void renderNews() {
        String[] n = newest(a);
        news.setVisibility(View.GONE);
        if (n == null || n[0].equals(Updater.prefs(a).getString(KEY_NEWS, ""))) return;
        news.setVisibility(View.VISIBLE);
        TextView t = Ui.text(a, "Новое: " + n[1], 15, Ui.TEXT, "sans-serif-medium");
        t.setPadding(0, a.dp(8), a.dp(8), a.dp(8));
        t.setOnClickListener(v -> { markNewsSeen(a); news.setVisibility(View.GONE); a.startActivity(new Intent(a, Changelog.class)); });
        news.addView(t, Ui.weight(1));
        TextView close = Ui.text(a, "✕", 18, Ui.MUTED, null);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Скрыть");
        close.setOnClickListener(v -> { markNewsSeen(a); news.setVisibility(View.GONE); });
        news.addView(close, new LinearLayout.LayoutParams(a.dp(44), a.dp(44)));
    }

    private static final String[] QUOTES = {"Звонок для учителя. Но таймер для тебя.", "До конца урока всегда чуть дольше, чем кажется.",
            "Кто понял жизнь, тот сидит на последней парте.", "Забыл форму: классика жанра.", "Домашка, сделанная вечером, утром не снится.",
            "Вторая смена обуви спасает мир.", "«Достаём двойные листочки»: фраза, от которой холодеет спина.", "Сегодня точно спросят. Или нет.",
            "Столовая: успей за одну перемену.", "Учебник дома, знания тоже."};

    /** Три нажатия на «Звонок» подряд: змейка, скоротать перемену (как на сайте). */
    private void secret() {
        long now = System.currentTimeMillis();
        taps = now - tapT < 1600 ? taps + 1 : 1;
        tapT = now;
        if (taps < 3) return;
        taps = 0;
        Snake.show(a);
    }
}
