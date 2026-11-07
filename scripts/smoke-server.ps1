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
$PartnerName = 'SmokePartner'

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

    # Accidents far from players are random; the smoke test switches them off so that its miner always lives.
    $defaults = Join-Path $ServerDir 'defaultconfigs'
    New-Item -ItemType Directory -Force -Path $defaults | Out-Null
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $defaults 'kingdomworld-server.toml'), "[work]`n`ttaskRiskMultiplier = 0.0`n", $utf8)

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

function Count-Log([string]$Pattern) { [regex]::Matches((Get-LatestLog), $Pattern).Count }

# Waits for a match after the first $Seen ones, for answers to commands that are sent more than once.
function Wait-ForNewLog([object]$Server, [string]$Pattern, [int]$TimeoutSeconds, [int]$Seen) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $found = [regex]::Matches((Get-LatestLog), $Pattern)
        if ($found.Count -gt $Seen) { return $found[$Seen] }
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
$script:MinerId = $null
$script:Mined = 0
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

    # Work posts: a mine with a miner far from players, who digs by the estimate of its zone.
    if ($script:CitizenIds.Count -gt 0) {
        $script:MinerId = $script:CitizenIds[0]
        # The mine lies in chunks that stay loaded without players, and in solid rock whatever the world looks like.
        Send-ServerCommand $server 'forceload add 0 0 31 31'
        if (-not (Wait-ForLog $server 'Marked .* chunks in minecraft:overworld from \[0, 0\] to \[1, 1\]' $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] the chunks of the mine could not be force loaded'
        }
        Start-Sleep -Seconds 2
        Send-ServerCommand $server 'fill 4 36 4 20 41 20 minecraft:stone'
        Send-ServerCommand $server 'setblock 2 70 2 kingdomworld:storehouse'
        if (-not (Wait-ForLog $server 'Changed the block at 2, 70, 2' $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] the storehouse could not be placed'
        }
        Send-ServerCommand $server "kingdomworld admin storage put minecraft:iron_pickaxe 2 $KingdomName"
        if (Wait-ForLog $server "Stored 2 of minecraft:iron_pickaxe in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] pickaxes put into the storehouse'
        } else {
            Add-Failure '[session1] pickaxes could not be put into the storehouse'
        }
        Send-ServerCommand $server 'setblock 12 40 12 kingdomworld:work_post'
        Send-ServerCommand $server 'kingdomworld admin posts set 12 40 12 profession miner'
        if (Wait-ForLog $server 'Work post at 12 40 12: Miner, radius \d+, seats 1' $CommandTimeout 'No work post at|Unknown or incomplete command') {
            Write-Ok '[session1] work post placed and set to mining'
        } else {
            Add-Failure '[session1] the work post was not registered or could not be set to mining'
        }
        Send-ServerCommand $server "kingdomworld admin citizens profession $($script:MinerId) miner"
        if (Wait-ForLog $server '\S+ is now: Miner' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session1] citizen $($script:MinerId) became a miner"
        } else {
            Add-Failure "[session1] citizen $($script:MinerId) could not become a miner"
        }
        Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 20"
        if (-not (Wait-ForLog $server "Ran 20 simulation step\(s\) of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] the simulation steps did not run'
        }
        Send-ServerCommand $server "kingdomworld admin posts list $KingdomName"
        $post = Wait-ForLog $server ' - 12 40 12: Miner, workers 1/1, radius \d+, (.+), today (\d+), about (\d+) a day' $CommandTimeout $ConsoleFailPattern
        if (-not $post) {
            Add-Failure '[session1] the work post is not listed with its miner'
        } elseif ([int]$post.Groups[2].Value -le 0) {
            Add-Failure "[session1] the miner gathered nothing far from players (status: $($post.Groups[1].Value))"
        } else {
            $script:Mined = [int]$post.Groups[2].Value
            Write-Ok "[session1] the miner gathered $($script:Mined) item(s) far from players ($($post.Groups[1].Value))"
        }
        Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
        $stored = Wait-ForLog $server "Storehouses of ${KingdomName}: (\d+) kinds of items in 1 storehouses" $CommandTimeout $ConsoleFailPattern
        if ($stored -and [int]$stored.Groups[1].Value -ge 2) {
            Write-Ok "[session1] the storehouse holds $($stored.Groups[1].Value) kinds of items"
        } else {
            Add-Failure '[session1] the harvest of the mine did not reach the storehouse'
        }
    }

    # Workshops and orders: far from players a smelter at a furnace and a smith at a crafting table make an iron sword
    # from raw iron through a chain of orders (the sword asks for ingots, the ingots for smelting).
    if ($script:CitizenIds.Count -gt 0) {
        Send-ServerCommand $server "kingdomworld admin citizens spawn 2 $KingdomName"
        if (-not (Wait-ForLog $server "Spawned 2 citizen\(s\) in $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] 2 more citizens could not be added'
        }
        $all = Get-CitizenList $server 4 'session1'
        if ($all) {
            $new = @($all | Where-Object { $script:CitizenIds -notcontains $_ })
            $script:CitizenIds = @($all)
            $script:SmelterId = $new[0]
            $script:SmithId = $new[1]
            Send-ServerCommand $server 'setblock 26 70 4 minecraft:furnace'
            Send-ServerCommand $server 'setblock 26 70 12 minecraft:crafting_table'
            Send-ServerCommand $server 'setblock 24 70 4 kingdomworld:work_post'
            Send-ServerCommand $server 'setblock 24 70 12 kingdomworld:work_post'
            Start-Sleep -Seconds 2
            Send-ServerCommand $server 'kingdomworld admin posts set 24 70 4 profession smelter'
            if (-not (Wait-ForLog $server 'Work post at 24 70 4: Smelter, radius \d+' $CommandTimeout 'No work post at 24 70 4|Unknown or incomplete command')) {
                Add-Failure '[session1] the smelter post was not registered or could not be set'
            }
            Send-ServerCommand $server 'kingdomworld admin posts set 24 70 12 profession smith'
            if (-not (Wait-ForLog $server 'Work post at 24 70 12: Smith, radius \d+' $CommandTimeout 'No work post at 24 70 12|Unknown or incomplete command')) {
                Add-Failure '[session1] the smithy post was not registered or could not be set'
            }
            Send-ServerCommand $server "kingdomworld admin citizens profession $($script:SmelterId) smelter"
            Send-ServerCommand $server "kingdomworld admin citizens profession $($script:SmithId) smith"
            if ((Wait-ForLog $server '\S+ is now: Smelter' $CommandTimeout $ConsoleFailPattern) -and (Wait-ForLog $server '\S+ is now: Smith' $CommandTimeout $ConsoleFailPattern)) {
                Write-Ok "[session1] citizens $($script:SmelterId) and $($script:SmithId) became a smelter and a smith"
            } else {
                Add-Failure '[session1] the smelter or the smith could not be appointed'
            }
            foreach ($stock in @('minecraft:raw_iron 2', 'minecraft:coal 2', 'minecraft:stick 1')) {
                Send-ServerCommand $server "kingdomworld admin storage put $stock $KingdomName"
            }
            if (-not (Wait-ForLog $server "Stored 1 of minecraft:stick in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
                Add-Failure '[session1] the materials of the sword could not be stored'
            }
            Send-ServerCommand $server "kingdomworld admin orders add minecraft:iron_sword 1 make $KingdomName"
            if (Wait-ForLog $server 'Order #(\d+) given: minecraft:iron_sword, 1 pcs\., make' $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session1] the order of an iron sword was given'
            } else {
                Add-Failure '[session1] the order of an iron sword was refused'
            }
            Send-ServerCommand $server "kingdomworld admin orders add minecraft:bread 5 keep $KingdomName"
            $keep = Wait-ForLog $server 'Order #(\d+) given: minecraft:bread, 5 pcs\., keep' $CommandTimeout $ConsoleFailPattern
            if ($keep) {
                $script:KeepOrderId = $keep.Groups[1].Value
                Write-Ok "[session1] a keep order #$($script:KeepOrderId) for bread was given"
            } else {
                Add-Failure '[session1] the keep order for bread was refused'
            }
            Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 6"
            if (-not (Wait-ForLog $server "Ran 6 simulation step\(s\) of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
                Add-Failure '[session1] the simulation steps of the workshops did not run'
            }
            Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
            if (Wait-ForLog $server ' - minecraft:iron_sword: 1' $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session1] the smith made the iron sword from raw iron through the smelter far from players'
                $script:SwordMade = $true
            } else {
                Add-Failure '[session1] the iron sword was not made (chain smelter -> smith)'
            }
            Send-ServerCommand $server "kingdomworld admin orders list $KingdomName"
            if (Wait-ForLog $server "Orders of ${KingdomName}: 1\b" $CommandTimeout $ConsoleFailPattern) {
                Write-Ok '[session1] the finished order left, the keep order stays'
            } else {
                Add-Failure '[session1] expected only the keep order to be left'
            }

            # With the mods of the pack: an item of a mod with a crafting table recipe is made by the smith on order.
            if ($Pack) {
                Send-ServerCommand $server 'kingdomworld admin recipes list smith mods'
                # A weapon or a tool, not armor or other gear that wears out.
                $modItem = Wait-ForLog $server ' - ([a-z0-9_.-]+:[a-z0-9_./-]*(?:sword|axe|shovel|hoe|bow|spear|dagger|hammer|mace|halberd|katana|scythe)[a-z0-9_./-]*): crafting, recipe ' $CommandTimeout 'Smith: 0 recipes'
                if (-not $modItem) {
                    Add-Failure '[session1] no tool or weapon of a mod with a crafting table recipe was found'
                } else {
                    $item = $modItem.Groups[1].Value
                    $script:ModItem = $item
                    Write-Ok "[session1] a smith item of a mod: $item"
                    Send-ServerCommand $server "kingdomworld admin recipes stock $item $KingdomName"
                    if (-not (Wait-ForLog $server "For $([regex]::Escape($item)) \(recipe " $CommandTimeout $ConsoleFailPattern)) {
                        Add-Failure "[session1] the materials of $item could not be stored"
                    }
                    Send-ServerCommand $server "kingdomworld admin orders add $item 1 make $KingdomName"
                    if (-not (Wait-ForLog $server "Order #\d+ given: $([regex]::Escape($item)), 1 pcs\." $CommandTimeout $ConsoleFailPattern)) {
                        Add-Failure "[session1] the order of $item was refused"
                    }
                    Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 4"
                    if (-not (Wait-ForLog $server "Ran 4 simulation step\(s\) of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
                        Add-Failure '[session1] the simulation steps for the item of a mod did not run'
                    }
                    Send-ServerCommand $server "kingdomworld admin orders list $KingdomName"
                    Start-Sleep -Seconds 2
                    Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
                    if (Wait-ForLog $server " - $([regex]::Escape($item)): 1" $CommandTimeout $ConsoleFailPattern) {
                        Write-Ok "[session1] the smith made $item on order"
                    } else {
                        Add-Failure "[session1] the smith did not make $item on order"
                    }
                }
            }
        }
    }

    # Money: the treasury gets 1000 from an operator, a minter far from players strikes silver coins from five iron
    # ingots of the storehouses, a payday pays the wages, and the worth of every item (of the mods too) is worked out.
    if ($script:CitizenIds.Count -gt 0) {
        $rawPattern = 'Raw: balance (\d+), copper (\d+), silver (\d+), gold (\d+), funds (\d+), supply (\d+)'
        Send-ServerCommand $server "kingdomworld admin economy treasury $KingdomName add 1000"
        if (Wait-ForLog $server "Added 1000 to the treasury of $KingdomName, funds 1000" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] the treasury got 1000 from the operator'
        } else {
            Add-Failure '[session1] the operator could not add money to the treasury'
        }
        $count = $script:CitizenIds.Count + 1
        Send-ServerCommand $server "kingdomworld admin citizens spawn 1 $KingdomName"
        if (-not (Wait-ForLog $server "Spawned 1 citizen\(s\) in $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
            Add-Failure '[session1] the minter could not be added'
        }
        $all = Get-CitizenList $server $count 'session1'
        if ($all) {
            $new = @($all | Where-Object { $script:CitizenIds -notcontains $_ })
            $script:CitizenIds = @($all)
            $script:MinterId = $new[0]
            Send-ServerCommand $server 'setblock 26 70 20 kingdomworld:mint'
            Send-ServerCommand $server 'setblock 24 70 20 kingdomworld:work_post'
            Start-Sleep -Seconds 2
            Send-ServerCommand $server 'kingdomworld admin posts set 24 70 20 profession minter'
            if (-not (Wait-ForLog $server 'Work post at 24 70 20: Minter, radius \d+' $CommandTimeout 'No work post at 24 70 20|Unknown or incomplete command')) {
                Add-Failure '[session1] the mint post was not registered or could not be set'
            }
            Send-ServerCommand $server "kingdomworld admin citizens profession $($script:MinterId) minter"
            if (Wait-ForLog $server '\S+ is now: Minter' $CommandTimeout $ConsoleFailPattern) {
                Write-Ok "[session1] citizen $($script:MinterId) became a minter"
            } else {
                Add-Failure '[session1] the minter could not be appointed'
            }
            Send-ServerCommand $server "kingdomworld admin storage put minecraft:iron_ingot 5 $KingdomName"
            if (-not (Wait-ForLog $server "Stored 5 of minecraft:iron_ingot in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
                Add-Failure '[session1] the iron for the mint could not be stored'
            }
            $runs = Count-Log "Ran 1 simulation step\(s\) of $KingdomName"
            Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 1"
            [void](Wait-ForNewLog $server "Ran 1 simulation step\(s\) of $KingdomName" $CommandTimeout $runs)
            # The zone of the post is looked at over a few ticks before the minter knows its mint.
            Start-Sleep -Seconds 3
            $runs = Count-Log "Ran 6 simulation step\(s\) of $KingdomName"
            Send-ServerCommand $server "kingdomworld admin simulate $KingdomName 6"
            if (-not (Wait-ForNewLog $server "Ran 6 simulation step\(s\) of $KingdomName" $CommandTimeout $runs)) {
                Add-Failure '[session1] the simulation steps of the mint did not run'
            }
            $seen = Count-Log $rawPattern
            Send-ServerCommand $server "kingdomworld admin economy info $KingdomName"
            $raw = Wait-ForNewLog $server $rawPattern $CommandTimeout $seen
            # The smelter may have made copper ingots of the miner's ore: the minter strikes them too.
            $funds = 0
            if ($raw) {
                $vault = [int64]$raw.Groups[2].Value + 10 * [int64]$raw.Groups[3].Value + 100 * [int64]$raw.Groups[4].Value
                $funds = [int64]$raw.Groups[5].Value
            }
            if ($raw -and [int64]$raw.Groups[1].Value -eq 1000 -and [int64]$raw.Groups[3].Value -eq 10 -and $funds -eq 1000 + $vault) {
                Write-Ok "[session1] the minter struck 10 silver coins from 5 iron ingots into the vault: $($raw.Value)"
            } else {
                Add-Failure "[session1] expected 10 silver coins in the vault and 1000 on the books, got '$(if ($raw) { $raw.Value })'"
            }
            Send-ServerCommand $server "kingdomworld admin economy payday $KingdomName"
            $pay = Wait-ForLog $server "Payday of $KingdomName on day (\d+): tax payers (\d+), taxes (\d+), wages (\d+) of (\d+), citizens (\d+)" $CommandTimeout $ConsoleFailPattern
            if ($pay -and $pay.Groups[4].Value -eq $pay.Groups[5].Value -and [int]$pay.Groups[6].Value -eq $count) {
                Write-Ok "[session1] the payday paid all wages: $($pay.Groups[4].Value) to $count citizens"
            } else {
                Add-Failure "[session1] the payday did not pay the wages: '$(if ($pay) { $pay.Value })'"
            }
            $seen = Count-Log $rawPattern
            Send-ServerCommand $server "kingdomworld admin economy info $KingdomName"
            $raw = Wait-ForNewLog $server $rawPattern $CommandTimeout $seen
            if ($raw -and $pay -and [int64]$raw.Groups[5].Value -eq ($funds - [int64]$pay.Groups[4].Value)) {
                $script:EconomyRaw = $raw.Value
                Write-Ok "[session1] the wages left the treasury: $($raw.Value)"
            } else {
                Add-Failure "[session1] the treasury after the payday is wrong: '$(if ($raw) { $raw.Value })'"
            }
        }
        Send-ServerCommand $server 'kingdomworld admin economy values'
        $values = Wait-ForLog $server 'Item values: (\d+) items \((\d+) from recipes, (\d+) from the config, (\d+) by default\), (\d+) recipes, (\d+) unreadable' $CommandTimeout $ConsoleFailPattern
        if ($values -and [int]$values.Groups[1].Value -gt 1000 -and [int]$values.Groups[2].Value -gt 300) {
            Write-Ok "[session1] item values: $($values.Value)"
            if ([int]$values.Groups[6].Value -gt 0) {
                Add-Warning "[session1] $($values.Groups[6].Value) recipe(s) of mods could not be read for item values"
            }
        } else {
            Add-Failure "[session1] the worth of items was not worked out: '$(if ($values) { $values.Value })'"
        }
        Send-ServerCommand $server 'kingdomworld admin economy value minecraft:iron_sword'
        if (Wait-ForLog $server 'is worth .+ \(from recipes; minecraft:iron_sword = (\d+)\)' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session1] an iron sword is worth what its recipe costs'
        } else {
            Add-Failure '[session1] the worth of an iron sword was not worked out from its recipe'
        }
    }

    # Trade: a second kingdom far away buys wheat in a deal; its caravan leaves with the payment in escrow and is still on
    # the way when the server stops. A test buyer buys wheat at the market from the account. No money appears anywhere.
    Send-ServerCommand $server "kingdomworld admin found $PartnerName 2000 70 2000"
    if (Wait-ForLog $server "Founded kingdom $PartnerName \(id ($IdPattern)\) with (\d+) chunk\(s\)" $CommandTimeout $ConsoleFailPattern) {
        Write-Ok "[session1] the partner kingdom $PartnerName was founded"
    } else {
        Add-Failure "[session1] the partner kingdom $PartnerName was not founded"
    }
    Send-ServerCommand $server 'forceload add 1984 1984 2015 2015'
    Start-Sleep -Seconds 3
    Send-ServerCommand $server 'setblock 2001 70 2001 kingdomworld:storehouse'
    Start-Sleep -Seconds 2
    Send-ServerCommand $server "kingdomworld admin economy treasury $PartnerName add 500"
    if (-not (Wait-ForLog $server "Added 500 to the treasury of $PartnerName, funds 500" $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure "[session1] the treasury of $PartnerName got no money"
    }
    Send-ServerCommand $server "kingdomworld admin storage put minecraft:wheat 64 $KingdomName"
    if (-not (Wait-ForLog $server "Stored 64 of minecraft:wheat in the storehouses of $KingdomName" $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure '[session1] the wheat for the deal could not be stored'
    }
    $ratePattern = '(\S+): rate (\d+), money (\d+), coins in hands (\d+), treasury (\d+), reserves (\d+), exported today (\d+)'
    Send-ServerCommand $server "kingdomworld admin trade propose $KingdomName $PartnerName minecraft:wheat 32 100"
    if (-not (Wait-ForLog $server "The proposal went to $PartnerName" $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure '[session1] the deal was not proposed'
    }
    Send-ServerCommand $server 'kingdomworld admin trade deals'
    $deal = Wait-ForLog $server "#(\d+) $KingdomName -> ${PartnerName}: minecraft:wheat x32 for 100, proposed, deliveries 0" $CommandTimeout $ConsoleFailPattern
    if ($deal) {
        Send-ServerCommand $server "kingdomworld admin trade accept $PartnerName $($deal.Groups[1].Value)"
        if (Wait-ForLog $server 'The deal is accepted and the caravan left' $CommandTimeout 'Unknown or incomplete command|Incorrect argument|read-only|too few|too little') {
            Write-Ok "[session1] $PartnerName accepted deal #$($deal.Groups[1].Value) and the caravan left"
        } else {
            Add-Failure '[session1] the deal was not accepted'
        }
    } else {
        Add-Failure '[session1] the proposed deal is not listed'
    }
    Send-ServerCommand $server 'kingdomworld admin trade caravans'
    $caravan = Wait-ForLog $server "#(\d+) $KingdomName -> ${PartnerName}: minecraft:wheat x32, payment 100, traveling, (\d+) ticks left" $CommandTimeout $ConsoleFailPattern
    if ($caravan -and [int64]$caravan.Groups[2].Value -gt 6000) {
        $script:CaravanId = $caravan.Groups[1].Value
        Write-Ok "[session1] caravan #$($script:CaravanId) is on the way, $($caravan.Groups[2].Value) ticks left"
    } else {
        Add-Failure "[session1] no caravan on a long way: '$(if ($caravan) { $caravan.Value })'"
    }
    Send-ServerCommand $server "kingdomworld admin trade sell $KingdomName minecraft:wheat 5 0"
    if (-not (Wait-ForLog $server 'The kingdom sells Wheat' $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure '[session1] the market does not sell wheat'
    }
    $seen = Count-Log $ratePattern
    Send-ServerCommand $server 'kingdomworld admin trade rates'
    [void](Wait-ForNewLog $server $ratePattern $CommandTimeout $seen)
    Start-Sleep -Seconds 1
    $before = [regex]::Matches((Get-LatestLog), "${KingdomName}: rate \d+, money (\d+),")
    $moneyBefore = if ($before.Count -gt 0) { [int64]$before[$before.Count - 1].Groups[1].Value } else { -1 }
    Send-ServerCommand $server "kingdomworld admin trade wallet $KingdomName SmokeBuyer 100"
    if (-not (Wait-ForLog $server "Account of SmokeBuyer in the currency of ${KingdomName}: 100" $CommandTimeout $ConsoleFailPattern)) {
        Add-Failure '[session1] the test buyer got no money'
    }
    Send-ServerCommand $server "kingdomworld admin trade purchase $KingdomName SmokeBuyer minecraft:wheat 3"
    if (Wait-ForLog $server 'Bought Wheat: 3 items for' $CommandTimeout $ConsoleFailPattern) {
        Write-Ok '[session1] the test buyer bought 3 wheat at the market from the account'
    } else {
        Add-Failure '[session1] buying at the market failed'
    }
    $seen = Count-Log $ratePattern
    Send-ServerCommand $server 'kingdomworld admin trade rates'
    [void](Wait-ForNewLog $server $ratePattern $CommandTimeout $seen)
    Start-Sleep -Seconds 1
    $log = Get-LatestLog
    $mine = [regex]::Matches($log, "${KingdomName}: rate \d+, money (\d+),")
    $theirs = [regex]::Matches($log, "${PartnerName}: rate \d+, money (\d+),")
    $moneyAfter = if ($mine.Count -gt 0) { [int64]$mine[$mine.Count - 1].Groups[1].Value } else { -2 }
    $partnerMoney = if ($theirs.Count -gt 0) { [int64]$theirs[$theirs.Count - 1].Groups[1].Value } else { -1 }
    if ($moneyAfter -eq $moneyBefore + 100 -and $partnerMoney -eq 500) {
        $script:TradeMoney = "$moneyAfter/$partnerMoney"
        Write-Ok "[session1] no money appeared: $KingdomName $moneyBefore + 100 from the operator = $moneyAfter, $PartnerName 500 with 100 in escrow"
    } else {
        Add-Failure "[session1] money changed in trade: $KingdomName $moneyBefore -> $moneyAfter (expected +100), $PartnerName $partnerMoney (expected 500)"
    }
    if ($script:EconomyRaw) {
        $seen = Count-Log 'Raw: balance (\d+), copper (\d+), silver (\d+), gold (\d+), funds (\d+), supply (\d+)'
        Send-ServerCommand $server "kingdomworld admin economy info $KingdomName"
        $raw = Wait-ForNewLog $server 'Raw: balance (\d+), copper (\d+), silver (\d+), gold (\d+), funds (\d+), supply (\d+)' $CommandTimeout $seen
        if ($raw) { $script:EconomyRaw = $raw.Value }
    }
}

$dataFile = Join-Path $ServerDir 'world\data\kingdomworld_kingdoms.dat'
if (Test-Path -LiteralPath $dataFile) { Write-Ok "saved data written: $dataFile" } else { Add-Failure "saved data file missing: $dataFile" }
$serverConfig = Join-Path $ServerDir 'world\serverconfig\kingdomworld-server.toml'
if (Test-Path -LiteralPath $serverConfig) { Write-Ok 'per-world server config created' } else { Add-Failure "server config missing: $serverConfig" }

if (-not $script:CreatedId) { Stop-Smoke 'kingdom was not created, skipping the restart check' }

Invoke-Phase 'session2' {
    param($server)
    $citizenCount = $script:CitizenIds.Count
    $postCount = 1
    if ($script:SmithId) { $postCount += 2 }
    if ($script:MinterId) { $postCount += 1 }
    if (-not (Wait-ForLog $server "KingdomWorld loaded 2 kingdom\(s\) with $citizenCount citizen\(s\), $postCount work post\(s\), 2 storehouse\(s\), 2 treasury\(ies\), \d+ player\(s\) with accounts, 1 market\(s\), 1 deal\(s\), 1 caravan\(s\) on the way" 10)) {
        Add-Failure "[session2] expected 2 kingdoms with $citizenCount citizens, $postCount work post(s), 2 storehouses, 2 treasuries, a market, a deal and a caravan on the way to be loaded after restart"
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
    $ids = Get-CitizenList $server $citizenCount 'session2'
    if ($ids) {
        $expected = @($script:CitizenIds | Sort-Object)
        $actual = @($ids | Sort-Object)
        if (($expected -join ',') -eq ($actual -join ',')) {
            Write-Ok "[session2] the same $citizenCount citizens survived the restart: $($actual -join ', ')"
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
    if ($script:MinerId) {
        Send-ServerCommand $server "kingdomworld admin posts list $KingdomName"
        $post = Wait-ForLog $server ' - 12 40 12: Miner, workers 1/1, radius \d+, (.+), today (\d+)' $CommandTimeout $ConsoleFailPattern
        if ($post) {
            Write-Ok '[session2] the work post kept its profession and its miner after restart'
        } else {
            Add-Failure '[session2] the work post lost its settings or its miner after restart'
        }
        Send-ServerCommand $server "kingdomworld admin citizens info $($script:MinerId)"
        if (Wait-ForLog $server 'Workplace: 12 40 12' $CommandTimeout 'No citizen has an id') {
            Write-Ok "[session2] the miner $($script:MinerId) still works at the post"
        } else {
            Add-Failure "[session2] the miner $($script:MinerId) lost the workplace after restart"
        }
        Send-ServerCommand $server "kingdomworld admin storage list $KingdomName"
        $stored = Wait-ForLog $server "Storehouses of ${KingdomName}: (\d+) kinds of items in 1 storehouses" $CommandTimeout $ConsoleFailPattern
        if ($stored -and [int]$stored.Groups[1].Value -ge 2) {
            Write-Ok '[session2] the contents of the storehouse survived the restart'
        } else {
            Add-Failure '[session2] the storehouse lost its contents after restart'
        }
    }
    if ($script:SwordMade) {
        if (Wait-ForLog $server ' - minecraft:iron_sword: 1' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the iron sword is still in the storehouse'
        } else {
            Add-Failure '[session2] the iron sword was lost after restart'
        }
    }
    if ($script:KeepOrderId) {
        Send-ServerCommand $server "kingdomworld admin orders list $KingdomName"
        if (Wait-ForLog $server " - #$($script:KeepOrderId) minecraft:bread \(.+\): keep 5" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] the keep order #$($script:KeepOrderId) survived the restart"
        } else {
            Add-Failure "[session2] the keep order #$($script:KeepOrderId) was lost after restart"
        }
        Send-ServerCommand $server 'kingdomworld admin posts list SmokeTest'
        if (Wait-ForLog $server ' - 24 70 12: Smith, workers 1/1' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the smithy kept its smith after restart'
        } else {
            Add-Failure '[session2] the smithy lost its smith after restart'
        }
    }
    if ($script:EconomyRaw) {
        # The treasury and its coins are exactly as they were: nothing lost, nothing made by the restart.
        Send-ServerCommand $server "kingdomworld admin economy info $KingdomName"
        $raw = Wait-ForLog $server 'Raw: balance (\d+), copper (\d+), silver (\d+), gold (\d+), funds (\d+), supply (\d+)' $CommandTimeout $ConsoleFailPattern
        if ($raw -and $raw.Value -eq $script:EconomyRaw) {
            Write-Ok "[session2] the treasury survived the restart unchanged: $($raw.Value)"
        } else {
            Add-Failure "[session2] the treasury changed in the restart: '$(if ($raw) { $raw.Value })', expected '$($script:EconomyRaw)'"
        }
        Send-ServerCommand $server 'kingdomworld admin posts list SmokeTest'
        if (Wait-ForLog $server ' - 24 70 20: Minter, workers 1/1' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the mint post kept its minter after restart'
        } else {
            Add-Failure '[session2] the mint post lost its minter after restart'
        }
    }
    if ($script:CaravanId) {
        # The caravan is still on its way after the restart, with the same cargo and payment; brought in now, it delivers
        # the wheat and pays the seller, and the money of both currencies stays what it was.
        Send-ServerCommand $server 'kingdomworld admin trade caravans'
        if (Wait-ForLog $server "#$($script:CaravanId) $KingdomName -> ${PartnerName}: minecraft:wheat x32, payment 100, traveling, (\d+) ticks left" $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] caravan #$($script:CaravanId) survived the restart on its way"
        } else {
            Add-Failure "[session2] caravan #$($script:CaravanId) did not survive the restart"
        }
        Send-ServerCommand $server 'kingdomworld admin trade arrive'
        if (Wait-ForLog $server 'Caravans arrived: 1, still on the way: 0' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok '[session2] the caravan arrived'
        } else {
            Add-Failure '[session2] the caravan did not arrive'
        }
        Send-ServerCommand $server "kingdomworld admin storage list $PartnerName"
        if (Wait-ForLog $server ' - (minecraft:wheat|Wheat): 32' $CommandTimeout $ConsoleFailPattern) {
            Write-Ok "[session2] the wheat is in the storehouse of $PartnerName"
        } else {
            Add-Failure "[session2] the wheat did not reach the storehouse of $PartnerName"
        }
        $ratePattern = '(\S+): rate (\d+), money (\d+), coins in hands (\d+), treasury (\d+), reserves (\d+), exported today (\d+)'
        $seen = Count-Log $ratePattern
        Send-ServerCommand $server 'kingdomworld admin trade rates'
        [void](Wait-ForNewLog $server $ratePattern $CommandTimeout $seen)
        Start-Sleep -Seconds 1
        $log = Get-LatestLog
        $mine = [regex]::Matches($log, "${KingdomName}: rate \d+, money (\d+), coins in hands \d+, treasury \d+, reserves (\d+)")
        $theirs = [regex]::Matches($log, "${PartnerName}: rate \d+, money (\d+),")
        $now = if ($mine.Count -gt 0 -and $theirs.Count -gt 0) { "$($mine[$mine.Count - 1].Groups[1].Value)/$($theirs[$theirs.Count - 1].Groups[1].Value)" } else { '' }
        if ($now -eq $script:TradeMoney -and $mine[$mine.Count - 1].Groups[2].Value -eq '1') {
            Write-Ok "[session2] the delivery made no money: $now, the seller holds the buyer's currency in its reserves"
        } else {
            Add-Failure "[session2] money changed in the delivery: '$now', expected '$($script:TradeMoney)' and a reserve"
        }
    }
}

Write-Summary
if ($script:Failures.Count -gt 0) { exit 1 }
exit 0
