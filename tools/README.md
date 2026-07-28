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

- в staging автоматически добавляется `app/wp-updater.jar`, а в app-image — launcher `WorldPainter-Update.exe` (требуется `javac` в PATH);
- `-GenerateUpdateManifest -UpdateBaseUrl <база>` — сгенерировать `release/update-manifest.txt` для апдейтера (добавляется к draft-ассетам);
- `-UpdateManifestUrl <url>` — URL манифеста, зашиваемый в `app/updater.properties`.

Подробности: `tools/updater/README.md`.

## Чистые релизные архивы

`tools/release/pack-release-archives.ps1` собирает архивы для GitHub и отсекает лишнее: `target/`, `release/`, `.git/`, IDE-файлы, `*.class`, `*.jar`, `*.exe`, `*.zip`, логи и временные файлы.

```powershell
.\tools\release\pack-release-archives.ps1 -VerifyOnly
.\tools\release\pack-release-archives.ps1
```

Проверяется: нет выхлопа сборки, нет ссылок на генератор деревьев, версия совпадает в README, `installer.iss` и `build-windows-installer.ps1`, есть `docs/RELEASE_NOTES_<версия>.md`. При любой проблеме скрипт останавливается и ничего не собирает.

На выходе в `release/github/`: `...-github-ready.zip` (всё для коммита), `WorldPainter-Languages-2.27.0-L2.0.1.zip` (исходники без `tools/`), `...-release-tools.zip` (только `tools/`).
