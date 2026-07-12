<div align="center">
<img src="assets/icon.png" alt="WorldPainter Languages" width="132">

<h1>WorldPainter Languages</h1>

<p>
<strong>Неофициальный многоязычный fork WorldPainter 2.27.0</strong><br>
редактор миров Minecraft с локализацией интерфейса на русский, белорусский, украинский и казахский языки.
</p>

<p>
<img alt="Release" src="https://img.shields.io/badge/release-2.27.0--L1-e53935?style=for-the-badge">
<img alt="Base" src="https://img.shields.io/badge/base-WorldPainter%202.27.0-2d8cff?style=for-the-badge">
<img alt="Languages" src="https://img.shields.io/badge/languages-RU%20%7C%20BE%20%7C%20UK%20%7C%20KK-6d4aff?style=for-the-badge">
<img alt="License" src="https://img.shields.io/badge/license-GPLv3-43a047?style=for-the-badge">
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
> **This is an unofficial localization fork.**
> WorldPainter Languages не является официальной версией WorldPainter и не связан с автором оригинального проекта.

Официальный WorldPainter:

- сайт: [worldpainter.net](https://www.worldpainter.net/)
- репозиторий: [Captain-Chaos/WorldPainter](https://github.com/Captain-Chaos/WorldPainter)

## О проекте

**WorldPainter Languages 2.27.0-L1** основан на **WorldPainter 2.27.0** и сохраняет полную совместимость с оригинальными `.world` файлами.

Интерфейс переведён на 4 языка — русский, белорусский, украинский и казахский — с полным паритетом ключей между языками. Язык подхватывается автоматически из языка системы, английский всегда доступен как базовый.

## Скачать

Все файлы — на странице [Releases](https://github.com/saplome/WorldPainter-LANGUAGES/releases):

| Файл | Для чего нужен |
| --- | --- |
| `WorldPainter-Languages-2.27.0-L1-Setup.exe` | обычная установка на Windows: ярлыки, меню «Пуск», деинсталлятор |
| `WorldPainter-Languages-2.27.0-L1-Portable.zip` | работает без установки — распаковал и запустил |
| `Source code.zip` | исходный код релиза |

> [!NOTE]
> Для обычного пользователя лучше `Setup.exe`. Portable удобна, если не хочется ничего устанавливать.

## Что готово

- Локализация интерфейса WorldPainter 2.27.0 на 4 языка: RU / BE / UK / KK.
- Переведены меню, окна, панели, диалоги, предупреждения, ошибки, progress-сообщения, блоки, биомы, растения и слои.
- Переводы разделены на доменные bundle-файлы (strings / blocks / gamedata / layers / swing) — новый язык добавляется без правки кода.
- Готовые сборки для Windows: установщик и portable-версия. Java устанавливать не нужно — среда выполнения встроена.
- Сохранена лицензия **GNU GPLv3**.

## Установка

1. Откройте страницу **Releases**.
2. Скачайте `WorldPainter-Languages-2.27.0-L1-Setup.exe`.
3. Запустите установщик и следуйте шагам.
4. Готово — программа запустится на языке вашей системы.

При первом запуске программа предложит перенести настройки из оригинального WorldPainter, если он установлен. Ваши миры и настройки оригинала при этом не изменяются.

## Portable

1. Скачайте `WorldPainter-Languages-2.27.0-L1-Portable.zip`.
2. Распакуйте в любую папку (или на флешку).
3. Запустите `WorldPainter Languages.exe`.

Установка и отдельная Java не требуются.

## Сборка

Требования: JDK 17, Maven, доступ к Maven Central.

```bash
mvn clean package
```

Собранные jar-файлы появятся в `WPCore/target`, `WPGUI/target`, `WPDynmapPreviewer/target`.

## Структура

```text
WPCore/               ядро WorldPainter
WPGUI/                графический интерфейс и файлы переводов
WPDynmapPreviewer/    dynmap previewer
assets/               иконки проекта
```

## Ограничения

- Это неофициальный fork.
- Названия платформ Minecraft, версии Minecraft, форматы и расширения файлов намеренно не переводятся.
- Некоторые редкие аварийные сценарии требуют ручной проверки на реальной Windows-системе.

## Поддержать проект

Если проект вам полезен — поставьте репозиторию ⭐ на GitHub: [saplome/WorldPainter-LANGUAGES](https://github.com/saplome/WorldPainter-LANGUAGES).
Поддержать оригинальный WorldPainter можно на [worldpainter.net](https://www.worldpainter.net/).

## Благодарности

Спасибо **[Captain-Chaos](https://github.com/Captain-Chaos/)** и участникам оригинального WorldPainter за огромную работу над проектом.
WorldPainter Languages существует как многоязычная адаптация и не заменяет официальный WorldPainter.

## Лицензия

WorldPainter Languages является fork проекта WorldPainter и распространяется на условиях **GNU General Public License v3**.

Условия лицензии не изменялись. Полный текст — в файле [LICENSE](LICENSE), сведения об оригинальном проекте — в [NOTICE.md](NOTICE.md).
