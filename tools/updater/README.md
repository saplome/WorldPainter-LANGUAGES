# Инкрементальный апдейтер (wp-updater)

Обновление установленного приложения без полной переустановки: клиент сравнивает SHA-256 локальных файлов с манифестом на хостинге и докачивает только изменившиеся файлы.

## Состав

- `WPUpdater.java` — автономный апдейтер (без зависимостей, Java 17). При сборке `build-windows-installer.ps1` компилируется в `app/wp-updater.jar`, а в app-image через `jpackage --add-launcher` добавляется `WorldPainter-Update.exe` рядом с главным exe.
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

CLI: `--manifest <url>`, `--root <dir>`, `--launch <path>`, `--check-only`, `--no-launch`.
Коды выхода: `0` — актуально/обновлено, `1` — есть обновления (только с `--check-only`), `2` — ошибка.
Сам `wp-updater.jar` и `updater.properties` в манифест не входят (обновляются установщиком).
Пути в манифесте проверяются: абсолютные и выходящие за пределы установки (`..`) отклоняются.

## Релизный цикл

1. Собрать app-image: `build-windows-installer.ps1 -BuildPortable ...` (апдейтер стейджится автоматически).
2. Сгенерировать манифест:
   - PS1: добавить `-GenerateUpdateManifest -UpdateBaseUrl <база>` — получится `release/update-manifest.txt` (добавляется к draft-ассетам);
   - либо python: `python3 tools/generate_update_manifest.py --root release/staging/app --version <версия> --base-url <база> --output release/update-manifest.txt`.
3. Выложить содержимое `release/staging/app` (без `wp-updater.jar` и `updater.properties`) на хостинг под `<база>/...`, а `update-manifest.txt` — на стабильный URL, зашитый в `updater.properties` (`-UpdateManifestUrl`).

## Хостинг (рекомендация)

- Файлы приложения: отдельный публичный git-репозиторий-CDN (например `saplome/WorldPainter-LANGUAGES-cdn`), раздача через `https://raw.githubusercontent.com/<owner>/<repo>/main/app/...` — поддерживает подкаталоги (`lib/...`), лимит 100 МБ на файл.
- Манифест: ассетом GitHub Release по стабильному URL `https://github.com/saplome/WorldPainter-LANGUAGES/releases/latest/download/update-manifest.txt` (значение по умолчанию `-UpdateManifestUrl`) либо тоже в CDN-репозитории.
- ВАЖНО: ассеты GitHub Release плоские (без подкаталогов), поэтому сами файлы приложения в ассеты релиза класть нельзя — только манифест.
