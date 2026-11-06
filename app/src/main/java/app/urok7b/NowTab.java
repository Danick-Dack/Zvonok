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
    private final LinearLayout hero, nextCard, dots, list, news;
    private final TextView date, badge, status, title, big, caption, sideH, sideV, statH, statV, update;
    private final TextView nextH, nextS, foot, vac, tz, dayTitle, dayMeta;
    private final Chronometer chrono;
    private final RingView ring;
    private final FrameLayout roomBox, nextRoomBox, noteBox;
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

        // шапка: «Звонок» (десять нажатий: змейка), дата и класс
        LinearLayout head = Ui.horizontal(a);
        LinearLayout t = Ui.vertical(a);
        t.setLayoutParams(Ui.weight(1));
        TextView brand = Ui.heading(Ui.text(a, "Звонок", 28, Ui.TEXT, "sans-serif-medium"));
        brand.setOnClickListener(v -> secret());
        t.addView(brand);
        date = Ui.text(a, "", 14, Ui.MUTED, null);
        t.addView(date);
        head.addView(t);
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
        status = Ui.text(a, "", 11, Ui.SOFT, "sans-serif-medium");
        status.setLetterSpacing(0.12f);
        status.setPadding(a.dp(11), a.dp(6), a.dp(12), a.dp(6));
        status.setBackground(Ui.round(a, Ui.RAISED, 12, 0));
        top.addView(status);
        top.addView(new View(a), Ui.weight(1));
        dots = Ui.horizontal(a);
        top.addView(dots);
        hero.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // крупный шрифт в системе: кольцо и цифры растут, но не вылезают за экран
        float k = Math.min(1.25f, Math.max(1f, a.getResources().getConfiguration().fontScale));
        FrameLayout ringBox = new FrameLayout(a);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(a.dp(236 * k), a.dp(236 * k));
        rp.topMargin = a.dp(14);
        ring = new RingView(a);
        ringBox.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout center = Ui.vertical(a);
        center.setGravity(Gravity.CENTER);
        chrono = new Chronometer(a);
        chrono.setCountDown(true);
        big = Ui.text(a, "", 50, Ui.TEXT, null);
        for (TextView x : new TextView[]{chrono, big}) {
            x.setTextColor(Ui.TEXT);
            x.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 50 * k);
            x.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            x.setFontFeatureSettings("tnum");
            x.setIncludeFontPadding(false);
            x.setGravity(Gravity.CENTER);
            center.addView(x);
        }
        caption = Ui.text(a, "", 11, Ui.MUTED, "sans-serif-medium");
        caption.setLetterSpacing(0.14f);
        caption.setPadding(0, a.dp(8), 0, 0);
        caption.setGravity(Gravity.CENTER);
        center.addView(caption);
        ringBox.addView(center, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hero.addView(ringBox, rp);

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
        nextH = Ui.text(a, "", 11, Ui.MUTED, "sans-serif-medium");
        nextH.setLetterSpacing(0.14f);
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
        TextView h = Ui.text(a, "", 10, Ui.MUTED, "sans-serif-medium");
        h.setLetterSpacing(0.14f);
        c.addView(h);
        c.addView(Ui.text(a, "", 17, Ui.TEXT, "sans-serif-medium"));
        return c;
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

        status.setText((d.live ? "●  " : "") + d.label);
        status.setTextColor(d.live ? Ui.TEXT : Ui.SOFT);
        buildDots(d);
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
        chrono.setVisibility(d.timer ? View.VISIBLE : View.GONE);
        big.setVisibility(d.timer ? View.GONE : View.VISIBLE);
        caption.setText(caption(s));
        String subj = d.kind == Schedule.Kind.LESSON ? d.title
                : d.kind == Schedule.Kind.BREAK || d.kind == Schedule.Kind.BEFORE ? d.nextSubject : null;
        ring.setColor(subj == null ? Ui.ACCENT : Ui.vivid(subj));
        ring.setProgress(d.progress >= 0 ? d.progress / 100f : -1);
        hero.setBackground(heroBg(d.timer ? subj : null));
        title.setText(d.title);
        setRoom(roomBox, d.room, subj, d.timer);
        sideH.setText(d.sideHead);
        sideV.setText(d.sideValue);
        statH.setText(d.statHead);
        statV.setText(d.statValue);
        hero.setContentDescription(spoken(t));

        nextCard.setVisibility(d.nextSubject.isEmpty() ? View.GONE : View.VISIBLE);
        nextH.setText(d.nextHead);
        nextS.setText(d.nextSubject);
        setRoom(nextRoomBox, d.nextRoom, d.nextSubject, false);
        foot.setText(d.foot);
        foot.setVisibility(d.foot.isEmpty() ? View.GONE : View.VISIBLE);
        String v = Schedule.vacationText(Schedule.vacation(t.now));
        vac.setText(v);
        vac.setVisibility(v.isEmpty() ? View.GONE : View.VISIBLE);
        int off = t.now.getOffset().getTotalSeconds();
        tz.setVisibility(off == 5 * 3600 || off == 6 * 3600 ? View.GONE : View.VISIBLE); // +6: Алматы на старой базе поясов

        boolean live = s.kind != Schedule.Kind.AFTER && !s.list.isEmpty();
        LocalDate day = live ? today : s.nextDate;
        List<Schedule.Lesson> l = live ? s.list : day == null ? Collections.<Schedule.Lesson>emptyList() : Schedule.lessonsOn(day);
        String sig = d.sig + "|" + today + "|" + d.progress / 5 + "|" + Ui.version;
        if (!changed && sig.equals(lastList)) return;
        lastList = sig;
        dayTitle.setText(live ? "Сегодня" : s.nextDays == 1 ? "Завтра" : day != null ? Schedule.dayName(day.getDayOfWeek()) : "Дальше");
        dayMeta.setText(day == null ? "" : Schedule.dateText(day) + " · " + Day.range(l));
        noteBox.removeAllViews();
        TextView note = day == null ? null : Day.noteView(a, day);
        if (note != null) noteBox.addView(note);
        list.removeAllViews();
        list.addView(Day.timeline(a, l, live, s, t.min, d.kind == Schedule.Kind.LESSON ? d.progress : -1, "Ближайших уроков нет"));
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

    /** Десять нажатий на «Звонок» подряд: змейка, скоротать перемену (как на сайте). */
    private void secret() {
        long now = System.currentTimeMillis();
        taps = now - tapT < 1600 ? taps + 1 : 1;
        tapT = now;
        if (taps < 10) return;
        taps = 0;
        Snake.show(a);
    }
}
