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

**WorldPainter RU 2.27.0-r1** основан на **WorldPainter 2.27.0** и сохраняет совместимость с оригинальными `.world` файлами.  
Текущий релиз относится к **Phase 1**: русификация без изменения архитектуры, UI-фреймворка, бизнес-логики и форматов файлов.

## Скачать

Рекомендуемый вариант для обычного пользователя — Windows installer из GitHub Releases:

| Артефакт | Для чего нужен |
| --- | --- |
| `WorldPainter RU 2.27.0-r1 Setup.exe` | обычная установка на Windows, ярлыки, меню «Пуск» |
| `WorldPainter RU Portable.zip` | переносимая сборка для ручного запуска через Java |
| `WorldPainter-RU-src.zip` | архив исходного кода релиза |

> [!NOTE]
> Portable-сборка не заменяет полноценный installer. Она удобна для тестов и ручного запуска, но для рядового пользователя лучше использовать `Setup.exe`.

## Что готово

- Русская локализация основных окон, меню, панелей и диалогов.
- Переведены многие предупреждения, ошибки, progress-сообщения и `JOptionPane`.
- Подготовлена сборка Windows installer через `jpackage` и WiX.
- Добавлены release-документы: changelog, roadmap, build guide и packaging notes.
- Сохранена лицензия **GNU GPLv3**, совместимая с оригинальным WorldPainter.

## Скриншоты

Скриншоты будут добавлены перед публичным релизом.

| Главное окно | Настройки | Экспорт |
| --- | --- | --- |
| _скриншот будет добавлен_ | _скриншот будет добавлен_ | _скриншот будет добавлен_ |

## Установка

1. Откройте страницу **Releases**.
2. Скачайте `WorldPainter RU 2.27.0-r1 Setup.exe`.
3. Запустите установщик.
4. На шаге выбора ярлыков отметьте нужные пункты.
5. После установки можно сразу запустить WorldPainter RU.

Windows Installer использует техническую версию `2.27.1`, потому что MSI `ProductVersion` не поддерживает суффиксы вроде `-r1`. Публичная версия релиза остаётся `2.27.0-r1`.

## Portable

Portable-архив содержит runtime layout приложения. Для ручного запуска из распакованной папки:

```powershell
java -jar WPGUI-2.27.0.jar
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

Подробнее:

- [BUILDING_RU.md](BUILDING_RU.md)
- [PACKAGING_NOTES.md](PACKAGING_NOTES.md)
- [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md)

## Структура

```text
WPCore/               ядро WorldPainter
WPGUI/                графический интерфейс
WPDynmapPreviewer/    dynmap previewer
assets/               иконки WorldPainter RU
scripts/              сборка installer
```

## Ограничения

- Это неофициальный fork.
- Modern UI, FlatLaf, dark/light themes и новая архитектура локализации пока не входят в релиз.
- Названия платформ Minecraft, версии Minecraft, форматы и расширения файлов намеренно не переводятся.
- Некоторые редкие аварийные сценарии требуют ручной проверки на реальной Windows-системе.

## Roadmap

- **Phase 1:** полная русификация.
- **Phase 2:** новая архитектура локализации.
- **Phase 3:** Modern UI, FlatLaf, dark/light themes, Windows 11 style.
- **Phase 4:** новые возможности.

Подробнее: [ROADMAP.md](ROADMAP.md)

## Благодарности

Спасибо **Pepijn Schmitz** и участникам оригинального WorldPainter за огромную работу над проектом.  
WorldPainter RU существует как русскоязычная адаптация и не заменяет официальный WorldPainter.

## Лицензия

WorldPainter RU является fork проекта WorldPainter и распространяется на условиях **GNU General Public License v3**.

Лицензия GPLv3 обязательна для совместимости с оригинальным проектом. Условия лицензии не изменялись. Полный текст находится в файле [LICENSE](LICENSE), а сведения об оригинальном проекте и авторстве вынесены в [NOTICE.md](NOTICE.md).
