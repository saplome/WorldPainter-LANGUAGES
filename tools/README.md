# Windows release tools

Каталог содержит developer-only сценарии и ресурсы сборки Portable, Inno Setup и WiX/MSI. Он не входит в runtime JAR или установленное приложение.

## Основные переключатели

- `-SkipMaven` — не запускать `mvn clean package`, использовать уже собранные JAR;
- `-BuildPortable` — создать app-image и Portable ZIP;
- `-BuildInnoInstaller` — создать фирменный Setup.exe через Inno Setup 6;
- `-BuildInstaller` — создать классический jpackage/WiX installer;
- `-CreateDraftRelease` — создать или обновить **черновик** GitHub Release;
- `-OpenDraftRelease` — открыть черновик в браузере после загрузки;
- `-AdditionalDraftAsset <путь>` — добавить к черновику исходники или другой файл;
- `-ReleaseTag`, `-ReleaseTitle`, `-ReleaseNotesFile` — переопределить данные черновика.

## Сборка и загрузка черновика

```powershell
gh auth login
mvn clean install -DskipTests

powershell -ExecutionPolicy Bypass -File .\tools\windows-packaging\build-windows-installer.ps1 `
  -SkipMaven -BuildPortable -BuildInnoInstaller `
  -CreateDraftRelease -OpenDraftRelease
```

Скрипт загружает найденные Setup.exe, Portable ZIP и дополнительные assets. Повторный запуск заменяет одноимённые файлы. Публикация никогда не выполняется автоматически: после проверки страницы GitHub нажмите **Publish release** вручную.

## Инкрементальные обновления

- в staging автоматически добавляется `app/wp-updater-<версия>.jar`, а в app-image — launcher `WorldPainter-Update.exe` (требуется `javac` в PATH);
- `-GenerateUpdateManifest -UpdateBaseUrl <база>` — сгенерировать `release/update-manifest.txt` для апдейтера (добавляется к draft-ассетам);
- `-UpdateManifestUrl <url>` — URL манифеста, зашиваемый в `app/updater.properties`.

Прогнать весь канал обновлений локально, без публикации: `.\tools\updater\test-updater-local.ps1` (поднимает HTTP-сервер на localhost и проверяет семь сценариев).

Подробности: `tools/updater/README.md`.

## Чистые релизные архивы

`tools/release/pack-release-archives.ps1` собирает архивы исходников и отсекает лишнее: `target/`, корневой `release/`, `.git/`, IDE-файлы, `*.class`, `*.jar`, `*.exe`, `*.zip`, логи и временные файлы. Каталог `tools/release` — это исходники, он в архивы попадает.

```powershell
.\tools\release\pack-release-archives.ps1 -VerifyOnly
.\tools\release\pack-release-archives.ps1
```

Проверяется: нет выхлопа сборки, ни в одном файле не осталось абсолютного пути к домашнему каталогу сборочной машины, версия совпадает в `README.md`, `installer.iss` и `build-windows-installer.ps1`, есть `docs/RELEASE_NOTES_<версия>.md`. При любой проблеме скрипт останавливается и ничего не собирает.

На выходе в `release/github/`: `...-github-ready.zip` (всё), `WorldPainter-Languages-<версия>.zip` (исходники без `tools/`), `...-release-tools.zip` (только `tools/`).

## Подготовка загрузки на GitHub

`tools/release/prepare-github-upload.ps1` складывает в `release/upload/` всё, что уходит на GitHub: ассеты релиза (`release-assets/`), скрипты публикации (`publish-step1-cdn.sh`, `verify-cdn.sh`, `publish-step2-release.sh`) и инструкцию `UPLOAD_RU.md`.

```powershell
.\tools\release\prepare-github-upload.ps1
```

Перед сборкой набора скрипт сверяет манифест обновления с содержимым `release/cdn/app` — размер, SHA-256 и URL каждого файла — и останавливается, если что-то расходится. Запускать после каждой пересборки архивов. Порядок публикации и устройство канала обновлений: `docs/UPDATE_CHANNEL_RU.md`.
