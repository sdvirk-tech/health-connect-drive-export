# Health Sync Watch (Wear OS)

Companion к телефону. Собирает показания и шлёт их в **Health Sync** на телефоне (`/healthsync/samples`) → `watch_samples.jsonl` → zip (`watchSamples`) в выбранную папку.

Нужны **Wear OS 3+** (Galaxy Watch 4 / 5 / 6 / 7 / Ultra). Tizen не поддерживается.

## Что откуда берётся

| Тип | Живой датчик (Health Services) | Health Connect на часах |
|---|---|---|
| Пульс | да (фон + «Замерить пульс») | да, если Samsung пишет |
| Шаги | да, если HS умеет | — |
| HRV | только если HS объявит тип (на Ultra обычно нет) | да, если Samsung пишет |
| SpO₂ | то же | да, если Samsung пишет |
| Сон | нет | да, если Samsung пишет |
| Давление | нет | да, после замера в Samsung Health Monitor и записи в HC |
| ЭКГ | нет | в Health Connect 1.1 записи ECG нет; остаётся Samsung Health Monitor |

Разрешения в приложении **читают** данные. Пустой тип при «все галочки выданы» почти всегда значит: **Samsung Health не пишет этот тип в Health Connect**. На часах кнопка **«Почему пусто»** показывает, что умеет Health Services и есть ли HC.

## На часах

1. **Разрешения** — датчики тела (Always) + Health Connect.
2. **Фон: датчики** — PassiveMonitoring (пульс/шаги).
3. **Замерить пульс** — ~25 с оптического датчика.
4. **HC: сон/SpO2/BP/HRV** — читает Health Connect на часах за 7 дней.
5. **На телефон** — Wear Data Layer. Телефон рядом, Health Sync хотя бы раз открыт.

Затем на телефоне: **Папка для zip** → **Выгрузить сейчас**. Zip содержит `watchSamples` и записи Health Connect телефона.

`applicationId` часов: `ru.sdvirk.healthsync.wear`.
