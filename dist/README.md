# Debug APK 0.3.0 (ветка watch-vitals-export)

Скачать:

- Телефон: [HealthSync-phone-0.3.0-debug.apk](HealthSync-phone-0.3.0-debug.apk) — `ru.sdvirk.healthsync`
- Часы: [HealthSync-wear-0.3.0-debug.apk](HealthSync-wear-0.3.0-debug.apk) — `ru.sdvirk.healthsync.wear` **только на Galaxy Watch**

Часы (из корня репозитория, без Gradle):

```bat
.\install-watch.cmd -Pair 192.168.2.142:ПОРТ_КОДА -PairCode 123456 -Watch 192.168.2.142:ПОРТ_ПОДКЛЮЧЕНИЯ
```

Или вручную:

```bat
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
"%ADB%" pair 192.168.2.142:ПОРТ_КОДА 123456
"%ADB%" connect 192.168.2.142:ПОРТ_ПОДКЛЮЧЕНИЯ
"%ADB%" -s 192.168.2.142:ПОРТ_ПОДКЛЮЧЕНИЯ install -r dist\HealthSync-wear-0.3.0-debug.apk
```

Телефон по USB:

```bat
"%ADB%" install -r dist\HealthSync-phone-0.3.0-debug.apk
```

Wear-APK на телефон не ставить (`MISSING_SHARED_LIBRARY`).
