# Сборка WorldPainter Languages

Этот документ описывает сборку WorldPainter Languages 3.0.1 из исходников.

## Требования

- JDK 17
- Maven
- Git
- доступ к Maven Central
- доступ к Maven-репозиторию оригинального WorldPainter: `https://www.worldpainter.net/maven-repo/`

На Windows для сборки installer.exe дополнительно понадобятся:

- JDK с `jpackage`
- WiX Toolset с доступными `candle.exe` и `light.exe`

JDK 17 должен стоять в `PATH` первым. `jpackage`, `javac` и `jar` вызываются по имени, а среду, которую
`jpackage` вкладывает внутрь сборки, он берёт из своего собственного JDK — то есть из того, который нашёлся
в `PATH`. Одного `JAVA_HOME` для этого недостаточно: поиск по имени его не смотрит, и установленный рядом
JDK другой версии молча попадёт в релиз. Сценарий сборки сверяет мажорную версию всех трёх инструментов и
на любой, кроме 17, останавливается, ничего не собрав.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
```

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

## Сценарии упаковки

Сценарии упаковки лежат в репозитории — `tools\windows-packaging\` и `tools\release\`, — поэтому `Source code (zip)` релиза (GitHub собирает его из тега) уже содержит всё для сборки Portable и установщика. Отдельных архивов с `tools/` в релизе больше нет.

`tools\release\pack-release-archives.ps1` остался для локальных снимков исходников: он собирает `release\github\*.zip` без `target/`, `.git/` и результатов сборки. В релиз эти архивы не попадают.

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

По умолчанию используется тег `L3.0.1`, заголовок `WorldPainter Languages 3.0.1` и файл `docs/RELEASE_NOTES_3.0.1.md`. Номер версии — обычный (`3.0.1`), буква `L` остаётся только в теге: сборки до `2.27.0-L2.0.1` искали в теге `L` и число за ней. Повторный запуск обновляет существующий черновик и заменяет assets. Если релиз с этим тегом уже опубликован, скрипт останавливается и ничего не перезаписывает.

---

**WorldPainter Languages** — неофициальный локализационный форк [WorldPainter](https://www.pepsoft.org/WorldPainter/), © 2011–2026 pepsoft.org, Нидерланды. Этот документ написан в 2026 году для WorldPainter Languages и не входит в оригинальный проект; © 2026 [saplome](https://github.com/saplome). Лицензия — [GNU General Public License v3](../LICENSE), та же, что и у приложения.
