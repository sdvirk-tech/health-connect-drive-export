# Debug APK 0.3.7 (ветка watch-vitals-export)

Скачать **оба** файла одной версии:

- Телефон: [HealthSync-phone-0.3.7-debug.apk](HealthSync-phone-0.3.7-debug.apk)
- Часы: [HealthSync-wear-0.3.7-debug.apk](HealthSync-wear-0.3.7-debug.apk) — **только на Galaxy Watch**

На Galaxy Watch Ultra (SM-L705F) Health Services отдаёт: пульс, шаги, калории, дистанцию, этажи, набор высоты + сон как состояние asleep. SpO₂/давление/ЭКГ в Health Services нет.

```bat
cd C:\IT\Cursor\health-connect-drive-export
git fetch origin
git checkout cursor/watch-vitals-export-8125
git pull
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" install -r dist\HealthSync-phone-0.3.7-debug.apk
.\install-watch.cmd -Apk dist\HealthSync-wear-0.3.7-debug.apk -Watch 192.168.2.142:44643
```
