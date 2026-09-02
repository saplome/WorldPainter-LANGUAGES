# Инкрементальный апдейтер (wp-updater)

Обновление установленного приложения без полной переустановки: клиент сравнивает SHA-256 локальных файлов с манифестом на хостинге и докачивает только изменившиеся файлы.

## Состав

- `WPUpdater.java` — автономный апдейтер (без зависимостей, Java 17). При сборке `build-windows-installer.ps1` компилируется в `app/wp-updater-<версия>.jar`, а в app-image через `jpackage --add-launcher` добавляется `WorldPainter-Update.exe` рядом с главным exe. Имя jar версионное намеренно: Windows держит jar работающего апдейтера открытым, поэтому заменить его на месте нельзя — новая версия приходит под новым именем, а `app/WorldPainter-Update.cfg` указывает лаунчер на неё.
- `../generate_update_manifest.py` — кроссплатформенный генератор манифеста (эквивалент встроен в PS1 как `-GenerateUpdateManifest`).

## Формат манифеста (`update-manifest.txt`, UTF-8, поля через TAB)

```
format=1
version=<версия>
file=<sha256>\t<размер в байтах>\t<относительный/путь>\t<абсолютный URL>
delete=<относительный/путь>
```

## Как это работает на клиенте

1. `WorldPainter-Update.exe` — отдельный процесс (на Windows JAR'ы запущенного приложения заблокированы, поэтому обновлять их изнутри приложения нельзя). Он читает `updater.properties` рядом с jar: `manifestUrl`, `root` (по умолчанию каталог jar, т.е. `app/`), `launch`.
2. Скачивает манифест (редиректы GitHub поддерживаются), для каждого файла сравнивает размер и SHA-256; несовпавшие докачивает во временный файл `*.wpupdate-tmp`, проверяет размер и хеш и только затем атомарно заменяет цель — прерванное обновление не оставляет полузаписанных файлов.
3. Выполняет `delete=`-записи и запускает `launch` (главный exe).

CLI: `--manifest <url>`, `--root <dir>`, `--launch <path>`, `--check-only`, `--no-launch`, `--wait-pid <pid>`, `--elevated`.
`--wait-pid` ждёт завершения указанного процесса перед заменой первого файла — WorldPainter передаёт туда свой PID, чтобы его jar'ы успели освободиться. `--elevated` помечает уже повышенный запуск и отключает повторный запрос прав: если каталог установки недоступен для записи, апдейтер сам перезапускает себя через UAC. Отказ от UAC приходит как `ERROR_CANCELLED` (1223) от повышенной копии; апдейтер в этом случае возвращает `2`, снова запускает приложение и ничего не меняет.
Коды выхода: `0` — актуально/обновлено, `1` — есть обновления (только с `--check-only`), `2` — ошибка.
Манифест описывает весь каталог `app/`, включая `updater.properties` и сам `wp-updater-<версия>.jar`: иначе после обновления клиент остался бы со старым апдейтером и старым URL манифеста. Jar, из которого запущен текущий процесс, пропускается (Windows не даёт заменить открытый файл), а устаревшие `wp-updater-*.jar` предыдущих версий удаляются.
Пути в манифесте проверяются: абсолютные и выходящие за пределы установки (`..`) отклоняются.

## Релизный цикл

1. Собрать app-image: `build-windows-installer.ps1 -BuildPortable ...` (апдейтер стейджится автоматически).
2. Сгенерировать манифест:
   - PS1: добавить `-GenerateUpdateManifest -UpdateBaseUrl <база>` — получится `release/update-manifest.txt` (добавляется к draft-ассетам);
   - либо python: `python3 tools/generate_update_manifest.py --root "release/app-image/WorldPainter Languages/app" --version <версия> --base-url <база> --output release/update-manifest.txt`.

   Корень — именно каталог `app` внутри app-image, а не `release/staging/app`: `.jpackage.xml` и оба `*.cfg`
   создаёт сам jpackage, в staging их нет. Именно `*.cfg` содержат `app.classpath` с именами jar-файлов,
   поэтому манифест без них оставил бы клиента со classpath, ссылающимся на удалённые jar.
3. Выложить содержимое этого же каталога `app` целиком на хостинг под `<база>/...` (сборка с
   `-GenerateUpdateManifest` уже готовит такую копию в `release/cdn/app`), а `update-manifest.txt` — на
   стабильный URL, зашитый в `updater.properties` (`-UpdateManifestUrl`).

## Хостинг (рекомендация)

- Файлы приложения: отдельный публичный git-репозиторий-CDN (например `saplome/WorldPainter-LANGUAGES-cdn`), раздача через `https://raw.githubusercontent.com/<owner>/<repo>/main/app/...` — поддерживает подкаталоги (`lib/...`), лимит 100 МБ на файл.
- Манифест: ассетом GitHub Release по стабильному URL `https://github.com/saplome/WorldPainter-LANGUAGES/releases/latest/download/update-manifest.txt` (значение по умолчанию `-UpdateManifestUrl`) либо тоже в CDN-репозитории.
- ВАЖНО: ассеты GitHub Release плоские (без подкаталогов), поэтому сами файлы приложения в ассеты релиза класть нельзя — только манифест.

## Локальная проверка (без публикации)

`test-updater-local.ps1` прогоняет апдейтер целиком на локальном HTTP-сервере, не трогая реальную установку:

```powershell
cd <корень исходников>
.\tools\windows-packaging\build-windows-installer.ps1 -BuildPortable
.\tools\updater\test-updater-local.ps1
```

Скрипт копирует `release\staging\app` во временный каталог (`%TEMP%\wp-updater-local-test`) как «CDN» и как «установку», поднимает `http://localhost:8000/`, генерирует манифест и проверяет семь сценариев: актуальное состояние (exit 0), обнаружение обновлений (exit 1), докачку только изменённых файлов + новый файл + `delete=` без остатков `*.wpupdate-tmp`, повторный запуск как no-op, отказ при испорченной загрузке (exit 2, локальные файлы целы), отсутствующий манифест (exit 2) и попытку заменить сам апдейтер (exit 0, jar не тронут).

Параметры: `-ProjectRoot <путь>`, `-Port <порт>`, `-WorkDir <путь>`, `-KeepWorkDir`. Требуется JDK 17 (`java` в PATH). Если порт не поднимается без прав администратора: `netsh http add urlacl url=http://localhost:8000/ user=%USERNAME%` либо `-Port` другой.
