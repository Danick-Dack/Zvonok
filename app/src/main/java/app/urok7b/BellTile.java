package app.urok7b;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.time.ZonedDateTime;

/**
 * Плитка в шторке быстрых настроек: урок, кабинет и сколько до звонка.
 * Считает, только пока шторка открыта (onStartListening..onStopListening): в остальное время её нет в памяти,
 * и заряд она не тратит. Нажатие открывает приложение.
 */
public class BellTile extends TileService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    // Минуты на плитке меняются только на границе минуты (звонки всегда ровно в начале минуты),
    // поэтому просыпаемся раз в минуту сразу после неё: показ тот же, работы вдвое меньше, чем раз в 30 секунд.
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            draw();
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 200);
        }
    };

    @Override
    public void onStartListening() {
        super.onStartListening();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    public void onStopListening() {
        handler.removeCallbacks(tick);
        super.onStopListening();
    }

    private void draw() {
        Tile t = getQsTile();
        if (t == null) return;
        Schedule.load(this);
        long nowMs = System.currentTimeMillis();
        Schedule.Status s = Schedule.compute(ZonedDateTime.now());
        Updater.Display d = Updater.describe(s, nowMs);
        String left = d.timer ? Schedule.fmtLeft(d.target - nowMs) : "";
        String label, sub;
        switch (s.kind) {
            case LESSON:
                label = d.title + (d.room.isEmpty() ? "" : " · " + d.room);
                sub = "звонок через " + left;
                break;
            case BREAK:
                label = d.nextSubject + (d.nextRoom.isEmpty() ? "" : " · " + d.nextRoom);
                sub = "урок через " + left;
                break;
            case BEFORE:
                label = d.nextSubject + (d.nextRoom.isEmpty() ? "" : " · " + d.nextRoom);
                sub = d.timer ? "уроки через " + left : "первый урок в " + d.big;
                break;
            default:
                label = d.title;
                sub = d.foot;
        }
        t.setLabel(label.isEmpty() ? "Звонок" : label);
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(sub);
        t.setContentDescription(label + ", " + sub);
        t.setState(d.live ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 11, i, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
