# ТЗ: Android-приложение «Health Sync → Drive»

**Цель:** легально читать данные из Health Connect (куда пишет Samsung Health) и ежедневно выгружать в Google Drive папку пользователя.

**Не делать:** UI-автоматизацию / логин в Samsung Health / скрейп экранов.

## Данные (MVP)

Читать за выбранный период (по умолчанию последние 7 дней + инкремент с последнего sync):

- Heart rate (+ resting HR если есть)
- Heart rate variability (RMSSD)
- Sleep sessions (+ stages если доступны)
- Oxygen saturation (SpO₂)
- Blood pressure
- Weight
- Steps / distance (опционально)
- Exercise sessions (если есть)

Формат выгрузки: один zip или папка с JSON/CSV по типам + `manifest.json` (время выгрузки, версии, диапазон дат, счётчики записей).

Целевая папка Drive (уже есть):  
`https://drive.google.com/drive/folders/10pwzTmlxVLAshc7_DfOuecSktde-HKjw`

Имя файла: `health_export_YYYY-MM-DD_HHmm.zip` (не затирать историю).

## UX

1. Первый запуск: запрос permissions Health Connect (только нужные READ).
2. Подсказка: в Samsung Health включить синк с Health Connect по тем же типам.
3. Кнопка «Выгрузить сейчас» + опция «Автораз в сутки» (WorkManager).
4. Статус последней выгрузки (успех/ошибка, сколько записей по типам).
5. Настройка: Google account / папка Drive (OAuth Drive scope минимальный: писать в выбранную папку).

## Техстек

- Kotlin, minSdk 28+, target актуальный
- androidx.health.connect:connect-client
- WorkManager для фонового sync
- Google Drive REST или Drive Android API (upload)
- Без root, без Accessibility-скрапинга

## Успех

- Приложение собирается (Gradle)
- README: установка, permissions, как связать Samsung Health → Health Connect
- Dummy/sample без реальных данных на эмуляторе; на устройстве с HC — реальный экспорт
- Чёткий список пустых vs заполненных типов в манифесте (чтобы ловить «пустой экспорт» как сейчас)

## Вне скоупа v1

Samsung Health Data SDK / партнёрка Samsung, парсинг UI, серверная аналитика.
