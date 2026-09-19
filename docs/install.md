# Сборка и установка (телефон + часы)

## Windows: одна команда

Две ошибки из PowerShell значат разное:

| Что написало | Почему | Что делать |
|---|---|---|
| `What went wrong: 25.0.3` | `JAVA_HOME` / JBR — Java **25+** (у тебя ещё бывает **27**). Gradle 8.11 так не запускается. | Не ставь JBR. `.\build-install.cmd` сам найдёт/поставит **JDK 17**. |
| `NativeCommandError` / `openjdk version "27"` | `java -version` пишет в stderr, а в скрипте стоял `Stop`. Это не «Java сломан». | `git pull` и снова `.\build-install.cmd`. |
| `Изменение политики выполнения` | Ты запустил `Set-ExecutionPolicy`. Для `.cmd` это не нужно. | **Ctrl+C**. Дальше только `.\build-install.cmd`. Если уже спросило — одна буква `A` и Enter, не `{A}` и не `"a"`. |
| `adb: no devices` / `device '192.168.2.142:36169' not found` | Wi‑Fi ADB на часах протух (порт меняется). `adb.exe` у тебя есть. | На часах выключи/включи беспроводную отладку, возьми **новый** IP:порт. |
| `".\install-watch.cmd" не является командой` | Файл есть только в ветке `cursor/watch-vitals-export-8125`. `git pull` её скачал, но не переключил. Папка `HealthWear` — не этот репозиторий. | `git fetch origin` затем `git checkout cursor/watch-vitals-export-8125`. Проверка: `dir install-watch.cmd`. |
| `pair ... ПОРТ_КОДА` / `protocol fault` | В команду вставили слова **ПОРТ_КОДА**, а не цифры с часов. | Порт сопряжения — только цифры, другой, чем `44643`. Если `adb devices` уже показывает `192.168.2.142:44643 device` — `pair` не нужен. |
| `RFCX718KLEZ unauthorized` | Телефон не нажал Allow. На установку часов это не влияет. | На телефоне: разблокировать → **Разрешить отладку по USB**. |

```bat
cd C:\IT\Cursor\health-connect-drive-export
git pull
.\build-install.cmd
```

Не запускай `Set-ExecutionPolicy` и не запускай `.\build-install.ps1`. Только `.cmd`.

Сборка уже прошла. `adb connect` без pairing на Galaxy Watch Ultra падает. Два разных порта:

1. Беспроводная отладка → на главном экране IP:порт (**подключение**, ты пробовал `192.168.2.142:36723`).
2. **Сопряжение по коду** / Pair with pairing code → **другой** порт и 6 цифр. Экран не закрывай.
3. ПК и часы в одной сети `192.168.2.x`.

```bat
.\build-install.cmd -Pair 192.168.2.142:37111 -PairCode 847291 -Watch 192.168.2.142:44643
```

Цифры `37111` / `847291` — **пример**. Бери порт и код с экрана «Сопряжение по коду». `44643` — порт подключения с главного экрана беспроводной отладки (у тебя он уже работал). Не вставляй слова `ПОРТ_КОДА` и `ПОРТ_ПОДКЛЮЧЕНИЯ`.

### Только часы (готовая APK, без Gradle)

Сборку не запускает. Ставит `dist\HealthSync-wear-0.3.4-debug.apk` **только на Galaxy Watch**.

1. На часах: параметры разработчика → **Беспроводная отладка** вкл.
2. На главном экране отладки скопируй **IP:порт подключения** (у тебя уже было `192.168.2.142:44643`).
3. Открой **Сопряжение по коду** — **другой** порт (не 44643) и 6 цифр. Экран не закрывай. Если часы уже `device` в `adb devices` — этот шаг не нужен.
4. Скрипт лежит **не** в `HealthWear`. Сначала ветка:

```bat
cd C:\IT\Cursor\health-connect-drive-export
git fetch origin
git checkout cursor/watch-vitals-export-8125
dir install-watch.cmd
.\install-watch.cmd -Apk dist\HealthSync-wear-0.3.4-debug.apk -Watch 192.168.2.142:44643
```

Если часы ещё не paired, добавь `-Pair 192.168.2.142:37111 -PairCode 847291` (цифры с экрана сопряжения, не слова).

Те же шаги вручную (так у тебя уже получилось `Success`):

```bat
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set APK=C:\IT\Cursor\HealthWear\HealthSync-wear-0.3.0-debug.apk
"%ADB%" connect 192.168.2.142:44643
"%ADB%" -s 192.168.2.142:44643 install -r "%APK%"
```

Не пиши слово `IP` в адресе. Не ставь этот APK на телефон.

Не задавай вручную:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

Отсюда Java 25.0.3 — Gradle сразу падает.

---

## Сначала: ты открыл не тот проект

Ошибка:

```
Error running 'app'
INSTALL_FAILED_MISSING_SHARED_LIBRARY
Package com.example.health_wear requires unavailable shared library com.google.android.wearable
List of apks: C:\IT\Cursor\HealthWear2\app\build\intermediates\apk\debug\app-debug.apk
```

(то же самое с `_Health-wear`) — это **шаблон Android Studio «Wear OS»**, не Health Sync.

В таком шаблоне конфигурация называется `app`, но это **приложение для часов**. Телефон не умеет `com.google.android.wearable` → установка всегда падает.

**Закрой `Healt-wear` / `HealthWear2` / `_Health-wear`.** Это шаблон Studio, его чинить не нужно.

Если Studio пишет **Unavailable on device SM-L705F** и **No target device found** — часы как раз подключены (SM-L705F = Galaxy Watch Ultra). Шаблон Studio часто имеет слишком высокий minSdk или Studio не видит признаки Wear по Wi‑Fi ADB. Не жми Run в этом окне.

Модуль часов есть **только в ветке PR**, в `main` его ещё нет. В PowerShell:

```bat
cd C:\IT\Cursor\health-connect-drive-export
git fetch origin
git checkout cursor/watch-vitals-export-8125
```

Если папки репозитория нет:

```bat
cd C:\IT\Cursor
git clone -b cursor/watch-vitals-export-8125 https://github.com/sdvirk-tech/health-connect-drive-export.git
```

Потом **File → Open** → `C:\IT\Cursor\health-connect-drive-export`  
(**не** `...\health-connect-drive-export\wear`).

Слева три модуля: `app`, `wear`, `shared`.

- Если виден только `app` и имя проекта `Healt-wear` / `HealthWear2` — открыт шаблон Studio.
- Если корень дерева — папка `wear` и Gradle пишет `Could not create parent directory for lock file C:\ProgramData\...` — открыт **подкаталог** `wear`. Закрой проект. Открой родителя. Затем **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle user home** поставь `C:\Users\%USERNAME%\.gradle` (каталог, куда Studio может писать, не `C:\ProgramData\...`). **Try Again**.

Сборка из корня репозитория: `.\build-install.cmd` (он выставляет JDK 17, вызывает `.\gradlew.bat` и `adb.exe` из SDK).

Если `dir gradlew.bat` пишет, что файла нет — ты не в корне репозитория (`C:\IT\Cursor\health-connect-drive-export`).

`adb.exe` у тебя уже есть:

`C:\Users\Di\AppData\Local\Android\Sdk\platform-tools\adb.exe`

Не копируй `$adb = "СЮДА\platform-tools\adb.exe"`. Не копируй `-s 192.168.2.142:36169` — этот порт уже истёк.

Строка `rsor\health-connect-drive-export` — обрывок `cd`, её можно игнорировать.

На часах появится **Health Sync Watch** (`ru.sdvirk.healthsync.wear`), не `com.example.health_wear`.

| Где жмёшь Run | Куда ставится | Package |
|---|---|---|
| `app` | телефон | `ru.sdvirk.healthsync` |
| `wear` | Galaxy Watch | `ru.sdvirk.healthsync.wear` |

Если в логе package `com.example.health_wear` — снова открыт шаблон, не этот репозиторий.

---

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
2. **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** → **17** (Download JDK… Temurin 17). Не **jbr** / Embedded с версией 25 — иначе Sync тоже упадёт с `25.0.3`.
3. Дождись **Gradle Sync**.
4. Слева в Project должны быть модули `app`, `wear`, `shared`.

Командная строка (**PowerShell**, из корня):

```bat
.\build-install.cmd
```

Или только сборка, если JDK 17 уже в `JAVA_HOME` (не Studio JBR 25):

```powershell
.\gradlew.bat :app:assembleDebug :wear:assembleDebug
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
