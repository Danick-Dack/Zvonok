package app.urok7b;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.view.View;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Как на сайте (site/src/js/fun.js): серия дней, звуки часов и звонка, иконка приложения на выбор, мелкие анимации. */
final class Fun {

    static final String KEY_SOUND = "sound";
    private static final String KEY_STREAK = "streak", KEY_STREAK_DAY = "streak_day", KEY_STREAK_BEST = "streak_best";
    private static final int RATE = 22050;
    private static AudioTrack tick, bell;

    private Fun() {}

    // ---------- серия дней ----------

    /**
     * Серия растёт, когда открываешь расписание в новый день. Выходные и каникулы её не рвут, пропущенный учебный день рвёт.
     * @return {серия, рекорд, 1 если серия только что выросла}
     */
    static int[] streak(Context c) {
        SharedPreferences p = Updater.prefs(c);
        long today = LocalDate.now().toEpochDay(), last = p.getLong(KEY_STREAK_DAY, 0);
        int n = p.getInt(KEY_STREAK, 0), best = p.getInt(KEY_STREAK_BEST, 0);
        if (last == today) return new int[]{n, best, 0};
        boolean kept = n > 0 && last < today && today - last < 120;
        for (long d = last + 1; kept && d < today; d++) {
            LocalDate day = LocalDate.ofEpochDay(d);
            if (Schedule.dayOff(day) == null && !Schedule.lessonsOn(day).isEmpty()) kept = false;
        }
        n = kept ? n + 1 : 1;
        best = Math.max(best, n);
        p.edit().putLong(KEY_STREAK_DAY, today).putInt(KEY_STREAK, n).putInt(KEY_STREAK_BEST, best).apply();
        return new int[]{n, best, 1};
    }

    // ---------- звуки: файлов нет, щелчок часов и звонок считаются на лету ----------

    static boolean soundOn(Context c) {
        return Updater.prefs(c).getBoolean(KEY_SOUND, false);
    }

    /** Щелчок секундной стрелки или (ring) школьный звонок на две секунды: молоточек бьёт по чашке 20 раз в секунду. */
    static void play(Context c, boolean ring) {
        if (!soundOn(c)) return;
        try {
            AudioTrack t = ring ? bell : tick;
            if (t == null) {
                t = make(ring);
                if (ring) bell = t;
                else tick = t;
            }
            if (t.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) t.stop();
            t.reloadStaticData();
            t.play();
        } catch (RuntimeException e) {
            // телефон не дал звук: остаёмся без него
        }
    }

    private static AudioTrack make(boolean ring) {
        short[] pcm = new short[ring ? RATE * 22 / 10 : RATE * 3 / 100];
        for (int i = 0; i < pcm.length; i++) {
            double t = i / (double) RATE;
            double wave = ring ? Math.signum(Math.sin(2 * Math.PI * 2093 * t)) * 0.5 + Math.asin(Math.sin(2 * Math.PI * 3136 * t)) / Math.PI
                    : Math.signum(Math.sin(2 * Math.PI * 2000 * t));
            pcm[i] = (short) (wave * Math.exp(-(ring ? t % 0.05 * 60 : t * 150)) * (ring ? 9000 : 5000));
        }
        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length * 2).build();
        t.write(pcm, 0, pcm.length);
        return t;
    }

    /** Экран ушёл с глаз: звук отпускаем. */
    static void quiet() {
        for (AudioTrack t : new AudioTrack[]{tick, bell}) if (t != null) t.release();
        tick = bell = null;
    }

    // ---------- анимации ----------

    /** Вид «подпрыгивает»: размер проходит по точкам scale за ms миллисекунд, times раз. */
    static void pop(View v, long ms, int times, float... scale) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofFloat(View.SCALE_X, scale),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scale));
        a.setDuration(ms);
        a.setRepeatCount(times - 1);
        a.start();
    }

    // ---------- иконка приложения: ярлыки (activity-alias) из манифеста, включён всегда один ----------

    /** Ярлыки с подписью icon_name в порядке манифеста. Свою иконку достаточно дописать в манифест, здесь она появится сама. */
    static List<ActivityInfo> icons(Context c) {
        List<ActivityInfo> out = new ArrayList<>();
        try {
            ActivityInfo[] all = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS).activities;
            for (ActivityInfo i : all == null ? new ActivityInfo[0] : all)
                if (i.targetActivity != null && i.metaData != null && i.metaData.containsKey("icon_name")) out.add(i);
        } catch (PackageManager.NameNotFoundException e) {
            // своего пакета не может не быть
        }
        return out;
    }

    static boolean iconOn(Context c, ActivityInfo i) {
        int st = c.getPackageManager().getComponentEnabledSetting(new ComponentName(c, i.name));
        return st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ? i.enabled : st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    /** Сначала включаем новый значок, потом выключаем остальные: без значка приложение не остаётся ни на миг. */
    static void setIcon(Context c, ActivityInfo pick) {
        PackageManager pm = c.getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(c, pick.name), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (ActivityInfo i : icons(c))
            if (!i.name.equals(pick.name))
                pm.setComponentEnabledSetting(new ComponentName(c, i.name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }
}
