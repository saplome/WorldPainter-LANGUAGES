# Сборка WorldPainter Languages

Этот документ описывает сборку WorldPainter Languages 3.0.0 из исходников.

## Требования

- JDK 17
- Maven
- Git
- доступ к Maven Central
- доступ к Maven-репозиторию оригинального WorldPainter: `https://www.worldpainter.net/maven-repo/`

На Windows для сборки installer.exe дополнительно понадобятся:

- JDK с `jpackage`
- WiX Toolset с доступными `candle.exe` и `light.exe`

## Проверка инструментов

```bash
java -version
mvn -version
```

Для упаковки:

```bash
jpackage --version
candle.exe -?
light.exe -?
```

## Полная сборка

Из корня проекта:

```bash
mvn clean package
```

## Запуск GUI из исходников

```bash
mvn -pl WPGUI exec:exec
```

Этот запуск использует Maven classpath и не требует готового installer/portable layout.

## Частые проблемы

### Maven не может скачать зависимости

Проверьте доступ к сети и к репозиторию:

```text
https://www.worldpainter.net/maven-repo/
```

Некоторые зависимости WorldPainter не находятся в Maven Central.

### `java -jar WPGUI-2.27.1.jar` не запускается

Это ожидаемо. `WPGUI-2.27.1.jar` не является самодостаточным executable jar и требует внешнего classpath.

### Не найден JDK 17 toolchain

Убедитесь, что установлен JDK 17 и Maven видит правильный `JAVA_HOME`.

## Разделение исходников и release tools

`WorldPainter-Languages-3.0.0.zip` содержит чистые исходники без сценариев упаковки. `WorldPainter-Languages-3.0.0-release-tools.zip` содержит каталог `tools/`; распакуйте его в корень исходников перед сборкой Portable или установщика. GitHub-ready архив уже содержит `tools/` и предназначен для прямого коммита в репозиторий.

## Релизная сборка

Перед публикацией:

```bash
mvn clean package
mvn -pl WPGUI exec:exec
```

После ручной проверки GUI можно готовить portable/installer layout.

Сборка installer.exe на Windows:

```powershell
# Классический установщик (WiX/MSI)
.\tools\windows-packaging\build-windows-installer.ps1 -BuildInstaller

# Portable-zip + установщик Inno Setup (нужен Inno Setup 6)
.\tools\windows-packaging\build-windows-installer.ps1 -BuildPortable -BuildInnoInstaller
```


## Предпросмотр GitHub Release как черновика

1. Установите GitHub CLI: `winget install --id GitHub.cli`.
2. Выполните `gh auth login` и выберите GitHub.com → HTTPS → вход через браузер.
3. Соберите релиз и передайте `-CreateDraftRelease -OpenDraftRelease`.

```powershell
.\tools\windows-packaging\build-windows-installer.ps1 `
  -BuildPortable -BuildInnoInstaller `
  -CreateDraftRelease -OpenDraftRelease
```

По умолчанию используется тег `L3.0.0`, заголовок `WorldPainter Languages 3.0.0` и файл `docs/RELEASE_NOTES_3.0.0.md`. Номер версии — обычный (`3.0.0`), буква `L` остаётся только в теге: сборки до `2.27.0-L2.0.1` искали в теге `L` и число за ней. Повторный запуск обновляет существующий черновик и заменяет assets. Если релиз с этим тегом уже опубликован, скрипт останавливается и ничего не перезаписывает.
