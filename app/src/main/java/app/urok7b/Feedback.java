package app.urok7b;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * «Написать нам» прямо в приложении, та же отправка, что на сайте: анонимное письмо на почту проекта.
 * Уходят только тип, текст и класс (если указан). Основной путь Web3Forms, запасной FormSubmit.
 * В интернет выходит только по нажатию «Отправить».
 */
public class Feedback extends Activity {

    static final String EXTRA_ABOUT = "about", EXTRA_CLASS = "class", EXTRA_DAY = "day";
    private static final String W3F_KEY = "47a818a4-abad-4577-951b-19ed93e1ae86"; // публичный по замыслу сервиса, как на сайте
    private static final String FALLBACK = "https://formsubmit.co/ajax/zvonok165lyceumalmaty@gmail.com";
    private static final String[] KINDS = {"Идея", "Ошибка", "Другое"};
    private static final String[] DAYS = {"понедельник", "вторник", "среда", "четверг", "пятница", "суббота"};
    private static final int MAX = 1000;
    private static final long PAUSE = 30_000;
    private static final String KEY_DRAFT = "fb_draft", KEY_LAST = "fb_last";

    private int kind;
    private String cls = "";
    private EditText text;
    private TextView send, count, error, clsValue;
    private LinearLayout form, done;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable wait = this::update;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences p = Updater.prefs(this);
        String draft = p.getString(KEY_DRAFT, "");
        // пришли из «Ошибка в расписании?»: подставляем класс и день
        if ("schedule".equals(getIntent().getStringExtra(EXTRA_ABOUT))) {
            kind = 1;
            cls = getIntent().getStringExtra(EXTRA_CLASS) == null ? "" : getIntent().getStringExtra(EXTRA_CLASS);
            int d = getIntent().getIntExtra(EXTRA_DAY, -1);
            if (draft.trim().isEmpty()) draft = "Ошибка в расписании" + (cls.isEmpty() ? "" : " " + cls)
                    + (d >= 0 && d < DAYS.length ? ", " + DAYS[d] : "") + ".\nУрок: \nКак правильно: ";
        }
        if (state != null) {
            kind = state.getInt("kind", kind);
            cls = state.getString("cls", cls);
        }

        LinearLayout root = Ui.screen(this, "Написать нам", "Идея, ошибка или вопрос. Без имени и почты");
        form = Ui.vertical(this);
        root.addView(form);
        LinearLayout kinds = Ui.horizontal(this);
        form.addView(kinds, Ui.mw(this, 18));
        for (int i = 0; i < KINDS.length; i++) {
            final int k = i;
            TextView pill = Ui.pill(this, KINDS[i], i == kind, v -> { Ui.tick(v); kind = k; recolor(kinds); });
            pill.setMinHeight(Ui.dp(this, 44));
            kinds.addView(pill);
        }
        LinearLayout clsRow = Ui.linkRow(this, "Класс", "Необязательно", cls.isEmpty() ? "не указан" : cls, v -> pickClass());
        clsValue = (TextView) clsRow.getChildAt(1);
        form.addView(clsRow);

        text = new EditText(this);
        text.setText(draft);
        text.setHint("Что случилось или что придумал?");
        text.setTextSize(16);
        text.setTextColor(Ui.TEXT);
        text.setHintTextColor(Ui.MUTED);
        text.setGravity(Gravity.TOP);
        text.setMinLines(5);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX)});
        text.setBackground(Ui.round(this, Ui.FIELD, 16, Ui.LINE));
        text.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        text.setContentDescription("Текст письма");
        form.addView(text, Ui.mw(this, 8));
        count = Ui.text(this, "", 12, Ui.MUTED, null);
        count.setGravity(Gravity.END);
        count.setPadding(0, Ui.dp(this, 4), Ui.dp(this, 4), 0);
        form.addView(count, Ui.mw(this, 0));
        error = Ui.text(this, "", 14, Ui.BAD, null);
        error.setVisibility(View.GONE);
        form.addView(error, Ui.mw(this, 6));
        send = Ui.button(this, "Отправить", true, v -> submit());
        form.addView(send);
        TextView privacy = Ui.text(this, "Письмо уйдёт через сервис отправки Web3Forms на почту «Звонка». "
                + "Твоё имя, почту и телефон мы не узнаем.", 13, Ui.MUTED, null);
        privacy.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4), 0);
        form.addView(privacy);
        text.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                Updater.prefs(Feedback.this).edit().putString(KEY_DRAFT, e.toString()).apply();
                error.setVisibility(View.GONE);
                update();
            }
        });

        done = Ui.vertical(this);
        done.setVisibility(View.GONE);
        TextView ok = Ui.heading(Ui.text(this, "Спасибо! Письмо ушло.", 22, Ui.TEXT, "sans-serif-medium"));
        ok.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 6));
        done.addView(ok);
        done.addView(Ui.text(this, "Мы читаем всё. Если нужно исправить расписание, исправим в ближайшее время.", 15, Ui.SOFT, null));
        done.addView(Ui.button(this, "Написать ещё", false, v -> { done.setVisibility(View.GONE); form.setVisibility(View.VISIBLE); update(); }));
        root.addView(done);
        update();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("kind", kind);
        out.putString("cls", cls);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(wait);
        super.onDestroy();
    }

    private void recolor(LinearLayout kinds) {
        for (int i = 0; i < kinds.getChildCount(); i++) {
            TextView t = (TextView) kinds.getChildAt(i);
            t.setTextColor(i == kind ? Ui.ON_ACCENT : Ui.TEXT);
            t.setBackground(i == kind ? Ui.round(this, Ui.ACCENT, 14, 0) : Ui.round(this, Ui.FIELD, 14, Ui.LINE));
            t.setSelected(i == kind);
        }
    }

    private void pickClass() {
        List<String> all = Schedule.classes(this);
        String[] items = new String[all.size() + 1];
        items[0] = "Не указывать";
        for (int i = 0; i < all.size(); i++) items[i + 1] = all.get(i);
        new android.app.AlertDialog.Builder(this, Ui.dialogTheme()).setTitle("Класс")
                .setItems(items, (d, w) -> { cls = w == 0 ? "" : items[w]; clsValue.setText(cls.isEmpty() ? "не указан" : cls); }).show();
    }

    private long left() {
        return Math.max(0, Updater.prefs(this).getLong(KEY_LAST, 0) + PAUSE - System.currentTimeMillis());
    }

    private void update() {
        int n = text.length();
        long w = left();
        count.setText(n + " / " + MAX);
        count.setTextColor(n >= 950 ? Ui.WARN : Ui.MUTED);
        boolean short3 = text.getText().toString().trim().length() < 3;
        send.setEnabled(!short3 && w == 0);
        send.setAlpha(send.isEnabled() ? 1f : 0.5f);
        main.removeCallbacks(wait);
        if (w > 0) {
            send.setText("Можно через " + (w + 999) / 1000 + " с");
            main.postDelayed(wait, 1000 - w % 1000 + 20);
        } else {
            send.setText(short3 ? "Сначала напиши пару слов" : "Отправить");
        }
    }

    private void fail(String t) {
        error.setText(t);
        error.setVisibility(View.VISIBLE);
        error.announceForAccessibility(t);
    }

    private void submit() {
        String body = text.getText().toString().trim();
        if (body.length() < 3 || left() > 0) return;
        // спам почти всегда со ссылками: живому человеку хватает одной-двух
        if (body.split("https?:|www\\.", -1).length - 1 > 3) {
            fail("Слишком много ссылок. Оставь одну-две.");
            return;
        }
        send.setEnabled(false);
        send.setText("Отправляю…");
        String k = KINDS[kind], c = cls.isEmpty() ? "не указан" : cls;
        new Thread(() -> {
            boolean ok;
            try {
                JSONObject f = new JSONObject().put("Тип", k).put("Сообщение", body).put("Класс", c).put("Откуда", "приложение Android");
                ok = post("https://api.web3forms.com/submit", new JSONObject(f.toString()).put("access_key", W3F_KEY)
                        .put("subject", "Звонок: " + k).put("from_name", "Звонок · анонимно"))
                        || post(FALLBACK, new JSONObject(f.toString()).put("_subject", "Звонок: " + k).put("_template", "box").put("_captcha", "false"));
            } catch (Exception e) {
                ok = false;
            }
            final boolean sent = ok;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (sent) {
                    Updater.prefs(this).edit().putLong(KEY_LAST, System.currentTimeMillis()).putString(KEY_DRAFT, "").apply();
                    text.setText("");
                    form.setVisibility(View.GONE);
                    done.setVisibility(View.VISIBLE);
                    done.announceForAccessibility("Спасибо! Письмо ушло.");
                } else {
                    fail("Не получилось отправить: нет интернета или сервис не ответил. Текст сохранён, попробуй чуть позже.");
                }
                update();
            });
        }).start();
    }

    /** POST JSON; true, если сервис принял письмо. Только HTTPS. */
    private static boolean post(String url, JSONObject body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() / 100 != 2) return false;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0 && b.size() < 64_000; ) b.write(buf, 0, n);
                Object s = new JSONObject(new String(b.toByteArray(), StandardCharsets.UTF_8)).opt("success");
                return !(Boolean.FALSE.equals(s) || "false".equals(s));
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
