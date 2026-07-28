# Полный аудит окон и Windows Explorer picker (v7.3)

## Выполненный план

1. Просканировать весь Java-код всех модулей на `JFileChooser`, AWT `FileDialog`, `JOptionPane`, `MessageUtils`, `JDialog`, `setVisible(true)`, `JPopupMenu`, `JColorChooser` и все методы выбора файлов.
2. Свести все активные пути open/save/multi-select/folder в одну точку — `FileUtils`.
3. На Windows заменить backend на прямой Vista+ COM `IFileOpenDialog`/`IFileSaveDialog`; не использовать `SHBrowseForFolder`, `GetOpenFileName` или Swing chooser.
4. Проверить owner HWND, модальность, отдельный STA-поток, Unicode/UNC, начальный каталог, имя файла, расширение, фильтры, multi-select, overwrite prompt и отмену.
5. Удалить старый `jnafilechooser`, legacy folder browser, Windows fallback на `JFileChooser` и мёртвый закомментированный chooser.
6. Проверить каждый файловый сценарий приложения, локализации и неизменность генератора деревьев.
7. Скомпилировать изменённые классы Java 17, выполнить статический QA, проверить ZIP и SHA-256.

## Архитектура после исправления

- Windows: только `WindowsExplorerFileDialog`, прямой COM Vista+.
- Open: `CLSID_FileOpenDialog` + `IID_IFileOpenDialog`.
- Save: `CLSID_FileSaveDialog` + `IID_IFileSaveDialog`.
- Folder: `IFileOpenDialog` + `FOS_PICKFOLDERS`.
- Multi-open: `FOS_ALLOWMULTISELECT` + `IFileOpenDialog.GetResults`.
- Save: `FOS_OVERWRITEPROMPT`, `SetDefaultExtension`, `SetFileName`.
- Все режимы: `FOS_FORCEFILESYSTEM`, `FOS_PATHMUSTEXIST`, `FOS_NOCHANGEDIR`, owner HWND и STA.
- Отмена (`0x800704C7`) возвращает `null` без сообщения об ошибке.
- При внутренней ошибке Windows legacy chooser **не показывается**: операция отменяется и ошибка попадает в лог.
- macOS сохраняет системный AWT picker; Swing chooser остаётся только платформенным fallback для не-Windows систем.

## Проверенные файловые сценарии

- Открытие/сохранение `.world`, Save for original, recent/open flow.
- Выбор существующей карты и каталога Minecraft saves.
- Каталог экспорта мира и data pack.
- Экспорт изображения и height map.
- Импорт/экспорт custom layer и terrain; multi-select.
- BO2/custom object: файлы и каталог.
- Script Runner и параметры скриптов.
- Respawn Player (`level.dat`).
- Tree Generator (`.schem`).
- Общие image/mask/heightmap helpers.
- Неиспользуемый совместимый mixed files-or-folder API: перед запуском Explorer предлагает режим, так как Windows `IFileDialog` не позволяет одним выделением смешивать файлы и каталоги.

## Инвентаризация всех popup-поверхностей

Полный машинный список сохранён в `v73-popup-inventory.tsv` в QA-пакете.

- 40 классов диалогов (`JDialog`/`WorldPainterDialog`).
- 39 файлов с `JOptionPane`, 209 вызовов.
- 30 файлов с `MessageUtils`, 35 вызовов.
- 45 файлов с `setVisible(true)`, 97 вызовов.
- 8 файлов с `JPopupMenu`, 36 упоминаний.
- 3 файла с `JColorChooser`, 4 вызова.
- 11 файлов напрямую вызывают центральный `FileUtils`; остальные файловые сценарии проходят через эти helper-ы.

`JOptionPane`, `MessageUtils`, редакторы, подтверждения, ошибки, progress dialogs, colour pickers и popup menus не выбирают путь на диске и поэтому не заменяются проводником. Их owner/modality и назначение включены в аудит; активных прямых файловых chooser-ов вне `FileUtils` нет.

## Ручной Windows smoke-test

Автоматический Linux QA не может отобразить COM UI. Перед релизом бинарника на Windows пройти: open/save/multi/folder; cancel; overwrite yes/no; Unicode и кириллица; UNC/network path; недоступный network path; длинный путь; фильтры; default extension; owner focus и Alt-Tab; повторное открытие после cancel.
