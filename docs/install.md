# Сборка и установка (телефон + часы)

В репозитории **два приложения**, не одно.

| Gradle-модуль | APK | Куда ставить | `applicationId` |
|---|---|---|---|
| `:app` | `app/build/outputs/apk/debug/app-debug.apk` | **телефон** | `ru.sdvirk.healthsync` |
| `:wear` | `wear/build/outputs/apk/debug/wear-debug.apk` | **Galaxy Watch** | `ru.sdvirk.healthsync.wear` |

Не открывай отдельный проект вроде `_Health-wear` и не жми Run на шаблоне `com.example.health_wear`. Модуль часов уже есть: папка `wear/` в этом репозитории.

## Ошибка `INSTALL_FAILED_MISSING_SHARED_LIBRARY`

```
requires unavailable shared library com.google.android.wearable
```

Это значит: **Wear-APK поставили на телефон** (или на эмулятор телефона). Библиотека `com.google.android.wearable` есть только на Wear OS.

Как получается:

1. В Android Studio вверху выбрана конфигурация **wear** / **app** из Wear-проекта, а в списке устройств — **телефон**.
2. Или открыт отдельный Wear-шаблон (`com.example.health_wear`), а не корень `health-connect-drive-export`.

Что делать: ставь **`:app` на телефон**, **`:wear` на часы**. См. ниже.

## 1. Открыть проект

Android Studio 2025.1+ (нужен AGP 8.9, JDK 17):

1. **File → Open** → папка **корня** репозитория (`health-connect-drive-export`), не `_Health-wear` и не `wear/`.
2. Дождись **Gradle Sync**.
3. Слева в Project должны быть модули `app`, `wear`, `shared`.

Командная строка (из корня, Windows):

```bat
gradlew.bat :app:assembleDebug :wear:assembleDebug
```

macOS / Linux:

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
```

Готовые файлы:

- телефон: `app\build\outputs\apk\debug\app-debug.apk`
- часы: `wear\build\outputs\apk\debug\wear-debug.apk`

Не ставь APK из `build\intermediates\...` — бери `outputs\apk\debug\`.

## 2. Телефон

1. USB-отладка на телефоне.
2. В Android Studio сверху: конфигурация **app**, устройство — **твой телефон** → Run.
3. Или:

```bat
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Если подключены и телефон, и часы, укажи серийник телефона:

```bat
adb -s <СЕРИЙНИК_ТЕЛЕФОНА> install -r app\build\outputs\apk\debug\app-debug.apk
```

## 3. Часы (Galaxy Watch 4+)

Часы должны быть **сопряжены** с телефоном (приложение Galaxy Wearable / Watch). Tizen-часы (Watch 3 и старше) не подходят — нужен Wear OS.

### Режим разработчика на часах

1. **Настройки → О часах → Сведения о ПО**.
2. Несколько раз нажми **Версия ПО** (или номер сборки), пока не появится «вы разработчик».
3. **Настройки → Параметры разработчика**:
   - **Отладка ADB** — вкл
   - **Отладка по Wi‑Fi** / **Беспроводная отладка** — вкл
4. Часы и компьютер в одной Wi‑Fi сети. Смотри IP и порт на экране отладки.

### Подключить adb к часам

Wear OS 4/5 (часто два порта: pairing и connect):

```bat
adb pair <IP>:<ПОРТ_СОПРЯЖЕНИЯ>
```

Введи код с часов, затем:

```bat
adb connect <IP>:<ПОРТ_ПОДКЛЮЧЕНИЯ>
adb devices
```

В списке должны быть **два** device: телефон и `IP:порт` часов.

Старые прошивки (просто Wi‑Fi ADB, порт 5555):

```bat
adb connect <IP_ЧАСОВ>:5555
```

### Поставить Wear-APK на часы

Android Studio: конфигурация **wear**, в списке устройств выбери **часы** (не телефон) → Run.

Или:

```bat
adb -s <IP_ЧАСОВ>:<ПОРТ> install -r wear\build\outputs\apk\debug\wear-debug.apk
```

Если `adb devices` показывает одно устройство-часы, можно без `-s`.

На часах появится **Health Sync Watch**.

## 4. Первый запуск

**На часах**

1. Открой Health Sync Watch.
2. **Разрешения** — датчики тела. В настройках часов: приложение → датчики → **Всегда** / Allow all the time.
3. **Фон: пульс**.
4. Надень часы, **Замерить сейчас**.
5. **На телефон** — отправить пробы (телефон рядом, Bluetooth вкл, Health Sync открыт хотя бы раз).

**На телефоне**

В статусе: `С часов на телефоне: N проб`. Затем **Выгрузить сейчас**. В zip поле `watchSamples`.

## Android Studio: две конфигурации

Вверху слева — не один Run.

| Run configuration | Device |
|---|---|
| `app` | телефон |
| `wear` | часы |

Если выбран `wear` + телефон → будет `INSTALL_FAILED_MISSING_SHARED_LIBRARY` или отказ из‑за `android.hardware.type.watch`.

## Если часы не видны в Studio

```bat
adb kill-server
adb start-server
adb devices
adb connect <IP>:<ПОРТ>
```

Переподключи беспроводную отладку на часах (IP мог смениться).

Эмулятор Wear в AVD можно использовать только чтобы проверить, что APK ставится. Пульса там нет — нужен реальный Galaxy Watch.
