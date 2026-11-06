<#
.SYNOPSIS
    Smoke test of KingdomWorld on a real dedicated Forge server.

.DESCRIPTION
    Installs a Forge 1.20.1-47.4.10 dedicated server outside the repository, puts the freshly built mod jar
    into it, accepts the EULA and runs two server sessions on a fresh world:

      1. start, wait for "Done", found the kingdom SmokeTest with a throne at 8 70 8
         ("kingdomworld admin found"), claim chunk [2, 0] for it ("kingdomworld admin claim"), add three
         citizens ("kingdomworld admin citizens spawn"), list them, let one die ("kingdomworld admin citizens
         kill") and check that two remain and none has a body without players, then "stop";
      2. start again, check with "kingdomworld debug list", "kingdomworld admin owner" and "kingdom info" that
         the kingdom, its id and all 10 chunks survived the restart, and with "kingdomworld admin citizens list"
         and "info" that exactly the same two citizens are there (nothing lost, nothing doubled), then "stop".

    The test passes when both sessions start and stop cleanly, the kingdom, its land and its citizens created in
    session 1 are found unchanged in session 2, the fire protection hook is active, and the logs contain no errors or
    exceptions related to kingdomworld. The JVM
    class loading log is also checked to prove that no class of the mod's client package is loaded on the
    dedicated server.

    With -Pack the server lives in "server-pack" and additionally gets the server-side and common mods of the
    modpack instance (client-only mods are skipped). The modpack folder is only read, never modified.

    Exit code 0 means success; any failure ends the script with exit code 1.

.PARAMETER Pack
    Also install the modpack's server-side mods.

.PARAMETER WithoutMod
    Diagnostic mode: do not install KingdomWorld, only check that the server reaches "Done" and stops.
    Useful with -Pack to find out whether a pack mod breaks the server on its own.

.PARAMETER SkipBuild
    Use the jar already in build/libs instead of running "gradlew build".

.PARAMETER TestEnvRoot
    Folder for the test servers and the installer cache. Must be outside the repository.

.PARAMETER PackModsDir
    Mods folder of the modpack instance (read only).

.PARAMETER ExcludeModIds
    Additional mod ids to leave out in -Pack mode.

.PARAMETER JavaHome
    JDK 17 used for the build and the server.

.PARAMETER Memory
    Maximum heap of the server JVM, e.g. 2G or 6G.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1 -Pack -SkipBuild
#>
[CmdletBinding()]
param(
    [switch]$Pack,
    [switch]$WithoutMod,
    [switch]$SkipBuild,
    [string]$TestEnvRoot = 'D:\Projects\kingdom-world-testenv',
    [string]$PackModsDir = 'D:\Games\Minecraft\Instances\Taghi RPG & Create RU\mods',
    [string[]]$ExcludeModIds = @(),
    [string]$JavaHome = $(if ($env:KINGDOMWORLD_JAVA_HOME) { $env:KINGDOMWORLD_JAVA_HOME } else { 'C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot' }),
    [string]$Memory = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ForgeVersion = '1.20.1-47.4.10'
$InstallerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$ForgeVersion/forge-$ForgeVersion-installer.jar"
$KingdomName = 'SmokeTest'

# Mods that only make sense on a client. Mods that declare clientSideOnly=true are skipped as well.
$ClientOnlyModIds = @(
    'absoluteafkcinema', 'ambientsounds', 'clientsort', 'eatinganimation', 'embeddium', 'rubidium',
    'entity_model_features', 'entity_texture_features', 'entityculling', 'holdmyitems', 'immediatelyfast',
    'irisflw', 'locatorbar', 'mousetweaks', 'notenoughanimations', 'oculus', 'sound_physics_remastered'
)

$RepoRoot = Split-Path -Parent $PSScriptRoot
$Java = Join-Path $JavaHome 'bin\java.exe'
$ServerDir = Join-Path $TestEnvRoot $(if ($Pack) { 'server-pack' } else { 'server-clean' })
$CacheDir = Join-Path $TestEnvRoot 'cache'
$SmokeLogDir = Join-Path $ServerDir 'smoke-logs'
if (-not $Memory) { $Memory = if ($Pack) { '6G' } else { '2G' } }
$StartTimeout = if ($Pack) { 1500 } else { 420 }
$StopTimeout = if ($Pack) { 600 } else { 180 }
$CommandTimeout = 90

$script:Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$script:Failures = New-Object System.Collections.Generic.List[string]
$script:Warnings = New-Object System.Collections.Generic.List[string]

function Write-Step([string]$Message) { Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Ok([string]$Message) { Write-Host "    OK: $Message" -ForegroundColor Green }
function Add-Failure([string]$Message) { $script:Failures.Add($Message); Write-Host "    FAIL: $Message" -ForegroundColor Red }
function Add-Warning([string]$Message) { $script:Warnings.Add($Message); Write-Host "    WARN: $Message" -ForegroundColor Yellow }

function Stop-Smoke([string]$Message) {
    if ($Message) { Add-Failure $Message }
    Write-Summary
    exit 1
}

function Write-Summary {
    Write-Host ''
    Write-Host '==== Smoke test summary ====' -ForegroundColor Cyan
    Write-Host "Server folder: $ServerDir"
    Write-Host "Logs: $SmokeLogDir"
    foreach ($w in $script:Warnings) { Write-Host "  warning: $w" -ForegroundColor Yellow }
    if ($script:Failures.Count -eq 0) {
        Write-Host 'RESULT: PASSED' -ForegroundColor Green
    } else {
        foreach ($f in $script:Failures) { Write-Host "  failure: $f" -ForegroundColor Red }
        Write-Host 'RESULT: FAILED' -ForegroundColor Red
    }
}

function Read-SharedText([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return '' }
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete)
    try {
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
        return $reader.ReadToEnd()
    } finally {
        $stream.Dispose()
    }
}

function Set-ServerProperty([string]$File, [string]$Key, [string]$Value) {
    $lines = @()
    if (Test-Path -LiteralPath $File) { $lines = @(Get-Content -LiteralPath $File) }
    $found = $false
    $lines = @($lines | ForEach-Object {
        if ($_ -match "^\s*$([regex]::Escape($Key))\s*=") { $found = $true; "$Key=$Value" } else { $_ }
    })
    if (-not $found) { $lines += "$Key=$Value" }
    Set-Content -LiteralPath $File -Value $lines -Encoding ASCII
}

function Get-ModInfo([string]$Jar) {
    # Returns mod ids declared in [[mods]] sections and whether the jar marks itself client-only.
    $ids = New-Object System.Collections.Generic.List[string]
    $clientOnly = $false
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        $entry = $zip.GetEntry('META-INF/mods.toml')
        if ($entry) {
            $reader = New-Object System.IO.StreamReader($entry.Open())
            try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
            $inMods = $false
            foreach ($line in ($text -split "`r?`n")) {
                if ($line -match '^\s*\[\[mods\]\]') { $inMods = $true; continue }
                if ($line -match '^\s*\[') { $inMods = $false; continue }
                if ($line -match '^\s*clientSideOnly\s*=\s*true') { $clientOnly = $true }
                if ($inMods -and $line -match '^\s*modId\s*=\s*"([^"]+)"') { $ids.Add($Matches[1]) }
            }
        }
    } finally {
        $zip.Dispose()
    }
    return [pscustomobject]@{ Ids = $ids.ToArray(); ClientOnly = $clientOnly }
}

function Invoke-Build {
    Write-Step 'Building the mod (gradlew build)'
    $env:JAVA_HOME = $JavaHome
    Push-Location $RepoRoot
    try {
        & (Join-Path $RepoRoot 'gradlew.bat') build --console=plain
        if ($LASTEXITCODE -ne 0) { Stop-Smoke "gradlew build failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

function Get-ModJar {
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'build\libs') -Filter 'kingdomworld-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|slim|all)\.jar$' } |
        Sort-Object LastWriteTime -Descending)
    if ($jars.Count -eq 0) { Stop-Smoke 'No kingdomworld jar in build/libs; run without -SkipBuild' }
    return $jars[0].FullName
}

function Install-ForgeServer {
    $argsFile = Join-Path $ServerDir "libraries\net\minecraftforge\forge\$ForgeVersion\win_args.txt"
    if (Test-Path -LiteralPath $argsFile) {
        Write-Ok "Forge $ForgeVersion server already installed"
        return
    }
    Write-Step "Installing Forge $ForgeVersion server into $ServerDir"
    New-Item -ItemType Directory -Force -Path $ServerDir, $CacheDir | Out-Null
    $installer = Join-Path $CacheDir "forge-$ForgeVersion-installer.jar"
    if (-not (Test-Path -LiteralPath $installer)) {
        Write-Host "    downloading $InstallerUrl"
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $InstallerUrl -OutFile $installer -UseBasicParsing
    }
    $installLog = Join-Path $CacheDir "install-$(Split-Path -Leaf $ServerDir).log"
    $process = Start-Process -FilePath $Java -ArgumentList @('-jar', "`"$installer`"", '--installServer', "`"$ServerDir`"") `
        -WorkingDirectory $CacheDir -NoNewWindow -Wait -PassThru -RedirectStandardOutput $installLog -RedirectStandardError "$installLog.err"
    if ($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $argsFile)) {
        Stop-Smoke "Forge installer failed (exit code $($process.ExitCode)), see $installLog"
    }
    Write-Ok 'Forge server installed'
}

function Initialize-Server([string]$ModJar) {
    Write-Step 'Preparing server folder'
    Set-Content -LiteralPath (Join-Path $ServerDir 'eula.txt') -Value 'eula=true' -Encoding ASCII
    $properties = Join-Path $ServerDir 'server.properties'
    # A port other than the default avoids clashes with a server that may already run on this machine.
    Set-ServerProperty $properties 'server-port' $(if ($Pack) { '25598' } else { '25599' })
    Set-ServerProperty $properties 'motd' 'KingdomWorld smoke test'

    foreach ($dir in @('world', 'logs', 'crash-reports', 'smoke-logs')) {
        $path = Join-Path $ServerDir $dir
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
    }
    New-Item -ItemType Directory -Force -Path $SmokeLogDir | Out-Null

    $modsDir = Join-Path $ServerDir 'mods'
    if (Test-Path -LiteralPath $modsDir) { Remove-Item -LiteralPath $modsDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $modsDir | Out-Null

    if (-not $WithoutMod) {
        Copy-Item -LiteralPath $ModJar -Destination $modsDir
        Write-Ok "installed $(Split-Path -Leaf $ModJar)"
    } else {
        Add-Warning 'KingdomWorld is NOT installed (-WithoutMod diagnostic run)'
    }

    if ($Pack) {
        if (-not (Test-Path -LiteralPath $PackModsDir)) { Stop-Smoke "Modpack folder not found: $PackModsDir" }
        $excluded = @($ClientOnlyModIds + $ExcludeModIds)
        $copied = 0
        foreach ($jar in @(Get-ChildItem -LiteralPath $PackModsDir -Filter '*.jar' | Sort-Object Name)) {
            $info = Get-ModInfo $jar.FullName
            $hit = @($info.Ids | Where-Object { $excluded -contains $_ })
            if ($info.ClientOnly -or $hit.Count -gt 0) {
                $reason = if ($hit.Count -gt 0) { $hit -join ',' } else { 'clientSideOnly=true' }
                Write-Host "    skip  $($jar.Name) ($reason)"
                continue
            }
            Copy-Item -LiteralPath $jar.FullName -Destination $modsDir
            $copied++
        }
        Write-Ok "copied $copied modpack jar(s) from $PackModsDir"
    }
}

function Start-Server([string]$Phase) {
    $logsDir = Join-Path $ServerDir 'logs'
    if (Test-Path -LiteralPath $logsDir) { Remove-Item -LiteralPath $logsDir -Recurse -Force }
    $classLog = "smoke-logs/classload-$Phase.log"
    $arguments = @(
        "-Xmx$Memory",
        "-Xlog:class+load=info:file=$classLog",
        '@user_jvm_args.txt',
        "@libraries/net/minecraftforge/forge/$ForgeVersion/win_args.txt",
        'nogui'
    ) -join ' '
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Java
    $startInfo.Arguments = $arguments
    $startInfo.WorkingDirectory = $ServerDir
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    # Windows PowerShell creates the stdin writer with the console input encoding and flushes it right away.
    # Under code page 65001 that encoding carries a byte order mark, which would reach the server in front of
    # the first command ("?kingdomworld ..."). Switch to UTF-8 without a BOM while the process starts.
    $savedInputEncoding = $null
    try {
        if ([Console]::InputEncoding.GetPreamble().Length -gt 0) {
            $savedInputEncoding = [Console]::InputEncoding
            [Console]::InputEncoding = $script:Utf8NoBom
        }
    } catch {
        Add-Warning "could not change the console input encoding: $($_.Exception.Message)"
    }
    try {
        $process = [System.Diagnostics.Process]::Start($startInfo)
    } finally {
        if ($savedInputEncoding) { try { [Console]::InputEncoding = $savedInputEncoding } catch { } }
    }
    # Commands go through our own writer: UTF-8 without a BOM, flushed after every line.
    $stdin = New-Object System.IO.StreamWriter($process.StandardInput.BaseStream, $script:Utf8NoBom)
    $stdin.AutoFlush = $true
    $stdin.NewLine = "`n"
    # Drain the console streams so the server never blocks on a full pipe; latest.log is used for checks.
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    return [pscustomobject]@{ Process = $process; Stdin = $stdin; Stdout = $stdout; Stderr = $stderr; Phase = $Phase; Started = Get-Date }
}

function Get-LatestLog { Read-SharedText (Join-Path $ServerDir 'logs\latest.log') }

function Wait-ForLog([object]$Server, [string]$Pattern, [int]$TimeoutSeconds, [string]$FailPattern = '') {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $log = Get-LatestLog
        $match = [regex]::Match($log, $Pattern)
        if ($match.Success) { return $match }
        if ($FailPattern -and [regex]::IsMatch($log, $FailPattern)) { return $null }
        if ($Server.Process.HasExited) { return $null }
        Start-Sleep -Milliseconds 1000
    }
    return $null
}

function Send-ServerCommand([object]$Server, [string]$Command) {
    Write-Host "    > $Command"
    $Server.Stdin.WriteLine($Command)
}

function Stop-Server([object]$Server) {
    if (-not $Server.Process.HasExited) {
        Send-ServerCommand $Server 'stop'
        if (-not $Server.Process.WaitForExit($StopTimeout * 1000)) {
            Add-Failure "[$($Server.Phase)] server did not stop within $StopTimeout s, killing it"
            $Server.Process.Kill()
            $Server.Process.WaitForExit()
        }
    }
    $Server.Process.WaitForExit()
    $exitCode = $Server.Process.ExitCode
    Save-PhaseLogs $Server
    if ($exitCode -ne 0) {
        Add-Failure "[$($Server.Phase)] server exited with code $exitCode"
    } else {
        Write-Ok "[$($Server.Phase)] server stopped (exit code 0)"
    }
}

function Save-PhaseLogs([object]$Server) {
    $phase = $Server.Phase
    try { Set-Content -LiteralPath (Join-Path $SmokeLogDir "$phase-stdout.log") -Value $Server.Stdout.Result -Encoding UTF8 } catch { }
    try { Set-Content -LiteralPath (Join-Path $SmokeLogDir "$phase-stderr.log") -Value $Server.Stderr.Result -Encoding UTF8 } catch { }
    foreach ($name in @('latest.log', 'debug.log')) {
        $source = Join-Path $ServerDir "logs\$name"
        if (Test-Path -LiteralPath $source) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $SmokeLogDir "$phase-$name") -Force
        }
    }
}

function Test-PhaseLogs([string]$Phase) {
    # Errors and exceptions that mention the mod fail the test; its warnings are reported.
    $problems = New-Object System.Collections.Generic.List[string]
    foreach ($name in @('latest.log', 'debug.log', 'stdout.log', 'stderr.log')) {
        $file = Join-Path $SmokeLogDir "$Phase-$name"
        if (-not (Test-Path -LiteralPath $file)) { continue }
        foreach ($line in (Get-Content -LiteralPath $file -Encoding UTF8)) {
            if ($line -notmatch '(?i)kingdomworld|sekerator') { continue }
            $isLogRecord = $line -match '^\[[^\]]*\] \[[^\]]*/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]'
            # Errors: ERROR/FATAL records, frames of our code in a stack trace, exception lines that are part of
            # a stack trace (they do not start with a log record header) and warnings that carry an exception.
            $isError = $line -match '/(ERROR|FATAL)\]' `
                -or $line -match '^\s+at io\.github\.sekerator\.kingdomworld\.' `
                -or (-not $isLogRecord -and $line -match '(?i)(exception|error|caused by)') `
                -or ($line -match '/WARN\]' -and $line -match '(?i)exception')
            if ($isError) {
                $problems.Add("${name}: $($line.Trim())")
            } elseif ($line -match '/WARN\] \[io\.gi\.se\.ki|/WARN\] \[io\.github\.sekerator') {
                Add-Warning "[$Phase] $($line.Trim())"
            }
        }
    }
    if ($problems.Count -gt 0) {
        $shown = @($problems | Select-Object -Unique | Select-Object -First 20)
        Add-Failure "[$Phase] kingdomworld errors in logs:`n      $($shown -join "`n      ")"
    } else {
        Write-Ok "[$Phase] no kingdomworld errors or exceptions in logs"
    }

    $crashDir = Join-Path $ServerDir 'crash-reports'
    if (Test-Path -LiteralPath $crashDir) {
        $reports = @(Get-ChildItem -LiteralPath $crashDir -File)
        if ($reports.Count -gt 0) { Add-Failure "[$Phase] crash report(s) written: $($reports.Name -join ', ')" }
    }

    if (-not $WithoutMod) {
        $classLog = Join-Path $SmokeLogDir "classload-$Phase.log"
        $classText = Read-SharedText $classLog
        if (-not $classText) {
            Add-Failure "[$Phase] class loading log is missing"
        } elseif ($classText -notmatch 'io\.github\.sekerator\.kingdomworld\.KingdomWorld ') {
            Add-Failure "[$Phase] class loading log does not show the mod entry point, the check is not reliable"
        } else {
            $clientClasses = @([regex]::Matches($classText, 'io\.github\.sekerator\.kingdomworld\.client\.[\w.$]+') | ForEach-Object { $_.Value } | Select-Object -Unique)
            if ($clientClasses.Count -gt 0) {
                Add-Failure "[$Phase] client classes were loaded on the dedicated server: $($clientClasses -join ', ')"
            } else {
                Write-Ok "[$Phase] no client classes of the mod were loaded"
            }
        }
    }
}

function Invoke-Phase([string]$Phase, [scriptblock]$Body) {
    Write-Step "Session '$Phase': starting server"
    $server = Start-Server $Phase
    $done = Wait-ForLog $server '\]: Done \((\d+[.,]\d+)s\)!' $StartTimeout
    if (-not $done) {
        if (-not $server.Process.HasExited) { $server.Process.Kill(); $server.Process.WaitForExit() }
        Save-PhaseLogs $server
        Test-PhaseLogs $Phase
        Stop-Smoke "[$Phase] server did not reach 'Done' within $StartTimeout s (exited: $($server.Process.HasExited))"
    }
    Write-Ok "[$Phase] server started in $($done.Groups[1].Value)s"
    try {
        & $Body $server
    } finally {
        Stop-Server $server
        Test-PhaseLogs $Phase
    }
}

# ---------------------------------------------------------------------------------------------------------

Write-Host "KingdomWorld smoke test ($(if ($Pack) { 'modpack' } else { 'clean' }) server)" -ForegroundColor Cyan
if (-not (Test-Path -LiteralPath $Java)) { Stop-Smoke "Java not found at $Java (set -JavaHome)" }
$repoFull = [System.IO.Path]::GetFullPath($RepoRoot).TrimEnd('\') + '\'
if ([System.IO.Path]::GetFullPath($TestEnvRoot).StartsWith($repoFull, [System.StringComparison]::OrdinalIgnoreCase)) {
    Stop-Smoke "TestEnvRoot must be outside the repository: $TestEnvRoot"
}

$modJar = $null
if (-not $WithoutMod) {
    if (-not $SkipBuild) { Invoke-Build }
    $modJar = Get-ModJar
}
Install-ForgeServer
Initialize-Server $modJar

if ($WithoutMod) {
    Invoke-Phase 'baseline' { param($server) }
    Write-Summary
    if ($script:Failures.Count -gt 0) { exit 1 }
    exit 0
}

$script:CreatedId = $null
$script:CitizenIds = @()
$ConsoleFailPattern = 'Unknown or incomplete command|Incorrect argument|already exists|There is no kingdom|read-only'
$IdPattern ='[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'

# Lists the citizens of the smoke kingdom and returns their short ids, or $null if the list did not appear.
function Get-CitizenList([object]$Server, [int]$Expected, [string]$Phase) {
    $before = (Get-LatestLog).Length
    Send-ServerCommand $Server "kingdomworld admin citizens list $KingdomName"
    $header = Wait-ForLog $Server "Citizens of ${KingdomName}: $Expected \((\d+) present in the world\)" $CommandTimeout $ConsoleFailPattern
    if (-not $header) {
        Add-Failure "[$Phase] 'kingdomworld admin citizens list' did not report $Expected citizen(s)"
        return $null
    }
    if ($header.Groups[1].Value -ne '0') {
        Add-Failure "[$Phase] $($header.Groups[1].Value) citizen bodies exist on a server without players"
    }
    # The entries follow the header; give the server a moment to print them all.
    $deadline = (Get-Date).AddSeconds(10)
    do {
        $log = (Get-LatestLog).Substring($before)
        $ids = @([regex]::Matches($log, '(?m) - ([0-9a-f]{8}) \S+: ') | ForEach-Object { $_.Groups[1].Value })
        if ($ids.Count -ge $Expected) { break }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    if ($ids.Count -ne $Expected) {
        Add-Failure "[$Phase] expected $Expected citizen entries, found $($ids.Count)"
        return $null
    }
    if (@($ids | Select-Object -Unique).Count -ne $ids.Count) {
        Add-Failure "[$Phase] a citizen is listed twice: $($ids -join ', ')"
    }
    return $ids
}

Invoke-Phase 'session1' {
    param($server)
    if (-not (Wait-ForLog $server 'KingdomWorld loaded 0 kingdom\(s\)' 10)) {
        Add-Failure '[session1] expected a fresh world with 0 kingdoms'
    }
    if (Wait-ForLog $server 'KingdomWorld fire protection: active' 10) {
        Write-Ok '[session1] fire protection hook is active'
    } else {
        Add-Failure '[session1] the fire protection hook did not report itself active'
    }
    Send-ServerCommand $server "kingdomworld admin found $KingdomName 8 70 8"
    $created = Wait-ForLog $server "Founded kingdom $KingdomName \(id ($IdPattern)\) with (\d+) chunk\(s\)" $CommandTimeout $ConsoleFailPattern
    if ($created) {
        $script:CreatedId = $created.Groups[1].Value
        if ($created.Groups[2].Value -eq '9') {
            Write-Ok "[session1] kingdom founded with id $($script:CreatedId) and 9 chunks"
        } else {
            Add-Failure "[session1] expected 9 initial chunks, got $($created.Groups[2].Value)"
        }
    } else {
        Add-Failure "[session1] 'kingdomworld admin found $KingdomName' did not report success"
    }
    Send-ServerCommand $server "kingdomworld admin claim $KingdomName 2 0"
    if (Wait-ForLog $server "Chunk \[2, 0\] now belongs to $KingdomName" $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] chunk [2, 0] claimed'
    } else {
        Add-Failure "[session1] 'kingdomworld admin claim $KingdomName 2 0' did not report success"
    }
    Send-ServerCommand $server 'kingdomworld admin claim SmokeTest 9 9'
    if (Wait-ForLog $server 'Chunk \[9, 9\] cannot be claimed' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] a chunk that does not touch the land is refused'
    } else {
        Add-Failure '[session1] claiming a detached chunk was not refused'
    }

    Send-ServerCommand $server "kingdomworld admin citizens spawn 3 $KingdomName"
    if (Wait-ForLog $server "Spawned 3 citizen\(s\) in $KingdomName" $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] 3 citizens added'
    } else {
        Add-Failure "[session1] 'kingdomworld admin citizens spawn 3 $KingdomName' did not report success"
    }
    $ids = Get-CitizenList $server 3 'session1'
    if ($ids) {
        Write-Ok "[session1] citizens listed: $($ids -join ', '), no bodies without players"
        $victim = $ids[0]
        Send-ServerCommand $server "kingdomworld admin citizens kill $victim"
        if (Wait-ForLog $server "Citizen \S+ \($victim\) is dead" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session1] citizen $victim died by command"
        } else {
            Add-Failure "[session1] 'kingdomworld admin citizens kill $victim' did not report the death"
        }
        $left = Get-CitizenList $server 2 'session1'
        if ($left) {
            if ($left -contains $victim) { Add-Failure "[session1] the dead citizen $victim is still listed" }
            $script:CitizenIds = @($left)
            Write-Ok "[session1] 2 citizens remain: $($left -join ', ')"
        }
    }
}

$dataFile = Join-Path $ServerDir 'world\data\kingdomworld_kingdoms.dat'
if (Test-Path -LiteralPath $dataFile) { Write-Ok "saved data written: $dataFile" } else { Add-Failure "saved data file missing: $dataFile" }
$serverConfig = Join-Path $ServerDir 'world\serverconfig\kingdomworld-server.toml'
if (Test-Path -LiteralPath $serverConfig) { Write-Ok 'per-world server config created' } else { Add-Failure "server config missing: $serverConfig" }

if (-not $script:CreatedId) { Stop-Smoke 'kingdom was not created, skipping the restart check' }

Invoke-Phase 'session2' {
    param($server)
    if (-not (Wait-ForLog $server 'KingdomWorld loaded 1 kingdom\(s\) with 2 citizen\(s\)' 10)) {
        Add-Failure '[session2] expected 1 kingdom with 2 citizens to be loaded after restart'
    }
    Send-ServerCommand $server 'kingdomworld debug list'
    $listed = Wait-ForLog $server " - $KingdomName \(id ($IdPattern)\)" $CommandTimeout 'Unknown or incomplete command|There are no kingdoms yet'
    if (-not $listed) {
        Add-Failure "[session2] $KingdomName is missing from 'kingdomworld debug list' after restart"
    } elseif ($listed.Groups[1].Value -ne $script:CreatedId) {
        Add-Failure "[session2] $KingdomName has id $($listed.Groups[1].Value) after restart, expected $($script:CreatedId)"
    } else {
        Write-Ok "[session2] $KingdomName survived the restart with the same id"
    }
    Send-ServerCommand $server "kingdomworld debug info $KingdomName"
    if (Wait-ForLog $server "Id: $([regex]::Escape($script:CreatedId))" $CommandTimeout 'There is no kingdom') {
        Write-Ok "[session2] 'kingdomworld debug info $KingdomName' works"
    } else {
        Add-Failure "[session2] 'kingdomworld debug info $KingdomName' did not show the kingdom"
    }
    foreach ($check in @(@('2 0', 'belongs to SmokeTest'), @('0 0', 'belongs to SmokeTest'), @('-1 -1', 'belongs to SmokeTest'), @('3 0', 'is unclaimed'))) {
        $coords = $check[0]
        $label = "[$($coords -replace ' ', ', ')]"
        Send-ServerCommand $server "kingdomworld admin owner $coords"
        if (Wait-ForLog $server "Chunk $([regex]::Escape($label)) in minecraft:overworld $($check[1])" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] chunk $label $($check[1])"
        } else {
            Add-Failure "[session2] chunk ${label}: expected '$($check[1])' after restart"
        }
    }
    Send-ServerCommand $server "kingdom info $KingdomName"
    if (Wait-ForLog $server 'Chunks: 10 of \d+' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok "[session2] 'kingdom info' reports all 10 chunks after restart"
    } else {
        Add-Failure "[session2] 'kingdom info $KingdomName' did not report 10 chunks"
    }
    $ids = Get-CitizenList $server 2 'session2'
    if ($ids) {
        $expected = @($script:CitizenIds | Sort-Object)
        $actual = @($ids | Sort-Object)
        if (($expected -join ',') -eq ($actual -join ',')) {
            Write-Ok "[session2] the same 2 citizens survived the restart: $($actual -join ', ')"
        } else {
            Add-Failure "[session2] citizens after restart are $($actual -join ', '), expected $($expected -join ', ')"
        }
        $first = $ids[0]
        Send-ServerCommand $server "kingdomworld admin citizens info $first"
        if (Wait-ForLog $server "Kingdom: $KingdomName" $CommandTimeout 'No citizen has an id') {
            Write-Ok "[session2] 'kingdomworld admin citizens info $first' works"
        } else {
            Add-Failure "[session2] 'kingdomworld admin citizens info $first' did not show the citizen"
        }
    }
}

Write-Summary
if ($script:Failures.Count -gt 0) { exit 1 }
exit 0
