# Debug APK 0.3.3 (ветка watch-vitals-export)

Скачать **оба** файла одной версии:

- Телефон: [HealthSync-phone-0.3.3-debug.apk](HealthSync-phone-0.3.3-debug.apk)
- Часы: [HealthSync-wear-0.3.3-debug.apk](HealthSync-wear-0.3.3-debug.apk) — **только на Galaxy Watch**

Связь часов с телефоном идёт по **Wi-Fi** (не через Play Companion). Health Sync на телефоне оставьте открытым, та же сеть. На часах: **Лог на телефон**.

На Samsung **нет отдельного приложения Health Connect** — кнопка открывает системные настройки или экран разрешений Health Sync.

```bat
cd C:\IT\Cursor\health-connect-drive-export
git fetch origin
git checkout cursor/watch-vitals-export-8125
git pull
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" install -r dist\HealthSync-phone-0.3.3-debug.apk
.\install-watch.cmd -Apk dist\HealthSync-wear-0.3.3-debug.apk -Watch 192.168.2.142:44643
```
