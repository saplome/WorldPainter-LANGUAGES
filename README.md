<div align="center">
  <img src="assets/icon.png" alt="WorldPainter Languages" width="132">

  <h1>WorldPainter Languages</h1>

  <p>
    <strong>Неофициальный многоязычный форк WorldPainter</strong><br>
    Полностью локализованный WorldPainter с дополнительными темами, иконками, Windows-интеграцией и пользовательскими слоями генерации.
  </p>

  <p>
    <img alt="Release" src="https://img.shields.io/badge/release-3.0.0-e53935?style=for-the-badge">
    <img alt="Base" src="https://img.shields.io/badge/base-WorldPainter%202.27.1-2d8cff?style=for-the-badge">
    <img alt="Languages" src="https://img.shields.io/badge/languages-EN%20%2B%2010-6d4aff?style=for-the-badge">
    <img alt="License" src="https://img.shields.io/badge/license-GPLv3-43a047?style=for-the-badge">
  </p>

  <p>
    <a href="#о-проекте">О проекте</a> •
    <a href="#возможности">Возможности</a> •
    <a href="#скачать">Скачать</a> •
    <a href="#совместимость-миров">Совместимость</a> •
    <a href="#сборка-из-исходников">Сборка</a> •
    <a href="#лицензия">Лицензия</a>
  </p>
</div>

---

> [!IMPORTANT]
> **This is an unofficial fork.** WorldPainter Languages не является официальной версией WorldPainter и не связан с автором оригинального проекта.

Официальный WorldPainter: [worldpainter.net](https://www.worldpainter.net/) · [Captain-Chaos/WorldPainter](https://github.com/Captain-Chaos/WorldPainter)

## О проекте

WorldPainter — интерактивный редактор карт для Minecraft: рельеф, биомы, растительность и структуры рисуются кистями, как в графическом редакторе, а затем экспортируются в готовый мир.

WorldPainter Languages — форк, который добавляет к нему полноценную многоязычность и несколько собственных возможностей генерации. Текущий релиз — **3.0.0**, собран на базе **WorldPainter 2.27.1**.

Одиннадцать языков интерфейса — английский и десять переводов:

русский · беларуская · українська · қазақша · Deutsch · Français · Español · Italiano · Nederlands · 简体中文

Все одиннадцать локалей содержат одинаковый полный набор строк в девяти семействах ресурсов: интерфейс, меню, диалоги, предупреждения, ошибки, сообщения операций, блоки, растения, биомы, слои и стандартные Swing-элементы.

## Возможности

### Локализация

- Полный паритет ключей во всех одиннадцати локалях.
- Названия блоков, растений и биомов сверены с терминологией Minecraft и локализованных Minecraft Wiki.
- Язык выбирается в настройках; для китайского предусмотрен список подходящих системных шрифтов.
- Названия платформ Minecraft, версии, идентификаторы, форматы и расширения файлов намеренно не переводятся.

<img width="797" height="308" alt="Выбор языка интерфейса" src="https://github.com/user-attachments/assets/cf23198c-2d83-499d-84b8-b14017c4298e" />

### Слои и материалы форка

- **[BETA] Система пещер** (`CaveSystem`) — трёхмерный terrain-aware слой: крупные шумовые залы, cheese/grand-полости, соединённые spaghetti/backbone/noodle-тоннели, естественные выходы, аквиферы и лавовые зоны, пышные и натёчные области, глиняные водоёмы, растительность, небольшие и гигантские натёчные образования. Все семейства пещер, границы, входы, жидкости и декорации настраиваются в семи вкладках.
- **[BETA] Айсберги** (`Icebergs`) — айсберги из плотного льда в открытой воде.
- Терейны `ICE` и `PACKED_ICE`.

> [!WARNING]
> Экспорт мира с этими слоями идёт заметно дольше обычного: структура пещер считается в трёх измерениях.

### Интерфейс и темы

Две темы форка — **FlatLaf Cyan light** и **FlatLaf One Dark**, каждая со своим набором интерфейсных иконок. Штатные System, Metal, Nimbus, Dark Metal и Dark Nimbus сохранены.

**FlatLaf Cyan light**
<img width="1920" height="1032" alt="Тема FlatLaf Cyan light" src="https://github.com/user-attachments/assets/b611bab4-75ae-4515-b620-eed908a6d142" />

**FlatLaf One Dark**
<img width="1920" height="1032" alt="Тема FlatLaf One Dark" src="https://github.com/user-attachments/assets/4647910c-cbcc-4d9a-b665-8d4b90589c90" />

- 1107 иконок блоков и материалов в окнах выбора.
- 95 точных sprite-иконок биомов для FlatLaf-тем.
- Иконки слоёв, операций, панелей и docking-элементов следуют за темой.

### Windows

- Современный системный выбор файлов Vista+ через `IFileDialog`, с безопасным fallback на Swing.
- Регистрация в **Open with** для `.world`; назначение обработчиком по умолчанию — отдельная опция установщика.
- Установщик, Portable-сборка и классический MSI собираются единым PowerShell-сценарием.
- Встроенный апдейтер: докачивает только изменившиеся файлы приложения и сверяет их по SHA-256.

## Скачать

Готовые сборки — на странице [GitHub Releases](https://github.com/saplome/WorldPainter-LANGUAGES/releases).

| Файл | Назначение |
| --- | --- |
| `WorldPainter-Languages-<версия>-Setup.exe` | обычная установка в Windows: ярлыки, меню «Пуск», ассоциация `.world`. Рекомендуется большинству |
| `WorldPainter-Languages-<версия>-Portable.zip` | распаковать в любую папку и запустить `WorldPainter Languages.exe` |
| `WorldPainter-Languages-<версия>.zip` | исходный код |

Java устанавливать не нужно: и установщик, и Portable несут собственную Java-среду.

При первом запуске настройки оригинального WorldPainter копируются в отдельный каталог **WorldPainter Languages**. Настройки оригинала при этом не изменяются — обе программы могут стоять рядом.

## Совместимость миров

Обычные `.world`-файлы без возможностей форка остаются совместимыми с оригинальным WorldPainter.

Мир со слоями **[BETA] Система пещер** или **[BETA] Айсберги** оригинальная программа открыть не сможет: она не знает классы этих слоёв. Для передачи такого мира есть отдельная команда:

**Файл → Сохранить копию для оригинального WorldPainter…**

Она создаёт независимую копию, удаляет данные fork-only слоёв и заменяет терейны `ICE`/`PACKED_ICE` на штатный `DEEP_SNOW`. Текущий файл и обычная команда сохранения не затрагиваются.

## Сборка из исходников

Требования: Windows 10/11, JDK 17, Maven и доступ к Maven Central, JitPack и Maven-репозиторию WorldPainter. Для фирменного установщика нужен Inno Setup 6, для классического MSI — WiX Toolset 3.x.

```powershell
# Сборка исходников
mvn clean install -DskipTests

# Portable + Inno Setup
powershell -ExecutionPolicy Bypass -File .\tools\windows-packaging\build-windows-installer.ps1 -SkipMaven -BuildPortable -BuildInnoInstaller

# Классический WiX/MSI
powershell -ExecutionPolicy Bypass -File .\tools\windows-packaging\build-windows-installer.ps1 -SkipMaven -BuildInstaller
```

Чистый архив исходников содержит ядро, GUI, Dynmap previewer, все одиннадцать локалей, документацию и лицензии — без результатов сборки и релиз-кандидатов. Сценарии упаковки лежат в отдельном `release-tools.zip`: распакуйте его в корень проекта так, чтобы существовал путь `tools\windows-packaging\build-windows-installer.ps1`. В `github-ready.zip` они уже на месте. Перед пересборкой WorldPainter нужно закрыть.

## Документация

| Документ | О чём |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | что изменилось в каждой версии форка |
| [docs/CHANGES_L1_TO_L2_RU.md](docs/CHANGES_L1_TO_L2_RU.md) | подробный разбор изменений после первого релиза |
| [docs/UPDATE_CHANNEL_RU.md](docs/UPDATE_CHANNEL_RU.md) | как устроены проверка обновлений и апдейтер |
| [NOTICE.md](NOTICE.md) | что именно изменил форк и какие сторонние компоненты используются |

## Поддержать проект

Если проект полезен — поставьте репозиторию ⭐: [saplome/WorldPainter-LANGUAGES](https://github.com/saplome/WorldPainter-LANGUAGES).

Ошибки и предложения — в [Issues](https://github.com/saplome/WorldPainter-LANGUAGES/issues). Кнопка в окне ошибки сама копирует отчёт в буфер обмена и открывает трекер.

Поддержать оригинальный WorldPainter можно на [worldpainter.net](https://www.worldpainter.net/).

## Благодарности

Спасибо **Pepijn Schmitz (Captain-Chaos)** и всем участникам оригинального WorldPainter за многолетнюю работу над проектом.

WorldPainter Languages дополняет оригинальный проект локализациями и возможностями форка, но не заменяет официальную версию.

## Лицензия

WorldPainter Languages распространяется на условиях **GNU General Public License v3** — той же лицензии, что и оригинальная программа.

| Что | Авторские права |
| --- | --- |
| Оригинальный WorldPainter | © 2011–2026 [pepsoft.org](https://www.pepsoft.org/), Нидерланды — Pepijn Schmitz (Captain-Chaos) |
| Форк: переводы на 10 языков, дополнительные слои, темы, иконки, Windows-упаковка, канал обновлений и документация | © 2026 [saplome](https://github.com/saplome) |

Изменения относительно оригинального WorldPainter внесены в 2026 году saplome. Каждый изменённый и каждый новый файл помечен в заголовке, кто и когда его изменил или создал; перечень изменений по версиям — в [CHANGELOG.md](CHANGELOG.md).

Полный текст лицензии — в [LICENSE](LICENSE), сведения об оригинальном проекте и модификациях — в [NOTICE.md](NOTICE.md). Исходный код форка целиком доступен в этом репозитории — [saplome/WorldPainter-LANGUAGES](https://github.com/saplome/WorldPainter-LANGUAGES) — как того требует GPL v3 для распространяемых сборок.
