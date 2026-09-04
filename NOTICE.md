# NOTICE

WorldPainter Languages is an unofficial localization fork of WorldPainter. It ships an English interface and ten complete localizations — Russian, Belarusian, Ukrainian, Kazakh, German, French, Spanish, Italian, Dutch and Simplified Chinese — eleven interface languages in total, each with the same full set of strings across nine resource families.

## Original project

- https://www.worldpainter.net/
- https://github.com/Captain-Chaos/WorldPainter

Original author: Pepijn Schmitz (Captain-Chaos) and the WorldPainter contributors.

Original WorldPainter code Copyright © 2011-2026 pepsoft.org, The Netherlands.

## Fork

Copyright © 2026 saplome (https://github.com/saplome).

The work of the fork covers: the interface translations and the localization infrastructure; the [BETA] Cave System and [BETA] Icebergs layers and the ICE and PACKED_ICE terrains; the [BETA] tree generator; the FlatLaf themes and the theme-aware icon handling; the block, biome and tree icon sets used by the pickers and the tree generator; the modern Windows file dialog; the update checker, the incremental updater and its update channel; the Windows packaging for Portable, WiX/MSI and Inno Setup; and the documentation in this repository.

Dutch is the one exception among the localizations: WorldPainter itself shipped a partial Dutch translation of 148 strings, contributed through Crowdin. Those strings are kept as they were, and the fork extended the translation to the full set.

## Modifications

As section 5(a) of the GNU General Public License requires, every file the fork changed or added carries a notice in its header naming who changed it and when. Files that are byte-identical to WorldPainter 2.27.1 keep the original notices unchanged and carry no notice of the fork.

The changes are listed per version in [CHANGELOG.md](CHANGELOG.md), and in the application itself under **Help > About > Changes**, where the history of the fork precedes the original change log.

## Source code

The complete source code of this fork, including its packaging and release tooling, is available at https://github.com/saplome/WorldPainter-LANGUAGES. This is how section 6 of the GPL is satisfied for the binary builds published on the releases page.

## Licensing

WorldPainter Languages preserves the licensing terms of the original project: the GNU General Public License, version 3. The full text is in [LICENSE](LICENSE), reproduced verbatim as published by the Free Software Foundation.

## Artwork

The application icon, the splash screen and the About and banner artwork are assets of this fork. They replace the original shovel icon (by Rokey) and the original splash images (by winddelay and razer), which is why those two credits are not repeated in the Credits tab of the fork; every other credit of the original project is kept.

The interface icons of the FlatLaf themes are theme-specific versions of the same icons the original uses, which come from the Silk icon set by Mark James (Creative Commons Attribution 2.5); the dark Metal and Nimbus icon sets are almic's, extended here with the icons of the new layers. Both credits are kept in the Credits tab.

The block icons, the biome icons and the tree textures depict Minecraft blocks and biomes. Minecraft is a trademark of Mojang Synergies AB; neither WorldPainter nor this fork is affiliated with or endorsed by Mojang or Microsoft.

## Third-party components inherited from WorldPainter

Kept unchanged from the original project:

- `WPCore/src/main/java/org/pepsoft/minecraft/RegionFile.java` and `RegionFileCache.java` — an implementation of the region file format by Scaevolus, later modified by Mojang AB; the author disclaims copyright to that source code.
- `WPCore/src/main/java/com/khorn/terraincontrol/util/minecraftTypes/DefaultMaterial.java` — Copyright © contributors of TerrainControl, MIT License.
- The libraries the original project uses and credits under **Help > About > Credits**: the JIDE Docking and Action Frameworks, JNBT by Graham Edgecombe, the dynmap colour schemes by Mike Primm, the JPen project, the Silk icon set by Mark James, and the flood fill algorithm by J. Dunlap in the Java port by Owen Kaluza.

## Third-party components added by the fork

- **FlatLaf 3.7.2** (`flatlaf`, `flatlaf-intellij-themes`, `flatlaf-jide-oss`) — Copyright © FormDev Software GmbH, Apache License 2.0. Bundled in the binary builds.
- **JNA 5.13.0** — Copyright © the JNA contributors, dual-licensed under the Apache License 2.0 and the LGPL, version 2.1 or later. Bundled in the binary builds; it drives the Windows IFileDialog integration.
- **jpackage WiX resources** in `tools/windows-packaging/jpackage-resources/windows/` — copied out of the JDK (`jdk.jpackage/resources`), Copyright © Oracle and/or its affiliates, released with the OpenJDK under the GNU General Public License, version 2, with the Classpath exception. The file headers of Oracle are kept verbatim, and the changes of the fork — Russian dialog text instead of the `!(loc.*)` references, and the checkbox that launches the application — are described in each file. These are build-time resources for jpackage: they are not part of the application and are not installed.
- **Inno Setup** by Jordan Russell and Martijn Laan builds the published Setup.exe. It is used as an external tool under its own license; no part of it is included in this repository.

## Java runtime in the binary builds

The Setup.exe and Portable builds contain a Java runtime image that jpackage produces with jlink from Eclipse Temurin JDK 17: OpenJDK code, Copyright © Oracle and/or its affiliates and the OpenJDK contributors, licensed under the GNU General Public License, version 2, with the Classpath exception. Its source is available from https://adoptium.net/ and https://openjdk.org/. The source archives of this fork contain no Java runtime.

## Non-affiliation

This fork is not affiliated with, endorsed by, or maintained by the original WorldPainter author.

