# Debug APK 0.3.0 (ветка watch-vitals-export)

Скачать:

- Телефон: [HealthSync-phone-0.3.0-debug.apk](HealthSync-phone-0.3.0-debug.apk) — `ru.sdvirk.healthsync`
- Часы: [HealthSync-wear-0.3.0-debug.apk](HealthSync-wear-0.3.0-debug.apk) — `ru.sdvirk.healthsync.wear` **только на Galaxy Watch**

```bat
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r HealthSync-phone-0.3.0-debug.apk
& $adb -s IP:ПОРТ install -r HealthSync-wear-0.3.0-debug.apk
```

Wear-APK на телефон не ставить (`MISSING_SHARED_LIBRARY`).
