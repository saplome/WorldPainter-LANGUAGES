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

[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$Version = '3.0.0',
    [string]$OutputDir = '',
    [switch]$VerifyOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
} else {
    $ProjectRoot = (Resolve-Path $ProjectRoot).Path
}
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $ProjectRoot 'release\github'
}

$ExcludedDirNames = @('.git', '.idea', '.vscode', '.settings', 'target', 'node_modules', '__pycache__', 'logs', 'surefire-reports', 'failsafe-reports', 'terminals', 'agent-tools', 'mcps')
# Excluded only directly under the project root. 'release' is the build output folder, but 'tools\release' holds the
# packaging scripts that have to ship: matching the segment name anywhere dropped them from every archive silently.
$ExcludedRootDirNames = @('release')
$ExcludedExtensions = @('.class', '.jar', '.exe', '.msi', '.zip', '.7z', '.rar', '.log', '.tmp', '.bak', '.orig', '.rej', '.swp', '.swo', '.pyc', '.iml')
$ExcludedFileNames = @('.DS_Store', 'Thumbs.db', 'dependency-reduced-pom.xml', '.flattened-pom.xml')
# Strings that must never reach a published source archive. The tree generator used to be listed here while it was
# unreleased work in progress; since 3.0.0 it ships as a [BETA] feature, so the only thing left to guard against
# is a leak of the build machine itself: an absolute home directory hard-coded in a script, project file or resource.
# Keep the patterns ASCII: PowerShell 5.1 reads a BOM-less .ps1 as ANSI, which would mangle anything else.
$ForbiddenPatterns = @('C:\Users\', 'c:/users/')

$script:Problems = New-Object System.Collections.Generic.List[string]

function Write-Step {
    param([string]$Message)
    Write-Host ''
    Write-Host "=== $Message" -ForegroundColor Cyan
}

function Add-Problem {
    param([string]$Message)
    $script:Problems.Add($Message) | Out-Null
    Write-Host "  PROBLEM  $Message" -ForegroundColor Red
}

function Test-Excluded {
    param([System.IO.FileInfo]$File)
    $relative = $File.FullName.Substring($ProjectRoot.Length).TrimStart('\')
    $segments = $relative.Split('\')
    if (($segments.Count -gt 1) -and ($ExcludedRootDirNames -contains $segments[0])) { return $true }
    foreach ($part in $segments) {
        if ($ExcludedDirNames -contains $part) { return $true }
    }
    if ($ExcludedFileNames -contains $File.Name) { return $true }
    if ($ExcludedExtensions -contains $File.Extension.ToLowerInvariant()) { return $true }
    if ($File.Name -like 'hs_err_pid*') { return $true }
    if ($File.Name -like 'replay_pid*') { return $true }
    if ($File.Name -like '*.releaseBackup') { return $true }
    if ($File.Name -like '*~') { return $true }
    return $false
}

function Get-PayloadFiles {
    $all = Get-ChildItem -LiteralPath $ProjectRoot -Recurse -File -Force
    $kept = New-Object System.Collections.Generic.List[System.IO.FileInfo]
    foreach ($file in $all) {
        if (Test-Excluded -File $file) { continue }
        $kept.Add($file) | Out-Null
    }
    return , $kept
}

function Get-RelativePath {
    param([System.IO.FileInfo]$File)
    return $File.FullName.Substring($ProjectRoot.Length).TrimStart('\')
}

function Invoke-Verification {
    param($Files)

    Write-Step 'Verifying the payload'
    Write-Host "  files to publish: $($Files.Count)"

    if ($Files.Count -eq 0) { Add-Problem 'no files matched'; return }

    foreach ($file in $Files) {
        $relative = Get-RelativePath -File $file
        # 'release' counts as build output only at the root; tools\release is source that must ship.
        if (($relative -match '(?i)^release(\\|$)') -or ($relative -match '(?i)(^|\\)target(\\|$)')) {
            Add-Problem "build output leaked into the payload: $relative"
        }
    }

    $textExtensions = @('.java', '.properties', '.xml', '.iss', '.ps1', '.py', '.tsv', '.form')
    $scanExceptions = @('pack-release-archives.ps1')
    foreach ($file in $Files) {
        if ($textExtensions -notcontains $file.Extension.ToLowerInvariant()) { continue }
        if ($scanExceptions -contains $file.Name) { continue }
        $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
        if ($null -eq $content) { continue }
        foreach ($pattern in $ForbiddenPatterns) {
            if ($content -like "*$pattern*") {
                Add-Problem "forbidden reference '$pattern' in $(Get-RelativePath -File $file)"
                break
            }
        }
    }

    $expectedVersionFiles = @('README.md', 'tools\windows-packaging\installer.iss', 'tools\windows-packaging\build-windows-installer.ps1')
    foreach ($relative in $expectedVersionFiles) {
        $path = Join-Path $ProjectRoot $relative
        if (-not (Test-Path -LiteralPath $path)) {
            Add-Problem "missing file: $relative"
            continue
        }
        $content = Get-Content -LiteralPath $path -Raw
        if ($content -notlike "*$Version*") {
            Add-Problem "version $Version not found in $relative"
        }
    }

    $notes = Join-Path $ProjectRoot "docs\RELEASE_NOTES_$Version.md"
    if (-not (Test-Path -LiteralPath $notes)) {
        Add-Problem "missing release notes: docs\RELEASE_NOTES_$Version.md"
    }

    if ($script:Problems.Count -eq 0) {
        Write-Host '  verification passed' -ForegroundColor Green
    }
}

function New-Archive {
    param(
        [string]$Path,
        $Files,
        [scriptblock]$Filter
    )

    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
    Add-Type -AssemblyName System.IO.Compression | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

    $zip = [System.IO.Compression.ZipFile]::Open($Path, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        $count = 0
        foreach ($file in $Files) {
            $relative = Get-RelativePath -File $file
            if ($Filter -and -not (& $Filter $relative)) { continue }
            $entryName = $relative.Replace('\', '/')
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $entryName, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
            $count++
        }
    } finally {
        $zip.Dispose()
    }

    $size = (Get-Item -LiteralPath $Path).Length
    $hash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host ("  {0}" -f (Split-Path -Leaf $Path))
    Write-Host ("    entries : {0}" -f $count)
    Write-Host ("    size    : {0} bytes" -f $size)
    Write-Host ("    sha256  : {0}" -f $hash)
}

Write-Step 'Collecting the payload'
Write-Host "  project root : $ProjectRoot"
Write-Host "  version      : $Version"
$files = Get-PayloadFiles

Invoke-Verification -Files $files

if ($script:Problems.Count -gt 0) {
    Write-Host ''
    Write-Host "FAILED: $($script:Problems.Count) problem(s); nothing was packed." -ForegroundColor Red
    exit 1
}

if ($VerifyOnly) {
    Write-Host ''
    Write-Host 'Verification only; no archives were created.' -ForegroundColor Green
    exit 0
}

if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

Write-Step 'Packing the archives'
New-Archive -Path (Join-Path $OutputDir "WorldPainter-Languages-$Version-github-ready.zip") -Files $files -Filter { param($relative) $true }
New-Archive -Path (Join-Path $OutputDir "WorldPainter-Languages-$Version.zip") -Files $files -Filter { param($relative) -not $relative.StartsWith('tools\') }
New-Archive -Path (Join-Path $OutputDir "WorldPainter-Languages-$Version-release-tools.zip") -Files $files -Filter { param($relative) $relative.StartsWith('tools\') }

Write-Host ''
Write-Host "Done. Archives are in $OutputDir" -ForegroundColor Green
