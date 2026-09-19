# Debug APK 0.3.2 (ветка watch-vitals-export)

Скачать:

- Телефон: [HealthSync-phone-0.3.2-debug.apk](HealthSync-phone-0.3.2-debug.apk) — `ru.sdvirk.healthsync`
- Часы: [HealthSync-wear-0.3.2-debug.apk](HealthSync-wear-0.3.2-debug.apk) — `ru.sdvirk.healthsync.wear` **только на Galaxy Watch**

Нужны **оба** APK одной версии. На часах: **Почему пусто** или **Лог на телефон** → на телефоне кнопка **Показать лог часов**.

```bat
cd C:\IT\Cursor\health-connect-drive-export
git fetch origin
git checkout cursor/watch-vitals-export-8125
git pull
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" install -r dist\HealthSync-phone-0.3.2-debug.apk
.\install-watch.cmd -Apk dist\HealthSync-wear-0.3.2-debug.apk -Watch 192.168.2.142:44643
```

Wear-APK на телефон не ставить (`MISSING_SHARED_LIBRARY`).
