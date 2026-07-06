#requires -version 5.1

[CmdletBinding()]
param(
    [switch]$SkipMaven,
    [switch]$BuildInstaller
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProductName = 'WorldPainter RU'
$ProductVersion = '2.27.0-r2'
$WindowsInstallerVersion = '2.27.2'
$WindowsUpgradeUuid = 'd5984a7f-cb32-48c8-b6f1-97a3c4c0da44'
$ProductVendor = 'WorldPainter RU'
$ProductDescription = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String('0KDRg9GB0LjRhNC40YbQuNGA0L7QstCw0L3QvdCw0Y8g0LLQtdGA0YHQuNGPIFdvcmxkUGFpbnRlciAyLjI3LjA='))
$MavenVersion = '2.27.0'
$MainClass = 'org.pepsoft.worldpainter.Main'
$JPackageEnabled = [bool]$BuildInstaller

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir '..')).Path
$JPackageResourceDir = Join-Path $ScriptDir 'jpackage-resources\windows'
$IconIcoPath = Join-Path $ProjectRoot 'assets\icon.ico'
$ReleaseDir = Join-Path $ProjectRoot 'release'
$StagingDir = Join-Path $ReleaseDir 'staging'
$AppDir = Join-Path $StagingDir 'app'
$LibDir = Join-Path $AppDir 'lib'
$InstallerDir = Join-Path $ReleaseDir 'installer'
$InstallerWorkDir = Join-Path $ReleaseDir 'installer-work'
$LogsDir = Join-Path $ReleaseDir 'logs'

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
        '--dest', $InstallerWorkDir,
        '--icon', $IconIcoPath,
        '--resource-dir', $JPackageResourceDir,
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

Write-Host "WorldPainter RU Windows installer preparation"
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

Test-Tool 'java' 'Install JDK 17 and check PATH/JAVA_HOME.' -Required | Out-Null
Test-Tool 'jar' 'Install a full JDK, not only a JRE, and check PATH.' -Required | Out-Null
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
Show-StagingContents

Write-Host ""
Write-Host "Prepared release staging directory:"
Write-Host "  $AppDir"
Write-Host "Release output directory:"
Write-Host "  $ReleaseDir"

if ($BuildInstaller) {
    Invoke-JPackage
} else {
    Write-Host ""
    Write-Warning "installer.exe was not built because -BuildInstaller was not specified."
    Write-Host "To build the installer, run:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File .\scripts\build-windows-installer.ps1 -BuildInstaller"
}
