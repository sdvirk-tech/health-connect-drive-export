# Health Connect → Google Drive Export

Android-приложение: читает данные из **Health Connect** (куда пишет Samsung Health) и раз в сутки кладёт zip в твою папку Drive.

Репозиторий: https://github.com/sdvirk-tech/health-connect-drive-export  
Package / `applicationId`: `ru.sdvirk.healthsync`

## Что делает

1. Запрашивает разрешения Health Connect (пульс, покой, HRV, сон, SpO₂, давление, вес, шаги, дистанция, тренировки; плюс чтение в фоне и истории).
2. Читает записи за N дней (по умолчанию 7).
3. Пишет JSON + summary в zip (`health_export_YYYY-MM-DD_HHmm.zip`). Старые файлы **не затираются**.
4. После **входа в Google** загружает zip в Drive через **Drive API** (OAuth). Целевая папка задана в коде.

**Не** логинится в Samsung Health и **не** парсит UI.

Apps Script Web App **больше не основной путь**: GET на `/exec` ещё отвечает `{"ok":true,"service":"health-sync"}`, но POST после 302 на `script.googleusercontent.com/macros/echo` даёт **HTTP 405** (и с curl, и с телефона). В UI URL скрипта спрятан в «Дополнительно» как необязательный fallback.

## Требования

- Android 9+ (API 28), лучше 14+ с системным Health Connect.
- JDK 17+ для сборки.
- На телефоне установлены Samsung Health и Health Connect.
- Google-аккаунт с правом записи в папку Drive (обычно тот же, где лежит папка).

## Сборка APK

### Android Studio

1. Открой корень репозитория в Android Studio (2025.1+; нужен AGP 8.9).
2. Дождись Sync Gradle (wrapper уже в репозитории: Gradle 8.11.1, compileSdk 36).
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Командная строка

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Установка на телефон по USB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## OAuth: Google Cloud Console

Плагин Firebase / `google-services.json` **не обязателен**. Нужен проект в [Google Cloud Console](https://console.cloud.google.com/) и два OAuth client в **одном** проекте.

### 1. Включить Drive API

APIs & Services → Library → **Google Drive API** → Enable.

### 2. Экран согласия OAuth

APIs & Services → **OAuth consent screen**:

- User type: **External** (для личного Gmail).
- App name / email — любые.
- Scopes → Add: `https://www.googleapis.com/auth/drive`  
  (нужен именно `drive`, а не только `drive.file`: папка уже существует и не создавалась этим приложением).
- **Test users** → добавь Gmail, которым будешь входить на телефоне (пока приложение не прошло verification).

### 3. Android OAuth client (обязательно)

APIs & Services → Credentials → **Create credentials → OAuth client ID → Android**:

- Package name: `ru.sdvirk.healthsync` (не меняй `applicationId`).
- SHA-1 debug-ключа:

```bash
./gradlew :app:signingReport
```

В отчёте блок `Variant: debug` → `SHA1: …`. Для своего release-keystore добавь второй Android client с его SHA-1.

Без этого на телефоне будет `DEVELOPER_ERROR (10)`.

### 4. Web OAuth client (для кнопки Credential Manager)

Credentials → **Create credentials → OAuth client ID → Web application**.

Redirect URI можно не задавать. Скопируй **Client ID** (`….apps.googleusercontent.com`) в:

`app/src/main/res/values/oauth.xml` → `default_web_client_id`

Если строка пустая, «Войти в Google» всё равно работает через AuthorizationClient (нужен только Android client). Web client даёт экран Credential Manager / Sign in with Google.

Секрет Web client в приложение **не клади**.

### 5. Если всё же используешь Firebase / google-services.json

1. Создай Android-приложение в Firebase с package `ru.sdvirk.healthsync`.
2. Скачай `google-services.json` в каталог `app/` (плагин `google-services` в этом проекте не подключён — файл сам по себе ничего не подставляет).
3. Из JSON возьми `client_id` с `"client_type": 3` (Web) и вставь в `oauth.xml` как `default_web_client_id`.
4. SHA-1 debug/release всё равно должен быть в том же GCP-проекте (Firebase Console → Project settings → Your apps → SHA certificate fingerprints).

## Как пользоваться

1. Собери debug APK, поставь на телефон.
2. **«Войти в Google»** → выбери аккаунт с доступом к папке Drive → подтверди доступ к Диску.
3. **«Запросить разрешения Health Connect»**.
4. **«Выгрузить сейчас»** → в папке появится `health_export_YYYY-MM-DD_HHmm.zip`.
5. **«Автораз в сутки»** — WorkManager, нужен интернет и уже выполненный вход в Google.

Без входа в Google (и без fallback URL) zip остаётся в кэше приложения (`…/cache/health_export_….zip`).

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

## Drive-папка

Укажи ID своей папки Drive (`YOUR_DRIVE_FOLDER_ID`), не коммить личный id:

- предпочтительно `local.properties`: `drive.folder.id=YOUR_DRIVE_FOLDER_ID` (файл уже в `.gitignore`)
- либо локальный `app/src/main/res/values/drive_folder.xml` (скопируй `drive_folder.example.xml` и не оставляй оба файла в `res/values/` — одинаковое имя ресурса)


## Fallback: Apps Script

Инструкция: [`docs/apps-script-upload.md`](docs/apps-script-upload.md). Поле URL в приложении скрыто под «Дополнительно» и **не обязательно**. Основной путь — вход в Google.

## Статус

- [x] Gradle wrapper + манифест + разрешения HC (включая фон / историю)
- [x] Reader / JSON zip / WorkManager / UI
- [x] Прямая загрузка в Drive: Google Sign-In (Credential Manager) + Drive API
- [ ] SQLite-формат как в текущем пустом `health_connect_export.db` (по желанию)
