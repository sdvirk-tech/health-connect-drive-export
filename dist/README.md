# Debug APK 0.3.6 (ветка watch-vitals-export)

Скачать **оба** файла одной версии:

- Телефон: [HealthSync-phone-0.3.6-debug.apk](HealthSync-phone-0.3.6-debug.apk)
- Часы: [HealthSync-wear-0.3.6-debug.apk](HealthSync-wear-0.3.6-debug.apk) — **только на Galaxy Watch**

Пульс идёт по **уже спаренному Bluetooth** (RFCOMM), не через Health Connect. Health Sync на телефоне оставь открытым. На часах разреши датчики — пульс снимается сам и уходит на телефон.

```bat
cd C:\IT\Cursor\health-connect-drive-export
git fetch origin
git checkout cursor/watch-vitals-export-8125
git pull
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" install -r dist\HealthSync-phone-0.3.6-debug.apk
.\install-watch.cmd -Apk dist\HealthSync-wear-0.3.6-debug.apk -Watch 192.168.2.142:44643
```
