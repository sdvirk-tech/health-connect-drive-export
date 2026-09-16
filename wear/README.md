# НЕ открывай эту папку в Android Studio

Путь на экране `C:\IT\Cursor\health-connect-drive-export\wear` — **неправильный**.

**File → Open** → на уровень выше:

`C:\IT\Cursor\health-connect-drive-export`

Слева должны быть модули `app`, `wear`, `shared`. Если корневая папка в дереве называется только `wear` — закрой окно и открой родителя.

---

Это часовой модуль. APK ставится **только на Galaxy Watch** (Wear OS 3+).

На телефон: `INSTALL_FAILED_MISSING_SHARED_LIBRARY` / `com.google.android.wearable`.

Телефон — модуль `:app` в корне того же репозитория.

Сборка: [`docs/install.md`](../docs/install.md). Из корня репозитория (PowerShell): `.\build-install.ps1`.
