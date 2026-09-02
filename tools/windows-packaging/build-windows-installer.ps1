#requires -version 5.1

[CmdletBinding()]
param(
    [switch]$SkipMaven,
    [switch]$BuildInstaller,
    [switch]$BuildAppImage,
    [switch]$BuildPortable,
    [switch]$BuildInnoInstaller,
    [switch]$CreateDraftRelease,
    [switch]$OpenDraftRelease,
    [string]$ReleaseTag = 'L3.0.0',
    [string]$ReleaseTitle = 'WorldPainter Languages 3.0.0',
    [string]$ReleaseNotesFile = '',
    [string[]]$AdditionalDraftAsset = @(),
    [switch]$GenerateUpdateManifest,
    [string]$UpdateManifestUrl = 'https://github.com/saplome/WorldPainter-LANGUAGES/releases/latest/download/update-manifest.txt',
    [string]$UpdateBaseUrl = 'https://raw.githubusercontent.com/saplome/WorldPainter-LANGUAGES-cdn/main/app',
    # app directory of the previous release. Files that exist there but not in this build become delete= entries in the
    # manifest, so installations of the previous release do not keep obsolete jars forever. Defaults to the installed
    # copy, which is exactly what the update has to clean up.
    [string]$PreviousAppDir = 'C:\Program Files\WorldPainter Languages\app'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProductName = 'WorldPainter Languages'
$ProductVersion = '3.0.0'
# Windows wants file/product versions as plain numbers, so this is simply $ProductVersion with a fourth component.
# The release tag is 'L' + $ProductVersion: builds up to 2.27.0-L2.0.1 only ever looked for L followed by a number,
# and without that letter they would never report a new release. The number itself continues the same line those
# builds used - L1, L2.0.0, L2.0.1, then 3.0.0 - so they compare it correctly. Keep it monotonic across releases.
$WindowsInstallerVersion = '3.0.0.0'
$WindowsUpgradeUuid = 'd5984a7f-cb32-48c8-b6f1-97a3c4c0da44'
$ProductVendor = 'WorldPainter Languages'
$ProductDescription = 'WorldPainter Languages'
$MavenVersion = '2.27.1'
$MainClass = 'org.pepsoft.worldpainter.Main'
$UpdaterMainClass = 'org.pepsoft.worldpainter.updater.WPUpdater'
$JPackageEnabled = [bool]$BuildInstaller

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir '..\..')).Path
$JPackageResourceDir = Join-Path $ScriptDir 'jpackage-resources\windows'
$IconIcoPath = Join-Path $ProjectRoot 'assets\icon.ico'
$ReleaseDir = Join-Path $ProjectRoot 'release'
$StagingDir = Join-Path $ReleaseDir 'staging'
$AppDir = Join-Path $StagingDir 'app'
$LibDir = Join-Path $AppDir 'lib'
$InstallerDir = Join-Path $ReleaseDir 'installer'
$InstallerWorkDir = Join-Path $ReleaseDir 'installer-work'
$AppImageDir = Join-Path $ReleaseDir 'app-image'
$PortableZipPath = Join-Path $ReleaseDir "WorldPainter-Languages-$ProductVersion-Portable.zip"
$GitHubRepository = 'saplome/WorldPainter-LANGUAGES'
$DefaultReleaseNotesPath = Join-Path $ProjectRoot "docs\RELEASE_NOTES_$ProductVersion.md"
$LogsDir = Join-Path $ReleaseDir 'logs'
$UpdaterSourcePath = Join-Path $ProjectRoot 'tools\updater\WPUpdater.java'
$UpdateLauncherPropsPath = Join-Path $StagingDir 'update-launcher.properties'
$UpdateManifestPath = Join-Path $ReleaseDir 'update-manifest.txt'
$CdnDir = Join-Path $ReleaseDir 'cdn'
# The updater jar carries the release version in its name. The running updater holds its own jar open, so a fixed name
# could never be replaced by the updater itself; a versioned name arrives as a new file and the launcher .cfg (which is
# part of the update) switches over to it.
$UpdaterJarName = "wp-updater-$ProductVersion.jar"

function Fail {
    param([string]$Message)

    [Console]::Error.WriteLine("ERROR: $Message")
    exit 1
}

function Test-Tool {
    param(
        [string]$Name,
        [string]$Hint,
        [switch]$Required
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue

    if ($command) {
        Write-Host "Found ${Name}: $($command.Source)"
        return $true
    }

    $message = "Required tool not found: '$Name'. $Hint"
    if ($Required) {
        Fail $message
    }

    Write-Warning $message
    return $false
}

function Ensure-CleanDirectory {
    param([string]$Path)

    $root = [System.IO.Path]::GetFullPath($ProjectRoot)
    $fullPath = [System.IO.Path]::GetFullPath($Path)

    if (-not $fullPath.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        Fail "Refusing to clean a directory outside the project: $fullPath"
    }

    if (Test-Path -LiteralPath $fullPath) {
        Remove-Item -LiteralPath $fullPath -Recurse -Force
    }

    New-Item -ItemType Directory -Path $fullPath -Force | Out-Null
}

function Invoke-Checked {
    param(
        [string]$Tool,
        [string[]]$Arguments
    )

    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) {
        Fail "Command failed: $Tool $($Arguments -join ' ')"
    }
}

function Find-ModuleJar {
    param(
        [string]$ModuleName,
        [string]$ExpectedFileName
    )

    $jarPath = Join-Path (Join-Path $ProjectRoot $ModuleName) "target\$ExpectedFileName"

    if (-not (Test-Path -LiteralPath $jarPath)) {
        Fail "Module jar was not found: $jarPath. Expected it after 'mvn clean package'."
    }

    return (Resolve-Path $jarPath).Path
}

function Copy-ModuleJar {
    param(
        [string]$ModuleName,
        [string]$ExpectedFileName
    )

    $jar = Find-ModuleJar $ModuleName $ExpectedFileName
    Copy-Item -LiteralPath $jar -Destination $AppDir -Force
    Write-Host "Copied module jar: $jar"
    return (Join-Path $AppDir $ExpectedFileName)
}

function Copy-RuntimeDependencies {
    Write-Host "Copying WPGUI runtime dependencies to: $LibDir"

    Push-Location $ProjectRoot
    try {
        Invoke-Checked 'mvn' @(
            '-pl', 'WPGUI',
            'dependency:copy-dependencies',
            '-DincludeScope=runtime',
            '-DexcludeArtifactIds=WPCore,WPGUI,WPDynmapPreviewer',
            "-DoutputDirectory=$LibDir"
        )
    } finally {
        Pop-Location
    }

    $dependencyJars = @(Get-ChildItem -LiteralPath $LibDir -Filter '*.jar' -File -ErrorAction SilentlyContinue)
    if ($dependencyJars.Count -eq 0) {
        Fail "No runtime dependency jars were copied to $LibDir."
    }

    Write-Host "Runtime dependency jars copied: $($dependencyJars.Count)"
}

function New-ManifestAttributeLines {
    param(
        [string]$Name,
        [string]$Value
    )

    $firstLineLimit = 70
    $continuationPayloadLimit = 69
    $remaining = "${Name}: $Value"
    $lines = @()

    if ($remaining.Length -le $firstLineLimit) {
        return @($remaining)
    }

    $lines += $remaining.Substring(0, $firstLineLimit)
    $remaining = $remaining.Substring($firstLineLimit)

    while ($remaining.Length -gt $continuationPayloadLimit) {
        $lines += " $($remaining.Substring(0, $continuationPayloadLimit))"
        $remaining = $remaining.Substring($continuationPayloadLimit)
    }

    if ($remaining.Length -gt 0) {
        $lines += " $remaining"
    }

    return $lines
}

function Update-GuiJarManifest {
    param([string]$GuiJarPath)

    $classPathEntries = @(
        "WPCore-$MavenVersion.jar",
        "WPDynmapPreviewer-$MavenVersion.jar"
    )

    $classPathEntries += @(Get-ChildItem -LiteralPath $LibDir -Filter '*.jar' -File | Sort-Object Name | ForEach-Object {
        "lib/$($_.Name)"
    })

    $manifestPath = Join-Path $StagingDir 'jpackage-manifest.mf'
    $manifestLines = @("Main-Class: $MainClass")
    $manifestLines += New-ManifestAttributeLines 'Class-Path' ($classPathEntries -join ' ')
    $manifestLines += ''

    Set-Content -LiteralPath $manifestPath -Value $manifestLines -Encoding ASCII
    Invoke-Checked 'jar' @('umf', $manifestPath, $GuiJarPath)

    Write-Host "Updated staged GUI jar manifest with Main-Class and Class-Path."
}

function New-UpdaterStaging {
    if (-not (Test-Path -LiteralPath $UpdaterSourcePath)) {
        Fail "Updater source was not found: $UpdaterSourcePath"
    }

    $updaterClassesDir = Join-Path $StagingDir 'updater-classes'
    Ensure-CleanDirectory $updaterClassesDir

    Write-Host "Compiling updater: $UpdaterSourcePath"
    Invoke-Checked 'javac' @('--release', '17', '-d', $updaterClassesDir, $UpdaterSourcePath)

    $updaterJarPath = Join-Path $AppDir $UpdaterJarName
    if (Test-Path -LiteralPath $updaterJarPath) {
        Remove-Item -LiteralPath $updaterJarPath -Force
    }
    Invoke-Checked 'jar' @('cfe', $updaterJarPath, $UpdaterMainClass, '-C', $updaterClassesDir, '.')

    @(
        "# Configuration for $UpdaterJarName (WorldPainter-Update.exe)"
        "manifestUrl=$UpdateManifestUrl"
        "launch=../$ProductName.exe"
    ) | Set-Content -LiteralPath (Join-Path $AppDir 'updater.properties') -Encoding ascii

    @(
        "main-jar=$UpdaterJarName"
        "main-class=$UpdaterMainClass"
        'win-console=true'
        'win-shortcut=false'
    ) | Set-Content -LiteralPath $UpdateLauncherPropsPath -Encoding ascii

    Write-Host "Updater staged: $updaterJarPath"
}

function Get-RelativeFileMap {
    param([string]$Directory)

    $map = @{}
    $prefixLength = $Directory.TrimEnd('\').Length + 1
    foreach ($file in Get-ChildItem -LiteralPath $Directory -Recurse -File -ErrorAction Stop) {
        $relative = $file.FullName.Substring($prefixLength) -replace '\\', '/'
        $map[$relative] = $file
    }
    return $map
}

function New-UpdateManifest {
    param([string]$SourceAppDir)

    if (-not $UpdateBaseUrl) {
        Fail "-GenerateUpdateManifest requires -UpdateBaseUrl <url> (base URL under which the app directory will be hosted)."
    }
    if (-not (Test-Path -LiteralPath $SourceAppDir)) {
        Fail "Update manifest source directory was not found: $SourceAppDir"
    }

    $baseUrl = $UpdateBaseUrl.TrimEnd('/')
    # The manifest is generated from the jpackage app image, not from the staging directory, because the image also
    # contains the generated .cfg launcher files. Those carry the class path, so they change whenever a jar is renamed.
    $currentFiles = Get-RelativeFileMap $SourceAppDir
    if ($currentFiles.Count -eq 0) {
        Fail "No files were found in $SourceAppDir for the update manifest."
    }

    $obsolete = @()
    if ($PreviousAppDir -and (Test-Path -LiteralPath $PreviousAppDir)) {
        $previousFiles = Get-RelativeFileMap $PreviousAppDir
        $obsolete = @($previousFiles.Keys | Where-Object { -not $currentFiles.ContainsKey($_) } | Sort-Object)
        # The updater jar of the previous release is exactly the jar that performs this update, and Windows keeps it
        # open, so a delete= entry for it can only ever fail. Older updaters treat that failure as fatal and abort
        # after the new files are already in place. The updater removes leftover wp-updater*.jar files itself, on the
        # next run, when it no longer runs from them.
        $selfDeletes = @($obsolete | Where-Object { $_ -like 'wp-updater*.jar' })
        if ($selfDeletes.Count -gt 0) {
            $obsolete = @($obsolete | Where-Object { $_ -notlike 'wp-updater*.jar' })
            Write-Host "Skipping delete entries for the previous updater jar(s): $($selfDeletes -join ', ')"
        }
        Write-Host "Previous release app directory: $PreviousAppDir ($($previousFiles.Count) files, $($obsolete.Count) obsolete)"
    } else {
        Write-Warning "No previous app directory at '$PreviousAppDir'; the manifest will not delete any obsolete files."
    }

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add('# WorldPainter Languages update manifest')
    $lines.Add('# Generated by tools/windows-packaging/build-windows-installer.ps1')
    $lines.Add('format=1')
    $lines.Add("version=$ProductVersion")

    $totalBytes = [long]0
    foreach ($relativePath in ($currentFiles.Keys | Sort-Object)) {
        $file = $currentFiles[$relativePath]
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $encodedPath = (($relativePath -split '/') | ForEach-Object { [uri]::EscapeDataString($_) }) -join '/'
        $lines.Add("file=$hash`t$($file.Length)`t$relativePath`t$baseUrl/$encodedPath")
        $totalBytes += $file.Length
    }
    foreach ($relativePath in $obsolete) {
        $lines.Add("delete=$relativePath")
    }
    $lines.Add('')

    [System.IO.File]::WriteAllText($UpdateManifestPath, ($lines -join "`n"), (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Update manifest created: $UpdateManifestPath ($($currentFiles.Count) files, $totalBytes bytes covered, $($obsolete.Count) deletions)"
    Write-Host "  Source: $SourceAppDir"
    Write-Host "  Base URL: $baseUrl"
}

function New-CdnPayload {
    param([string]$SourceAppDir)

    # Everything the updater downloads is served from a plain repository, because GitHub release assets are flat and
    # cannot carry the app/lib/... layout. This produces the exact directory to push, so the published hashes and the
    # served bytes can never drift apart.
    Ensure-CleanDirectory $CdnDir
    $cdnAppDir = Join-Path $CdnDir 'app'
    Copy-Item -LiteralPath $SourceAppDir -Destination $cdnAppDir -Recurse -Force

    Copy-Item -LiteralPath $UpdateManifestPath -Destination (Join-Path $CdnDir 'update-manifest.txt') -Force

    # The client compares the SHA-256 of the served bytes with the manifest, so git must not touch a single byte. With
    # the usual Windows core.autocrlf=true, the .cfg and .properties files of the app image would be committed with LF
    # instead of CRLF and every download would be rejected as corrupted.
    @(
        '# Do not let git normalise anything: the updater verifies SHA-256 of the served bytes.'
        '* -text'
    ) | Set-Content -LiteralPath (Join-Path $CdnDir '.gitattributes') -Encoding ascii

    $readmePath = Join-Path $CdnDir 'README.md'
    @(
        '# WorldPainter Languages update CDN'
        ''
        'Download host for the incremental updater of'
        '[WorldPainter Languages](https://github.com/saplome/WorldPainter-LANGUAGES).'
        ''
        "Current contents: application files of **$ProductVersion**."
        ''
        '`app/` mirrors the `app/` directory of an installation one to one. `WorldPainter-Update.exe` reads'
        '`update-manifest.txt` from the release assets of the main repository and downloads only the files whose'
        'SHA-256 differs, from:'
        ''
        '```'
        "$($UpdateBaseUrl.TrimEnd('/'))/<path inside app>"
        '```'
        ''
        '`update-manifest.txt` is copied here for reference only; the client always reads the copy attached to the'
        'release in the main repository.'
        ''
        '## Updating this repository for a new release'
        ''
        '1. Replace `app/` with the `app/` directory of the new build.'
        '2. Commit and push to `main` **before** publishing the GitHub release, so every URL in the new manifest'
        '   already resolves.'
        '3. Upload the new `update-manifest.txt` as an asset of the release in the main repository.'
        ''
        'Keep `.gitattributes` (`* -text`) in place. The updater verifies SHA-256 of the served bytes, so any'
        'end-of-line normalisation by git would break every download of the `.cfg` and `.properties` files.'
        ''
        'Do not enable Git LFS here either: `raw.githubusercontent.com` would serve the LFS pointer text instead of'
        'the file.'
        ''
        'No license file of its own: these are build artifacts of WorldPainter Languages (GPLv3).'
    ) | Set-Content -LiteralPath $readmePath -Encoding utf8

    $files = @(Get-ChildItem -LiteralPath $cdnAppDir -Recurse -File)
    $totalBytes = ($files | Measure-Object Length -Sum).Sum
    Write-Host ""
    Write-Host "CDN payload ready to push: $CdnDir"
    Write-Host "  app/ files: $($files.Count), $([math]::Round($totalBytes / 1MB, 2)) MB"
}

function Show-StagingContents {
    $files = @(Get-ChildItem -LiteralPath $AppDir -Recurse -File | Sort-Object FullName)
    $totalBytes = ($files | Measure-Object Length -Sum).Sum

    Write-Host ""
    Write-Host "Staging app contents:"
    Write-Host "  Directory: $AppDir"
    Write-Host "  Files: $($files.Count)"
    Write-Host "  Size: $([math]::Round($totalBytes / 1MB, 2)) MB"

    foreach ($file in $files) {
        $relative = $file.FullName.Substring($AppDir.Length + 1)
        Write-Host "  $relative"
    }
}

function Invoke-JPackage {
    if (-not $script:WixAvailable) {
        Fail "Cannot build installer.exe because WiX tools are missing. Required: candle.exe and light.exe in PATH."
    }

    if (-not (Test-Path -LiteralPath $JPackageResourceDir)) {
        Fail "jpackage resource directory was not found: $JPackageResourceDir"
    }

    if (-not (Test-Path -LiteralPath $IconIcoPath)) {
        Fail "Installer icon was not found: $IconIcoPath"
    }

    $mainJarName = "WPGUI-$MavenVersion.jar"
    $fileAssociationsPath = Join-Path $StagingDir 'world-file-association.properties'
    @(
        'extension=world'
        'mime-type=application/x-worldpainter-world'
        'description=WorldPainter world'
        "icon=$IconIcoPath"
    ) | Set-Content -LiteralPath $fileAssociationsPath -Encoding ascii
    $finalInstallerName = "$ProductName $ProductVersion Setup.exe"
    $finalInstallerPath = Join-Path $InstallerDir $finalInstallerName
    $jpackageArguments = @(
        '--type', 'exe',
        '--name', $ProductName,
        '--app-version', $WindowsInstallerVersion,
        '--vendor', $ProductVendor,
        '--description', $ProductDescription,
        '--input', $AppDir,
        '--main-jar', $mainJarName,
        '--main-class', $MainClass,
        '--add-launcher', "WorldPainter-Update=$UpdateLauncherPropsPath",
        '--dest', $InstallerWorkDir,
        '--icon', $IconIcoPath,
        '--resource-dir', $JPackageResourceDir,
        '--file-associations', $fileAssociationsPath,
        '--win-menu',
        '--win-menu-group', $ProductName,
        '--win-shortcut',
        '--win-shortcut-prompt',
        '--win-dir-chooser',
        '--win-upgrade-uuid', $WindowsUpgradeUuid
    )

    Write-Host ""
    Write-Host "Running jpackage:"
    Write-Host "  jpackage $($jpackageArguments -join ' ')"

    Invoke-Checked 'jpackage' $jpackageArguments

    $installers = @(Get-ChildItem -LiteralPath $InstallerWorkDir -Filter '*.exe' -File | Sort-Object LastWriteTime -Descending)
    if ($installers.Count -eq 0) {
        Fail "jpackage completed, but no installer.exe was found in $InstallerWorkDir."
    }

    New-Item -ItemType Directory -Path $InstallerDir -Force | Out-Null
    if (Test-Path -LiteralPath $finalInstallerPath) {
        try {
            Remove-Item -LiteralPath $finalInstallerPath -Force
        } catch {
            Fail "Could not replace existing installer because it is locked: $finalInstallerPath. Close any running setup process and try again. Fresh installer remains in: $($installers[0].FullName)"
        }
    }

    Move-Item -LiteralPath $installers[0].FullName -Destination $finalInstallerPath

    Write-Host ""
    Write-Host "Installer created: $finalInstallerPath"
}

function Invoke-JPackageAppImage {
    Ensure-CleanDirectory $AppImageDir
    $mainJarName = "WPGUI-$MavenVersion.jar"
    $jpArguments = @(
        '--type', 'app-image',
        '--name', $ProductName,
        '--app-version', $WindowsInstallerVersion,
        '--vendor', $ProductVendor,
        '--description', $ProductDescription,
        '--input', $AppDir,
        '--main-jar', $mainJarName,
        '--main-class', $MainClass,
        '--add-launcher', "WorldPainter-Update=$UpdateLauncherPropsPath",
        '--dest', $AppImageDir,
        '--icon', $IconIcoPath
    )

    Write-Host ""
    Write-Host "Running jpackage (app-image):"
    Write-Host "  jpackage $($jpArguments -join ' ')"

    Invoke-Checked 'jpackage' $jpArguments

    $appImageRoot = Join-Path $AppImageDir $ProductName
    if (-not (Test-Path -LiteralPath $appImageRoot)) {
        Fail "jpackage completed, but the app image was not found: $appImageRoot"
    }

    Write-Host ""
    Write-Host "App image created: $appImageRoot"
}

function New-PortableZip {
    $appImageRoot = Join-Path $AppImageDir $ProductName
    if (-not (Test-Path -LiteralPath $appImageRoot)) {
        Fail "App image was not found: $appImageRoot. Run with -BuildPortable or -BuildAppImage first."
    }

    if (Test-Path -LiteralPath $PortableZipPath) {
        Remove-Item -LiteralPath $PortableZipPath -Force
    }

    Write-Host ""
    Write-Host "Creating portable zip..."
    Compress-Archive -Path (Join-Path $appImageRoot '*') -DestinationPath $PortableZipPath -CompressionLevel Optimal

    Write-Host "Portable zip created: $PortableZipPath"
}

function Invoke-InnoSetup {
    $iscc = $null
    $isccCommand = Get-Command 'ISCC.exe' -ErrorAction SilentlyContinue
    if ($isccCommand) {
        $iscc = $isccCommand.Source
    } else {
        $isccCandidates = @()
        if (${env:ProgramFiles(x86)}) { $isccCandidates += "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe" }
        if ($env:ProgramFiles) { $isccCandidates += "$env:ProgramFiles\Inno Setup 6\ISCC.exe" }
        if ($env:LOCALAPPDATA) { $isccCandidates += "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe" }
        foreach ($candidate in $isccCandidates) {
            if (Test-Path -LiteralPath $candidate) {
                $iscc = $candidate
                break
            }
        }
    }

    if (-not $iscc) {
        Fail "Inno Setup 6 (ISCC.exe) was not found. Install it from https://jrsoftware.org/isdl.php and retry."
    }

    Write-Host ""
    Write-Host "Found ISCC.exe: $iscc"
    Write-Host "Compiling Inno Setup installer..."

    Invoke-Checked $iscc @((Join-Path $ScriptDir 'installer.iss'))

    Write-Host "Inno Setup installer created in: $InstallerDir"
}


function Get-DraftReleaseAssets {
    $assets = New-Object System.Collections.Generic.List[string]

    if (Test-Path -LiteralPath $PortableZipPath) {
        $assets.Add((Resolve-Path $PortableZipPath).Path)
    }

    if (Test-Path -LiteralPath $UpdateManifestPath) {
        $assets.Add((Resolve-Path $UpdateManifestPath).Path)
    }

    if (Test-Path -LiteralPath $InstallerDir) {
        Get-ChildItem -LiteralPath $InstallerDir -Filter '*.exe' -File | Sort-Object Name | ForEach-Object {
            $assets.Add($_.FullName)
        }
    }

    foreach ($candidate in @(
        (Join-Path $ReleaseDir "WorldPainter-Languages-$ProductVersion-src.zip"),
        (Join-Path $ProjectRoot "WorldPainter-Languages-$ProductVersion-src.zip")
    )) {
        if (Test-Path -LiteralPath $candidate) {
            $assets.Add((Resolve-Path $candidate).Path)
        }
    }

    foreach ($asset in $AdditionalDraftAsset) {
        if (-not (Test-Path -LiteralPath $asset -PathType Leaf)) {
            Fail "Additional draft asset was not found: $asset"
        }
        $assets.Add((Resolve-Path $asset).Path)
    }

    $uniqueAssets = @($assets | Select-Object -Unique)
    if ($uniqueAssets.Count -eq 0) {
        Fail "No release assets were found. Build Portable/Installer or pass -AdditionalDraftAsset."
    }

    return $uniqueAssets
}

function Sync-GitHubDraftRelease {
    Test-Tool 'gh' 'Install GitHub CLI and run gh auth login.' -Required | Out-Null

    & gh auth status --hostname github.com
    if ($LASTEXITCODE -ne 0) {
        Fail "GitHub CLI is not authenticated. Run: gh auth login"
    }

    $notesPath = if ([string]::IsNullOrWhiteSpace($ReleaseNotesFile)) {
        $DefaultReleaseNotesPath
    } else {
        (Resolve-Path $ReleaseNotesFile).Path
    }
    if (-not (Test-Path -LiteralPath $notesPath -PathType Leaf)) {
        Fail "Release notes file was not found: $notesPath"
    }

    $assets = @(Get-DraftReleaseAssets)

    [string]$draftState = ''
    [int]$releaseViewExitCode = 0
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $draftState = (& gh release view $ReleaseTag --repo $GitHubRepository --json isDraft --jq '.isDraft' 2>$null)
        $releaseViewExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $releaseExists = ($releaseViewExitCode -eq 0)

    if ($releaseExists) {
        if ($draftState.Trim().ToLowerInvariant() -ne 'true') {
            Fail "Release tag $ReleaseTag already exists and is published. Refusing to replace it."
        }
        Invoke-Checked 'gh' @(
            'release', 'edit', $ReleaseTag,
            '--repo', $GitHubRepository,
            '--draft',
            '--title', $ReleaseTitle,
            '--notes-file', $notesPath
        )
    } else {
        Invoke-Checked 'gh' @(
            'release', 'create', $ReleaseTag,
            '--repo', $GitHubRepository,
            '--draft',
            '--title', $ReleaseTitle,
            '--notes-file', $notesPath
        )
    }

    $uploadArguments = @('release', 'upload', $ReleaseTag, '--repo', $GitHubRepository, '--clobber')
    $uploadArguments += $assets
    Invoke-Checked 'gh' $uploadArguments

    [string]$releaseUrl = (& gh release view $ReleaseTag --repo $GitHubRepository --json url --jq '.url')
    if ($LASTEXITCODE -ne 0) {
        Fail "Draft release was updated, but its URL could not be read."
    }

    Write-Host ""
    Write-Host "GitHub draft release updated: $releaseUrl"
    Write-Host "The release is still a draft and is not visible to regular users."

    if ($OpenDraftRelease) {
        Invoke-Checked 'gh' @('release', 'view', $ReleaseTag, '--repo', $GitHubRepository, '--web')
    }
}

function Assert-VersionConsistency {
    # A version typo here is invisible until the build is already on users' machines: the update check inside the
    # application compares its own CURRENT_PRODUCT_VERSION against the release tag, so a stale constant makes every copy
    # either offer an update to the version it already runs, or never notice the release at all.
    if ($ProductVersion -notmatch '^\d+\.\d+\.\d+$') {
        Fail "ProductVersion '$ProductVersion' is not <major>.<minor>.<patch>."
    }

    $expectedWindowsVersion = "$ProductVersion.0"
    if ($WindowsInstallerVersion -ne $expectedWindowsVersion) {
        Fail "WindowsInstallerVersion '$WindowsInstallerVersion' does not match '$expectedWindowsVersion' derived from '$ProductVersion'."
    }

    # The letter is what makes releases visible to builds up to 2.27.0-L2.0.1: they extract the number that follows
    # 'L' and compare it against 2, ignoring the rest of the tag. A tag without it leaves those users on an old build
    # with no notification at all.
    $expectedTag = "L$ProductVersion"
    if ($ReleaseTag -ne $expectedTag) {
        Fail "ReleaseTag '$ReleaseTag' is not '$expectedTag'."
    }
    if ([int]($ProductVersion -split '\.')[0] -lt 3) {
        Fail "ProductVersion '$ProductVersion' starts below 3, so builds up to 2.27.0-L2.0.1 would not recognise the release."
    }
    if ($ReleaseTitle -notlike "*$ProductVersion*") {
        Fail "ReleaseTitle '$ReleaseTitle' does not mention version '$ProductVersion'."
    }

    # The Maven version stays on the upstream release this fork is based on, which is also where the jar names and the
    # base version shown in the About dialog come from.
    $pomVersion = ([xml](Get-Content -LiteralPath (Join-Path $ProjectRoot 'pom.xml') -Raw)).project.version
    if ($pomVersion -ne $MavenVersion) {
        Fail "pom.xml declares version '$pomVersion', but this build expects '$MavenVersion'."
    }

    $checkerPath = Join-Path $ProjectRoot 'WPGUI\src\main\java\org\pepsoft\worldpainter\ForkUpdateChecker.java'
    $checkerMatch = [regex]::Match((Get-Content -LiteralPath $checkerPath -Raw), 'CURRENT_PRODUCT_VERSION\s*=\s*"([^"]+)"')
    if (-not $checkerMatch.Success) {
        Fail "Could not read CURRENT_PRODUCT_VERSION from $checkerPath."
    }
    if ($checkerMatch.Groups[1].Value -ne $ProductVersion) {
        Fail ("ForkUpdateChecker.CURRENT_PRODUCT_VERSION is '{0}', but this build is '{1}': the shipped update check would compare against the wrong version." -f $checkerMatch.Groups[1].Value, $ProductVersion)
    }

    $issPath = Join-Path $ScriptDir 'installer.iss'
    $issMatch = [regex]::Match((Get-Content -LiteralPath $issPath -Raw), '#define\s+MyAppVersion\s+"([^"]+)"')
    if (-not $issMatch.Success) {
        Fail "Could not read MyAppVersion from $issPath."
    }
    if ($issMatch.Groups[1].Value -ne $ProductVersion) {
        Fail ("installer.iss declares MyAppVersion '{0}' instead of '{1}', so the installer would be named and registered under the wrong version." -f $issMatch.Groups[1].Value, $ProductVersion)
    }

    # Release notes are named after the version, so bumping $ProductVersion without writing them is caught here rather
    # than at 'gh release create', when the build is already finished and the tag is about to be pushed.
    if (-not (Test-Path -LiteralPath $DefaultReleaseNotesPath -PathType Leaf)) {
        Fail "Release notes for this version are missing: docs\RELEASE_NOTES_$ProductVersion.md"
    }

    Write-Host "Version consistency: product $ProductVersion, tag $ReleaseTag, Windows $WindowsInstallerVersion, base $MavenVersion - all sources agree"
}

Write-Host "WorldPainter Languages Windows installer preparation"
Write-Host "Product: $ProductName"
Write-Host "Version: $ProductVersion"
Write-Host "Windows installer version: $WindowsInstallerVersion"
Write-Host "Windows upgrade UUID: $WindowsUpgradeUuid"
Write-Host "Vendor: $ProductVendor"
Write-Host "Description: $ProductDescription"
Write-Host "Maven artifact version: $MavenVersion"
Write-Host "Main class: $MainClass"
Write-Host "Project root: $ProjectRoot"
Write-Host "Release path: $ReleaseDir"
Write-Host "Staging path: $AppDir"
Write-Host "Installer path: $InstallerDir"
Write-Host "Installer work path: $InstallerWorkDir"
Write-Host "Logs path: $LogsDir"
Write-Host "Installer icon: $IconIcoPath"
Write-Host "jpackage resources: $JPackageResourceDir"
Write-Host "jpackage execution: $(if ($JPackageEnabled) { 'enabled' } else { 'disabled, preparation only' })"
Write-Host ""

Assert-VersionConsistency
Write-Host ""

Test-Tool 'java' 'Install JDK 17 and check PATH/JAVA_HOME.' -Required | Out-Null
Test-Tool 'jar' 'Install a full JDK, not only a JRE, and check PATH.' -Required | Out-Null
Test-Tool 'javac' 'Install a full JDK, not only a JRE, and check PATH.' -Required | Out-Null
Test-Tool 'mvn' 'Install Maven and check PATH.' -Required | Out-Null
Test-Tool 'jpackage' 'Use a JDK distribution which includes jpackage.' -Required | Out-Null

if ($BuildInstaller) {
    $candleFound = Test-Tool 'candle.exe' 'Install WiX Toolset and add its bin directory to PATH.' -Required
    $lightFound = Test-Tool 'light.exe' 'Install WiX Toolset and add its bin directory to PATH.' -Required
} else {
    $candleFound = Test-Tool 'candle.exe' 'Install WiX Toolset and add its bin directory to PATH.'
    $lightFound = Test-Tool 'light.exe' 'Install WiX Toolset and add its bin directory to PATH.'
}
$script:WixAvailable = ($candleFound -and $lightFound)

Write-Host ""
Write-Host "Tool check completed."
Write-Host ""

if (-not $SkipMaven) {
    Push-Location $ProjectRoot
    try {
        Write-Host "Running Maven build..."
        Invoke-Checked 'mvn' @('clean', 'package')
    } finally {
        Pop-Location
    }
} else {
    Write-Warning "Maven build skipped by -SkipMaven."
}

New-Item -ItemType Directory -Path $ReleaseDir -Force | Out-Null
Ensure-CleanDirectory $LogsDir
Ensure-CleanDirectory $StagingDir
New-Item -ItemType Directory -Path $AppDir -Force | Out-Null
New-Item -ItemType Directory -Path $LibDir -Force | Out-Null
Ensure-CleanDirectory $InstallerWorkDir
New-Item -ItemType Directory -Path $InstallerDir -Force | Out-Null

Copy-RuntimeDependencies

$stagedGuiJar = Copy-ModuleJar 'WPGUI' "WPGUI-$MavenVersion.jar"
Copy-ModuleJar 'WPCore' "WPCore-$MavenVersion.jar" | Out-Null
Copy-ModuleJar 'WPDynmapPreviewer' "WPDynmapPreviewer-$MavenVersion.jar" | Out-Null

Write-Host ""
Write-Host "Found GUI jar: $stagedGuiJar"
Update-GuiJarManifest $stagedGuiJar
New-UpdaterStaging
Show-StagingContents

Write-Host ""
Write-Host "Prepared release staging directory:"
Write-Host "  $AppDir"
Write-Host "Release output directory:"
Write-Host "  $ReleaseDir"

if ($BuildInstaller) {
    Invoke-JPackage
}

if ($BuildAppImage -or $BuildPortable -or $BuildInnoInstaller) {
    Invoke-JPackageAppImage
}

if ($BuildPortable) {
    New-PortableZip
}

if ($BuildInnoInstaller) {
    Invoke-InnoSetup
}

if ($GenerateUpdateManifest) {
    $appImageAppDir = Join-Path (Join-Path $AppImageDir $ProductName) 'app'
    if (-not (Test-Path -LiteralPath $appImageAppDir)) {
        Fail "-GenerateUpdateManifest needs the jpackage app image; add -BuildAppImage, -BuildPortable or -BuildInnoInstaller."
    }
    New-UpdateManifest -SourceAppDir $appImageAppDir
    New-CdnPayload -SourceAppDir $appImageAppDir
}

if (-not ($BuildInstaller -or $BuildAppImage -or $BuildPortable -or $BuildInnoInstaller)) {
    Write-Host ""
    Write-Warning "Nothing was packaged because no build switch was specified."
    Write-Host "Available build switches:"
    Write-Host "  -BuildInstaller       classic WiX/MSI installer (requires WiX Toolset)"
    Write-Host "  -BuildAppImage        standalone app image via jpackage"
    Write-Host "  -BuildPortable        app image + portable zip"
    Write-Host "  -BuildInnoInstaller   app image + branded Inno Setup installer (requires Inno Setup 6)"
    Write-Host "  -GenerateUpdateManifest   update-manifest.txt for the incremental updater (requires -UpdateBaseUrl)"
}

if ($CreateDraftRelease) {
    Sync-GitHubDraftRelease
}
