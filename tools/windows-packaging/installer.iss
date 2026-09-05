; Inno Setup 6 script for WorldPainter Languages
;
; This file is part of WorldPainter Languages, an unofficial localization fork of WorldPainter
; (https://github.com/saplome/WorldPainter-LANGUAGES).
; Copyright (C) 2026 saplome. Written in 2026 for WorldPainter Languages; the original project
; ships no Inno Setup script. Licensed under the GNU General Public License, version 3, the same
; licence as the application it packages. See the LICENSE file for details.
;
; Requires Inno Setup 6: https://jrsoftware.org/isdl.php
; Build the app image first:
;   powershell -ExecutionPolicy Bypass -File .\tools\windows-packaging\build-windows-installer.ps1 -BuildAppImage
; Or let the build script do everything:
;   powershell -ExecutionPolicy Bypass -File .\tools\windows-packaging\build-windows-installer.ps1 -BuildInnoInstaller

#define MyAppName "WorldPainter Languages"
#define MyAppVersion "3.0.1"
; The publisher is the person who maintains this fork, which is what Windows shows in
; "Apps & features" and in the UAC prompt; the application name is separate, above.
#define MyAppPublisher "saplome"
; Keep this ASCII: Inno only reads a .iss as UTF-8 when the file has a byte order mark, so a
; (c) sign would end up mojibake in Setup.exe version information on a non-UTF-8 code page.
#define MyAppCopyright "Copyright (C) 2011-2026 pepsoft.org; modifications (C) 2026 saplome. GPL v3."
#define MyAppURL "https://github.com/saplome/WorldPainter-LANGUAGES"
#define MyAppExeName "WorldPainter Languages.exe"
#define AppImageDir "..\..\release\app-image\WorldPainter Languages"

[Setup]
AppId={{8F4D9C21-6A0B-4E7D-9C55-3B1A2D7E64F9}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
; Without this, Setup.exe ships with an empty LegalCopyright, which Windows shows as a blank
; line in the Details tab. Inno derives VersionInfoCopyright and VersionInfoCompany from
; AppCopyright and AppPublisher, so setting these two covers the whole version resource.
AppCopyright={#MyAppCopyright}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
LicenseFile=..\..\LICENSE
OutputDir=..\..\release\installer
OutputBaseFilename=WorldPainter-Languages-{#MyAppVersion}-Setup
SetupIconFile=..\..\assets\icon.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2
SolidCompression=yes
; Setup.exe leaves FileVersion blank unless this is set explicitly, and Windows reads it in the Details tab and in
; SmartScreen heuristics. It only accepts plain numbers, which is why it could not be set while versions looked
; like 2.27.1-L2.1.0.
VersionInfoVersion={#MyAppVersion}
WizardStyle=modern
WizardImageFile=inno\wizard-side.bmp
WizardSmallImageFile=inno\wizard-small.bmp
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequiredOverridesAllowed=dialog
ChangesAssociations=yes

[Languages]
; Belarusian and Kazakh are not shipped with Inno Setup officially;
; the application itself follows the Windows display language for all 5 languages.
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "german"; MessagesFile: "compiler:Languages\German.isl"
Name: "french"; MessagesFile: "compiler:Languages\French.isl"
Name: "ukrainian"; MessagesFile: "compiler:Languages\Ukrainian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "associateworld"; Description: "Associate .world files with WorldPainter Languages"; GroupDescription: "File associations:"; Flags: unchecked

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Registry]
; Always expose the fork in Windows "Open with" without taking over the original WorldPainter association.
Root: HKA; Subkey: "Software\Classes\Applications\{#MyAppExeName}\SupportedTypes"; ValueType: string; ValueName: ".world"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Applications\{#MyAppExeName}\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\WorldPainterLanguages.world"; ValueType: string; ValueName: ""; ValueData: "WorldPainter world"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\WorldPainterLanguages.world\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"",0"
Root: HKA; Subkey: "Software\Classes\WorldPainterLanguages.world\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\.world\OpenWithProgids"; ValueType: none; ValueName: "WorldPainterLanguages.world"; Flags: uninsdeletevalue
; Optional explicit takeover for all .world files; Windows associations cannot vary by the layers inside a file.
Root: HKA; Subkey: "Software\Classes\.world"; ValueType: string; ValueName: ""; ValueData: "WorldPainterLanguages.world"; Tasks: associateworld; Flags: uninsdeletevalue

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent
