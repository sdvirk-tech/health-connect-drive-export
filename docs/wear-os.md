# Health Sync Watch (Wear OS)

Companion к телефону. Собирает показания и шлёт их в **Health Sync** на телефоне (`/healthsync/samples`) → `watch_samples.jsonl` → zip (`watchSamples`) в выбранную папку.

Нужны **Wear OS 3+** (Galaxy Watch 4 / 5 / 6 / 7 / Ultra). Tizen не поддерживается.

## Что откуда берётся

| Тип | Живой датчик (Health Services) | Health Connect на часах |
|---|---|---|
| Пульс | да (фон + «Замерить пульс») | да, если Samsung пишет |
| Шаги / калории / дистанция / этажи / высота | да, все типы, которые часы объявляют (на Ultra есть) | — |
| Сон | состояние asleep (надень часы на ночь) | да, если Samsung пишет (на Ultra HC нет) |
| HRV | только если HS объявит тип (на Ultra нет) | да, если Samsung пишет на телефоне |
| SpO₂ | то же | да, если Samsung пишет на телефоне |
| Давление | нет | да, после замера в Samsung Health Monitor и записи в HC телефона |
| ЭКГ | нет | в Health Connect 1.1 записи ECG нет; остаётся Samsung Health Monitor |

Разрешения в приложении **читают** данные. Пустой тип при «все галочки выданы» почти всегда значит: **Samsung Health не пишет этот тип в Health Connect**. На часах кнопка **«Почему пусто»** показывает, что умеет Health Services и есть ли HC.

## На часах

1. **Разрешения** — датчики тела (Always) + Health Connect.
2. **Все датчики (фон)** — подписка на **все** типы PassiveMonitoring, которые часы умеют (пульс, шаги, калории, дистанция, этажи, высота + состояние asleep).
3. **Замерить пульс** — ~25 с оптического датчика.
4. **HC: сон/SpO2/BP/HRV** — читает Health Connect на часах за 30 дней.
5. **Почему пусто** / **Лог на телефон** — собирает диагностику и шлёт её в Health Sync на телефоне (кнопка «Показать лог часов», файл `watch_diag.txt` в zip).
На Ultra Health Services отдаёт пульс, шаги, калории, дистанцию, этажи, набор высоты. Сон — состояние asleep. SpO₂/HRV/давление/ЭКГ в датчиках HS нет.

При нулях после всех галочек: разрешения Health Sync только читают. Samsung Health должен **писать** в Health Connect (Настройки → Health Connect). Живой пульс — кнопка «Замерить пульс» на часах, не Health Connect.

Затем на телефоне: **Папка для zip** → **Выгрузить сейчас**. Zip содержит `watchSamples` и записи Health Connect телефона.

`applicationId` часов: `ru.sdvirk.healthsync.wear`.

Установка с ПК (без Gradle): `.\install-watch.cmd -Apk dist\HealthSync-wear-0.3.7-debug.apk -Watch 192.168.2.142:44643`. Подробности: [`docs/install.md`](install.md).
