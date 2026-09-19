# Debug APK 0.3.5 (ветка watch-vitals-export)

Скачать **оба** файла одной версии:

- Телефон: [HealthSync-phone-0.3.5-debug.apk](HealthSync-phone-0.3.5-debug.apk)
- Часы: [HealthSync-wear-0.3.5-debug.apk](HealthSync-wear-0.3.5-debug.apk) — **только на Galaxy Watch**

Обмен часов с телефоном идёт **постоянно по Bluetooth** (Nearby Connections + Wear Data Layer). Health Sync на телефоне оставьте открытым. Разреши Bluetooth в обоих приложениях. Wi-Fi — запасной канал.

```bat
cd C:\IT\Cursor\health-connect-drive-export
git fetch origin
git checkout cursor/watch-vitals-export-8125
git pull
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" install -r dist\HealthSync-phone-0.3.5-debug.apk
.\install-watch.cmd -Apk dist\HealthSync-wear-0.3.5-debug.apk -Watch 192.168.2.142:44643
```
