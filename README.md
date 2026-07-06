<div align="center">
  <img src="assets/icon.png" alt="WorldPainter RU" width="132">

  <h1>WorldPainter RU</h1>

  <p>
    <strong>Неофициальный русскоязычный fork WorldPainter 2.27.0</strong><br>
    редактор миров Minecraft с русской локализацией интерфейса.
  </p>

  <p>
    <img alt="Release" src="https://img.shields.io/badge/release-2.27.0--r1-e53935?style=for-the-badge">
    <img alt="Base" src="https://img.shields.io/badge/base-WorldPainter%202.27.0-2d8cff?style=for-the-badge">
    <img alt="License" src="https://img.shields.io/badge/license-GPLv3-43a047?style=for-the-badge">
    <img alt="Platform" src="https://img.shields.io/badge/platform-Windows%20%7C%20Java-6d4aff?style=for-the-badge">
  </p>

  <p>
    <a href="#скачать">Скачать</a> •
    <a href="#что-готово">Что готово</a> •
    <a href="#сборка">Сборка</a> •
    <a href="#лицензия">Лицензия</a>
  </p>
</div>

---

> [!IMPORTANT]
> **This is an unofficial Russian localization fork.**  
> WorldPainter RU не является официальной версией WorldPainter и не связан с автором оригинального проекта.

Официальный WorldPainter:

- сайт: [worldpainter.net](https://www.worldpainter.net/)
- репозиторий: [Captain-Chaos/WorldPainter](https://github.com/Captain-Chaos/WorldPainter)

## О проекте

**WorldPainter RU 2.27.0-r2** основан на **WorldPainter 2.27.0** и сохраняет совместимость с оригинальными `.world` файлами.  
Текущий релиз относится к **Phase 1**: русификация без изменения архитектуры, UI-фреймворка, бизнес-логики и форматов файлов.

Для первого публичного релиза Phase 1 считается завершённой: русифицирована основная часть пользовательского интерфейса, включая меню, окна, диалоги, предупреждения, импорт, экспорт и настройки. Редкие системные, сторонние или аварийные сообщения ещё могут требовать ручной проверки.

## Скачать

Рекомендуемый вариант для обычного пользователя — Windows installer из GitHub Releases:

| Файл | Для чего нужен |
| --- | --- |
| `WorldPainter-RU-2.27.0-r2-Setup.exe` | обычная установка на Windows, ярлыки, меню «Пуск» |
| `WorldPainter-RU-2.27.0-r2-Portable.zip` | переносимая сборка для ручного запуска через Java |
| `WorldPainter-RU-2.27.0-r1-src.zip` | архив исходного кода релиза |

> [!NOTE]
> Portable-сборка не заменяет полноценный installer. Она удобна для тестов и ручного запуска, но для рядового пользователя лучше использовать `Setup.exe`.

## Что готово

- Русская локализация основной части интерфейса WorldPainter 2.27.0.
- Переведены меню, окна, панели, диалоги, предупреждения, ошибки, progress-сообщения и `JOptionPane`.
- Подготовлена сборка Windows installer через `jpackage` и WiX.
- Подготовлена базовая документация по установке и сборке из исходников.
- Сохранена лицензия **GNU GPLv3**, совместимая с оригинальным WorldPainter.

## Установка

1. Откройте страницу **Releases**.
2. Скачайте `WorldPainter-RU-2.27.0-r2-Setup.exe`.
3. Запустите установщик.
4. На шаге выбора ярлыков отметьте нужные пункты.
5. После установки можно сразу запустить WorldPainter RU.

Windows Installer использует техническую версию `2.27.2`, потому что MSI `ProductVersion` не поддерживает суффиксы вроде `-r2`. Публичная версия релиза остаётся `2.27.0-r2`.

## Portable

Portable-архив содержит runtime layout приложения. Для ручного запуска из распакованной папки:

```powershell
java -cp "WPGUI-2.27.0.jar;WPCore-2.27.0.jar;WPDynmapPreviewer-2.27.0.jar;lib/*" org.pepsoft.worldpainter.Main
```

Если нужен максимально простой запуск без командной строки, используйте installer.

## Сборка

Требования:

- JDK 17
- Maven
- WiX Toolset 3.x для сборки Windows installer
- доступ к Maven Central и зависимостям WorldPainter

Сборка проекта:

```bash
mvn clean package
```

Запуск GUI из исходников:

```bash
mvn -pl WPGUI exec:exec
```

Сборка Windows installer:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-windows-installer.ps1 -BuildInstaller
```

Подробнее: [BUILDING_RU.md](docs/BUILDING_RU.md)

## Структура

```text
WPCore/               ядро WorldPainter
WPGUI/                графический интерфейс
WPDynmapPreviewer/    dynmap previewer
assets/               иконки WorldPainter RU
docs/                 сборка из исходников
scripts/              сборка installer
```

## Ограничения

- Это неофициальный fork.
- Modern UI, FlatLaf, dark/light themes и новая архитектура локализации пока не входят в релиз.
- Названия платформ Minecraft, версии Minecraft, форматы и расширения файлов намеренно не переводятся.
- Некоторые редкие аварийные сценарии требуют ручной проверки на реальной Windows-системе.

## Благодарности

Спасибо **Pepijn Schmitz** и участникам оригинального WorldPainter за огромную работу над проектом.  
WorldPainter RU существует как русскоязычная адаптация и не заменяет официальный WorldPainter.

## Лицензия

WorldPainter RU является fork проекта WorldPainter и распространяется на условиях **GNU General Public License v3**.

Лицензия GPLv3 обязательна для совместимости с оригинальным проектом. Условия лицензии не изменялись. Полный текст находится в файле [LICENSE](LICENSE), а сведения об оригинальном проекте и авторстве вынесены в [NOTICE.md](NOTICE.md).
