# Локализация WorldPainter Languages

Переводы хранятся в resource bundles (UTF-8, CRLF):

```text
WPGUI/src/main/resources/org/pepsoft/worldpainter/resources/
    strings[_xx].properties     — интерфейс
    blocks[_xx].properties      — блоки Minecraft
    gamedata[_xx].properties    — биомы, растения, рельеф, цвета
    layers[_xx].properties      — слои и операции
    swing[_xx].properties       — стандартные диалоги Swing
    Category / GameType / Generator / LightOrigin — enum-значения
    languages.list              — список языков для меню
```

Доступ из кода: `WPI18n.s("some.key")` — ключи маршрутизируются в нужный bundle по префиксу автоматически.
Если ключ не найден, показывается английский текст или сам ключ — неполный перевод не ломает запуск.

## Как добавить новый язык

1. Скопируйте 5 файлов `(strings|blocks|gamedata|layers|swing)_<код>.properties` и переведите значения.
2. Добавьте строку `<код> = <название>` в `languages.list`.
3. Правки кода не требуются.

Важно: сохраняйте плейсхолдеры `{0}`, `{1}` и HTML-теги в значениях; количество ключей во всех языках должно совпадать.

---

**WorldPainter Languages** — неофициальный локализационный форк [WorldPainter](https://www.pepsoft.org/WorldPainter/), © 2011–2026 pepsoft.org, Нидерланды. Этот документ написан в 2026 году для WorldPainter Languages и не входит в оригинальный проект; © 2026 [saplome](https://github.com/saplome). Лицензия — [GNU General Public License v3](../LICENSE), та же, что и у приложения.
