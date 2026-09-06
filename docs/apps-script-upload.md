# Apps Script: принять zip и положить в папку Drive

1. https://script.google.com → новый проект.
2. Вставь код ниже, замени `FOLDER_ID`.
3. Deploy → New deployment → Web app → Execute as: Me → Anyone.
4. Скопируй URL в поле Upload URL в приложении.

```javascript
var FOLDER_ID = '10pwzTmlxVLAshc7_DfOuecSktde-HKjw';
var SECRET = ''; // опционально, тот же что в приложении

function doPost(e) {
  if (SECRET && e.parameter.secret !== SECRET) {
    return ContentService.createTextOutput('forbidden');
  }
  var blob = e.parameter.file
    ? Utilities.newBlob(Utilities.base64Decode(e.parameter.file), 'application/zip', 'health_connect_export.zip')
    : e.postData.contents; // зависит от клиента; для multipart лучше Drive API или parse вручную
  // Для multipart из OkHttp проще принять raw bytes через e.postData:
  var bytes = e.postData.contents; // может не сработать для multipart — тогда используй HTML form или Drive API
  var folder = DriveApp.getFolderById(FOLDER_ID);
  var files = folder.getFilesByName('health_connect_export.zip');
  while (files.hasNext()) files.next().setTrashed(true);
  folder.createFile(Utilities.newBlob(e.postData.getBytes ? e.postData.getBytes() : [], 'application/zip', 'health_connect_export.zip'));
  return ContentService.createTextOutput('ok');
}
```

На практике multipart в Apps Script капризный: для MVP часто делают простой backend на Cloud Function или принимают base64 в JSON body. В приложении можно добавить режим `{"fileBase64":"..."}` — скажи, допишу.
