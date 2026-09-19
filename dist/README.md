# Debug APK 0.3.0 (ветка watch-vitals-export)

Скачать:

- Телефон: [HealthSync-phone-0.3.0-debug.apk](HealthSync-phone-0.3.0-debug.apk) — `ru.sdvirk.healthsync`
- Часы: [HealthSync-wear-0.3.0-debug.apk](HealthSync-wear-0.3.0-debug.apk) — `ru.sdvirk.healthsync.wear` **только на Galaxy Watch**

Часы (из корня репозитория, без Gradle):

```bat
cd C:\IT\Cursor\health-connect-drive-export
git fetch origin
git checkout cursor/watch-vitals-export-8125
.\install-watch.cmd -Apk C:\IT\Cursor\HealthWear\HealthSync-wear-0.3.0-debug.apk -Watch 192.168.2.142:44643
```

Не копируй слова `ПОРТ_КОДА`. Если часы уже `device`, `-Pair` не нужен.

Или вручную:

```bat
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" connect 192.168.2.142:44643
"%ADB%" -s 192.168.2.142:44643 install -r C:\IT\Cursor\HealthWear\HealthSync-wear-0.3.0-debug.apk
```

Телефон по USB:

```bat
"%ADB%" install -r dist\HealthSync-phone-0.3.0-debug.apk
```

Wear-APK на телефон не ставить (`MISSING_SHARED_LIBRARY`).
