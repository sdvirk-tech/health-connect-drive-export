# Apps Script: принять zip и положить в папку Drive

**Это необязательный fallback.** Основной путь приложения — Google Sign-In + Drive API.

Web App больше не подходит как единственный канал: GET на `/exec` ещё отвечает `{"ok":true,"service":"health-sync"}`, но POST после 302 на `script.googleusercontent.com/macros/echo` даёт **HTTP 405** (воспроизводится с `curl` и с телефона). Даже если клиент повторяет POST на Location (как `curl --location-trusted`), echo-эндпоинт метод не принимает.

Используй этот скрипт только если OAuth Drive API на телефоне недоступен.

---

Приложение шлёт **JSON** (не multipart):

```json
{
  "fileName": "health_export_2026-09-06_2210.zip",
  "mimeType": "application/zip",
  "fileBase64": "<base64 zip>",
  "secret": "опционально"
}
```

Multipart в `doPost` у Apps Script почти не парсится. JSON читается из `e.postData.contents`.

Лимит Web App — десятки мегабайт на запрос; недельный zip с витальными обычно намного меньше.

## Развёртывание

1. Открой [script.google.com](https://script.google.com) → **Новый проект**.
2. Вставь код ниже, подставь `FOLDER_ID` своей папки Drive (и при желании `SECRET`). Личный id в git не коммить.
3. **Deploy → New deployment → Web app**
   - Execute as: **Me**
   - Who has access: **Anyone** (иначе телефон получит HTML-страницу логина вместо `ok`)
4. Скопируй URL вида `https://script.google.com/macros/s/…/exec` в поле **Upload URL** в приложении.
5. Проверка: открой URL в браузере — должен вернуться `{"ok":true,"service":"health-sync"}`.

Целевая папка: укажи ID своей папки Drive (`YOUR_DRIVE_FOLDER_ID`).

Старые выгрузки **не затираются**: каждый файл с датой в имени (`health_export_YYYY-MM-DD_HHmm.zip`).

## Код

```javascript
var FOLDER_ID = 'YOUR_DRIVE_FOLDER_ID';
var SECRET = ''; // тот же текст, что в поле «Секрет» приложения, или пусто

function doGet() {
  return json_({ ok: true, service: 'health-sync' });
}

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return json_({ ok: false, error: 'empty body' });
    }

    var payload = JSON.parse(e.postData.contents);
    if (SECRET && payload.secret !== SECRET) {
      return json_({ ok: false, error: 'forbidden' });
    }

    var b64 = payload.fileBase64 || payload.file || '';
    if (!b64) {
      return json_({ ok: false, error: 'fileBase64 is required' });
    }

    var bytes = Utilities.base64Decode(b64);
    var name = payload.fileName || payload.filename || ('health_export_' + Date.now() + '.zip');
    var mime = payload.mimeType || 'application/zip';
    var blob = Utilities.newBlob(bytes, mime, name);
    var folder = DriveApp.getFolderById(FOLDER_ID);
    var file = folder.createFile(blob);

    return json_({
      ok: true,
      fileId: file.getId(),
      name: file.getName(),
      url: file.getUrl()
    });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function json_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
```

## Проверка с компьютера

```bash
python3 - <<'PY'
import json, base64, urllib.request
url = "https://script.google.com/macros/s/XXXX/exec"
payload = {
    "fileName": "health_export_probe.zip",
    "mimeType": "application/zip",
    "fileBase64": base64.b64encode(b"PK\x03\x04probe").decode("ascii"),
    "secret": "",
}
req = urllib.request.Request(
    url,
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST",
)
print(urllib.request.urlopen(req).read().decode())
PY
```

`curl -L` **не подойдёт**: 302 от Google превращает POST в GET, и `doPost` не вызывается. Даже повтор POST на Location часто заканчивается **405** на `/macros/echo` — поэтому приложение по умолчанию больше не ходит в Apps Script.
