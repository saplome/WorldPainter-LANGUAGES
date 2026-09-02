<div align="center">
  <img src="assets/icon.png" alt="WorldPainter Languages" width="132">

  <h1>WorldPainter Languages</h1>

  <p>
    <strong>Неофициальный многоязычный форк WorldPainter 2.27.1</strong><br>
    Полностью локализованный WorldPainter с дополнительными темами, иконками, Windows-интеграцией и пользовательскими слоями генерации.
  </p>

  <p>
    <img alt="Release" src="https://img.shields.io/badge/release-3.0.0-e53935?style=for-the-badge">
    <img alt="Base" src="https://img.shields.io/badge/base-WorldPainter%202.27.1-2d8cff?style=for-the-badge">
    <img alt="Languages" src="https://img.shields.io/badge/languages-EN%20%2B%2010-6d4aff?style=for-the-badge">
    <img alt="License" src="https://img.shields.io/badge/license-GPLv3-43a047?style=for-the-badge">
  </p>

  <p>
    <a href="#скачать">Скачать</a> •
    <a href="#главное-в-300">Главное в 3.0.0</a> •
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

**WorldPainter Languages 3.0.0** основан на **WorldPainter 2.27.1**.

В релизе доступны английский и десять полных локализаций (всего 11 языков интерфейса):

- русский;
- беларуская;
- українська;
- қазақша;
- Deutsch;
- Français;
- Español;
- Italiano;
- Nederlands;
- 简体中文.

Все одиннадцать локалей содержат одинаковый полный набор строк в девяти семействах ресурсов. Переведены интерфейс, меню, диалоги, предупреждения, ошибки, сообщения операций, блоки, растения, биомы, слои и стандартные Swing-элементы.

## Скачать

Готовые файлы публикуются на странице [GitHub Releases](https://github.com/saplome/WorldPainter-LANGUAGES/releases).

| Файл | Назначение |
| --- | --- |
| `WorldPainter-Languages-3.0.0-Setup.exe` | обычная установка в Windows, ярлыки и меню «Пуск» |
| `WorldPainter-Languages-3.0.0-Portable.zip` | переносимая сборка со встроенной Java-средой |
| `WorldPainter-Languages-3.0.0.zip` | чистый исходный код без сценариев релизной упаковки |

> [!NOTE]
> Для большинства пользователей рекомендуется `WorldPainter-Languages-3.0.0-Setup.exe`.

## Главное в 3.0.0

### Нумерация версий

- Форк перешёл на обычные номера версий: **3.0.0** вместо `2.27.1-L2.1.0`. Это та же линия, что и раньше (`L1` → `L2.0.0` → `L2.0.1` → `3.0.0`), просто без версии базы внутри номера.
- Версия WorldPainter, на которой собран форк, теперь указана отдельно — в окне **«О программе»** и в именах jar-файлов. Она берётся прямо из кода, поэтому не может разойтись с реальностью.
- Тег релиза остаётся с буквой — `L3.0.0`: сборки до `2.27.0-L2.0.1` искали в теге `L` и число за ней, без буквы они бы не увидели новый релиз.

### Локализация

- Английский + 10 локализаций с полным паритетом ключей.
- Названия блоков, растений и биомов сверены с терминологией Minecraft и локализованных Minecraft Wiki.
- Поддерживается выбор языка в настройках; для китайского предусмотрен список подходящих системных шрифтов.
<img width="797" height="308" alt="image" src="https://github.com/user-attachments/assets/cf23198c-2d83-499d-84b8-b14017c4298e" />

### Новые слои и материалы

- **[BETA] Система пещер** (`CaveSystem`) — трёхмерный terrain-aware слой с крупными шумовыми залами, cheese/grand-полостями, соединёнными spaghetti/backbone/noodle-тоннелями, естественными выходами, аквиферами и лавовыми зонами. Поддерживает отдельные пышные и натёчные области, глиняные водоёмы, растительность, небольшие и гигантские натёчные образования. Все семейства пещер, границы, входы, жидкости и декорации настраиваются в семи вкладках.
- **[BETA] Айсберги** (`Icebergs`) — генерация айсбергов из плотного льда в открытой воде.

- **:bangbang::bangbang:НОВЫЕ СЛОИ МОГУТ ДОЛГО ЭКСПОРТИРОВАТЬСЯ В МИР Minecraft ИЗ-ЗА СЛОЖНОЙ СТРУКТУРЫ:bangbang::bangbang:** 

---

### Интерфейс и иконки

- Две **НОВЫЕ** темы:

**FlatLaf Cyan light**
<img width="1920" height="1032" alt="image" src="https://github.com/user-attachments/assets/b611bab4-75ae-4515-b620-eed908a6d142" />

**FlatLaf One Dark**
<img width="1920" height="1032" alt="image" src="https://github.com/user-attachments/assets/4647910c-cbcc-4d9a-b665-8d4b90589c90" />

> Также сохранены System, Metal, Nimbus, Dark Metal и Dark Nimbus.
- Тематические наборы интерфейсных иконок для обеих FlatLaf-тем.
- 1107 иконок блоков и материалов в окнах выбора.
- 95 точных sprite-иконок биомов для FlatLaf-тем.
- Исправлены цвета, docking-элементы, подсказки JIDE, миниатюры кистей и переключение темы/масштаба.

---

### Windows и обновления

- Современный системный выбор файлов Vista+ через `IFileDialog`, с безопасным fallback на Swing.
- Автоматическая и ручная проверка новых релизов WorldPainter Languages на GitHub.
- Windows installer, Portable и Inno Setup собираются единым PowerShell-сценарием.
- Установщик регистрирует WorldPainter Languages в **Open with** для `.world`; назначение обработчиком по умолчанию остаётся отдельной опцией.

Полный консолидированный список изменений после L1: [docs/CHANGES_L1_TO_L2_RU.md](docs/CHANGES_L1_TO_L2_RU.md).

---

## Совместимость миров

Обычные `.world`-файлы без возможностей форка остаются совместимыми с оригинальным WorldPainter 2.27.1.

Мир, содержащий слои **[BETA] Система пещер** или **[BETA] Айсберги**, нельзя открывать непосредственно в оригинальной программе: оригинал не знает классы этих слоёв. Для передачи такого мира используйте отдельную команду:

**Файл → Сохранить копию для оригинального WorldPainter…**

Она создаёт независимую копию, удаляет данные fork-only слоёв и заменяет терейны `ICE`/`PACKED_ICE` на штатный `DEEP_SNOW`. Текущий файл и обычная команда сохранения не изменяются.

---

## Установка

1. Откройте [Releases](https://github.com/saplome/WorldPainter-LANGUAGES/releases).
2. Скачайте `WorldPainter-Languages-3.0.0-Setup.exe`.
3. Запустите установщик и следуйте шагам.
4. Выберите язык и тему в настройках WorldPainter.

При первом запуске настройки оригинального WorldPainter могут быть скопированы в отдельный каталог **WorldPainter Languages**; исходные настройки оригинала не изменяются.

---

## Portable

1. Скачайте `WorldPainter-Languages-3.0.0-Portable.zip`.
2. Распакуйте архив в отдельную папку.
3. Запустите `WorldPainter Languages.exe`.

---

## Обновление

Переустанавливать программу не нужно: начиная с L2.0.1 в комплекте идёт инкрементальный апдейтер.

**Из программы.** **Справка → Проверить обновления…** — если на GitHub есть более новый релиз, появится окно с кнопкой **«Обновить сейчас»**. WorldPainter закроется, `WorldPainter-Update.exe` докачает только изменившиеся файлы, проверит их по SHA-256 и запустит программу заново. Проверка выполняется и при старте, её можно отключить в настройках.

**Вручную.** Запустите `WorldPainter-Update.exe` из папки установки (или из папки Portable-сборки). Это тот же процесс без участия основного приложения.

> Пользователям **L2.0.1**: о релизе 3.0.0 та сборка сообщит сама, но кнопка в её окне умеет только открыть страницу релиза. Обновитесь один раз через `WorldPainter-Update.exe` из папки установки (для установки в `Program Files` — от имени администратора) или установите Setup поверх — дальше кнопка **«Обновить сейчас»** работает штатно.

Файлы обновления раздаются из репозитория [WorldPainter-LANGUAGES-cdn](https://github.com/saplome/WorldPainter-LANGUAGES-cdn), манифест публикуется как ассет релиза. Обновляются только файлы приложения (`app/`); Java-среда и `.exe`-лаунчеры не меняются между релизами одной базы. Как устроен канал обновлений и что публикуется при каждом релизе: [docs/UPDATE_CHANNEL_RU.md](docs/UPDATE_CHANNEL_RU.md).

---

## Сборка из исходников

Требования:

- Windows 10/11;
- JDK 17;
- Maven;
- доступ к Maven Central, JitPack и Maven-репозиторию WorldPainter;
- Inno Setup 6 для фирменного Setup.exe;
- WiX Toolset 3.x для классического MSI/jpackage installer.

WorldPainter нужно закрыть перед пересборкой. Чистый `src.zip` содержит только исходники. Для Portable или установщика распакуйте отдельный `release-tools.zip` в корень проекта: после распаковки должен существовать путь `tools\windows-packaging\build-windows-installer.ps1`.

```powershell
# Сборка исходников
mvn clean install -DskipTests

# Portable + Inno Setup
powershell -ExecutionPolicy Bypass -File .\tools\windows-packaging\build-windows-installer.ps1 -SkipMaven -BuildPortable -BuildInnoInstaller

# Классический WiX/MSI
powershell -ExecutionPolicy Bypass -File .\tools\windows-packaging\build-windows-installer.ps1 -SkipMaven -BuildInstaller
```

---

## Что входит в исходники

- ядро, GUI и Dynmap previewer;
- все одиннадцать локалей с полным паритетом строк;
- документация и лицензии.

Чистый архив исходников не содержит временных файлов, результатов Maven, release-кандидатов и сценариев упаковки. Инструменты Windows вынесены в отдельный архив, а в GitHub-ready архиве сохранены в каталоге `tools/` для прямого коммита.

---

## Ограничения

- Это неофициальный форк WorldPainter 2.27.1.
- Fork-only слои требуют специальной команды сохранения копии для оригинальной программы.
- Названия платформ Minecraft, версии, идентификаторы, форматы и расширения файлов намеренно не переводятся.
- Финальная релизная сборка и GUI-проверка выполняются на Windows/JDK 17.

---

## Поддержать проект

Если проект полезен, поставьте репозиторию ⭐: [saplome/WorldPainter-LANGUAGES](https://github.com/saplome/WorldPainter-LANGUAGES).

Поддержать оригинальный WorldPainter можно на [worldpainter.net](https://www.worldpainter.net/).

---

## Благодарности

Спасибо **Pepijn Schmitz (Captain-Chaos)** и всем участникам оригинального WorldPainter за многолетнюю работу над проектом.

WorldPainter Languages дополняет оригинальный проект локализациями и возможностями форка, но не заменяет официальную версию.

## Лицензия

WorldPainter Languages распространяется на условиях **GNU General Public License v3**.

Полный текст лицензии находится в [LICENSE](LICENSE), сведения об оригинальном проекте и модификациях — в [NOTICE.md](NOTICE.md).
