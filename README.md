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

## Сборка и установка

Полная инструкция (телефон / часы, Windows, ошибка `MISSING_SHARED_LIBRARY`): **[`docs/install.md`](docs/install.md)**.

Кратко:

```bat
gradlew.bat :app:assembleDebug :wear:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb connect <IP_ЧАСОВ>:<ПОРТ>
adb -s <IP_ЧАСОВ>:<ПОРТ> install -r wear\build\outputs\apk\debug\wear-debug.apk
```

- Конфигурация **app** в Android Studio → только **телефон**.
- Конфигурация **wear** → только **часы**. Wear-APK на телефон не ставится (`com.google.android.wearable`).
- Не используй отдельный шаблон `_Health-wear` / `com.example.health_wear` — модуль часов уже `:wear` в этом репо.

### Android Studio

1. Открой **корень** репозитория (2025.1+; нужен AGP 8.9).
2. Дождись Sync Gradle.
3. Run **app** на телефоне. Run **wear** на часах.

## Часы: пульс напрямую (Wear OS)

Samsung Health часто **не пишет пульс** в Health Connect. Тогда телефонный zip пустой по HR. Модуль `:wear` снимает пульс (и шаги, если Health Services их отдаёт) с датчика часов и передаёт на телефон по Wear Data Layer.

`applicationId` часов: `ru.sdvirk.healthsync.wear` (телефонный `ru.sdvirk.healthsync` не меняется).

Как собрать и поставить на часы (ADB Wi‑Fi, Android Studio, разбор `INSTALL_FAILED_MISSING_SHARED_LIBRARY`): **[`docs/install.md`](docs/install.md)**.

Как пользоваться на часах после установки: [`docs/wear-os.md`](docs/wear-os.md).

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
