# This file is part of WorldPainter Languages, an unofficial localization fork of
# WorldPainter (https://github.com/saplome/WorldPainter-LANGUAGES).
#
# Copyright (C) 2026 saplome. Written in 2026 for WorldPainter Languages; the original
# project ships no such script. Kept ASCII on purpose: PowerShell 5.1 reads a .ps1 without
# a byte order mark using the ANSI code page, which mangles non-ASCII characters.
#
# Licensed under the GNU General Public License, version 3, the same licence as the
# application it builds. See the LICENSE file for details.

#requires -version 5.1

<#
.SYNOPSIS
    Verifies the release output and collects everything that has to be uploaded to GitHub.

.DESCRIPTION
    Checks that the update manifest and the CDN payload describe exactly the same bytes, that the
    installer and the portable archive exist, and that the release notes are in place. Then copies
    the release assets into release\upload\release-assets and prints the commands for publishing.

    Run it after:
      tools\windows-packaging\build-windows-installer.ps1 -BuildPortable -BuildInnoInstaller -GenerateUpdateManifest
#>

[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [switch]$VerifyOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Problems = New-Object System.Collections.Generic.List[string]

function Write-Section {
    param([string]$Message)
    Write-Host ''
    Write-Host "=== $Message" -ForegroundColor Cyan
}

function Write-Ok {
    param([string]$Message)
    Write-Host "  ok    $Message" -ForegroundColor Green
}

function Write-Problem {
    param([string]$Message)
    Write-Host "  FAIL  $Message" -ForegroundColor Red
    $script:Problems.Add($Message)
}

function Get-ScriptValue {
    param(
        [string]$Path,
        [string]$Name
    )

    $pattern = '^\$' + [regex]::Escape($Name) + "\s*=\s*'([^']+)'"
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        $match = [regex]::Match($line, $pattern)
        if ($match.Success) { return $match.Groups[1].Value }
    }
    throw "Could not read `$$Name from $Path"
}

function Get-ParamValue {
    param(
        [string]$Path,
        [string]$Name
    )

    $pattern = '^\s*\[string\]\$' + [regex]::Escape($Name) + "\s*=\s*'([^']+)'"
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        $match = [regex]::Match($line, $pattern)
        if ($match.Success) { return $match.Groups[1].Value }
    }
    throw "Could not read parameter `$$Name from $Path"
}

function Read-Manifest {
    param([string]$Path)

    $files = New-Object System.Collections.Generic.List[object]
    $deletes = New-Object System.Collections.Generic.List[string]
    $version = ''
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        if ($line.StartsWith('#') -or ($line.Trim() -eq '')) { continue }
        if ($line.StartsWith('version=')) { $version = $line.Substring(8); continue }
        if ($line.StartsWith('delete=')) { $deletes.Add($line.Substring(7)); continue }
        if (-not $line.StartsWith('file=')) { continue }
        $fields = $line.Substring(5) -split "`t"
        if ($fields.Count -ne 4) {
            Write-Problem "malformed manifest line: $line"
            continue
        }
        $files.Add([pscustomobject]@{
            Sha256 = $fields[0]
            Size   = [long]$fields[1]
            Path   = $fields[2]
            Url    = $fields[3]
        })
    }
    return [pscustomobject]@{ Version = $version; Files = $files; Deletes = $deletes }
}

function ConvertTo-BashPath {
    param([string]$Path)

    $slashed = $Path -replace '\\', '/'
    $match = [regex]::Match($slashed, '^([A-Za-z]):/(.*)$')
    if ($match.Success) {
        return '/' + $match.Groups[1].Value.ToLowerInvariant() + '/' + $match.Groups[2].Value
    }
    return $slashed
}

function Format-BashSingleQuoted {
    param([string]$Value)
    # 'it'\''s' is the only way to get a quote into a single-quoted shell word.
    return "'" + ($Value -replace "'", "'\''") + "'"
}

function Write-BashScript {
    param(
        [string]$Path,
        [string[]]$Lines
    )

    # LF only, no BOM: bash on Windows chokes on a CR at the end of "set -euo pipefail".
    $text = ($Lines -join "`n") + "`n"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $text, $utf8NoBom)
    Write-Host "  + $(Split-Path -Leaf $Path)"
}

if (-not $ProjectRoot) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path

$buildScript = Join-Path $ProjectRoot 'tools\windows-packaging\build-windows-installer.ps1'
if (-not (Test-Path -LiteralPath $buildScript)) {
    throw "Build script not found: $buildScript"
}
$productVersion = Get-ScriptValue -Path $buildScript -Name 'ProductVersion'
$gitHubRepository = Get-ScriptValue -Path $buildScript -Name 'GitHubRepository'
$updateBaseUrl = (Get-ParamValue -Path $buildScript -Name 'UpdateBaseUrl').TrimEnd('/')
$releaseTag = Get-ParamValue -Path $buildScript -Name 'ReleaseTag'
$cdnRepository = ($updateBaseUrl -replace '^https://raw\.githubusercontent\.com/', '' -split '/')[0..1] -join '/'

$releaseDir = Join-Path $ProjectRoot 'release'
$cdnDir = Join-Path $releaseDir 'cdn'
$cdnAppDir = Join-Path $cdnDir 'app'
$manifestPath = Join-Path $releaseDir 'update-manifest.txt'
$installerPath = Join-Path $releaseDir "installer\WorldPainter-Languages-$productVersion-Setup.exe"
$portablePath = Join-Path $releaseDir "WorldPainter-Languages-$productVersion-Portable.zip"
$notesPath = Join-Path $ProjectRoot "docs\RELEASE_NOTES_$productVersion.md"
$uploadDir = Join-Path $releaseDir 'upload'
$assetsDir = Join-Path $uploadDir 'release-assets'

Write-Host "Product version : $productVersion"
Write-Host "Main repository : $gitHubRepository"
Write-Host "CDN repository  : $cdnRepository"
Write-Host "Release tag     : $releaseTag"

Write-Section 'Release artifacts'
foreach ($item in @(
    @{ Path = $manifestPath;   Label = 'update manifest' },
    @{ Path = $installerPath;  Label = 'Inno Setup installer' },
    @{ Path = $portablePath;   Label = 'portable archive' },
    @{ Path = $notesPath;      Label = 'release notes' }
)) {
    if (Test-Path -LiteralPath $item.Path -PathType Leaf) {
        $size = (Get-Item -LiteralPath $item.Path).Length
        Write-Ok "$($item.Label): $(Split-Path -Leaf $item.Path) ($([math]::Round($size / 1MB, 2)) MB)"
    } else {
        Write-Problem "$($item.Label) is missing: $($item.Path)"
    }
}

Write-Section 'CDN payload'
if (-not (Test-Path -LiteralPath $cdnAppDir -PathType Container)) {
    Write-Problem "CDN payload is missing: $cdnAppDir (run the build with -GenerateUpdateManifest)"
} else {
    Write-Ok "app directory: $cdnAppDir"
}
$gitAttributesPath = Join-Path $cdnDir '.gitattributes'
if (Test-Path -LiteralPath $gitAttributesPath -PathType Leaf) {
    if ((Get-Content -LiteralPath $gitAttributesPath) -match '^\*\s+-text') {
        Write-Ok '.gitattributes disables end-of-line conversion (* -text)'
    } else {
        Write-Problem ".gitattributes exists but has no '* -text' rule; git would rewrite CRLF and break every hash"
    }
} else {
    Write-Problem "missing $gitAttributesPath ('* -text' is required, otherwise git rewrites CRLF)"
}

Write-Section 'Manifest versus CDN payload'
if ((Test-Path -LiteralPath $manifestPath -PathType Leaf) -and (Test-Path -LiteralPath $cdnAppDir -PathType Container)) {
    $manifest = Read-Manifest -Path $manifestPath
    if ($manifest.Version -ne $productVersion) {
        Write-Problem "manifest version is '$($manifest.Version)', expected '$productVersion'"
    } else {
        Write-Ok "manifest version: $($manifest.Version)"
    }

    $cdnFullPath = [System.IO.Path]::GetFullPath($cdnAppDir)
    $covered = @{}
    $mismatches = 0
    foreach ($entry in $manifest.Files) {
        $covered[$entry.Path] = $true
        $local = Join-Path $cdnAppDir ($entry.Path -replace '/', '\')
        if (-not (Test-Path -LiteralPath $local -PathType Leaf)) {
            Write-Problem "manifest lists $($entry.Path), but the CDN payload does not contain it"
            $mismatches++
            continue
        }
        $file = Get-Item -LiteralPath $local
        if ($file.Length -ne $entry.Size) {
            Write-Problem "$($entry.Path): manifest says $($entry.Size) bytes, payload has $($file.Length)"
            $mismatches++
            continue
        }
        $hash = (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $entry.Sha256.ToLowerInvariant()) {
            Write-Problem "$($entry.Path): SHA-256 differs between the manifest and the payload"
            $mismatches++
            continue
        }
        $expectedUrl = "$updateBaseUrl/" + ((($entry.Path -split '/') | ForEach-Object { [uri]::EscapeDataString($_) }) -join '/')
        if ($entry.Url -ne $expectedUrl) {
            Write-Problem "$($entry.Path): URL is $($entry.Url), expected $expectedUrl"
            $mismatches++
        }
    }
    if ($mismatches -eq 0) {
        Write-Ok "$($manifest.Files.Count) file(s) verified: size, SHA-256 and URL all match the payload"
    }

    $uncovered = @(Get-ChildItem -LiteralPath $cdnAppDir -Recurse -File | ForEach-Object {
        $_.FullName.Substring($cdnFullPath.Length + 1) -replace '\\', '/'
    } | Where-Object { -not $covered.ContainsKey($_) })
    if ($uncovered.Count -gt 0) {
        Write-Problem "the payload contains $($uncovered.Count) file(s) that the manifest does not describe: $($uncovered -join ', ')"
    } else {
        Write-Ok 'every file of the payload is described by the manifest'
    }

    if ($manifest.Deletes.Count -gt 0) {
        Write-Host "  note  $($manifest.Deletes.Count) obsolete file(s) will be deleted on update:" -ForegroundColor DarkGray
        foreach ($path in $manifest.Deletes) { Write-Host "          - $path" -ForegroundColor DarkGray }
    }
    foreach ($path in $manifest.Deletes) {
        if ($covered.ContainsKey($path)) {
            Write-Problem "$path is listed both as a file and as a deletion"
        }
        if ($path -like 'wp-updater*.jar') {
            Write-Problem "$path is an updater jar; deleting it always fails because that jar performs the update"
        }
    }
}

Write-Section 'Client configuration inside the payload'
$clientConfig = Join-Path $cdnAppDir 'updater.properties'
if (Test-Path -LiteralPath $clientConfig -PathType Leaf) {
    $configuredUrl = ((Get-Content -LiteralPath $clientConfig) | Where-Object { $_ -like 'manifestUrl=*' }) -replace '^manifestUrl=', ''
    $expectedManifestUrl = Get-ParamValue -Path $buildScript -Name 'UpdateManifestUrl'
    if ($configuredUrl -eq $expectedManifestUrl) {
        Write-Ok "updater.properties points at $configuredUrl"
    } else {
        Write-Problem "updater.properties points at '$configuredUrl', expected '$expectedManifestUrl'"
    }
} else {
    Write-Problem "updater.properties is missing from the payload: $clientConfig"
}

if ($script:Problems.Count -gt 0) {
    Write-Host ''
    Write-Host "PROBLEMS: $($script:Problems.Count) - nothing was staged for upload." -ForegroundColor Red
    foreach ($problem in $script:Problems) { Write-Host "  - $problem" -ForegroundColor Red }
    exit 1
}

Write-Host ''
Write-Host 'All checks passed.' -ForegroundColor Green

if (-not $VerifyOnly) {
    Write-Section 'Staging the release assets'
    if (Test-Path -LiteralPath $assetsDir) { Remove-Item -LiteralPath $assetsDir -Recurse -Force }
    New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null
    foreach ($path in @($installerPath, $portablePath, $manifestPath)) {
        Copy-Item -LiteralPath $path -Destination $assetsDir -Force
        Write-Host "  + $(Split-Path -Leaf $path)"
    }
    $sourceArchives = @(Get-ChildItem -LiteralPath (Join-Path $releaseDir 'github') -Filter '*.zip' -File -ErrorAction SilentlyContinue)
    foreach ($archive in $sourceArchives) {
        Copy-Item -LiteralPath $archive.FullName -Destination $assetsDir -Force
        Write-Host "  + $($archive.Name)"
    }
    if ($sourceArchives.Count -eq 0) {
        Write-Host '  (no source archives; run tools\release\pack-release-archives.ps1 if the release should contain them)' -ForegroundColor DarkGray
    }
    Write-Host "  staged in: $assetsDir"
}

if ($VerifyOnly) {
    Write-Host ''
    Write-Host 'Verify-only run: nothing was staged and no publish scripts were generated.' -ForegroundColor Yellow
    exit 0
}

Write-Section 'Generating the publish scripts'
Write-BashScript -Path (Join-Path $uploadDir 'publish-step1-cdn.sh') -Lines @(
    '#!/usr/bin/env bash'
    "# Step 1 of 2: push the application files of $productVersion to the update CDN."
    '# Generated by tools/release/prepare-github-upload.ps1 - regenerate it instead of editing it.'
    'set -euo pipefail'
    ''
    "CDN_DIR=$(Format-BashSingleQuoted (ConvertTo-BashPath $cdnDir))"
    "REPO=$(Format-BashSingleQuoted $cdnRepository)"
    "VERSION=$(Format-BashSingleQuoted $productVersion)"
    ''
    'cd "$CDN_DIR"'
    ''
    'if ! gh repo view "$REPO" >/dev/null 2>&1; then'
    '    echo "== creating https://github.com/$REPO"'
    '    gh repo create "$REPO" --public --description "Update host for WorldPainter Languages"'
    'fi'
    ''
    'if [ ! -d .git ]; then'
    '    echo "== first push into $REPO"'
    '    git init -b main'
    '    git remote add origin "https://github.com/$REPO.git"'
    'fi'
    ''
    '# The updater compares the SHA-256 of the bytes GitHub serves, so git must not rewrite a single'
    '# line ending. .gitattributes says "* -text"; this makes it impossible to lose that by accident.'
    'git config core.autocrlf false'
    'git config core.safecrlf false'
    ''
    'git add -A'
    'if git rev-parse --verify HEAD >/dev/null 2>&1 && git diff --cached --quiet; then'
    '    echo "== nothing to commit: the CDN already matches this build"'
    'else'
    '    git commit -m "WorldPainter Languages $VERSION app files"'
    'fi'
    'git push -u origin main'
    ''
    'echo'
    'echo "Pushed. Give raw.githubusercontent.com a few seconds, then run:"'
    'echo "  ./verify-cdn.sh"'
)
$verifyHeader = @(
    '#!/usr/bin/env bash'
    "# Downloads every application file of $productVersion from the CDN and compares it with the manifest."
    '# Run it between publish-step1-cdn.sh and publish-step2-release.sh.'
    '# Generated by tools/release/prepare-github-upload.ps1 - regenerate it instead of editing it.'
    'set -uo pipefail'
    ''
    'tmp="$(mktemp -d)"'
    'trap ''rm -rf "$tmp"'' EXIT'
    ''
    'fail=0'
    'total=0'
    ''
    'check() {'
    '    local sha="$1" size="$2" path="$3" url="$4"'
    '    local out="$tmp/payload.bin"'
    '    local code actual_size actual_sha'
    '    total=$((total + 1))'
    '    code=$(curl -sSL -o "$out" -w "%{http_code}" "$url" || echo 000)'
    '    if [ "$code" != "200" ]; then'
    '        echo "  FAIL  HTTP $code - $path"'
    '        fail=$((fail + 1))'
    '        return'
    '    fi'
    '    actual_size=$(wc -c < "$out" | tr -d " ")'
    '    actual_sha=$(sha256sum "$out" | cut -d" " -f1)'
    '    if [ "$actual_size" != "$size" ]; then'
    '        echo "  FAIL  $actual_size bytes instead of $size - $path"'
    '        fail=$((fail + 1))'
    '        return'
    '    fi'
    '    if [ "$actual_sha" != "$sha" ]; then'
    '        echo "  FAIL  SHA-256 mismatch - $path"'
    '        fail=$((fail + 1))'
    '        return'
    '    fi'
    '    echo "  ok    $path"'
    '}'
    ''
    "echo ""== $($manifest.Files.Count) file(s) from $updateBaseUrl"""
    ''
)
$checkLines = @($manifest.Files | ForEach-Object {
    'check {0} {1} {2} {3}' -f
        (Format-BashSingleQuoted $_.Sha256),
        (Format-BashSingleQuoted ([string]$_.Size)),
        (Format-BashSingleQuoted $_.Path),
        (Format-BashSingleQuoted $_.Url)
})
$verifyFooter = @(
    ''
    'echo'
    'if [ "$fail" -ne 0 ]; then'
    '    echo "FAILED: $fail of $total file(s) differ from the manifest. Do not publish the release yet."'
    '    exit 1'
    'fi'
    'echo "All $total file(s) match the manifest byte for byte."'
    'echo "The CDN is live; now run: ./publish-step2-release.sh"'
)
Write-BashScript -Path (Join-Path $uploadDir 'verify-cdn.sh') -Lines ($verifyHeader + $checkLines + $verifyFooter)
$assetLines = @(Get-ChildItem -LiteralPath $assetsDir -File | Sort-Object Name | ForEach-Object {
    '    ' + (Format-BashSingleQuoted (ConvertTo-BashPath $_.FullName)) + ' \'
})
$assetLines[-1] = $assetLines[-1].TrimEnd(' \')
Write-BashScript -Path (Join-Path $uploadDir 'publish-step2-release.sh') -Lines (@(
    '#!/usr/bin/env bash'
    "# Step 2 of 2: publish release $releaseTag in $gitHubRepository."
    '# Usage: ./publish-step2-release.sh [--draft]'
    '# Run it only after publish-step1-cdn.sh and verify-cdn.sh have both succeeded.'
    '# Generated by tools/release/prepare-github-upload.ps1 - regenerate it instead of editing it.'
    'set -euo pipefail'
    ''
    "REPO=$(Format-BashSingleQuoted $gitHubRepository)"
    "TAG=$(Format-BashSingleQuoted $releaseTag)"
    "TITLE=$(Format-BashSingleQuoted "WorldPainter Languages $productVersion")"
    "NOTES=$(Format-BashSingleQuoted (ConvertTo-BashPath $notesPath))"
    "PROBE=$(Format-BashSingleQuoted "$updateBaseUrl/updater.properties")"
    "SRC=$(Format-BashSingleQuoted (ConvertTo-BashPath $ProjectRoot))"
    ''
    '# releases/latest/download switches over the moment this command returns, so a release whose'
    '# manifest points at files that are not on the CDN yet would break every update it reaches.'
    'code=$(curl -s -o /dev/null -w "%{http_code}" "$PROBE" || echo 000)'
    'if [ "$code" != "200" ]; then'
    '    echo "The CDN does not serve $PROBE yet (HTTP $code)."'
    '    echo "Run ./publish-step1-cdn.sh first, then ./verify-cdn.sh."'
    '    exit 1'
    'fi'
    ''
    '# Without --target the tag is cut from whatever the default branch currently points at, so a'
    '# release published before the sources are pushed ships "Source code" archives of older code. The'
    '# manifest and the binaries would still be correct, which is what makes it easy to miss - so the'
    '# commit that produced this build has to be on GitHub before the tag exists.'
    'head=$(git -C "$SRC" rev-parse HEAD)'
    'remote=$(git -C "$SRC" ls-remote "https://github.com/$REPO.git" HEAD | cut -f1)'
    'if [ "$head" != "$remote" ]; then'
    '    echo "The sources of this build are not the ones on GitHub."'
    '    echo "  local HEAD:  $head"'
    '    echo "  remote HEAD: ${remote:-<the repository has no commits>}"'
    '    echo "Push them first, so that $TAG points at the code it ships:"'
    '    echo "  git -C \"$SRC\" push origin HEAD"'
    '    exit 1'
    'fi'
    ''
    '# --draft keeps releases/latest on the previous release, and creates no tag, until you press'
    '# Publish by hand: the update channel does not switch over while you look the release over.'
    'DRAFT=""'
    'if [ "${1:-}" = "--draft" ]; then'
    '    DRAFT="--draft"'
    '    shift'
    'fi'
    ''
    'gh release create "$TAG" \'
    '    --repo "$REPO" \'
    '    --target "$head" \'
    '    --title "$TITLE" \'
    '    --notes-file "$NOTES" \'
    '    ${DRAFT:+"$DRAFT"} \'
) + $assetLines + @(
    ''
    'echo'
    'if [ -n "$DRAFT" ]; then'
    '    echo "Draft created: https://github.com/$REPO/releases"'
    '    echo "Nobody else can see it and $TAG does not exist yet; both happen when you press Publish."'
    '    echo "After publishing, check:"'
    'else'
    '    echo "Published: https://github.com/$REPO/releases/tag/$TAG"'
    '    echo "Final check:"'
    'fi'
    'echo "  curl -sI https://github.com/$REPO/releases/latest/download/update-manifest.txt | head -1"'
))
$cdnFileCount = $manifest.Files.Count
$cdnSizeMb = [math]::Round((($manifest.Files | Measure-Object -Property Size -Sum).Sum) / 1MB, 2)
$stagedAssets = @(Get-ChildItem -LiteralPath $assetsDir -File | Sort-Object Name)
# "MB" in Cyrillic, spelled out in code points so that this file can stay pure ASCII (see below).
$mbUnit = [string][char]0x041C + [string][char]0x0411
$assetTable = (($stagedAssets | ForEach-Object {
    '- `' + $_.Name + '` - ' + [math]::Round($_.Length / 1MB, 2) + ' ' + $mbUnit
}) -join "`n")

# The instruction sheet is in Russian, so it lives in its own UTF-8 template: PowerShell 5.1 reads a
# BOM-less .ps1 as ANSI, which would mangle any Cyrillic kept in this file.
$templatePath = Join-Path $PSScriptRoot 'UPLOAD_RU.template.md'
if (-not (Test-Path -LiteralPath $templatePath -PathType Leaf)) {
    throw "Instruction sheet template not found: $templatePath"
}
$sheet = [System.IO.File]::ReadAllText($templatePath, [System.Text.Encoding]::UTF8)
foreach ($token in @(
    @{ Name = '@@VERSION@@';     Value = $productVersion },
    @{ Name = '@@TAG@@';         Value = $releaseTag },
    @{ Name = '@@MAIN_REPO@@';   Value = $gitHubRepository },
    @{ Name = '@@CDN_REPO@@';    Value = $cdnRepository },
    @{ Name = '@@BASE_URL@@';    Value = $updateBaseUrl },
    @{ Name = '@@UPLOAD_DIR@@';  Value = (ConvertTo-BashPath $uploadDir) },
    @{ Name = '@@CDN_DIR@@';     Value = (ConvertTo-BashPath $cdnDir) },
    @{ Name = '@@SRC_DIR@@';     Value = (ConvertTo-BashPath $ProjectRoot) },
    @{ Name = '@@FILE_COUNT@@';  Value = [string]$cdnFileCount },
    @{ Name = '@@PAYLOAD_MB@@';  Value = [string]$cdnSizeMb },
    @{ Name = '@@NOTES_FILE@@';  Value = "docs/RELEASE_NOTES_$productVersion.md" },
    @{ Name = '@@ASSET_TABLE@@'; Value = $assetTable }
)) {
    $sheet = $sheet.Replace($token.Name, $token.Value)
}
$leftover = @([regex]::Matches($sheet, '@@[A-Z_]+@@') | ForEach-Object { $_.Value } | Sort-Object -Unique)
if ($leftover.Count -gt 0) {
    throw "The template still contains unsubstituted token(s): $($leftover -join ', ')"
}
$sheetPath = Join-Path $uploadDir 'UPLOAD_RU.md'
[System.IO.File]::WriteAllText($sheetPath, $sheet, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "  + $(Split-Path -Leaf $sheetPath)"

Write-Section 'Ready to upload'
Write-Host "  Everything to publish is in: $uploadDir"
Write-Host ''
Write-Host '  Read this first:' -ForegroundColor Yellow
Write-Host "    $(Join-Path $uploadDir 'UPLOAD_RU.md')"
Write-Host ''
Write-Host '  Then, in Git Bash:' -ForegroundColor Yellow
Write-Host "    cd $(ConvertTo-BashPath $uploadDir)"
Write-Host '    ./publish-step1-cdn.sh        # application files -> CDN repository'
Write-Host '    ./verify-cdn.sh               # proves the CDN serves the manifest bytes'
Write-Host '    ./publish-step2-release.sh    # tag + release + assets'
Write-Host ''
Write-Host 'Details: docs\UPDATE_CHANNEL_RU.md'
exit 0
