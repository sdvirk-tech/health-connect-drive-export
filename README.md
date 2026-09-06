# Health Connect → Google Drive Export

Android-приложение: читает данные из **Health Connect** (куда пишет Samsung Health) и раз в сутки кладёт `health_connect_export.zip` в твою папку Drive.

Репозиторий: https://github.com/sdvirk-tech/health-connect-drive-export

## Что делает

1. Запрашивает разрешения Health Connect (пульс, покой, HRV, сон, SpO₂, давление, вес, шаги, дистанция, тренировки).
2. Читает записи за N дней.
3. Пишет JSON + summary в zip.
4. POST на твой Upload URL (Apps Script Web App или свой backend) → файл в Drive-папку.

**Не** логинится в Samsung Health и **не** парсит UI.

## Требования

- Android 9+ (API 28), лучше 14+ с системным Health Connect.
- На телефоне: Samsung Health → настройки → Health Connect → разрешить те же типы данных.
- Установлен Health Connect (на новых Samsung обычно встроен).

## Сборка

1. Открой проект в Android Studio (Ladybug+).
2. Sync Gradle, собери debug APK.
3. Установи на телефон, выдай разрешения.
4. Вставь Upload URL (см. `docs/apps-script-upload.md`).
5. «Выгрузить сейчас» → проверь папку Drive.

## Drive-папка

https://drive.google.com/drive/folders/10pwzTmlxVLAshc7_DfOuecSktde-HKjw

## Статус скелета

- [x] Gradle + манифест + разрешения HC
- [x] Reader / JSON zip / WorkManager / UI
- [ ] Реальный Apps Script endpoint (шаблон в docs)
- [ ] OAuth Drive API как альтернатива
- [ ] SQLite-формат как в текущем пустом `health_connect_export.db` (по желанию)
