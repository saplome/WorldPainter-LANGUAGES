#requires -version 5.1

[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [int]$Port = 8000,
    [string]$WorkDir = '',
    [switch]$KeepWorkDir
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Failures = New-Object System.Collections.Generic.List[string]
$script:ServerJob = $null

function Write-Step {
    param([string]$Message)
    Write-Host ''
    Write-Host "=== $Message" -ForegroundColor Cyan
}

function Write-Pass {
    param([string]$Message)
    Write-Host "  PASS  $Message" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Message)
    Write-Host "  FAIL  $Message" -ForegroundColor Red
    $script:Failures.Add($Message)
}

function Assert-Condition {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if ($Condition) { Write-Pass $Message } else { Write-Fail $Message }
}

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function New-LocalManifest {
    param(
        [string]$Root,
        [string]$BaseUrl,
        [string]$Version,
        [string]$Output,
        [string[]]$Delete = @()
    )

    $baseUrl = $BaseUrl.TrimEnd('/')
    # Nothing is excluded: a release manifest covers the whole app directory, including the updater jar itself, and the
    # updater is expected to skip the jar it is running from.
    $rootFull = [System.IO.Path]::GetFullPath($Root)
    $files = @(Get-ChildItem -LiteralPath $rootFull -Recurse -File | Sort-Object FullName)
    if (@($files).Count -eq 0) {
        throw "No files found under $rootFull for the manifest."
    }

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add('# WorldPainter Languages update manifest (local test)')
    $lines.Add('format=1')
    $lines.Add("version=$Version")
    foreach ($file in $files) {
        $relativePath = $file.FullName.Substring($rootFull.Length + 1) -replace '\\', '/'
        $hash = Get-Sha256 -Path $file.FullName
        $encodedPath = (($relativePath -split '/') | ForEach-Object { [uri]::EscapeDataString($_) }) -join '/'
        $lines.Add("file=$hash`t$($file.Length)`t$relativePath`t$baseUrl/$encodedPath")
    }
    foreach ($path in $Delete) {
        $lines.Add("delete=$path")
    }
    $lines.Add('')

    [System.IO.File]::WriteAllText($Output, ($lines -join "`n"), (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "  manifest: $($files.Count) file(s), $($Delete.Count) delete entry(ies)"
}

function Start-LocalServer {
    param(
        [string]$Root,
        [int]$Port
    )

    $serverScript = {
        param($prefix, $rootDir)
        $listener = New-Object System.Net.HttpListener
        $listener.Prefixes.Add($prefix)
        $listener.Start()
        $rootFull = [System.IO.Path]::GetFullPath($rootDir)
        while ($listener.IsListening) {
            $context = $listener.GetContext()
            try {
                $relative = [uri]::UnescapeDataString($context.Request.Url.AbsolutePath.TrimStart('/'))
                if ($relative -eq '__shutdown__') {
                    $context.Response.StatusCode = 200
                    $context.Response.Close()
                    break
                }
                $candidate = [System.IO.Path]::GetFullPath((Join-Path $rootFull ($relative -replace '/', '\')))
                if ($candidate.StartsWith($rootFull) -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
                    $bytes = [System.IO.File]::ReadAllBytes($candidate)
                    $context.Response.ContentType = 'application/octet-stream'
                    $context.Response.ContentLength64 = $bytes.Length
                    $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
                } else {
                    $context.Response.StatusCode = 404
                }
            } catch {
                try { $context.Response.StatusCode = 500 } catch { }
            }
            try { $context.Response.Close() } catch { }
        }
        try { $listener.Stop() } catch { }
    }

    $prefix = "http://localhost:$Port/"
    $job = Start-Job -ScriptBlock $serverScript -ArgumentList $prefix, $Root

    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Milliseconds 250
        if ($job.State -eq 'Failed') { break }
        try {
            Invoke-WebRequest -Uri "http://localhost:$Port/update-manifest.txt" -UseBasicParsing -TimeoutSec 5 | Out-Null
            $ready = $true
            break
        } catch {
            $response = $_.Exception.Response
            if ($response -and ($response.StatusCode.value__ -eq 404)) { $ready = $true; break }
        }
    }

    if (-not $ready) {
        $reason = ''
        try { $reason = (Receive-Job -Job $job -ErrorAction SilentlyContinue | Out-String).Trim() } catch { }
        Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
        Write-Host ''
        Write-Host "Could not start the local HTTP server on port $Port." -ForegroundColor Red
        if ($reason) { Write-Host $reason -ForegroundColor DarkGray }
        Write-Host 'Fix: run this script in an elevated PowerShell, or reserve the URL once:' -ForegroundColor Yellow
        Write-Host "  netsh http add urlacl url=http://localhost:$Port/ user=$env:USERNAME" -ForegroundColor Yellow
        Write-Host '  (or pass another port with -Port)' -ForegroundColor Yellow
        throw "Local HTTP server did not start."
    }

    return $job
}

function Stop-LocalServer {
    if (-not $script:ServerJob) { return }
    try { Invoke-WebRequest -Uri "http://localhost:$Port/__shutdown__" -UseBasicParsing -TimeoutSec 5 | Out-Null } catch { }
    Start-Sleep -Milliseconds 500
    Stop-Job -Job $script:ServerJob -ErrorAction SilentlyContinue
    Remove-Job -Job $script:ServerJob -Force -ErrorAction SilentlyContinue
    $script:ServerJob = $null
}

function Invoke-Updater {
    param(
        [string]$Title,
        [string[]]$Arguments
    )

    Write-Host "  > java -jar $(Split-Path -Leaf $script:UpdaterJar) $($Arguments -join ' ')" -ForegroundColor DarkGray
    $stdout = Join-Path $script:LogDir ("$Title.out.txt")
    $stderr = Join-Path $script:LogDir ("$Title.err.txt")
    $allArguments = @('-jar', $script:UpdaterJar) + $Arguments
    $process = Start-Process -FilePath 'java' -ArgumentList $allArguments -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $output = ''
    if (Test-Path -LiteralPath $stdout) { $output += (Get-Content -LiteralPath $stdout -Raw -ErrorAction SilentlyContinue) }
    if (Test-Path -LiteralPath $stderr) { $output += (Get-Content -LiteralPath $stderr -Raw -ErrorAction SilentlyContinue) }
    if ($output) {
        foreach ($line in ($output -split "`r?`n")) {
            if ($line.Trim()) { Write-Host "    | $line" -ForegroundColor DarkGray }
        }
    }
    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Output   = $output
    }
}

function Get-TempLeftoverCount {
    param([string]$Root)
    $items = @(Get-ChildItem -LiteralPath $Root -Recurse -File -Filter '*.wpupdate-tmp' -ErrorAction SilentlyContinue)
    return [int]$items.Count
}

if (-not $ProjectRoot) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
if (-not (Test-Path -LiteralPath $ProjectRoot -PathType Container)) {
    throw "Project root not found: $ProjectRoot"
}
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path

$sourceAppDir = Join-Path $ProjectRoot 'release\staging\app'
if (-not (Test-Path -LiteralPath $sourceAppDir -PathType Container)) {
    Write-Host "Build output not found: $sourceAppDir" -ForegroundColor Red
    Write-Host 'Build it first, for example:' -ForegroundColor Yellow
    Write-Host "  .\tools\windows-packaging\build-windows-installer.ps1 -BuildPortable" -ForegroundColor Yellow
    throw 'Nothing to test.'
}

$sourceUpdaterJars = @(Get-ChildItem -LiteralPath $sourceAppDir -Filter 'wp-updater*.jar' -File)
if ($sourceUpdaterJars.Count -ne 1) {
    throw "Expected exactly one wp-updater*.jar in $sourceAppDir, found $($sourceUpdaterJars.Count). Rebuild with the current build-windows-installer.ps1."
}
$updaterJarName = $sourceUpdaterJars[0].Name

if (-not (Get-Command 'java' -ErrorAction SilentlyContinue)) {
    throw 'java was not found on PATH. Install/point PATH at JDK 17.'
}

if (-not $WorkDir) {
    $WorkDir = Join-Path $env:TEMP 'wp-updater-local-test'
}
if (Test-Path -LiteralPath $WorkDir) {
    Remove-Item -LiteralPath $WorkDir -Recurse -Force
}
$null = New-Item -ItemType Directory -Path $WorkDir -Force

$cdnDir = Join-Path $WorkDir 'cdn'
$cdnAppDir = Join-Path $cdnDir 'app'
$installDir = Join-Path $WorkDir 'install'
$script:LogDir = Join-Path $WorkDir 'logs'
$manifestPath = Join-Path $cdnDir 'update-manifest.txt'
$manifestUrl = "http://localhost:$Port/update-manifest.txt"
$baseUrl = "http://localhost:$Port/app"
$null = New-Item -ItemType Directory -Path $cdnDir, $script:LogDir -Force

Write-Step 'Preparing the sandbox copies'
Write-Host "  project root : $ProjectRoot"
Write-Host "  work dir     : $WorkDir"
Copy-Item -LiteralPath $sourceAppDir -Destination $cdnAppDir -Recurse -Force
Copy-Item -LiteralPath $sourceAppDir -Destination $installDir -Recurse -Force
$script:UpdaterJar = Join-Path $installDir $updaterJarName
$installedFileCount = @(Get-ChildItem -LiteralPath $installDir -Recurse -File).Length
Write-Host "  installed copy: $installedFileCount file(s)"

try {
    New-LocalManifest -Root $cdnAppDir -BaseUrl $baseUrl -Version '2.27.0-L2.0.0-test1' -Output $manifestPath

    Write-Step "Starting local HTTP server on http://localhost:$Port/"
    $script:ServerJob = Start-LocalServer -Root $cdnDir -Port $Port
    Write-Host '  server is up'

    Write-Step 'T1  check-only on an up-to-date installation (expect exit 0)'
    $t1 = Invoke-Updater -Title 't1-check-clean' -Arguments @('--manifest', $manifestUrl, '--root', $installDir, '--check-only', '--no-launch')
    Assert-Condition ($t1.ExitCode -eq 0) "exit code 0 (actual: $($t1.ExitCode))"
    Assert-Condition ($t1.Output -match 'up to date') 'reports that everything is up to date'

    Write-Step 'T2  publishing a fake new version'
    $changedFile = @(Get-ChildItem -LiteralPath $cdnAppDir -Recurse -File -Filter '*.jar' | Where-Object { $_.Name -notlike 'wp-updater*' } | Sort-Object Length) | Select-Object -First 1
    if (-not $changedFile) {
        $changedFile = @(Get-ChildItem -LiteralPath $cdnAppDir -Recurse -File | Sort-Object Length) | Select-Object -First 1
    }
    $changedRelative = $changedFile.FullName.Substring(([System.IO.Path]::GetFullPath($cdnAppDir)).Length + 1)
    Add-Content -LiteralPath $changedFile.FullName -Value 'local-updater-test' -Encoding Ascii
    $newRemoteFile = Join-Path $cdnAppDir 'updater-test-new.txt'
    [System.IO.File]::WriteAllText($newRemoteFile, "added by the local updater test`n", (New-Object System.Text.UTF8Encoding($false)))
    $obsoleteLocalFile = Join-Path $installDir 'updater-test-obsolete.txt'
    [System.IO.File]::WriteAllText($obsoleteLocalFile, "must be deleted by the updater`n", (New-Object System.Text.UTF8Encoding($false)))

    New-LocalManifest -Root $cdnAppDir -BaseUrl $baseUrl -Version '2.27.0-L2.0.0-test2' -Output $manifestPath -Delete @('updater-test-obsolete.txt')
    Write-Host "  changed remote file: $changedRelative"

    $expectedHash = Get-Sha256 -Path $changedFile.FullName
    $untouchedBefore = @{}
    foreach ($file in (Get-ChildItem -LiteralPath $installDir -Recurse -File)) {
        $untouchedBefore[$file.FullName] = $file.LastWriteTimeUtc
    }

    $t2 = Invoke-Updater -Title 't2-check-dirty' -Arguments @('--manifest', $manifestUrl, '--root', $installDir, '--check-only', '--no-launch')
    Assert-Condition ($t2.ExitCode -eq 1) "check-only exit code 1 when updates exist (actual: $($t2.ExitCode))"
    Assert-Condition ($t2.Output -match [regex]::Escape(($changedRelative -replace '\\', '/'))) 'lists the changed file'
    Assert-Condition ($t2.Output -match 'updater-test-new.txt') 'lists the new file'
    Assert-Condition ($t2.Output -match 'updater-test-obsolete.txt') 'lists the obsolete file for deletion'
    Assert-Condition ((Get-Sha256 -Path (Join-Path $installDir $changedRelative)) -ne $expectedHash) 'check-only did not modify anything'

    Write-Step 'T3  applying the update (expect exit 0, delta download only)'
    $t3 = Invoke-Updater -Title 't3-apply' -Arguments @('--manifest', $manifestUrl, '--root', $installDir, '--no-launch')
    Assert-Condition ($t3.ExitCode -eq 0) "exit code 0 (actual: $($t3.ExitCode))"
    Assert-Condition ((Get-Sha256 -Path (Join-Path $installDir $changedRelative)) -eq $expectedHash) 'changed file now matches the manifest hash'
    Assert-Condition (Test-Path -LiteralPath (Join-Path $installDir 'updater-test-new.txt') -PathType Leaf) 'new file was downloaded'
    Assert-Condition (-not (Test-Path -LiteralPath $obsoleteLocalFile)) 'obsolete file was deleted'
    Assert-Condition ((Get-TempLeftoverCount -Root $installDir) -eq 0) 'no *.wpupdate-tmp leftovers'

    $rewritten = New-Object System.Collections.Generic.List[string]
    foreach ($file in (Get-ChildItem -LiteralPath $installDir -Recurse -File)) {
        if (-not $untouchedBefore.ContainsKey($file.FullName)) { continue }
        if ($untouchedBefore[$file.FullName] -ne $file.LastWriteTimeUtc) {
            $rewritten.Add($file.FullName.Substring($installDir.Length + 1))
        }
    }
    Assert-Condition ($rewritten.Count -eq 1) "exactly one pre-existing file was rewritten (actual: $($rewritten.Count) -> $($rewritten -join ', '))"

    Write-Step 'T4  re-running the updater (expect a no-op)'
    $t4 = Invoke-Updater -Title 't4-noop' -Arguments @('--manifest', $manifestUrl, '--root', $installDir, '--no-launch')
    Assert-Condition ($t4.ExitCode -eq 0) "exit code 0 (actual: $($t4.ExitCode))"
    Assert-Condition ($t4.Output -match 'up to date') 'reports that everything is up to date'

    Write-Step 'T5  corrupted download is rejected (expect exit 2, installation intact)'
    $localTarget = Join-Path $installDir $changedRelative
    Add-Content -LiteralPath $localTarget -Value 'make-local-outdated' -Encoding Ascii
    $localHashBefore = Get-Sha256 -Path $localTarget
    Add-Content -LiteralPath $changedFile.FullName -Value 'corrupt-remote-without-manifest-update' -Encoding Ascii

    $t5 = Invoke-Updater -Title 't5-corrupt' -Arguments @('--manifest', $manifestUrl, '--root', $installDir, '--no-launch')
    Assert-Condition ($t5.ExitCode -eq 2) "exit code 2 (actual: $($t5.ExitCode))"
    Assert-Condition ($t5.Output -match '(?i)(SHA-256|Size) mismatch') 'reports a hash/size mismatch'
    Assert-Condition ((Get-Sha256 -Path $localTarget) -eq $localHashBefore) 'local file was left untouched'
    Assert-Condition ((Get-TempLeftoverCount -Root $installDir) -eq 0) 'no *.wpupdate-tmp leftovers'

    Write-Step 'T6  manifest URL that does not exist (expect exit 2)'
    $t6 = Invoke-Updater -Title 't6-missing-manifest' -Arguments @('--manifest', "http://localhost:$Port/no-such-manifest.txt", '--root', $installDir, '--check-only', '--no-launch')
    Assert-Condition ($t6.ExitCode -eq 2) "exit code 2 (actual: $($t6.ExitCode))"

    Write-Step 'T7  the updater does not try to overwrite its own jar (expect exit 0, jar untouched)'
    # T5 left the served bytes and the manifest out of sync on purpose. Republish the current CDN state and converge,
    # so that afterwards the updater jar is the only difference.
    New-LocalManifest -Root $cdnAppDir -BaseUrl $baseUrl -Version '2.27.1-L2.1.0-test7a' -Output $manifestPath
    $t7restore = Invoke-Updater -Title 't7-restore' -Arguments @('--manifest', $manifestUrl, '--root', $installDir, '--no-launch')
    Assert-Condition ($t7restore.ExitCode -eq 0) "converged on the republished manifest (actual: $($t7restore.ExitCode))"

    $cdnUpdaterJar = Join-Path $cdnAppDir $updaterJarName
    $installedUpdaterJar = Join-Path $installDir $updaterJarName
    Add-Content -LiteralPath $cdnUpdaterJar -Value 'pretend-this-is-a-newer-updater' -Encoding Ascii
    New-LocalManifest -Root $cdnAppDir -BaseUrl $baseUrl -Version '2.27.1-L2.1.0-test7b' -Output $manifestPath
    $updaterHashBefore = Get-Sha256 -Path $installedUpdaterJar

    $t7 = Invoke-Updater -Title 't7-self-jar' -Arguments @('--manifest', $manifestUrl, '--root', $installDir, '--no-launch')
    Assert-Condition ($t7.ExitCode -eq 0) "exit code 0 despite the manifest listing a different updater jar (actual: $($t7.ExitCode))"
    Assert-Condition ((Get-Sha256 -Path $installedUpdaterJar) -eq $updaterHashBefore) 'the running updater jar was left alone'
} finally {
    Stop-LocalServer
}

Write-Host ''
if ($script:Failures.Count -eq 0) {
    Write-Host 'ALL CHECKS PASSED - the incremental updater works end to end.' -ForegroundColor Green
} else {
    Write-Host "FAILED CHECKS: $($script:Failures.Count)" -ForegroundColor Red
    foreach ($failure in $script:Failures) { Write-Host "  - $failure" -ForegroundColor Red }
}
Write-Host "Logs: $script:LogDir"

if ($KeepWorkDir) {
    Write-Host "Work directory kept: $WorkDir"
} else {
    try {
        Remove-Item -LiteralPath $WorkDir -Recurse -Force
        Write-Host 'Work directory removed (pass -KeepWorkDir to inspect it).'
    } catch {
        Write-Host "Could not remove $WorkDir : $_" -ForegroundColor Yellow
    }
}

if ($script:Failures.Count -gt 0) { exit 1 }
exit 0
