# Сборка WorldPainter RU

Этот документ описывает сборку WorldPainter RU 2.27.0-r1 из исходников.

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

### `java -jar WPGUI-2.27.0.jar` не запускается

Это ожидаемо. `WPGUI-2.27.0.jar` не является самодостаточным executable jar и требует внешнего classpath.

### Не найден JDK 17 toolchain

Убедитесь, что установлен JDK 17 и Maven видит правильный `JAVA_HOME`.

## Релизная сборка

Перед публикацией:

```bash
mvn clean package
mvn -pl WPGUI exec:exec
```

После ручной проверки GUI можно готовить portable/installer layout.

Сборка installer.exe на Windows:

```powershell
.\scripts\build-windows-installer.ps1 -BuildInstaller
```
