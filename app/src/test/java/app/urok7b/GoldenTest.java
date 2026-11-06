package app.urok7b;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Android считает день так же, как сайт и виджет iPhone: каждый ответ Schedule сверяется с эталоном,
 * который tools/golden.py снял с site/src/js/rules.js (все классы, замены, каникулы, полночь, перевод часов).
 */
public class GoldenTest {

    private static final JSONObject G = load();
    private static final String[] HUE = {"red", "orange", "yellow", "green", "mint", "teal", "cyan", "blue", "indigo",
            "purple", "pink", "brown", "gray"};
    private final List<String> errors = new ArrayList<>();
    private String loaded;

    private static JSONObject load() {
        try (InputStream in = GoldenTest.class.getResourceAsStream("/golden.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("нет app/src/test/resources/golden.json: запусти py tools/golden.py", e);
        }
    }

    /** Данные эталона; "custom": своё расписание из эталона поверх 7Б. */
    private void use(String cls) {
        if (cls.equals(loaded)) return;
        boolean own = "custom".equals(cls);
        Schedule.init(G.getJSONObject("schedule").toString(), G.getJSONObject("changes").toString(),
                own ? "7Б" : cls, own ? G.getJSONObject("custom").toString() : null);
        loaded = cls;
    }

    private void check(String where, Object want, Object got) {
        if (!Objects.equals(want, got)) errors.add(where + ": ждали " + want + ", получили " + got);
    }

    private void done(int total) {
        int shown = Math.min(errors.size(), 40);
        assertTrue(errors.size() + " из " + total + " не совпало:\n" + String.join("\n", errors.subList(0, shown)), errors.isEmpty());
    }

    private static String lesson(Schedule.Lesson l) {
        return l == null ? null : "[" + l.num + "," + l.subject + "," + l.room + "]";
    }

    private static String lesson(JSONArray a) {
        return a == null ? null : "[" + a.getInt(0) + "," + a.getString(1) + "," + a.getString(2) + "]";
    }

    @Test
    public void stateAtEveryMoment() {
        JSONArray cases = G.getJSONArray("cases");
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i), e = c.getJSONObject("expect");
            String where = c.getString("cls") + " " + c.getString("local") + " " + c.getString("tz");
            use(c.getString("cls"));
            ZonedDateTime now = Instant.ofEpochMilli(c.getLong("now")).atZone(ZoneId.of(c.getString("tz")));
            Schedule.Status s = Schedule.compute(now);
            String kind = e.getString("kind");
            check(where + " kind", kind.equals("OFF") ? "DAY_OFF" : kind, s.kind.name());
            check(where + " cur", lesson(e.optJSONArray("cur")), lesson(s.current));
            check(where + " next", lesson(e.optJSONArray("next")), lesson(s.next));
            check(where + " target", e.optLong("target", 0), s.targetMillis);
            check(where + " start", e.optLong("start", 0), s.kind == Schedule.Kind.AFTER || s.kind == Schedule.Kind.DAY_OFF ? 0 : s.startMillis);
            if (e.has("soon")) check(where + " soon", e.getBoolean("soon"), s.notify);
            check(where + " count", e.optInt("count", 0), s.todayCount);
            if (e.has("end")) check(where + " end", e.getString("end"), s.todayEnd);
            check(where + " idx", e.optInt("idx", -1), s.index);
            check(where + " holiday", e.optString("holiday", null), s.holiday);
            check(where + " nDate", e.optString("nDate", null), s.nextDate == null ? null : s.nextDate.toString());
            if (e.has("nDate")) {
                check(where + " nDays", e.getInt("nDays"), s.nextDays);
                check(where + " nLabel", e.getString("nLabel"), s.nextDayLabel);
                check(where + " nWhen", e.getString("nWhen"), s.nextDayWhen);
                check(where + " nCount", e.getInt("nCount"), s.nextDayCount);
                check(where + " nEnd", e.getString("nEnd"), s.nextDayEnd);
            }
            Schedule.Vacation v = Schedule.vacation(now);
            JSONArray ev = e.optJSONArray("vac");
            check(where + " vac", ev == null ? null : ev.getInt(0) + "," + ev.getBoolean(1) + "," + ev.getString(2),
                    v == null ? null : v.days + "," + v.today + "," + v.name);
            check(where + " vacText", e.optString("vacText", ""), Schedule.vacationText(v));
        }
        done(cases.length());
    }

    @Test
    public void lessonsOfEveryDay() {
        JSONObject days = G.getJSONObject("days"), cancelled = G.getJSONObject("cancelled");
        int total = 0;
        for (Iterator<String> it = days.keys(); it.hasNext(); ) {
            String cls = it.next();
            use(cls);
            JSONObject byDate = days.getJSONObject(cls);
            for (Iterator<String> dt = byDate.keys(); dt.hasNext(); total++) {
                String date = dt.next();
                StringBuilder got = new StringBuilder();
                for (Schedule.Lesson l : Schedule.lessonsOn(LocalDate.parse(date))) {
                    got.append(got.length() > 0 ? "," : "").append("[").append(l.num).append(",").append(l.start).append(",").append(l.end)
                            .append(",").append(l.subject).append(",").append(l.room).append(",").append(l.changed ? 1 : 0)
                            .append(",").append(l.shortBell ? 1 : 0).append("]");
                }
                StringBuilder want = new StringBuilder();
                JSONArray list = byDate.getJSONArray(date);
                for (int i = 0; i < list.length(); i++) {
                    JSONArray l = list.getJSONArray(i);
                    want.append(i > 0 ? "," : "").append("[").append(l.getInt(0)).append(",").append(l.getInt(1)).append(",").append(l.getInt(2))
                            .append(",").append(l.getString(3)).append(",").append(l.getString(4)).append(",").append(l.getInt(5))
                            .append(",").append(l.getInt(6)).append("]");
                }
                check(cls + " " + date, want.toString(), got.toString());
                JSONObject gone = cancelled.optJSONObject(cls);
                JSONArray g = gone == null ? null : gone.optJSONArray(date);
                List<Integer> wantGone = new ArrayList<>();
                for (int i = 0; g != null && i < g.length(); i++) wantGone.add(g.getInt(i));
                check(cls + " " + date + " отменены", wantGone, Schedule.cancelledOn(LocalDate.parse(date)));
            }
        }
        done(total);
    }

    @Test
    public void rooms() {
        use("7Б");
        JSONObject r = G.getJSONObject("rooms");
        List<String> all = new ArrayList<>();
        for (Object x : r.getJSONArray("all")) all.add((String) x);
        assertEquals("все кабинеты по порядку", all, Schedule.rooms());
        JSONArray days = r.getJSONArray("days");
        int total = 0;
        for (int i = 0; i < days.length(); i++) {
            JSONObject d = days.getJSONObject(i);
            LocalDate date = LocalDate.parse(d.getString("date"));
            Map<String, List<Schedule.Use>> use = Schedule.roomDay(date);
            JSONArray slots = d.getJSONArray("slots");
            List<Integer> want = new ArrayList<>();
            for (int k = 0; k < slots.length(); k++) want.add(slots.getJSONObject(k).getInt("n"));
            check(date + " уроки", want, Schedule.slots(date, use));
            for (int k = 0; k < slots.length(); k++, total++) {
                JSONObject s = slots.getJSONObject(k);
                List<String> free = new ArrayList<>();
                for (Object x : s.getJSONArray("free")) free.add((String) x);
                check(date + " урок " + s.getInt("n") + " свободны", free, Schedule.freeRooms(use, s.getInt("n")));
            }
        }
        JSONArray now = r.getJSONArray("now");
        for (int i = 0; i < now.length(); i++, total++) {
            JSONObject n = now.getJSONObject(i);
            LocalDate date = LocalDate.parse(n.getString("date"));
            Schedule.RoomState st = Schedule.roomNow(n.getString("room"), Schedule.roomDay(date), n.getInt("min"));
            check(date + " " + n.getString("room") + " " + n.getInt("min"), n.getBoolean("busy") + " " + n.getString("text"), st.busy + " " + st.text);
        }
        done(total);
    }

    @Test
    public void subjectColors() {
        use("7Б");
        JSONObject hues = G.getJSONObject("hues");
        for (Iterator<String> it = hues.keys(); it.hasNext(); ) {
            String s = it.next();
            int i = Hues.index(s);
            check("цвет «" + s + "»", hues.getString(s), i < 0 ? "none" : HUE[i]);
        }
        done(hues.length());
    }
}
