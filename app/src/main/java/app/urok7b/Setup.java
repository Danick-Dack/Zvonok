package app.urok7b;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Обязательная настройка телефона: пока не включено всё, без чего таймер в уведомлении, на экране блокировки
 * и в виджетах отстаёт или пропадает, дальше приложение не пускает. Пункты, которые проверить нельзя
 * (автозапуск у некоторых марок), достаточно один раз открыть. Возвращается сам, если что-то выключат.
 */
public class Setup extends Activity {

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        List<Perms.Item> all = Perms.list(this);
        java.util.Set<String> need = new java.util.HashSet<>();
        for (Perms.Item i : Perms.missing(this)) need.add(i.key);
        if (need.isEmpty()) {
            Toast.makeText(this, "Всё готово", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        LinearLayout root = Ui.screen(this, "Настрой телефон", "Без этого таймер в уведомлении, на экране блокировки и в виджетах "
                + "может отставать или пропасть. Нажми на каждый пункт и разреши в открывшихся настройках, потом вернись сюда.");
        root.getChildAt(0).setVisibility(View.GONE); // без стрелки «назад»: шаг обязательный
        LinearLayout card = Ui.card(this);
        root.addView(card, Ui.mw(this, 18));
        for (Perms.Item it : all) {
            boolean todo = need.contains(it.key);
            boolean optional = !todo && Boolean.FALSE.equals(it.ok); // по желанию (таймер в строке состояния), но выключен
            LinearLayout r = Ui.horizontal(this);
            r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            LinearLayout t = Ui.twoLines(this, it.title, it.why);
            t.setLayoutParams(Ui.weight(1));
            r.addView(t);
            TextView st = Ui.text(this, !todo && !optional ? "✓ Готово" : it.ok == null ? "Открыть" : "Включить", 14,
                    todo ? Ui.ON_ACCENT : optional ? Ui.ACCENT : Ui.MUTED, "sans-serif-medium");
            if (todo) st.setBackground(Ui.round(this, Ui.ACCENT, 12, 0));
            else if (optional) st.setBackground(Ui.round(this, 0, 12, Ui.ACCENT));
            st.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = Ui.dp(this, 10);
            r.addView(st, lp);
            r.setContentDescription(it.title + ". " + it.why + ". " + (todo ? "Нужно включить" : optional ? "Выключено, по желанию" : "Готово"));
            r.setOnClickListener(v -> { Ui.tick(v); Perms.markSeen(this, it); it.fix.run(); });
            card.addView(r);
        }
        TextView left = Ui.text(this, "Осталось включить: " + need.size(), 15, Ui.SOFT, "sans-serif-medium");
        left.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), 0, 0);
        root.addView(left);
        TextView why = Ui.text(this, "Разрешения не заставляют «Звонок» работать чаще: пока экран выключен, он ничего не делает и телефон не будит "
                + "(кроме вибрации перед уроком, если ты её включишь). "
                + "Разрешения нужны, чтобы система не закрывала его и показывала таймер вовремя.", 13, Ui.MUTED, null);
        why.setLineSpacing(0, 1.15f);
        why.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), 0);
        root.addView(why);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        Updater.update(this);
        build();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true); // шаг обязательный: «Назад» сворачивает приложение, а не пропускает настройку
    }
}
