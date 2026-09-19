# Debug APK 0.3.4 (ветка watch-vitals-export)

Скачать **оба** файла одной версии:

- Телефон: [HealthSync-phone-0.3.4-debug.apk](HealthSync-phone-0.3.4-debug.apk)
- Часы: [HealthSync-wear-0.3.4-debug.apk](HealthSync-wear-0.3.4-debug.apk) — **только на Galaxy Watch**

Связь часов с телефоном идёт по **Wi-Fi** (скан подсети :8765, не Play Companion). Health Sync на телефоне оставьте открытым, та же сеть, Wi-Fi на часах включён. На часах: **Лог на телефон**. Лог сам появится на экране телефона.

На Samsung **нет отдельного приложения Health Connect**. Кнопка «Настройки Health Connect» больше не открывает Health Sync само на себя. Разрешения: **Запросить разрешения Health Connect**. Писатель данных: Samsung Health → Настройки → Health Connect.

```bat
cd C:\IT\Cursor\health-connect-drive-export
git fetch origin
git checkout cursor/watch-vitals-export-8125
git pull
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" install -r dist\HealthSync-phone-0.3.4-debug.apk
.\install-watch.cmd -Apk dist\HealthSync-wear-0.3.4-debug.apk -Watch 192.168.2.142:44643
```
