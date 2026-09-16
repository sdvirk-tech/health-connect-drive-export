# Health Sync Watch (Wear OS)

Companion к телефону: снимает пульс с Galaxy Watch через **Health Services**, без Samsung Health и без Health Connect.

Нужны **Wear OS 3+** (Galaxy Watch 4 / 5 / 6 / 7 / Ultra). Tizen-часы не поддерживаются.

## Зачем

На телефоне Samsung Health часто не отдаёт пульс в Health Connect → в zip `HR samples: 0`. Часы читают датчик напрямую.

## Что собирается

- Пульс (`heart_rate`) — фон (PassiveMonitoring) + кнопка «Замерить сейчас» (MeasureClient).
- Шаги (`steps`) — если Health Services это умеет в фоне.

Пробы копятся на часах, уходят на телефон по Bluetooth (Wearable MessageClient, путь `/healthsync/samples`), на телефоне пишутся в `watch_samples.jsonl` и при выгрузке попадают в `health_export.json` → `watchSamples`.

## Сборка и установка

См. **[`docs/install.md`](install.md)** — там телефон vs часы, Android Studio, Windows `.\build-install.cmd` и ошибка `INSTALL_FAILED_MISSING_SHARED_LIBRARY` (Wear-APK нельзя ставить на телефон).

Не открывай отдельный проект `_Health-wear` / package `com.example.health_wear`. Модуль часов — `:wear` в этом репозитории.

## На часах

1. **Разрешения** — датчики тела; на Wear OS 4+ ещё «в фоне» / Always.
2. **Фон: пульс** — регистрирует PassiveListenerService (переживает закрытие приложения; после перезагрузки включается снова).
3. **Замерить сейчас** — ~25 секунд живого пульса. Надень часы плотнее.
4. **На телефон** — ручная отправка. Если телефон не найден: открой Health Sync на телефоне и проверь Bluetooth.

Настройки часов → Приложения → Health Sync Watch → Датчики → **Разрешить всегда**.

## На телефоне

В статусе: `Watch HR: N`. Zip содержит:

```json
"watchSamples": [
  { "type": "heart_rate", "time": "2026-09-16T20:00:00Z", "value": 68.0, "source": "wear" }
]
```

Старые zip не затираются. `applicationId` телефона не менялся (`ru.sdvirk.healthsync`).
