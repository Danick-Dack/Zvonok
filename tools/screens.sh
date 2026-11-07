#!/usr/bin/env bash
# Снимки экранов «Звонка» на эмуляторе (GitHub Actions, build.yml → screens).
#   bash tools/screens.sh <отладочный APK> <папка> <уровень API>
# Время и тема задаются снаружи: дата эмулятора (нужен root) и настройки приложения через run-as,
# поэтому в самом приложении никаких тестовых лазеек нет.
set -u
APK=$1 OUT=$2 API=$3 PKG=app.urok7b
mkdir -p "$OUT"
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
adb shell svc power stayon true
adb shell input keyevent 82
adb install -r "$APK" >/dev/null
sleep 20   # система после загрузки ещё занята: так меньше окон «приложение не отвечает»

# настройки приложения: класс 7Б, вопрос про обновления уже задан, тема $1
prefs() {
  adb shell am force-stop $PKG
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n<string name="class">7Б</string>\n<boolean name="update_asked" value="true" />\n<string name="app_scheme">%s</string>\n</map>\n' "$1" \
    | adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/settings.xml'"
}
at() { adb shell am force-stop $PKG; adb shell "date $1" >/dev/null; sleep 1; }   # MMDDhhmmYYYY.ss, время Алматы
tab() { adb shell am start -W -n $PKG/.MainActivity --es tab "$1" >/dev/null; sleep 2; }
# окно «… не отвечает» от занятого эмулятора закрываем кнопкой «Wait», чтобы не закрывало снимок
calm() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b; b=$(adb shell cat /sdcard/ui.xml 2>/dev/null | grep -o 'text="Wait"[^>]*' | grep -o 'bounds="[^"]*"' | head -1 | tr -dc '0-9,[]')
  if [ -n "$b" ]; then
    set -- $(echo "$b" | tr '[],' '   ')
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1
  fi
}
shot() { calm; adb exec-out screencap -p > "$OUT/api${API}_$1.png"; echo "снимок $1"; }

# первый запуск: обязательная настройка телефона, пока не выданы разрешения
prefs dark
at 111010202026.00
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 3; shot dark_0_setup
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell dumpsys deviceidle whitelist +$PKG >/dev/null

for scheme in dark light; do
  prefs $scheme
  at 111010202026.00   # вторник 10 ноября, 10:20: идёт 3 урок
  tab now;   shot ${scheme}_1_now_lesson
  tab week;  shot ${scheme}_2_week
  tab rooms; shot ${scheme}_3_rooms
  tab more;  shot ${scheme}_4_more
  at 111010382026.00   # перемена перед 4 уроком
  tab now;   shot ${scheme}_5_now_break
  at 111412002026.00   # суббота
  tab now;   shot ${scheme}_6_now_weekend
done

# уведомление во время урока (на Android 16 ещё и таймер в строке состояния)
prefs dark
at 111010202026.00
tab now
adb shell cmd statusbar expand-notifications; sleep 2; shot dark_7_notification
adb shell cmd statusbar collapse; sleep 1
# таймер в строке состояния виден, когда само приложение свёрнуто
adb shell input keyevent KEYCODE_HOME; sleep 3; shot dark_8_statusbar
flags=$(adb shell dumpsys notification --noredact | grep -A 40 "$PKG" | grep -m 3 -oE "flags=0x[0-9a-f]+|promot[A-Za-z]*=[a-z0-9]+")
echo "::notice::API $API: уведомление «Звонка»: $(echo $flags) (FLAG_PROMOTED_ONGOING = 0x40000)"

# крупный шрифт
adb shell settings put system font_scale 1.3
at 111010202026.00
tab now; shot dark_9_big_font
adb shell settings put system font_scale 1.0

# экран повёрнут набок: приложение не заперто в портрете, всё помещается и прокручивается
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
tab now; sleep 1; shot dark_10_landscape
adb shell settings put system user_rotation 0; sleep 1

# остальные экраны
for a in Changelog Feedback StyleActivity EditorActivity; do
  adb shell am start -W -n $PKG/.$a >/dev/null; sleep 2; shot "dark_screen_$a"
done
ls -la "$OUT"
