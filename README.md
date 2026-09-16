# Health Connect → Google Drive Export

Android-приложение: читает данные из **Health Connect** (куда пишет Samsung Health) и раз в сутки кладёт zip в твою папку Drive.

Репозиторий: https://github.com/sdvirk-tech/health-connect-drive-export  
Package / `applicationId`: `ru.sdvirk.healthsync`

## Что делает

1. Запрашивает разрешения Health Connect (пульс, покой, HRV, сон, SpO₂, давление, вес, шаги, дистанция, тренировки; плюс чтение в фоне и истории).
2. Читает записи за N дней (по умолчанию 7).
3. Пишет JSON + summary в zip (`health_export_YYYY-MM-DD_HHmm.zip`).
4. POST JSON `{ fileName, mimeType, fileBase64, secret? }` на Upload URL (Apps Script Web App) → файл в Drive-папку.
5. Если пульс из Samsung Health **не попадает** в Health Connect — приложение на **Galaxy Watch** (Wear OS) читает пульс через Health Services и шлёт пробы на телефон. Они попадают в zip как `watchSamples`.

**Не** логинится в Samsung Health и **не** парсит UI.

## Требования

- Android 9+ (API 28), лучше 14+ с системным Health Connect.
- JDK 17+ для сборки.
- На телефоне установлены Samsung Health и Health Connect.
- Для пульса с запястья: Galaxy Watch 4+ (Wear OS 3+), Bluetooth с телефоном.

## Сборка APK

### Android Studio

1. Открой корень репозитория в Android Studio (2025.1+; нужен AGP 8.9).
2. Дождись Sync Gradle (wrapper уже в репозитории: Gradle 8.11.1, compileSdk 36).
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Командная строка

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
# Телефон: app/build/outputs/apk/debug/app-debug.apk
# Часы:    wear/build/outputs/apk/debug/wear-debug.apk
```

Установка на телефон по USB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Часы: пульс напрямую (Wear OS)

Samsung Health часто **не пишет пульс** в Health Connect. Тогда телефонный zip пустой по HR. Модуль `:wear` снимает пульс (и шаги, если Health Services их отдаёт) с датчика часов и передаёт на телефон по Wear Data Layer.

`applicationId` часов: `ru.sdvirk.healthsync.wear` (телефонный `ru.sdvirk.healthsync` не меняется).

1. Включи режим разработчика на часах (Настройки → О часах → версия ПО, 5 нажатий) → отладка по Wi‑Fi / беспроводная отладка.
2. Собери и поставь оба APK (телефон и часы должны быть сопряжены):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb connect <IP_ЧАСОВ>:<ПОРТ>
adb -s <IP_ЧАСОВ>:<ПОРТ> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

3. На часах открой **Health Sync Watch** → **Разрешения** → датчики тела (лучше «Всегда» / background) → **Фон: пульс**.
4. **Замерить сейчас** — живой пульс, когда часы на запястье.
5. **На телефон** — отправить накопленные пробы. Фоновый сервис тоже пытается слать сам.
6. На телефоне в статусе: `С часов на телефоне: N проб`. **Выгрузить сейчас** — в JSON поле `watchSamples` (`type: heart_rate|steps`, `source: wear`).

Пробы хранятся локально ~90 дней и не затирают старые zip.

Подробности: [`docs/wear-os.md`](docs/wear-os.md).

## Разрешения: Samsung Health → Health Connect

Приложение читает **только Health Connect**. Если Samsung Health не пишет туда данные, zip будет пустым по типам.

1. Обнови **Samsung Health** и **Health Connect** из Play Store (на новых Samsung Health Connect обычно системный).
2. Открой **Samsung Health → Настройки → Health Connect** (на части прошивок: «Подключённые сервисы» / «Данные и разрешения»).
3. Разреши Samsung Health **записывать** в Health Connect те же типы:
   - пульс и пульс покоя
   - HRV (вариабельность)
   - сон
   - насыщение кислородом (SpO₂)
   - давление
   - вес
   - шаги, дистанция
   - тренировки
4. В **Health Sync** нажми **«Запросить разрешения Health Connect»** и выдай чтение тех же типов, плюс «в фоне» и «история» (для автовыгрузки и окна больше 30 дней).
5. Подожди синхронизацию (иногда несколько минут / зарядка / Wi‑Fi) и нажми **«Выгрузить сейчас»**.
6. В статусе смотри счётчики: `HR samples: 0 · Sleep: 0 …` значит тип не дошёл до Health Connect.

Системные настройки (дубль): **Настройки Android → Health Connect → Доступ приложений**.

## Upload URL

1. Разверни Web App по инструкции [`docs/apps-script-upload.md`](docs/apps-script-upload.md).
2. Вставь URL `https://script.google.com/macros/s/…/exec` в поле **Upload URL**.
3. Секрет — необязательно; если задал в скрипте, укажи тот же в приложении.
4. **«Выгрузить сейчас»** → в папке Drive появится новый zip (старые файлы не удаляются).
5. **«Автораз в сутки»** — WorkManager, нужен интернет.

Без URL zip остаётся в кэше приложения (`…/cache/health_export_….zip`).

## Drive-папка

`YOUR_DRIVE_FOLDER_URL` (укажи свою папку Drive; не коммить личный URL)

## Статус

- [x] Gradle wrapper + манифест + разрешения HC (включая фон / историю)
- [x] Reader / JSON zip / WorkManager / UI
- [x] Надёжная загрузка в Apps Script: JSON + base64 (не multipart)
- [x] Wear OS: пульс/шаги с часов → телефон → `watchSamples` в zip
- [ ] OAuth Drive API как альтернатива
- [ ] SQLite-формат как в текущем пустом `health_connect_export.db` (по желанию)
