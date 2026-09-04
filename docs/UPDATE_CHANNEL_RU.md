# Канал обновлений WorldPainter Languages

Документ описывает, как работает обновление «одной кнопкой» и что нужно опубликовать на GitHub,
чтобы оно заработало.

## Как это устроено

1. WorldPainter (класс `ForkUpdateChecker`) спрашивает у GitHub API последний релиз основного
   репозитория: `https://api.github.com/repos/saplome/WorldPainter-LANGUAGES/releases/latest`.
   Из тега (`L3.0.0`) берётся номер версии и сравнивается с текущей. Номер версии обычный, буква
   `L` в теге нужна только для совместимости: сборки до `2.27.0-L2.0.1` искали в теге `L` и число
   за ней, поэтому без неё они бы не увидели релиз. Теги прошлых релизов в старом виде
   (`v2.27.0-L2.0.1`) тоже разбираются — из них берётся часть после `L`.
2. Если версия новее, в диалоге появляется кнопка **«Обновить сейчас»**. Приложение штатно
   закрывается и запускает `WorldPainter-Update.exe` из папки установки.
3. `WorldPainter-Update.exe` — это лаунчер jpackage, который запускает `wp-updater-<версия>.jar`.
   Апдейтер читает `manifestUrl` из `updater.properties` и скачивает манифест:
   `https://github.com/saplome/WorldPainter-LANGUAGES/releases/latest/download/update-manifest.txt`.
4. Манифест — текстовый файл: по строке на файл, поля разделены табуляцией.

   ```
   format=1
   version=3.0.0
   file=<sha256>	<размер>	<путь внутри app>	<полный URL файла>
   delete=<путь внутри app>
   ```

5. Для каждой строки `file=` апдейтер сравнивает размер и SHA-256 локального файла. Совпало —
   пропускает, иначе скачивает во временный `*.wpupdate-tmp`, проверяет хэш и заменяет файл
   атомарно. Файлы из строк `delete=` удаляются.
6. По завершении апдейтер запускает `..\WorldPainter Languages.exe` (значение `launch` в
   `updater.properties`).

Если папка установки недоступна для записи (обычный случай `C:\Program Files`), апдейтер сам
перезапускается с запросом прав администратора и делает работу в повышенном процессе. Если UAC
отклонён, повышенная копия возвращает `ERROR_CANCELLED` (1223), апдейтер снова запускает приложение
и завершается с кодом `2`; установка остаётся нетронутой.

Коды выхода: `0` — успех, `1` — есть обновления (только при `--check-only`), `2` — ошибка.

## Что где хранится

| Что | Где | Кто читает |
| --- | --- | --- |
| Номер последней версии | тег релиза в `saplome/WorldPainter-LANGUAGES` | сам WorldPainter |
| `update-manifest.txt` | ассет того же релиза | `WorldPainter-Update.exe` |
| Файлы `app/**` (54 файла, 33 МБ) | репозиторий `saplome/WorldPainter-LANGUAGES-cdn`, ветка `main`, папка `app/` | `WorldPainter-Update.exe` |
| `Setup.exe`, `Portable.zip`, архивы исходников | ассеты релиза | человек |

## Почему нужен второй репозиторий

Ассеты релиза на GitHub «плоские»: у них нет путей, только имена файлов. Манифесту же нужен
отдельный URL на каждый файл, включая `app/lib/guava-33.4.8-jre.jar`. Поэтому файлы приложения
лежат в обычном репозитории и раздаются через `raw.githubusercontent.com`, а манифест — в ассетах
релиза, потому что ссылка `releases/latest/download/...` всегда указывает на актуальный релиз.

## Шаг 1. Создать CDN-репозиторий (один раз)

Имя должно совпадать с URL в манифесте: **`WorldPainter-LANGUAGES-cdn`**, владелец `saplome`,
видимость **public** (`raw.githubusercontent.com` не отдаёт файлы приватных репозиториев без
токена), ветка по умолчанию **`main`**.

Через веб-интерфейс: **New repository** → имя `WorldPainter-LANGUAGES-cdn` → Public → **без**
README, .gitignore и лицензии → Create.

Или через `gh` (один раз выполнить `gh auth login`):

```bash
gh repo create saplome/WorldPainter-LANGUAGES-cdn --public --description "Update host for WorldPainter Languages"
```

Затем залить подготовленную папку `release\cdn` (в ней уже лежат `app/`, `update-manifest.txt`,
`README.md` и `.gitattributes`):

```bash
cd /c/WorldPainter-LANGUAGES-main/WorldPainter-Languages/release/cdn
git init -b main
git add -A
git commit -m "WorldPainter Languages 3.0.0 app files"
git remote add origin https://github.com/saplome/WorldPainter-LANGUAGES-cdn.git
git push -u origin main
```

**Не удаляйте `.gitattributes` со строкой `* -text`.** Апдейтер сверяет SHA-256 отданных байтов с
манифестом, а при обычной для Windows настройке `core.autocrlf=true` git заменил бы CRLF на LF в
`.cfg` и `.properties` — и каждое обновление падало бы с «SHA-256 mismatch».

**Не включайте Git LFS**: `raw.githubusercontent.com` отдал бы текстовый указатель LFS вместо файла.

## Шаг 2. Что публиковать при каждом релизе

Всё готовое лежит в `release\` после сборки
(`tools\windows-packaging\build-windows-installer.ps1 -BuildPortable -BuildInnoInstaller -GenerateUpdateManifest`):

| Файл или папка | Куда |
| --- | --- |
| `release\cdn\` (`app/`, `.gitattributes`, `README.md`, `update-manifest.txt`) | коммит в `WorldPainter-LANGUAGES-cdn`, ветка `main` |
| `release\update-manifest.txt` | ассет релиза |
| `release\installer\WorldPainter-Languages-3.0.0-Setup.exe` | ассет релиза |
| `release\WorldPainter-Languages-3.0.0-Portable.zip` | ассет релиза |
| `release\github\*.zip` (архивы исходников, `tools\release\pack-release-archives.ps1`) | ассет релиза |

## Шаг 3. Порядок публикации

Порядок важен: пока файлы не в CDN, манифест ссылается в пустоту.

1. **Сначала CDN.** Обновить `app/` и `update-manifest.txt` в `WorldPainter-LANGUAGES-cdn` и
   запушить в `main`:

   ```bash
   cd /c/WorldPainter-LANGUAGES-main/WorldPainter-Languages/release/cdn
   git add -A && git commit -m "WorldPainter Languages 3.0.0 app files" && git push
   ```

   Если репозиторий уже был склонирован раньше, замените в нём `app/` целиком (удалить старую папку,
   скопировать новую), чтобы исчезнувшие файлы попали в коммит как удаления.

2. **Потом релиз** в основном репозитории — с тегом `L3.0.0`:

   ```bash
   cd /c/WorldPainter-LANGUAGES-main/WorldPainter-Languages
   gh release create L3.0.0 \
     --repo saplome/WorldPainter-LANGUAGES \
     --title "WorldPainter Languages 3.0.0" \
     --notes-file docs/RELEASE_NOTES_3.0.0.md \
     "release/installer/WorldPainter-Languages-3.0.0-Setup.exe" \
     "release/WorldPainter-Languages-3.0.0-Portable.zip" \
     "release/update-manifest.txt"
   ```

   Черновик вместо публикации — добавить `--draft`; тогда `releases/latest` пока не меняется.

3. Тег `L3.0.0` должен существовать в основном репозитории (`gh release create` создаст его
   из текущего коммита, если тега ещё нет).

## Шаг 4. Проверка после публикации

```bash
curl -sI https://github.com/saplome/WorldPainter-LANGUAGES/releases/latest/download/update-manifest.txt | head -1
curl -s https://raw.githubusercontent.com/saplome/WorldPainter-LANGUAGES-cdn/main/app/updater.properties
```

Первая команда должна вернуть `HTTP/2 302` (редирект на файл), вторая — содержимое файла, а не
страницу с ошибкой. Полная проверка — на реальной установке: запустить `WorldPainter-Update.exe`
с параметром `--check-only`; код `0` означает «всё актуально», `1` — «есть обновления».

Локальная проверка всего канала без публикации: `tools\updater\test-updater-local.ps1` поднимает
HTTP-сервер на localhost, генерирует манифест и прогоняет семь сценариев, включая порчу файла и
попытку заменить сам апдейтер.

---

**WorldPainter Languages** — неофициальный локализационный форк [WorldPainter](https://www.pepsoft.org/WorldPainter/), © 2011–2026 pepsoft.org, Нидерланды. Этот документ написан в 2026 году для WorldPainter Languages и не входит в оригинальный проект; © 2026 [saplome](https://github.com/saplome). Лицензия — [GNU General Public License v3](../LICENSE), та же, что и у приложения.
