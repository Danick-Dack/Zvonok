package app.urok7b;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Срабатывает каждую минуту в школьное время, а также после перезагрузки и смены времени. */
public class TickReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Updater.update(ctx);
    }
}
