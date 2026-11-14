package app.urok7b;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Обязательные шаги перед расписанием. Сначала обновление: если вышла версия новее установленной, дальше приложение
 * не пускает, пока её не поставят. Потом настройка телефона: пока не включено всё, без чего таймер в уведомлении,
 * на экране блокировки и в виджетах отстаёт или пропадает. Пункты, которые проверить нельзя (автозапуск у некоторых
 * марок), достаточно один раз открыть. Экран возвращается сам, если что-то выключат или выйдет новая версия.
 * С EXTRA_REVIEW это просто список разрешений из «Ещё»: со стрелкой «назад» и без требований.
 */
public class Setup extends Activity {

    static final String EXTRA_REVIEW = "review";

    private boolean review() {
        return getIntent().getBooleanExtra(EXTRA_REVIEW, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        if (Updates.available(this)) {
            update();
            return;
        }
        List<Perms.Item> all = Perms.list(this);
        java.util.Set<String> need = new java.util.HashSet<>();
        for (Perms.Item i : Perms.missing(this)) need.add(i.key);
        if (need.isEmpty() && !review()) {
            finish();
            return;
        }
        LinearLayout root = Ui.screen(this, review() ? "Разрешения" : "Настрой телефон",
                "Без них таймер в уведомлении, на экране блокировки и в виджетах отстаёт или пропадает. "
                + (need.isEmpty() ? "Сейчас всё включено" : "Нажми на пункт, разреши в настройках и вернись сюда"));
        if (!review()) root.getChildAt(0).setVisibility(View.GONE); // без стрелки «назад»: шаг обязательный
        LinearLayout card = Ui.card(this);
        root.addView(card, Ui.mw(this, 20));
        for (Perms.Item it : all) {
            boolean todo = need.contains(it.key);
            LinearLayout r = Ui.horizontal(this);
            r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            LinearLayout t = Ui.twoLines(this, it.title, it.why);
            t.setLayoutParams(Ui.weight(1));
            r.addView(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = Ui.dp(this, 12);
            if (todo) {
                TextView st = Ui.text(this, it.ok == null ? "Открыть" : "Включить", 14, Ui.ON_ACCENT, "sans-serif-medium");
                st.setBackground(Ui.round(this, Ui.ACCENT, 10, 0));
                st.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
                r.addView(st, lp);
            } else {
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
                ip.leftMargin = Ui.dp(this, 12);
                r.addView(Ui.icon(this, R.drawable.ic_check, Ui.MUTED, 20), ip);
            }
            r.setContentDescription(it.title + ". " + it.why + ". " + (todo ? "Нужно включить" : "Готово"));
            r.setOnClickListener(v -> { Ui.tick(v); Perms.markSeen(this, it); it.fix.run(); });
            card.addView(r);
        }
        if (!need.isEmpty()) {
            TextView left = Ui.text(this, "Осталось: " + need.size() + " из " + all.size(), 14, Ui.SOFT, "sans-serif-medium");
            left.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), 0, 0);
            root.addView(left);
        }
        TextView why = Ui.text(this, "Разрешения не заставляют «Звонок» работать чаще: пока экран выключен, он ничего не делает и телефон не будит "
                + "(кроме вибрации перед звонком, если ты её включишь). "
                + "Они нужны, чтобы система не закрывала его и показывала таймер вовремя.", 13, Ui.MUTED, null);
        why.setLineSpacing(0, 1.15f);
        why.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), 0);
        root.addView(why);
    }

    /** Вышла версия новее: единственное, что можно сделать в приложении, это скачать её. */
    private void update() {
        LinearLayout root = Ui.screen(this, "Обнови «Звонок»", "Вышла версия " + Updates.latestName(this) + ", у тебя " + Updates.currentName(this)
                + ". Расписание и замены верные только в последней версии, поэтому со старой приложение дальше не пускает");
        root.getChildAt(0).setVisibility(View.GONE);
        LinearLayout card = Ui.card(this);
        root.addView(card, Ui.mw(this, 20));
        String[][] steps = {
                {"Скачай файл", "Zvonok-165.apk, меньше 300 КБ"},
                {"Открой его и нажми «Обновить»", "Новая версия встанет поверх старой"},
                {"Готово", "Класс, кружки и оформление останутся на месте"},
        };
        for (int i = 0; i < steps.length; i++) {
            LinearLayout r = Ui.horizontal(this);
            r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            TextView n = Ui.text(this, String.valueOf(i + 1), 15, Ui.MUTED, "sans-serif-medium");
            n.setMinWidth(Ui.dp(this, 28));
            r.addView(n);
            LinearLayout t = Ui.twoLines(this, steps[i][0], steps[i][1]);
            t.setLayoutParams(Ui.weight(1));
            r.addView(t);
            card.addView(r);
        }
        root.addView(Ui.button(this, "Скачать обновление", true, v -> {
            Ui.tick(v);
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Updates.APK_URL)));
            } catch (RuntimeException e) {
                Toast.makeText(this, "Не нашлось браузера. Открой сайт «Звонка» и скачай оттуда", Toast.LENGTH_LONG).show();
            }
        }), Ui.mw(this, 16));
        TextView note = Ui.text(this, "Уведомление с таймером и виджеты пока работают как раньше", 13, Ui.MUTED, null);
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), 0);
        root.addView(note);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        Updater.update(this);
        build();
    }

    @Override
    public void onBackPressed() {
        if (review() && !Updates.available(this)) finish();
        else moveTaskToBack(true); // шаг обязательный: «Назад» сворачивает приложение, а не пропускает его
    }
}
